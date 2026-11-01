@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)

package moe.shizuku.manager.module


import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import moe.shizuku.manager.R
import moe.shizuku.manager.module.catalog.TokenStore
import moe.shizuku.manager.module.discovery.ModuleDiscoveryManager
import moe.shizuku.manager.module.update.UpdateChecker
import moe.shizuku.manager.ui.compose.ExpressiveSwitch
import moe.shizuku.manager.ui.compose.ShizukuIcon
import moe.shizuku.manager.ui.compose.ShizukuLazyScaffold
import moe.shizuku.manager.utils.AiExplainUtil

private val MODULE_MIME_TYPES = arrayOf(
    "application/zip",
    "application/x-zip",
    "application/octet-stream",
    "application/x-zip-compressed"
)

@Composable
fun ModulesScreen(
    onOpenWebUi: (String) -> Unit,
    onCreateShortcut: ((AdbModule) -> Unit)? = null,
    listState: LazyListState = rememberLazyListState(),
    modulesState: MutableState<List<AdbModule>>
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var showCatalog by remember { mutableStateOf(false) }
    var modules by modulesState
    var checkingUpdates by remember { mutableStateOf(false) }
    var updatingModuleId by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<AdbModule?>(null) }
    var pendingCommand by remember { mutableStateOf<ModuleCommandRequest?>(null) }
    var runningModuleId by remember { mutableStateOf<String?>(null) }
    var aiExplanation by remember { mutableStateOf<String?>(null) }
    var aiLoading by remember { mutableStateOf(false) }
    val liveOutput by AdbModuleManager.liveOutput.collectAsState()
    val reloadTick by AdbModuleManager.modulesReloadTick.collectAsState()
    var liveLogHidden by remember { mutableStateOf(false) }

    LaunchedEffect(liveOutput?.moduleId, liveOutput?.kind, liveOutput?.running) {
        if (liveOutput?.running == true) {
            liveLogHidden = false
            aiExplanation = null
            aiLoading = false
        }
    }

    fun reload() {
        scope.launch {
            modules = AdbModuleManager.listModules(context)
        }
    }

    LaunchedEffect(reloadTick) {
        modules = AdbModuleManager.listModules(context)
    }

    fun checkAllUpdates() {
        scope.launch {
            checkingUpdates = true
            Toast.makeText(context, context.getString(R.string.modules_checking_updates), Toast.LENGTH_SHORT).show()
            val checker = UpdateChecker.getInstance()
            val discoveryManager = ModuleDiscoveryManager.getInstance(context)
            val catalogModules = runCatching { discoveryManager.getModules(forceRefresh = false) }.getOrDefault(emptyList())
            val githubPat = TokenStore.getToken(context)
            var foundUpdates = 0
            val updated = modules.map { m ->
                val result = checker.checkUpdate(m, catalogModules, githubPat)
                val info = if (result.hasUpdate && result.latestVersion != null) {
                    foundUpdates++
                    ModuleUpdateInfo(
                        newVersion = result.latestVersion,
                        newVersionCode = result.latestVersionCode,
                        zipUrl = result.downloadUrl ?: "",
                        changelog = result.changelog,
                        repoOwner = result.repoOwner,
                        repoName = result.repoName,
                        subPath = result.subPath
                    )
                } else null
                m.copy(updateInfo = info)
            }
            modules = updated
            checkingUpdates = false
            val msg = if (foundUpdates > 0) {
                context.getString(R.string.modules_update_available, "$foundUpdates")
            } else {
                context.getString(R.string.modules_no_updates)
            }
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    fun updateModule(module: AdbModule) {
        val info = module.updateInfo ?: return
        liveLogHidden = false
        aiExplanation = null
        aiLoading = false
        updatingModuleId = module.id
        if (!info.repoOwner.isNullOrBlank() && !info.repoName.isNullOrBlank()) {
            AdbModuleManager.launchCatalogInstall(
                context = context,
                moduleId = module.id,
                moduleName = module.name,
                owner = info.repoOwner,
                repo = info.repoName,
                subPath = info.subPath
            ) { result ->
                updatingModuleId = null
                result.onSuccess {
                    Toast.makeText(
                        context,
                        context.getString(R.string.modules_update_success, info.newVersion),
                        Toast.LENGTH_SHORT
                    ).show()
                    reload()
                }.onFailure {
                    Toast.makeText(
                        context,
                        "Update failed: ${it.message ?: it.javaClass.simpleName}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        } else if (info.zipUrl.startsWith("http://") || info.zipUrl.startsWith("https://")) {
            AdbModuleManager.launchUrlUpdateInstall(
                context = context,
                module = module,
                zipUrl = info.zipUrl,
                newVersion = info.newVersion
            ) { result ->
                updatingModuleId = null
                result.onSuccess {
                    Toast.makeText(
                        context,
                        context.getString(R.string.modules_update_success, info.newVersion),
                        Toast.LENGTH_SHORT
                    ).show()
                    reload()
                }.onFailure {
                    Toast.makeText(
                        context,
                        "Update failed: ${it.message ?: it.javaClass.simpleName}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        } else {
            updatingModuleId = null
            Toast.makeText(context, "Update failed: No valid update source", Toast.LENGTH_LONG).show()
        }
    }

    fun runModuleAction(module: AdbModule) {
        liveLogHidden = false
        aiExplanation = null
        aiLoading = false
        runningModuleId = module.id
        AdbModuleManager.launchScriptExecution(
            module = module,
            isService = false
        ) {
            runningModuleId = null
        }
    }

    fun runModuleService(module: AdbModule) {
        liveLogHidden = false
        aiExplanation = null
        aiLoading = false
        runningModuleId = module.id
        AdbModuleManager.launchScriptExecution(
            module = module,
            isService = true
        ) {
            runningModuleId = null
        }
    }

    val zipLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        liveLogHidden = false
        aiExplanation = null
        aiLoading = false
        AdbModuleManager.launchInstallFromUri(
            context = context,
            uri = uri,
            displayName = uri.lastPathSegment?.substringAfterLast('/') ?: "ZIP Module"
        ) { result ->
            result.onSuccess { module ->
                Toast.makeText(
                    context,
                    context.getString(R.string.modules_install_success, module.name),
                    Toast.LENGTH_SHORT
                ).show()
                reload()
            }.onFailure {
                Toast.makeText(
                    context,
                    context.getString(R.string.modules_install_failed, it.message ?: it.javaClass.simpleName),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    if (showCatalog) {
        moe.shizuku.manager.module.catalog.CatalogScreen(
            onNavigateUp = {
                showCatalog = false
                reload()
            }
        )
    } else {
        ShizukuLazyScaffold(
            title = stringResource(R.string.modules_title),
            onNavigateUp = null,
            bottomInset = 112.dp,
            listState = listState,
            isRefreshing = checkingUpdates,
            onRefresh = {
                view.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
                checkAllUpdates()
            },
            actions = {
                ModuleTabsSwitcher(
                    onCatalog = { showCatalog = true }
                )
                Spacer(modifier = Modifier.size(8.dp))
                FilledTonalButton(
                    modifier = Modifier.height(36.dp),
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                    onClick = {
                        zipLauncher.launch(MODULE_MIME_TYPES)
                    }
                ) {
                    ShizukuIcon(
                        R.drawable.ic_outline_arrow_upward_24,
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .size(16.dp)
                    )
                    Text(stringResource(R.string.modules_install_zip))
                }
            }
        ) {
            item {
                AnimatedContent(targetState = modules.isEmpty(), label = "module-empty-state") { empty ->
                    if (empty) {
                        EmptyModulesCard(onInstall = { zipLauncher.launch(MODULE_MIME_TYPES) })
                    }
                }
            }
            items(modules, key = { it.id }) { module ->
                ModuleCard(
                    module = module,
                    busy = runningModuleId == module.id,
                    updating = updatingModuleId == module.id,
                    trusted = ModuleSettings.isModuleTrusted(module.id),
                    modifier = Modifier.animateItem(),
                    onToggle = {
                        scope.launch {
                            AdbModuleManager.setEnabled(module, !module.enabled)
                            reload()
                        }
                    },
                    onRunAction = {
                        if (ModuleSettings.recommandForAction()) {
                            pendingCommand = ModuleCommandRequest(
                                module = module,
                                source = ModuleCommandSource.ACTION,
                                command = module.actionCommandPreview()
                            )
                        } else {
                            runModuleAction(module)
                        }
                    },
                    onRunService = {
                        runModuleService(module)
                    },
                    onOpenWebUi = {
                        onOpenWebUi(module.id)
                    },
                    onCreateShortcut = {
                        onCreateShortcut?.invoke(module) ?: ModuleShortcutHelper.addModuleShortcut(context, module)
                    },
                    onDelete = { deleteTarget = module },
                    onTrustChange = { trusted ->
                        scope.launch {
                            ModuleSettings.setModuleTrusted(module.id, trusted)
                            // Trust controls permissions, not the enabled/disabled switch.
                            reload()
                        }
                    },
                    onUpdateModule = { updateModule(module) }
                )
            }
        }
    }

    liveOutput?.takeIf { !liveLogHidden }?.let { live ->
        ModuleLiveConsoleDialog(
            live = live,
            aiExplanation = aiExplanation,
            aiLoading = aiLoading,
            onAskAi = {
                val apiKey = ModuleSettings.getComputApiKey()
                if (apiKey.isBlank()) {
                    Toast.makeText(context, context.getString(R.string.comput_ai_no_active_toast), Toast.LENGTH_SHORT).show()
                } else {
                    aiLoading = true
                    scope.launch {
                        val moduleInfo = "Module: ${live.moduleName} (${live.moduleId})"
                        val scriptInfo = live.subtitle.ifBlank { live.kind.name }
                        aiExplanation = AiExplainUtil.explainFailure(
                            contextStr = "Shevery Android app, module ${live.kind.name.lowercase()}",
                            inputDetail = "$moduleInfo, target = $scriptInfo",
                            outputLog = live.text,
                            apiKey = apiKey
                        )
                        aiLoading = false
                    }
                }
            },
            onRunAction = live.module?.takeIf {
                !live.running &&
                    live.kind == AdbModuleManager.ConsoleKind.INSTALL &&
                    live.succeeded &&
                    it.enabled &&
                    it.hasAction &&
                    ModuleSettings.canRunAction(it)
            }?.let { installedModule ->
                {
                    if (ModuleSettings.recommandForAction()) {
                        AdbModuleManager.clearLiveOutput()
                        pendingCommand = ModuleCommandRequest(
                            module = installedModule,
                            source = ModuleCommandSource.ACTION,
                            command = installedModule.actionCommandPreview()
                        )
                    } else {
                        runModuleAction(installedModule)
                    }
                }
            },
            onRerun = live.module?.takeIf {
                !live.running &&
                    (live.kind == AdbModuleManager.ConsoleKind.ACTION || live.kind == AdbModuleManager.ConsoleKind.SERVICE)
            }?.let { targetModule ->
                {
                    if (live.kind == AdbModuleManager.ConsoleKind.SERVICE) {
                        runModuleService(targetModule)
                    } else {
                        runModuleAction(targetModule)
                    }
                }
            },
            onHide = { liveLogHidden = true },
            onClose = {
                aiExplanation = null
                aiLoading = false
                AdbModuleManager.clearLiveOutput()
            }
        )
    }

    deleteTarget?.let { module ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.modules_delete_title)) },
            text = { Text(stringResource(R.string.modules_delete_message, module.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            AdbModuleManager.delete(module)
                            deleteTarget = null
                            reload()
                        }
                    }
                ) {
                    Text(stringResource(R.string.modules_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.extraLarge
        )
    }

    pendingCommand?.let { request ->
        ReCommandDialog(
            request = request,
            busy = runningModuleId == request.module.id,
            onDismiss = { pendingCommand = null },
            onReject = { pendingCommand = null },
            onApprove = {
                pendingCommand = null
                runModuleAction(request.module)
            }
        )
    }
}

private fun AdbModule.actionCommandPreview(): String {
    val script = actionScript ?: return "sh action.sh"
    val content = runCatching { script.readText().trim() }.getOrDefault("")
    return buildString {
        append("sh ")
        append(script.absolutePath)
        if (content.isNotBlank()) {
            appendLine()
            appendLine()
            append(content)
        }
    }.take(MAX_RECOMMAND_COMMAND_CHARS)
}

private const val MAX_RECOMMAND_COMMAND_CHARS = 64 * 1024

@Composable
private fun EmptyModulesCard(onInstall: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.modules_empty_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.modules_empty_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FilledTonalButton(onClick = onInstall) {
                ShizukuIcon(
                    R.drawable.ic_outline_arrow_upward_24,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(18.dp)
                )
                Text(stringResource(R.string.modules_install_zip))
            }
        }
    }
}

@Composable
private fun ModuleCard(
    module: AdbModule,
    busy: Boolean,
    updating: Boolean,
    trusted: Boolean,
    modifier: Modifier = Modifier,
    onToggle: () -> Unit,
    onRunAction: () -> Unit,
    onRunService: () -> Unit,
    onOpenWebUi: () -> Unit,
    onCreateShortcut: () -> Unit,
    onDelete: () -> Unit,
    onTrustChange: (Boolean) -> Unit,
    onUpdateModule: () -> Unit
) {
    var expanded by remember(module.id) { mutableStateOf(true) }
    var showTrustAction by remember(module.id, trusted) { mutableStateOf(false) }
    val containerColor by animateColorAsState(
        targetValue = if (trusted) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else if (module.enabled) {
            MaterialTheme.colorScheme.surfaceContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLowest
        },
        animationSpec = tween(260),
        label = "module-container"
    )
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .combinedClickable(
                onClick = { expanded = !expanded },
                onLongClick = {
                    expanded = true
                    showTrustAction = !showTrustAction
                }
            )
            .animateContentSize(animationSpec = tween(280)),
        shape = MaterialTheme.shapes.extraLarge,
        color = containerColor,
        tonalElevation = 1.dp
    ) {
        Column {
            ModuleBanner(module)
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                module.updateInfo?.let { updateInfo ->
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.modules_update_available, updateInfo.newVersion),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Button(
                                onClick = onUpdateModule,
                                enabled = !updating,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                if (updating) {
                                    LoadingIndicator(modifier = Modifier.size(16.dp))
                                } else {
                                    Text(stringResource(R.string.modules_update_button))
                                }
                            }
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = module.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        ModuleInfoLine(label = R.string.modules_version_label, value = module.version ?: "-")
                        ModuleInfoLine(label = R.string.modules_author_label, value = module.author ?: "-")
                    }
                    ExpressiveSwitch(
                        checked = module.enabled,
                        onCheckedChange = { onToggle() }
                    )
                }
                module.description?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (expanded) 4 else 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                ModuleChips(module, trusted)
                AnimatedVisibility(
                    visible = busy,
                    enter = fadeIn(tween(180)) + expandVertically(tween(180)),
                    exit = fadeOut(tween(120)) + shrinkVertically(tween(120))
                ) {
                    RunningRow()
                }
                AnimatedVisibility(
                    visible = expanded,
                    enter = fadeIn(tween(180)) + expandVertically(tween(220)),
                    exit = fadeOut(tween(120)) + shrinkVertically(tween(160))
                ) {
                    ModuleActions(
                        module = module,
                        busy = busy,
                        onRunAction = onRunAction,
                        onRunService = onRunService,
                        onOpenWebUi = onOpenWebUi,
                        onCreateShortcut = onCreateShortcut,
                        onDelete = onDelete,
                        trusted = trusted,
                        showTrustAction = showTrustAction,
                        onTrustChange = {
                            showTrustAction = false
                            onTrustChange(it)
                        }
                    )
                }
            }
        }
    }
}



@Composable
private fun ModuleChips(module: AdbModule, trusted: Boolean) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AssistChip(
            onClick = {},
            label = { Text(module.id) }
        )
        AssistChip(onClick = {}, label = { Text(module.formattedSize) })
        if (trusted) {
            AssistChip(onClick = {}, label = { Text(stringResource(R.string.modules_full_trust)) })
        }
    }
}

@Composable
private fun ModuleBanner(module: AdbModule) {
    val bannerFile = module.banner
    if (bannerFile != null && bannerFile.exists()) {
        val bitmap = remember(bannerFile.lastModified()) {
            BitmapFactory.decodeFile(bannerFile.absolutePath)
        }
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
private fun ModuleInfoLine(@StringRes label: Int, value: String) {
    Text(
        text = stringResource(label, value),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun RunningRow() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LoadingIndicator(Modifier.size(28.dp))
        Text(
            text = stringResource(R.string.modules_running),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ModuleActions(
    module: AdbModule,
    busy: Boolean,
    trusted: Boolean,
    showTrustAction: Boolean,
    onRunAction: () -> Unit,
    onRunService: () -> Unit,
    onOpenWebUi: () -> Unit,
    onCreateShortcut: () -> Unit,
    onDelete: () -> Unit,
    onTrustChange: (Boolean) -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ModuleButton(
            label = R.string.modules_run_action,
            icon = R.drawable.ic_outline_play_arrow_24,
            primary = true,
            enabled = !busy && module.enabled && module.hasAction && ModuleSettings.canRunAction(module),
            onClick = onRunAction
        )
        ModuleButton(
            label = R.string.modules_open_webui,
            icon = R.drawable.ic_outline_open_in_new_24,
            enabled = !busy && module.hasWebUi,
            onClick = onOpenWebUi
        )
        ModuleButton(
            label = R.string.modules_shortcut,
            icon = R.drawable.ic_shortcut_24dp,
            enabled = !busy && module.hasWebUi,
            onClick = onCreateShortcut
        )
        ModuleButton(
            label = R.string.modules_run_service,
            icon = R.drawable.ic_terminal_24,
            enabled = !busy &&
                    module.enabled &&
                    module.hasService &&
                    ModuleSettings.canRunService(module) &&
                    ModuleSettings.canRunBackground(module),
            onClick = onRunService
        )
        ModuleButton(
            label = R.string.modules_delete,
            icon = R.drawable.ic_delete_24,
            enabled = !busy,
            onClick = onDelete
        )
        if (showTrustAction) {
            ModuleButton(
                label = if (trusted) R.string.modules_untrust else R.string.modules_trust,
                icon = R.drawable.ic_outline_check_circle_24,
                enabled = !busy,
                onClick = { onTrustChange(!trusted) }
            )
        }
    }
}

@Composable
private fun ModuleButton(
    @StringRes label: Int,
    @DrawableRes icon: Int,
    enabled: Boolean,
    primary: Boolean = false,
    onClick: () -> Unit
) {
    if (primary) {
        Button(
            onClick = onClick,
            enabled = enabled,
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding
        ) {
            ShizukuIcon(
                icon,
                modifier = Modifier
                    .padding(end = 6.dp)
                    .size(18.dp)
            )
            Text(stringResource(label))
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding
        ) {
            ShizukuIcon(
                icon,
                modifier = Modifier
                    .padding(end = 6.dp)
                    .size(18.dp)
            )
            Text(stringResource(label))
        }
    }
}

@Composable
private fun ModuleTabsSwitcher(
    onCatalog: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.height(36.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ModuleTabSegment(
                selected = true,
                icon = Icons.Rounded.CheckCircle,
                contentDescription = stringResource(R.string.modules_tab_installed),
                onClick = {},
                enabled = false
            )
            ModuleTabSegment(
                selected = false,
                icon = Icons.Rounded.Apps,
                contentDescription = stringResource(R.string.modules_tab_catalog),
                onClick = onCatalog
            )
        }
    }
}

@Composable
private fun ModuleTabSegment(
    selected: Boolean,
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier.height(36.dp),
        onClick = onClick,
        enabled = enabled
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 14.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun ModuleLiveConsoleDialog(
    live: AdbModuleManager.LiveModuleOutput,
    aiExplanation: String?,
    aiLoading: Boolean,
    onAskAi: () -> Unit,
    onRunAction: (() -> Unit)? = null,
    onRerun: (() -> Unit)? = null,
    onHide: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val verticalScroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()
    var showAiPanel by remember(live.moduleId, live.kind) { mutableStateOf(false) }

    LaunchedEffect(live.text) {
        verticalScroll.scrollTo(verticalScroll.maxValue)
    }

    Dialog(
        onDismissRequest = {
            if (live.running) {
                onHide()
            } else {
                onClose()
            }
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Top Header Bar (KernelSU Next style)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 18.dp, end = 12.dp, top = 16.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = when {
                            live.running -> MaterialTheme.colorScheme.primaryContainer
                            live.succeeded -> MaterialTheme.colorScheme.secondaryContainer
                            else -> MaterialTheme.colorScheme.errorContainer
                        }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            when {
                                live.running -> LoadingIndicator(modifier = Modifier.size(22.dp))
                                live.succeeded -> Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(22.dp)
                                )
                                else -> Icon(
                                    imageVector = Icons.Rounded.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = live.moduleName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val kindLabel = when (live.kind) {
                            AdbModuleManager.ConsoleKind.INSTALL -> "Flashing / Installing"
                            AdbModuleManager.ConsoleKind.ACTION -> "Action Script"
                            AdbModuleManager.ConsoleKind.SERVICE -> "Service Script"
                        }
                        val sub = if (live.subtitle.isNotBlank()) "$kindLabel • ${live.subtitle}" else kindLabel
                        Text(
                            text = sub,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Status Badge Pill
                    val badgeContainer = when {
                        live.running -> MaterialTheme.colorScheme.primaryContainer
                        live.succeeded -> MaterialTheme.colorScheme.tertiaryContainer
                        else -> MaterialTheme.colorScheme.errorContainer
                    }
                    val badgeContent = when {
                        live.running -> MaterialTheme.colorScheme.onPrimaryContainer
                        live.succeeded -> MaterialTheme.colorScheme.onTertiaryContainer
                        else -> MaterialTheme.colorScheme.onErrorContainer
                    }
                    val badgeText = when {
                        live.running && live.progress != null -> "${(live.progress * 100).toInt().coerceIn(0, 100)}%"
                        live.running -> "LIVE"
                        live.succeeded -> if (live.kind == AdbModuleManager.ConsoleKind.INSTALL) "DONE" else "EXIT 0"
                        else -> "EXIT ${live.exitCode ?: 1}"
                    }
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = badgeContainer
                    ) {
                        Text(
                            text = badgeText,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = badgeContent
                        )
                    }

                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(
                                ClipData.newPlainText(live.moduleName, live.text)
                            )
                            Toast.makeText(
                                context,
                                context.getString(R.string.comput_output_copied),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = stringResource(R.string.modules_recommand_copy),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                if (live.running) {
                    val currentProgress = live.progress
                    if (currentProgress != null) {
                        LinearProgressIndicator(
                            progress = { currentProgress.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                        )
                    }
                } else {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                }

                // Live Terminal Output Area
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    shape = MaterialTheme.shapes.large,
                    color = Color(0xFF0B0F14)
                ) {
                    SelectionContainer {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(verticalScroll)
                                .horizontalScroll(horizontalScroll)
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            val lines = remember(live.text) { live.text.lines() }
                            lines.forEach { line ->
                                val lineColor = when {
                                    line.startsWith("[✓]") || line.trimStart().startsWith("✓") -> Color(0xFF4ADE80)
                                    line.startsWith("[!]") || line.startsWith("!") -> Color(0xFFF87171)
                                    line.startsWith("*") -> Color(0xFF93C5FD)
                                    line.startsWith("$") -> Color(0xFFFBBF24)
                                    line.startsWith("-") -> Color(0xFFE2E8F0)
                                    else -> Color(0xFFD1D5DB)
                                }
                                Text(
                                    text = line.ifEmpty { " " },
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.5.sp,
                                    lineHeight = 18.sp,
                                    color = lineColor
                                )
                            }
                        }
                    }
                }

                // Collapsible AI Explanation Section
                AnimatedVisibility(
                    visible = showAiPanel || aiLoading || aiExplanation != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.comput_ai_explanation),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (aiLoading) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    LoadingIndicator(modifier = Modifier.size(22.dp))
                                    Text(
                                        text = stringResource(R.string.modules_running),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else if (!aiExplanation.isNullOrBlank()) {
                                Text(
                                    text = aiExplanation,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(110.dp)
                                        .verticalScroll(rememberScrollState())
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // Bottom Action Bar
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (live.running) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilledTonalButton(
                                onClick = onHide,
                                modifier = Modifier.heightIn(min = 36.dp, max = 42.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.modules_live_log_hide),
                                    maxLines = 1,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    } else {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    showAiPanel = true
                                    onAskAi()
                                },
                                enabled = !aiLoading,
                                modifier = Modifier.heightIn(min = 36.dp, max = 42.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Terminal,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .padding(end = 6.dp)
                                        .size(16.dp)
                                )
                                Text(
                                    text = stringResource(R.string.comput_ask_gemini),
                                    maxLines = 1,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }

                            if (onRerun != null) {
                                FilledTonalButton(
                                    onClick = onRerun,
                                    modifier = Modifier.heightIn(min = 36.dp, max = 42.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .padding(end = 6.dp)
                                            .size(16.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.home_refresh),
                                        maxLines = 1,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }

                            if (onRunAction != null) {
                                FilledTonalButton(
                                    onClick = onRunAction,
                                    modifier = Modifier.heightIn(min = 36.dp, max = 42.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .padding(end = 6.dp)
                                            .size(16.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.modules_run_action),
                                        maxLines = 1,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }

                            Button(
                                onClick = onClose,
                                modifier = Modifier.heightIn(min = 36.dp, max = 42.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .padding(end = 6.dp)
                                        .size(16.dp)
                                )
                                Text(
                                    text = stringResource(R.string.modules_recommand_close),
                                    maxLines = 1,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
