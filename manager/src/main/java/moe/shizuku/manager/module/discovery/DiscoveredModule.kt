package moe.shizuku.manager.module.discovery

import kotlinx.serialization.Serializable

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
    val isOfficial: Boolean = false
)
