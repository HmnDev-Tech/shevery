@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)

package moe.shizuku.manager.module.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import moe.shizuku.manager.utils.CustomTabsHelper
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import android.content.Intent
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.Surface
import moe.shizuku.manager.BuildConfig
import moe.shizuku.manager.R
import moe.shizuku.manager.module.ModuleSettings
import moe.shizuku.manager.ui.compose.GroupDivider
import moe.shizuku.manager.ui.compose.SettingsGroup
import moe.shizuku.manager.ui.compose.SettingsRow
import moe.shizuku.manager.ui.compose.SwitchSettingsRow

@Composable
fun AppUpdateSettingsGroup() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var appUpdateChannel by remember { mutableStateOf(ModuleSettings.getAppUpdateChannel()) }
    var appAutoCheck by remember { mutableStateOf(ModuleSettings.isAppUpdateAutoCheckEnabled()) }
    var appUpdateFrequency by remember { mutableStateOf(ModuleSettings.getAppUpdateFrequency()) }
    var isCheckingAppUpdate by remember { mutableStateOf(false) }
    var appUpdateResult by remember { mutableStateOf<SheveryAppUpdateResult?>(null) }

    val pendingVersion = remember { mutableStateOf<String?>(null) }
    val pendingUrl = remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        pendingVersion.value = ModuleSettings.getPendingUpdateVersion()
        pendingUrl.value = ModuleSettings.getPendingUpdateUrl()
    }
    val version = pendingVersion.value
    val url = pendingUrl.value
    val showPendingInstall = remember { mutableStateOf(false) }
    if (!version.isNullOrEmpty() && !url.isNullOrEmpty())) {
        if (showPendingInstall.value)) {
            SheveryAppUpdateDialog(
                result = SheveryAppUpdateResult(
                    hasUpdate = true,
                    currentVersion = BuildConfig.VERSION_NAME,
                    latestVersion = version,
                    releaseTitle = null,
                    releaseNotes = null,
                    downloadUrl = url,
                    htmlUrl = null,
                    isPreRelease = false,
                    publishedAt = null,
                    error = null
                ),
                onDismiss = { showPendingInstall.value = false },
            )
        }
        Surface(
            onClick = { showPendingInstall.value = true },
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.primaryContainer,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_outline_info_24),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.home_update_available_title, version),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = stringResource(R.string.home_update_available_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }

    SettingsGroup(title = stringResource(R.string.shevery_update_group_title)) {
        SettingsRow(
            icon = R.drawable.ic_server_restart,
            title = stringResource(R.string.shevery_update_check_title),
            summary = stringResource(R.string.shevery_update_check_summary) + " (${BuildConfig.VERSION_NAME})",
            onClick = {
                scope.launch {
                    isCheckingAppUpdate = true
                    try {
                        appUpdateResult = SheveryUpdateChecker.getInstance().checkAppUpdate(context)
                    } catch (e: Exception) {
                        appUpdateResult = SheveryAppUpdateResult(
                            hasUpdate = false,
                            currentVersion = BuildConfig.VERSION_NAME,
                            latestVersion = null,
                            releaseTitle = null,
                            releaseNotes = null,
                            downloadUrl = null,
                            htmlUrl = null,
                            isPreRelease = false,
                            publishedAt = null,
                            error = e.message ?: "check_failed"
                        )
                    } finally {
                        isCheckingAppUpdate = false
                    }
                }
            }
        )
        GroupDivider()
        AppUpdateChannelDropdown(
            selected = appUpdateChannel,
            onSelect = {
                appUpdateChannel = it
                ModuleSettings.setAppUpdateChannel(it)
            }
        )
        GroupDivider()
        SwitchSettingsRow(
            icon = R.drawable.ic_outline_notifications_active_24,
            title = stringResource(R.string.shevery_update_auto_check),
            summary = stringResource(R.string.shevery_update_auto_check_summary),
            checked = appAutoCheck,
            onCheckedChange = {
                appAutoCheck = it
                ModuleSettings.setAppUpdateAutoCheckEnabled(it)
            }
        )
        if (appAutoCheck) {
            GroupDivider()
            UpdateFrequencyDropdownForApp(
                selected = appUpdateFrequency,
                onSelect = {
                    appUpdateFrequency = it
                    ModuleSettings.setAppUpdateFrequency(it)
                }
            )
        }
    }

    if (isCheckingAppUpdate) {
        AlertDialog(
            onDismissRequest = { isCheckingAppUpdate = false },
            title = { Text(stringResource(R.string.shevery_update_check_title)) },
text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(vertical =  8.dp),
                ) {
                    LoadingIndicator(modifier = Modifier.size(28.dp))
                    Text(stringResource(R.string.shevery_update_checking))
                }
            },
            confirmButton = {}
        )
    }

    appUpdateResult?.let { result ->
        SheveryAppUpdateDialog(
            result = result,
            onDismiss = { appUpdateResult = null }
        )
    }
}

@Composable
private fun AppUpdateChannelDropdown(
    selected: ModuleSettings.AppUpdateChannel,
    onSelect: (ModuleSettings.AppUpdateChannel) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val entries = ModuleSettings.AppUpdateChannel.entries

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        SettingsRow(
            modifier = Modifier.menuAnchor(),
            icon = R.drawable.ic_outline_info_24,
            title = stringResource(R.string.shevery_update_channel),
            summary = stringResource(selected.labelRes),
            onClick = { expanded = true },
            trailing = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            }
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            entries.forEach { channel ->
                DropdownMenuItem(
                    text = { Text(stringResource(channel.labelRes)) },
                    onClick = {
                        onSelect(channel)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun UpdateFrequencyDropdownForApp(
    selected: ModuleSettings.UpdateFrequency,
    onSelect: (ModuleSettings.UpdateFrequency) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        SettingsRow(
            modifier = Modifier.menuAnchor(),
            icon = R.drawable.ic_outline_notifications_active_24,
            title = stringResource(R.string.update_settings_frequency_label),
            summary = stringResource(when (selected) {
                ModuleSettings.UpdateFrequency.MANUAL -> R.string.update_settings_frequency_manual
                ModuleSettings.UpdateFrequency.DAILY -> R.string.update_settings_frequency_daily
                ModuleSettings.UpdateFrequency.WEEKLY -> R.string.update_settings_frequency_weekly
            }),
            onClick = { expanded = true },
            trailing = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            }
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.update_settings_frequency_manual)) },
                onClick = {
                    onSelect(ModuleSettings.UpdateFrequency.MANUAL)
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.update_settings_frequency_daily)) },
                onClick = {
                    onSelect(ModuleSettings.UpdateFrequency.DAILY)
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.update_settings_frequency_weekly)) },
                onClick = {
                    onSelect(ModuleSettings.UpdateFrequency.WEEKLY)
                    expanded = false
                }
            )
        }
    }
}

private enum class AppUpdatePhase { Idle, Downloading, Installing, Failed }

@Composable
internal fun SheveryAppUpdateDialog(
    result: SheveryAppUpdateResult,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val apkFile = remember { File(context.cacheDir, "shevery-app-update.apk") }

    var phase by remember(result) { mutableStateOf(AppUpdatePhase.Idle) }
    var progress by remember(result) { mutableStateOf(-1f) } // -1 = unknown length (indeterminate)
    var errorMessage by remember(result) { mutableStateOf<String?>(null) }

    fun startDownload() {
        scope.launch {
            phase = AppUpdatePhase.Downloading
            progress = -1f
            errorMessage = null
            try {
                AppUpdateDownloader.downloadToFile(
                    url = result.downloadUrl!!,
                    targetFile = apkFile,
                    onProgress = { read, total ->
                        progress = total?.takeIf { it > 0 }?.let { read.toFloat() / it } ?: -1f
                    }
                )
                phase = AppUpdatePhase.Installing
                when (val outcome = AppUpdateInstaller.install(context.applicationContext, apkFile)) {

                    is AppUpdateInstaller.Result.UserActionRequired -> {
                        outcome.confirmIntent?.let { confirm ->
                            runCatching {
                                context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                        }
                        onDismiss()
                    }
                    is AppUpdateInstaller.Result.Success -> {
                        runCatching {
                            android.widget.Toast.makeText(
                                context, R.string.shevery_update_install_success,
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                        onDismiss()
                    }
                    is AppUpdateInstaller.Result.Failed -> {
                        apkFile.delete()
                        phase = AppUpdatePhase.Failed
                        errorMessage = outcome.message
                    }
                }
            } catch (e: CancellationException) {
                apkFile.delete()
                throw e
            } catch (e: Exception) {
                apkFile.delete()
                phase = AppUpdatePhase.Failed
                errorMessage = e.message ?: e.javaClass.simpleName
            }
        }
    }

    AlertDialog(
        onDismissRequest = {
            if (phase != AppUpdatePhase.Installing) onDismiss()
        },
        title = {
            when (phase) {
                AppUpdatePhase.Idle -> if (result.hasUpdate) {
                    Text(stringResource(R.string.shevery_update_available_title))
                } else if (result.error != null) {
                    Text(stringResource(R.string.shevery_update_check_failed, result.error))
                } else {
                    Text(stringResource(R.string.shevery_update_up_to_date, result.currentVersion))
                }
                AppUpdatePhase.Downloading -> Text(stringResource(R.string.shevery_update_downloading_title))
                AppUpdatePhase.Installing -> Text(stringResource(R.string.shevery_update_installing))
                AppUpdatePhase.Failed -> Text(stringResource(R.string.shevery_update_install_failed_title))
            }
        },
        text = {
            when (phase) {
AppUpdatePhase.Downloading -> {
                    Column(
                        modifier = Modifier.padding(vertical =  8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (progress >=  0f) {
                            LinearProgressIndicator(
                                progress = { progress.coerceIn(0f,  1f) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                text = stringResource(R.string.shevery_update_download_progress, (progress.coerceIn(0f,  1f) * 100).toInt()),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                LoadingIndicator(modifier = Modifier.size(28.dp))
                                Text(stringResource(R.string.shevery_update_preparing), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                AppUpdatePhase.Installing -> Row(
                    modifier = Modifier.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {

                    LoadingIndicator(modifier = Modifier.size(28.dp))
                    Text(stringResource(R.string.shevery_update_installing), style = MaterialTheme.typography.bodyMedium)


                }
                AppUpdatePhase.Failed -> Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {

                    Text(errorMessage ?: "?", style = MaterialTheme.typography.bodyMedium)


                }
                AppUpdatePhase.Idle -> {
                    if (result.hasUpdate && result.latestVersion != null) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {


                            Text(
                                text = stringResource(
                                    R.string.shevery_update_available_msg, 
                                    result.latestVersion, 
                                    result.currentVersion
                                ),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (!result.releaseNotes.isNullOrBlank()) {
                                Text(
                                    text = result.releaseNotes.take(400).let {
                                        if (result.releaseNotes.length >400) "$it…" else it

                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (result.isPreRelease) {
                                Text(
                                    text = "⚠ Pre-release / Beta",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    } else if (result.error != null) {
                        Text(result.error ?: "", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        null


                    }
                }
            }
        },
        confirmButton = {
            when (phase) {

                AppUpdatePhase.Idle -> if (result.hasUpdate && result.downloadUrl != null) {
                    TextButton(onClick = { startDownload() }) {
                        Text(stringResource(R.string.shevery_update_download_install))
                    }
                }
                AppUpdatePhase.Failed -> TextButton(onClick = { startDownload() }) {
                    Text(stringResource(R.string.shevery_update_retry))
                }
                else -> Unit



            }
        },
        dismissButton = {
            when (phase) {

                AppUpdatePhase.Idle -> if (result.hasUpdate && result.htmlUrl != null) {
                    TextButton(onClick = {
                        CustomTabsHelper.launchUrlOrCopy(context, result.htmlUrl)
                        onDismiss()
                    }) {
                        Text(stringResource(R.string.shevery_update_view_release))
                    }
                } else {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(android.R.string.ok))
                    }
                }
                AppUpdatePhase.Downloading -> TextButton(onClick = onDismiss) {
                    Text(stringResource(android.R.string.cancel))
                }
                AppUpdatePhase.Failed -> TextButton(onClick = {
                    val url = result.downloadUrl ?: result.htmlUrl ?: ""
                    if (url.isNotBlank()) CustomTabsHelper.launchUrlOrCopy(context, url)
                    onDismiss()
                }) {
                    Text(stringResource(R.string.shevery_update_download_manual))
                }
                AppUpdatePhase.Installing -> Unit


            }
        }
    )
}