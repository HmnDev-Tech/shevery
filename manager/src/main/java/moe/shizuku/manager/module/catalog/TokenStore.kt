package moe.shizuku.manager.module.catalog

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.IOException
import java.security.GeneralSecurityException

object TokenStore {
    private const val PREFS_NAME = "catalog_token_prefs"
    private const val ENCRYPTED_PREFS_NAME = "catalog_token_prefs.enc"
    private const val TAG = "TokenStore"
    private const val KEY_GITHUB_PAT = "github_pat"

    @Volatile
    private var cachedPrefs: SharedPreferences? = null

    private fun createEncryptedPrefs(appContext: Context, masterKey: MasterKey): SharedPreferences {
        return EncryptedSharedPreferences.create(
            appContext,
            ENCRYPTED_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /**
     * Clean any stale .enc file from a prior partial/broken migration.
     * Called ONLY during migration recovery (migrateAndRecreate), NOT on the
     * normal getOrCreateEncryptedPrefs success path — calling it there would
     * delete the active encrypted prefs file on every cold start and lose the token.
     */
    private fun cleanupStaleEncrypedPrefs(appContext: Context) {
        try {
            appContext.deleteSharedPreferences(ENCRYPTED_PREFS_NAME)
        } catch (_: Exception) {
            // file may not exist — ignore
        }
    }

    private fun getOrCreateEncryptedPrefs(context: Context): SharedPreferences {
        val appContext = context.applicationContext
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return try {
            val prefs = createEncryptedPrefs(appContext, masterKey)
            // One-time happy-path migration: if legacy plaintext has a token
            // and the new encrypted prefs is empty, copy it over and delete
            // the legacy file. This covers the normal first-launch-after-upgrade
            // case where createEncryptedPrefs succeeds (no keystore issue).
            try {
                val legacy = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val legacyToken = legacy.getString(KEY_GITHUB_PAT, null)
                if (!legacyToken.isNullOrBlank() &&
                    prefs.getString(KEY_GITHUB_PAT, null).isNullOrBlank()) {
                    prefs.edit().putString(KEY_GITHUB_PAT, legacyToken).commit()
                    appContext.deleteSharedPreferences(PREFS_NAME)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Happy-path legacy migration check failed (non-fatal)", e)
            }
            prefs
        } catch (e: GeneralSecurityException) {
            Log.w(TAG, "EncryptedSharedPreferences creation failed (keystore), migrating: ${e.message}")
            migrateAndRecreate(appContext, masterKey)
        } catch (e: IOException) {
            Log.w(TAG, "EncryptedSharedPreferences creation failed (IO), migrating: ${e.message}")
            migrateAndRecreate(appContext, masterKey)
        }
    }

    /**
     * Atomic migration: read legacy plaintext token, create new encrypted prefs
     * on a SEPARATE filename (so legacy file is never clobbered), copy token in,
     * then delete ONLY the legacy plaintext file. If any step fails, the legacy
     * file (and the token) survives.
     */
    private fun migrateAndRecreate(appContext: Context, masterKey: MasterKey): SharedPreferences {
        // 1) Best-effort read of the legacy plaintext token
        var legacyToken: String? = null
        try {
            val plain = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            legacyToken = plain.getString(KEY_GITHUB_PAT, null)
        } catch (_: Exception) {
            // ignore — best-effort migration
        }

        // 2) Clean any stale .enc file ONLY if we have a legacy token to migrate.
        //    If legacyToken is null (already migrated, or never existed), do NOT
        //    delete the .enc file — that would wipe a valid user token on keystore
        //    invalidation. Guard against silent data loss.
        if (!legacyToken.isNullOrBlank()) {
            cleanupStaleEncrypedPrefs(appContext)
        }
        val fresh = try {
            createEncryptedPrefs(appContext, masterKey)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create new encrypted prefs; legacy file kept", e)
            throw e
        }

        // 3) Copy token into encrypted prefs BEFORE deleting legacy file
        if (!legacyToken.isNullOrBlank()) {
            try {
                fresh.edit().putString(KEY_GITHUB_PAT, legacyToken).commit()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to persist migrated token into encrypted prefs", e)
                throw e
            }
        }

        // 4) Only now invalidate cache + delete ONLY the legacy plaintext file.
        //    The new encrypted prefs (catalog_token_prefs.enc) are NOT touched.
        synchronized(this) { cachedPrefs = null }
        try {
            appContext.deleteSharedPreferences(PREFS_NAME)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete legacy prefs after migration (non-fatal)", e)
        }
        return fresh
    }

    private fun getCachedPrefs(context: Context): SharedPreferences {
        return cachedPrefs ?: synchronized(this) {
            cachedPrefs ?: getOrCreateEncryptedPrefs(context).also { cachedPrefs = it }
        }
    }

    fun getToken(context: Context): String? {
        return getCachedPrefs(context).getString(KEY_GITHUB_PAT, null)
    }

    fun setToken(context: Context, token: String) {
        getCachedPrefs(context).edit().putString(KEY_GITHUB_PAT, token).apply()
    }

    fun clearToken(context: Context) {
        getCachedPrefs(context).edit().remove(KEY_GITHUB_PAT).apply()
    }

    fun isValidTokenFormat(token: String): Boolean {
        val trimmed = token.trim()
        return trimmed.length >= 30 &&
                (trimmed.startsWith("ghp_") || trimmed.startsWith("github_pat_"))
    }
}
