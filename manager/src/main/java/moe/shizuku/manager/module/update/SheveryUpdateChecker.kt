package moe.shizuku.manager.module.update

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import moe.shizuku.manager.BuildConfig
import moe.shizuku.manager.module.ModuleSettings
import moe.shizuku.manager.module.catalog.TokenStore
import moe.shizuku.manager.module.discovery.RateLimitTracker
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

@Serializable
data class SheveryAppUpdateResult(
    val hasUpdate: Boolean,
    val currentVersion: String,
    val latestVersion: String?,
    val releaseTitle: String?,
    val releaseNotes: String?,
    val downloadUrl: String?,
    val htmlUrl: String?,
    val isPreRelease: Boolean,
    val publishedAt: String?,
    val error: String? = null
)

class SheveryUpdateChecker private constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val rateLimit = RateLimitTracker()

    suspend fun checkAppUpdate(
        context: Context,
        forceChannel: ModuleSettings.AppUpdateChannel? = null
    ): SheveryAppUpdateResult = withContext(Dispatchers.IO) {
        val currentVersion = BuildConfig.VERSION_NAME
        val channel = forceChannel ?: ModuleSettings.getAppUpdateChannel()
        val githubPat = TokenStore.getToken(context)

        try {
            val url = "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases"
            val request = buildRequest(url, githubPat)
            val response = client.newCall(request).execute()

            response.use { resp ->
                rateLimit.update(resp.headers)
                if (!resp.isSuccessful) {
                    return@withContext SheveryAppUpdateResult(
                        hasUpdate = false,
                        currentVersion = currentVersion,
                        latestVersion = null,
                        releaseTitle = null,
                        releaseNotes = null,
                        downloadUrl = null,
                        htmlUrl = null,
                        isPreRelease = false,
                        publishedAt = null,
                        error = "HTTP ${resp.code}: ${resp.message}"
                    )
                }

                val body = resp.body?.string() ?: return@withContext SheveryAppUpdateResult(
                    hasUpdate = false,
                    currentVersion = currentVersion,
                    latestVersion = null,
                    releaseTitle = null,
                    releaseNotes = null,
                    downloadUrl = null,
                    htmlUrl = null,
                    isPreRelease = false,
                    publishedAt = null,
                    error = "Empty response"
                )

                val releases = json.decodeFromString<List<GitHubRelease>>(body)
                // GitHub orders /releases by created_at, not published_at, so a
                // release drafted early but published late (e.g. r37: drafted
                // Aug 21, published Sep 13) lands below older stables and is
                // skipped by firstOrNull. Pick the newest by published_at instead.
                val targetRelease = when (channel) {
                    ModuleSettings.AppUpdateChannel.STABLE -> releases.filter { !it.prerelease && !it.draft }
                        .maxByOrNull { it.publishedAt ?: "" }
                    ModuleSettings.AppUpdateChannel.BETA_PRE_RELEASE -> releases.filter { !it.draft }
                        .maxByOrNull { it.publishedAt ?: "" }
                } ?: releases.filter { !it.draft }.maxByOrNull { it.publishedAt ?: "" }

                if (targetRelease == null) {
                    return@withContext SheveryAppUpdateResult(
                        hasUpdate = false,
                        currentVersion = currentVersion,
                        latestVersion = null,
                        releaseTitle = null,
                        releaseNotes = null,
                        downloadUrl = null,
                        htmlUrl = null,
                        isPreRelease = false,
                        publishedAt = null
                    )
                }

                val hasUpdate = isReleaseNewer(targetRelease.tagName, currentVersion, BuildConfig.VERSION_CODE)
                val apkAsset = targetRelease.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                val downloadUrl = apkAsset?.browserDownloadUrl ?: targetRelease.htmlUrl

                ModuleSettings.setAppLastCheckTime(System.currentTimeMillis())

                SheveryAppUpdateResult(
                    hasUpdate = hasUpdate,
                    currentVersion = currentVersion,
                    latestVersion = targetRelease.tagName,
                    releaseTitle = targetRelease.name ?: targetRelease.tagName,
                    releaseNotes = targetRelease.body,
                    downloadUrl = downloadUrl,
                    htmlUrl = targetRelease.htmlUrl,
                    isPreRelease = targetRelease.prerelease,
                    publishedAt = targetRelease.publishedAt
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check Shevery update", e)
            SheveryAppUpdateResult(
                hasUpdate = false,
                currentVersion = currentVersion,
                latestVersion = null,
                releaseTitle = null,
                releaseNotes = null,
                downloadUrl = null,
                htmlUrl = null,
                isPreRelease = false,
                publishedAt = null,
                error = e.message ?: "Unknown error"
            )
        }
    }

    private fun isReleaseNewer(releaseTag: String, currentVersion: String, currentVersionCode: Int): Boolean {
        // Extract revision number (e.g., r32 in Shevery-r32-beta1 or v13.9.0-r31)
        val releaseRevision = extractRevisionNumber(releaseTag)
        val currentRevision = extractRevisionNumber(currentVersion) ?: currentVersionCode

        if (releaseRevision != null && currentRevision != null) {
            if (releaseRevision > currentRevision) return true
            if (releaseRevision < currentRevision) return false
        }

        // Compare semantic version parts
        val cleanRelease = releaseTag.removePrefix("v").removePrefix("Shevery-").substringBefore("-")
        val cleanCurrent = currentVersion.removePrefix("v").substringBefore(".r").substringBefore("-")

        val relParts = cleanRelease.split(".").mapNotNull { it.toIntOrNull() }
        val curParts = cleanCurrent.split(".").mapNotNull { it.toIntOrNull() }

        for (i in 0 until maxOf(relParts.size, curParts.size)) {
            val rp = relParts.getOrElse(i) { 0 }
            val cp = curParts.getOrElse(i) { 0 }
            if (rp > cp) return true
            if (rp < cp) return false
        }

        return false
    }

    private fun extractRevisionNumber(versionString: String): Int? {
        val rMatch = Regex("(?i)r(\\d+)").find(versionString)
        return rMatch?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun buildRequest(url: String, githubPat: String?): Request {
        val builder = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")

        if (!githubPat.isNullOrBlank()) {
            builder.header("Authorization", "Bearer $githubPat")
        }

        return builder.build()
    }

    companion object {
        private const val TAG = "SheveryUpdateChecker"
        private const val REPO_OWNER = "HmnDev-Tech"
        private const val REPO_NAME = "shevery"

        @Volatile
        private var instance: SheveryUpdateChecker? = null

        fun getInstance(): SheveryUpdateChecker {
            return instance ?: synchronized(this) {
                instance ?: SheveryUpdateChecker().also {
                    instance = it
                }
            }
        }

        /**
         * True when [releaseTag] (e.g. "r35") is newer than the installed build.
         * Used to suppress stale stored pendings: a banner saved before an update
         * must not keep advertising the old version after a newer build is installed.
         */
        fun isTagNewerThanInstalled(releaseTag: String?): Boolean {
            if (releaseTag.isNullOrBlank()) return false
            return getInstance().isReleaseNewer(
                releaseTag,
                BuildConfig.VERSION_NAME,
                BuildConfig.VERSION_CODE
            )
        }
    }
}
