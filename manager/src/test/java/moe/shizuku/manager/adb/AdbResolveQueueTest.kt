package moe.shizuku.manager.adb

import org.junit.Assert.assertEquals
import org.junit.Test

class AdbResolveQueueTest {

    @Test
    fun stoppingOneDiscoveryLeavesOtherCandidatesQueued() {
        val queue = AdbResolveQueue<Pair<Int, String>>()
        queue.addLast(1 to "connect")
        queue.addLast(2 to "pairing")
        queue.addLast(1 to "connect-again")

        queue.removeAll { it.first == 1 }

        assertEquals(1, queue.size())
        assertEquals(2 to "pairing", queue.removeFirst())
    }

    @Test
    fun busyResolverKeepsCurrentCandidateAheadOfBurst() {
        val queue = AdbResolveQueue<Int>()
        (1..99).forEach(queue::addLast)

        repeat(20) {
            val busyCandidate = queue.removeFirst()
            assertEquals(1, busyCandidate)
            queue.retryFirst(busyCandidate)
        }

        assertEquals(1, queue.removeFirst())
        assertEquals(98, queue.size())
        assertEquals((2..99).toList(), generateSequence { if (queue.isEmpty()) null else queue.removeFirst() }.toList())
    }
}
