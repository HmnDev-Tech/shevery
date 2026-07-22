package moe.shizuku.manager.module

import java.io.File

data class ModuleUpdateInfo(
    val newVersion: String,
    val newVersionCode: Long? = null,
    val zipUrl: String,
    val changelog: String? = null
)

data class AdbModule(
    val id: String,
    val name: String,
    val version: String?,
    val versionCode: Long?,
    val author: String?,
    val description: String?,
    val directory: File,
    val banner: File?,
    val webRoot: File?,
    val declaresShellBridge: Boolean,
    val actionScript: File?,
    val serviceScript: File?,
    val logsDir: File,
    val sizeBytes: Long,
    val enabled: Boolean,
    val url: String? = null,
    val updateJson: String? = null,
    val updateInfo: ModuleUpdateInfo? = null
) {
    val hasWebUi: Boolean
        get() = webRoot?.resolve("index.html")?.isFile == true

    val hasAction: Boolean
        get() = actionScript?.isFile == true

    val hasService: Boolean
        get() = serviceScript?.isFile == true

    val formattedSize: String
        get() {
            val kb = sizeBytes / 1024.0
            if (kb < 1024.0) return "${"%.1f".format(kb)} KB"
            return "${"%.1f".format(kb / 1024.0)} MB"
        }

    val lastActionLog: File
        get() = logsDir.resolve("action-last.log")

    val lastServiceLog: File
        get() = logsDir.resolve("service-last.log")
}

data class ModuleActionResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String
) {
    val combinedOutput: String
        get() = buildString {
            if (stdout.isNotBlank()) append(stdout.trim())
            if (stderr.isNotBlank()) {
                if (isNotEmpty()) append("\n\n")
                append(stderr.trim())
            }
            if (isBlank()) append("No output.")
        }
}
