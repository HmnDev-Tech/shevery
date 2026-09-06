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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
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
import moe.shizuku.server.IShizukuService
import moe.shizuku.manager.starter.Starter
import moe.shizuku.manager.utils.EnvironmentUtils
import moe.shizuku.manager.utils.ShizukuStateMachine
import rikka.shizuku.Shizuku
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object WatchdogManager {

    data class StopResult(
        val exitRequested: Boolean,
        val stopped: Boolean,
        val fallbackAttempted: Boolean = false,
        val error: String? = null
    )

    private const val CHANNEL_ID = "service_watchdog"
    private const val NOTIFICATION_ID = 1001
    private const val EXPECTED_DEATH_WINDOW_MS = 10_000L
    private const val WIRELESS_ADB_DISCOVERY_TIMEOUT_SECONDS = 5L
    private const val DHIZUKU_BIND_TIMEOUT_MS = 10_000L
    private const val KEY_USER_STOP_REQUESTED = "watchdog_user_stop_requested"

    @Volatile
    var isStarterActive = false

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
            expectingDeath = false
        }

        Shizuku.addBinderDeadListener {
            onServiceDied(appContext)
        }
    }

    fun isEnabled(): Boolean {
        return ModuleSettings.isAutoRestartOnCrash() || ModuleSettings.isKeepAlive() || ModuleSettings.isErrorProtectEnabled()
    }

    fun shouldRunService(): Boolean {
        return isEnabled() && !isUserStopRequested()
    }

    fun reconcileService(context: Context) {
        WatchdogService.reconcile(context.applicationContext)
    }

    private fun onServiceDied(context: Context) {
        logd("Service died detected by watchdog")

        if (isStarterActive) {
            logi("Service death occurred while StarterActivity is active. Suppressing watchdog restart.")
            return
        }

        if (consumeExpectedDeath()) {
            logi("Service death was expected. Resetting expected-death flag.")
            return
        }

        if (isUserStopRequested()) {
            logi("Service death came from a user-initiated stop. Suppressing watchdog notification and restart.")
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
            context, 0x7F050001, intent,
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

    fun clearUserStopRequest(context: Context? = null) {
        setUserStopRequested(false)
        expectingDeath = false
        context?.let { WatchdogService.reconcile(it.applicationContext) }
    }

    private fun setUserStopRequested(value: Boolean) {
        userStopRequested = value
        ShizukuSettings.getPreferences()
            .edit()
            .putBoolean(KEY_USER_STOP_REQUESTED, value)
            .apply()
    }

    fun isUserStopRequested(): Boolean {
        return userStopRequested || ShizukuSettings.getPreferences().getBoolean(KEY_USER_STOP_REQUESTED, false)
    }

    fun attemptRestart(context: Context) {
        val appContext = context.applicationContext
        clearExpectedDeathWhenStale()

        if (isStarterActive) {
            logi("Skipping watchdog restart because StarterActivity is active")
            return
        }

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

    fun stopServer(context: Context? = null, userInitiated: Boolean = true) {
        requestStopServer(context, userInitiated)
    }

    fun requestStopServer(context: Context? = null, userInitiated: Boolean = true): Throwable? {
        if (userInitiated) {
            setUserStopRequested(true)
            context?.let { WatchdogService.reconcile(it.applicationContext) }
        }
        expectingDeath = true
        return try {
            Shizuku.exit()
            null
        } catch (e: Throwable) {
            logd("Failed to stop Shevery service through binder exit: ${e.message}")
            expectingDeath = false
            e
        }
    }

    suspend fun stopServerAndWait(
        context: Context? = null,
        userInitiated: Boolean = true,
        timeoutMs: Long = 3_000L
    ): StopResult {
        val exitError = requestStopServer(context, userInitiated)
        if (exitError != null) {
            return StopResult(
                exitRequested = false,
                stopped = !Shizuku.pingBinder(),
                error = exitError.message ?: exitError.javaClass.simpleName
            )
        }

        if (waitUntilBinderStops(timeoutMs)) {
            return StopResult(exitRequested = true, stopped = true)
        }

        val fallbackError = forceStopServerProcess()
        if (fallbackError != null) {
            return StopResult(
                exitRequested = true,
                stopped = !Shizuku.pingBinder(),
                fallbackAttempted = true,
                error = fallbackError
            )
        }

        return StopResult(
            exitRequested = true,
            stopped = waitUntilBinderStops(timeoutMs),
            fallbackAttempted = true
        )
    }

    private suspend fun waitUntilBinderStops(timeoutMs: Long): Boolean {
        return ShizukuStateMachine.awaitStopped(timeoutMs)
    }

    private fun forceStopServerProcess(): String? {
        return try {
            if (!Shizuku.pingBinder()) return null
            val binder = Shizuku.getBinder() ?: return "binder was null"
            val service = IShizukuService.Stub.asInterface(binder)
            val process = service.newProcess(
                arrayOf("sh", "-c", "for pid in $(pidof shizuku_server shevery_server 2>/dev/null); do kill -9 \"\$pid\"; done"),
                null,
                null
            )
            val exitCode = process.waitFor()
            if (exitCode == 0) null else "fallback kill exit code $exitCode"
        } catch (e: Throwable) {
            logd("Failed to force-stop Shevery service process: ${e.message}")
            e.message ?: e.javaClass.simpleName
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

    private suspend fun restartAdb(context: Context) {
        if (ShizukuSettings.isTcpMode() && restartTcp(context)) {
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            restartWirelessAdb(context)
        }
    }

    private suspend fun restartTcp(context: Context): Boolean {
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
                if (waitForShizukuBinder()) {
                    logi("Restart via TCP verified on port $port")
                    return true
                }
                logd("Restart via TCP command completed on port $port, but binder did not become available")
            } catch (e: Exception) {
                logd("Restart via TCP failed on port $port: ${e.message}")
            }
        }
        return false
    }

    private suspend fun restartWirelessAdb(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false

        val appContext = context.applicationContext
        val startAttempted = AtomicBoolean(false)
        val result = CompletableDeferred<Boolean>()
        val adbMdns = AdbMdns(appContext, AdbMdns.TLS_CONNECT) { port ->
            if (port <= 0 || result.isCompleted || !startAttempted.compareAndSet(false, true)) return@AdbMdns
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    AdbStarter.start(port = port, context = appContext, listener = { _ -> })
                    if (waitForShizukuBinder()) {
                        logi("Restart via Wireless ADB successful from discovered port $port")
                        result.complete(true)
                    } else {
                        logd("Restart via Wireless ADB command completed on port $port, but binder did not become available")
                        startAttempted.set(false)
                    }
                } catch (e: Exception) {
                    logd("Restart via Wireless ADB failed on port $port: ${e.message}")
                    startAttempted.set(false)
                }
            }
        }

        return try {
            adbMdns.start()
            withTimeoutOrNull(WIRELESS_ADB_DISCOVERY_TIMEOUT_SECONDS * 1000L + 20_000L) {
                result.await()
            } ?: false
        } finally {
            adbMdns.stop()
        }
    }

    private suspend fun waitForShizukuBinder(timeoutMs: Long = 10_000L): Boolean {
        return ShizukuStateMachine.awaitRunning(timeoutMs)
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
