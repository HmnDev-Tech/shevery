@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package moe.shizuku.manager.logs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import androidx.compose.runtime.mutableStateListOf
import moe.shizuku.manager.R
import moe.shizuku.manager.module.ModuleSettings
import moe.shizuku.manager.ui.compose.ShizukuLazyScaffold
import moe.shizuku.manager.utils.AiExplainUtil
import moe.shizuku.server.IShizukuService
import org.json.JSONArray
import org.json.JSONObject
import rikka.shizuku.Shizuku
import java.net.HttpURLConnection
import java.net.URL

@Composable
fun ComputScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var command by remember { mutableStateOf("pm list packages -3") }
    var outputLog by remember { mutableStateOf("Console initialized. Privileged Shevery service connected.\nReady for commands.") }
    var isRunning by remember { mutableStateOf(false) }
    var isAdbMode by remember { mutableStateOf(false) }
    var isExplaining by remember { mutableStateOf(false) }
    var aiExplanation by remember { mutableStateOf("") }
    var showReCommandPrompt by remember { mutableStateOf(false) }

    var isRecording by remember { mutableStateOf(false) }
    val recordedCommands = remember { mutableStateListOf<String>() }
    var showSaveMacroDialog by remember { mutableStateOf(false) }
    var macroNameInput by remember { mutableStateOf("") }

    var commandiumPrompt by remember { mutableStateOf("") }
    var isCommandiumGenerating by remember { mutableStateOf(false) }
    var generatedCommandiumResult by remember { mutableStateOf("") }

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

    fun saveMacro(name: String) {
        val updated = savedMacros.toMutableMap()
        updated[name] = recordedCommands.toList()
        savedMacros = updated.toMap()
        val json = JSONObject()
        savedMacros.forEach { (k, v) ->
            json.put(k, JSONArray(v))
        }
        ModuleSettings.setComputMacros(json.toString())
        recordedCommands.clear()
    }

    fun deleteMacro(name: String) {
        val updated = savedMacros.toMutableMap()
        updated.remove(name)
        savedMacros = updated.toMap()
        val json = JSONObject()
        savedMacros.forEach { (k, v) ->
            json.put(k, JSONArray(v))
        }
        ModuleSettings.setComputMacros(json.toString())
    }

    val errorColor = Color(0xFFEF5350)
    val warningColor = Color(0xFFFFB300)
    val normalColor = MaterialTheme.colorScheme.onSurfaceVariant

    val annotatedOutput = remember(outputLog) {
        buildAnnotatedLog(outputLog, errorColor, warningColor, normalColor)
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
                            You are already connected to the device's privileged shell via Shevery.
                            
                            For on-device shell commands, type them directly without 'adb' or 'adb shell'.
                            Examples:
                              pm list packages
                              settings get secure android_id
                              dumpsys battery
                            
                            Note: Host-side commands like 'adb devices', 'adb push/pull', or 'adb install' are not supported directly inside the device shell, but you can use standard shell equivalents (e.g. 'pm install').
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
                return@withContext Pair("Error: Privileged Shevery Service is not running. Please start the service first.", true)
            }
            try {
                val binder = Shizuku.getBinder() ?: return@withContext Pair("Error: Unable to retrieve Shevery binder.", true)
                val service = IShizukuService.Stub.asInterface(binder)
                val remote = service.newProcess(
                    arrayOf("sh", "-c", finalCmd),
                    null,
                    null
                )
                
                val stdoutPfd = remote.getInputStream()
                val stderrPfd = remote.getErrorStream()

                var stdoutText = ""
                var stderrText = ""
                val stdoutThread = Thread {
                    try {
                        stdoutText = readStreamTail(stdoutPfd)
                    } catch (ignore: Exception) { }
                }
                val stderrThread = Thread {
                    try {
                        stderrText = readStreamTail(stderrPfd)
                    } catch (ignore: Exception) { }
                }
                stdoutThread.start()
                stderrThread.start()
                
                val finished = remote.waitForTimeout(120L, java.util.concurrent.TimeUnit.SECONDS.name)
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
                
                val resString = buildString {
                    if (stdoutText.isNotBlank()) append(stdoutText.trim())
                    if (stderrText.isNotBlank()) {
                        if (isNotEmpty()) append("\n")
                        append("[E] ")
                        append(stderrText.trim())
                    }
                    if (!finished) {
                        if (isNotEmpty()) append("\n")
                        append("[E] Command timed out after 120 seconds.")
                    } else if (exitCode != 0) {
                        if (isNotEmpty()) append("\n")
                        append("[E] Command exited with code $exitCode.")
                    }
                    if (isEmpty()) append("Command completed with no output.")
                }
                val hasFailed = !finished || exitCode != 0 || stderrText.isNotBlank()
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
            isRunning = true
            aiExplanation = ""
            
            if (isRecording) {
                recordedCommands.add(cmd)
            }

            if (isAdbMode) {
                outputLog = "ADB Command Translation Mode Active\nOriginal: $cmd\nExecuting...\n"
            } else {
                outputLog = "Executing: $cmd ...\n"
            }

            val (result, hasFailed) = executeCommandInternal(cmd)
            outputLog = result
            isRunning = false

            if (hasFailed && ModuleSettings.isComputAiExplainEnabled()) {
                val apiKey = ModuleSettings.getComputApiKey()
                if (apiKey.isNotBlank()) {
                    scope.launch {
                        isExplaining = true
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
            isRunning = true
            aiExplanation = ""
            val fullLog = StringBuilder()
            fullLog.append("Running macro: $macroName\n\n")
            outputLog = fullLog.toString()

            for (cmd in commands) {
                fullLog.append("> Executing command: $cmd\n")
                outputLog = fullLog.toString()
                
                val (result, hasFailed) = executeCommandInternal(cmd)
                fullLog.append(result).append("\n\n")
                outputLog = fullLog.toString()

                if (hasFailed && ModuleSettings.isComputAiExplainEnabled()) {
                    val apiKey = ModuleSettings.getComputApiKey()
                    if (apiKey.isNotBlank()) {
                        scope.launch {
                            isExplaining = true
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
        }
    }

    fun requestRun() {
        if (ModuleSettings.isComputRecommandEnabled()) {
            showReCommandPrompt = true
        } else {
            runShellCommand(command)
        }
    }

    ShizukuLazyScaffold(
        title = "Comput",
        onNavigateUp = null
    ) {
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "ADB Comput Console",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = RoundedCornerShape(24.dp)
                            )
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val shModeColor = if (!isAdbMode) MaterialTheme.colorScheme.primary else Color.Transparent
                        val shTextColor = if (!isAdbMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        val adbModeColor = if (isAdbMode) MaterialTheme.colorScheme.primary else Color.Transparent
                        val adbTextColor = if (isAdbMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(20.dp))
                                .background(shModeColor)
                                .clickable { isAdbMode = false }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "sh Mode",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = shTextColor
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(20.dp))
                                .background(adbModeColor)
                                .clickable { isAdbMode = true }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "adb Mode",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = adbTextColor
                            )
                        }
                    }
                    
                    OutlinedTextField(
                        value = command,
                        onValueChange = { command = it },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp
                        ),
                        label = { Text("Command") },
                        placeholder = { Text("e.g. pm list packages") },
                        maxLines = 4,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { requestRun() },
                            enabled = !isRunning && command.isNotBlank(),
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.large
                        ) {
                            if (isRunning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = "Run",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.size(6.dp))
                                Text("Run")
                            }
                        }
                        
                        TextButton(
                            onClick = {
                                command = ""
                                outputLog = "Console cleared."
                                aiExplanation = ""
                            },
                            enabled = !isRunning,
                            shape = MaterialTheme.shapes.large
                        ) {
                            Icon(Icons.Rounded.Clear, contentDescription = "Clear")
                            Spacer(Modifier.size(6.dp))
                            Text("Clear")
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Console Output",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("ADB Output", outputLog))
                                Toast.makeText(context, "Output copied to clipboard", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ContentCopy,
                                contentDescription = "Copy Output",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    
                    Spacer(Modifier.height(8.dp))
                    
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 160.dp, max = 320.dp)
                            .background(Color.Black.copy(alpha = 0.05f))
                            .verticalScroll(rememberScrollState()),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        SelectionContainer {
                            Text(
                                text = annotatedOutput,
                                modifier = Modifier.padding(12.dp),
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp
                                )
                            )
                        }
                    }
                }
            }
        }

        if (outputLog.isNotBlank() && !isRunning) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = "AI Explanation",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.size(8.dp))
                                Text(
                                    text = "Gemini Explain",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            
                            Button(
                                onClick = {
                                    scope.launch {
                                        isExplaining = true
                                        val apiKey = ModuleSettings.getComputApiKey()
                                        aiExplanation = explainCommandWithGemini(command, outputLog, apiKey)
                                        isExplaining = false
                                    }
                                },
                                enabled = !isExplaining,
                                shape = MaterialTheme.shapes.large,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                if (isExplaining) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text("Ask Gemini")
                                }
                            }
                        }
                        
                        if (aiExplanation.isNotBlank()) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f))
                            SelectionContainer {
                                Text(
                                    text = aiExplanation,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.8f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = "Commandium",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Commandium AI Assistant",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Text(
                        text = "Generate shell commands using natural language. Gemini will output a raw, copyable command.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = commandiumPrompt,
                        onValueChange = { commandiumPrompt = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("What command do you need?") },
                        placeholder = { Text("e.g. Find all files larger than 10MB in /sdcard") },
                        maxLines = 3,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        )
                    )

                    Button(
                        onClick = {
                            scope.launch {
                                isCommandiumGenerating = true
                                val apiKey = ModuleSettings.getComputApiKey()
                                generatedCommandiumResult = AiExplainUtil.generateCommand(commandiumPrompt, apiKey)
                                isCommandiumGenerating = false
                            }
                        },
                        enabled = !isCommandiumGenerating && commandiumPrompt.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large
                    ) {
                        if (isCommandiumGenerating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Ask Commandium")
                        }
                    }

                    if (generatedCommandiumResult.isNotBlank()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                            shape = MaterialTheme.shapes.medium,
                            border = CardDefaults.outlinedCardBorder()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Generated Command:",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                SelectionContainer {
                                    Text(
                                        text = generatedCommandiumResult,
                                        style = TextStyle(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
                                                shape = MaterialTheme.shapes.small
                                            )
                                            .padding(8.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            command = generatedCommandiumResult
                                            Toast.makeText(context, "Command pasted to input", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                        shape = MaterialTheme.shapes.medium
                                    ) {
                                        Text("Use command")
                                    }
                                    IconButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("Commandium", generatedCommandiumResult))
                                            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.ContentCopy,
                                            contentDescription = "Copy command"
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.8f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Console Macros",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        if (isRecording) {
                            Button(
                                onClick = {
                                    showSaveMacroDialog = true
                                    isRecording = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350)),
                                shape = MaterialTheme.shapes.large
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(Color.White, shape = RoundedCornerShape(5.dp))
                                )
                                Spacer(Modifier.size(6.dp))
                                Text("Stop (${recordedCommands.size})")
                            }
                        } else {
                            Button(
                                onClick = {
                                    recordedCommands.clear()
                                    isRecording = true
                                },
                                shape = MaterialTheme.shapes.large
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(Color.Red, shape = RoundedCornerShape(5.dp))
                                )
                                Spacer(Modifier.size(6.dp))
                                Text("Record Macro")
                            }
                        }
                    }

                    if (isRecording) {
                        Text(
                            text = "Recording active. Run commands using the main input to add them to this macro.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFEF5350),
                            fontWeight = FontWeight.SemiBold
                        )
                        if (recordedCommands.isNotEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.Black.copy(alpha = 0.05f), MaterialTheme.shapes.medium)
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                recordedCommands.forEachIndexed { i, c ->
                                    Text(
                                        text = "${i + 1}. $c",
                                        style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))

                    Text(
                        text = "Saved Macros",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (savedMacros.isEmpty()) {
                        Text(
                            text = "No saved macros yet. Click 'Record Macro' to start recording console commands.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            savedMacros.forEach { (name, cmds) ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                                    shape = MaterialTheme.shapes.large,
                                    border = CardDefaults.outlinedCardBorder()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = name,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color.Black.copy(alpha = 0.03f), MaterialTheme.shapes.small)
                                                .padding(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            cmds.take(3).forEach { c ->
                                                Text(
                                                    text = "> $c",
                                                    maxLines = 1,
                                                    style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            if (cmds.size > 3) {
                                                Text(
                                                    text = "... and ${cmds.size - 3} more",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Button(
                                                onClick = { runMacro(name, cmds) },
                                                enabled = !isRunning,
                                                modifier = Modifier.weight(1f),
                                                shape = MaterialTheme.shapes.medium
                                            ) {
                                                Text("Run")
                                            }
                                            TextButton(
                                                onClick = { deleteMacro(name) },
                                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                                shape = MaterialTheme.shapes.medium
                                            ) {
                                                Text("Delete")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showReCommandPrompt) {
        AlertDialog(
            onDismissRequest = { showReCommandPrompt = false },
            title = { Text("ReCommand confirmation") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Are you sure you want to execute this ADB command in Comput?",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(
                            text = command,
                            modifier = Modifier.padding(10.dp),
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
                    }
                ) {
                    Text("Execute")
                }
            },
            dismissButton = {
                TextButton(onClick = { showReCommandPrompt = false }) {
                    Text("Cancel")
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.extraLarge
        )
    }

    if (showSaveMacroDialog) {
        AlertDialog(
            onDismissRequest = { showSaveMacroDialog = false },
            title = { Text("Save Macro") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Enter a name for the recorded macro:")
                    OutlinedTextField(
                        value = macroNameInput,
                        onValueChange = { macroNameInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Macro Name") },
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
                    enabled = macroNameInput.isNotBlank()
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        recordedCommands.clear()
                        macroNameInput = ""
                        showSaveMacroDialog = false
                    }
                ) {
                    Text("Cancel")
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.extraLarge
        )
    }
}

private fun buildAnnotatedLog(
    text: String,
    errorColor: Color,
    warningColor: Color,
    normalColor: Color
): AnnotatedString {
    return buildAnnotatedString {
        val lines = text.split("\n")
        lines.forEachIndexed { index, line ->
            val isError = line.contains("error", ignoreCase = true) ||
                    line.contains("failed", ignoreCase = true) ||
                    line.contains("exception", ignoreCase = true) ||
                    line.contains("[E]", ignoreCase = true) ||
                    line.contains("denied", ignoreCase = true)
            val isWarning = line.contains("warn", ignoreCase = true) ||
                    line.contains("[W]", ignoreCase = true)

            if (isError) {
                withStyle(style = SpanStyle(color = errorColor, fontWeight = FontWeight.Bold)) {
                    append(line)
                }
            } else if (isWarning) {
                withStyle(style = SpanStyle(color = warningColor, fontWeight = FontWeight.SemiBold)) {
                    append(line)
                }
            } else {
                withStyle(style = SpanStyle(color = normalColor)) {
                    append(line)
                }
            }
            if (index < lines.lastIndex) {
                append("\n")
            }
        }
    }
}

private suspend fun explainCommandWithGemini(
    command: String,
    output: String,
    apiKey: String
): String = withContext(Dispatchers.IO) {
    if (apiKey.isBlank()) {
        return@withContext "Google AI Studio API Key is empty! Please configure it in Shevery Settings (Comput Console Settings)."
    }
    try {
        val selectedModel = ModuleSettings.getComputGeminiModel()
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$selectedModel:generateContent?key=$apiKey")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")

        val currentLocale = java.util.Locale.getDefault()
        val prompt = "CRITICAL: You must write the entire explanation in the following language: ${currentLocale.displayName} (locale code: ${currentLocale.toLanguageTag()}).\n\n" +
                "Explain the following shell command and its execution output in a clear, concise, and helpful developer-focused way. If there are errors or warnings, explain what caused them and how to resolve them:\n\n" +
                "Command: $command\n\n" +
                "Output:\n$output"
        val requestBody = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                    })
                })
            })
        }

        conn.outputStream.use { os ->
            os.write(requestBody.toString().toByteArray(Charsets.UTF_8))
            os.flush()
        }

        val responseCode = conn.responseCode
        if (responseCode == 200) {
            val responseText = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(responseText)
            val text = json.getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
            text.trim()
        } else {
            val errText = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "No details."
            "Gemini API returned error code $responseCode: $errText"
        }
    } catch (e: Exception) {
        "Failed to reach Gemini API: ${e.message ?: "Connection error."}"
    }
}

private fun readStreamTail(pfd: ParcelFileDescriptor): String {
    return ParcelFileDescriptor.AutoCloseInputStream(pfd).reader(Charsets.UTF_8).use { reader ->
        val buffer = CharArray(8192)
        val tail = StringBuilder()
        while (true) {
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

