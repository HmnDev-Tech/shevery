package moe.shizuku.manager.module

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.shizuku.manager.R
import moe.shizuku.manager.ui.compose.MonospaceLog

enum class ModuleCommandSource {
    WEB_UI,
    ACTION
}

data class ModuleCommandRequest(
    val module: AdbModule,
    val source: ModuleCommandSource,
    val command: String
)

fun interface ModuleCommandReviewer {
    fun confirmCommand(request: ModuleCommandRequest): Boolean
}

@Composable
fun ReCommandDialog(
    request: ModuleCommandRequest,
    busy: Boolean = false,
    onDismiss: () -> Unit,
    onReject: () -> Unit,
    onApprove: () -> Unit
) {
    val context = LocalContext.current
    val canExpandCommand = request.command.length > COMMAND_PREVIEW_CHARS
    var expanded by remember(request.command) { mutableStateOf(!canExpandCommand) }
    val commandPreview = if (expanded || !canExpandCommand) {
        request.command
    } else {
        request.command.take(COMMAND_PREVIEW_CHARS) + "\n..."
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.modules_recommand_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(
                        R.string.modules_recommand_source,
                        request.module.name,
                        when (request.source) {
                            ModuleCommandSource.WEB_UI -> "WebUI"
                            ModuleCommandSource.ACTION -> "Action"
                        }
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                MonospaceLog(commandPreview)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (canExpandCommand) {
                        IconButton(onClick = { expanded = !expanded }) {
                            Icon(
                                imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                contentDescription = if (expanded) {
                                    stringResource(R.string.modules_recommand_collapse)
                                } else {
                                    stringResource(R.string.modules_recommand_expand)
                                }
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(
                                ClipData.newPlainText(
                                    context.getString(R.string.modules_recommand_command_clip_label),
                                    request.command
                                )
                            )
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = stringResource(R.string.modules_recommand_copy)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !busy,
                onClick = onApprove
            ) {
                Text(stringResource(R.string.modules_recommand_execute))
            }
        },
        dismissButton = {
            TextButton(onClick = onReject) {
                Text(stringResource(R.string.modules_recommand_close))
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge
    )
}

private const val COMMAND_PREVIEW_CHARS = 420
