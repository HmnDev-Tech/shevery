package moe.shizuku.manager.worker

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.receiver.ShizukuReceiverStarter
import rikka.shizuku.Shizuku

/**
 * Safety net for the "boot on cellular, no auto-restart on Wi-Fi" bug.
 *
 * The boot worker carries an UNMETERED constraint so WorkManager resumes it
 * when Wi-Fi returns — but only while the work is still pending. Work that
 * already terminally failed (older app versions, user-cancelled retries) stays
 * dead. This observer re-enqueues an idle start worker whenever an unmetered
 * network becomes available, so returning to Wi-Fi restarts Shevery even in
 * those stranded states.

 */
object AdbNetworkObserver {

    private const val TAG = "AdbNetworkObserver"

    @Volatile
    private var registered = false

    /** Suppresses back-to-back onAvailable flaps racing the constraint-unblock. */
    @Volatile
    private var lastTriggerMs = 0L
    private const val DEBOUNCE_MS =  5_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Strong ref: ConnectivityManager does not retain the callback; an
     *  anonymous instance would be GC'd and silently kill the observer. */
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    /** Never unregistered: process-scoped singleton, registered once at
     *  Application.onCreate; process death cleans it up,and the
     *  registered flag guards double-observation on re-entry. */
    fun register(app: Application) {
        // Wireless debugging needs API 30+; on older devices there is
        // nothing to observe, so skip installing the callback entirely.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        if (registered) return
        synchronized(this) {
            if (registered) return
            try {
                val cm = app.getSystemService(ConnectivityManager::class.java) ?: return
                val request = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                    .build()
                val callback = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        onUnmeteredAvailable(app)
                    }
                }
                networkCallback = callback
                cm.registerNetworkCallback(request, callback)
                registered = true
            } catch (e: Exception) {
                // Observing is best-effort; the constrained worker covers the
                Log.w(TAG, "registerNetworkCallback failed", e)
                // normal path on its own.

            }
        }
    }

    private fun onUnmeteredAvailable(app: Application) {
        // Hostile ROMs clear adb_wifi_enabled when wifi drops and never restore
        // it: a live session dies and stays dead. Re-arm after the stack
        // settles (gated by the toggle; idempotent; no-ops when off or flag-1(.
        reArmWifiAfterSettle(app)
        if (Shizuku.pingBinder()) {
            // "awaiting Wi-Fi" banner is definitively stale — clear it. This
            // check runs before the debounce so a no-op binder-up flap doesn't
            // burn the 5s window a real unstarted case would then wait out.

            ShizukuReceiverStarter.updateNotification(
                app.applicationContext,
                ShizukuReceiverStarter.WorkerState.STOPPED
            )
            return
        }
        if (!ShizukuSettings.getStartOnBootAdb()) return
        if (ShizukuSettings.getLastLaunchMode() == ShizukuSettings.LaunchMethod.ROOT) return
        if (moe.shizuku.manager.service.WatchdogManager.isUserStopRequested()) return

        synchronized(this) {
            val now = android.os.SystemClock.elapsedRealtime()
            if (now - lastTriggerMs < DEBOUNCE_MS) return

            lastTriggerMs = now
        }
        scope.launch {
            try {
                // Unmetered Wi-Fi is back: re-enqueue, then post a banner that
                // reflects the actual WorkManager state instead of optimistic RUNNING.
                // Actively RUNNING work is left alone, since REPLACE would cancel a
                // live discovery mid-flight. Everything else is stale now that the
                // network is back: REPLACE cancels the pending wait and starts a
                // fresh attempt immediately, instead of KEEP no-op'ing, which would
                // leave a delayed retry parked until its countdown has expired.
                val state = withTimeout(5_000L) {
                    val states = WorkManager.getInstance(app.applicationContext)
                        .getWorkInfosForUniqueWork(AdbStartWorker.UNIQUE_WORK_NAME)
                        .get()
                        .map { it.state }
                    val state = activeAdbWorkState(states)
                    if (state != WorkInfo.State.RUNNING) {

                        AdbStartWorker.enqueue(app.applicationContext)
                    }

                    when (state) {
                        WorkInfo.State.RUNNING -> ShizukuReceiverStarter.WorkerState.RUNNING
                        WorkInfo.State.BLOCKED -> ShizukuReceiverStarter.WorkerState.AWAITING_WIFI

                        // Post-enqueue race:the fresh row may not be visible yet, or
                        // the constraint hasn't flipped when we read, so fall back to
                        // the worker's own environment-based guess, same as the worker
                        // itself uses when it wakes.
                        else -> AdbStartWorker.bannerStateFor(app.applicationContext)
                }
                }
                ShizukuReceiverStarter.updateNotification(app.applicationContext, state)
            } catch (e: Exception) {
                Log.w(TAG, "onUnmeteredAvailable enqueue failed", e)
            }
        }
    }

    private fun reArmWifiAfterSettle(app: Application) {
        scope.launch {
            delay(3_000)
            WifiDebugReassert.reassertIfEnabled(app.applicationContext)
        }
    }
}

/** Finished rows may precede the currently running unique work. */
internal fun activeAdbWorkState(states: List<WorkInfo.State>): WorkInfo.State? =
    states.firstOrNull { it == WorkInfo.State.RUNNING }
        ?: states.firstOrNull { !it.isFinished }
