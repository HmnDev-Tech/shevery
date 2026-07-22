package moe.shizuku.manager.module.update

import kotlinx.serialization.Serializable

@Serializable
data class UpdateResult(
    val moduleId: String,
    val hasUpdate: Boolean,
    val currentVersion: String?,
    val currentVersionCode: Long?,
    val latestVersion: String?,
    val latestVersionCode: Long?,
    val downloadUrl: String?,
    val changelog: String?
)
