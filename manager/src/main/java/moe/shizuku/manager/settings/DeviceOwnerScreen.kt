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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val scope = rememberCoroutineScope()
    var isOwner by remember { mutableStateOf(DeviceOwnerManager.isOwner(context)) }
    var isDeviceOwnerActive by remember { mutableStateOf(DeviceOwnerManager.isDeviceOwner(context)) }
    var isProfileOwnerActive by remember { mutableStateOf(DeviceOwnerManager.isProfileOwner(context)) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Device Owner, 1 = Profile Owner
    var showDeactivateDialog by remember { mutableStateOf(false) }
    var isActivating by remember { mutableStateOf(false) }

    val isShizukuRunning = remember {
        try { rikka.shizuku.Shizuku.pingBinder() } catch (_: Throwable) { false }
    }

    val currentCommand = remember(selectedTab, context) {
        if (selectedTab == 1) DeviceOwnerManager.getProfileOwnerAdbCommand(context)
        else DeviceOwnerManager.getDeviceOwnerAdbCommand(context)
    }

    val titleText = when {
        isDeviceOwnerActive -> stringResource(R.string.device_owner_active)
        isProfileOwnerActive -> stringResource(R.string.profile_owner_active)
        else -> stringResource(R.string.device_owner_inactive)
    }
    val descText = when {
        isDeviceOwnerActive -> stringResource(R.string.device_owner_active_desc)
        isProfileOwnerActive -> stringResource(R.string.profile_owner_active_desc)
        selectedTab == 0 -> stringResource(R.string.device_owner_inactive_desc)
        else -> stringResource(R.string.profile_owner_inactive_desc)
    }

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
                    text = titleText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isOwner) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = descText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isOwner) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (!isOwner) {
                    Spacer(modifier = Modifier.height(16.dp))
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text(stringResource(R.string.device_owner_tab)) }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text(stringResource(R.string.profile_owner_tab)) }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    MonospaceLog(text = currentCommand)
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            ClipboardUtils.put(context, currentCommand)
                            Toast.makeText(context, R.string.automation_copied_to_clipboard, Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.home_adb_dialog_view_command_copy_button))
                    }

                    if (isShizukuRunning) {
                        Spacer(modifier = Modifier.height(8.dp))
                        FilledTonalButton(
                            onClick = {
                                isActivating = true
                                scope.launch(Dispatchers.IO) {
                                    val (ok, msg) = DeviceOwnerManager.activateOwnerViaShizuku(context, asProfileOwner = (selectedTab == 1))
                                    withContext(Dispatchers.Main) {
                                        isActivating = false
                                        if (ok) {
                                            isOwner = DeviceOwnerManager.isOwner(context)
                                            isDeviceOwnerActive = DeviceOwnerManager.isDeviceOwner(context)
                                            isProfileOwnerActive = DeviceOwnerManager.isProfileOwner(context)
                                            Toast.makeText(context, R.string.activate_via_shizuku_success, Toast.LENGTH_LONG).show()
                                        } else {
                                            Toast.makeText(context, context.getString(R.string.activate_via_shizuku_failed, msg), Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            },
                            enabled = !isActivating,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.activate_via_shizuku))
                        }
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
            
            Spacer(modifier = Modifier.height(16.dp))

            SettingsGroup(title = stringResource(R.string.device_owner_danger_zone_title)) {
                SettingsRow(
                    icon = R.drawable.ic_delete_24,
                    title = if (isProfileOwnerActive) stringResource(R.string.profile_owner_deactivate_title) else stringResource(R.string.device_owner_deactivate_title),
                    summary = if (isProfileOwnerActive) stringResource(R.string.profile_owner_deactivate_summary) else stringResource(R.string.device_owner_deactivate_summary),
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

        val dialogTitle = if (isProfileOwnerActive) stringResource(R.string.profile_owner_deactivate_dialog_title) else stringResource(R.string.device_owner_deactivate_dialog_title)
        val dialogMessage = if (isProfileOwnerActive) stringResource(R.string.profile_owner_deactivate_dialog_message) else stringResource(R.string.device_owner_deactivate_dialog_message)

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
                Text(dialogTitle)
            },
            text = {
                Text(
                    text = dialogMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeactivateDialog = false
                        fun performDeactivation() {
                            val wasProfileOwner = isProfileOwnerActive
                            val ok = DeviceOwnerManager.clearOwner(context)
                            if (ok) {
                                isOwner = false
                                isDeviceOwnerActive = false
                                isProfileOwnerActive = false
                                val successRes = if (wasProfileOwner) R.string.profile_owner_deactivate_success else R.string.device_owner_deactivate_success
                                Toast.makeText(context, successRes, Toast.LENGTH_LONG).show()
                            } else {
                                val failRes = if (wasProfileOwner) R.string.profile_owner_deactivate_failed else R.string.device_owner_deactivate_failed
                                Toast.makeText(context, failRes, Toast.LENGTH_LONG).show()
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
