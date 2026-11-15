package moe.shizuku.common.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildUtilsTest {

    @Test
    fun standardSdkLevelsSatisfyExpectedThresholds() {
        assertTrue(BuildUtils.isAtLeast(34, 0, 34))
        assertTrue(BuildUtils.isAtLeast(35, 0, 34))
        assertTrue(BuildUtils.isAtLeast(31, 0, 31))
        assertTrue(BuildUtils.isAtLeast(30, 0, 30))
        assertTrue(BuildUtils.isAtLeast(29, 0, 29))
        assertTrue(BuildUtils.isAtLeast(28, 0, 28))
        assertTrue(BuildUtils.isAtLeast(26, 0, 26))
        assertTrue(BuildUtils.isAtLeast(24, 0, 24))
        assertTrue(BuildUtils.isAtLeast(23, 0, 23))

        assertFalse(BuildUtils.isAtLeast(33, 0, 34))
        assertFalse(BuildUtils.isAtLeast(30, 0, 31))
        assertFalse(BuildUtils.isAtLeast(29, 0, 30))
        assertFalse(BuildUtils.isAtLeast(28, 0, 29))
    }

    @Test
    fun previewSdkUnlocksApi31AndApi34Thresholds() {
        assertTrue(BuildUtils.isAtLeast(30, 1, 31))
        assertFalse(BuildUtils.isAtLeast(29, 1, 31))

        assertTrue(BuildUtils.isAtLeast(33, 1, 34))
        assertFalse(BuildUtils.isAtLeast(32, 1, 34))
    }
}
