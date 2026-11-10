package moe.shizuku.manager.utils

import android.util.Log
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import moe.shizuku.manager.AppConstants
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.adb.AdbArm
import moe.shizuku.manager.application
import moe.shizuku.manager.service.WatchdogManager
import rikka.shizuku.Shizuku

/**
 * Centralized state machine for the Shizuku server lifecycle.
 *
 * Replaces scattered pingBinder() checks with a single source of truth.
 * State transitions are driven by binder received/dead listeners and explicit
 * set() calls. UI can observe via [asFlow] for reactive state updates.
 *
 * States:
 * - STARTING: Server is being launched (root or ADB)
 * - RUNNING: Binder is alive (pingBinder() returns true)
 * - STOPPING: Stop has been requested, waiting for binder to die
 * - STOPPED: Binder is dead, stop was expected (or initial state)
 * - CRASHED: Binder died unexpectedly (not from an explicit stop)
 */
object ShizukuStateMachine {

    enum class State { STARTING, RUNNING, STOPPING, STOPPED, CRASHED }

    private val TAG = AppConstants.TAG

    private val state = AtomicReference<State>(State.STOPPED)
    private val listeners = CopyOnWriteArrayList<(State) -> Unit>()
    private var listenersRegistered = false

    init {
        registerListeners()
        if (Shizuku.pingBinder()) {
            state.set(State.RUNNING)
        }
    }

    private fun registerListeners() {
        if (listenersRegistered) return
        listenersRegistered = true
        Shizuku.addBinderReceivedListenerSticky(
            Shizuku.OnBinderReceivedListener { set(State.RUNNING) }
        )
        Shizuku.addBinderDeadListener(
            Shizuku.OnBinderDeadListener { setDead() }
        )
    }

    fun get(): State = state.get()

    private fun transitionAtomic(transform: (State) -> State) {
        var oldState: State
        var newState: State
        do {
            oldState = state.get()
            newState = transform(oldState)
            if (oldState == newState) return
        } while (!state.compareAndSet(oldState, newState))

        Log.i("Shizuku", "State transition: $oldState -> $newState")
        java.util.ArrayList<(State) -> Unit>().apply {
            listeners.forEach { add(it) }
        }.forEach { it(newState) }
    }

    fun set(newState: State) = transitionAtomic { newState }

    /**
     * Pre-start lifecycle coordinator.
     * If the server is currently RUNNING or pingBinder() is true, this is an intentional restart:
     * sets [WatchdogManager.expectingDeath] to true to swallow the upcoming SIGKILL binder death,
     * and sets state to [State.STARTING].
     * If the server was STOPPED, moves to [State.STARTING] with expectingDeath = false.
     */
    fun onStartRequested() {
        if (isRunning() || Shizuku.pingBinder()) {
            WatchdogManager.expectingDeath = true
        }
        set(State.STARTING)
    }

    /**
     * Fallback when a starter fails or times out.
     * Reconciles state to STOPPED (if pingBinder is false) or RUNNING (if binder arrived).
     */
    fun onStartFailed() {
        if (Shizuku.pingBinder()) {
            set(State.RUNNING)
        } else {
            set(State.STOPPED)
        }
    }

    /**
     * Pre-stop lifecycle coordinator.
     * Transitions state to [State.STOPPING] when a user or system stop is requested.
     */
    fun onStopRequested() {
        set(State.STOPPING)
    }

    /**
     * Called when the binder dies. If a stop was requested (explicit STOPPING or
     * userStopRequested), disarm USB debugging if configured and transition to STOPPED.
     * If the server was RUNNING without a stop request, it crashed unless an expected
     * restart death is active ([WatchdogManager.isExpectingDeathActive]).
     */
    internal fun setDead() = transitionAtomic {
        when {
            it == State.STOPPING || WatchdogManager.isUserStopRequested() -> {
                try {
                    val appContext = application.applicationContext
                    if (ShizukuSettings.getAutoDisableUsbDebugging()) {
                        // WRITE_SECURE_SETTINGS or Device Owner — AdbArm picks the path.
                        AdbArm.disarmUsbDebugging(appContext)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to auto-disable USB debugging on stop", e)
                }
                State.STOPPED
            }
            it == State.RUNNING -> {
                if (WatchdogManager.isExpectingDeathActive()) {
                    State.STARTING
                } else {
                    State.CRASHED
                }
            }
            it == State.STARTING -> State.STARTING
            else -> it
        }
    }

    /**
     * Force a state refresh by pinging the binder.
     * Returns the current state after refresh.
     */
    fun update(): State {
        val currentState = if (Shizuku.pingBinder()) State.RUNNING else State.STOPPED
        set(currentState)
        return currentState
    }

    fun isRunning(): Boolean = get() == State.RUNNING

    fun isDead(): Boolean = get() == State.STOPPED || get() == State.CRASHED

    fun addListener(listener: (State) -> Unit) {
        listeners.add(listener)
        listener(state.get())
    }

    fun removeListener(listener: (State) -> Unit) {
        listeners.remove(listener)
    }

    /**
     * Reactive state observation for Compose UI.
     * Emits the current state immediately on collection, then on every transition.
     */
    fun asFlow(): Flow<State> = callbackFlow {
        val listener: (State) -> Unit = { trySend(it).isSuccess }
        addListener(listener)
        awaitClose { removeListener(listener) }
    }

    /**
     * Suspending awaiter that waits until the server enters [State.RUNNING].
     * If the server is already RUNNING (or pingBinder() returns true), completes immediately (0ms).
     * Otherwise suspends on state flow transitions until RUNNING is emitted or [timeoutMs] elapses.
     */
    suspend fun awaitRunning(timeoutMs: Long = 10_000L): Boolean {
        if (isRunning() || Shizuku.pingBinder()) {
            if (!isRunning()) set(State.RUNNING)
            return true
        }
        return withTimeoutOrNull(timeoutMs) {
            asFlow().first { it == State.RUNNING }
            true
        } ?: (isRunning() || Shizuku.pingBinder())
    }

    /**
     * Suspending awaiter that waits until the server stops or crashes.
     * If the server is already STOPPED/CRASHED, completes immediately (0ms).
     * Otherwise suspends on state flow transitions until STOPPED or CRASHED is emitted or [timeoutMs] elapses.
     */
    suspend fun awaitStopped(timeoutMs: Long = 5_000L): Boolean {
        if (isDead() && !Shizuku.pingBinder()) {
            return true
        }
        return withTimeoutOrNull(timeoutMs) {
            asFlow().first { it == State.STOPPED || it == State.CRASHED }
            true
        } ?: (isDead() || !Shizuku.pingBinder())
    }
}
