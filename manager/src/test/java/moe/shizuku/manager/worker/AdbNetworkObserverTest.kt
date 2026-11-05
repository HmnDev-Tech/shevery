package moe.shizuku.manager.worker

import androidx.work.WorkInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AdbNetworkObserverTest {
    @Test fun runningWorkWinsOverHistoricalRows() {
        assertEquals(
            WorkInfo.State.RUNNING,
            activeAdbWorkState(listOf(WorkInfo.State.SUCCEEDED, WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED))
        )
    }

    @Test fun pendingWorkWinsOverHistoricalRows() {
        assertEquals(
            WorkInfo.State.BLOCKED,
            activeAdbWorkState(listOf(WorkInfo.State.CANCELLED, WorkInfo.State.BLOCKED))
        )
        assertNull(activeAdbWorkState(listOf(WorkInfo.State.SUCCEEDED, WorkInfo.State.FAILED)))
    }
}
