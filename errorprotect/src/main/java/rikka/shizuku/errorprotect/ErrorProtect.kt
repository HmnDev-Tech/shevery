package rikka.shizuku.errorprotect

import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.os.ServiceManager
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

object ErrorProtect {

    private const val TAG = "ErrorProtect"

    const val DEFAULT_TIMEOUT_MS = 60_000L
    const val DEFAULT_POLL_INTERVAL_MS = 60_000L
    private const val MIN_CHECK_DELAY_MS = 1_000L
    private const val INITIAL_REBIND_DELAY_MS = 2_000L
    private const val BACKOFF_BASE_MS = 5_000L
    private const val MAX_BACKOFF_MS = 60_000L
    private const val MAX_REBIND_ATTEMPTS = 10

    private val CRITICAL_SERVICES = arrayOf("package", "activity", "user", "appops")
    private val OPTIONAL_SERVICES = arrayOf("permissionmgr")

    interface Listener {
        fun onServiceRecovered(name: String) {}
        fun onFatalFault(reason: String) {}
    }

    private val lock = Any()
    private val started = AtomicBoolean(false)
    private var watchdogThread: HandlerThread? = null
    @Volatile
    private var handler: Handler? = null
    private var healthCheckRunnable: Runnable? = null

    private val lastBeatTime = AtomicLong(0L)
    private val beatCount = AtomicLong(0L)
    private val totalRebinds = AtomicInteger(0)
    private var startTimeMs = 0L
    private var timeoutMs = DEFAULT_TIMEOUT_MS
    private var pollIntervalMs = DEFAULT_POLL_INTERVAL_MS
    @Volatile
    private var listener: Listener? = null

    private val bindings = ConcurrentHashMap<String, IBinder>()
    private val deathRecipients = ConcurrentHashMap<String, IBinder.DeathRecipient>()
    private val rebindAttempts = ConcurrentHashMap<String, Int>()
    private val pendingRebinds = ConcurrentHashMap.newKeySet<String>()

    @JvmStatic
    @JvmOverloads
    fun start(
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        pollIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS,
        listener: Listener? = null
    ) {
        synchronized(lock) {
            if (!started.compareAndSet(false, true)) return

            this.timeoutMs = timeoutMs.coerceAtLeast(5_000L)
            this.pollIntervalMs = pollIntervalMs.coerceAtLeast(5_000L)
            this.listener = listener

            val now = SystemClock.uptimeMillis()
            this.startTimeMs = now
            this.lastBeatTime.set(now)
            this.beatCount.set(0L)

            val thread = HandlerThread("errorprotect").apply {
                isDaemon = true
                start()
            }
            val threadHandler = Handler(thread.looper)
            watchdogThread = thread
            handler = threadHandler

            val checkRunnable = Runnable { checkHealth() }
            healthCheckRunnable = checkRunnable

            threadHandler.post {
                if (!started.get()) return@post
                linkToServices(critical = true, *CRITICAL_SERVICES)
                linkToServices(critical = false, *OPTIONAL_SERVICES)
                val initialDelay = minOf(this.pollIntervalMs, this.timeoutMs)
                threadHandler.postDelayed(checkRunnable, initialDelay)
            }
        }
    }

    @JvmStatic
    fun stop() {
        synchronized(lock) {
            if (!started.compareAndSet(true, false)) return

            val currentHandler = handler
            healthCheckRunnable?.let { currentHandler?.removeCallbacks(it) }
            healthCheckRunnable = null
            currentHandler?.removeCallbacksAndMessages(null)

            unlinkAllServices()
            rebindAttempts.clear()
            pendingRebinds.clear()
            lastBeatTime.set(0L)
            listener = null

            watchdogThread?.quitSafely()
            watchdogThread = null
            handler = null
        }
    }

    @JvmStatic
    fun beat() {
        lastBeatTime.set(SystemClock.uptimeMillis())
        beatCount.incrementAndGet()
    }

    @JvmStatic
    fun isStarted(): Boolean = started.get()

    @JvmStatic
    fun getBoundServiceCount(): Int = bindings.size

    @JvmStatic
    fun getTotalRebinds(): Int = totalRebinds.get()

    private fun unlinkAllServices() {
        for ((name, recipient) in deathRecipients) {
            bindings[name]?.let { binder ->
                try {
                    binder.unlinkToDeath(recipient, 0)
                } catch (_: Exception) {
                }
            }
        }
        bindings.clear()
        deathRecipients.clear()
    }

    private fun unlinkService(name: String) {
        val binder = bindings.remove(name)
        val recipient = deathRecipients.remove(name)
        if (binder != null && recipient != null) {
            try {
                binder.unlinkToDeath(recipient, 0)
            } catch (_: Exception) {
            }
        }
    }

    private fun linkToServices(critical: Boolean, vararg names: String) {
        for (name in names) {
            if (!started.get()) return
            val binder = ServiceManager.getService(name)
            if (binder == null || !binder.pingBinder() || !linkTo(name, binder)) {
                if (critical) {
                    Log.w(TAG, "Initial bind to '$name' unavailable, scheduling rebind")
                    scheduleRebind(name, INITIAL_REBIND_DELAY_MS)
                }
            }
        }
    }

    private fun linkTo(name: String, binder: IBinder): Boolean {
        unlinkService(name)

        val recipient = IBinder.DeathRecipient {
            if (!started.get()) return@DeathRecipient
            Log.e(TAG, "System service died: $name")
            handler?.post {
                if (!started.get()) return@post
                unlinkService(name)
                rebindAttempts.remove(name)
                scheduleRebind(name, INITIAL_REBIND_DELAY_MS)
            }
        }
        return try {
            binder.linkToDeath(recipient, 0)
            bindings[name] = binder
            deathRecipients[name] = recipient
            rebindAttempts.remove(name)
            true
        } catch (e: Exception) {
            Log.e(TAG, "linkToDeath($name) failed", e)
            false
        }
    }

    private fun scheduleRebind(name: String, delayMs: Long) {
        if (!started.get()) return
        if (!pendingRebinds.add(name)) return

        val posted = handler?.postDelayed({
            pendingRebinds.remove(name)
            rebind(name)
        }, delayMs) ?: false

        if (!posted) {
            pendingRebinds.remove(name)
        }
    }

    private fun rebind(name: String) {
        if (!started.get()) return

        val attempts = rebindAttempts.getOrDefault(name, 0)
        val binder = ServiceManager.getService(name)
        if (binder != null && binder.pingBinder() && linkTo(name, binder)) {
            totalRebinds.incrementAndGet()
            Log.i(TAG, "System service recovered: $name (after ${attempts + 1} attempt(s))")
            try {
                listener?.onServiceRecovered(name)
            } catch (t: Throwable) {
                Log.w(TAG, "Listener onServiceRecovered($name) threw", t)
            }
            return
        }

        val nextAttempt = attempts + 1
        if (nextAttempt >= MAX_REBIND_ATTEMPTS) {
            rebindAttempts.remove(name)
            if (name in CRITICAL_SERVICES) {
                Log.e(
                    TAG,
                    "Critical service '$name' failed to recover after $MAX_REBIND_ATTEMPTS attempts; terminating server for starter recovery"
                )
                terminateForRecovery("service_unrecoverable:$name")
            } else {
                Log.w(TAG, "Optional service '$name' did not recover after $MAX_REBIND_ATTEMPTS attempts")
            }
            return
        }

        rebindAttempts[name] = nextAttempt
        val shift = attempts.coerceAtMost(4)
        val delay = minOf(BACKOFF_BASE_MS * (1L shl shift), MAX_BACKOFF_MS)
        Log.w(TAG, "Rebind attempt $nextAttempt/$MAX_REBIND_ATTEMPTS for '$name' failed; retrying in ${delay}ms")
        scheduleRebind(name, delay)
    }

    private fun checkHealth() {
        if (!started.get()) return

        val now = SystemClock.uptimeMillis()
        val beat = lastBeatTime.get()
        var nextCheckDelay = pollIntervalMs

        if (beat > 0L) {
            val elapsed = now - beat
            if (elapsed >= timeoutMs) {
                Log.e(TAG, "Main thread heartbeat overdue by ${elapsed}ms (timeout=${timeoutMs}ms)")
                dumpMainThreadStackTrace()
                terminateForRecovery("heartbeat_timeout:${elapsed}ms")
                return
            }
            val remainingUntilTimeout = (timeoutMs - elapsed).coerceAtLeast(MIN_CHECK_DELAY_MS)
            nextCheckDelay = minOf(pollIntervalMs, remainingUntilTimeout)
        }

        for (name in CRITICAL_SERVICES) {
            if (!started.get()) return
            val binder = bindings[name]
            if (binder == null) {
                if (!pendingRebinds.contains(name)) {
                    scheduleRebind(name, INITIAL_REBIND_DELAY_MS)
                }
                continue
            }
            try {
                if (!binder.pingBinder()) {
                    Log.w(TAG, "System service '$name' unresponsive during fallback check")
                    unlinkService(name)
                    scheduleRebind(name, INITIAL_REBIND_DELAY_MS)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Fallback check failed for '$name'", e)
                unlinkService(name)
                scheduleRebind(name, INITIAL_REBIND_DELAY_MS)
            }
        }

        val runnable = healthCheckRunnable ?: return
        if (started.get()) {
            handler?.postDelayed(runnable, nextCheckDelay)
        }
    }

    private fun dumpMainThreadStackTrace() {
        try {
            val mainThread = Looper.getMainLooper()?.thread ?: return
            val stack = mainThread.stackTrace
            val sb = StringBuilder(512)
            sb.append("Main thread '").append(mainThread.name)
                .append("' (state=").append(mainThread.state).append(") stack trace:\n")
            for (element in stack) {
                sb.append("    at ").append(element).append('\n')
            }
            Log.e(TAG, sb.toString())
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to capture main thread stack trace", t)
        }
    }

    private fun terminateForRecovery(reason: String) {
        try {
            listener?.onFatalFault(reason)
        } catch (_: Throwable) {
        }
        stop()
        Process.killProcess(Process.myPid())
    }
}
