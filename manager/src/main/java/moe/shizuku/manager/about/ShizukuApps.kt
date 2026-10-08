package moe.shizuku.manager.about

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import moe.shizuku.manager.module.catalog.TokenStore
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

internal data class ShizukuApp(
    val name: String,
    val summary: String,
    val url: String,
    val stars: Int = 0
)

internal data class ShizukuAppsState(
    val apps: List<ShizukuApp>,
    val isLive: Boolean
)

internal object ShizukuApps {

    const val PICK_COUNT = 3

    /** Offline fallback only: used when there is no token/network or the API fails. */
    val fallback: List<ShizukuApp> = listOf(
        ShizukuApp("Canta", "Uninstall and debloat any app without root, via Shizuku", "https://github.com/samolego/Canta"),
        ShizukuApp("ShizuTools", "Controls the Android system through Shizuku", "https://github.com/legendsayantan/ShizuTools"),
        ShizukuApp("Inure", "App manager with root and Shizuku support", "https://github.com/Hamza417/Inure"),
        ShizukuApp("InstallWithOptions", "Installs APKs on device with advanced options through Shizuku", "https://github.com/zacharee/InstallWithOptions"),
        ShizukuApp("ShizuCallRecorder", "Records calls on a non-rooted device through Shizuku", "https://github.com/kitsumed/ShizuCallRecorder"),
        ShizukuApp("Universal Installer", "Silent split-APK installs and VirusTotal scanning via Shizuku", "https://github.com/pass-with-high-score/universal-installer"),
        ShizukuApp("AutoTask", "Automation assistant driven by Shizuku and accessibility", "https://github.com/xjunz/AutoTask"),
        ShizukuApp("AutoSkip", "Skips app splash screens using Shizuku", "https://github.com/xjunz/AutoSkip"),
        ShizukuApp("shappky", "Stops background apps to boost performance with Shizuku", "https://github.com/YasserNull/shappky"),
        ShizukuApp("WiFi Password Manager", "Manages saved WiFi passwords through Shizuku or root", "https://github.com/Khh-vu/wifi-password-manager"),
        ShizukuApp("Android Screener", "Changes screen resolution and refresh rate via Shizuku", "https://github.com/jiesou/Android-Screener"),
        ShizukuApp("RebootNya", "Reboot utility that supports both root and Shizuku", "https://github.com/daisukiKaffuChino/RebootNya"),
        ShizukuApp("Buge App Manager", "App and permission management that needs Shizuku or root", "https://github.com/BugeStudioTeam/Buge-App-Manager"),
        ShizukuApp("BatStats", "Battery monitor with per-app statistics via Shizuku", "https://github.com/mlm-games/BatStats"),
        ShizukuApp("FrameX", "FPS meter and thermal diagnostics powered by Shizuku", "https://github.com/MaheshSharan/FrameX-Android"),
        ShizukuApp("DarQ Reborn", "Per-app force dark mode for Android 10 and up, via Shizuku", "https://github.com/Arora-Sir/DarQ-Reborn"),
        ShizukuApp("ShizuStore", "App store that installs Shizuku apps from their sources", "https://github.com/timschneeb/ShizuStore")
    )

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Serializable
    private data class SearchResponse(val items: List<Repo> = emptyList())

    @Serializable
    private data class Repo(
        val name: String = "",
        val full_name: String = "",
        val description: String? = null,
        val html_url: String = "",
        val stargazers_count: Int = 0,
        val archived: Boolean = false,
        val fork: Boolean = false
    )

    fun <T> randomPick(pool: List<T>, count: Int = PICK_COUNT): List<T> {
        if (pool.isEmpty()) return emptyList()
        if (pool.size <= count) return pool.shuffled()
        return pool.shuffled().take(count)
    }

    /**
     * Loads Shizuku apps from GitHub on demand (no daily rotation).
     * Call from a Refresh button. Uses the stored GitHub PAT when present
     * (5000 req/h), otherwise the unauthenticated quota.
     * Falls back to [fallback] when offline or on error.
     */
    fun load(context: Context): ShizukuAppsState {
        return try {
            val live = searchShizukuRepos(context)
            if (live.isEmpty()) {
                ShizukuAppsState(randomPick(fallback), false)
            } else {
                ShizukuAppsState(randomPick(live), true)
            }
        } catch (_: Throwable) {
            ShizukuAppsState(randomPick(fallback), false)
        }
    }

    /** Explicit manual refresh entry-point for the Refresh button. */
    fun refresh(context: Context): ShizukuAppsState = load(context)

    private fun searchShizukuRepos(context: Context): List<ShizukuApp> {
        val url = "https://api.github.com/search/repositories" +
                "?q=topic:shizuku&sort=stars&order=desc&per_page=30"
        val builder = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
        val token = try {
            TokenStore.getToken(context)
        } catch (_: Throwable) {
            null
        }
        if (!token.isNullOrBlank()) {
            builder.header("Authorization", "Bearer ${token.trim()}")
        }
        client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val body = response.body?.string() ?: return emptyList()
            val parsed = json.decodeFromString<SearchResponse>(body)
            return parsed.items
                .filter { !it.archived && !it.fork && it.html_url.isNotBlank() && it.name.isNotBlank() }
                .map {
                    ShizukuApp(
                        name = it.name,
                        summary = it.description?.trim().orEmpty(),
                        url = it.html_url,
                        stars = it.stargazers_count
                    )
                }
        }
    }
}
