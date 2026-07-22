package moe.shizuku.manager.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import moe.shizuku.manager.MainActivity
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.ShizukuSettings.LaunchMethod
import moe.shizuku.manager.adb.AdbClient
import moe.shizuku.manager.adb.AdbStarter
import moe.shizuku.manager.adb.AdbKey
import moe.shizuku.manager.adb.AdbMdns
import moe.shizuku.manager.adb.PreferenceAdbKeyStore
import moe.shizuku.manager.ktx.logd
import moe.shizuku.manager.ktx.logi
import moe.shizuku.manager.module.ModuleSettings
import moe.shizuku.manager.starter.Starter
import moe.shizuku.manager.utils.EnvironmentUtils
import rikka.shizuku.Shizuku
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object WatchdogManager {

    private const val CHANNEL_ID = "service_watchdog"
    private const val NOTIFICATION_ID = 1001
    private const val EXPECTED_DEATH_WINDOW_MS = 10_000L
    private const val WIRELESS_ADB_DISCOVERY_TIMEOUT_SECONDS = 5L
    private const val DHIZUKU_BIND_TIMEOUT_MS = 10_000L
    private const val KEY_USER_STOP_REQUESTED = "watchdog_user_stop_requested"

    @Volatile
    var expectingDeath = false
        set(value) {
            field = value
            expectedDeathDeadlineMillis = if (value) {
                SystemClock.elapsedRealtime() + EXPECTED_DEATH_WINDOW_MS
            } else {
                0L
            }
        }

    @Volatile
    private var expectedDeathDeadlineMillis = 0L

    @Volatile
    private var initialized = false

    private val restartInProgress = AtomicBoolean(false)

    @Volatile
    private var userStopRequested = false

    fun init(context: Context) {
        val appContext = context.applicationContext
        if (initialized) return
        initialized = true

        userStopRequested = ShizukuSettings.getPreferences().getBoolean(KEY_USER_STOP_REQUESTED, false)

        logi("Initializing service watchdog")

        Shizuku.addBinderReceivedListenerSticky {
            clearUserStopRequest()
        }

        Shizuku.addBinderDeadListener {
            onServiceDied(appContext)
        }
    }

    fun isEnabled(): Boolean {
        return ModuleSettings.isAutoRestartOnCrash() || ModuleSettings.isKeepAlive() || ModuleSettings.isErrorProtectEnabled()
    }

    fun reconcileService(context: Context) {
        WatchdogService.reconcile(context.applicationContext)
    }

    private fun onServiceDied(context: Context) {
        logd("Service died detected by watchdog")

        if (consumeExpectedDeath()) {
            logi("Service death was expected. Resetting expected-death flag.")
            return
        }

        if (ModuleSettings.isNotifyOnServiceDeath()) {
            showDeathNotification(context)
        }

        if (isEnabled()) {
            attemptRestart(context)
        }
    }

    private fun consumeExpectedDeath(): Boolean {
        if (!expectingDeath) return false

        val now = SystemClock.elapsedRealtime()
        val deadline = expectedDeathDeadlineMillis
        expectingDeath = false

        if (deadline == 0L || now <= deadline) {
            return true
        }

        logd("Ignoring stale expected-death flag")
        return false
    }

    private fun clearExpectedDeathWhenStale() {
        if (!expectingDeath) return
        val deadline = expectedDeathDeadlineMillis
        if (deadline != 0L && SystemClock.elapsedRealtime() > deadline) {
            logd("Clearing stale expected-death flag")
            expectingDeath = false
        }
    }

    private fun showDeathNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_watchdog),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_server_error_24dp)
            .setContentTitle(context.getString(R.string.notification_watchdog_title))
            .setContentText(context.getString(R.string.notification_watchdog_text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun clearUserStopRequest() {
        setUserStopRequested(false)
        expectingDeath = false
    }

    private fun setUserStopRequested(value: Boolean) {
        userStopRequested = value
        ShizukuSettings.getPreferences()
            .edit()
            .putBoolean(KEY_USER_STOP_REQUESTED, value)
            .apply()
    }

    private fun isUserStopRequested(): Boolean {
        return userStopRequested || ShizukuSettings.getPreferences().getBoolean(KEY_USER_STOP_REQUESTED, false)
    }

    fun attemptRestart(context: Context) {
        val appContext = context.applicationContext
        clearExpectedDeathWhenStale()

        if (isUserStopRequested()) {
            logi("Skipping watchdog restart because the last stop was user-initiated")
            return
        }

        if (!restartInProgress.compareAndSet(false, true)) {
            logd("Restart already in progress, skipping duplicate watchdog restart")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val lastMode = ShizukuSettings.getLastLaunchMode()
                logi("Attempting to restart service (Last mode: $lastMode)")

                when (lastMode) {
                    LaunchMethod.ROOT -> restartRoot()
                    LaunchMethod.ADB -> restartAdb(appContext)
                    LaunchMethod.DHIZUKU -> restartDhizuku(appContext)
                }
            } finally {
                restartInProgress.set(false)
            }
        }
    }

    fun stopServer(userInitiated: Boolean = true) {
        if (userInitiated) {
            setUserStopRequested(true)
        }
        expectingDeath = true
        try {
            Shizuku.exit()
        } catch (_: Throwable) {
            expectingDeath = false
            if (userInitiated) {
                setUserStopRequested(false)
            }
        }
    }

    private fun restartRoot() {
        try {
            if (!Shell.getShell().isRoot) {
                Shell.getCachedShell()?.close()
            }
            if (Shell.getShell().isRoot) {
                Shell.cmd(Starter.internalCommand).exec()
            }
        } catch (e: Exception) {
            logd("Watchdog root restart failed: ${e.message}")
        }
    }

    private fun restartAdb(context: Context) {
        if (ShizukuSettings.isTcpMode() && restartTcp(context)) {
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            restartWirelessAdb(context)
        }
    }

    private fun restartTcp(context: Context): Boolean {
        val livePort = EnvironmentUtils.getLiveAdbTcpPort()
        val configuredPort = EnvironmentUtils.getAdbTcpPort()
        val candidatePorts = sequenceOf(livePort, configuredPort, 5555)
            .filter { it > 0 }
            .distinct()
            .toList()

        if (candidatePorts.isEmpty()) {
            logd("Restart via TCP skipped: no candidate ADB TCP ports")
            return false
        }

        val key = AdbKey(PreferenceAdbKeyStore(ShizukuSettings.getPreferences()), "shizuku")
        for (port in candidatePorts) {
            try {
                AdbClient("127.0.0.1", port, key).use { client ->
                    client.connect()
                    client.shellCommand(Starter.internalCommand) { _ -> }
                }
                logi("Restart via TCP sent on port $port")
                return true
            } catch (e: Exception) {
                logd("Restart via TCP failed on port $port: ${e.message}")
            }
        }
        return false
    }

    private fun restartWirelessAdb(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false

        var restarted = false
        val startAttempted = AtomicBoolean(false)
        val latch = CountDownLatch(1)
        val adbMdns = AdbMdns(context, AdbMdns.TLS_CONNECT) { port ->
            if (port <= 0 || restarted || !startAttempted.compareAndSet(false, true)) return@AdbMdns
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    AdbStarter.start(port = port, context = context.applicationContext, listener = { _ -> })
                    restarted = true
                    logi("Restart via Wireless ADB successful from discovered port $port")
                } catch (e: Exception) {
                    logd("Restart via Wireless ADB failed on port $port: ${e.message}")
                } finally {
                    latch.countDown()
                }
            }
        }

        return try {
            adbMdns.start()
            latch.await(WIRELESS_ADB_DISCOVERY_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            restarted
        } finally {
            adbMdns.stop()
        }
    }

    private suspend fun waitForShizukuBinder(timeoutMs: Long = 10_000L): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (Shizuku.pingBinder()) return true
            kotlinx.coroutines.delay(500)
        }
        return Shizuku.pingBinder()
    }

    private suspend fun restartDhizuku(context: Context) {
        try {
            logi("Watchdog attempting Dhizuku restart...")
            val initResult = com.rosan.dhizuku.api.Dhizuku.init(context.applicationContext)
            if (!initResult) {
                logd("Dhizuku init failed in watchdog")
                return
            }
            if (!com.rosan.dhizuku.api.Dhizuku.isPermissionGranted()) {
                logd("Dhizuku permission is not granted in watchdog")
                return
            }
            val userServiceArgs = com.rosan.dhizuku.api.DhizukuUserServiceArgs(
                android.content.ComponentName(context.applicationContext, moe.shizuku.manager.dhizuku.DhizukuService::class.java)
            )
            var connection: android.content.ServiceConnection? = null
            try {
                val serviceResult = withTimeoutOrNull(DHIZUKU_BIND_TIMEOUT_MS) {
                    suspendCancellableCoroutine<android.os.IBinder?> { cont ->
                        val conn = object : android.content.ServiceConnection {
                            override fun onServiceConnected(name: android.content.ComponentName?, service: android.os.IBinder?) {
                                if (cont.isActive) cont.resumeWith(Result.success(service))
                            }
                            override fun onServiceDisconnected(name: android.content.ComponentName?) {}
                        }
                        connection = conn
                        val bound = com.rosan.dhizuku.api.Dhizuku.bindUserService(userServiceArgs, conn)
                        if (!bound && cont.isActive) {
                            cont.resumeWith(Result.success(null))
                        }
                    }
                }
                if (serviceResult == null) {
                    logd("Dhizuku service binding failed or timed out in watchdog")
                    return
                }
                val dhizukuService = moe.shizuku.manager.dhizuku.IDhizukuService.Stub.asInterface(serviceResult)
                logi("Watchdog executing Shevery starter directly via Dhizuku Device Owner...")
                dhizukuService.runCommand(Starter.internalCommand)
                if (waitForShizukuBinder()) {
                    logi("Watchdog verified Shevery binder after Dhizuku restart")
                } else {
                    logd("Watchdog Dhizuku starter command completed, but binder did not become available")
                    if (ModuleSettings.isNotifyOnServiceDeath()) {
                        showDeathNotification(context)
                    }
                }
            } finally {
                connection?.let { conn ->
                    try {
                        com.rosan.dhizuku.api.Dhizuku.unbindUserService(conn)
                    } catch (e: Exception) { }
                }
            }
        } catch (e: Exception) {
            logd("Watchdog Dhizuku restart failed: ${e.message}")
        }
    }
}
