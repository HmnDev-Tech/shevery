@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)

package moe.shizuku.manager.logs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import moe.shizuku.manager.R
import moe.shizuku.manager.commandium.AiProviderRepository
import moe.shizuku.manager.comput.agent.AgentApproval
import moe.shizuku.manager.comput.agent.AgentExecutedAction
import moe.shizuku.manager.comput.agent.AgentModuleInfo
import moe.shizuku.manager.comput.agent.AgentPlan
import moe.shizuku.manager.comput.agent.AgentSafetyMode
import moe.shizuku.manager.comput.agent.AgentStep
import moe.shizuku.manager.comput.agent.AgentStepKind
import moe.shizuku.manager.comput.agent.AgentStepStatus
import moe.shizuku.manager.comput.agent.ComputAgent
import moe.shizuku.manager.module.ModuleSettings

private enum class AgentPhase { IDLE, PLANNING, EXECUTING, DONE }

/**
 * Agentic loop over the active AI provider: the model plans shell commands and
 * module-service runs, the app executes them step by step (asking first unless
 * the safety mode says otherwise), comments every action and shows progress,
 * then reports a full answer plus the executed actions.
 *
 * Shell / module execution itself is injected from [ComputScreen] so this sheet
 * stays free of Shizuku plumbing.
 */
@Composable
fun ComputAgentSheet(
    onDismiss: () -> Unit,
    onConfigureProvider: () -> Unit = {},
    onCopy: (String) -> Unit = {},
    modulesProvider: suspend () -> List<AgentModuleInfo>,
    onRunShell: suspend (String) -> Pair<String, Boolean>,
    onRunModuleService: suspend (String) -> Pair<String, Boolean>,
) {
    val scope = rememberCoroutineScope()
    var goal by remember { mutableStateOf("") }
    var phase by remember { mutableStateOf(AgentPhase.IDLE) }
    var plan by remember { mutableStateOf<AgentPlan?>(null) }
    var statuses by remember { mutableStateOf<List<AgentStepStatus>>(emptyList()) }
    var outputs by remember { mutableStateOf<List<String>>(emptyList()) }
    var finalAnswer by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var summarizing by remember { mutableStateOf(false) }
    var stopped by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }

    var approvalIndex by remember { mutableStateOf<Int?>(null) }
    var approvalGate by remember { mutableStateOf<CompletableDeferred<AgentApproval>?>(null) }
    var showCustomEditor by remember { mutableStateOf(false) }
    var customTarget by remember { mutableStateOf("") }

    val safetyMode = ModuleSettings.getAgentSafetyMode()
    val hasProvider = AiProviderRepository.getActive() != null &&
        AiProviderRepository.getActiveKey().isNotBlank()

    fun currentLocale(): Pair<String, String> {
        val locale = Locale.getDefault()
        return locale.toLanguageTag() to locale.getDisplayName(Locale.ENGLISH)
    }

    fun start() {
        if (goal.isBlank() || phase == AgentPhase.PLANNING || phase == AgentPhase.EXECUTING) return
        error = null
        finalAnswer = ""
        stopped = false
        plan = null
        statuses = emptyList()
        outputs = emptyList()
        job = scope.launch {
            var allowAlways = false
            try {
                phase = AgentPhase.PLANNING
                val modules = try {
                    modulesProvider()
                } catch (e: Exception) {
                    emptyList()
                }
                val mode = ModuleSettings.getAgentSafetyMode()
                val maxSteps = ModuleSettings.getAgentMaxSteps()
                val askShell = ModuleSettings.isAgentAskShell()
                val askModule = ModuleSettings.isAgentAskModuleService()
                val (tag, name) = currentLocale()
                val planResult = ComputAgent.buildPlan(goal.trim(), modules, maxSteps, tag, name)
                val built = planResult.getOrElse { e ->
                    error = e.message ?: "Planning failed."
                    phase = AgentPhase.IDLE
                    return@launch
                }
                if (built.steps.isEmpty()) {
                    error = null
                    plan = built
                    phase = AgentPhase.DONE
                    return@launch
                }
                if (built.steps.size > maxSteps) {
                    plan = built.copy(steps = built.steps.take(maxSteps))
                } else {
                    plan = built
                }
                val steps = plan!!.steps
                statuses = List(steps.size) { AgentStepStatus.PENDING }
                outputs = List(steps.size) { "" }
                phase = AgentPhase.EXECUTING

                val executed = mutableListOf<AgentExecutedAction>()
                for (i in steps.indices) {
                    if (!isActive) break
                    val step = steps[i]
                    var target = step.target
                    if (!allowAlways && ComputAgent.requiresApproval(mode, askShell, askModule, step)) {
                        val gate = CompletableDeferred<AgentApproval>()
                        customTarget = target
                        showCustomEditor = false
                        approvalGate = gate
                        approvalIndex = i
                        val decision = try {
                            gate.await()
                        } finally {
                            approvalGate = null
                            approvalIndex = null
                            showCustomEditor = false
                        }
                        when (decision) {
                            is AgentApproval.Deny -> {
                                statuses = statuses.toMutableList().also { it[i] = AgentStepStatus.DENIED }
                                executed += AgentExecutedAction(step, AgentStepStatus.DENIED, "")
                                continue
                            }
                            is AgentApproval.Allow -> {
                                if (!decision.overrideTarget.isNullOrBlank()) target = decision.overrideTarget
                            }
                            is AgentApproval.AllowAlways -> {
                                allowAlways = true
                                if (!decision.overrideTarget.isNullOrBlank()) target = decision.overrideTarget
                            }
                        }
                    }
                    statuses = statuses.toMutableList().also { it[i] = AgentStepStatus.RUNNING }
                    try {
                        val (out, failed) = when (step.kind) {
                            AgentStepKind.SHELL -> onRunShell(target)
                            AgentStepKind.MODULE_SERVICE -> onRunModuleService(target)
                        }
                        val status = if (failed) AgentStepStatus.FAILED else AgentStepStatus.DONE
                        val finalStep = if (target != step.target) step.copy(target = target) else step
                        statuses = statuses.toMutableList().also { it[i] = status }
                        outputs = outputs.toMutableList().also { it[i] = out }
                        executed += AgentExecutedAction(finalStep, status, out)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        statuses = statuses.toMutableList().also { it[i] = AgentStepStatus.FAILED }
                        val msg = e.message ?: "Execution failed."
                        outputs = outputs.toMutableList().also { it[i] = msg }
                        executed += AgentExecutedAction(step, AgentStepStatus.FAILED, msg)
                    }
                }
                val (tag2, name2) = currentLocale()
                if (executed.isNotEmpty()) {
                    summarizing = true
                    finalAnswer = ComputAgent.summarize(goal.trim(), executed, tag2, name2)
                        .getOrElse { executed.joinToString("\n\n") { it.output.ifBlank { it.status.name } } }
                    summarizing = false
                }
                phase = AgentPhase.DONE
            } catch (e: CancellationException) {
                stopped = true
                statuses = statuses.map { if (it == AgentStepStatus.RUNNING || it == AgentStepStatus.PENDING) AgentStepStatus.SKIPPED else it }
                phase = AgentPhase.DONE
            } catch (e: Exception) {
                error = e.message ?: "Agent failed."
                phase = AgentPhase.IDLE
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    ModalBottomSheet(
        onDismissRequest = {
            stop()
            onDismiss()
        },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        val scroll = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scroll)
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.SmartToy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.comput_agent_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = {
                    stop()
                    onDismiss()
                }) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(android.R.string.cancel))
                }
            }
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = CircleShape,
            ) {
                Text(
                    text = when (safetyMode) {
                        AgentSafetyMode.SECURE -> stringResource(R.string.comput_agent_mode_secure)
                        AgentSafetyMode.TURBO -> stringResource(R.string.comput_agent_mode_turbo)
                        AgentSafetyMode.CUSTOM -> stringResource(R.string.comput_agent_mode_custom)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            Text(
                text = stringResource(R.string.comput_agent_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (!hasProvider) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp),
                        )
                        Text(
                            text = stringResource(R.string.comput_ai_no_provider_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = stringResource(R.string.comput_ai_no_provider_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Button(onClick = onConfigureProvider, shape = CircleShape) {
                            Text(stringResource(R.string.comput_ai_configure_provider), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                OutlinedTextField(
                    value = goal,
                    onValueChange = { goal = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    label = { Text(stringResource(R.string.comput_agent_goal_label)) },
                    placeholder = { Text(stringResource(R.string.comput_agent_goal_placeholder)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { start() }),
                    maxLines = 3,
                    enabled = phase == AgentPhase.IDLE || phase == AgentPhase.DONE,
                )
                Button(
                    onClick = {
                        if (phase == AgentPhase.EXECUTING || phase == AgentPhase.PLANNING) stop() else start()
                    },
                    enabled = hasProvider && (goal.isNotBlank() || phase == AgentPhase.EXECUTING || phase == AgentPhase.PLANNING),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = CircleShape,
                    colors = if (phase == AgentPhase.EXECUTING || phase == AgentPhase.PLANNING) {
                        ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    } else {
                        ButtonDefaults.buttonColors()
                    },
                ) {
                    Icon(
                        imageVector = if (phase == AgentPhase.EXECUTING || phase == AgentPhase.PLANNING) {
                            Icons.Rounded.Stop
                        } else {
                            Icons.Rounded.PlayArrow
                        },
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (phase == AgentPhase.EXECUTING || phase == AgentPhase.PLANNING) {
                            stringResource(R.string.comput_agent_stop)
                        } else {
                            stringResource(R.string.comput_agent_run)
                        },
                        fontWeight = FontWeight.Bold,
                    )
                }

                val statusText = when (phase) {
                    AgentPhase.PLANNING -> stringResource(R.string.comput_agent_planning)
                    AgentPhase.EXECUTING -> {
                        val done = statuses.count { it == AgentStepStatus.DONE || it == AgentStepStatus.FAILED || it == AgentStepStatus.DENIED }
                        stringResource(R.string.comput_agent_executing, done, statuses.size)
                    }
                    else -> ""
                }
                if (statusText.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = statusText,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (error != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = error!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }

                val currentPlan = plan
                if (currentPlan != null && currentPlan.steps.isEmpty() && phase == AgentPhase.DONE) {
                    Text(
                        text = stringResource(R.string.comput_agent_plan_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (currentPlan != null && currentPlan.steps.isNotEmpty()) {
                    val done = statuses.count {
                        it == AgentStepStatus.DONE || it == AgentStepStatus.FAILED ||
                            it == AgentStepStatus.DENIED || it == AgentStepStatus.SKIPPED
                    }
                    LinearProgressIndicator(
                        progress = { if (statuses.isEmpty()) 0f else done.toFloat() / statuses.size },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    currentPlan.steps.forEachIndexed { index, step ->
                        AgentStepCard(
                            index = index,
                            step = step,
                            status = statuses.getOrNull(index) ?: AgentStepStatus.PENDING,
                            output = outputs.getOrNull(index).orEmpty(),
                        )
                    }
                }

                if (summarizing) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.comput_agent_summarizing),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (finalAnswer.isNotBlank()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.SmartToy,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.comput_agent_final_answer),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(
                                    onClick = { onCopy(finalAnswer) },
                                    modifier = Modifier.size(28.dp),
                                ) {
                                    Icon(
                                        Icons.Rounded.ContentCopy,
                                        contentDescription = stringResource(R.string.comput_copy_output),
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                            SelectionContainer {
                                Text(
                                    text = finalAnswer,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                        }
                    }
                }
                if (stopped) {
                    Text(
                        text = stringResource(R.string.comput_agent_stopped),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    val pending = approvalIndex
    val gate = approvalGate
    if (pending != null && gate != null) {
        val step = plan?.steps?.getOrNull(pending)
        if (step != null) {
            AgentApprovalDialog(
                step = step,
                customEditorOpen = showCustomEditor,
                customTarget = customTarget,
                onCustomTargetChange = { customTarget = it },
                onOpenCustomEditor = { showCustomEditor = true },
                onAllow = { gate.complete(AgentApproval.Allow(if (showCustomEditor) customTarget else null)); },
                onAllowAlways = { gate.complete(AgentApproval.AllowAlways(if (showCustomEditor) customTarget else null)); },
                onDeny = { gate.complete(AgentApproval.Deny); },
            )
        }
    }
}

@Composable
private fun AgentStepCard(
    index: Int,
    step: AgentStep,
    status: AgentStepStatus,
    output: String,
) {
    val (icon, tint) = when (status) {
        AgentStepStatus.PENDING -> Icons.Rounded.HourglassEmpty to MaterialTheme.colorScheme.onSurfaceVariant
        AgentStepStatus.RUNNING -> Icons.Rounded.PlayArrow to MaterialTheme.colorScheme.primary
        AgentStepStatus.DONE -> Icons.Rounded.CheckCircle to MaterialTheme.colorScheme.primary
        AgentStepStatus.FAILED -> Icons.Rounded.ErrorOutline to MaterialTheme.colorScheme.error
        AgentStepStatus.DENIED, AgentStepStatus.SKIPPED ->
            Icons.Rounded.Close to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "#${index + 1} · " + stringResource(
                        if (step.kind == AgentStepKind.SHELL) {
                            R.string.comput_agent_step_shell
                        } else {
                            R.string.comput_agent_step_service
                        },
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                if (status == AgentStepStatus.RUNNING) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                }
            }
            if (step.comment.isNotBlank()) {
                Text(text = step.comment, style = MaterialTheme.typography.bodySmall)
            }
            SelectionContainer {
                Text(
                    text = step.target,
                    style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if ((status == AgentStepStatus.DONE || status == AgentStepStatus.FAILED) && output.isNotBlank()) {
                Text(
                    text = output.take(600).trim(),
                    style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                    color = if (status == AgentStepStatus.FAILED) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            if (status == AgentStepStatus.DENIED) {
                Text(
                    text = stringResource(R.string.comput_agent_denied),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AgentApprovalDialog(
    step: AgentStep,
    customEditorOpen: Boolean,
    customTarget: String,
    onCustomTargetChange: (String) -> Unit,
    onOpenCustomEditor: () -> Unit,
    onAllow: () -> Unit,
    onAllowAlways: () -> Unit,
    onDeny: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        icon = {
            Icon(
                imageVector = Icons.Rounded.SmartToy,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = { Text(stringResource(R.string.comput_agent_approve_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (step.comment.isNotBlank()) {
                    Text(text = step.comment, style = MaterialTheme.typography.bodyMedium)
                }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(
                        text = step.target,
                        modifier = Modifier.padding(12.dp),
                        style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                    )
                }
                if (customEditorOpen && step.kind == AgentStepKind.SHELL) {
                    OutlinedTextField(
                        value = customTarget,
                        onValueChange = onCustomTargetChange,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        label = { Text(stringResource(R.string.comput_agent_custom_hint)) },
                        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                        maxLines = 4,
                    )
                }
                AgentApprovalButton(
                    label = stringResource(R.string.comput_agent_allow),
                    onClick = onAllow,
                    primary = true,
                )
                AgentApprovalButton(
                    label = stringResource(R.string.comput_agent_deny),
                    onClick = onDeny,
                    primary = false,
                )
                if (step.kind == AgentStepKind.SHELL && !customEditorOpen) {
                    AgentApprovalButton(
                        label = stringResource(R.string.comput_agent_custom),
                        onClick = onOpenCustomEditor,
                        primary = false,
                    )
                }
                AgentApprovalButton(
                    label = stringResource(R.string.comput_agent_allow_always),
                    onClick = onAllowAlways,
                    primary = false,
                )
            }
        },
        confirmButton = {},
        dismissButton = {},
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge,
    )
}

@Composable
private fun AgentApprovalButton(
    label: String,
    onClick: () -> Unit,
    primary: Boolean,
) {
    if (primary) {
        Button(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = CircleShape) {
            Text(label, fontWeight = FontWeight.Bold)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = CircleShape) {
            Text(label)
        }
    }
}
