package moe.shizuku.manager.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.provider.Settings
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
import moe.shizuku.manager.ktx.logd
import moe.shizuku.manager.ktx.logi
import moe.shizuku.manager.ktx.logw
import moe.shizuku.manager.module.ModuleSettings
import moe.shizuku.manager.receiver.SheveryControlReceiver
import moe.shizuku.manager.starter.Starter
import moe.shizuku.manager.utils.ShizukuStateMachine
import moe.shizuku.manager.worker.AdbStartWorker
import rikka.shizuku.Shizuku
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Lightweight Watchdog inspired by NightDog (kerneldroid/Nightzuku).
 *
 * Principle (NightDog):
 * - server/manager calls [beat] on every binder-received heartbeat;
 * - a single watchdog thread polls every [POLL_INTERVAL_MS];
 * - if heartbeat is overdue by [HEARTBEAT_TIMEOUT_MS], or binder death is
 *   observed, we don't just kill/stop Shevery: we issue a restart command
 *   routed by last launch mode (root / adb / dhizuku).
 *
 * Old heavy methods were deleted: zombie-protect coroutine loops with
 * StateFlow, pidof force-kill via IShizukuService.newProcess, duplicate
 * death/recovery channels. Single HandlerThread + single IO scope only.
 */
object WatchdogManager {

    data class StopResult(
        val exitRequested: Boolean,
        val stopped: Boolean,
        val fallbackAttempted: Boolean = false,
        val error: String? = null
    )

    private const val DEATH_CHANNEL_ID = "service_watchdog_death"
    private const val NOTIFICATION_ID = 1001
    private const val EXPECTED_DEATH_WINDOW_MS = 30_000L
    private const val MIN_RESTART_INTERVAL_MS = 15_000L
    private const val KEY_USER_STOP_REQUESTED = "watchdog_user_stop_requested"

    // NightDog defaults: heartbeat overdue 60s, poll 60s.
    private const val HEARTBEAT_TIMEOUT_MS = 60_000L
    private const val POLL_INTERVAL_MS = 60_000L

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
    private var lastRestartAttemptMs = 0L

    @Volatile
    private var userStopRequested = false

    // NightDog-style heartbeat + single watchdog thread.
    private val lastBeatMs = AtomicLong(0L)
    private var watchdogThread: HandlerThread? = null
    private var watchdogHandler: Handler? = null
    private val monitorStarted = AtomicBoolean(false)
    private val monitorRunnable = object : Runnable {
        override fun run() {
            try {
                checkHealth()
            } finally {
                watchdogHandler?.postDelayed(this, POLL_INTERVAL_MS)
            }
        }
    }

    fun init(context: Context) {
        val appContext = context.applicationContext
        ModuleSettings.migrateLegacyWatchdogPrefs()
        if (initialized) return
        initialized = true

        userStopRequested = ShizukuSettings.getPreferences().getBoolean(KEY_USER_STOP_REQUESTED, false)

        logi("Initializing service watchdog (NightDog-style)")

        Shizuku.addBinderReceivedListenerSticky {
            beat()
            expectingDeath = false
            clearUserStopRequest(appContext)
        }

        Shizuku.addBinderDeadListener {
            onServiceDied(appContext)
        }

        if (shouldRunService()) startMonitor() else stopMonitor()
    }

    fun isEnabled(): Boolean = ModuleSettings.isWatchdogEnabled()

    /** NightDog heartbeat: call when binder is received / healthy. */
    fun beat() {
        lastBeatMs.set(SystemClock.elapsedRealtime())
    }

    fun isExpectingDeathActive(): Boolean {
        if (isStarterActive) return true
        if (!expectingDeath) return false
        val deadline = expectedDeathDeadlineMillis
        if (deadline == 0L) return true
        return SystemClock.elapsedRealtime() <= deadline
    }

    fun shouldRunService(): Boolean = isEnabled() && !isUserStopRequested()

    fun reconcileService(context: Context) {
        if (shouldRunService()) startMonitor() else stopMonitor()
        WatchdogService.reconcile(context.applicationContext)
    }

    private fun startMonitor() {
        if (!monitorStarted.compareAndSet(false, true)) return
        stopMonitorThread()
        val thread = HandlerThread("SheveryWatchdog").apply { start() }
        watchdogThread = thread
        val handler = Handler(thread.looper)
        watchdogHandler = handler
        if (lastBeatMs.get() == 0L && Shizuku.pingBinder()) beat()
        handler.postDelayed(monitorRunnable, POLL_INTERVAL_MS)
        logi("Watchdog monitor started (poll=${POLL_INTERVAL_MS}ms)")
    }

    private fun stopMonitor() {
        if (!monitorStarted.compareAndSet(true, false)) {
            stopMonitorThread()
            return
        }
        stopMonitorThread()
        logi("Watchdog monitor stopped")
    }

    private fun stopMonitorThread() {
        try {
            watchdogHandler?.removeCallbacks(monitorRunnable)
        } catch (_: Throwable) {
        }
        watchdogHandler = null
        try {
            watchdogThread?.quitSafely()
        } catch (_: Throwable) {
        }
        watchdogThread = null
    }

    /** Single 60s poll: heartbeat overdue or ping failure -> restart command. */
    private fun checkHealth() {
        if (!shouldRunService()) return
        if (isExpectingDeathActive()) return
        if (isUserStopRequested()) return
        if (ShizukuSettings.getLastLaunchMode() == LaunchMethod.UNKNOWN) return

        val beat = lastBeatMs.get()
        if (beat > 0 && SystemClock.elapsedRealtime() - beat <= HEARTBEAT_TIMEOUT_MS) {
            // Fresh heartbeat; verify binder still answers (NightDog fallback ping).
            if (Shizuku.pingBinder()) return
        }

        val overdue = beat > 0 && SystemClock.elapsedRealtime() - beat > HEARTBEAT_TIMEOUT_MS
        val dead = !Shizuku.pingBinder()
        if (!overdue && !dead) return

        if (overdue) {
            logw("Watchdog: heartbeat overdue, issuing restart command")
        } else {
            logw("Watchdog: binder dead on poll, issuing restart command")
        }
        try {
            val app = moe.shizuku.manager.application.applicationContext
            attemptRestart(app)
        } catch (e: Throwable) {
            logd("Watchdog poll restart failed: ${e.message}")
        }
    }

    private fun onServiceDied(context: Context) {
        logw("Service died detected by watchdog")

        if (isStarterActive) {
            logi("Service death while StarterActivity is active. Suppressing restart.")
            return
        }

        if (consumeExpectedDeath()) {
            logi("Service death was expected.")
            return
        }

        if (isUserStopRequested()) {
            logi("Service death from user-initiated stop. Suppressing restart.")
            return
        }

        if (ModuleSettings.isNotifyOnServiceDeath() || isEnabled()) {
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
        if (deadline == 0L || now <= deadline) return true
        logd("Ignoring stale expected-death flag")
        return false
    }

    private fun showDeathNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                DEATH_CHANNEL_ID,
                context.getString(R.string.notification_channel_watchdog_death),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        val launchIntent = Intent(context, MainActivity::class.java)
        val launchPendingIntent = PendingIntent.getActivity(
            context, 0x7F050001, launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val restartIntent = Intent(context, SheveryControlReceiver::class.java).apply {
            action = SheveryControlReceiver.ACTION_START_SERVER
        }
        val restartPendingIntent = PendingIntent.getBroadcast(
            context, 0x7F050003, restartIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val nb = NotificationCompat.Builder(context, DEATH_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_server_error_24dp)
            .setContentTitle(context.getString(R.string.notification_watchdog_title))
            .setContentText(context.getString(R.string.notification_watchdog_text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(launchPendingIntent)
            .setAutoCancel(true)
            .addAction(
                R.drawable.ic_server_ok_24dp,
                context.getString(R.string.home_root_button_restart),
                restartPendingIntent
            )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelSettingsIntent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                putExtra(Settings.EXTRA_CHANNEL_ID, DEATH_CHANNEL_ID)
            }
            val channelSettingsPendingIntent = PendingIntent.getActivity(
                context, 0x7F050004, channelSettingsIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            nb.addAction(
                0,
                context.getString(R.string.watchdog_action_channel_settings),
                channelSettingsPendingIntent
            )
        }

        notificationManager.notify(NOTIFICATION_ID, nb.build())
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

    fun showDeathNotificationPublic(context: Context) = showDeathNotification(context)

    fun showRecoveryNotificationIfEnabled(context: Context) {
        if (!ModuleSettings.isNotifyOnRecovery()) return
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                DEATH_CHANNEL_ID,
                context.getString(R.string.notification_channel_watchdog_death),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0x7F050002, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, DEATH_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_server_ok_24dp)
            .setContentTitle(context.getString(R.string.notification_recovery_title))
            .setContentText(context.getString(R.string.notification_recovery_text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID + 1, notification)
    }

    suspend fun waitForBinder(timeoutMs: Long = 10_000L): Boolean =
        ShizukuStateMachine.awaitRunning(timeoutMs)

    /**
     * NightDog upgrade: instead of only killing/stopping Shevery,
     * issue a restart command for the last launch mode, then verify recovery.
     */
    fun attemptRestart(context: Context) {
        val appContext = context.applicationContext

        if (isStarterActive) {
            logi("Skipping watchdog restart: StarterActivity is active")
            return
        }
        if (isUserStopRequested()) {
            logi("Skipping watchdog restart: user-initiated stop")
            return
        }

        val lastMode = ShizukuSettings.getLastLaunchMode()
        if (lastMode == LaunchMethod.UNKNOWN) {
            logd("Skipping watchdog restart: server was never started")
            return
        }

        val now = SystemClock.elapsedRealtime()
        if (now - lastRestartAttemptMs < MIN_RESTART_INTERVAL_MS) {
            logd("Skipping watchdog restart: cooldown active")
            return
        }
        if (!restartInProgress.compareAndSet(false, true)) {
            logd("Restart already in progress, skipping duplicate")
            return
        }
        lastRestartAttemptMs = now

        CoroutineScope(Dispatchers.IO).launch {
            try {
                logi("Watchdog restart command (last mode: $lastMode)")
                when (lastMode) {
                    LaunchMethod.ROOT -> restartRoot()
                    LaunchMethod.ADB -> restartAdb(appContext)
                    LaunchMethod.DHIZUKU -> restartDhizuku(appContext)
                    else -> logd("Unknown last mode $lastMode")
                }

                val recovered = ShizukuStateMachine.awaitRunning(15_000L)
                if (recovered) {
                    logi("Watchdog: Shevery service recovered after restart command")
                    beat()
                    showRecoveryNotificationIfEnabled(appContext)
                } else {
                    logw("Watchdog: restart command did not recover binder")
                    if (ModuleSettings.isNotifyOnServiceDeath()) {
                        showDeathNotification(appContext)
                    }
                }
            } finally {
                restartInProgress.set(false)
            }
        }
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
            logd("Failed to stop Shevery service: ${e.message}")
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
        val stopped = ShizukuStateMachine.awaitStopped(timeoutMs)
        return StopResult(exitRequested = true, stopped = stopped)
    }

    private fun restartRoot() {
        try {
            if (!Shell.getShell().isRoot) {
                Shell.getCachedShell()?.close()
            }
            if (Shell.getShell().isRoot) {
                ShizukuStateMachine.set(ShizukuStateMachine.State.STARTING)
                Shell.cmd(Starter.internalCommand).exec()
            }
        } catch (e: Exception) {
            logd("Watchdog root restart failed: ${e.message}")
        }
    }

    private fun restartAdb(context: Context) {
        AdbStartWorker.enqueueIfIdle(context.applicationContext)
    }

    private suspend fun restartDhizuku(context: Context) {
        try {
            logi("Watchdog attempting Dhizuku restart...")
            val initResult = com.rosan.dhizuku.api.Dhizuku.init(context.applicationContext)
            if (!initResult || !com.rosan.dhizuku.api.Dhizuku.isPermissionGranted()) {
                logd("Dhizuku not ready for watchdog restart")
                return
            }
            val args = com.rosan.dhizuku.api.DhizukuUserServiceArgs(
                android.content.ComponentName(
                    context.applicationContext,
                    moe.shizuku.manager.dhizuku.DhizukuService::class.java
                )
            )
            var connection: android.content.ServiceConnection? = null
            try {
                val binder = withTimeoutOrNull(10_000L) {
                    suspendCancellableCoroutine<android.os.IBinder?> { cont ->
                        val conn = object : android.content.ServiceConnection {
                            override fun onServiceConnected(name: android.content.ComponentName?, service: android.os.IBinder?) {
                                if (cont.isActive) cont.resumeWith(Result.success(service))
                            }
                            override fun onServiceDisconnected(name: android.content.ComponentName?) {}
                        }
                        connection = conn
                        if (!com.rosan.dhizuku.api.Dhizuku.bindUserService(args, conn) && cont.isActive) {
                            cont.resumeWith(Result.success(null))
                        }
                    }
                }
                if (binder == null) {
                    logd("Dhizuku bind failed in watchdog")
                    return
                }
                val service = moe.shizuku.manager.dhizuku.IDhizukuService.Stub.asInterface(binder)
                ShizukuStateMachine.set(ShizukuStateMachine.State.STARTING)
                service.runCommand(Starter.internalCommand)
            } finally {
                connection?.let {
                    try {
                        com.rosan.dhizuku.api.Dhizuku.unbindUserService(it)
                    } catch (_: Exception) {
                    }
                }
            }
        } catch (e: Exception) {
            logd("Watchdog Dhizuku restart failed: ${e.message}")
        }
    }
}
