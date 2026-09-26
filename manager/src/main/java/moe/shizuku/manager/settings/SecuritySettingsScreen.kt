package moe.shizuku.manager.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import moe.shizuku.manager.R
import moe.shizuku.manager.security.AuthManager
import moe.shizuku.manager.security.SecuritySettings
import moe.shizuku.manager.ui.compose.GroupDivider
import moe.shizuku.manager.ui.compose.SettingsGroup
import moe.shizuku.manager.ui.compose.SettingsRow
import moe.shizuku.manager.ui.compose.SwitchSettingsRow

@Composable
fun SecuritySettingsContent() {
    val context = LocalContext.current
    val activity = context as? FragmentActivity

    var isAuthEnabled by remember { mutableStateOf(SecuritySettings.isAuthEnabled) }
    var timeoutSeconds by remember { mutableIntStateOf(SecuritySettings.timeoutSeconds) }
    var authAppOpen by remember { mutableStateOf(SecuritySettings.authOnAppOpen) }
    var authPermissions by remember { mutableStateOf(SecuritySettings.authOnPermissions) }
    var authServer by remember { mutableStateOf(SecuritySettings.authOnServer) }
    var authDeviceOwner by remember { mutableStateOf(SecuritySettings.authOnDeviceOwner) }
    var authStubs by remember { mutableStateOf(SecuritySettings.authOnStubs) }

    Column(modifier = Modifier.fillMaxWidth()) {
        SettingsGroup(title = stringResource(R.string.settings_security_title)) {
            SwitchSettingsRow(
                icon = R.drawable.ic_security_24dp,
                title = stringResource(R.string.security_require_auth_title),
                summary = stringResource(R.string.security_require_auth_summary),
                checked = isAuthEnabled,
                onCheckedChange = { desiredState ->
                    if (desiredState) {
                        if (activity != null) {
                            AuthManager.authenticate(
                                activity = activity,
                                title = context.getString(R.string.security_auth_prompt_title),
                                onResult = { success ->
                                    if (success) {
                                        SecuritySettings.isAuthEnabled = true
                                        isAuthEnabled = true
                                    } else {
                                        Toast.makeText(context, "Authentication failed", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        } else {
                            SecuritySettings.isAuthEnabled = true
                            isAuthEnabled = true
                        }
                    } else {
                        if (activity != null) {
                            AuthManager.authenticate(
                                activity = activity,
                                title = context.getString(R.string.security_auth_prompt_title),
                                onResult = { success ->
                                    if (success) {
                                        SecuritySettings.isAuthEnabled = false
                                        isAuthEnabled = false
                                        AuthManager.invalidateSession()
                                    }
                                }
                            )
                        } else {
                            SecuritySettings.isAuthEnabled = false
                            isAuthEnabled = false
                            AuthManager.invalidateSession()
                        }
                    }
                }
            )

            if (isAuthEnabled) {
                GroupDivider()
                val timeoutSummary = when (timeoutSeconds) {
                    60 -> stringResource(R.string.security_timeout_1min)
                    300 -> stringResource(R.string.security_timeout_5min)
                    900 -> stringResource(R.string.security_timeout_15min)
                    1800 -> stringResource(R.string.security_timeout_30min)
                    3600 -> stringResource(R.string.security_timeout_1hour)
                    else -> stringResource(R.string.security_timeout_immediately)
                }

                SettingsRow(
                    icon = R.drawable.ic_schedule_24dp,
                    title = stringResource(R.string.security_timeout_title),
                    summary = timeoutSummary,
                    onClick = {
                        val next = when (timeoutSeconds) {
                            0 -> 60
                            60 -> 300
                            300 -> 900
                            900 -> 1800
                            1800 -> 3600
                            else -> 0
                        }
                        timeoutSeconds = next
                        SecuritySettings.timeoutSeconds = next
                    }
                )
            }
        }

        if (isAuthEnabled) {
            SettingsGroup(title = stringResource(R.string.security_protected_actions_title)) {
                SwitchSettingsRow(
                    icon = null,
                    title = stringResource(R.string.security_action_app_open),
                    summary = null,
                    checked = authAppOpen,
                    onCheckedChange = {
                        authAppOpen = it
                        SecuritySettings.authOnAppOpen = it
                    }
                )
                GroupDivider()
                SwitchSettingsRow(
                    icon = null,
                    title = stringResource(R.string.security_action_permissions),
                    summary = null,
                    checked = authPermissions,
                    onCheckedChange = {
                        authPermissions = it
                        SecuritySettings.authOnPermissions = it
                    }
                )
                GroupDivider()
                SwitchSettingsRow(
                    icon = null,
                    title = stringResource(R.string.security_action_server),
                    summary = null,
                    checked = authServer,
                    onCheckedChange = {
                        authServer = it
                        SecuritySettings.authOnServer = it
                    }
                )
                GroupDivider()
                SwitchSettingsRow(
                    icon = null,
                    title = stringResource(R.string.security_action_device_owner),
                    summary = null,
                    checked = authDeviceOwner,
                    onCheckedChange = {
                        authDeviceOwner = it
                        SecuritySettings.authOnDeviceOwner = it
                    }
                )
                GroupDivider()
                SwitchSettingsRow(
                    icon = null,
                    title = stringResource(R.string.security_action_stubs),
                    summary = null,
                    checked = authStubs,
                    onCheckedChange = {
                        authStubs = it
                        SecuritySettings.authOnStubs = it
                    }
                )
            }
        }
    }
}
