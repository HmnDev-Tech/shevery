@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package moe.shizuku.manager.management

import android.content.pm.PermissionInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import moe.shizuku.manager.Helps
import moe.shizuku.manager.R
import moe.shizuku.manager.app.AppActivity
import moe.shizuku.manager.authorization.AuthorizationManager
import moe.shizuku.manager.ui.compose.GroupDivider
import moe.shizuku.manager.ui.compose.SettingsGroup
import moe.shizuku.manager.ui.compose.SettingsRow
import moe.shizuku.manager.ui.compose.ShizukuExpressiveTheme
import moe.shizuku.manager.ui.compose.ShizukuLazyScaffold
import moe.shizuku.manager.ui.compose.SwitchSettingsRow
import moe.shizuku.manager.utils.CustomTabsHelper
import moe.shizuku.manager.utils.ShizukuSystemApis
import moe.shizuku.manager.utils.UserHandleCompat
import rikka.html.text.HtmlCompat
import rikka.shizuku.Shizuku

/**
 * Privileges of a single app: Shizuku access (allowed / denied), whether that
 * access is session-only, the app's own runtime permissions granted through
 * Shizuku, and the package details behind them.
 *
 * Opened from [ApplicationManagementActivity] instead of the list row's old
 * tap-to-toggle behaviour, so a tap on a row always lands here.
 */
class AppDetailActivity : AppActivity() {

    private val permissionTick = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pkgName = intent.getStringExtra(EXTRA_PACKAGE_NAME)
        if (pkgName == null) {
            finish()
            return
        }
        if (!Shizuku.pingBinder()) {
            finish()
            return
        }
        val packageInfo = try {
            // GET_PERMISSIONS populates requestedPermissions (the runtime
            // permission privileges below), GET_META_DATA the requires-root flag.
            packageManager.getPackageInfo(
                pkgName,
                PackageManager.GET_PERMISSIONS or PackageManager.GET_META_DATA
            )
        } catch (e: Exception) {
            finish()
            return
        }
        val applicationInfo = packageInfo.applicationInfo ?: run {
            finish()
            return
        }
        val uid = applicationInfo.uid
        val userId = UserHandleCompat.getUserId(uid)

        setContent {
            val tick = permissionTick.intValue
            var granted by remember(tick) {
                mutableStateOf(
                    try {
                        AuthorizationManager.granted(pkgName, uid)
                    } catch (_: SecurityException) {
                        false
                    }
                )
            }
            val isOneTime = remember(tick) { AuthorizationManager.isOneTime(uid) }
            var showAdbLimitedDialog by remember { mutableStateOf(false) }

            val label = remember(applicationInfo) { applicationInfo.loadLabel(packageManager).toString() }
            val icon = remember(applicationInfo) {
                applicationInfo.loadIcon(packageManager).toBitmap(192, 192).asImageBitmap()
            }
            val versionName = packageInfo.versionName.orEmpty()
            val requiresRoot = remember(applicationInfo) {
                applicationInfo.metaData?.getBoolean("moe.shizuku.client.V3_REQUIRES_ROOT") == true
            }

            // Dangerous (runtime) permissions declared by the app: these are the
            // actual privileges that can be granted or revoked through Shizuku.
            val runtimePermissions = remember(packageInfo) {
                packageInfo.requestedPermissions.orEmpty().mapNotNull { permission ->
                    val info = try {
                        packageManager.getPermissionInfo(permission, 0)
                    } catch (_: Exception) {
                        null
                    } ?: return@mapNotNull null
                    // protectionLevel & 0xf == the base type; PROTECTION_MASK_BASE is not public API.
                    val base = info.protectionLevel and 0xf
                    if (base != PermissionInfo.PROTECTION_DANGEROUS) return@mapNotNull null
                    permission to info.loadLabel(packageManager).toString()
                }
            }
            val runtimePermissionStates = remember(tick, runtimePermissions) {
                runtimePermissions.associate { (permission, _) ->
                    val isGranted = try {
                        ShizukuSystemApis.checkPermission(permission, pkgName, userId) == PackageManager.PERMISSION_GRANTED
                    } catch (_: Exception) {
                        false
                    }
                    permission to isGranted
                }
            }

            fun applyGrant(value: Boolean) {
                try {
                    if (value) {
                        AuthorizationManager.grant(pkgName, uid)
                    } else {
                        AuthorizationManager.revoke(pkgName, uid)
                    }
                    granted = value
                    permissionTick.intValue++
                    setResult(RESULT_OK)
                } catch (_: SecurityException) {
                    val serverUid = try {
                        Shizuku.getUid()
                    } catch (_: Throwable) {
                        return
                    }
                    if (serverUid != 0) {
                        showAdbLimitedDialog = true
                    }
                }
            }

            fun setSessionOnly(value: Boolean) {
                try {
                    if (value) {
                        // Keep access for this session only; do not revoke on the server.
                        AuthorizationManager.grantSessionOnly(pkgName, uid)
                    } else {
                        AuthorizationManager.grant(pkgName, uid)
                        Toast.makeText(this@AppDetailActivity, R.string.app_management_made_permanent, Toast.LENGTH_SHORT).show()
                    }
                    permissionTick.intValue++
                    setResult(RESULT_OK)
                } catch (_: SecurityException) {
                    val serverUid = try {
                        Shizuku.getUid()
                    } catch (_: Throwable) {
                        return
                    }
                    if (serverUid != 0) {
                        showAdbLimitedDialog = true
                    }
                }
            }

            fun setRuntimePermission(permission: String, value: Boolean) {
                try {
                    if (value) {
                        ShizukuSystemApis.grantRuntimePermission(pkgName, permission, userId)
                    } else {
                        ShizukuSystemApis.revokeRuntimePermission(pkgName, permission, userId)
                    }
                    permissionTick.intValue++
                    setResult(RESULT_OK)
                } catch (_: Exception) {
                    val serverUid = try {
                        Shizuku.getUid()
                    } catch (_: Throwable) {
                        return
                    }
                    if (serverUid != 0) {
                        showAdbLimitedDialog = true
                    }
                }
            }

            ShizukuExpressiveTheme {
                ShizukuLazyScaffold(
                    title = label,
                    onNavigateUp = { finish() }
                ) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.extraLarge,
                            color = MaterialTheme.colorScheme.surfaceContainerLow
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Surface(
                                    modifier = Modifier.size(64.dp),
                                    shape = RoundedCornerShape(18.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                                ) {
                                    Image(
                                        bitmap = icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(64.dp)
                                    )
                                }
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = packageInfo.packageName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (versionName.isNotBlank()) {
                                            VersionBadge(text = "v$versionName")
                                        }
                                        if (requiresRoot) {
                                            VersionBadge(
                                                text = stringResource(R.string.app_management_item_summary_requires_root),
                                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        SettingsGroup(title = stringResource(R.string.app_detail_privileges_title)) {
                            SwitchSettingsRow(
                                icon = R.drawable.ic_security_24dp,
                                title = stringResource(R.string.app_detail_shizuku_access),
                                checked = granted,
                                onCheckedChange = { value -> applyGrant(value) },
                                summary = if (granted) {
                                    stringResource(R.string.app_detail_state_allowed)
                                } else {
                                    stringResource(R.string.app_detail_state_denied)
                                }
                            )
                            GroupDivider()
                            SwitchSettingsRow(
                                icon = R.drawable.ic_schedule_24dp,
                                title = stringResource(R.string.app_detail_session_only),
                                checked = isOneTime,
                                onCheckedChange = { value -> setSessionOnly(value) },
                                summary = stringResource(R.string.app_detail_session_only_summary)
                            )
                        }
                    }

                    item {
                        SettingsGroup(title = stringResource(R.string.app_detail_runtime_permissions)) {
                            if (runtimePermissions.isEmpty()) {
                                SettingsRow(
                                    icon = null,
                                    title = stringResource(R.string.app_detail_runtime_permissions_empty)
                                )
                            } else {
                                runtimePermissions.forEachIndexed { index, permissionEntry ->
                                    val permission = permissionEntry.first
                                    val permLabel = permissionEntry.second
                                    if (index > 0) {
                                        GroupDivider()
                                    }
                                    SwitchSettingsRow(
                                        icon = null,
                                        title = permLabel,
                                        summary = permission,
                                        checked = runtimePermissionStates[permission] == true,
                                        onCheckedChange = { value -> setRuntimePermission(permission, value) }
                                    )
                                }
                            }
                        }
                    }

                    item {
                        SettingsGroup(title = stringResource(R.string.app_detail_info_title)) {
                            SettingsRow(
                                icon = null,
                                title = stringResource(R.string.app_detail_package),
                                summary = packageInfo.packageName
                            )
                            GroupDivider()
                            SettingsRow(
                                icon = null,
                                title = stringResource(R.string.app_detail_version),
                                summary = if (versionName.isBlank()) {
                                    stringResource(R.string.app_detail_version_unknown)
                                } else {
                                    versionName
                                }
                            )
                            GroupDivider()
                            SettingsRow(
                                icon = null,
                                title = stringResource(R.string.app_detail_uid),
                                summary = uid.toString()
                            )
                        }
                    }
                }

                if (showAdbLimitedDialog) {
                    AlertDialog(
                        onDismissRequest = { showAdbLimitedDialog = false },
                        title = {
                            Text(
                                text = stringResource(R.string.app_management_dialog_adb_is_limited_title),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        text = {
                            Text(
                                text = HtmlCompat.fromHtml(
                                    getString(R.string.app_management_dialog_adb_is_limited_message, Helps.ADB.get()),
                                    HtmlCompat.FROM_HTML_OPTION_TRIM_WHITESPACE
                                ).toString(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        confirmButton = {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        CustomTabsHelper.launchUrlOrCopy(this@AppDetailActivity, Helps.ADB.get())
                                    }
                                ) {
                                    Text(stringResource(R.string.home_adb_button_view_help))
                                }
                                Button(onClick = { showAdbLimitedDialog = false }) {
                                    Text(stringResource(android.R.string.ok))
                                }
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = MaterialTheme.shapes.extraLarge
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        permissionTick.intValue++
    }

    companion object {
        const val EXTRA_PACKAGE_NAME = "package_name"
    }
}

@Composable
private fun VersionBadge(
    text: String,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = containerColor
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
