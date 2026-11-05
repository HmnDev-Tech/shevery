package moe.shizuku.manager.adb

import java.io.Closeable
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException

/** Closing the resource unblocks socket I/O; interrupting a coroutine alone does not. */
internal object CancellableIo {
    private data class Operation(val owner: Any?, val finished: CompletableDeferred<Unit>)
    private val active = java.util.concurrent.ConcurrentHashMap<Closeable, Operation>()
    private const val CLEANUP_TIMEOUT_MS = 5_000L
    private val executor = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "adb-io").apply { isDaemon = true }
    }

    fun closeActive(owner: Any? = null) {
        active.entries.toList().filter { owner == null || it.value.owner == owner }
            .forEach { runCatching { it.key.close() } }
    }

    /** Called under the launch gate. A stuck old operation must prevent a new command. */
    suspend fun awaitIdle() {
        val finished = withTimeoutOrNull(CLEANUP_TIMEOUT_MS) {
            active.values.toList().forEach { it.finished.await() }
            true
        } ?: false
        if (!finished) throw IOException("Previous ADB I/O has not stopped; refusing another command")
    }

    suspend fun <T> run(resource: Closeable, owner: Any? = null,
                        operation: (checkActive: () -> Unit) -> T): T {
        val finished = CompletableDeferred<Unit>()
        try {
            return suspendCancellableCoroutine { continuation ->
                active[resource] = Operation(owner, finished)
                continuation.invokeOnCancellation { runCatching { resource.close() } }
                try {
                    executor.execute {
                        try {
                            val checkActive = {
                                if (!continuation.isActive) throw CancellationException("ADB operation cancelled")
                            }
                            checkActive()
                            val result = operation(checkActive)
                            checkActive()
                            continuation.resume(result)
                        } catch (e: Throwable) {
                            if (continuation.isActive) continuation.resumeWithException(e)
                        } finally {
                            runCatching { resource.close() }
                            active.remove(resource)
                            finished.complete(Unit)
                        }
                    }
                } catch (e: Exception) {
                    runCatching { resource.close() }
                    active.remove(resource)
                    finished.complete(Unit)
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
            }
        } finally {
            // Cancellation resumes the continuation before the executor unwinds.
            // Keep the caller's launch gate until cleanup, with a bounded wait.
            // If cleanup stalls, awaitIdle() keeps later launches from sending commands.
            withContext(NonCancellable) {
                withTimeoutOrNull(CLEANUP_TIMEOUT_MS) { finished.await() }
            }
        }
    }
}
