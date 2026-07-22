package moe.shizuku.manager.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.shizuku.manager.module.ModuleSettings
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object AiExplainUtil {

    suspend fun explainFailure(
        contextStr: String,
        inputDetail: String,
        outputLog: String,
        apiKey: String
    ): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext "Google AI Studio API Key is empty! Please configure it in Shevery Settings."
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
                    "An error or failure occurred in the application context: $contextStr.\n" +
                    "Input / Action details:\n$inputDetail\n\n" +
                    "Output / Error Log:\n$outputLog\n\n" +
                    "Explain this failure in a clear, concise, and helpful developer-focused way, and suggest how to resolve it."

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

    suspend fun generateCommand(
        prompt: String,
        apiKey: String
    ): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext "Error: API Key is empty!"
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

            val requestPrompt = "You are a shell command assistant. Generate a shell command based on the following user prompt.\n" +
                    "CRITICAL: Return ONLY the raw shell command, without any markdown formatting (do not wrap in ``` or `), explanations, or trailing text. The output should be directly executable in a shell.\n\n" +
                    "Prompt: $prompt"

            val requestBody = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", requestPrompt)
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
                "Error: Gemini API returned error code $responseCode: $errText"
            }
        } catch (e: Exception) {
            "Error: Failed to reach Gemini API: ${e.message ?: "Connection error."}"
        }
    }
}
