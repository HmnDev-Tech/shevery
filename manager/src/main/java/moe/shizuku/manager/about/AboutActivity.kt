@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package moe.shizuku.manager.about

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialShapes
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.graphics.shapes.Morph
import moe.shizuku.manager.Helps
import moe.shizuku.manager.ui.compose.ExpressiveSegmentedSection
import moe.shizuku.manager.ui.compose.ExpressiveSegmentedItem
import moe.shizuku.manager.ui.compose.LocalReducedMotion

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
import moe.shizuku.manager.guide.ShakeGuideHost
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

        val versionName = packageManager.getPackageInfo(packageName, 0).versionName ?: getString(R.string.about_version_unknown)

        setContent {
            val scope = rememberCoroutineScope()
            var isCheckingUpdate by remember { mutableStateOf(false) }
            var showSupportDialog by remember { mutableStateOf(false) }
            var appUpdateResult by remember { mutableStateOf<SheveryAppUpdateResult?>(null) }

            ShizukuExpressiveTheme {
                ShakeGuideHost()
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
                            text = stringResource(R.string.about_open_source),
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
                    val shapes = remember {
                        listOf(MaterialShapes.Cookie9Sided, MaterialShapes.Cookie12Sided, MaterialShapes.Burst, MaterialShapes.Sunny)
                    }
                    val reduced = LocalReducedMotion.current
                    val view = LocalView.current
                    val scope = rememberCoroutineScope()
                    var current by remember { mutableIntStateOf(0) }
                    var changing by remember { mutableStateOf(false) }
                    val progress = remember { Animatable(0f) }
                    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
                    val angle = if (reduced) 0f else {
                        val rotation = rememberInfiniteTransition(label = "about-badge")
                        val angle by rotation.animateFloat(0f, 360f, infiniteRepeatable(tween(24_000, easing = LinearEasing)), label = "rotation")
                        angle
                    }
                    val shape = remember(current) { Morph(shapes[current], shapes[(current + 1) % shapes.size]) }
                    val badge = cookieShape { shape.asCubics(progress.value.coerceIn(0f, 1f)) }
                    Box(
                        Modifier.size(112.dp).clickable {
                            if (!changing) {
                                changing = true
                                view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                                scope.launch {
                                    if (!reduced) progress.animateTo(1f, spec)
                                    current = (current + 1) % shapes.size
                                    progress.snapTo(0f)
                                    changing = false
                                }
                                iconTaps++
                                if (iconTaps >= ABOUT_ICON_TAP_THRESHOLD) {
                                    iconTaps = 0
                                    CustomTabsHelper.launchUrlOrCopy(context, ABOUT_ICON_TAP_URL)
                                }
                            }
                        },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(Modifier.size(104.dp).graphicsLayer { rotationZ = angle }
                            .clip(badge).background(MaterialTheme.colorScheme.tertiaryContainer))
                        Image(appIcon, contentDescription = stringResource(R.string.app_name), modifier = Modifier.size(64.dp))
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )

                Spacer(modifier = Modifier.height(8.dp))

                AboutBadge(stringResource(R.string.about_version_value, versionName), MaterialShapes.Cookie4Sided)
                AboutBadge(stringResource(R.string.about_build_value, moe.shizuku.manager.BuildConfig.VERSION_CODE), MaterialShapes.Cookie6Sided)
                AboutBadge(stringResource(R.string.about_license_value), MaterialShapes.Clover4Leaf)

            }
        }
    }

    @Composable
    private fun AboutBadge(label: String, polygon: androidx.graphics.shapes.RoundedPolygon) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
        ) {
            val badge = remember(polygon) {
                cookieShape { polygon.cubics }
            }
            Box(Modifier.size(16.dp).clip(badge).background(MaterialTheme.colorScheme.primary))
            Text(label, style = MaterialTheme.typography.labelLarge)
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
                    text = stringResource(R.string.about_description_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = stringResource(R.string.about_description_body),
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
                    text = stringResource(R.string.about_versioning),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = stringResource(R.string.about_versioning_summary),
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
        val links = listOf(
            R.string.about_source_code_button to "https://github.com/HmnDev-Tech/shevery",
            R.string.about_wiki to Helps.SHEVERY_WIKI,
            R.string.about_releases to "https://github.com/HmnDev-Tech/shevery/releases",
            R.string.about_telegram to "https://t.me/hmndevtech",
            R.string.about_donate to null,
            R.string.about_credits to "https://github.com/HmnDev-Tech/shevery/graphs/contributors"
        )
        ExpressiveSegmentedSection(stringResource(R.string.about_resources)) {
            links.forEachIndexed { index, (label, url) ->
                ExpressiveSegmentedItem(index, links.size, R.drawable.ic_baseline_link_24,
                    stringResource(label), onClick = { if (url == null) onSupportClick() else CustomTabsHelper.launchUrlOrCopy(context, url) })
            }
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

private fun polygonOutline(cubics: List<androidx.graphics.shapes.Cubic>, width: Float, height: Float): Outline {
    val path = Path()
    val start = cubics.first()
    path.moveTo(start.anchor0X * width, start.anchor0Y * height)
    cubics.forEach { cubic ->
        path.cubicTo(cubic.control0X * width, cubic.control0Y * height,
            cubic.control1X * width, cubic.control1Y * height, cubic.anchor1X * width, cubic.anchor1Y * height)
    }
    path.close()
    return Outline.Generic(path)
}

private fun cookieShape(cubics: () -> List<androidx.graphics.shapes.Cubic>): Shape = object : Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        density: androidx.compose.ui.unit.Density
    ): Outline = polygonOutline(cubics(), size.width, size.height)
}
