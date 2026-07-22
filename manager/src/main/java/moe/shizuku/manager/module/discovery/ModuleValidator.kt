package moe.shizuku.manager.module.discovery

import android.util.Log
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class ModuleValidator(private val client: OkHttpClient, private val rateLimit: RateLimitTracker) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun validateRepo(owner: String, repo: String, authHeader: String? = null): List<DiscoveredModule> {
        val now = System.currentTimeMillis()

        val rootModule = checkModuleProp(owner, repo, null, authHeader)
        if (rootModule != null) {
            return listOf(rootModule)
        }

        return scanSubdirs(owner, repo, now, authHeader)
    }

    private fun checkModuleProp(owner: String, repo: String, subPath: String?, authHeader: String? = null): DiscoveredModule? {
        val path = if (subPath != null) "$subPath/module.prop" else "module.prop"
        val url = "https://api.github.com/repos/$owner/$repo/contents/$path"

        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .apply { authHeader?.let { header("Authorization", it) } }
            .build()

        val response = try {
            client.newCall(request).execute()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch $url", e)
            return null
        }

        response.use { resp ->
            rateLimit.update(resp.headers)
            if (!resp.isSuccessful) return null

            val body = resp.body?.string() ?: return null
            val item = try {
                json.decodeFromString<ContentItem>(body)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse content item", e)
                return null
            }

            if (item.type != "file" || item.downloadUrl == null) return null

            val propContent = fetchRawContent(item.downloadUrl, authHeader) ?: return null
            return parseModuleProp(propContent, owner, repo, subPath)
        }
    }

    private fun scanSubdirs(owner: String, repo: String, now: Long, authHeader: String? = null): List<DiscoveredModule> {
        val url = "https://api.github.com/repos/$owner/$repo/contents/"

        val builder = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
        authHeader?.let { builder.header("Authorization", it) }
        val request = builder.build()

        val response = try {
            client.newCall(request).execute()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to list root contents for $owner/$repo", e)
            return emptyList()
        }

        response.use { resp ->
            rateLimit.update(resp.headers)
            if (!resp.isSuccessful) return emptyList()

            val body = resp.body?.string() ?: return emptyList()
            val items = try {
                json.decodeFromString<List<ContentItem>>(body)
            } catch (e: Exception) {
                return emptyList()
            }

            val dirs = items.filter { it.type == "dir" }
            val modules = mutableListOf<DiscoveredModule>()

            for (dir in dirs) {
                val module = checkModuleProp(owner, repo, dir.name, authHeader)
                if (module != null) {
                    modules.add(module)
                } else {
                    val subDirModules = scanSecondLevel(owner, repo, dir.name, authHeader)
                    modules.addAll(subDirModules)
                }
            }

            return modules
        }
    }

    private fun scanSecondLevel(owner: String, repo: String, parentDir: String, authHeader: String? = null): List<DiscoveredModule> {
        val url = "https://api.github.com/repos/$owner/$repo/contents/$parentDir"

        val builder = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
        authHeader?.let { builder.header("Authorization", it) }
        val request = builder.build()

        val response = try {
            client.newCall(request).execute()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to list contents for $owner/$repo/$parentDir", e)
            return emptyList()
        }

        response.use { resp ->
            rateLimit.update(resp.headers)
            if (!resp.isSuccessful) return emptyList()

            val body = resp.body?.string() ?: return emptyList()
            val items = try {
                json.decodeFromString<List<ContentItem>>(body)
            } catch (e: Exception) {
                return emptyList()
            }

            val dirs = items.filter { it.type == "dir" }
            val modules = mutableListOf<DiscoveredModule>()

            for (dir in dirs) {
                val subPath = "$parentDir/${dir.name}"
                val module = checkModuleProp(owner, repo, subPath, authHeader)
                if (module != null) {
                    modules.add(module)
                }
            }

            return modules
        }
    }

    private fun fetchRawContent(url: String, authHeader: String? = null): String? {
        val builder = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
        authHeader?.let { builder.header("Authorization", it) }
        val request = builder.build()

        val response = try {
            client.newCall(request).execute()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch raw content", e)
            return null
        }

        response.use { resp ->
            rateLimit.update(resp.headers)
            if (!resp.isSuccessful) return null
            return resp.body?.string()
        }
    }

    private fun parseModuleProp(
        raw: String,
        owner: String,
        repo: String,
        subPath: String?
    ): DiscoveredModule? {
        val props = raw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") && it.contains("=") }
            .associate {
                val index = it.indexOf('=')
                it.substring(0, index).trim() to it.substring(index + 1).trim()
            }

        val id = props["id"]?.takeIf { it.isNotBlank() } ?: return null
        if (!ID_REGEX.matches(id)) return null
        val name = props["name"]?.takeIf { it.isNotBlank() } ?: id

        return DiscoveredModule(
            repoFullName = "$owner/$repo",
            repoUrl = "https://github.com/$owner/$repo",
            moduleId = id,
            moduleName = name,
            version = props["version"]?.takeIf { it.isNotBlank() },
            versionCode = props["versionCode"]?.toLongOrNull(),
            author = props["author"]?.takeIf { it.isNotBlank() },
            description = props["description"]?.takeIf { it.isNotBlank() },
            stars = 0,
            repoDescription = null,
            ownerAvatar = "",
            subPath = subPath,
            lastChecked = System.currentTimeMillis(),
            isValid = true
        )
    }

    companion object {
        private const val TAG = "ModuleValidator"
        private val ID_REGEX = Regex("[A-Za-z][A-Za-z0-9._-]{1,63}")

        fun create(): ModuleValidator {
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
            return ModuleValidator(client, RateLimitTracker())
        }
    }
}
