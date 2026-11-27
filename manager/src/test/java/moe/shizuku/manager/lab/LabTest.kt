package moe.shizuku.manager.lab

import java.io.File
import moe.shizuku.manager.guide.ShakeGesture
import moe.shizuku.manager.guide.ShakeSensitivity
import moe.shizuku.manager.module.AdbModule
import moe.shizuku.manager.module.activeModuleCount
import org.junit.Assert.*
import org.junit.Test

class LabTest {
    @Test
    fun sensitivityUsesDifferentThresholdsAndPeakCounts() {
        val high = ShakeGesture(ShakeSensitivity.HIGH)
        val low = ShakeGesture(ShakeSensitivity.LOW)
        for (gesture in listOf(high, low)) {
            assertFalse(gesture.onSample(1.7f, 100))
            gesture.onSample(1f, 200)
        }
        assertTrue(high.onSample(1.7f, 300))
        assertFalse(low.onSample(1.7f, 300))
    }

    @Test
    fun diagnosticReportIncludesOnlyStatusFields() {
        assertEquals("Shizuku connected\nShevery disconnected\nConnectors disabled",
            diagnosticBundle("Shizuku connected", "Shevery disconnected", "Connectors disabled"))
    }

    @Test
    fun countsEnabledModulesIndependentlyOfInstalledTotal() {
        val file = File("fixture")
        val module = AdbModule("test", "Test", null, null, null, null, file, null,
            webRoot = null, declaresShellBridge = false, actionScript = null, serviceScript = null,
            logsDir = file, sizeBytes = 0, enabled = true)
        assertEquals(1, activeModuleCount(listOf(module, module.copy(id = "disabled", enabled = false))))
        assertEquals(0, activeModuleCount(emptyList()))
    }
}
