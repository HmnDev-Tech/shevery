package moe.shizuku.manager.settings

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

private data class DevOptionsHideTargetApp(val packageName: String, val label: String)

class DeveloperOptionsHideActivity : AppActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            var refresh by remember { mutableStateOf(0) }
            var apps by remember { mutableStateOf(emptyList<DevOptionsHideTargetApp>()) }
            var targets by remember { mutableStateOf(DeveloperOptionsHideManager.targetPackages()) }
            var searchQuery by rememberSaveable { mutableStateOf("") }
            var usageAccess by remember { mutableStateOf(DeveloperOptionsHideManager.hasUsageAccess(context)) }
            var canChangeSetting by remember { mutableStateOf(AdbArm.canArm(context)) }
            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                usageAccess = DeveloperOptionsHideManager.hasUsageAccess(context)
                canChangeSetting = AdbArm.canArm(context)
                DeveloperOptionsHideService.refresh(context)
                refresh++
            }
            androidx.compose.runtime.LaunchedEffect(refresh) {
                apps = withContext(Dispatchers.IO) { loadTargetApps() }
                DeveloperOptionsHideManager.retainInstalledTargets(
                    context,
                    apps.map { it.packageName }.toSet()
                )
                targets = DeveloperOptionsHideManager.targetPackages()
            }
            val filteredApps = remember(apps, searchQuery) {
                val query = searchQuery.trim()
                if (query.isEmpty()) {
                    apps
                } else {
                    apps.filter {
                        it.label.contains(query, ignoreCase = true) ||
                            it.packageName.contains(query, ignoreCase = true)
                    }
                }
            }

            ShizukuExpressiveTheme {
                ShizukuLazyScaffold(
                    title = stringResource(R.string.developer_options_hide_title),
                    onNavigateUp = { finish() }
                ) {
                    item {
                        SettingsGroup(title = stringResource(R.string.developer_options_hide_title)) {
                            Text(
                                stringResource(R.string.developer_options_hide_warning),
                                modifier = Modifier.padding(16.dp)
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
                                    stringResource(R.string.developer_options_hide_permission_required),
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    }
                    item {
                        SettingsGroup(title = stringResource(R.string.usb_debugging_hide_apps)) {
                            TextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                placeholder = { Text(stringResource(R.string.app_management_search_hint)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Search,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(
                                            onClick = { searchQuery = "" },
                                            modifier = Modifier.size(48.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Clear,
                                                contentDescription = stringResource(R.string.app_management_clear_search)
                                            )
                                        }
                                    }
                                },
                                singleLine = true,
                                shape = MaterialTheme.shapes.extraLarge,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                )
                            )
                        }
                    }
                    items(filteredApps, key = { it.packageName }) { app ->
                        SwitchSettingsRow(
                            icon = R.drawable.ic_system_icon,
                            title = app.label,
                            summary = app.packageName,
                            checked = app.packageName in targets,
                            enabled = app.packageName in targets || (usageAccess && canChangeSetting),
                            onCheckedChange = { enabled ->
                                DeveloperOptionsHideManager.setTarget(context, app.packageName, enabled)
                                targets = DeveloperOptionsHideManager.targetPackages()
                            }
                        )
                    }
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun loadTargetApps(): List<DevOptionsHideTargetApp> =
        packageManager.getInstalledApplications(0)
            .asSequence()
            .filter { it.packageName != packageName }
            .filter { packageManager.getLaunchIntentForPackage(it.packageName) != null }
            .map { DevOptionsHideTargetApp(it.packageName, it.loadLabel(packageManager).toString()) }
            .sortedBy { it.label.lowercase() }
            .toList()
}
