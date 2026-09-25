@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)

package moe.shizuku.manager.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import moe.shizuku.manager.R
import moe.shizuku.manager.compat.StubManager
import moe.shizuku.manager.security.AuthManager
import moe.shizuku.manager.security.ProtectedAction
import moe.shizuku.manager.ui.compose.ShizukuExpressiveTheme
import moe.shizuku.manager.ui.compose.ShizukuLazyScaffold

@Composable
fun CompatStubsScreen(
    onNavigateUp: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var shizukuInstalled by remember { mutableStateOf(StubManager.isInstalled(context, StubManager.StubType.SHIZUKU)) }
    var dhizukuInstalled by remember { mutableStateOf(StubManager.isInstalled(context, StubManager.StubType.DHIZUKU)) }
    var shizukuVersion by remember { mutableStateOf(StubManager.getInstalledVersion(context, StubManager.StubType.SHIZUKU)) }
    var dhizukuVersion by remember { mutableStateOf(StubManager.getInstalledVersion(context, StubManager.StubType.DHIZUKU)) }

    var busyStub by remember { mutableStateOf<StubManager.StubType?>(null) }

    fun refreshState() {
        shizukuInstalled = StubManager.isInstalled(context, StubManager.StubType.SHIZUKU)
        dhizukuInstalled = StubManager.isInstalled(context, StubManager.StubType.DHIZUKU)
        shizukuVersion = StubManager.getInstalledVersion(context, StubManager.StubType.SHIZUKU)
        dhizukuVersion = StubManager.getInstalledVersion(context, StubManager.StubType.DHIZUKU)
    }

    fun handleStubAction(type: StubManager.StubType, isCurrentlyInstalled: Boolean) {
        AuthManager.executeWithAuth(
            context = context,
            action = ProtectedAction.STUB_MANAGEMENT,
            onSuccess = {
                scope.launch {
                    busyStub = type
                    val result = if (isCurrentlyInstalled) {
                        StubManager.uninstall(context, type)
                    } else {
                        StubManager.install(context, type)
                    }
                    if (result.ok) {
                        val messageRes = if (isCurrentlyInstalled) {
                            R.string.stub_uninstalled_success
                        } else {
                            R.string.stub_installed_success
                        }
                        val stubTitle = context.getString(type.titleRes)
                        val text = if (isCurrentlyInstalled) {
                            context.getString(messageRes, stubTitle)
                        } else {
                            context.getString(messageRes, stubTitle, result.channel)
                        }
                        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
                    } else {
                        val action = if (isCurrentlyInstalled) "uninstall" else "install"
                        Toast.makeText(
                            context,
                            context.getString(
                                R.string.settings_compat_stub_failed,
                                action,
                                result.channel,
                                result.error ?: "failed"
                            ),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    refreshState()
                    busyStub = null
                }
            }
        )
    }

    ShizukuExpressiveTheme {
        ShizukuLazyScaffold(
            title = stringResource(R.string.settings_compat_stubs_title),
            onNavigateUp = onNavigateUp
        ) {
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_system_icon),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Text(
                            text = stringResource(R.string.compat_stubs_header_info),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                StubItemCard(
                    title = stringResource(R.string.stub_shizuku_title),
                    packageName = StubManager.StubType.SHIZUKU.packageName,
                    summary = stringResource(R.string.stub_shizuku_summary),
                    isInstalled = shizukuInstalled,
                    version = shizukuVersion,
                    isBusy = busyStub == StubManager.StubType.SHIZUKU,
                    onActionClick = { handleStubAction(StubManager.StubType.SHIZUKU, shizukuInstalled) }
                )
            }

            item {
                StubItemCard(
                    title = stringResource(R.string.stub_dhizuku_title),
                    packageName = StubManager.StubType.DHIZUKU.packageName,
                    summary = stringResource(R.string.stub_dhizuku_summary),
                    isInstalled = dhizukuInstalled,
                    version = dhizukuVersion,
                    isBusy = busyStub == StubManager.StubType.DHIZUKU,
                    onActionClick = { handleStubAction(StubManager.StubType.DHIZUKU, dhizukuInstalled) }
                )
            }
        }
    }
}

@Composable
private fun StubItemCard(
    title: String,
    packageName: String,
    summary: String,
    isInstalled: Boolean,
    version: String?,
    isBusy: Boolean,
    onActionClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = packageName,
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isInstalled) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    }
                ) {
                    Text(
                        text = if (isInstalled) {
                            if (!version.isNullOrBlank()) {
                                "${stringResource(R.string.stub_status_installed)} v$version"
                            } else {
                                stringResource(R.string.stub_status_installed)
                            }
                        } else {
                            stringResource(R.string.stub_status_not_installed)
                        },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isInstalled) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 3.dp
                    )
                } else if (isInstalled) {
                    Button(
                        onClick = onActionClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(stringResource(R.string.stub_action_uninstall))
                    }
                } else {
                    Button(
                        onClick = onActionClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(stringResource(R.string.stub_action_install))
                    }
                }
            }
        }
    }
}
