package moe.shizuku.manager.module.update

import java.io.Closeable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/** Register before commit, even when commit delivers a result synchronously. */
internal object InstallStatusAwaiter {
    suspend fun <T : Any> await(timeoutMs: Long, register: ((T) -> Unit) -> Closeable,
                               commit: suspend () -> Unit): T? {
        val result = CompletableDeferred<T>()
        val registration = register { result.complete(it) }
        return try {
            withTimeoutOrNull(timeoutMs) {
                commit()
                result.await()
            }
        } finally {
            registration.close()
        }
    }
}
