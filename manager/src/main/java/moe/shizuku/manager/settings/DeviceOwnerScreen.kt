package moe.shizuku.manager.settings

import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import moe.shizuku.manager.R
import moe.shizuku.manager.deviceowner.DeviceOwnerManager
import moe.shizuku.manager.ui.compose.GroupDivider
import moe.shizuku.manager.ui.compose.MonospaceLog
import moe.shizuku.manager.ui.compose.SettingsGroup
import moe.shizuku.manager.ui.compose.SettingsRow
import rikka.core.util.ClipboardUtils

@Composable
fun DeviceOwnerContent() {
    val context = LocalContext.current
    val pm = context.packageManager
    var isOwner by remember { mutableStateOf(DeviceOwnerManager.isDeviceOwner(context)) }
    var selectedAppForDelegation by remember { mutableStateOf<ApplicationInfo?>(null) }
    var showAppPickerDialog by remember { mutableStateOf(false) }
    var selectedAppForTransfer by remember { mutableStateOf<ApplicationInfo?>(null) }
    var showTransferConfirm by remember { mutableStateOf(false) }

    val adbCommand = remember(context) { DeviceOwnerManager.getAdbCommand(context) }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Status Card
        Card(
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(
                containerColor = if (isOwner) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = if (isOwner) stringResource(R.string.device_owner_active) else stringResource(R.string.device_owner_inactive),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isOwner) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isOwner) stringResource(R.string.device_owner_active_desc) else stringResource(R.string.device_owner_inactive_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isOwner) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (!isOwner) {
                    Spacer(modifier = Modifier.height(16.dp))
                    MonospaceLog(text = adbCommand)
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            ClipboardUtils.put(context, adbCommand)
                            Toast.makeText(context, R.string.automation_copied_to_clipboard, Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.home_adb_dialog_view_command_copy_button))
                    }
                }
            }
        }

        if (isOwner) {
            // Device Owner Features
            SettingsGroup(title = stringResource(R.string.device_owner_features_title)) {
                SettingsRow(
                    icon = R.drawable.ic_adb_24dp,
                    title = stringResource(R.string.device_owner_enable_adb),
                    summary = stringResource(R.string.device_owner_enable_adb_summary),
                    onClick = {
                        val ok = DeviceOwnerManager.enableAdbViaDpm(context)
                        Toast.makeText(
                            context,
                            if (ok) "ADB enabled via DPM" else "Failed to enable ADB",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            }

            // Delegation Group
            SettingsGroup(title = stringResource(R.string.device_owner_delegation_title)) {
                val eligibleApps = remember {
                    pm.getInstalledApplications(0)
                        .filter { it.packageName != context.packageName }
                        .sortedBy { it.loadLabel(pm).toString() }
                }

                SettingsRow(
                    icon = R.drawable.ic_add_24,
                    title = stringResource(R.string.device_owner_delegation_title),
                    summary = stringResource(R.string.device_owner_delegation_summary),
                    onClick = {
                        showAppPickerDialog = true
                    }
                )

                // Show apps with existing delegations
                val delegatedApps = remember(selectedAppForDelegation) {
                    eligibleApps.filter { app ->
                        DeviceOwnerManager.getDelegatedScopes(context, app.packageName).isNotEmpty()
                    }
                }

                delegatedApps.forEach { app ->
                    GroupDivider()
                    val scopes = DeviceOwnerManager.getDelegatedScopes(context, app.packageName)
                    val label = app.loadLabel(pm).toString()
                    SettingsRow(
                        icon = null,
                        title = label,
                        summary = "${app.packageName} (${scopes.size} scopes)",
                        onClick = {
                            selectedAppForDelegation = app
                        }
                    )
                }
            }

            // Transfer Ownership Group
            SettingsGroup(title = stringResource(R.string.device_owner_transfer_title)) {
                val eligibleAdmins = remember { DeviceOwnerManager.getEligibleAdminApps(context) }

                if (eligibleAdmins.isEmpty()) {
                    SettingsRow(
                        icon = null,
                        title = stringResource(R.string.device_owner_transfer_title),
                        summary = stringResource(R.string.device_owner_delegation_empty)
                    )
                } else {
                    eligibleAdmins.forEachIndexed { index, adminApp ->
                        if (index > 0) GroupDivider()
                        val appLabel = adminApp.loadLabel(pm).toString()
                        SettingsRow(
                            icon = null,
                            title = appLabel,
                            summary = adminApp.packageName,
                            onClick = {
                                selectedAppForTransfer = adminApp
                                showTransferConfirm = true
                            }
                        )
                    }
                }
            }
        }
    }

    // App Picker Dialog for Delegation
    if (showAppPickerDialog) {
        val eligibleApps = remember {
            pm.getInstalledApplications(0)
                .filter { it.packageName != context.packageName }
                .sortedBy { it.loadLabel(pm).toString() }
        }

        AlertDialog(
            onDismissRequest = { showAppPickerDialog = false },
            title = { Text(stringResource(R.string.device_owner_delegation_title)) },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp)) {
                    items(eligibleApps, key = { it.packageName }) { app ->
                        val label = remember(app) { app.loadLabel(pm).toString() }
                        val icon = remember(app) {
                            try {
                                app.loadIcon(pm)?.toBitmap(40, 40)?.asImageBitmap()
                            } catch (_: Throwable) {
                                null
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showAppPickerDialog = false
                                    selectedAppForDelegation = app
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (icon != null) {
                                Image(
                                    bitmap = icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp))
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                            }
                            Column {
                                Text(text = label, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                Text(text = app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAppPickerDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    // App Delegation Dialog
    selectedAppForDelegation?.let { appInfo ->
        DelegationScopesDialog(
            context = context,
            appInfo = appInfo,
            onDismiss = { selectedAppForDelegation = null }
        )
    }

    // Transfer Confirmation Dialog
    if (showTransferConfirm && selectedAppForTransfer != null) {
        val targetApp = selectedAppForTransfer!!
        val appLabel = targetApp.loadLabel(context.packageManager).toString()

        AlertDialog(
            onDismissRequest = {
                showTransferConfirm = false
                selectedAppForTransfer = null
            },
            title = { Text(stringResource(R.string.device_owner_transfer_dialog_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.device_owner_transfer_dialog_message))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Target: $appLabel (${targetApp.packageName})",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        val receivers = context.packageManager.queryBroadcastReceivers(
                            android.content.Intent(android.app.admin.DeviceAdminReceiver.ACTION_DEVICE_ADMIN_ENABLED)
                                .setPackage(targetApp.packageName),
                            0
                        )
                        val receiverName = receivers.firstOrNull()?.activityInfo?.name
                        if (receiverName != null) {
                            val cn = ComponentName(targetApp.packageName, receiverName)
                            val ok = DeviceOwnerManager.transferOwnership(context, cn)
                            if (ok) {
                                isOwner = false
                                Toast.makeText(context, R.string.device_owner_transfer_success, Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, R.string.device_owner_transfer_failed, Toast.LENGTH_LONG).show()
                            }
                        } else {
                            Toast.makeText(context, "No DeviceAdminReceiver found in target app", Toast.LENGTH_SHORT).show()
                        }
                        showTransferConfirm = false
                        selectedAppForTransfer = null
                    }
                ) {
                    Text(stringResource(R.string.device_owner_transfer_button))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showTransferConfirm = false
                    selectedAppForTransfer = null
                }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun DelegationScopesDialog(
    context: Context,
    appInfo: ApplicationInfo,
    onDismiss: () -> Unit
) {
    val pm = context.packageManager
    val appLabel = remember(appInfo) { appInfo.loadLabel(pm).toString() }
    val initialScopes = remember(appInfo) {
        DeviceOwnerManager.getDelegatedScopes(context, appInfo.packageName).toSet()
    }
    var currentScopes by remember(appInfo) { mutableStateOf(initialScopes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Delegation: $appLabel")
                Text(
                    text = appInfo.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column {
                DeviceOwnerManager.ALL_SCOPES.forEach { scope ->
                    val isChecked = currentScopes.contains(scope.scopeName)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                currentScopes = if (isChecked) {
                                    currentScopes - scope.scopeName
                                } else {
                                    currentScopes + scope.scopeName
                                }
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = { checked ->
                                currentScopes = if (checked) {
                                    currentScopes + scope.scopeName
                                } else {
                                    currentScopes - scope.scopeName
                                }
                            }
                        )
                        Column(modifier = Modifier.padding(start = 8.dp)) {
                            Text(text = scope.label, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                            Text(text = scope.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val ok = DeviceOwnerManager.setDelegatedScopes(
                        context = context,
                        packageName = appInfo.packageName,
                        scopes = currentScopes.toList()
                    )
                    Toast.makeText(
                        context,
                        if (ok) "Delegation updated" else "Failed to update delegation",
                        Toast.LENGTH_SHORT
                    ).show()
                    onDismiss()
                }
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}
