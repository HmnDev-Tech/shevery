package moe.shizuku.manager.adb

import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Owns both local I/O cleanup and the bounded observation of a dispatched launch. */
internal class AdbLaunchCoordinator(
    private val awaitIdle: suspend () -> Unit,
    private val awaitReady: suspend () -> Boolean,
    private val isRunning: () -> Boolean,
) {
    private val mutex = Mutex()
    @Volatile private var uncertainLaunch = false

    suspend fun <T> withGate(checkAllowed: () -> Unit, operation: suspend (previousRecovered: Boolean) -> T): T = mutex.withLock {
        awaitIdle()
        currentCoroutineContext().ensureActive()
        checkAllowed()
        val previousRecovered = if (uncertainLaunch) {
            // The previous remote command cannot be retracted by closing its socket.
            val recovered = awaitReady()
            uncertainLaunch = false
            currentCoroutineContext().ensureActive()
            checkAllowed()
            recovered || isRunning()
        } else false
        operation(previousRecovered)
    }

    suspend fun launch(skipIfRunning: Boolean, checkAllowed: () -> Unit,
                       operation: suspend (markDispatched: () -> Unit) -> Unit) = withGate(checkAllowed) { previousRecovered ->
        // Also skip a late Binder from the preceding, previously uncertain launch.
        if ((skipIfRunning || previousRecovered) && isRunning()) return@withGate
        val dispatched = AtomicBoolean(false)
        var failure: Exception? = null
        try {
            operation {
                dispatched.set(true)
                uncertainLaunch = true
            }
        } catch (e: Exception) {
            failure = e
        }
        val ready = if (dispatched.get()) {
            // Keep this wait inside the gate even if the caller was cancelled.
            withContext(NonCancellable) { awaitReady() }.also { uncertainLaunch = !it }
        } else false
        currentCoroutineContext().ensureActive()
        checkAllowed()
        failure?.let {
            // A lost shell response is harmless if the actual service became ready.
            if (it is CancellationException || !ready || it !is IOException) throw it
        }
        check(dispatched.get() && ready) { "Starter did not deliver service Binder" }
    }
}
