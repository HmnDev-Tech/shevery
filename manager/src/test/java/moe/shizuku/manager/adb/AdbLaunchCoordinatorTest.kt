package moe.shizuku.manager.adb

import java.io.Closeable
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class AdbLaunchCoordinatorTest {
    @Test fun cancellationAtWriteBoundaryDoesNotObserveANonexistentRemoteLaunch() = runBlocking {
        var observations = 0
        val coordinator = AdbLaunchCoordinator({}, { observations++; true }, { false })
        val output = java.io.ByteArrayOutputStream()
        try {
            coordinator.launch(false, {}) { mark ->
                AdbCommandDispatch.write(output, byteArrayOf(1),
                    checkAllowed = { throw CancellationException("intent disabled") }, mark)
            }
            fail("Cancelled intent must not be sent")
        } catch (_: CancellationException) { }
        assertEquals(0, output.size())
        assertEquals(0, observations)
        coordinator.launch(false, {}) { mark ->
            AdbCommandDispatch.write(output, byteArrayOf(2), {}, mark)
        }
        assertEquals(1, observations)
        assertArrayEquals(byteArrayOf(2), output.toByteArray())
    }

    @Test fun writeAcceptedThenFailedStillObservesRemoteBinder() = runBlocking {
        var observations = 0
        val accepted = java.io.ByteArrayOutputStream()
        val output = object : java.io.OutputStream() {
            override fun write(value: Int) { accepted.write(value) }
            override fun flush() { throw IOException("response lost after accepted write") }
        }
        val coordinator = AdbLaunchCoordinator({}, { observations++; true }, { false })
        coordinator.launch(false, {}) { mark ->
            AdbCommandDispatch.write(output, byteArrayOf(1, 2), {}, mark)
        }
        assertArrayEquals(byteArrayOf(1, 2), accepted.toByteArray())
        assertEquals(1, observations)
    }

    @Test fun cancellationKeepsGateThroughIoCleanupAndRemoteObservation() = runBlocking {
        val ioEntered = CountDownLatch(1)
        val socketClosed = CountDownLatch(1)
        val releaseIo = CountDownLatch(1)
        val observationEntered = CompletableDeferred<Unit>()
        val releaseObservation = CompletableDeferred<Boolean>()
        val secondEntered = AtomicBoolean(false)
        val coordinator = AdbLaunchCoordinator({ CancellableIo.awaitIdle() }, {
            observationEntered.complete(Unit)
            releaseObservation.await()
        }, { false })
        val first = launch(Dispatchers.Default) {
            coordinator.launch(false, {}) { dispatched ->
                CancellableIo.run(Closeable { socketClosed.countDown() }) {
                    dispatched()
                    ioEntered.countDown()
                    try { check(socketClosed.await(3, TimeUnit.SECONDS)) }
                    finally { check(releaseIo.await(3, TimeUnit.SECONDS)) }
                }
            }
        }
        check(ioEntered.await(3, TimeUnit.SECONDS))
        first.cancel()
        val second = launch(Dispatchers.Default) {
            coordinator.withGate({}) { secondEntered.set(true) }
        }
        try {
            check(socketClosed.await(3, TimeUnit.SECONDS))
            assertFalse(first.isCompleted)
            assertFalse(secondEntered.get())
            releaseIo.countDown()
            withTimeout(3_000) { observationEntered.await() }
            assertFalse(first.isCompleted)
            assertFalse(secondEntered.get())
            releaseObservation.complete(true)
            withTimeout(3_000) { first.join(); second.join() }
            assertTrue(secondEntered.get())
        } finally {
            releaseIo.countDown()
            releaseObservation.complete(true)
            first.cancelAndJoin()
            second.cancelAndJoin()
        }
    }

    @Test fun lateBinderAfterUncertainCommandPreventsAnotherDispatchEvenForManualStart() = runBlocking {
        var running = false
        var observations = 0
        var commands = 0
        val coordinator = AdbLaunchCoordinator({}, {
            observations++
            if (observations > 1) running = true
            running
        }, { running })
        try {
            coordinator.launch(false, {}) { dispatched -> commands++; dispatched(); throw IOException("response lost") }
            fail("First result must remain uncertain")
        } catch (_: IOException) { }
        coordinator.launch(false, {}) { commands++; it() }
        assertEquals(1, commands)
        assertEquals(2, observations)
    }

    @Test fun stuckPreviousIoRefusesNewCommand() = runBlocking {
        var commandSent = false
        val coordinator = AdbLaunchCoordinator({ throw IOException("old I/O still active") }, { false }, { false })
        try {
            coordinator.launch(false, {}) { commandSent = true; it() }
            fail("Must refuse overlapping I/O")
        } catch (_: IOException) { }
        assertFalse(commandSent)
    }
}
