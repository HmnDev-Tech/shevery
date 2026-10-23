package moe.shizuku.manager.home

import android.os.SystemClock
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.utils.ShizukuStateMachine

/**
 * Process-scoped uptime tracker for the Shevery service.
 *
 * Backed by [ShizukuStateMachine] transitions instead of UI `remember` state so
 * the measured start time survives tab switches, configuration changes, and the
 * service being started while the home tab is not composed at all (watchdog
 * recovery, notification actions, starter screens). A UI-scoped version reset
 * every time the home page left composition, which made the experimental
 * uptime setting restart from zero and look like a no-op.
 *
 * The stamp is additionally mirrored into preferences so closing and reopening
 * the manager does not restart the counter while the same server process is
 * still alive. [SystemClock.elapsedRealtime] makes a reboot invalidate a stale
 * stamp automatically (it is smaller than any pre-reboot value).
 */
object ServiceUptime {

    private const val KEY_SINCE = "exp_service_uptime_since"

    /** Time of the RUNNING transition per [SystemClock.elapsedRealtime], 0 when the server is not running. */
    @Volatile
    @JvmStatic
    var sinceMs: Long = 0L
        private set

    init {
        ShizukuStateMachine.addListener { state ->
            if (state == ShizukuStateMachine.State.RUNNING) {
                if (sinceMs == 0L) {
                    val now = SystemClock.elapsedRealtime()
                    val saved = ShizukuSettings.getPreferences().getLong(KEY_SINCE, 0L)
                    // Restore only a plausible stamp: recorded before "now" and after
                    // boot; anything else (reboot, first launch) means "start counting
                    // from the moment we first see the running server".
                    sinceMs = if (saved in 1L..now) saved else now
                    ShizukuSettings.getPreferences().edit().putLong(KEY_SINCE, sinceMs).apply()
                }
            } else {
                // STARTING/STOPPING/STOPPED/CRASHED all mean "no running server
                // process right now"; dropping the stamp here also clears values
                // left behind by a server that died while the manager was closed.
                sinceMs = 0L
                ShizukuSettings.getPreferences().edit().remove(KEY_SINCE).apply()
            }
        }
    }

    /** Milliseconds the current server process has been running, or 0 when it is not running. */
    fun uptimeMs(): Long {
        val since = sinceMs
        if (since == 0L) return 0L
        return (SystemClock.elapsedRealtime() - since).coerceAtLeast(0L)
    }
}
