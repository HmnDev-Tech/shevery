package moe.shizuku.manager.commandium

import moe.shizuku.manager.R

/**
 * Built-in provider presets shown in the provider picker.
 * Moved byte-identical out of AiProviderDialog so the future AI manager
 * screen and the dialog share one source of truth.
 */
data class AiProviderPreset(
    val name: String,
    val baseUrl: String,
    val nameRes: Int? = null,
)

val aiProviderPresets = listOf(
    AiProviderPreset("OpenRouter", "https://openrouter.ai/api/v1"),
    AiProviderPreset("OpenAI", "https://api.openai.com/v1"),
    AiProviderPreset("DeepSeek", "https://api.deepseek.com/v1"),
    AiProviderPreset("Groq", "https://api.groq.com/openai/v1"),
    AiProviderPreset("Mistral", "https://api.mistral.ai/v1"),
    AiProviderPreset("Google Gemini", "https://generativelanguage.googleapis.com/v1beta/openai"),
    AiProviderPreset("Together AI", "https://api.together.xyz/v1"),
    AiProviderPreset("NVIDIA NIM", "https://integrate.api.nvidia.com/v1"),
    AiProviderPreset("Cerebras", "https://api.cerebras.ai/v1"),
    AiProviderPreset("xAI", "https://api.x.ai/v1"),
    AiProviderPreset("DeepInfra", "https://api.deepinfra.com/v1/openai"),
    AiProviderPreset("", "", R.string.comput_ai_preset_custom),
)

fun urlIsValid(url: String): Boolean =
    url.startsWith("http://") || url.startsWith("https://")

fun matchPresetName(currentName: String, currentBaseUrl: String): String =
    when {
        currentName.isBlank() -> "OpenRouter"
        else ->
            aiProviderPresets.firstOrNull { it.name == currentName && it.baseUrl == currentBaseUrl.trim() }?.name ?: "Custom"
    }