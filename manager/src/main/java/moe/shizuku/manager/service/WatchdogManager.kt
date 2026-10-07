package moe.shizuku.manager.service

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.ShizukuSettings.LaunchMethod
import moe.shizuku.manager.ktx.logd
import moe.shizuku.manager.ktx.logi
import moe.shizuku.manager.ktx.logw
import moe.shizuku.manager.module.ModuleSettings
import moe.shizuku.manager.starter.Starter
import moe.shizuku.manager.utils.ShizukuStateMachine
import moe.shizuku.manager.worker.AdbStartWorker
import rikka.shizuku.Shizuku
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Watchdog rewritten from scratch on the NightDog principle
 * (kerneldroid/Nightzuku: heartbeat + 60s poll + restart command).
 *
 * There is intentionally nothing else here:
 * - heartbeat via [beat], updated on every binder-received;
 * - a single HandlerThread polls every 60s;
 * - heartbeat overdue by 60s, or dead binder, issues a restart
 *   command for the last launch mode (root / adb / dhizuku).
 *
 * Hang detection also runs inside shizuku_server itself
 * (rikka.shizuku.server.Watchdog, NightDog principle): on a 60s main-thread
 * hang the server kills its own process, this manager observes binder death
 * and issues the restart command.
 *
 * No foreground service, no persistent notification, no death/recovery
 * notifications, no force-kill, no StateFlow loops.
 */
object WatchdogManager {

    data class StopResult(
        val exitRequested: Boolean,
        val stopped: Boolean,
        val fallbackAttempted: Boolean = false,
        val error: String? = null
    )

    private const val HEARTBEAT_TIMEOUT_MS = 60_000L
    private const val POLL_INTERVAL_MS = 60_000L
    private const val EXPECTED_DEATH_WINDOW_MS = 30_000L
    private const val MIN_RESTART_INTERVAL_MS = 15_000L
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
    private var lastRestartAttemptMs = 0L

    @Volatile
    private var userStopRequested = false

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

        logi("Watchdog init (heartbeat only)")

        Shizuku.addBinderReceivedListenerSticky {
            beat()
            expectingDeath = false
            setUserStopRequested(false)
        }

        Shizuku.addBinderDeadListener {
            onServiceDied(appContext)
        }

        if (shouldRunService()) startMonitor() else stopMonitor()
    }

    fun isEnabled(): Boolean = ModuleSettings.isWatchdogEnabled()

    /** NightDog heartbeat: refresh on every binder-received. */
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

    /** Starts/stops the single HandlerThread monitor. No service, no notification. */
    fun reconcileService(context: Context) {
        if (shouldRunService()) startMonitor() else stopMonitor()
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
        logi("Watchdog monitor started")
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

    private fun checkHealth() {
        if (!shouldRunService()) return
        if (isExpectingDeathActive()) return
        if (isUserStopRequested()) return
        if (ShizukuSettings.getLastLaunchMode() == LaunchMethod.UNKNOWN) return

        val beat = lastBeatMs.get()
        if (beat > 0 && SystemClock.elapsedRealtime() - beat <= HEARTBEAT_TIMEOUT_MS) {
            if (Shizuku.pingBinder()) return
        }

        val overdue = beat > 0 && SystemClock.elapsedRealtime() - beat > HEARTBEAT_TIMEOUT_MS
        if (!overdue && Shizuku.pingBinder()) return

        if (overdue) {
            logw("Watchdog: heartbeat overdue, restart command")
        } else {
            logw("Watchdog: binder dead, restart command")
        }
        try {
            attemptRestart(moe.shizuku.manager.application.applicationContext)
        } catch (e: Throwable) {
            logd("Watchdog poll restart failed: ${e.message}")
        }
    }

    private fun onServiceDied(context: Context) {
        logw("Watchdog: service died")
        if (isStarterActive) return
        if (consumeExpectedDeath()) return
        if (isUserStopRequested()) return
        if (!isEnabled()) return
        attemptRestart(context)
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

    fun clearUserStopRequest(context: Context? = null) {
        setUserStopRequested(false)
        expectingDeath = false
        context?.let { reconcileService(it.applicationContext) }
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

    /** Kill is not enough: issue a restart command, then verify the binder is back. */
    fun attemptRestart(context: Context) {
        val appContext = context.applicationContext

        if (isStarterActive) {
            logi("Watchdog: skip restart, starter active")
            return
        }
        if (isUserStopRequested()) {
            logi("Watchdog: skip restart, user stop")
            return
        }

        val lastMode = ShizukuSettings.getLastLaunchMode()
        if (lastMode == LaunchMethod.UNKNOWN) {
            logd("Watchdog: skip restart, never started")
            return
        }

        val now = SystemClock.elapsedRealtime()
        if (now - lastRestartAttemptMs < MIN_RESTART_INTERVAL_MS) {
            logd("Watchdog: skip restart, cooldown")
            return
        }
        if (!restartInProgress.compareAndSet(false, true)) {
            logd("Watchdog: restart already in progress")
            return
        }
        lastRestartAttemptMs = now

        CoroutineScope(Dispatchers.IO).launch {
            try {
                logi("Watchdog restart command (mode: $lastMode)")
                when (lastMode) {
                    LaunchMethod.ROOT -> restartRoot()
                    LaunchMethod.ADB -> restartAdb(appContext)
                    LaunchMethod.DHIZUKU -> restartDhizuku(appContext)
                    else -> logd("Watchdog: unknown mode $lastMode")
                }
                if (ShizukuStateMachine.awaitRunning(15_000L)) {
                    logi("Watchdog: recovered after restart command")
                    beat()
                } else {
                    logw("Watchdog: restart command did not recover binder")
                }
            } finally {
                restartInProgress.set(false)
            }
        }
    }

    fun requestStopServer(context: Context? = null, userInitiated: Boolean = true): Throwable? {
        if (userInitiated) {
            setUserStopRequested(true)
            context?.let { reconcileService(it.applicationContext) }
        }
        expectingDeath = true
        return try {
            Shizuku.exit()
            null
        } catch (e: Throwable) {
            logd("Watchdog stop failed: ${e.message}")
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
            val initResult = com.rosan.dhizuku.api.Dhizuku.init(context.applicationContext)
            if (!initResult || !com.rosan.dhizuku.api.Dhizuku.isPermissionGranted()) {
                logd("Watchdog: Dhizuku not ready")
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
                } ?: return
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
