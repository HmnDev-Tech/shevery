package moe.shizuku.manager.settings

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.shizuku.manager.R
import moe.shizuku.manager.adb.AdbArm
import moe.shizuku.manager.app.AppActivity
import moe.shizuku.manager.ui.compose.GroupDivider
import moe.shizuku.manager.ui.compose.SettingsGroup
import moe.shizuku.manager.ui.compose.SettingsRow
import moe.shizuku.manager.ui.compose.ShizukuExpressiveTheme
import moe.shizuku.manager.ui.compose.ShizukuLazyScaffold
import moe.shizuku.manager.ui.compose.SwitchSettingsRow

private data class HideTargetApp(val packageName: String, val label: String)

class UsbDebuggingHideActivity : AppActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            var refresh by remember { mutableStateOf(0) }
            var apps by remember { mutableStateOf(emptyList<HideTargetApp>()) }
            var targets by remember { mutableStateOf(UsbDebuggingHideManager.targetPackages()) }
            var usageAccess by remember { mutableStateOf(UsbDebuggingHideManager.hasUsageAccess(context)) }
            var canChangeSetting by remember { mutableStateOf(AdbArm.canArm(context)) }
            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                usageAccess = UsbDebuggingHideManager.hasUsageAccess(context)
                canChangeSetting = AdbArm.canArm(context)
                UsbDebuggingHideService.refresh(context)
                refresh++
            }
            androidx.compose.runtime.LaunchedEffect(refresh) {
                apps = withContext(Dispatchers.IO) { loadTargetApps() }
                UsbDebuggingHideManager.retainInstalledTargets(
                    context,
                    apps.map { it.packageName }.toSet()
                )
                targets = UsbDebuggingHideManager.targetPackages()
            }

            ShizukuExpressiveTheme {
                ShizukuLazyScaffold(
                    title = stringResource(R.string.usb_debugging_hide_title),
                    onNavigateUp = { finish() }
                ) {
                    item {
                        SettingsGroup(title = stringResource(R.string.usb_debugging_hide_title)) {
                            Text(
                                stringResource(R.string.usb_debugging_hide_warning),
                                modifier = androidx.compose.ui.Modifier.padding(16.dp)
                            )
                            GroupDivider()
                            SettingsRow(
                                icon = R.drawable.ic_security_24dp,
                                title = stringResource(R.string.usb_debugging_hide_usage_access),
                                summary = stringResource(
                                    if (usageAccess) R.string.usb_debugging_hide_usage_access_granted
                                    else R.string.usb_debugging_hide_usage_access_required
                                ),
                                enabled = !usageAccess,
                                onClick = {
                                    runCatching {
                                        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                                    }
                                }
                            )
                            if (!canChangeSetting) {
                                GroupDivider()
                                Text(
                                    stringResource(R.string.usb_debugging_hide_permission_required),
                                    modifier = androidx.compose.ui.Modifier.padding(16.dp)
                                )
                            }
                        }
                    }
                    item {
                        SettingsGroup(title = stringResource(R.string.usb_debugging_hide_apps)) {}
                    }
                    items(apps, key = { it.packageName }) { app ->
                        SwitchSettingsRow(
                            icon = R.drawable.ic_system_icon,
                            title = app.label,
                            summary = app.packageName,
                            checked = app.packageName in targets,
                            enabled = app.packageName in targets || (usageAccess && canChangeSetting),
                            onCheckedChange = { enabled ->
                                UsbDebuggingHideManager.setTarget(context, app.packageName, enabled)
                                targets = UsbDebuggingHideManager.targetPackages()
                            }
                        )
                    }
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun loadTargetApps(): List<HideTargetApp> =
        packageManager.getInstalledApplications(0)
            .asSequence()
            .filter { it.packageName != packageName }
            .filter { packageManager.getLaunchIntentForPackage(it.packageName) != null }
            .map { HideTargetApp(it.packageName, it.loadLabel(packageManager).toString()) }
            .sortedBy { it.label.lowercase() }
            .toList()
}
