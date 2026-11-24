@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package moe.shizuku.manager.home

import android.content.Context
import android.os.Build
import android.os.Process
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.shizuku.manager.BuildConfig
import moe.shizuku.manager.R
import moe.shizuku.manager.guide.GuideTopic
import moe.shizuku.manager.guide.ShakeGuideHost
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.module.ModuleSettings
import moe.shizuku.manager.ui.compose.ShizukuIcon
import rikka.core.util.ClipboardUtils
import rikka.shizuku.Shizuku
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val SHEVERY_LOG_KEYWORDS = listOf(
    "moe.shizuku",
    "shevery",
    "shizuku",
    "dhizuku",
    "commandium",
    "comput",
    "errorprotect",
    "watchdog",
    "adb",
    "rish",
    "starter",
    "androidruntime",
    "system.err",
    "fatal"
)

private val WHITESPACE_REGEX = Regex("\\s+")

internal fun collectSheverySmartLogs(
    context: Context,
    diagnosticsSummary: String? = null
): String {
    val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.US).format(Date())
    val versionName = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
    }.getOrDefault("unknown")

    val myPid = Process.myPid().toString()
    val myUid = Process.myUid()
    val rawLines = readLogcatLines()
    val filteredLines = filterSheveryLogLines(rawLines, myPid)
    val deduplicated = deduplicateConsecutiveLogs(filteredLines)
    val warningsAndErrors = extractWarningsAndErrors(deduplicated)
    val recentLines = if (deduplicated.size > 300) {
        deduplicated.takeLast(300)
    } else {
        deduplicated
    }

    return buildString {
        appendLine("=== SHEVERY SMART DIAGNOSTICS ===")
        appendLine("Generated: $timestamp")
        appendLine("Package: ${context.packageName}")
        appendLine("Version: $versionName (${BuildConfig.VERSION_CODE}, ${BuildConfig.BUILD_TYPE})")
        appendLine("Process: pid=$myPid uid=$myUid")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
        appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}, ${Build.SUPPORTED_ABIS.joinToString()})")
        appendLine(
            "Settings: tcpMode=${ShizukuSettings.isTcpMode()} (port=${ShizukuSettings.getTcpModePort()}), " +
                "classicNav=${ShizukuSettings.isClassicNav()}, " +
                "motionBlur=${ShizukuSettings.isMotionBlurEnabled()}, " +
                "autoscaleApp=${ShizukuSettings.isAutoscaleAppEnabled()}, " +
                "verboseLogging=${ModuleSettings.isVerboseLogging()}, " +
                "watchdog=${ModuleSettings.isWatchdogEnabled()}"
        )
        if (!diagnosticsSummary.isNullOrBlank()) {
            appendLine()
            appendLine("--- Diagnostics Snapshot ---")
            appendLine(diagnosticsSummary.trim())
        } else {
            val binderAlive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
            val serverUid = if (binderAlive) runCatching { Shizuku.getUid() }.getOrNull() else null
            val serverApi = if (binderAlive) runCatching { Shizuku.getVersion() }.getOrNull() else null
            appendLine("Binder: ${if (binderAlive) "alive (uid=$serverUid, api=$serverApi)" else "stopped"}")
        }

        appendLine()
        appendLine("=== WARNINGS & ERRORS (SMART SUMMARY) ===")
        if (warningsAndErrors.isEmpty()) {
            appendLine("None detected in recent Shevery log buffer.")
        } else {
            warningsAndErrors.forEach { appendLine(it) }
        }

        appendLine()
        appendLine("=== RECENT SHEVERY LOGS (${recentLines.size} lines) ===")
        if (recentLines.isEmpty()) {
            appendLine(context.getString(R.string.home_diagnostics_smart_logs_empty))
        } else {
            recentLines.forEach { appendLine(it) }
        }
    }.trim()
}

private fun readLogcatLines(): List<String> {
    val commands = listOf(
        listOf("logcat", "-b", "main,system,crash", "-d", "-v", "threadtime", "-t", "1200"),
        listOf("logcat", "-d", "-v", "threadtime", "-t", "1200")
    )
    for (cmd in commands) {
        val output = runCatching {
            val process = ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start()
            val lines = process.inputStream.bufferedReader().useLines { seq ->
                seq.filter { it.isNotBlank() && !it.startsWith("--------- beginning of") }.toList()
            }
            val exitCode = process.waitFor()
            if (exitCode == 0 || lines.isNotEmpty()) lines else emptyList()
        }.getOrDefault(emptyList())
        if (output.isNotEmpty()) return output
    }
    return emptyList()
}

private fun filterSheveryLogLines(lines: List<String>, myPid: String): List<String> {
    if (lines.isEmpty()) return emptyList()
    val matched = lines.filter { line ->
        val tokens = line.trimStart().split(WHITESPACE_REGEX, limit = 6)
        val linePid = tokens.getOrNull(2)
        if (linePid == myPid) {
            true
        } else {
            val lower = line.lowercase(Locale.ROOT)
            SHEVERY_LOG_KEYWORDS.any { keyword -> lower.contains(keyword) }
        }
    }
    return matched.ifEmpty { lines }
}

private fun logBodySignature(line: String): String {
    val tokens = line.trimStart().split(WHITESPACE_REGEX, limit = 6)
    return if (tokens.size >= 6) {
        "${tokens[4]} ${tokens[5]}"
    } else {
        line
    }
}

private fun deduplicateConsecutiveLogs(lines: List<String>): List<String> {
    if (lines.isEmpty()) return emptyList()
    val result = ArrayList<String>(lines.size)
    var previousSignature: String? = null
    var repeatCount = 0

    for (line in lines) {
        val signature = logBodySignature(line)
        if (signature == previousSignature) {
            repeatCount++
        } else {
            if (repeatCount > 0) {
                result.add("  ↳ [repeated $repeatCount more time${if (repeatCount == 1) "" else "s"}]")
                repeatCount = 0
            }
            result.add(line)
            previousSignature = signature
        }
    }
    if (repeatCount > 0) {
        result.add("  ↳ [repeated $repeatCount more time${if (repeatCount == 1) "" else "s"}]")
    }
    return result
}

private fun extractWarningsAndErrors(lines: List<String>): List<String> {
    val interesting = lines.filter { line ->
        val tokens = line.trimStart().split(WHITESPACE_REGEX, limit = 6)
        val level = tokens.getOrNull(4)
        level == "W" ||
            level == "E" ||
            level == "F" ||
            level == "A" ||
            line.contains("Exception", ignoreCase = false) ||
            line.contains("FATAL", ignoreCase = false) ||
            line.contains("Caused by:", ignoreCase = false) ||
            line.contains("ANR in ", ignoreCase = false)
    }
    return if (interesting.size > 40) interesting.takeLast(40) else interesting
}

@Composable
fun SheverySmartLogsDialog(
    diagnosticsSummary: String? = null,
    snapshotOnly: Boolean = false,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refreshTick by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var logsText by remember { mutableStateOf("") }

    LaunchedEffect(refreshTick, diagnosticsSummary, snapshotOnly) {
        isLoading = true
        logsText = withContext(Dispatchers.IO) {
            if (snapshotOnly) diagnosticsSummary.orEmpty() else collectSheverySmartLogs(context, diagnosticsSummary)
        }
        isLoading = false
    }

    val saveLogLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val contentToSave = logsText
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                        writer.write(contentToSave)
                    } ?: error("Failed to open output stream")
                }
            }.onSuccess {
                Toast.makeText(
                    context,
                    R.string.home_diagnostics_smart_logs_saved,
                    Toast.LENGTH_SHORT
                ).show()
            }.onFailure { error ->
                Toast.makeText(
                    context,
                    context.getString(
                        R.string.home_diagnostics_smart_logs_save_failed,
                        error.message ?: "unknown"
                    ),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(if (snapshotOnly) R.string.lab_ipc_title else R.string.home_diagnostics_smart_logs_title),
                    modifier = Modifier.weight(1f)
                )
                if (!snapshotOnly) IconButton(
                    onClick = { refreshTick++ },
                    enabled = !isLoading
                ) {
                    ShizukuIcon(
                        icon = R.drawable.ic_server_restart,
                        contentDescription = stringResource(R.string.home_refresh)
                    )
                }
            }
        },
        text = {
            ShakeGuideHost(GuideTopic.FAQ)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(if (snapshotOnly) R.string.lab_ipc_summary else R.string.home_diagnostics_smart_logs_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 360.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest
                ) {
                    if (isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(28.dp))
                                Text(
                                    text = stringResource(R.string.home_diagnostics_smart_logs_loading),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        SelectionContainer {
                            Text(
                                text = logsText,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState())
                                    .horizontalScroll(rememberScrollState())
                                    .padding(12.dp),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    enabled = !isLoading && logsText.isNotBlank(),
                    onClick = {
                        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                        saveLogLauncher.launch("shevery_logs_$stamp.log")
                    }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Download,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.home_diagnostics_smart_logs_download))
                }
                Button(
                    enabled = !isLoading && logsText.isNotBlank(),
                    onClick = {
                        ClipboardUtils.put(context, logsText)
                        Toast.makeText(
                            context,
                            R.string.home_diagnostics_smart_logs_copied,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.home_diagnostics_smart_logs_copy))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge
    )
}
