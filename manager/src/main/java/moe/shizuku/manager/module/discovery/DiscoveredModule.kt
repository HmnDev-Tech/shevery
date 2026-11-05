package moe.shizuku.manager.module.discovery

import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Serializable
data class DiscoveredModule(
    val repoFullName: String,
    val repoUrl: String,
    val moduleId: String,
    val moduleName: String,
    val version: String?,
    val versionCode: Long?,
    val author: String?,
    val description: String?,
    val stars: Int,
    val repoDescription: String?,
    val ownerAvatar: String,
    val subPath: String?,
    val lastChecked: Long,
    val isValid: Boolean,
    val isOfficial: Boolean = false,
    val createdAt: String? = null,
    val pushedAt: String? = null,
    val updatedAt: String? = null
) {
    val createdEpochMillis: Long
        get() = parseIsoTimestamp(createdAt).takeIf { it > 0L }
            ?: parseIsoTimestamp(pushedAt).takeIf { it > 0L }
            ?: parseIsoTimestamp(updatedAt)

    val updatedEpochMillis: Long
        get() = parseIsoTimestamp(pushedAt).takeIf { it > 0L }
            ?: parseIsoTimestamp(updatedAt).takeIf { it > 0L }
            ?: parseIsoTimestamp(createdAt)

    fun isOutdated(): Boolean {
        val updated = updatedEpochMillis
        if (updated <= 0L) return false
        val diff = System.currentTimeMillis() - updated
        val twentyDaysMs = 20L * 24 * 60 * 60 * 1000L
        return diff >= twentyDaysMs
    }

    fun isLongNonUpdating(): Boolean = isOutdated()

    companion object {
        fun parseIsoTimestamp(isoString: String?): Long {
            if (isoString.isNullOrBlank()) return 0L
            val trimmed = isoString.trim()
            val patterns = arrayOf(
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd'T'HH:mm:ssZ"
            )
            for (pattern in patterns) {
                try {
                    val sdf = SimpleDateFormat(pattern, Locale.US)
                    if (pattern.endsWith("'Z'")) {
                        sdf.timeZone = TimeZone.getTimeZone("UTC")
                    }
                    val parsed = sdf.parse(trimmed)?.time
                    if (parsed != null && parsed > 0L) return parsed
                } catch (_: Exception) {
                }
            }
            return 0L
        }
    }
}
