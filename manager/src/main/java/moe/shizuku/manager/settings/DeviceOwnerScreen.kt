package moe.shizuku.manager.settings

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Warning
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.delay
import moe.shizuku.manager.R
import moe.shizuku.manager.deviceowner.DeviceOwnerManager
import moe.shizuku.manager.security.AuthManager
import moe.shizuku.manager.security.SecuritySettings
import moe.shizuku.manager.ui.compose.MonospaceLog
import moe.shizuku.manager.ui.compose.SettingsGroup
import moe.shizuku.manager.ui.compose.SettingsRow
import rikka.core.util.ClipboardUtils

@Composable
fun DeviceOwnerContent(
    onOpenTransfer: () -> Unit = {},
    onOpenDelegation: () -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    var isOwner by remember { mutableStateOf(DeviceOwnerManager.isDeviceOwner(context)) }
    val adbCommand = remember(context) { DeviceOwnerManager.getAdbCommand(context) }
    var showDeactivateDialog by remember { mutableStateOf(false) }

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
            SettingsGroup(title = stringResource(R.string.device_owner_features_title)) {
                SettingsRow(
                    icon = R.drawable.ic_security_24dp,
                    title = stringResource(R.string.device_owner_delegation_title),
                    summary = stringResource(R.string.device_owner_delegation_summary),
                    onClick = onOpenDelegation
                )
                SettingsRow(
                    icon = R.drawable.ic_device_owner_24dp,
                    title = stringResource(R.string.device_owner_transfer_title),
                    summary = stringResource(R.string.device_owner_transfer_summary),
                    onClick = onOpenTransfer
                )
            }

            SettingsGroup(title = stringResource(R.string.device_owner_danger_zone_title)) {
                SettingsRow(
                    icon = R.drawable.ic_delete_24,
                    title = stringResource(R.string.device_owner_deactivate_title),
                    summary = stringResource(R.string.device_owner_deactivate_summary),
                    onClick = { showDeactivateDialog = true }
                )
            }
        }
    }

    if (showDeactivateDialog) {
        var countdown by remember { mutableIntStateOf(5) }
        LaunchedEffect(Unit) {
            while (countdown > 0) {
                delay(1000)
                countdown--
            }
        }

        AlertDialog(
            onDismissRequest = { showDeactivateDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(stringResource(R.string.device_owner_deactivate_dialog_title))
            },
            text = {
                Text(
                    text = stringResource(R.string.device_owner_deactivate_dialog_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeactivateDialog = false
                        fun performDeactivation() {
                            val ok = DeviceOwnerManager.clearDeviceOwner(context)
                            if (ok) {
                                isOwner = false
                                Toast.makeText(context, R.string.device_owner_deactivate_success, Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, R.string.device_owner_deactivate_failed, Toast.LENGTH_LONG).show()
                            }
                        }

                        if (activity != null) {
                            AuthManager.executeWithAuth(
                                activity = activity,
                                action = SecuritySettings.ProtectedAction.DEVICE_OWNER,
                                onSuccess = { performDeactivation() }
                            )
                        } else {
                            performDeactivation()
                        }
                    },
                    enabled = countdown == 0,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(
                        if (countdown > 0)
                            stringResource(R.string.device_owner_deactivate_countdown, countdown)
                        else
                            stringResource(R.string.device_owner_deactivate_button)
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeactivateDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
}
