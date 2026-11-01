package moe.shizuku.manager.module.update

import android.content.Context
import android.net.Uri
import android.util.Log
import moe.shizuku.manager.module.AdbModuleManager
import moe.shizuku.manager.module.ModuleSettings
import moe.shizuku.manager.module.discovery.ContentItem
import moe.shizuku.manager.module.discovery.GitHubRepo
import moe.shizuku.manager.module.discovery.RateLimitTracker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import moe.shizuku.manager.module.catalog.TokenStore
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.util.Locale
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
        subPath: String? = null,
        onLog: (String) -> Unit = {},
        onProgress: (Float?) -> Unit = {}
    ): Result<moe.shizuku.manager.module.AdbModule> = withContext(Dispatchers.IO) {
        try {
            cleanupOldZips(context)

            val installMode = ModuleSettings.getInstallMode()
            val githubPat = TokenStore.getToken(context)
            Log.d(TAG, "Installing $moduleId mode=$installMode token=${if (githubPat.isNullOrBlank()) "null" else "set"}")
            onLog("- Repository: $owner/$repo${if (!subPath.isNullOrBlank()) " ($subPath)" else ""}")
            onLog("- Install mode: ${if (installMode == ModuleSettings.InstallMode.SOURCES) "Build from sources" else "Release ZIP"}")
            onProgress(0.02f)

            val zipUri = when (installMode) {
                ModuleSettings.InstallMode.SOURCES -> {
                    buildFromSources(context, moduleId, owner, repo, subPath, githubPat, onLog, onProgress)
                }
                ModuleSettings.InstallMode.RELEASE -> {
                    downloadRelease(context, moduleId, owner, repo, githubPat, onLog, onProgress)
                        ?: run {
                            onLog("- Release ZIP unavailable, falling back to building from sources...")
                            buildFromSources(context, moduleId, owner, repo, subPath, githubPat, onLog, onProgress)
                        }
                }
            }

            if (zipUri == null) {
                return@withContext Result.failure(Exception("Failed to prepare module ZIP"))
            }

            val module = AdbModuleManager.install(context, zipUri, onLog = onLog, onProgress = onProgress)
            Result.success(module)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.e(TAG, "Install failed for $moduleId", e)
            onLog("! Error: ${e.message ?: e.javaClass.simpleName}")
            Result.failure(e)
        }
    }

    private suspend fun buildFromSources(
        context: Context,
        moduleId: String,
        owner: String,
        repo: String,
        subPath: String?,
        githubPat: String?,
        onLog: (String) -> Unit,
        onProgress: (Float?) -> Unit
    ): Uri? {
        val repoUrl = "https://api.github.com/repos/$owner/$repo"
        onLog("- Querying GitHub repository metadata ($owner/$repo)...")
        val repoRequest = buildGitHubRequest(repoUrl, githubPat)
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
        onLog("- Using branch: $defaultBranch")
        onProgress(0.06f)

        val rootPath = subPath?.trim('/')?.takeIf { it.isNotEmpty() } ?: ""
        val url = contentsUrl(owner, repo, rootPath, defaultBranch)
        onLog("- Listing directory contents: /${rootPath}")

        val request = buildGitHubRequest(url, githubPat)
        val response = client.newCall(request).execute()

        response.use { resp ->
            rateLimit.update(resp.headers)

            if (!resp.isSuccessful) {
                Log.w(TAG, "Contents API failed: ${resp.code} for $url")
                onLog("! GitHub Contents API returned HTTP ${resp.code}")
                return null
            }

            val body = resp.body?.string() ?: return null
            val items = json.decodeFromString<List<ContentItem>>(body)

            val allFiles = mutableListOf<ContentItem>()
            val dirsToFetch = ArrayDeque<String>()
            val visitedDirs = mutableSetOf(rootPath)

            for (item in items) {
                if (item.type == "file") {
                    allFiles.add(item)
                } else if (item.type == "dir") {
                    dirsToFetch.add(item.path)
                }
            }

            while (dirsToFetch.isNotEmpty()) {
                val dirPath = dirsToFetch.removeFirst()
                if (!visitedDirs.add(dirPath)) continue

                onLog("- Scanning subdirectory: /$dirPath")
                val dirUrl = contentsUrl(owner, repo, dirPath, defaultBranch)
                val dirRequest = buildGitHubRequest(dirUrl, githubPat)
                val dirResponse = client.newCall(dirRequest).execute()

                dirResponse.use { dirResp ->
                    rateLimit.update(dirResp.headers)
                    if (!dirResp.isSuccessful) {
                        throw IOException("Failed to list module directory $dirPath: HTTP ${dirResp.code}")
                    }
                    val dirBody = dirResp.body?.string()
                        ?: throw IOException("Empty response for module directory $dirPath")
                    val dirItems = json.decodeFromString<List<ContentItem>>(dirBody)
                    for (dirItem in dirItems) {
                        if (dirItem.type == "file") {
                            allFiles.add(dirItem)
                        } else if (dirItem.type == "dir") {
                            dirsToFetch.add(dirItem.path)
                        }
                    }
                }
            }

            if (allFiles.isEmpty()) {
                Log.w(TAG, "No files found in $url")
                onLog("! No files found in repository path")
                return null
            }

            onLog("- Discovered ${allFiles.size} files in repository")
            onProgress(0.10f)

            val zipFile = sourceZipBuilder.buildZip(
                context = context,
                moduleId = moduleId,
                files = allFiles,
                githubPat = githubPat,
                subPath = subPath,
                onLog = onLog,
                onProgress = onProgress
            ) ?: return null

            return Uri.fromFile(zipFile)
        }
    }

    private fun contentsUrl(owner: String, repo: String, path: String, ref: String): String {
        val encodedPath = path.trim('/').split('/')
            .filter { it.isNotEmpty() }
            .joinToString("/") { segment ->
                URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
            }
        val base = "https://api.github.com/repos/$owner/$repo/contents" +
                (if (encodedPath.isEmpty()) "" else "/$encodedPath")
        return "$base?ref=${URLEncoder.encode(ref, "UTF-8")}"
    }

    private suspend fun downloadRelease(
        context: Context,
        moduleId: String,
        owner: String,
        repo: String,
        githubPat: String?,
        onLog: (String) -> Unit,
        onProgress: (Float?) -> Unit
    ): Uri? {
        val url = "https://api.github.com/repos/$owner/$repo/releases/latest"
        onLog("- Checking latest GitHub release for $owner/$repo...")

        val request = buildGitHubRequest(url, githubPat)
        val response = client.newCall(request).execute()

        val release = response.use { resp ->
            rateLimit.update(resp.headers)

            if (!resp.isSuccessful) {
                Log.w(TAG, "Releases API failed: ${resp.code}")
                onLog("- Latest release endpoint returned HTTP ${resp.code}")
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
            onLog("- Release ${release.tagName} has no .zip asset attached")
            return null
        }

        onLog("- Found release ${release.tagName}: ${zipAsset.name} (${formatBytes(zipAsset.size)})")
        val downloadUrl = zipAsset.browserDownloadUrl
        val cacheDir = File(context.cacheDir, "module_zips").apply { mkdirs() }
        val zipFile = File(cacheDir, "$moduleId-release.zip")

        return try {
            onLog("- Downloading ${zipAsset.name}...")
            onProgress(0.10f)
            val downloadRequest = buildGitHubRequest(downloadUrl, githubPat)
            val downloadResponse = client.newCall(downloadRequest).execute()

            downloadResponse.use { dlResp ->
                if (!dlResp.isSuccessful) {
                    Log.w(TAG, "ZIP download failed: ${dlResp.code}")
                    onLog("! Asset download failed with HTTP ${dlResp.code}")
                    return null
                }

                val body = dlResp.body ?: return null
                val totalBytes = body.contentLength().takeIf { it > 0L } ?: zipAsset.size
                if (totalBytes > MAX_MODULE_ZIP_BYTES) {
                    onLog("! Release ZIP is too large (${formatBytes(totalBytes)})")
                    return null
                }
                body.byteStream().use { input ->
                    zipFile.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var downloaded = 0L
                        var nextMilestone = 20
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            if (downloaded > MAX_MODULE_ZIP_BYTES) {
                                throw IllegalStateException("Release ZIP exceeded the size limit")
                            }
                            if (totalBytes > 0L) {
                                val pct = ((downloaded * 100L) / totalBytes).toInt().coerceIn(0, 100)
                                onProgress(0.10f + (pct / 100f) * 0.65f)
                                if (pct >= nextMilestone && nextMilestone <= 100) {
                                    onLog("  ↳ $pct% (${formatBytes(downloaded)} / ${formatBytes(totalBytes)})")
                                    nextMilestone += 20
                                }
                            }
                        }
                    }
                }
                onLog("- Downloaded ${zipFile.name} (${formatBytes(zipFile.length())})")
                onProgress(0.78f)

                Uri.fromFile(zipFile)
            }
        } catch (e: Exception) {
            Log.e(TAG, "ZIP download error", e)
            onLog("! Download error: ${e.message ?: e.javaClass.simpleName}")
            zipFile.delete()
            null
        }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024L) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
        return String.format(Locale.US, "%.2f MB", kb / 1024.0)
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



    companion object {
        private const val TAG = "ModuleInstaller"
        private const val MAX_MODULE_ZIP_BYTES = 64L * 1024L * 1024L

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

internal fun buildGitHubRequest(url: String, githubPat: String?): Request {
    val builder = Request.Builder()
        .url(url)
        .header("Accept", "application/vnd.github+json")
        .header("X-GitHub-Api-Version", "2022-11-28")

    if (!githubPat.isNullOrBlank()) {
        builder.header("Authorization", "Bearer $githubPat")
    }

    return builder.build()
}
