package moe.shizuku.manager.app

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.annotation.StyleRes
import androidx.appcompat.app.AppCompatDelegate
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.utils.EnvironmentUtils
import rikka.core.util.ResourceUtils

/** Theme resolution utilities shared across activities and Compose UI. */
object ThemeHelper {

    private const val THEME_DEFAULT = "DEFAULT"
    private const val THEME_BLACK = "BLACK"

    const val KEY_LIGHT_THEME = "light_theme"
    const val KEY_BLACK_NIGHT_THEME = "black_night_theme"
    const val KEY_USE_SYSTEM_COLOR = "use_system_color"

    @JvmStatic
    fun isBlackNightTheme(context: Context): Boolean {
        return ShizukuSettings.getPreferences()
            .getBoolean(KEY_BLACK_NIGHT_THEME, EnvironmentUtils.isWatch(context))
    }

    @JvmStatic
    fun isUsingSystemColor(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ShizukuSettings.getPreferences().getBoolean(KEY_USE_SYSTEM_COLOR, true)
    }

    @JvmStatic
    fun getTheme(context: Context): String {
        if (isBlackNightTheme(context) &&
            ResourceUtils.isNightMode(context.resources.configuration)
        ) {
            return THEME_BLACK
        }
        return ShizukuSettings.getPreferences().getString(KEY_LIGHT_THEME, THEME_DEFAULT) ?: THEME_DEFAULT
    }

    @JvmStatic
    @StyleRes
    fun getThemeStyleRes(context: Context): Int {
        return when (getTheme(context)) {
            THEME_BLACK -> R.style.ThemeOverlay_Black
            THEME_DEFAULT -> R.style.ThemeOverlay
            else -> R.style.ThemeOverlay
        }
    }

    /**
     * Single source of truth for "is the app dark right now", shared by the
     * activity system bars (AppActivity) and the Compose content
     * (ShizukuExpressiveTheme). An explicit Light/Dark choice always wins;
     * Follow System reads the caller context's *resolved* configuration --
     * i.e. what AppCompat actually applied -- so bars and content agree by
     * construction.
     *
     * Deliberately NOT UiModeManager and NOT Resources.getSystem(): on phones
     * UiModeManager.getNightMode() reports car-dock state (almost always NO),
     * and the system config is the input to AppCompat, not its output.
     * Value 3 (legacy "follow" constant once shipped in night_mode_value) is
     * normalized to FOLLOW_SYSTEM so devices that stored it keep working.
     */
    @JvmStatic
    fun resolveAppDark(context: Context): Boolean {
        var nightMode = ShizukuSettings.getNightMode()
        if (nightMode == AppCompatDelegate.MODE_NIGHT_YES) return true
        if (nightMode == AppCompatDelegate.MODE_NIGHT_NO) return false
        if (nightMode == 3) nightMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        val config = context.resources.configuration
        return (config.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    }
}
