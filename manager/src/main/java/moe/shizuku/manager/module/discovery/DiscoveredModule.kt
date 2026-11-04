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
    val pushedAt: String? = null
) {
    val createdEpochMillis: Long
        get() = parseIsoTimestamp(createdAt)

    val updatedEpochMillis: Long
        get() = parseIsoTimestamp(pushedAt)

    fun isLongNonUpdating(): Boolean {
        val updated = updatedEpochMillis
        if (updated <= 0L) return false
        val diff = System.currentTimeMillis() - updated
        val twentyDaysMs = 20L * 24 * 60 * 60 * 1000L
        return diff > twentyDaysMs
    }

    companion object {
        fun parseIsoTimestamp(isoString: String?): Long {
            if (isoString.isNullOrBlank()) return 0L
            return try {
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                sdf.parse(isoString)?.time ?: 0L
            } catch (_: Exception) {
                try {
                    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
                    sdf.parse(isoString)?.time ?: 0L
                } catch (_: Exception) {
                    0L
                }
            }
        }
    }
}
