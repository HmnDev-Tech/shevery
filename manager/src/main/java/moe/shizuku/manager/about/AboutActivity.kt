@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package moe.shizuku.manager.about

import android.os.Bundle
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
                        AboutContributorsGroup()
                    }

                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    item {
                        AboutLinksGroup()
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
                    text = "Starting with 14.0, Shevery no longer uses the old \"14.0 rXX\" revision scheme. Releases advance as 14.0, 14.1, … up to 14.9, then continue at 15.0–15.9, and so on. The internal git commit count used for versionCode is unchanged.",
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    private data class Contributor(
        val name: String,
        val role: String,
        val url: String
    )

    private val contributors = listOf(
        Contributor(
            "HmnDev-Tech",
            "Project lead — releases, CI, settings hub, watchdog, self-update, docs and project direction",
            "https://github.com/HmnDev-Tech"
        ),
        Contributor(
            "Landon Moran",
            "Core developer — AI providers and Commandium, Material 3 Expressive, ADB hardening, Compose migrations, i18n, code cleanup",
            "https://github.com/LandonMoran"
        ),
        Contributor(
            "kerneldroid",
            "Comput console redesign, module catalog UI, Tasker plugin, legacy API stub, CI signing and module install fixes",
            "https://github.com/kerneldroid"
        ),
        Contributor(
            "OptionString",
            "Expressive floating navigation, Comput M3 redesign, launcher icons, settings layout and release workflow",
            "https://github.com/superman32432432"
        ),
        Contributor(
            "CodexofLost",
            "Material 3 tokens, wireless ADB and starter reliability, permission auth, watchdog and server startup fixes",
            "https://github.com/CodexofLost"
        ),
        Contributor(
            "Codex",
            "Service reliability: ADB lifecycle, TCP mode, watchdog keep-alive, notification controls, dialog migration",
            "https://github.com/codex"
        ),
        Contributor(
            "arysm4a",
            "Wireless-debugging boot autostart, battery-optimization exemption, boot receiver fixes, zh-CN docs",
            "https://github.com/tim1540"
        ),
        Contributor(
            "Jursin",
            "About screen, zh-CN translations, README wording",
            "https://github.com/Jursin"
        ),
        Contributor(
            "Fancy Fonts",
            "Asset and resource updates, translation strings, workflow cleanup",
            "https://github.com/blockawa"
        ),
        Contributor(
            "tura-ai-agent",
            "README localization (Japanese, Chinese) and language links",
            "https://github.com/tura-ai-agent"
        ),
        Contributor(
            "Rikka",
            "Original Shizuku project — the foundation Shevery is built on",
            "https://github.com/RikkaApps"
        ),
        Contributor(
            "iamr0s",
            "Dhizuku project — Device Owner architecture, API sharing and management integrated into Shevery",
            "https://github.com/iamr0s"
        )
    )

    @Composable
    private fun AboutContributorsGroup() {
        val context = this@AboutActivity
        SettingsGroup(title = "Contributors & developers") {
            contributors.forEachIndexed { index, contributor ->
                if (index > 0) GroupDivider()
                SettingsRow(
                    icon = null,
                    title = contributor.name,
                    summary = contributor.role,
                    onClick = {
                        CustomTabsHelper.launchUrlOrCopy(context, contributor.url)
                    }
                )
            }
        }
    }

    @Composable
    private fun AboutLinksGroup() {
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
                icon = R.drawable.ic_outline_info_24,
                title = "Channel",
                summary = "Join the community channel",
                onClick = {
                    CustomTabsHelper.launchUrlOrCopy(context, "https://t.me/hmndevtech")
                }
            )
        }
    }
}
