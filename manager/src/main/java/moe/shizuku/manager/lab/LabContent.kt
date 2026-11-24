package moe.shizuku.manager.lab

import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.app.ThemeHelper
import moe.shizuku.manager.guide.LocalShakeGuideActive
import moe.shizuku.manager.guide.ShakeDetector
import moe.shizuku.manager.guide.ShakeSensitivity
import moe.shizuku.manager.home.SheverySmartLogsDialog
import moe.shizuku.manager.module.ModuleSettings
import moe.shizuku.manager.ui.compose.ExpressiveSegmentedItem
import moe.shizuku.manager.ui.compose.ExpressiveSegmentedSection
import moe.shizuku.manager.ui.compose.ExpressiveSegmentedToggle
import rikka.shizuku.Shizuku

internal fun diagnosticBundle(shizuku: String, shevery: String, connectors: String) =
    listOf(shizuku, shevery, connectors).joinToString("\n")

@Composable
internal fun LabContent(calibrating: Boolean, onCalibratingChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    var sensitivity by remember { mutableIntStateOf(ShizukuSettings.shakeSensitivity()) }
    var hits by remember { mutableIntStateOf(0) }
    var report by remember { mutableStateOf<String?>(null) }
    var black by remember { mutableStateOf(ThemeHelper.isBlackNightTheme(context)) }
    var reduced by remember { mutableStateOf(ShizukuSettings.reducedExpressiveMotion()) }
    val detector = remember(context, sensitivity) {
        ShakeDetector(context.applicationContext, ShakeSensitivity.entries[sensitivity]) { hits++ }
    }
    val hasSensor = remember(context) {
        context.getSystemService(SensorManager::class.java)?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
    }
    val listening = calibrating && LocalShakeGuideActive.current && LocalWindowInfo.current.isWindowFocused
    LifecycleResumeEffect(listening, detector) {
        if (listening) detector.start()
        onPauseOrDispose { detector.stop() }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ExpressiveSegmentedSection(stringResource(R.string.lab_calibration)) {
            val choices = listOf(R.string.lab_shake_low, R.string.lab_shake_medium, R.string.lab_shake_high)
            choices.forEachIndexed { index, label ->
                val preset = ShakeSensitivity.entries[index]
                ExpressiveSegmentedToggle(
                    index, choices.size + 1, icon = null, title = stringResource(label), checked = index == sensitivity,
                    summary = stringResource(R.string.lab_shake_preset, preset.threshold, preset.peaks),
                    onCheckedChange = {
                        sensitivity = index
                        ShizukuSettings.setShakeSensitivity(index)
                    }
                )
            }
            ExpressiveSegmentedItem(3, 4, null, stringResource(if (calibrating) R.string.lab_test_stop else R.string.lab_test_start),
                enabled = hasSensor,
                summary = if (hasSensor) stringResource(R.string.lab_test_hits, hits)
                else stringResource(R.string.guide_shake_unavailable),
                onClick = { if (!calibrating) hits = 0; onCalibratingChange(!calibrating) })
        }
        ExpressiveSegmentedSection(stringResource(R.string.lab_title)) {
            ExpressiveSegmentedToggle(0, 3, null, stringResource(R.string.settings_black_night_theme), black, onCheckedChange = {
                black = it
                ShizukuSettings.getPreferences().edit().putBoolean(ThemeHelper.KEY_BLACK_NIGHT_THEME, it).apply()
            }, summary = stringResource(R.string.settings_black_night_theme_summary))
            ExpressiveSegmentedToggle(1, 3, null, stringResource(R.string.lab_reduced_motion), reduced, onCheckedChange = {
                reduced = it
                ShizukuSettings.setReducedExpressiveMotion(it)
            }, summary = stringResource(R.string.lab_reduced_motion_summary))
            ExpressiveSegmentedItem(2, 3, null, stringResource(R.string.lab_ipc_title), summary = stringResource(R.string.lab_ipc_summary),
                onClick = {
                    onCalibratingChange(false)
                    val connected = Shizuku.pingBinder()
                    val binder = if (connected) context.getString(R.string.lab_binder_connected,
                        runCatching { Shizuku.getUid() }.getOrDefault(-1)) else context.getString(R.string.lab_binder_disconnected)
                    val sheveryBinder = context.getString(if (com.hamondev.shevery.api.SheveryPrivilege.pingBinder())
                        R.string.lab_shevery_connected else R.string.lab_shevery_disconnected)
                    report = diagnosticBundle(binder, sheveryBinder, context.getString(
                        if (ModuleSettings.isConnectorEnabled()) R.string.lab_connectors_on else R.string.lab_connectors_off))
                })
        }
    }
    report?.let { SheverySmartLogsDialog(diagnosticsSummary = it, snapshotOnly = true, onDismiss = { report = null }) }
}
