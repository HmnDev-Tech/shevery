@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)

package moe.shizuku.manager.logs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.animateScrollTo
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import moe.shizuku.manager.R
import moe.shizuku.manager.commandium.AiModelSwitcherSheet
import moe.shizuku.manager.module.ModuleSettings
import moe.shizuku.manager.utils.AiExplainUtil

@Composable
fun CommandiumSheet(
    prompt: String,
    onPromptChange: (String) -> Unit,
    isGenerating: Boolean,
    onGeneratingChange: (Boolean) -> Unit,
    result: Result<String>?,
    onResultChange: (Result<String>?) -> Unit,
    scope: CoroutineScope,
    onDismiss: () -> Unit,
    onUseCommand: (String) -> Unit,
    onCopy: (String) -> Unit,
    onConfigureProvider: () -> Unit = {},
    history: List<String> = emptyList()
) {
    var generationJob by remember { mutableStateOf<Job?>(null) }
    val dismissAndCancel: () -> Unit = {
        generationJob?.cancel()
        onGeneratingChange(false)
        onResultChange(null)
        onDismiss()
    }
    val requestCommandium: () -> Unit = {
        if (prompt.isNotBlank() && !isGenerating) {
            onGeneratingChange(true)
            generationJob = scope.launch {
                val apiKey = moe.shizuku.manager.commandium.AiProviderRepository.getActive()
                    ?.let { moe.shizuku.manager.commandium.AiProviderRepository.getKey(it.id) }
                    ?: ModuleSettings.getComputApiKey()
                onResultChange(AiExplainUtil.generateCommand(prompt, apiKey))
                onGeneratingChange(false)
            }
        }
    }
    var showModelSwitcher by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { dismissAndCancel() },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = stringResource(R.string.comput_commandium_assistant),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        text = {
            val scrollState = rememberScrollState()
            Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(R.string.comput_commandium_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val activeProvider = moe.shizuku.manager.commandium.AiProviderRepository.getActive()
            val activeKey = activeProvider?.let { moe.shizuku.manager.commandium.AiProviderRepository.getKey(it.id) } ?: ""
            val activeLabel = when {
                activeProvider == null || activeKey.isBlank() ->
                    stringResource(R.string.comput_ai_chip_active, stringResource(R.string.comput_ai_key_missing))
                activeProvider.model.isNullOrBlank() -> {
                    val defaultModel = AiExplainUtil.resolveModel(activeProvider.baseUrl)
                    if (defaultModel.isBlank())
                        stringResource(R.string.comput_ai_chip_active, activeProvider.name)
                    else
                        stringResource(R.string.comput_ai_chip_active_model, activeProvider.name, defaultModel)
                }
                else ->
                    stringResource(R.string.comput_ai_chip_active_model, activeProvider.name, activeProvider.model)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = false,
                    onClick = { showModelSwitcher = true },
                    label = { Text(activeLabel, maxLines =1) },
                )
            }
            if (activeProvider == null || activeKey.isBlank()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = stringResource(R.string.comput_ai_no_provider_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = stringResource(R.string.comput_ai_no_provider_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = onConfigureProvider,
                            shape = CircleShape,
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text(stringResource(R.string.comput_ai_configure_provider), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (activeProvider != null && activeKey.isNotBlank()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    "List user installed apps",
                    "Check battery temperature & status",
                    "Find files larger than 50MB",
                    "Get device Android model & build"
                ).forEach { suggestion ->
                    FilterChip(
                        selected = prompt == suggestion,
                        onClick = { onPromptChange(suggestion) },
                        label = { Text(suggestion, style = MaterialTheme.typography.labelSmall) },
                        shape = CircleShape
                    )
                }
                if (history.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.comput_commandium_recent),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    history.take(5).forEach { past ->
                        FilterChip(
                            selected = prompt == past,
                            onClick = { onPromptChange(past) },
                            label = { Text(past, style = MaterialTheme.typography.labelSmall) },
                            shape = CircleShape
                        )
                    }
                }
            }

            OutlinedTextField(
                value = prompt,
                onValueChange = { onPromptChange(it) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                label = { Text(stringResource(R.string.comput_commandium_label)) },
                placeholder = { Text(stringResource(R.string.comput_commandium_placeholder)) },
                trailingIcon = {
                    IconButton(
                        onClick = { requestCommandium() }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Send,
                            contentDescription = stringResource(R.string.comput_command_send)
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Send
                ),
                keyboardActions = KeyboardActions(
                    onSend = { requestCommandium() }
                ),
                maxLines = 3
            )

            Button(
                onClick = { requestCommandium() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = CircleShape
            ) {
                if (isGenerating) {
                    LoadingIndicator(Modifier.size(18.dp), MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text(stringResource(R.string.comput_ask_commandium), fontWeight = FontWeight.Bold)
                }
            }

            // Persistent status node: TalkBack only announces live-region CONTENT
            // CHANGES reliably, so the node must stay composed and change its text
            // rather than being added/removed per state. Idle = empty (no announce).
            val statusText = when {
                isGenerating -> stringResource(R.string.comput_commandium_generating)
                result == null -> ""
                result.getOrNull() != null -> stringResource(R.string.comput_commandium_ready)
                else -> stringResource(R.string.comput_commandium_failed)
            }
            val statusColor = when {
                isGenerating -> MaterialTheme.colorScheme.primary
                result == null -> MaterialTheme.colorScheme.onSurfaceVariant
                result.getOrNull() != null -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.error
            }
            Text(
                text = statusText,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.labelMedium,
                color = statusColor
            )
            LaunchedEffect(result) {
                if (result != null) {
                    scrollState.animateScrollTo(scrollState.maxValue)
                }
            }
            }

            val outcome = result
            if (outcome != null) {
                val outcomeText = outcome.getOrNull()
                val isError = outcomeText == null
                val displayText = outcomeText ?: buildString {
                    append("Error: ")
                    append(outcome.exceptionOrNull()?.message ?: "Unknown error")
                }
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isError) {
                                Icon(
                                    imageVector = Icons.Rounded.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(
                                text = if (isError) stringResource(R.string.comput_commandium_error) else stringResource(R.string.comput_generated_command),
                                style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold),
                                color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        SelectionContainer {
                            Text(
                                text = displayText,
                                style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (!isError) {
                                Button(
                                    onClick = {
                                        onUseCommand(outcomeText!!)
                                        onDismiss()
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = CircleShape
                                ) {
                                    Text(stringResource(R.string.comput_use_command))
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { requestCommandium() },
                                    modifier = Modifier.weight(1f),
                                    shape = CircleShape
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.shevery_update_retry))
                                }
                            }
                            IconButton(
                                onClick = {
                                    onCopy(displayText)
                                }
                            ) {
                                Icon(Icons.Rounded.ContentCopy, contentDescription = stringResource(android.R.string.copy))
                            }
                        }
                    }
                }
            }
        }
        },
        confirmButton = {},

        dismissButton = {
            TextButton(onClick = { dismissAndCancel() }) {
                Text(stringResource(android.R.string.cancel))
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge
            )

            if (showModelSwitcher) {
                AiModelSwitcherSheet(
                    activeProviderId = moe.shizuku.manager.commandium.AiProviderRepository.getActive()?.id,
                    onSelect = { providerId, model ->
                        val target = moe.shizuku.manager.commandium.AiProviderRepository.getProviders().firstOrNull { it.id == providerId } ?: return@AiModelSwitcherSheet

                        moe.shizuku.manager.commandium.AiProviderRepository.update(target.copy(model = model))
                        if (providerId != moe.shizuku.manager.commandium.AiProviderRepository.getActiveId()) {

                            moe.shizuku.manager.commandium.AiProviderRepository.setActive(providerId)
                        }
                        showModelSwitcher = false
                    },
                    onDismiss ={ showModelSwitcher = false },
                )
            }
        }
