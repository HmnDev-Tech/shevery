package moe.shizuku.manager.service

import android.content.Context
import android.os.SystemClock
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.ShizukuSettings.LaunchMethod
import moe.shizuku.manager.ktx.logd
import moe.shizuku.manager.ktx.logi
import moe.shizuku.manager.ktx.logw
import moe.shizuku.manager.module.ModuleSettings
import moe.shizuku.manager.nightdog.NightDogClient
import moe.shizuku.manager.starter.Starter
import moe.shizuku.manager.utils.ShizukuStateMachine
import moe.shizuku.manager.worker.AdbStartWorker
import rikka.shizuku.Shizuku
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Client-side companion to NightDog (rikka.shizuku.nightdog.NightDog) and the
 * native starter supervisor (starter.cpp):
 * - Server-side hang detection, system-service rebinding, and process recovery
 *   are handled inside shizuku_server by NightDog and respawned within 5s by
 *   the detached native starter supervisor.
 * - NightDogClient synchronizes the enabled state with shizuku_server over
 *   Binder IPC (BINDER_TRANSACTION_setNightDogEnabled / getNightDogEnabled).
 * - If the binder dies and is not recovered by the native starter supervisor
 *   within [SUPERVISOR_RECOVERY_GRACE_MS], this manager issues a fallback
 *   restart for the last launch mode (root / adb / dhizuku).
 */
object WatchdogManager {

    data class StopResult(
        val exitRequested: Boolean,
        val stopped: Boolean,
        val fallbackAttempted: Boolean = false,
        val error: String? = null
    )

    private const val EXPECTED_DEATH_WINDOW_MS = 30_000L
    private const val MIN_RESTART_INTERVAL_MS = 15_000L
    private const val SUPERVISOR_RECOVERY_GRACE_MS = 8_000L
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

    fun init(context: Context) {
        val appContext = context.applicationContext
        ModuleSettings.migrateLegacyWatchdogPrefs()
        NightDogClient.init()
        if (initialized) return
        initialized = true

        userStopRequested = ShizukuSettings.getPreferences().getBoolean(KEY_USER_STOP_REQUESTED, false)

        logi("Watchdog init (NightDog + native supervisor)")

        Shizuku.addBinderReceivedListenerSticky {
            expectingDeath = false
            setUserStopRequested(false)
            NightDogClient.syncWithServer()
        }

        Shizuku.addBinderDeadListener {
            onServiceDied(appContext)
        }
    }

    fun isEnabled(): Boolean = NightDogClient.isEnabled()

    fun isExpectingDeathActive(): Boolean {
        if (isStarterActive) return true
        if (!expectingDeath) return false
        val deadline = expectedDeathDeadlineMillis
        if (deadline == 0L) return true
        return SystemClock.elapsedRealtime() <= deadline
    }

    fun shouldRunService(): Boolean = isEnabled() && !isUserStopRequested()

    /** Syncs desired NightDog state with shizuku_server. */
    fun reconcileService(context: Context) {
        NightDogClient.setEnabled(ModuleSettings.isWatchdogEnabled())
    }

    private fun onServiceDied(context: Context) {
        logw("Watchdog: service binder died")
        if (isStarterActive) return
        if (consumeExpectedDeath()) return
        if (isUserStopRequested()) return
        if (!ModuleSettings.isWatchdogEnabled()) return

        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            // Give the detached native starter supervisor (5s respawn delay)
            // time to recover shizuku_server before triggering a full client restart.
            delay(SUPERVISOR_RECOVERY_GRACE_MS)
            if (Shizuku.pingBinder() || isStarterActive || isUserStopRequested()) {
                logi("Watchdog: server already recovered by native supervisor")
                return@launch
            }
            attemptRestart(appContext)
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

    fun attemptRestart(context: Context, force: Boolean = false) {
        val appContext = context.applicationContext

        if (isStarterActive) {
            logi("Watchdog: skip restart, starter active")
            return
        }
        if (force) {
            clearUserStopRequest(appContext)
        } else {
            if (Shizuku.pingBinder()) {
                logd("Watchdog: skip restart, service already running")
                return
            }
            if (isUserStopRequested()) {
                logi("Watchdog: skip restart, user stop")
                return
            }
        }

        val lastMode = ShizukuSettings.getLastLaunchMode()
        if (lastMode == LaunchMethod.UNKNOWN) {
            logd("Watchdog: skip restart, never started")
            return
        }

        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastRestartAttemptMs < MIN_RESTART_INTERVAL_MS) {
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
                if (force && Shizuku.pingBinder()) {
                    stopServerAndWait(appContext, userInitiated = false)
                    expectingDeath = false
                }
                logi("Watchdog restart command (mode: $lastMode, force: $force)")
                when (lastMode) {
                    LaunchMethod.ROOT -> restartRoot()
                    LaunchMethod.ADB -> restartAdb(appContext, force)
                    LaunchMethod.DHIZUKU -> restartDhizuku(appContext)
                    else -> logd("Watchdog: unknown mode $lastMode")
                }
                if (ShizukuStateMachine.awaitRunning(15_000L)) {
                    logi("Watchdog: recovered after restart command")
                } else {
                    logw("Watchdog: restart command did not recover binder")
                    ShizukuStateMachine.onStartFailed()
                }
            } finally {
                restartInProgress.set(false)
            }
        }
    }

    fun requestStopServer(context: Context? = null, userInitiated: Boolean = true): Throwable? {
        if (userInitiated) {
            setUserStopRequested(true)
        }
        expectingDeath = true
        ShizukuStateMachine.onStopRequested()
        return try {
            Shizuku.exit()
            null
        } catch (e: Throwable) {
            logd("Watchdog stop failed: ${e.message}")
            expectingDeath = false
            ShizukuStateMachine.update()
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
                ShizukuStateMachine.onStartRequested()
                Shell.cmd(Starter.internalCommand).exec()
            }
        } catch (e: Exception) {
            logd("Watchdog root restart failed: ${e.message}")
            ShizukuStateMachine.onStartFailed()
        }
    }

    private fun restartAdb(context: Context, force: Boolean = false) {
        if (force) {
            AdbStartWorker.enqueue(context.applicationContext)
        } else {
            AdbStartWorker.enqueueIfIdle(context.applicationContext)
        }
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
                ShizukuStateMachine.onStartRequested()
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
