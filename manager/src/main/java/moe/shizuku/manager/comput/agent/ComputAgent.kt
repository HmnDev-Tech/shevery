package moe.shizuku.manager.comput.agent

import moe.shizuku.manager.commandium.AiProviderRepository
import moe.shizuku.manager.utils.AiClient
import moe.shizuku.manager.utils.AiExplainUtil
import org.json.JSONObject

/**
 * Safety modes for the Comput agent.
 *
 * SECURE asks before every step, TURBO runs the whole plan without asking,
 * CUSTOM asks per action type according to the user's own toggles.
 */
enum class AgentSafetyMode(val value: String) {
    SECURE("secure"),
    TURBO("turbo"),
    CUSTOM("custom");

    companion object {
        fun fromValue(value: String?): AgentSafetyMode =
            entries.firstOrNull { it.value == value } ?: SECURE
    }
}

enum class AgentStepKind { SHELL, MODULE_SERVICE }

data class AgentModuleInfo(
    val id: String,
    val name: String,
    val description: String,
    val hasService: Boolean,
)

data class AgentStep(
    val kind: AgentStepKind,
    /** Shell text for SHELL steps, module id for MODULE_SERVICE steps. */
    val target: String,
    /** Short per-action comment produced by the AI (user locale). */
    val comment: String,
)

data class AgentPlan(val goal: String, val steps: List<AgentStep>)

enum class AgentStepStatus { PENDING, RUNNING, DONE, FAILED, DENIED, SKIPPED }

data class AgentExecutedAction(
    val step: AgentStep,
    val status: AgentStepStatus,
    val output: String,
)

/** User decision in the per-step approval dialog (secure mode). */
sealed interface AgentApproval {
    /** Run once, optionally with an edited command typed by the user. */
    data class Allow(val overrideTarget: String? = null) : AgentApproval
    /** Skip this step and continue with the next one. */
    data object Deny : AgentApproval
    /** Run this and every remaining step without asking again. */
    data class AllowAlways(val overrideTarget: String? = null) : AgentApproval
}

object ComputAgent {

    const val MAX_STEPS_HARD = 10

    fun requiresApproval(
        mode: AgentSafetyMode,
        askShell: Boolean,
        askModuleService: Boolean,
        step: AgentStep,
    ): Boolean = when (mode) {
        AgentSafetyMode.SECURE -> true
        AgentSafetyMode.TURBO -> false
        AgentSafetyMode.CUSTOM -> when (step.kind) {
            AgentStepKind.SHELL -> askShell
            AgentStepKind.MODULE_SERVICE -> askModuleService
        }
    }

    fun modulesContext(modules: List<AgentModuleInfo>): String {
        val withService = modules.filter { it.hasService }
        if (withService.isEmpty()) return "No ADB modules with a service script are installed."
        return buildString {
            append("Installed ADB modules with a service script (use module_service only with these ids):\n")
            withService.forEach { m ->
                append("- id=\"${m.id}\" name=\"${m.name}\"")
                if (m.description.isNotBlank()) append(" — ${m.description.take(160)}")
                append("\n")
            }
        }
    }

    suspend fun buildPlan(
        goal: String,
        modules: List<AgentModuleInfo>,
        maxSteps: Int,
        localeTag: String,
        localeName: String,
    ): Result<AgentPlan> {
        val active = AiProviderRepository.getActive()
            ?: return Result.failure(IllegalStateException("no_provider"))
        val baseUrl = active.baseUrl.takeIf { it.isNotBlank() }
            ?: return Result.failure(IllegalStateException("no_provider"))
        val model = active.model.takeIf { it.isNotBlank() }
            ?: AiExplainUtil.resolveModel(baseUrl).takeIf { it.isNotBlank() }
            ?: return Result.failure(IllegalStateException("empty_model"))
        val key = AiProviderRepository.getKey(active.id)
        if (key.isBlank()) return Result.failure(IllegalStateException("empty_key"))

        val steps = maxSteps.coerceIn(1, MAX_STEPS_HARD)
        val systemPrompt = "You are the Comput agent inside the Shevery Android app. " +
            "You plan actions; the app executes them one by one in a privileged on-device " +
            "shell (Shizuku) and reports each result back to the user.\n" +
            "Shell environment: Android toybox with pm, am, dumpsys, settings, cmd, " +
            "service, getprop, wm, soc, toybox and standard text utilities. Linux-host " +
            "commands (apt, dpkg, systemctl, journalctl, ifconfig, iptables) do NOT exist " +
            "and must never be used.\n" +
            modulesContext(modules) + "\n" +
            "RULES:\n" +
            "1. Respond with ONLY a JSON object, no markdown fences, no prose.\n" +
            "2. Schema: {\"steps\": [{\"type\": \"shell\"|\"module_service\", " +
            "\"command\": \"...\", \"module_id\": \"...\", \"comment\": \"...\"}]}.\n" +
            "3. Use at most $steps steps, ordered so outputs of earlier steps inform later ones.\n" +
            "4. shell steps carry the raw shell command in \"command\".\n" +
            "5. module_service steps carry the module id in \"module_id\" and only for " +
            "modules listed above; otherwise use shell.\n" +
            "6. Every step needs a short \"comment\" (one sentence) explaining what it does.\n" +
            "7. Write every \"comment\" in this language: $localeName (locale code: $localeTag).\n" +
            "8. If the goal is impossible on-device, return {\"steps\": []}."
        val result = AiClient.chatCompletion(
            baseUrl = baseUrl,
            apiKey = key,
            model = model,
            systemPrompt = systemPrompt,
            userPrompt = "Goal: $goal",
        )
        return result.fold(
            onSuccess = { parsePlan(it, goal) },
            onFailure = { Result.failure(it) },
        )
    }

    fun parsePlan(raw: String, goal: String): Result<AgentPlan> {
        var text = raw.trim()
        val fence = Regex("(?is)```[a-zA-Z0-9_-]*\\s*\\n(.*?)\\n```").find(text)
        if (fence != null) text = fence.groupValues[1].trim()
        text = text
            .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        return try {
            val root = JSONObject(text)
            val arr = root.optJSONArray("steps") ?: return Result.failure(
                IllegalStateException("Agent returned no steps array."),
            )
            val steps = mutableListOf<AgentStep>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val type = obj.optString("type").trim().lowercase()
                val comment = obj.optString("comment").trim()
                when (type) {
                    "shell" -> {
                        val cmd = obj.optString("command").trim()
                        if (cmd.isNotEmpty()) steps += AgentStep(AgentStepKind.SHELL, cmd, comment)
                    }
                    "module_service", "service", "module" -> {
                        val id = obj.optString("module_id", obj.optString("moduleId")).trim()
                        if (id.isNotEmpty()) steps += AgentStep(AgentStepKind.MODULE_SERVICE, id, comment)
                    }
                }
                if (steps.size >= MAX_STEPS_HARD) break
            }
            Result.success(AgentPlan(goal, steps))
        } catch (e: Exception) {
            Result.failure(IllegalStateException("Could not parse the agent plan: ${e.message}"))
        }
    }

    suspend fun summarize(
        goal: String,
        executed: List<AgentExecutedAction>,
        localeTag: String,
        localeName: String,
    ): Result<String> {
        val active = AiProviderRepository.getActive()
            ?: return Result.failure(IllegalStateException("no_provider"))
        val baseUrl = active.baseUrl.takeIf { it.isNotBlank() }
            ?: return Result.failure(IllegalStateException("no_provider"))
        val model = active.model.takeIf { it.isNotBlank() }
            ?: AiExplainUtil.resolveModel(baseUrl).takeIf { it.isNotBlank() }
            ?: return Result.failure(IllegalStateException("empty_model"))
        val key = AiProviderRepository.getKey(active.id)
        if (key.isBlank()) return Result.failure(IllegalStateException("empty_key"))

        val transcript = buildString {
            append("Goal: ").append(goal).append("\n\nExecuted actions:\n")
            executed.forEachIndexed { index, action ->
                append(index + 1).append(". ")
                when (action.step.kind) {
                    AgentStepKind.SHELL -> append("shell: ").append(action.step.target)
                    AgentStepKind.MODULE_SERVICE -> append("module service: ").append(action.step.target)
                }
                append(" [").append(action.status.name.lowercase()).append("]\n")
                if (action.output.isNotBlank()) {
                    append(action.output.take(2000).trim()).append("\n")
                }
                append("\n")
            }
        }
        return AiClient.chatCompletion(
            baseUrl = baseUrl,
            apiKey = key,
            model = model,
            systemPrompt = "You are the Comput agent inside the Shevery Android app. " +
                "Summarize what was done for the user. Write the ENTIRE answer in this " +
                "language: $localeName (locale code: $localeTag). Be concise: verdict " +
                "first, then key findings. Do not invent results that are not in the transcript.",
            userPrompt = transcript,
        )
    }
}
