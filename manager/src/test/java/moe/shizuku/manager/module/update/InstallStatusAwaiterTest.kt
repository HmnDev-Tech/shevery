package moe.shizuku.manager.module.update

import java.io.Closeable
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class InstallStatusAwaiterTest {
    @Test fun immediateCommitCallbackCannotBeLost() = runBlocking {
        var callback: ((String) -> Unit)? = null
        var closed = false
        val status = InstallStatusAwaiter.await(1_000,
            register = { complete: (String) -> Unit -> callback = complete; Closeable { closed = true } },
            commit = { checkNotNull(callback)("confirmation") })
        assertEquals("confirmation", status)
        assertTrue(closed)
    }

    @Test fun timeoutReturnsFailureSignalAndUnregisters() = runBlocking {
        var closed = false
        val status = InstallStatusAwaiter.await<String>(25,
            register = { Closeable { closed = true } }, commit = {})
        assertNull(status)
        assertTrue(closed)
    }

    @Test fun externalCancellationPropagatesAndUnregisters() = runBlocking {
        val registered = CompletableDeferred<Unit>()
        var closed = false
        val job = launch {
            InstallStatusAwaiter.await<String>(10_000,
                register = { registered.complete(Unit); Closeable { closed = true } }, commit = {})
            fail("Cancellation was swallowed")
        }
        registered.await()
        job.cancelAndJoin()
        assertTrue(closed)
    }

    @Test fun failedCommitUnregisters() = runBlocking {
        var closed = false
        try {
            InstallStatusAwaiter.await<String>(1_000,
                register = { Closeable { closed = true } }, commit = { error("commit failed") })
            fail("Commit failure was swallowed")
        } catch (_: IllegalStateException) { }
        assertTrue(closed)
    }
}
