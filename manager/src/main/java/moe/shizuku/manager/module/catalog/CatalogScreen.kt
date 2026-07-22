package moe.shizuku.manager.module.catalog

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import moe.shizuku.manager.R
import moe.shizuku.manager.module.AdbModuleManager
import moe.shizuku.manager.module.ModuleSettings
import moe.shizuku.manager.module.discovery.DiscoveredModule
import moe.shizuku.manager.module.discovery.ModuleDiscoveryManager
import moe.shizuku.manager.module.update.ModuleInstaller
import moe.shizuku.manager.ui.compose.ShizukuExpressiveTheme
import moe.shizuku.manager.ui.compose.ShizukuIcon
import moe.shizuku.manager.ui.compose.ShizukuLazyScaffold
import java.util.Locale

private enum class SortMode { NEWEST, OLDEST, STARS, OFFICIAL }

private const val MAX_BANNER_BYTES = 512 * 1024
private const val MAX_README_CHARS = 16000

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CatalogScreen(onNavigateUp: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val discoveryManager = remember { ModuleDiscoveryManager.getInstance(context) }
    val installer = remember { ModuleInstaller.getInstance() }

    var modules by remember { mutableStateOf<List<DiscoveredModule>>(emptyList()) }
    var installedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var sortMode by remember { mutableStateOf(SortMode.STARS) }
    var showTokenDialog by remember { mutableStateOf(false) }
    var showInstallDialog by remember { mutableStateOf<DiscoveredModule?>(null) }
    var detailModule by remember { mutableStateOf<DiscoveredModule?>(null) }
    var installing by remember { mutableStateOf<String?>(null) }
    var installSuccess by remember { mutableStateOf(false) }
    var token by remember { mutableStateOf<String?>(TokenStore.getToken(context)) }
    var pendingDangerModule by remember { mutableStateOf<DiscoveredModule?>(null) }
    var showDangerDialog by remember { mutableStateOf(false) }
    var dangerReason by remember { mutableStateOf<String?>(null) }
    val client = remember { OkHttpClient() }

    LaunchedEffect(token) {
        if (token.isNullOrBlank()) { showTokenDialog = true; isLoading = false }
        else {
            val cached = discoveryManager.getModules(forceRefresh = false)
            if (cached.isNotEmpty()) { modules = cached; isLoading = false }
            else {
                isLoading = true; error = null
                try { modules = discoveryManager.getModules(forceRefresh = true) }
                catch (e: Exception) { error = e.message }
                finally { isLoading = false }
            }
        }
    }

    LaunchedEffect(modules) {
        installedIds = AdbModuleManager.listModules(context).map { it.id }.toSet()
    }

    val sortedModules = remember(modules, sortMode) {
        when (sortMode) {
            SortMode.NEWEST -> modules.sortedByDescending { it.lastChecked }
            SortMode.OLDEST -> modules.sortedBy { it.lastChecked }
            SortMode.STARS -> modules.sortedByDescending { it.stars }
            SortMode.OFFICIAL -> modules.sortedByDescending { it.isOfficial }
        }
    }

    ShizukuExpressiveTheme {
        AnimatedContent(
            targetState = detailModule,
            transitionSpec = {
                if (targetState != null) {
                    slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it / 3 } + fadeOut()
                } else {
                    slideInHorizontally { -it / 3 } + fadeIn() togetherWith slideOutHorizontally { it } + fadeOut()
                }
            },
            label = "detail-transition"
        ) { activeModule ->
            if (activeModule != null) {
                ModuleDetailScreen(
                    module = activeModule,
                    installed = activeModule.moduleId in installedIds,
                    installing = installing == activeModule.moduleId,
                    onBack = { detailModule = null },
                    onInstall = { showInstallDialog = activeModule },
                    onViewOnGitHub = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(activeModule.repoUrl)))
                    },
                    onRefreshInstalled = {
                        scope.launch { installedIds = AdbModuleManager.listModules(context).map { it.id }.toSet() }
                    }
                )
            } else {
                CatalogListScreen(
                    modules = sortedModules,
                    installedIds = installedIds,
                    isLoading = isLoading,
                    error = error,
                    sortMode = sortMode,
                    installing = installing,
                    onSortChange = { sortMode = it },
                    onRefresh = {
                        scope.launch {
                            isLoading = true; error = null
                            try {
                                modules = discoveryManager.getModules(forceRefresh = true)
                                installedIds = AdbModuleManager.listModules(context).map { it.id }.toSet()
                            } catch (e: Exception) { error = e.message }
                            finally { isLoading = false }
                        }
                    },
                    onCardClick = {
                        scope.launch {
                            val isDanger = withContext(Dispatchers.IO) { checkModuleDanger(client, it) }
                            if (isDanger != null) {
                                pendingDangerModule = it
                                dangerReason = isDanger
                                showDangerDialog = true
                            } else {
                                detailModule = it
                            }
                        }
                    },
                    onInstall = { showInstallDialog = it },
                    onViewOnGitHub = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it.repoUrl)))
                    },
                    onNavigateUp = onNavigateUp
                )
            }
        }

        if (showTokenDialog) {
            TokenInputDialog(
                onDismiss = { showTokenDialog = false; if (TokenStore.getToken(context).isNullOrBlank()) onNavigateUp() },
                onTokenSet = { pat -> TokenStore.setToken(context, pat); token = pat; showTokenDialog = false }
            )
        }

        showInstallDialog?.let { module ->
            InstallModeDialog(
                module = module,
                onDismiss = { showInstallDialog = null },
                onInstall = { mode ->
                    showInstallDialog = null
                    ModuleSettings.setInstallMode(mode)
                    installing = module.moduleId
                    scope.launch {
                        val owner = module.repoFullName.substringBefore('/')
                        val repo = module.repoFullName.substringAfter('/')
                        val result = installer.installModule(context, module.moduleId, owner, repo, module.subPath)
                        installing = null
                        result.fold(
                            onSuccess = {
                                installedIds = AdbModuleManager.listModules(context).map { it.id }.toSet()
                                installSuccess = true
                                Toast.makeText(context, context.getString(R.string.modules_install_success, module.moduleName), Toast.LENGTH_SHORT).show()
                            },
                            onFailure = {
                                Toast.makeText(context, context.getString(R.string.modules_catalog_install_failed, it.message ?: it.javaClass.simpleName), Toast.LENGTH_LONG).show()
                            }
                        )
                    }
                }
            )
        }

        if (installSuccess) {
            AlertDialog(
                onDismissRequest = { installSuccess = false },
                title = { Text(stringResource(R.string.modules_install_success, "")) },
                confirmButton = { TextButton(onClick = { installSuccess = false; onNavigateUp() }) { Text(stringResource(android.R.string.ok)) } }
            )
        }

        if (showDangerDialog) {
            AlertDialog(
                onDismissRequest = { showDangerDialog = false; pendingDangerModule = null },
                title = { Text("Potentially unsafe module") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "This module has unusually large content that may cause issues:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        dangerReason?.let {
                            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.errorContainer) {
                                Text(it, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                        Text("Do you want to continue anyway?", style = MaterialTheme.typography.bodyMedium)
                    }
                },
                confirmButton = {
                    FilledTonalButton(onClick = {
                        showDangerDialog = false
                        detailModule = pendingDangerModule
                        pendingDangerModule = null
                    }) { Text("Continue") }
                },
                dismissButton = {
                    TextButton(onClick = { showDangerDialog = false; pendingDangerModule = null }) { Text("Go back") }
                }
            )
        }
    }
}

private suspend fun checkModuleDanger(client: OkHttpClient, module: DiscoveredModule): String? {
    return withContext(Dispatchers.IO) {
        val owner = module.repoFullName.substringBefore('/')
        val repo = module.repoFullName.substringAfter('/')
        val subPath = module.subPath?.trim('/')?.let { "$it/" } ?: ""
        val candidates = listOf(
            "https://raw.githubusercontent.com/$owner/$repo/main/${subPath}banner.png",
            "https://raw.githubusercontent.com/$owner/$repo/master/${subPath}banner.png",
            "https://raw.githubusercontent.com/$owner/$repo/main/${subPath}banner.jpg",
            "https://raw.githubusercontent.com/$owner/$repo/master/${subPath}banner.jpg"
        )
        for (url in candidates) {
            try {
                val req = Request.Builder().url(url).head().build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val size = resp.header("Content-Length")?.toLongOrNull() ?: 0L
                        if (size > MAX_BANNER_BYTES) return@withContext "Banner too large: ${size / 1024}KB (max ${MAX_BANNER_BYTES / 1024}KB)"
                        return@withContext null
                    }
                }
            } catch (_: Exception) {}
        }
        null
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CatalogListScreen(
    modules: List<DiscoveredModule>,
    installedIds: Set<String>,
    isLoading: Boolean,
    error: String?,
    sortMode: SortMode,
    installing: String?,
    onSortChange: (SortMode) -> Unit,
    onRefresh: () -> Unit,
    onCardClick: (DiscoveredModule) -> Unit,
    onInstall: (DiscoveredModule) -> Unit,
    onViewOnGitHub: (DiscoveredModule) -> Unit,
    onNavigateUp: () -> Unit
) {
    ShizukuLazyScaffold(
        title = stringResource(R.string.modules_catalog_title),
        onNavigateUp = onNavigateUp,
        actions = {
            FilledTonalButton(modifier = Modifier.height(40.dp), onClick = onRefresh) {
                ShizukuIcon(R.drawable.ic_server_restart, modifier = Modifier.padding(end = 6.dp).size(16.dp))
                Text(stringResource(R.string.home_refresh), style = MaterialTheme.typography.labelLarge)
            }
        }
    ) {
        item { SortChipRow(sortMode = sortMode, onSortChange = onSortChange) }

        item {
            AnimatedVisibility(visible = isLoading, enter = fadeIn(), exit = fadeOut()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LoadingIndicator(Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(stringResource(R.string.modules_running), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (!isLoading && error != null) {
            item {
                Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.errorContainer) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(error ?: "Error", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        }

        if (!isLoading && modules.isEmpty() && error == null) {
            item {
                Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 1.dp) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(stringResource(R.string.modules_catalog_empty), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        items(modules, key = { "${it.repoFullName}/${it.moduleId}" }) { module ->
            CatalogModuleCard(
                module = module,
                installed = module.moduleId in installedIds,
                installing = installing == module.moduleId,
                onCardClick = { onCardClick(module) },
                onInstall = { onInstall(module) },
                onViewOnGitHub = { onViewOnGitHub(module) },
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ModuleDetailScreen(
    module: DiscoveredModule,
    installed: Boolean,
    installing: Boolean,
    onBack: () -> Unit,
    onInstall: () -> Unit,
    onViewOnGitHub: () -> Unit,
    onRefreshInstalled: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var readmeContent by remember { mutableStateOf<String?>(null) }
    var isLoadingReadme by remember { mutableStateOf(true) }
    var bannerUrl by remember { mutableStateOf<String?>(null) }
    val client = remember { OkHttpClient() }

    LaunchedEffect(module) {
        isLoadingReadme = true
        val owner = module.repoFullName.substringBefore('/')
        val repo = module.repoFullName.substringAfter('/')
        val subPath = module.subPath?.trim('/')?.let { "$it/" } ?: ""

        withContext(Dispatchers.IO) {
            coroutineScope {
                val bannerDeferred = async {
                    val candidates = listOf(
                        "https://raw.githubusercontent.com/$owner/$repo/main/${subPath}banner.png",
                        "https://raw.githubusercontent.com/$owner/$repo/master/${subPath}banner.png",
                        "https://raw.githubusercontent.com/$owner/$repo/main/${subPath}banner.jpg",
                        "https://raw.githubusercontent.com/$owner/$repo/master/${subPath}banner.jpg"
                    )
                    for (url in candidates) {
                        try {
                            val req = Request.Builder().url(url).head().build()
                            client.newCall(req).execute().use { resp ->
                                if (resp.isSuccessful) return@async url
                            }
                        } catch (_: Exception) {}
                    }
                    null
                }

                val readmeDeferred = async {
                    val candidates = listOf(
                        "https://raw.githubusercontent.com/$owner/$repo/main/README.md",
                        "https://raw.githubusercontent.com/$owner/$repo/master/README.md",
                        "https://raw.githubusercontent.com/$owner/$repo/main/${subPath}README.md",
                        "https://raw.githubusercontent.com/$owner/$repo/master/${subPath}README.md"
                    )
                    for (url in candidates) {
                        try {
                            val req = Request.Builder().url(url).build()
                            val resp = client.newCall(req).execute()
                            if (resp.isSuccessful) {
                                val body = resp.body?.string()?.take(MAX_README_CHARS)
                                resp.close()
                                if (!body.isNullOrBlank()) return@async body
                            }
                        } catch (_: Exception) {}
                    }
                    null
                }

                bannerUrl = bannerDeferred.await()
                readmeContent = readmeDeferred.await()
            }
        }
        isLoadingReadme = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(module.moduleName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onViewOnGitHub) {
                        ShizukuIcon(
                            R.drawable.ic_outline_open_in_new_24,
                            contentDescription = stringResource(R.string.accessibility_open_on_github),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (installed) {
                        FilledTonalButton(
                            modifier = Modifier.weight(1f).height(44.dp),
                            onClick = { onInstall() }
                        ) {
                            Icon(Icons.Rounded.Update, null, modifier = Modifier.size(18.dp).padding(end = 6.dp))
                            Text("Update", style = MaterialTheme.typography.labelLarge)
                        }
                    } else {
                        FilledTonalButton(
                            modifier = Modifier.weight(1f).height(44.dp),
                            enabled = !installing,
                            onClick = onInstall
                        ) {
                            ShizukuIcon(R.drawable.ic_outline_arrow_upward_24, modifier = Modifier.size(18.dp).padding(end = 6.dp))
                            Text(stringResource(R.string.modules_install_zip), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    OutlinedButton(
                        modifier = Modifier.height(44.dp),
                        onClick = onViewOnGitHub
                    ) {
                        ShizukuIcon(R.drawable.ic_outline_open_in_new_24, modifier = Modifier.size(18.dp).padding(end = 6.dp))
                        Text("GitHub", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            item {
                if (bannerUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(bannerUrl).crossfade(true).build(),
                        contentDescription = module.moduleName,
                        modifier = Modifier.fillMaxWidth().height(200.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(120.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(module.moduleName, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    }
                }
            }

            item {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(module.moduleName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        module.author ?: module.repoFullName.substringBefore('/'),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        module.version?.let {
                            DetailChip(text = "v$it", color = MaterialTheme.colorScheme.secondaryContainer, onColor = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                        DetailChip(
                            text = "${module.stars}",
                            icon = { Icon(Icons.Rounded.Star, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer) },
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            onColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        if (module.isOfficial) {
                            DetailChip(text = "Official", color = MaterialTheme.colorScheme.tertiaryContainer, onColor = MaterialTheme.colorScheme.onTertiaryContainer)
                        }
                        if (installed) {
                            DetailChip(text = "Installed", color = MaterialTheme.colorScheme.primaryContainer, onColor = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }

                    module.repoDescription?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            if (module.repoUrl.isNotBlank()) {
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("README", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

                        if (isLoadingReadme) {
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.Center) {
                                LoadingIndicator(Modifier.size(28.dp))
                            }
                        } else if (!readmeContent.isNullOrBlank()) {
                            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.fillMaxWidth()) {
                                ReadmeText(content = readmeContent!!, modifier = Modifier.padding(16.dp))
                            }
                        } else {
                            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                                Text("README not available", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun DetailChip(
    text: String,
    icon: @Composable (() -> Unit)? = null,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    onColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Surface(shape = MaterialTheme.shapes.small, color = color) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon?.invoke()
            Text(text = text, style = MaterialTheme.typography.labelSmall, color = onColor)
        }
    }
}

@Composable
private fun SortChipRow(sortMode: SortMode, onSortChange: (SortMode) -> Unit) {
    val labels = listOf(
        stringResource(R.string.modules_catalog_sort_stars),
        stringResource(R.string.modules_catalog_sort_newest),
        stringResource(R.string.modules_catalog_sort_oldest),
        stringResource(R.string.modules_catalog_sort_official)
    )
    val entries = listOf(SortMode.STARS, SortMode.NEWEST, SortMode.OLDEST, SortMode.OFFICIAL)

    SingleChoiceSegmentedButtonRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        entries.forEachIndexed { index, mode ->
            SegmentedButton(
                shape = SegmentedButtonDefaults.itemShape(index = index, count = entries.size),
                onClick = { onSortChange(mode) },
                selected = sortMode == mode,
                label = { Text(labels[index], style = MaterialTheme.typography.labelSmall) }
            )
        }
    }
}

@Composable
private fun CatalogModuleCard(
    module: DiscoveredModule,
    installed: Boolean,
    installing: Boolean,
    onCardClick: () -> Unit,
    onInstall: () -> Unit,
    onViewOnGitHub: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth().clickable(onClick = onCardClick),
        shape = MaterialTheme.shapes.large,
        color = if (installed) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = if (installed) 0.dp else 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                CatalogAvatar(ownerAvatar = module.ownerAvatar, moduleName = module.moduleName, modifier = Modifier.size(48.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(module.moduleName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(module.author ?: module.repoFullName.substringBefore('/'), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                module.version?.let { DetailChip(text = it, color = MaterialTheme.colorScheme.secondaryContainer, onColor = MaterialTheme.colorScheme.onSecondaryContainer) }
                DetailChip(text = "${module.stars}", icon = { Icon(Icons.Rounded.Star, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer) }, color = MaterialTheme.colorScheme.secondaryContainer, onColor = MaterialTheme.colorScheme.onSecondaryContainer)
                if (module.isOfficial) DetailChip(text = stringResource(R.string.modules_catalog_sort_official), color = MaterialTheme.colorScheme.tertiaryContainer, onColor = MaterialTheme.colorScheme.onTertiaryContainer)
                if (installed) DetailChip(text = "Installed", color = MaterialTheme.colorScheme.primaryContainer, onColor = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            module.repoDescription?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            AnimatedVisibility(visible = installing, enter = fadeIn(), exit = fadeOut()) { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(modifier = Modifier.height(40.dp), enabled = !installing, onClick = onInstall) {
                    ShizukuIcon(R.drawable.ic_outline_arrow_upward_24, modifier = Modifier.padding(end = 6.dp).size(16.dp))
                    Text(stringResource(R.string.modules_install_zip))
                }
                OutlinedButton(modifier = Modifier.height(40.dp), onClick = onViewOnGitHub) {
                    ShizukuIcon(R.drawable.ic_outline_open_in_new_24, modifier = Modifier.padding(end = 6.dp).size(16.dp))
                    Text("GitHub")
                }
            }
        }
    }
}

@Composable
private fun CatalogAvatar(ownerAvatar: String, moduleName: String, modifier: Modifier = Modifier) {
    val initials = remember(moduleName) { moduleName.take(2).uppercase(Locale.getDefault()) }
    if (ownerAvatar.isNotBlank()) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(ownerAvatar).crossfade(true).build(),
            contentDescription = moduleName,
            modifier = modifier.clip(CircleShape),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(modifier = modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Text(initials, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TokenInputDialog(onDismiss: () -> Unit, onTokenSet: (String) -> Unit) {
    var tokenInput by remember { mutableStateOf("") }
    val showWarning = tokenInput.isNotBlank() && !TokenStore.isValidTokenFormat(tokenInput)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.modules_catalog_token_required)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.modules_catalog_token_description), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(value = tokenInput, onValueChange = { tokenInput = it }, label = { Text(stringResource(R.string.modules_catalog_token_hint)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (showWarning) Text(stringResource(R.string.modules_catalog_token_format_warning), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(onClick = { onTokenSet(tokenInput) }, enabled = tokenInput.isNotBlank()) { Text(stringResource(android.R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun InstallModeDialog(module: DiscoveredModule, onDismiss: () -> Unit, onInstall: (ModuleSettings.InstallMode) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(module.moduleName) },
        text = {
            Text(
                text = stringResource(R.string.modules_catalog_install_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    onClick = { onInstall(ModuleSettings.InstallMode.SOURCES) }
                ) {
                    ShizukuIcon(R.drawable.ic_code_24dp, modifier = Modifier.size(18.dp).padding(end = 8.dp))
                    Text(stringResource(R.string.modules_catalog_install_sources), style = MaterialTheme.typography.labelLarge)
                }
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    onClick = { onInstall(ModuleSettings.InstallMode.RELEASE) }
                ) {
                    ShizukuIcon(R.drawable.ic_outline_arrow_upward_24, modifier = Modifier.size(18.dp).padding(end = 8.dp))
                    Text(stringResource(R.string.modules_catalog_install_release), style = MaterialTheme.typography.labelLarge)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

@Composable
private fun ReadmeText(content: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val blockQuoteShape = remember { RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        var inCodeBlock = false
        val codeBlockLines = mutableListOf<String>()

        for (line in content.lines()) {
            val trimmed = line.trimStart()

            if (trimmed.startsWith("```")) {
                if (inCodeBlock) {
                    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Text(
                            text = codeBlockLines.joinToString("\n"),
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    codeBlockLines.clear()
                    inCodeBlock = false
                } else {
                    inCodeBlock = true
                }
                continue
            }
            if (inCodeBlock) { codeBlockLines.add(line); continue }

            val t = trimmed.trim()
            if (t.isBlank()) continue

            val linkColor = MaterialTheme.colorScheme.primary
            when {
                t.startsWith("#### ") -> StyledText(parseInline(t.removePrefix("#### "), linkColor), MaterialTheme.typography.bodySmall, FontWeight.Medium)
                t.startsWith("### ") -> StyledText(parseInline(t.removePrefix("### "), linkColor), MaterialTheme.typography.titleSmall, FontWeight.Medium)
                t.startsWith("## ") -> StyledText(parseInline(t.removePrefix("## "), linkColor), MaterialTheme.typography.titleMedium, FontWeight.SemiBold)
                t.startsWith("# ") -> StyledText(parseInline(t.removePrefix("# "), linkColor), MaterialTheme.typography.titleLarge, FontWeight.Bold)
                t == "---" || t == "***" || t == "___" -> HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
                t.startsWith("> ") -> {
                    Surface(
                        shape = blockQuoteShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.border(BorderStroke(3.dp, linkColor), blockQuoteShape)
                    ) {
                        StyledText(parseInline(t.removePrefix("> "), linkColor), MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 8.dp))
                    }
                }
                t.startsWith("![") -> {
                    val match = Regex("!\\[([^]]*)]\\(([^)]+)\\)").find(t)
                    if (match != null) {
                        val (_, alt, url) = match.groupValues
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(url).crossfade(true).build(),
                            contentDescription = alt.ifBlank { null },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            contentScale = ContentScale.FillWidth
                        )
                    } else {
                        StyledText(parseInline(t, linkColor), MaterialTheme.typography.bodySmall)
                    }
                }
                Regex("^\\d+\\.\\s").containsMatchIn(t) -> {
                    val indent = (line.length - line.trimStart().length) / 2
                    val indentDp = (indent * 16).dp
                    val match = Regex("^(\\d+)\\.\\s(.+)").find(t)!!
                    Row(modifier = Modifier.padding(start = indentDp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${match.groupValues[1]}.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        StyledText(parseInline(match.groupValues[2], linkColor), MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                t.startsWith("- ") || t.startsWith("* ") -> {
                    val indent = (line.length - line.trimStart().length) / 2
                    val indentDp = (indent * 16).dp
                    val itemText = t.removePrefix("- ").removePrefix("* ")
                    Row(modifier = Modifier.padding(start = indentDp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("•", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        StyledText(parseInline(itemText, linkColor), MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                t.startsWith("|") -> {
                    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Text(t, modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                else -> StyledText(parseInline(t, linkColor), MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (inCodeBlock && codeBlockLines.isNotEmpty()) {
            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Text(
                    text = codeBlockLines.joinToString("\n"),
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun StyledText(
    annotated: androidx.compose.ui.text.AnnotatedString,
    style: androidx.compose.ui.text.TextStyle,
    fontWeight: FontWeight? = null,
    color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val styleWithWeight = if (fontWeight != null) style.copy(fontWeight = fontWeight) else style
    val colorSpecified = color != androidx.compose.ui.graphics.Color.Unspecified
    val hasLinks = annotated.getStringAnnotations("URL", 0, annotated.length).isNotEmpty()

    if (hasLinks) {
        Text(
            text = annotated,
            style = if (colorSpecified) styleWithWeight.copy(color = color) else styleWithWeight,
            modifier = modifier.clickable {
                annotated.getStringAnnotations("URL", 0, annotated.length).firstOrNull()?.let {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it.item)))
                }
            }
        )
    } else {
        Text(
            text = annotated,
            style = if (colorSpecified) styleWithWeight.copy(color = color) else styleWithWeight,
            modifier = modifier
        )
    }
}

private fun parseInline(text: String, linkColor: androidx.compose.ui.graphics.Color): androidx.compose.ui.text.AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            when {
                text.startsWith("**", i) && text.indexOf("**", i + 2).let { it > i + 2 } -> {
                    val end = text.indexOf("**", i + 2)
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(i + 2, end)) }
                    i = end + 2
                }
                text[i] == '*' && (i + 1 < text.length) && text[i + 1] != '*' && text.indexOf('*', i + 1).let { it > i + 1 } -> {
                    val end = text.indexOf('*', i + 1)
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(text.substring(i + 1, end)) }
                    i = end + 1
                }
                text[i] == '`' -> {
                    val end = text.indexOf('`', i + 1).let { if (it == -1) text.length else it }
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontSize = (14 * 0.9f).sp)) { append(text.substring(i + 1, end)) }
                    i = end + 1
                }
                text.startsWith("[", i) -> {
                    val match = Regex("^\\[([^]]+)]\\(([^)]+)\\)").find(text, i)
                    if (match != null) {
                        val label = match.groupValues[1]
                        val url = match.groupValues[2]
                        pushStringAnnotation(tag = "URL", annotation = url)
                        withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) { append(label) }
                        pop()
                        i = match.range.last + 1
                    } else {
                        append(text[i]); i++
                    }
                }
                else -> { append(text[i]); i++ }
            }
        }
    }
}
