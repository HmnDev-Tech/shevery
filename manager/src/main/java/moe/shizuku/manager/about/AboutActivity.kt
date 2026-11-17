@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package moe.shizuku.manager.about

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.launch
import moe.shizuku.manager.module.ModuleSettings
import moe.shizuku.manager.module.update.SheveryAppUpdateDialog
import moe.shizuku.manager.module.update.SheveryAppUpdateResult
import moe.shizuku.manager.module.update.SheveryUpdateChecker
import moe.shizuku.manager.R
import moe.shizuku.manager.app.AppActivity
import moe.shizuku.manager.ui.compose.ShizukuExpressiveTheme
import moe.shizuku.manager.ui.compose.ShizukuLazyScaffold
import moe.shizuku.manager.ui.compose.SettingsGroup
import moe.shizuku.manager.ui.compose.SettingsRow
import moe.shizuku.manager.ui.compose.GroupDivider
import moe.shizuku.manager.utils.CustomTabsHelper

private const val ABOUT_ICON_TAP_THRESHOLD = 7
private const val ABOUT_ICON_TAP_URL = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"

class AboutActivity : AppActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val versionName = packageManager.getPackageInfo(packageName, 0).versionName ?: "Unknown"

        setContent {
            val scope = rememberCoroutineScope()
            var isCheckingUpdate by remember { mutableStateOf(false) }
            var showSupportDialog by remember { mutableStateOf(false) }
            var appUpdateResult by remember { mutableStateOf<SheveryAppUpdateResult?>(null) }

            ShizukuExpressiveTheme {
                ShizukuLazyScaffold(
                    title = stringResource(R.string.action_about),
                    onNavigateUp = { finish() }
                ) {
                    item {
                        AboutHeader(versionName)
                    }
                    
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    item {
                        AboutDescriptionCard()
                    }

                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    item {
                        SettingsGroup(title = stringResource(R.string.shevery_update_group_title)) {
                            SettingsRow(
                                icon = R.drawable.ic_outline_notifications_active_24,
                                title = stringResource(R.string.shevery_update_check_title),
                                summary = stringResource(R.string.shevery_update_check_summary),
                                onClick = {
                                    scope.launch {
                                        isCheckingUpdate = true
                                        val result = SheveryUpdateChecker.getInstance().checkAppUpdate(this@AboutActivity)
                                        // Keep the home card in sync: refresh pending update on a live check,
                                        // and clear it once no newer release exists (so it never advertises an old version.

                                        if (result.error == null) {
                                            if (result.hasUpdate && result.downloadUrl != null) {
                                                ModuleSettings.setPendingUpdate(
                                                    version = result.latestVersion ?: "",
                                                    url = result.downloadUrl ?: "",
                                                    detectedAt = System.currentTimeMillis()
                                                )
                                            } else {
                                                ModuleSettings.clearPendingUpdate()
                                            }
                                        }
                                        appUpdateResult = result
                                        isCheckingUpdate = false
                                    }
                                }
                            )
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    item {
                        AboutVersioningCard()
                    }

                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    item {
                        AboutLinksGroup(onSupportClick = { showSupportDialog = true })
                    }

                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "Open Source Project",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)
                        )
                    }
                }

                if (isCheckingUpdate) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = {},
                        title = { Text(stringResource(R.string.shevery_update_check_title)) },
                        text = {
                            androidx.compose.foundation.layout.Row(
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
                                modifier = Modifier.padding(vertical = 8.dp)
                            ) {
                                LoadingIndicator(modifier = Modifier.size(28.dp))
                                Text(stringResource(R.string.shevery_update_checking))
                            }
                        },
                        confirmButton = {},
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = MaterialTheme.shapes.extraLarge
                    )
                }

                if (showSupportDialog) {
                    SupportDialog(onDismiss = { showSupportDialog = false })
                }

                appUpdateResult?.let { result ->
                    SheveryAppUpdateDialog(
                        result = result,
                        onDismiss = { appUpdateResult = null }
                    )
                }
            }
        }
    }

    @Composable
    private fun AboutHeader(versionName: String) {
        val context = androidx.compose.ui.platform.LocalContext.current
        var iconTaps by remember { mutableIntStateOf(0) }
        val appIcon = remember(context) {
            runCatching {
                val drawable = context.packageManager.getApplicationIcon(context.packageName)
                val bitmap = drawable.toBitmap(192, 192)
                bitmap.asImageBitmap()
            }.getOrNull()
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(vertical = 32.dp, horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (appIcon != null) {
                    Image(
                        bitmap = appIcon,
                        contentDescription = "App Icon",
                        modifier = Modifier
                            .size(96.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .clickable {
                                iconTaps++
                                if (iconTaps >= ABOUT_ICON_TAP_THRESHOLD) {
                                    iconTaps = 0
                                    CustomTabsHelper.launchUrlOrCopy(context, ABOUT_ICON_TAP_URL)
                                }
                            }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )

                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(
                        text = "Version $versionName",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }

            }
        }
    }

    @Composable
    private fun AboutDescriptionCard() {
        Card(
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "What is Shizuku?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "Shizuku is a powerful and secure service that bridges normal user-space applications with privileged system APIs directly through root or ADB bindings.",
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    @Composable
    private fun AboutVersioningCard() {
        Card(
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "Versioning",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "Starting with 14.0, Shevery no longer uses the old \"14.0 rXX\" revision scheme. Releases advance as 14.0, 14.1, 14.2, … up to 14.9, then continue at 15.0–15.9, and so on. The internal git commit count used for versionCode is unchanged.",
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    @Composable
    private fun AboutLinksGroup(onSupportClick: () -> Unit) {
        val context = this@AboutActivity
        SettingsGroup(title = "Resources & Community") {
            SettingsRow(
                icon = R.drawable.ic_baseline_link_24,
                title = stringResource(R.string.about_source_code_button),
                summary = "github.com/HmnDev-Tech/shevery",
                onClick = {
                    CustomTabsHelper.launchUrlOrCopy(context, "https://github.com/HmnDev-Tech/shevery")
               }
            )
            GroupDivider()
            SettingsRow(
                icon = R.drawable.ic_outline_favorite_24,
                title = "Support the project",
                summary = "Crypto: TON, TRON, EVM",
                onClick = onSupportClick
            )
            GroupDivider()
            SettingsRow(
                icon = R.drawable.ic_outline_info_24,
                title = "Channel",
                summary = "Join the community channel",
                onClick = {
                    CustomTabsHelper.launchUrlOrCopy(context, "https://t.me/hmndevtech")
                }
            )
        }
    }

    @Composable
    private fun SupportDialog(onDismiss: () -> Unit) {
        val wallets = listOf(
            Triple("TON", "TON, USDT (TON), Jettons", "UQBgVFTqbaG5WbOUpvFXbUBX05PWaS2tsoAb8BvPdcOU1WYN"),
            Triple("TRON (TRC-20)", "TRX, USDT (TRC-20)", "TPwfJNxhueYLZJUCpnpasm4h1QDqnZPsKL"),
            Triple("EVM (Ethereum, BSC, Polygon, Arbitrum, Base)", "ETH, BNB, POL, USDT, USDC", "0xe6d48DB4f8d874A0Fd0807ca48E6c75426F6060c")
        )
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Support the project") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "If you find this project useful, you can support its development via cryptocurrency transfers. Tap an address to copy it.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    wallets.forEach { (network, assets, address) ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    getSystemService(ClipboardManager::class.java)
                                        .setPrimaryClip(ClipData.newPlainText(network, address))
                                    Toast.makeText(this@AboutActivity, "Copied: $network", Toast.LENGTH_SHORT).show()
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            Text(network, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(assets, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Text(
                        text = "Verify the network before sending funds. Transfers sent to an unsupported network cannot be recovered.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.ok)) } },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.extraLarge
        )
    }
}
