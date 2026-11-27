package moe.shizuku.manager.guide

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShakeGestureTest {
    private val gesture = ShakeGesture()

    @Test
    fun requiresThreeDistinctPeaksAtOrAboveThreshold() {
        assertFalse(peak(0, 1.89f))
        assertFalse(peak(200, 1.89f))
        assertFalse(peak(300, Float.NaN))
        assertFalse(peak(400, 1.9f))
        assertFalse(peak(600, 1.9f))
        assertTrue(peak(800, 1.9f))
    }

    @Test
    fun sustainedAccelerationAndSmallDipsDoNotCreateNewPeaks() {
        assertFalse(peak(0))
        assertFalse(gesture.onSample(2.2f, 150))
        assertFalse(gesture.onSample(1.5f, 200))
        assertFalse(gesture.onSample(2.2f, 300))
        assertFalse(peak(500))
        assertTrue(peak(700))
    }

    @Test
    fun rejectsPeaksLessThanOneHundredMillisecondsApart() {
        assertFalse(peak(0))
        assertFalse(peak(99))
        assertFalse(peak(100))
        assertTrue(peak(200))
    }

    @Test
    fun expiresOldPeaksAndKeepsRecentPeaksInRollingWindow() {
        assertFalse(peak(0))
        assertFalse(peak(900))
        assertFalse(peak(1_001))
        assertTrue(peak(1_900))
    }

    @Test
    fun cooldownSurvivesResetAndDiscardsPeaksUntilItExpires() {
        assertFalse(peak(0))
        assertFalse(peak(100))
        assertTrue(peak(200))
        gesture.reset()

        assertFalse(peak(700))
        assertFalse(peak(1_000))
        assertFalse(peak(1_699))
        assertFalse(peak(1_700))
        assertFalse(peak(1_800))
        assertTrue(peak(1_900))
    }

    @Test
    fun resetDiscardsPartialGestureWhenListenerStops() {
        assertFalse(peak(0))
        assertFalse(peak(100))
        gesture.reset()
        assertFalse(peak(200))
        assertFalse(peak(300))
        assertTrue(peak(400))
    }

    private fun peak(timestampMs: Long, gForce: Float = 2.2f): Boolean {
        gesture.onSample(1.0f, timestampMs - 1)
        return gesture.onSample(gForce, timestampMs)
    }
}
