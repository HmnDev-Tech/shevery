package moe.shizuku.manager.module.catalog

import android.content.Context

object TokenStore {
    private const val PREFS_NAME = "catalog_token_prefs"
    private const val KEY_GITHUB_PAT = "github_pat"

    private fun getPrefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getToken(context: Context): String? {
        return getPrefs(context).getString(KEY_GITHUB_PAT, null)
    }

    fun setToken(context: Context, token: String) {
        getPrefs(context).edit().putString(KEY_GITHUB_PAT, token).apply()
    }

    fun clearToken(context: Context) {
        getPrefs(context).edit().remove(KEY_GITHUB_PAT).apply()
    }

    fun isValidTokenFormat(token: String): Boolean {
        val trimmed = token.trim()
        return trimmed.length >= 30 &&
                (trimmed.startsWith("ghp_") || trimmed.startsWith("github_pat_"))
    }
}
