package moe.shizuku.manager.module.discovery

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GitHubRepo(
    val id: Long,
    @SerialName("full_name") val fullName: String,
    @SerialName("html_url") val htmlUrl: String,
    val description: String? = null,
    @SerialName("stargazers_count") val stargazersCount: Int = 0,
    @SerialName("forks_count") val forksCount: Int = 0,
    val language: String? = null,
    val archived: Boolean = false,
    val fork: Boolean = false,
    @SerialName("default_branch") val defaultBranch: String = "main",
    @SerialName("pushed_at") val pushedAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val owner: Owner
) {
    @Serializable
    data class Owner(
        val login: String,
        @SerialName("avatar_url") val avatarUrl: String = ""
    )
}

@Serializable
data class SearchResponse(
    @SerialName("total_count") val totalCount: Int = 0,
    @SerialName("incomplete_results") val incompleteResults: Boolean = false,
    val items: List<GitHubRepo> = emptyList()
)

@Serializable
data class ContentItem(
    val name: String,
    val path: String,
    val type: String,
    @SerialName("download_url") val downloadUrl: String? = null
)

@Serializable
data class GitHubCommitItem(
    val commit: CommitDetail? = null
) {
    @Serializable
    data class CommitDetail(
        val committer: CommitPerson? = null,
        val author: CommitPerson? = null
    )

    @Serializable
    data class CommitPerson(
        val date: String? = null
    )
}
