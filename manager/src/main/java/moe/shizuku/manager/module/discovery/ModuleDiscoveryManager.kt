package moe.shizuku.manager.module.discovery

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import moe.shizuku.manager.module.catalog.TokenStore
import java.util.concurrent.TimeUnit

class ModuleDiscoveryManager private constructor(context: Context) {

    private val appContext = context.applicationContext

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val rateLimit = RateLimitTracker()
    private val validator = ModuleValidator(client, rateLimit)
    private val cache = DiscoveryCache(
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    )

    private fun authHeader(): String? {
        val token = TokenStore.getToken(appContext)
        return if (!token.isNullOrBlank()) "Bearer $token" else null
    }

    private val _modules = MutableStateFlow<List<DiscoveredModule>>(emptyList())
    val modules: StateFlow<List<DiscoveredModule>> = _modules.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    suspend fun refresh(): List<DiscoveredModule> = withContext(Dispatchers.IO) {
        if (rateLimit.isExhausted()) {
            val cached = cache.load()
            _modules.value = cached
            _error.value = "Rate limited. Reset in ${rateLimit.secondsUntilReset()}s."
            return@withContext cached
        }

        _isLoading.value = true
        _error.value = null

        try {
            val repos = searchRepos()
            val modules = validateRepos(repos)
            cache.save(modules)
            _modules.value = modules
            _error.value = null
            modules
        } catch (e: Exception) {
            Log.e(TAG, "Discovery refresh failed", e)
            val cached = cache.load()
            _modules.value = cached
            _error.value = e.message ?: "Unknown error"
            cached
        } finally {
            _isLoading.value = false
        }
    }

    suspend fun loadCached(): List<DiscoveredModule> = withContext(Dispatchers.IO) {
        val cached = cache.load()
        _modules.value = cached
        cached
    }

    suspend fun getModules(forceRefresh: Boolean = false): List<DiscoveredModule> {
        val cached = cache.load()
        if (!forceRefresh && cached.isNotEmpty() && !cache.isStale()) {
            _modules.value = cached
            return cached
        }
        return refresh()
    }

    private fun searchRepos(): List<GitHubRepo> {
        val url = "https://api.github.com/search/repositories" +
                "?q=topic:shevery-modules&sort=stars&per_page=30"

        val builder = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")

        authHeader()?.let { builder.header("Authorization", it) }

        val request = builder.build()

        val response = client.newCall(request).execute()
        rateLimit.update(response.headers)

        if (!response.isSuccessful) {
            throw IllegalStateException("GitHub search failed: ${response.code}")
        }

        val body = response.body?.string() ?: throw IllegalStateException("Empty response")
        val searchResponse = json.decodeFromString<SearchResponse>(body)
        return searchResponse.items.filter { !it.archived && !it.fork }
    }

    private fun validateRepos(repos: List<GitHubRepo>): List<DiscoveredModule> {
        val modules = mutableListOf<DiscoveredModule>()
        val auth = authHeader()
        val moduleCountByOwner = mutableMapOf<String, Int>()
        val skippedOwners = mutableSetOf<String>()

        for (repo in repos) {
            if (rateLimit.isExhausted()) break

            val owner = repo.owner.login
            val name = repo.fullName.substringAfter('/')
            val isOfficialOwner = owner.equals(OFFICIAL_OWNER, ignoreCase = true)

            if (!isOfficialOwner) {
                if (skippedOwners.contains(owner)) continue

                val currentCount = moduleCountByOwner.getOrDefault(owner, 0)
                if (currentCount >= MAX_MODULES_PER_USER) {
                    Log.d(TAG, "Skipping $owner repos - user already has $currentCount modules (max $MAX_MODULES_PER_USER)")
                    skippedOwners.add(owner)
                    continue
                }
            }

            val found = try {
                validator.validateRepo(owner, name, auth)
            } catch (e: Exception) {
                Log.w(TAG, "Validation failed for ${repo.fullName}", e)
                emptyList()
            }

            for (module in found) {
                if (!isOfficialOwner) {
                    val currentCount = moduleCountByOwner.getOrDefault(owner, 0)
                    if (currentCount >= MAX_MODULES_PER_USER) {
                        Log.d(TAG, "Skipping module ${module.moduleId} from $owner - limit reached ($currentCount/$MAX_MODULES_PER_USER)")
                        continue
                    }
                    moduleCountByOwner[owner] = currentCount + 1
                }

                modules.add(
                    module.copy(
                        stars = repo.stargazersCount,
                        repoDescription = repo.description,
                        ownerAvatar = repo.owner.avatarUrl,
                        isOfficial = isOfficialOwner
                    )
                )
            }
        }

        return modules.sortedByDescending { it.stars }
    }

    suspend fun getOfficialModules(): List<DiscoveredModule> {
        val cached = cache.load()
        val officialFromCache = cached.filter { it.isOfficial }
        if (officialFromCache.isNotEmpty()) return officialFromCache

        return withContext(Dispatchers.IO) {
            try {
                val repos = searchReposByOwner(OFFICIAL_OWNER)
                val modules = validateRepos(repos)
                modules
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch official modules", e)
                emptyList()
            }
        }
    }

    private fun searchReposByOwner(owner: String): List<GitHubRepo> {
        val url = "https://api.github.com/search/repositories" +
                "?q=topic:shevery-modules+user:$owner&sort=stars&per_page=30"

        val builder = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")

        authHeader()?.let { builder.header("Authorization", it) }

        val request = builder.build()
        val response = client.newCall(request).execute()
        rateLimit.update(response.headers)

        if (!response.isSuccessful) {
            throw IllegalStateException("GitHub search failed: ${response.code}")
        }

        val body = response.body?.string() ?: throw IllegalStateException("Empty response")
        val searchResponse = json.decodeFromString<SearchResponse>(body)
        return searchResponse.items.filter { !it.archived && !it.fork }
    }

    companion object {
        private const val TAG = "ModuleDiscovery"
        private const val PREFS_NAME = "module_discovery_cache"
        private const val MAX_MODULES_PER_USER = 4
        private const val OFFICIAL_OWNER = "HmnDev-Tech"

        @Volatile
        private var instance: ModuleDiscoveryManager? = null

        fun getInstance(context: Context): ModuleDiscoveryManager {
            return instance ?: synchronized(this) {
                instance ?: ModuleDiscoveryManager(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
}
