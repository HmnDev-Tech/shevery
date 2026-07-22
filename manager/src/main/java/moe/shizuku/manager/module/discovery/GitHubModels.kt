package moe.shizuku.manager.module.discovery

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GitHubRepo(
    val id: Long,
    @SerialName("full_name") val fullName: String,
    @SerialName("html_url") val htmlUrl: String,
    val description: String?,
    @SerialName("stargazers_count") val stargazersCount: Int,
    @SerialName("forks_count") val forksCount: Int,
    val language: String?,
    val topics: List<String> = emptyList(),
    val archived: Boolean = false,
    val fork: Boolean = false,
    @SerialName("default_branch") val defaultBranch: String = "main",
    @SerialName("pushed_at") val pushedAt: String,
    @SerialName("created_at") val createdAt: String,
    val owner: Owner
) {
    @Serializable
    data class Owner(
        val login: String,
        @SerialName("avatar_url") val avatarUrl: String
    )
}

@Serializable
data class SearchResponse(
    @SerialName("total_count") val totalCount: Int,
    @SerialName("incomplete_results") val incompleteResults: Boolean,
    val items: List<GitHubRepo>
)

@Serializable
data class ContentItem(
    val name: String,
    val path: String,
    val type: String,
    @SerialName("download_url") val downloadUrl: String? = null
)
