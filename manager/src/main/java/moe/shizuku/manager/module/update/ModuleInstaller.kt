package moe.shizuku.manager.module.update

import android.content.Context
import android.net.Uri
import android.util.Log
import moe.shizuku.manager.module.AdbModuleManager
import moe.shizuku.manager.module.ModuleSettings
import moe.shizuku.manager.module.discovery.ContentItem
import moe.shizuku.manager.module.discovery.GitHubRepo
import moe.shizuku.manager.module.discovery.RateLimitTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import moe.shizuku.manager.module.catalog.TokenStore
import java.io.File
import java.util.concurrent.TimeUnit

class ModuleInstaller private constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val rateLimit = RateLimitTracker()
    private val sourceZipBuilder = SourceZipBuilder.getInstance()

    suspend fun installModule(
        context: Context,
        moduleId: String,
        owner: String,
        repo: String,
        subPath: String? = null
    ): Result<moe.shizuku.manager.module.AdbModule> = withContext(Dispatchers.IO) {
        try {
            cleanupOldZips(context)

            val installMode = ModuleSettings.getInstallMode()
            val githubPat = TokenStore.getToken(context)
            Log.d(TAG, "Installing $moduleId mode=$installMode token=${if (githubPat.isNullOrBlank()) "null" else "set"}")

            val zipUri = when (installMode) {
                ModuleSettings.InstallMode.SOURCES -> {
                    buildFromSources(context, moduleId, owner, repo, subPath, githubPat)
                }
                ModuleSettings.InstallMode.RELEASE -> {
                    downloadRelease(context, moduleId, owner, repo, githubPat)
                }
            }

            if (zipUri == null) {
                return@withContext Result.failure(Exception("Failed to prepare module ZIP"))
            }

            val module = AdbModuleManager.install(context, zipUri)
            Result.success(module)
        } catch (e: Exception) {
            Log.e(TAG, "Install failed for $moduleId", e)
            Result.failure(e)
        }
    }

    private suspend fun buildFromSources(
        context: Context,
        moduleId: String,
        owner: String,
        repo: String,
        subPath: String?,
        githubPat: String?
    ): Uri? {
        val repoUrl = "https://api.github.com/repos/$owner/$repo"
        val repoRequest = buildRequest(repoUrl, githubPat)
        val repoResponse = client.newCall(repoRequest).execute()

        val defaultBranch = repoResponse.use { resp ->
            rateLimit.update(resp.headers)
            if (!resp.isSuccessful) {
                Log.w(TAG, "Repo API failed: ${resp.code} for $repoUrl")
                "main"
            } else {
                val body = resp.body?.string() ?: "main"
                json.decodeFromString<GitHubRepo>(body).defaultBranch
            }
        }

        val path = subPath?.trim('/')?.let { "/$it" } ?: ""
        val url = "https://api.github.com/repos/$owner/$repo/contents$path?ref=$defaultBranch"

        val request = buildRequest(url, githubPat)
        val response = client.newCall(request).execute()

        response.use { resp ->
            rateLimit.update(resp.headers)

            if (!resp.isSuccessful) {
                Log.w(TAG, "Contents API failed: ${resp.code} for $url")
                return null
            }

            val body = resp.body?.string() ?: return null
            val items = json.decodeFromString<List<ContentItem>>(body)

            val allFiles = mutableListOf<ContentItem>()
            val dirsToFetch = ArrayDeque<String>()

            for (item in items) {
                if (item.type == "file") {
                    allFiles.add(item)
                } else if (item.type == "dir") {
                    dirsToFetch.add(item.name)
                }
            }

            while (dirsToFetch.isNotEmpty()) {
                val dirName = dirsToFetch.removeFirst()
                val parentPath = path.trim('/').let { if (it.isNotEmpty()) "$it/$dirName" else dirName }
                val dirUrl = "https://api.github.com/repos/$owner/$repo/contents/$parentPath?ref=$defaultBranch"
                val dirRequest = buildRequest(dirUrl, githubPat)
                val dirResponse = client.newCall(dirRequest).execute()

                dirResponse.use { dirResp ->
                    rateLimit.update(dirResp.headers)
                    if (!dirResp.isSuccessful) {
                        Log.w(TAG, "Contents API failed for subdir: ${dirResp.code} for $dirUrl")
                        return@use
                    }
                    val dirBody = dirResp.body?.string() ?: return@use
                    val dirItems = json.decodeFromString<List<ContentItem>>(dirBody)
                    for (dirItem in dirItems) {
                        if (dirItem.type == "file") {
                            allFiles.add(dirItem)
                        } else if (dirItem.type == "dir") {
                            dirsToFetch.add(dirItem.name)
                        }
                    }
                }
            }

            if (allFiles.isEmpty()) {
                Log.w(TAG, "No files found in $url")
                return null
            }

        val zipFile = sourceZipBuilder.buildZip(context, moduleId, allFiles, githubPat, subPath)
            ?: return null

            return Uri.fromFile(zipFile)
        }
    }

    private suspend fun downloadRelease(
        context: Context,
        moduleId: String,
        owner: String,
        repo: String,
        githubPat: String?
    ): Uri? {
        val url = "https://api.github.com/repos/$owner/$repo/releases/latest"

        val request = buildRequest(url, githubPat)
        val response = client.newCall(request).execute()

        val release = response.use { resp ->
            rateLimit.update(resp.headers)

            if (!resp.isSuccessful) {
                Log.w(TAG, "Releases API failed: ${resp.code}")
                return null
            }

            val body = resp.body?.string() ?: return null
            json.decodeFromString<GitHubRelease>(body)
        }

        val zipAsset = release.assets.firstOrNull { asset ->
            asset.name.endsWith(".zip", ignoreCase = true) &&
                    (asset.name.contains(moduleId, ignoreCase = true) ||
                            asset.name.contains(repo, ignoreCase = true))
        } ?: release.assets.firstOrNull { it.name.endsWith(".zip", ignoreCase = true) }

        if (zipAsset == null) {
            Log.w(TAG, "No ZIP asset found in release")
            return null
        }

        val downloadUrl = zipAsset.browserDownloadUrl
        val cacheDir = File(context.cacheDir, "module_zips").apply { mkdirs() }
        val zipFile = File(cacheDir, "$moduleId-release.zip")

        return try {
            val downloadRequest = buildRequest(downloadUrl, githubPat)
            val downloadResponse = client.newCall(downloadRequest).execute()

            downloadResponse.use { dlResp ->
                if (!dlResp.isSuccessful) {
                    Log.w(TAG, "ZIP download failed: ${dlResp.code}")
                    return null
                }

                dlResp.body?.byteStream()?.use { input ->
                    zipFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                Uri.fromFile(zipFile)
            }
        } catch (e: Exception) {
            Log.e(TAG, "ZIP download error", e)
            zipFile.delete()
            null
        }
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

    private fun cleanupOldZips(context: Context) {
        val cacheDir = File(context.cacheDir, "module_zips")
        if (!cacheDir.isDirectory) return
        val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        cacheDir.listFiles()?.forEach { file ->
            if (file.isFile && file.lastModified() < cutoff) {
                file.delete()
            }
        }
    }

    fun getRateLimit(): RateLimitTracker = rateLimit

    companion object {
        private const val TAG = "ModuleInstaller"

        @Volatile
        private var instance: ModuleInstaller? = null

        fun getInstance(): ModuleInstaller {
            return instance ?: synchronized(this) {
                instance ?: ModuleInstaller().also {
                    instance = it
                }
            }
        }
    }
}
