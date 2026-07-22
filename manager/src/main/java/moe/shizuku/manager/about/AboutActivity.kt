package moe.shizuku.manager.about

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import moe.shizuku.manager.BuildConfig
import moe.shizuku.manager.R
import moe.shizuku.manager.app.AppActivity
import moe.shizuku.manager.ui.compose.ShizukuExpressiveTheme
import moe.shizuku.manager.ui.compose.ShizukuLazyScaffold
import moe.shizuku.manager.ui.compose.SettingsGroup
import moe.shizuku.manager.ui.compose.SettingsRow
import moe.shizuku.manager.ui.compose.GroupDivider
import moe.shizuku.manager.utils.CustomTabsHelper

class AboutActivity : AppActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val versionName = packageManager.getPackageInfo(packageName, 0).versionName ?: "Unknown"

        setContent {
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
                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    item {
                        AboutLinksGroup()
                    }

                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "© 2026 RikkaApps & Community. Open Source Project.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun AboutHeader(versionName: String) {
        val context = androidx.compose.ui.platform.LocalContext.current
        val appIcon = remember {
            val bitmap = android.graphics.BitmapFactory.decodeResource(
                context.resources, R.mipmap.ic_launcher
            )
            bitmap?.asImageBitmap()
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
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
                            .clip(CircleShape)
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
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(
                        text = "Version $versionName",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }

    @Composable
    private fun AboutDescriptionCard() {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
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
                icon = R.drawable.ic_baseline_link_24,
                title = "Website",
                summary = "shizuku.rikka.app",
                onClick = {
                    CustomTabsHelper.launchUrlOrCopy(context, "https://shizuku.rikka.app")
                }
            )
            GroupDivider()
            SettingsRow(
                icon = R.drawable.ic_outline_info_24,
                title = "Support & Channel",
                summary = "Join the community support",
                onClick = {
                    CustomTabsHelper.launchUrlOrCopy(context, "https://t.me/rikkacommunity")
                }
            )
        }
    }
}
