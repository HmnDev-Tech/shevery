@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)

package moe.shizuku.manager.logs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowDown
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.shizuku.manager.R
import moe.shizuku.manager.module.ModuleSettings
import moe.shizuku.manager.ui.compose.LocalFloatingNavBarVisible
import moe.shizuku.manager.ui.compose.ShizukuScaffold
import moe.shizuku.manager.settings.AiManagerScreen
import moe.shizuku.manager.utils.AiExplainUtil
import moe.shizuku.server.IShizukuService
import org.json.JSONArray
import org.json.JSONObject
import rikka.shizuku.Shizuku
import java.util.concurrent.atomic.AtomicBoolean

private data class PresetCommand(
    val title: String,
    val command: String,
    val category: String
)

private enum class ComputLineKind { COMMAND, ERROR, WARNING, NORMAL }

private data class ComputLogLine(
    val text: String,
    val kind: ComputLineKind
)

private val ComputSpring = spring<Float>(
    dampingRatio = 0.4f,
    stiffness = Spring.StiffnessMediumLow
)

@Composable
fun ComputScreen(
    listState: LazyListState = rememberLazyListState(),
    onSubpageChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var command by remember { mutableStateOf("pm list packages -3") }
    var outputLog by remember { mutableStateOf(context.getString(R.string.comput_console_initialized)) }
    var isRunning by remember { mutableStateOf(false) }
    var isAdbMode by remember { mutableStateOf(false) }
    var isExplaining by remember { mutableStateOf(false) }
    var aiExplanation by remember { mutableStateOf("") }
    var showGeminiSection by remember { mutableStateOf(false) }
    var showReCommandPrompt by remember { mutableStateOf(false) }
    var lastFailed by remember { mutableStateOf(false) }
    var lastRunCommand by remember { mutableStateOf("") }
    val cancelRequested = remember { AtomicBoolean(false) }

    var searchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var historyExpanded by remember { mutableStateOf(false) }
    var cmdHistory by remember { mutableStateOf(listOf<String>()) }

    var showCommandiumSheet by remember { mutableStateOf(false) }
    var showAiManager by remember { mutableStateOf(false) }
    var showMacrosSheet by remember { mutableStateOf(false) }
    var showPresetsSheet by remember { mutableStateOf(false) }

    val navBarState = LocalFloatingNavBarVisible.current

    LaunchedEffect(showAiManager) {
        val isSubpage = showAiManager
        navBarState.value = !isSubpage
        onSubpageChange(isSubpage)
        if (isSubpage) {
            snapshotFlow { navBarState.value }.collect { visible ->
                if (visible) {
                    navBarState.value = false
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            navBarState.value = true
            onSubpageChange(false)
        }
    }

    // AI Provider manager replaces the whole Comput tab while open: composing
    // it AFTER the Scaffold stacked a second TopAppBar over this one (dead
    // touches on its buttons); early-return keeps exactly one top bar on screen.
    if (showAiManager) {
        BackHandler(onBack = { showAiManager = false })
        AiManagerScreen(onNavigateUp = { showAiManager = false }, onChanged = {})
        return
    }

    var isRecording by remember { mutableStateOf(false) }
    val recordedCommands = remember { mutableStateListOf<String>() }
    var showSaveMacroDialog by remember { mutableStateOf(false) }
    var macroNameInput by remember { mutableStateOf("") }

    var commandiumPrompt by remember { mutableStateOf("") }
    var isCommandiumGenerating by remember { mutableStateOf(false) }
    var commandiumResult by remember { mutableStateOf<Result<String>?>(null) }
    val commandiumHistory = remember { mutableStateListOf<String>() }

    var savedMacros by remember {
        mutableStateOf<Map<String, List<String>>>(
            try {
                val json = JSONObject(ModuleSettings.getComputMacros())
                val map = mutableMapOf<String, List<String>>()
                json.keys().forEach { key ->
                    val arr = json.getJSONArray(key)
                    val list = mutableListOf<String>()
                    for (i in 0 until arr.length()) {
                        list.add(arr.getString(i))
                    }
                    map[key] = list
                }
                map.toMap()
            } catch (e: Exception) {
                emptyMap()
            }
        )
    }

    fun persistMacros() {
        val json = JSONObject()
        savedMacros.forEach { (k, v) ->
            json.put(k, JSONArray(v))
        }
        ModuleSettings.setComputMacros(json.toString())
    }

    fun saveMacro(name: String) {
        val updated = savedMacros.toMutableMap()
        updated[name] = recordedCommands.toList()
        savedMacros = updated.toMap()
        persistMacros()
        recordedCommands.clear()
    }

    fun deleteMacro(name: String) {
        val updated = savedMacros.toMutableMap()
        updated.remove(name)
        savedMacros = updated.toMap()
        persistMacros()
    }

    fun clearConsole() {
        command = ""
        outputLog = context.getString(R.string.comput_console_cleared)
        aiExplanation = ""
        showGeminiSection = false
        lastRunCommand = ""
        lastFailed = false
    }

    suspend fun executeCommandInternal(cmd: String): Pair<String, Boolean> {
        val finalCmd = if (isAdbMode) {
            var trimmed = cmd.trim()
            if (trimmed.startsWith("adb shell ")) {
                trimmed = trimmed.substring(10).trim()
            } else if (trimmed.startsWith("adb shell")) {
                trimmed = trimmed.substring(9).trim()
            } else if (trimmed.startsWith("shell ")) {
                trimmed = trimmed.substring(6).trim()
            } else if (trimmed.startsWith("shell")) {
                trimmed = trimmed.substring(5).trim()
            } else if (trimmed.startsWith("adb ")) {
                trimmed = trimmed.substring(4).trim()
            } else if (trimmed == "adb") {
                trimmed = "adb_help"
            }

            if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length >= 2) {
                trimmed = trimmed.substring(1, trimmed.length - 1).trim()
            } else if (trimmed.startsWith("'") && trimmed.endsWith("'") && trimmed.length >= 2) {
                trimmed = trimmed.substring(1, trimmed.length - 1).trim()
            }

            if (trimmed == "adb_help" || trimmed == "help" || trimmed == "--help" || trimmed == "-h") {
                "adb_internal_help"
            } else if (trimmed == "devices") {
                "adb_internal_devices"
            } else if (trimmed.startsWith("install")) {
                "adb_internal_install"
            } else if (trimmed.startsWith("push") || trimmed.startsWith("pull")) {
                "adb_internal_file_transfer"
            } else {
                trimmed
            }
        } else {
            cmd.trim()
        }

        if (finalCmd.isBlank()) {
            return Pair("[E] Error: Command translates to empty string.", true)
        }

        val result = withContext(Dispatchers.IO) {
            if (isAdbMode) {
                when (finalCmd) {
                    "adb_internal_help" -> {
                        return@withContext Pair("""
                            Android Debug Bridge (Shevery Console Bridge)
                            You are connected to the device privileged shell via Shevery.
                            
                            For on-device shell commands, type them directly without 'adb' or 'adb shell'.
                            Examples:
                              pm list packages
                              settings get secure android_id
                              dumpsys battery
                            
                            Note: Host-side commands like 'adb devices', 'adb push/pull', or 'adb install' are not supported directly inside the device shell. Use standard shell equivalents (e.g. 'pm install').
                        """.trimIndent(), false)
                    }
                    "adb_internal_devices" -> {
                        return@withContext Pair("""
                            List of devices attached
                            local_shevery_device    device
                            
                            [I] You are currently inside the shell of this device.
                        """.trimIndent(), false)
                    }
                    "adb_internal_install" -> {
                        return@withContext Pair("[E] 'adb install' is a host-side command.\nTo install an APK directly on the device, use:\n  pm install <path_to_apk>", true)
                    }
                    "adb_internal_file_transfer" -> {
                        return@withContext Pair("[E] 'adb push' and 'adb pull' are host-side file transfer commands.\nUse 'cp' or 'mv' to copy/move files on the device, or use a file manager.", true)
                    }
                }
            }

            if (!Shizuku.pingBinder()) {
                return@withContext Pair(context.getString(R.string.comput_error_not_running), true)
            }
            try {
                val binder = Shizuku.getBinder() ?: return@withContext Pair(context.getString(R.string.comput_error_no_binder), true)
                val service = IShizukuService.Stub.asInterface(binder)
                val remote = service.newProcess(
                    arrayOf("sh", "-c", finalCmd),
                    null,
                    null
                )

                val scopeContext = coroutineContext

                val destroy: () -> Unit = { runCatching { remote.destroy() } }
                val stdoutPfd = remote.getInputStream()
                val stderrPfd = remote.getErrorStream()

                var stdoutText = ""
                var stderrText = ""
                val stdoutThread = Thread {
                    try {
                        stdoutText = readStreamTail(stdoutPfd, cancelRequested, destroy)
                    } catch (ignore: Exception) { }
                }
                val stderrThread = Thread {
                    try {
                        stderrText = readStreamTail(stderrPfd, cancelRequested, destroy)
                    } catch (ignore: Exception) { }
                }
                stdoutThread.start()
                stderrThread.start()

                val finished = run {
                    var done = false
                    while (!cancelRequested.get() && scopeContext.isActive) {
                        if (remote.waitForTimeout(1L, java.util.concurrent.TimeUnit.SECONDS.name)) {
                            done = true
                            break
                        }
                    }
                    done
                }
                val exitCode = if (finished) {
                    remote.exitValue()
                } else {
                    remote.destroy()
                    try { stdoutPfd.close() } catch (ignore: Exception) {}
                    try { stderrPfd.close() } catch (ignore: Exception) {}
                    124
                }
                stdoutThread.join(1000)
                stderrThread.join(1000)

                val cancelled = cancelRequested.get()
                val resString = buildString {
                    if (stdoutText.isNotBlank()) append(stdoutText.trim())
                    if (stderrText.isNotBlank()) {
                        if (isNotEmpty()) append("\n")
                        append("[E] ")
                        append(stderrText.trim())
                    }
                    if (cancelled) {
                        if (isNotEmpty()) append("\n")
                        append("[E] ")
                        append(context.getString(R.string.comput_cancelled))
                    } else if (exitCode != 0) {
                        if (isNotEmpty()) append("\n")
                        append(context.getString(R.string.comput_exit_code, exitCode))
                    }
                    if (isEmpty()) append(context.getString(R.string.comput_command_no_output))
                }
                val hasFailed = !cancelled && ((!finished || exitCode != 0) || stderrText.isNotBlank())
                Pair(resString, hasFailed)
            } catch (e: Exception) {
                Pair("[E] Shell execution failed: ${e.message}", true)
            }
        }
        return result
    }

    fun runShellCommand(cmd: String) {
        if (cmd.isBlank()) return
        scope.launch {
            cancelRequested.set(false)
            historyExpanded = false
            isRunning = true
            lastFailed = false
            aiExplanation = ""
            lastRunCommand = cmd

            if (isRecording) {
                recordedCommands.add(cmd)
            }

            if (isAdbMode) {
                outputLog = context.getString(R.string.comput_adb_translate, cmd)
            } else {
                outputLog = context.getString(R.string.comput_executing, cmd)
            }

            val (result, hasFailed) = executeCommandInternal(cmd)
            val cancelled = cancelRequested.get()
            outputLog = result
            isRunning = false
            lastFailed = hasFailed

            if (!cancelled && cmd.isNotBlank()) {
                cmdHistory = (listOf(cmd) + cmdHistory.filter { it != cmd }).take(50)
            }

            if (hasFailed && !cancelled && ModuleSettings.isComputAiExplainEnabled()) {
                val apiKey = ModuleSettings.getComputApiKey()
                if (apiKey.isNotBlank()) {
                    scope.launch {
                        isExplaining = true
                        showGeminiSection = true
                        aiExplanation = AiExplainUtil.explainFailure(
                            contextStr = "Shevery Comput Console Shell Command Execution",
                            inputDetail = cmd,
                            outputLog = result,
                            apiKey = apiKey
                        )
                        isExplaining = false
                    }
                }
            }
        }
    }

    fun runMacro(macroName: String, commands: List<String>) {
        scope.launch {
            cancelRequested.set(false)
            historyExpanded = false
            isRunning = true
            lastFailed = false
            aiExplanation = ""
            val fullLog = StringBuilder()
            fullLog.append(context.getString(R.string.comput_running_macro, macroName)).append("\n\n")
            outputLog = fullLog.toString()

            for (cmd in commands) {
                if (cancelRequested.get()) break
                fullLog.append(context.getString(R.string.comput_executing_cmd, cmd)).append("\n")
                outputLog = fullLog.toString()

                val (result, hasFailed) = executeCommandInternal(cmd)
                fullLog.append(result).append("\n\n")
                outputLog = fullLog.toString()
                lastFailed = hasFailed

                if (hasFailed && ModuleSettings.isComputAiExplainEnabled()) {
                    val apiKey = ModuleSettings.getComputApiKey()
                    if (apiKey.isNotBlank()) {
                        scope.launch {
                            isExplaining = true
                            showGeminiSection = true
                            aiExplanation = AiExplainUtil.explainFailure(
                                contextStr = "Shevery Macro Command Execution ($macroName - $cmd)",
                                inputDetail = cmd,
                                outputLog = result,
                                apiKey = apiKey
                            )
                            isExplaining = false
                        }
                    }
                }
            }
            isRunning = false
            cancelRequested.set(false)
        }
    }

    fun requestRun() {
        if (ModuleSettings.isComputRecommandEnabled()) {
            showReCommandPrompt = true
        } else {
            runShellCommand(command)
        }
    }

    fun triggerGeminiExplanation() {
        scope.launch {
            isExplaining = true
            showGeminiSection = true
            val apiKey = ModuleSettings.getComputApiKey()
            aiExplanation = AiExplainUtil.explainFailure(
                contextStr = "Shevery Comput Console Shell Command Execution",
                inputDetail = "Command: $command",
                outputLog = outputLog,
                apiKey = apiKey
            )
            isExplaining = false
        }
    }

    fun copyToClipboard(label: String, text: String, toast: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, toast, Toast.LENGTH_SHORT).show()
    }

    val quickActionChips = remember {
        listOf(
            "pm list packages -3",
            "dumpsys battery",
            "logcat -d -t 30",
            "settings get secure android_id",
            "df -h",
            "getprop ro.build.version.release"
        )
    }

    val presetLibrary = remember {
        listOf(
            PresetCommand("User Installed Apps", "pm list packages -3", "Package Manager"),
            PresetCommand("System Packages", "pm list packages -s", "Package Manager"),
            PresetCommand("Disabled Packages", "pm list packages -d", "Package Manager"),
            PresetCommand("Manager APK Path", "pm path moe.shizuku.manager", "Package Manager"),

            PresetCommand("Android OS Version", "getprop ro.build.version.release", "System Props"),
            PresetCommand("Device Model Name", "getprop ro.product.model", "System Props"),
            PresetCommand("Display Screen Resolution", "wm size", "System Props"),
            PresetCommand("Screen Density (DPI)", "wm density", "System Props"),

            PresetCommand("Battery Health & Level", "dumpsys battery", "Battery & Power"),
            PresetCommand("Simulate Disconnected AC", "dumpsys battery unplug", "Battery & Power"),
            PresetCommand("Reset Battery Stats", "dumpsys battery reset", "Battery & Power"),

            PresetCommand("Disk Usage & Free Space", "df -h", "Storage & Files"),
            PresetCommand("Root Storage File List", "ls -la /sdcard", "Storage & Files"),
            PresetCommand("Download Folder Size", "du -sh /sdcard/Download", "Storage & Files"),

            PresetCommand("Active Network Sockets", "netstat -tulpn", "Network"),
            PresetCommand("Network Interfaces & IPs", "ip addr", "Network")
        )
    }

    val outputLines = remember(outputLog, lastRunCommand) {
        val lines = mutableListOf<ComputLogLine>()
        if (lastRunCommand.isNotBlank() && outputLog.isNotBlank() &&
            !outputLog.startsWith(context.getString(R.string.comput_console_cleared))
        ) {
            lines += ComputLogLine("$ ${lastRunCommand}", ComputLineKind.COMMAND)
        }
        lines += parseLogLines(outputLog)
        lines
    }

    val visibleLines = remember(outputLines, searchActive, searchQuery) {
        if (searchActive && searchQuery.isNotBlank()) {
            outputLines.filter { it.text.contains(searchQuery, ignoreCase = true) }
        } else {
            outputLines
        }
    }

    ShizukuScaffold(
        title = stringResource(R.string.comput_title),
        onNavigateUp = null
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .widthIn(max = 840.dp)
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
                contentPadding = PaddingValues(
                    bottom = 112.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 12.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    if (searchActive) {
                        ComputSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onClear = { searchQuery = "" },
                    onClose = {
                        searchActive = false
                        searchQuery = ""
                    }
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ComputUtilityButton(
                        icon = Icons.Rounded.Search,
                        contentDescription = stringResource(R.string.comput_search_desc),
                        onClick = {
                            searchActive = true
                            searchQuery = ""
                        }
                    )
                    ComputUtilityButton(
                        icon = Icons.Rounded.History,
                        contentDescription = stringResource(R.string.comput_history_desc),
                        onClick = {
                            if (cmdHistory.isEmpty()) {
                                Toast.makeText(context, context.getString(R.string.comput_history_empty), Toast.LENGTH_SHORT).show()
                            } else {
                                historyExpanded = true
                            }
                        }
                    )
                    ComputUtilityButton(
                        icon = Icons.Rounded.Tune,
                        contentDescription = stringResource(R.string.comput_ai_provider_title),
                        onClick = { showAiManager = true }
                    )
                    ComputUtilityButton(
                        icon = Icons.Rounded.AutoAwesome,
                        contentDescription = stringResource(R.string.comput_tab_commandium),
                        onClick = { showCommandiumSheet = true }
                    )
                    ComputUtilityButton(
                        icon = Icons.Rounded.PlaylistPlay,
                        contentDescription = stringResource(R.string.comput_tab_macros),
                        onClick = { showMacrosSheet = true }
                    )
                    ComputUtilityButton(
                        icon = Icons.Rounded.Apps,
                        contentDescription = stringResource(R.string.comput_tab_presets),
                        onClick = { showPresetsSheet = true }
                    )
                    ComputUtilityButton(
                        icon = Icons.Rounded.Clear,
                        contentDescription = stringResource(R.string.comput_clear),
                        onClick = { clearConsole() }
                    )
                }
            }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = { isAdbMode = !isAdbMode },
                        shape = CircleShape,
                        colors = ButtonDefaults.textButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Terminal,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (isAdbMode) {
                                stringResource(R.string.comput_adb_mode)
                            } else {
                                stringResource(R.string.comput_sh_mode)
                            },
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }

            item {
                ExposedDropdownMenuBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    expanded = historyExpanded,
                    onExpandedChange = { historyExpanded = it && cmdHistory.isNotEmpty() }
                ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = command,
                        onValueChange = { command = it },
                        modifier = Modifier
                            .weight(1f)
                            .menuAnchor(
                                ExposedDropdownMenuAnchorType.PrimaryEditable,
                                enabled = true
                            ),
                        shape = RoundedCornerShape(22.dp),
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp
                        ),
                        leadingIcon = {
                            Text(
                                text = if (isAdbMode) "#" else "$",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 12.dp)
                            )
                        },
                        trailingIcon = {
                            if (command.isNotEmpty()) {
                                IconButton(onClick = { command = "" }) {
                                    Icon(
                                        Icons.Rounded.Clear,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        },
                        label = { Text(stringResource(R.string.comput_command_label)) },
                        placeholder = { Text(stringResource(R.string.comput_command_placeholder)) },
                        maxLines = 3,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Ascii,
                            imeAction = ImeAction.Send
                        ),
                        keyboardActions = KeyboardActions(
                            onSend = { requestRun() }
                        ),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        )
                    )

                    FloatingActionButton(
                        modifier = Modifier.padding(top = 8.dp),
                        onClick = {
                            if (isRunning) {
                                cancelRequested.set(true)
                            } else {
                                requestRun()
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.run {
                            if (isRunning) errorContainer else primaryContainer
                        },
                        contentColor = MaterialTheme.colorScheme.run {
                            if (isRunning) onErrorContainer else onPrimaryContainer
                        }
                    ) {
                        AnimatedContent(
                            targetState = isRunning,
                            transitionSpec = {
                                (fadeIn(animationSpec = ComputSpring) +
                                    scaleIn(initialScale = 0.6f, animationSpec = ComputSpring))
                                    .togetherWith(
                                        fadeOut(animationSpec = ComputSpring) +
                                            scaleOut(targetScale = 0.6f, animationSpec = ComputSpring)
                                    )
                            },
                            label = "run_stop_fab"
                        ) { running ->
                            if (running) {
                                Icon(
                                    imageVector = Icons.Rounded.Stop,
                                    contentDescription = stringResource(R.string.comput_stop_desc),
                                    modifier = Modifier.size(24.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Send,
                                    contentDescription = stringResource(R.string.comput_run_desc),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }

                ExposedDropdownMenu(
                    expanded = historyExpanded,
                    onDismissRequest = { historyExpanded = false },
                    modifier = Modifier.heightIn(max = 360.dp),
                    scrollState = rememberScrollState()
                ) {
                    if (cmdHistory.isEmpty()) return@ExposedDropdownMenu
                    Text(
                        text = stringResource(R.string.comput_history_title),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    cmdHistory.forEach { historyCommand ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = historyCommand,
                                    maxLines = 1,
                                    modifier = Modifier.basicMarquee()
                                )
                            },
                            onClick = {
                                command = historyCommand
                                historyExpanded = false
                            }
                        )
                    }
                }
            }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    quickActionChips.forEach { chipText ->
                        FilterChip(
                            selected = command == chipText,
                            onClick = { command = chipText },
                            label = {
                                Text(
                                    text = chipText,
                                    style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                                    maxLines = 1
                                )
                            },
                            shape = RoundedCornerShape(16.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }
            }

            item {
                ComputOutputCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .heightIn(min = 160.dp, max = 320.dp),
                    lines = visibleLines,
                    isRunning = isRunning,
                    searchActive = searchActive,
                    searchQuery = searchQuery,
                    showGeminiSection = showGeminiSection,
                    isExplaining = isExplaining,
                    onToggleGemini = {
                        val activeKey = moe.shizuku.manager.commandium.AiProviderRepository.getActiveKey()
                        if (activeKey.isBlank()) {
                            scope.launch {
                                val action = snackbarHostState.showSnackbar(
                                    message = context.getString(R.string.comput_ai_no_active_toast),
                                    actionLabel = "Configure",
                                    withDismissAction = true,
                                    duration = SnackbarDuration.Long
                                )
                                if (action == SnackbarResult.ActionPerformed) {
                                    showAiManager = true
                                }
                            }
                        } else if (!showGeminiSection && aiExplanation.isBlank() && !isExplaining) {
                            triggerGeminiExplanation()
                        } else {
                            showGeminiSection = !showGeminiSection
                        }
                    },
                    onCopyOutput = {
                        copyToClipboard("ADB Output", outputLog, context.getString(R.string.comput_output_copied))
                    },
                    onClearOutput = { clearConsole() }
                )
            }

            item {
                AnimatedVisibility(visible = showGeminiSection) {
                    Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .animateContentSize(),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.comput_gemini_explain),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (aiExplanation.isNotBlank()) {
                                    IconButton(
                                        onClick = {
                                            copyToClipboard("Gemini Analysis", aiExplanation, context.getString(R.string.comput_copied_to_clipboard))
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.ContentCopy,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { showGeminiSection = false },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.Clear,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }

                        if (isExplaining) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                LoadingIndicator(Modifier.size(16.dp), MaterialTheme.colorScheme.onSecondaryContainer)
                                Text("Analyzing command & output...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        } else if (aiExplanation.isNotBlank()) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.15f))
                            SelectionContainer {
                                Text(
                                    text = aiExplanation,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        } else {
                            TextButton(
                                onClick = { triggerGeminiExplanation() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Click to generate Gemini AI breakdown", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
            }

            item {
                Text(
                    text = "Lines: ${outputLines.size} | Chars: ${outputLog.length}",
                    style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                )
            }
        }
    }

    // Modal BottomSheet for Commandium AI Studio
    if (showCommandiumSheet) {
        CommandiumSheet(
            onDismiss = { showCommandiumSheet = false },
            prompt = commandiumPrompt,
            onPromptChange = { commandiumPrompt = it },
            isGenerating = isCommandiumGenerating,
            onGeneratingChange = { isCommandiumGenerating = it },
            result = commandiumResult,
            onResultChange = { r ->
                commandiumResult = r
                if (r != null && r.isSuccess) {
                    commandiumHistory.remove(commandiumPrompt)
                    commandiumHistory.add(0, commandiumPrompt)
                    if (commandiumHistory.size > 8) commandiumHistory.removeAt(8)
                }
            },
            scope = scope,
            onUseCommand = { cmd ->
                command = cmd
                showCommandiumSheet = false
            },
            onCopy = { text ->
                copyToClipboard("Commandium", text,
                    context.getString(R.string.comput_copied_to_clipboard))
            },
            onConfigureProvider = {
                showCommandiumSheet = false
                showAiManager = true
            },
            history = commandiumHistory,
        )
    }

    // AI Provider manager - early-returned at the top of this composable; this
    // placement is unreachable while it is open, kept only for reference.

    // Modal BottomSheet for Macros
    if (showMacrosSheet) {
        ModalBottomSheet(
            onDismissRequest = { showMacrosSheet = false },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.comput_console_macros),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (isRecording) {
                        Button(
                            onClick = {
                                showSaveMacroDialog = true
                                isRecording = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            shape = CircleShape
                        ) {
                            Text(stringResource(R.string.comput_stop, recordedCommands.size))
                        }
                    } else {
                        Button(
                            onClick = {
                                recordedCommands.clear()
                                isRecording = true
                            },
                            shape = CircleShape
                        ) {
                            Text(stringResource(R.string.comput_record_macro))
                        }
                    }
                }

                if (isRecording) {
                    Text(
                        text = stringResource(R.string.comput_recording_active),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Text(
                    text = stringResource(R.string.comput_saved_macros),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                if (savedMacros.isEmpty()) {
                    Text(
                        text = stringResource(R.string.comput_no_macros),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        savedMacros.forEach { (name, cmds) ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = name,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "${cmds.size} cmds",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                showMacrosSheet = false
                                                runMacro(name, cmds)
                                            },
                                            enabled = !isRunning,
                                            modifier = Modifier.weight(1f),
                                            shape = CircleShape
                                        ) {
                                            Text(stringResource(R.string.comput_run))
                                        }
                                        TextButton(
                                            onClick = { deleteMacro(name) },
                                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                            shape = CircleShape
                                        ) {
                                            Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(stringResource(R.string.comput_delete))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    // Modal BottomSheet for Presets Library
    if (showPresetsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showPresetsSheet = false },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = stringResource(R.string.comput_presets_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.comput_presets_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val categories = remember { presetLibrary.map { it.category }.distinct() }
                categories.forEach { cat ->
                    Text(
                        text = cat.uppercase(),
                        style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )

                    presetLibrary.filter { it.category == cat }.forEach { preset ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = preset.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = preset.command,
                                        style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    IconButton(
                                        onClick = {
                                            command = preset.command
                                            showPresetsSheet = false
                                        }
                                    ) {
                                        Icon(Icons.Rounded.ContentPaste, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                                    }
                                    Button(
                                        onClick = {
                                            command = preset.command
                                            showPresetsSheet = false
                                            requestRun()
                                        },
                                        shape = CircleShape
                                    ) {
                                        Text(stringResource(R.string.comput_run), style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (showReCommandPrompt) {
        AlertDialog(
            onDismissRequest = { showReCommandPrompt = false },
            title = { Text(stringResource(R.string.comput_recommand_confirmation_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.comput_recommand_confirmation),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = command,
                            modifier = Modifier.padding(12.dp),
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp
                            )
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showReCommandPrompt = false
                        runShellCommand(command)
                    },
                    shape = CircleShape
                ) {
                    Text(stringResource(R.string.comput_execute))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showReCommandPrompt = false },
                    shape = CircleShape
                ) {
                    Text(stringResource(R.string.comput_cancel))
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.extraLarge
        )
    }

    if (showSaveMacroDialog) {
        AlertDialog(
            onDismissRequest = { showSaveMacroDialog = false },
            title = { Text(stringResource(R.string.comput_save_macro)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.comput_save_macro_hint))
                    OutlinedTextField(
                        value = macroNameInput,
                        onValueChange = { macroNameInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        label = { Text(stringResource(R.string.comput_macro_name_label)) },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (macroNameInput.isNotBlank()) {
                            saveMacro(macroNameInput.trim())
                            macroNameInput = ""
                            showSaveMacroDialog = false
                        }
                    },
                    enabled = macroNameInput.isNotBlank(),
                    shape = CircleShape
                ) {
                    Text(stringResource(R.string.comput_save))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        recordedCommands.clear()
                        macroNameInput = ""
                        showSaveMacroDialog = false
                    },
                    shape = CircleShape
                ) {
                    Text(stringResource(R.string.comput_cancel))
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.extraLarge
        )
    }
}
}

@Composable
private fun ComputUtilityButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun ComputSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = CircleShape,
        singleLine = true,
        leadingIcon = {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        },
        trailingIcon = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(end = 4.dp)
            ) {
                if (query.isNotEmpty()) {
                    IconButton(
                        onClick = onClear,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Clear,
                            contentDescription = stringResource(R.string.comput_clear_search_desc),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.comput_close_search_desc),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        },
        placeholder = { Text(stringResource(R.string.comput_search_hint)) },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        )
    )
}

@Composable
private fun ComputOutputCard(
    modifier: Modifier = Modifier,
    lines: List<ComputLogLine>,
    isRunning: Boolean,
    searchActive: Boolean,
    searchQuery: String,
    showGeminiSection: Boolean,
    isExplaining: Boolean,
    onToggleGemini: () -> Unit,
    onCopyOutput: () -> Unit,
    onClearOutput: () -> Unit
) {
    val dark = isSystemInDarkTheme()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var userScrolledAway by remember { mutableStateOf(false) }

    val bottomProximityPx = with(LocalDensity.current) { 48.dp.toPx() }
    val isNearBottom by remember(lines) {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()
                ?: return@derivedStateOf true
            val trailingGap = info.viewportEndOffset - (lastVisible.offset + lastVisible.size)
            lastVisible.index + 1 == info.totalItemsCount &&
                trailingGap >= -bottomProximityPx
        }
    }

    val showScrollFab by remember(lines) {
        derivedStateOf {
            lines.size > 4 && !isNearBottom && !searchActive
        }
    }

    LaunchedEffect(isNearBottom, isRunning) {
        snapshotFlow { listState.isScrollInProgress }
            .collect { scrolling ->
                if (isRunning && scrolling && !isNearBottom) {
                    userScrolledAway = true
                } else if (isNearBottom) {
                    userScrolledAway = false
                }
            }
    }

    LaunchedEffect(lines.size, isRunning, userScrolledAway) {
        if (isRunning && !userScrolledAway && lines.isNotEmpty()) {
            listState.scrollToItem(lines.lastIndex)
        }
    }

    LaunchedEffect(isRunning) {
        if (!isRunning && lines.isNotEmpty()) {
            listState.animateScrollToItem(lines.lastIndex)
        }
    }

    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (dark) {
                MaterialTheme.colorScheme.surfaceContainerLowest
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (dark) {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer
                        }
                    )
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.comput_output_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (isRunning) {
                        LoadingIndicator(
                            Modifier.size(14.dp),
                            MaterialTheme.colorScheme.primary
                        )
                    }
                    FilterChip(
                        selected = showGeminiSection,
                        onClick = onToggleGemini,
                        label = { Text(stringResource(R.string.comput_ask_gemini), style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = {
                            if (isExplaining) {
                                LoadingIndicator(Modifier.size(14.dp), MaterialTheme.colorScheme.primary)
                            } else {
                                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                            }
                        },
                        shape = CircleShape,
                        colors = FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f))
                    )
                    IconButton(
                        onClick = onCopyOutput,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = stringResource(R.string.comput_copy_output),
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = onClearOutput,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Clear,
                            contentDescription = stringResource(R.string.comput_clear),
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                if (searchActive && searchQuery.isNotBlank() && lines.isEmpty()) {
                    Text(
                        text = stringResource(R.string.comput_no_search_results),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    SelectionContainer {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 10.dp,
                                bottom = 16.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(lines) { index, line ->
                                ComputOutputLine(
                                    line = line,
                                    searchQuery = if (searchActive) searchQuery else "",
                                    isCommand = index == 0 && line.kind == ComputLineKind.COMMAND
                                )
                            }
                        }
                    }
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = showScrollFab,
                    modifier = Modifier.align(Alignment.BottomEnd),
                    enter = scaleIn(animationSpec = ComputSpring) + fadeIn(animationSpec = ComputSpring),
                    exit = scaleOut(animationSpec = ComputSpring) + fadeOut(animationSpec = ComputSpring)
                ) {
                    SmallFloatingActionButton(
                        onClick = {
                            if (lines.isNotEmpty()) {
                                scope.launch {
                                    listState.animateScrollToItem(lines.lastIndex)
                                }
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.KeyboardDoubleArrowDown,
                            contentDescription = stringResource(R.string.comput_scroll_bottom_desc)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ComputOutputLine(
    line: ComputLogLine,
    searchQuery: String,
    isCommand: Boolean
) {
    val baseColor = when (line.kind) {
        ComputLineKind.COMMAND -> MaterialTheme.colorScheme.primary
        ComputLineKind.ERROR -> MaterialTheme.colorScheme.error
        ComputLineKind.WARNING -> MaterialTheme.colorScheme.tertiary
        ComputLineKind.NORMAL -> MaterialTheme.colorScheme.onSurface
    }
    val fontWeight = when (line.kind) {
        ComputLineKind.COMMAND -> FontWeight.Bold
        ComputLineKind.ERROR -> FontWeight.SemiBold
        else -> FontWeight.Normal
    }
    val text = if (searchQuery.isNotBlank()) {
        val (highlightBg, highlightFg) = when (line.kind) {
            ComputLineKind.ERROR -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
            ComputLineKind.WARNING -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
            else -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        }
        highlightQuery(line.text, searchQuery, highlightBg, highlightFg)
    } else {
        AnnotatedString(line.text)
    }

    Text(
        text = text,
        style = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            fontWeight = fontWeight
        ),
        color = baseColor,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isCommand) {
                    Modifier.padding(top = 12.dp, bottom = 4.dp)
                } else {
                    Modifier
                }
            )
    )
}

private fun parseLogLines(text: String): List<ComputLogLine> {
    val result = mutableListOf<ComputLogLine>()
    text.split("\n").forEach { raw ->
        val line = raw.trimEnd()
        if (line.isEmpty()) return@forEach
        val lower = line.lowercase()
        val kind = when {
            line.startsWith("> ") || line.startsWith("Running macro:") -> ComputLineKind.COMMAND
            line.contains("[E]", ignoreCase = true) ||
                line.contains("error", ignoreCase = true) ||
                line.contains("failed", ignoreCase = true) ||
                line.contains("exception", ignoreCase = true) ||
                line.contains("denied", ignoreCase = true) -> ComputLineKind.ERROR
            line.contains("[W]", ignoreCase = true) ||
                lower.contains("warn") -> ComputLineKind.WARNING
            else -> ComputLineKind.NORMAL
        }
        result += ComputLogLine(line, kind)
    }
    return result
}

private fun highlightQuery(
    text: String,
    query: String,
    background: Color,
    foreground: Color
): AnnotatedString {
    if (query.isBlank()) return AnnotatedString(text)
    val matches = Regex(java.util.regex.Pattern.quote(query), RegexOption.IGNORE_CASE)
        .findAll(text)
        .toList()
    if (matches.isEmpty()) return AnnotatedString(text)
    return buildAnnotatedString {
        var cursor = 0
        for (match in matches) {
            if (match.range.first > cursor) {
                append(text.substring(cursor, match.range.first))
            }
            withStyle(
                style = SpanStyle(
                    background = background,
                    color = foreground,
                    fontWeight = FontWeight.Bold
                )
            ) {
                append(text.substring(match.range))
            }
            cursor = match.range.last + 1
        }
        if (cursor < text.length) {
            append(text.substring(cursor))
        }
    }
}

private fun readStreamTail(
    pfd: ParcelFileDescriptor,
    cancelRequested: AtomicBoolean,
    onCancel: () -> Unit
): String {
    return ParcelFileDescriptor.AutoCloseInputStream(pfd).reader(Charsets.UTF_8).use { reader ->
        val buffer = CharArray(8192)
        val tail = StringBuilder()
        while (true) {
            if (cancelRequested.get()) {
                onCancel()
                break
            }
            val read = reader.read(buffer)
            if (read <= 0) break
            tail.append(buffer, 0, read)
            if (tail.length > 64 * 1024 * 2) {
                tail.delete(0, tail.length - 64 * 1024)
            }
        }
        if (tail.length > 64 * 1024) {
            tail.substring(tail.length - 64 * 1024)
        } else {
            tail.toString()
        }
    }
}
