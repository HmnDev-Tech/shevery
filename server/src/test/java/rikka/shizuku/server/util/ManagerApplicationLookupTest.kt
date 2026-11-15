package rikka.shizuku.server.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.Callable
import java.util.concurrent.atomic.AtomicInteger

class ManagerApplicationLookupTest {

    @Test
    fun transientPackageManagerFailureRecovers() {
        val calls = AtomicInteger()
        val retries = AtomicInteger()
        val application = Any()
        val result = ManagerApplicationLookup.resolve(
            Callable {
                if (calls.incrementAndGet() < 3) {
                    throw IOException("package manager restarting")
                }
                application
            },
            Runnable { retries.incrementAndGet() }
        )
        assertSame(application, result.application)
        assertFalse(result.confirmedMissing)
        assertEquals(3, calls.get())
        assertEquals(2, retries.get())
    }

    @Test
    fun repeatedNullDuringReplacementDoesNotStopWhenPackageReturns() {
        val calls = AtomicInteger()
        val application = Any()
        val result = ManagerApplicationLookup.resolve(
            Callable<Any?> { if (calls.incrementAndGet() < 5) null else application },
            Runnable {}
        )
        assertSame(application, result.application)
        assertFalse(result.confirmedMissing)
    }

    @Test
    fun repeatedExceptionsNeverConfirmUninstallAndRetriesAreBounded() {
        val calls = AtomicInteger()
        val retries = AtomicInteger()
        val failure = IOException("Binder unavailable")
        val result = ManagerApplicationLookup.resolve(
            Callable<Any?> {
                calls.incrementAndGet()
                throw failure
            },
            Runnable { retries.incrementAndGet() }
        )
        assertNull(result.application)
        assertFalse(result.confirmedMissing)
        assertSame(failure, result.failure)
        assertEquals(5, calls.get())
        assertEquals(4, retries.get())
    }

    @Test
    fun onlyConsecutiveSuccessfulAbsenceCanConfirmUninstall() {
        val calls = AtomicInteger()
        val result = ManagerApplicationLookup.resolve(
            Callable<Any?> {
                if (calls.incrementAndGet() == 3) {
                    throw IOException("interrupted")
                }
                null
            },
            Runnable {}
        )
        assertFalse(result.confirmedMissing)
    }

    @Test
    fun repeatedSuccessfulAbsenceConfirmsUninstall() {
        val result = ManagerApplicationLookup.resolve(Callable<Any?> { null }, Runnable {})
        assertNull(result.application)
        assertTrue(result.confirmedMissing)
    }
}
