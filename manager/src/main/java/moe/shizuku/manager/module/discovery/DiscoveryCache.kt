package moe.shizuku.manager.module.discovery

import android.content.SharedPreferences
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class DiscoveryCache(private val prefs: SharedPreferences) {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    fun load(): List<DiscoveredModule> {
        val raw = prefs.getString(KEY_MODULES, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<DiscoveredModule>>(raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(modules: List<DiscoveredModule>) {
        val raw = try {
            json.encodeToString(ListSerializer(DiscoveredModule.serializer()), modules)
        } catch (e: Exception) {
            return
        }
        prefs.edit()
            .putString(KEY_MODULES, raw)
            .putLong(KEY_TIMESTAMP, System.currentTimeMillis())
            .apply()
    }

    fun isStale(): Boolean {
        val timestamp = prefs.getLong(KEY_TIMESTAMP, 0L)
        return System.currentTimeMillis() - timestamp > TTL_MS
    }

    fun clear() {
        prefs.edit().remove(KEY_MODULES).remove(KEY_TIMESTAMP).apply()
    }

    companion object {
        private const val KEY_MODULES = "discovery_cache_modules"
        private const val KEY_TIMESTAMP = "discovery_cache_timestamp"
        private const val TTL_MS = 6 * 60 * 60 * 1000L
    }
}
