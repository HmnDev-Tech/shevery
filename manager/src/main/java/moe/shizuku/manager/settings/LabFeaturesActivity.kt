package moe.shizuku.manager.settings

import android.os.Bundle
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import moe.shizuku.manager.R
import moe.shizuku.manager.app.AppActivity
import moe.shizuku.manager.module.ModuleSettings
import moe.shizuku.manager.service.WatchdogManager
import moe.shizuku.manager.ui.compose.GroupDivider
import moe.shizuku.manager.ui.compose.SettingsGroup
import moe.shizuku.manager.ui.compose.SettingsRow
import moe.shizuku.manager.ui.compose.ShizukuExpressiveTheme
import moe.shizuku.manager.ui.compose.ShizukuLazyScaffold
import moe.shizuku.manager.ui.compose.SwitchSettingsRow

class LabFeaturesActivity : AppActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val context = LocalContext.current
            var connectorEnabled by remember { mutableStateOf(ModuleSettings.isConnectorEnabled()) }
            var verboseLogging by remember { mutableStateOf(ModuleSettings.isVerboseLogging()) }
            var notifyRecovery by remember { mutableStateOf(ModuleSettings.isNotifyOnRecovery()) }
            var autoRefresh by remember { mutableStateOf(ModuleSettings.isAutoRefreshOnResume()) }
            var aiExplain by remember { mutableStateOf(ModuleSettings.isComputAiExplainEnabled()) }
            var showUnsafeDialog by remember { mutableStateOf(false) }
            var showRevokeDialog by remember { mutableStateOf(false) }

            ShizukuExpressiveTheme {
                ShizukuLazyScaffold(
                    title = stringResource(R.string.lab_features_title),
                    onNavigateUp = { finish() }
                ) {
                    item {
                        SettingsGroup(title = stringResource(R.string.lab_features_summary)) {
                            SwitchSettingsRow(
                                icon = R.drawable.ic_baseline_link_24,
                                title = stringResource(R.string.shizuku_connectors_title),
                                summary = stringResource(R.string.shizuku_connectors_summary),
                                checked = connectorEnabled,
                                onCheckedChange = { enabled ->
                                    if (enabled) {
                                        showUnsafeDialog = true
                                    } else {
                                        connectorEnabled = false
                                        ModuleSettings.setConnectorEnabled(false)
                                    }
                                }
                            )
                        }
                    }

                    item {
                        SettingsGroup(title = stringResource(R.string.lab_service_behavior_title)) {
                            SwitchSettingsRow(
                                icon = R.drawable.ic_outline_notifications_active_24,
                                title = stringResource(R.string.lab_notify_recovery_title),
                                summary = stringResource(R.string.lab_notify_recovery_summary),
                                checked = notifyRecovery,
                                onCheckedChange = { value ->
                                    notifyRecovery = value
                                    ModuleSettings.setNotifyOnRecovery(value)
                                }
                            )
                            GroupDivider()
                            SwitchSettingsRow(
                                icon = R.drawable.ic_server_restart,
                                title = stringResource(R.string.lab_auto_refresh_title),
                                summary = stringResource(R.string.lab_auto_refresh_summary),
                                checked = autoRefresh,
                                onCheckedChange = { value ->
                                    autoRefresh = value
                                    ModuleSettings.setAutoRefreshOnResume(value)
                                }
                            )
                        }
                    }

                    item {
                        SettingsGroup(title = stringResource(R.string.lab_debugging_title)) {
                            SwitchSettingsRow(
                                icon = R.drawable.ic_adb_24dp,
                                title = stringResource(R.string.lab_verbose_logging_title),
                                summary = stringResource(R.string.lab_verbose_logging_summary),
                                checked = verboseLogging,
                                onCheckedChange = { value ->
                                    verboseLogging = value
                                    ModuleSettings.setVerboseLogging(value)
                                }
                            )
                            GroupDivider()
                            SettingsRow(
                                icon = R.drawable.ic_adb_24dp,
                                title = stringResource(R.string.usb_debugging_hide_title),
                                summary = stringResource(R.string.usb_debugging_hide_open_summary),
                                onClick = {
                                    context.startActivity(Intent(context, UsbDebuggingHideActivity::class.java))
                                }
                            )
                        }
                    }

                    item {
                        SettingsGroup(title = stringResource(R.string.network_firewall_title)) {
                            SettingsRow(
                                icon = R.drawable.ic_security_24dp,
                                title = stringResource(R.string.network_firewall_title),
                                summary = stringResource(R.string.network_firewall_open_summary),
                                onClick = {
                                    context.startActivity(Intent(context, NetworkFirewallActivity::class.java))
                                }
                            )
                        }
                    }

                    item {
                        SettingsGroup(title = stringResource(R.string.lab_ai_title)) {
                            SwitchSettingsRow(
                                icon = R.drawable.ic_code_24dp,
                                title = stringResource(R.string.lab_ai_explain_title),
                                summary = stringResource(R.string.lab_ai_explain_summary),
                                checked = aiExplain,
                                onCheckedChange = { value ->
                                    aiExplain = value
                                    ModuleSettings.setComputAiExplainEnabled(value)
                                }
                            )
                        }
                    }

                    item {
                        SettingsGroup(title = stringResource(R.string.lab_maintenance_title)) {
                            SettingsRow(
                                icon = R.drawable.ic_server_restart,
                                title = stringResource(R.string.lab_restart_service_title),
                                summary = stringResource(R.string.lab_restart_service_summary),
                                onClick = {
                                    WatchdogManager.attemptRestart(context)
                                    Toast.makeText(context, "Restart requested", Toast.LENGTH_SHORT).show()
                                }
                            )
                            GroupDivider()
                            SettingsRow(
                                icon = R.drawable.ic_outline_notifications_active_24,
                                title = stringResource(R.string.lab_clear_update_title),
                                summary = stringResource(R.string.lab_clear_update_summary),
                                onClick = {
                                    ModuleSettings.clearPendingUpdate()
                                    Toast.makeText(context, "Update banner dismissed", Toast.LENGTH_SHORT).show()
                                }
                            )
                            GroupDivider()
                            SettingsRow(
                                icon = R.drawable.ic_warning_24,
                                title = stringResource(R.string.lab_revoke_trusted_title),
                                summary = stringResource(R.string.lab_revoke_trusted_summary),
                                onClick = { showRevokeDialog = true }
                            )
                        }
                    }
                }

                if (showUnsafeDialog) {
                    LabWarningDialog(
                        onDismiss = { showUnsafeDialog = false },
                        titleRes = R.string.unsafe_warning_title,
                        messageRes = R.string.unsafe_warning_message,
                        onConfirm = {
                            showUnsafeDialog = false
                            connectorEnabled = true
                            ModuleSettings.setConnectorEnabled(true)
                        }
                    )
                }

                if (showRevokeDialog) {
                    LabWarningDialog(
                        onDismiss = { showRevokeDialog = false },
                        titleRes = R.string.lab_revoke_trusted_warning_title,
                        messageRes = R.string.lab_revoke_trusted_warning_message,
                        onConfirm = {
                            showRevokeDialog = false
                            ModuleSettings.clearTrustedModules()
                            Toast.makeText(context, "Trusted modules revoked", Toast.LENGTH_SHORT).show()
                        }
                    )
                }

            }
        }
    }

    @Composable
    private fun LabWarningDialog(
        onDismiss: () -> Unit,
        titleRes: Int,
        messageRes: Int,
        onConfirm: () -> Unit
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(titleRes)) },
            text = { Text(stringResource(messageRes)) },
            confirmButton = {
                TextButton(onClick = onConfirm) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.extraLarge
        )
    }
}
