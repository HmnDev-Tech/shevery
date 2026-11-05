package moe.shizuku.manager.adb

import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class CancellableIoTest {
    @Test fun cancellationClosesBlockedSocketBeforeItCanSendACommand() = runBlocking {
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            val client = Socket()
            client.connect(server.localSocketAddress)
            server.accept().use { peer ->
                val reading = CountDownLatch(1)
                val finished = CountDownLatch(1)
                val sent = AtomicBoolean(false)
                val job = launch(Dispatchers.Default) {
                    CancellableIo.run(client) { checkActive ->
                        try {
                            reading.countDown()
                            client.getInputStream().read()
                            checkActive()
                            sent.set(true)
                            client.getOutputStream().write(1)
                        } finally { finished.countDown() }
                    }
                }
                assertTrue(reading.await(2, TimeUnit.SECONDS))
                job.cancelAndJoin()
                assertTrue(finished.await(2, TimeUnit.SECONDS))
                assertTrue(client.isClosed)
                assertFalse(sent.get())
                peer.soTimeout = 2_000
                assertEquals(-1, peer.getInputStream().read())
            }
        }
    }

    @Test fun successfulIoClosesResource() = runBlocking {
        val closed = AtomicBoolean(false)
        assertEquals(42, CancellableIo.run(Closeable { closed.set(true) }) { check -> check(); 42 })
        assertTrue("Returning from I/O must include resource cleanup", closed.get())
    }

    @Test fun sourceCancellationDoesNotCloseAnIndependentManualConnection() = runBlocking {
        val entered = CountDownLatch(2)
        val manualRelease = CountDownLatch(1)
        val autoClosed = CountDownLatch(1)
        val manualClosed = AtomicBoolean(false)
        val automatic = launch(Dispatchers.Default) {
            CancellableIo.run(Closeable { autoClosed.countDown() }, "WATCHDOG") {
                entered.countDown()
                check(autoClosed.await(3, TimeUnit.SECONDS))
            }
        }
        val manual = launch(Dispatchers.Default) {
            CancellableIo.run(Closeable { manualClosed.set(true) }, "MANUAL") {
                entered.countDown()
                check(manualRelease.await(3, TimeUnit.SECONDS))
            }
        }
        try {
            check(entered.await(3, TimeUnit.SECONDS))
            CancellableIo.closeActive("WATCHDOG")
            withTimeout(3_000) { automatic.join() }
            assertFalse(manualClosed.get())
        } finally {
            manualRelease.countDown()
            manual.join()
        }
    }
}
