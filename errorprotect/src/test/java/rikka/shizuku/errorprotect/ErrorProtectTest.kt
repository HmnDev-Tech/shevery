package rikka.shizuku.errorprotect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorProtectTest {

    @Test
    fun initialStateAccessorsReportSafeDefaults() {
        assertEquals(60_000L, ErrorProtect.DEFAULT_TIMEOUT_MS)
        assertEquals(60_000L, ErrorProtect.DEFAULT_POLL_INTERVAL_MS)
        assertFalse(ErrorProtect.isStarted())
        assertEquals(0, ErrorProtect.getBoundServiceCount())
        assertTrue(ErrorProtect.getTotalRebinds() >= 0)
    }

    @Test
    fun exponentialBackoffIsBoundedBetweenBaseAndMax() {
        assertEquals(5_000L, ErrorProtect.computeBackoffDelayMs(-1))
        assertEquals(5_000L, ErrorProtect.computeBackoffDelayMs(0))
        assertEquals(10_000L, ErrorProtect.computeBackoffDelayMs(1))
        assertEquals(20_000L, ErrorProtect.computeBackoffDelayMs(2))
        assertEquals(40_000L, ErrorProtect.computeBackoffDelayMs(3))
        assertEquals(60_000L, ErrorProtect.computeBackoffDelayMs(4))
        assertEquals(60_000L, ErrorProtect.computeBackoffDelayMs(99))
    }

    @Test
    fun heartbeatOverdueAndNextDelayFollowWatchdogBounds() {
        assertFalse(ErrorProtect.isHeartbeatOverdue(100_000L, 0L, 60_000L))
        assertFalse(ErrorProtect.isHeartbeatOverdue(159_999L, 100_000L, 60_000L))
        assertTrue(ErrorProtect.isHeartbeatOverdue(160_000L, 100_000L, 60_000L))
        assertTrue(ErrorProtect.isHeartbeatOverdue(200_000L, 100_000L, 60_000L))

        assertEquals(60_000L, ErrorProtect.computeNextCheckDelayMs(100_000L, 0L, 60_000L, 60_000L))
        assertEquals(10_000L, ErrorProtect.computeNextCheckDelayMs(150_000L, 100_000L, 60_000L, 60_000L))
        assertEquals(1_000L, ErrorProtect.computeNextCheckDelayMs(159_500L, 100_000L, 60_000L, 60_000L))
        assertEquals(5_000L, ErrorProtect.computeNextCheckDelayMs(110_000L, 100_000L, 60_000L, 5_000L))
    }

    @Test
    fun criticalServicesTerminateAfterExhaustedRebindsOptionalDoNot() {
        for (service in listOf("package", "activity", "user", "appops")) {
            assertTrue(ErrorProtect.isCriticalService(service))
            assertFalse(ErrorProtect.shouldTerminateOnExhaustedRebinds(service, 9))
            assertTrue(ErrorProtect.shouldTerminateOnExhaustedRebinds(service, 10))
        }

        assertFalse(ErrorProtect.isCriticalService("permissionmgr"))
        assertFalse(ErrorProtect.shouldTerminateOnExhaustedRebinds("permissionmgr", 10))
        assertFalse(ErrorProtect.shouldTerminateOnExhaustedRebinds("permissionmgr", 50))
    }
}
