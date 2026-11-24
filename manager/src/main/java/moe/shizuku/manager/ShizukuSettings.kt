package moe.shizuku.manager

import android.app.ActivityThread
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.os.Build
import android.text.TextUtils
import androidx.annotation.IntDef
import androidx.appcompat.app.AppCompatDelegate
import moe.shizuku.manager.utils.EmptySharedPreferencesImpl
import moe.shizuku.manager.utils.EnvironmentUtils
import java.util.Locale
import java.util.Random
import java.util.UUID

/** Persistent preferences and runtime configuration accessor for Shevery Manager. */
object ShizukuSettings {

    const val NAME = "settings"

    /**
     * The connector token lives in its own preference file so Android auto-backup and
     * device transfer (which only include settings.xml) never carry the secret.
     */
    const val AUTH_PREFS = "shevery_auth"
    const val NIGHT_MODE = "night_mode"
    const val LANGUAGE = "language"
    const val KEEP_START_ON_BOOT = "start_on_boot"
    const val TCP_MODE = "tcp_mode"
    const val TCP_MODE_PORT = "tcp_mode_port"
    const val TCP_MODE_RANDOMIZE_PORT = "tcp_mode_randomize_port"
    const val DEFAULT_TCP_MODE_PORT = 5555
    const val SUPPRESS_TCP_MODE_PROMPT = "suppress_tcp_mode_prompt"
    const val LAST_ADB_PORT = "last_adb_port"
    const val AUTO_DISABLE_USB_DEBUGGING = "auto_disable_usb_debugging"
    const val START_ON_BOOT_ADB = "start_on_boot_adb"
    const val SHAKE_SENSITIVITY = "shake_sensitivity"
    const val REDUCED_EXPRESSIVE_MOTION = "reduced_expressive_motion"

    fun shakeSensitivity(): Int = getPreferences().getInt(SHAKE_SENSITIVITY, 1).coerceIn(0, 2)

    fun setShakeSensitivity(value: Int) {
        getPreferences().edit().putInt(SHAKE_SENSITIVITY, value.coerceIn(0, 2)).apply()
    }

    fun reducedExpressiveMotion(): Boolean = getPreferences().getBoolean(REDUCED_EXPRESSIVE_MOTION, false)

    fun setReducedExpressiveMotion(value: Boolean) {
        getPreferences().edit().putBoolean(REDUCED_EXPRESSIVE_MOTION, value).apply()
    }

    const val SHAKE_FOR_GUIDE = "shake_for_guide"
    const val USE_CLASSIC_NAV = "use_classic_nav"

    const val SWIPE_BETWEEN_TABS = "swipe_between_tabs"
    const val MOTION_BLUR_ENABLED = "motion_blur_enabled"
    const val MOTION_BLUR_INTENSITY = "motion_blur_intensity"
    const val MOTION_BLUR_STRENGTH = "motion_blur_strength"
    const val AUTOSCALE_APP = "autoscale_app"

    const val SHIZUKU_WHITELIST_MODE = "shizuku_whitelist_mode"
    const val DEVICE_OWNER_WHITELIST_MODE = "device_owner_whitelist_mode"

    const val AUTH_TOKEN = "auth_token"

    private var sPreferences: SharedPreferences? = null
    private var sAuthPreferencesOverride: SharedPreferences? = null
    private var sAppContext: Context? = null

    @JvmStatic
    fun getPreferences(): SharedPreferences {
        return sPreferences ?: EmptySharedPreferencesImpl()
    }

    internal fun setPreferencesForTesting(
        preferences: SharedPreferences?,
        authPreferences: SharedPreferences? = null
    ) {
        sPreferences = preferences
        sAuthPreferencesOverride = authPreferences
    }

    private fun getSettingsStorageContext(context: Context): Context {
        val baseContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.createDeviceProtectedStorageContext()
        } else {
            context
        }

        return object : ContextWrapper(baseContext) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                return try {
                    super.getSharedPreferences(name, mode)
                } catch (_: IllegalStateException) {
                    // SharedPreferences in credential encrypted storage are not available until after user is unlocked
                    EmptySharedPreferencesImpl()
                }
            }
        }
    }

    @JvmStatic
    fun initialize(context: Context?) {
        if (context == null) return
        sAppContext = context.applicationContext ?: context
        if (sPreferences == null) {
            sPreferences = getSettingsStorageContext(context)
                .getSharedPreferences(NAME, Context.MODE_PRIVATE)
        }
    }

    private fun getAuthPreferences(): SharedPreferences {
        sAuthPreferencesOverride?.let { return it }
        val appContext = sAppContext ?: return EmptySharedPreferencesImpl()
        return getSettingsStorageContext(appContext)
            .getSharedPreferences(AUTH_PREFS, Context.MODE_PRIVATE)
    }

    @IntDef(LaunchMethod.UNKNOWN, LaunchMethod.ROOT, LaunchMethod.ADB)
    @Retention(AnnotationRetention.SOURCE)
    annotation class LaunchMethod {
        companion object {
            const val UNKNOWN = -1
            const val ROOT = 0
            const val ADB = 1
        }
    }

    @JvmStatic
    @LaunchMethod
    fun getLastLaunchMode(): Int {
        return getPreferences().getInt("mode", LaunchMethod.UNKNOWN)
    }

    @JvmStatic
    fun setLastLaunchMode(@LaunchMethod method: Int) {
        getPreferences().edit().putInt("mode", method).apply()
    }

    @JvmStatic
    @AppCompatDelegate.NightMode
    fun getNightMode(): Int {
        var defValue = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        val app = try {
            ActivityThread.currentActivityThread()?.application
        } catch (_: Throwable) {
            null
        } ?: sAppContext
        if (app != null && try { EnvironmentUtils.isWatch(app) } catch (_: Throwable) { false }) {
            defValue = AppCompatDelegate.MODE_NIGHT_YES
        }
        return getPreferences().getInt(NIGHT_MODE, defValue)
    }

    @JvmStatic
    fun getLocale(): Locale {
        val tag = getPreferences().getString(LANGUAGE, null)
        if (TextUtils.isEmpty(tag) || "SYSTEM" == tag) {
            return Locale.getDefault()
        }
        return Locale.forLanguageTag(tag!!)
    }

    @JvmStatic
    fun isTcpMode(): Boolean = getPreferences().getBoolean(TCP_MODE, false)

    @JvmStatic
    fun setTcpMode(enabled: Boolean) {
        getPreferences().edit().putBoolean(TCP_MODE, enabled).apply()
    }

    @JvmStatic
    fun getTcpModePort(): Int {
        val port = getPreferences().getInt(TCP_MODE_PORT, DEFAULT_TCP_MODE_PORT)
        return if (port in 1..65535) port else DEFAULT_TCP_MODE_PORT
    }

    @JvmStatic
    fun setTcpModePort(port: Int) {
        if (port in 1..65535) {
            getPreferences().edit().putInt(TCP_MODE_PORT, port).apply()
        }
    }

    @JvmStatic
    fun isTcpPortRandomized(): Boolean = getPreferences().getBoolean(TCP_MODE_RANDOMIZE_PORT, false)

    @JvmStatic
    fun setTcpPortRandomized(enabled: Boolean) {
        getPreferences().edit().putBoolean(TCP_MODE_RANDOMIZE_PORT, enabled).apply()
    }

    @JvmStatic
    fun randomTcpPort(): Int = 10000 + Random().nextInt(50000)

    @JvmStatic
    fun getAutoDisableUsbDebugging(): Boolean =
        getPreferences().getBoolean(AUTO_DISABLE_USB_DEBUGGING, false)

    @JvmStatic
    fun setAutoDisableUsbDebugging(enabled: Boolean) {
        getPreferences().edit().putBoolean(AUTO_DISABLE_USB_DEBUGGING, enabled).apply()
    }

    @JvmStatic
    fun getStartOnBootAdb(): Boolean = getPreferences().getBoolean(START_ON_BOOT_ADB, false)

    @JvmStatic
    fun setStartOnBootAdb(enabled: Boolean) {
        getPreferences().edit().putBoolean(START_ON_BOOT_ADB, enabled).apply()
    }

    @JvmStatic
    fun getStartOnBoot(): Boolean = getPreferences().getBoolean(KEEP_START_ON_BOOT, false)

    @JvmStatic
    fun setStartOnBoot(enabled: Boolean) {
        getPreferences().edit().putBoolean(KEEP_START_ON_BOOT, enabled).apply()
    }

    @JvmStatic
    fun isTcpModePromptSuppressed(): Boolean =
        getPreferences().getBoolean(SUPPRESS_TCP_MODE_PROMPT, false)

    @JvmStatic
    fun setTcpModePromptSuppressed(suppressed: Boolean) {
        getPreferences().edit().putBoolean(SUPPRESS_TCP_MODE_PROMPT, suppressed).apply()
    }

    @JvmStatic
    fun getLastAdbPort(): Int = getPreferences().getInt(LAST_ADB_PORT, -1)

    @JvmStatic
    fun setLastAdbPort(port: Int) {
        getPreferences().edit().putInt(LAST_ADB_PORT, port).apply()
    }

    fun isShakeForGuideEnabled(): Boolean = getPreferences().getBoolean(SHAKE_FOR_GUIDE, false)

    fun setShakeForGuideEnabled(enabled: Boolean) {
        getPreferences().edit().putBoolean(SHAKE_FOR_GUIDE, enabled).apply()
    }

    @JvmStatic
    fun isClassicNav(): Boolean = getPreferences().getBoolean(USE_CLASSIC_NAV, false)

    @JvmStatic
    fun setClassicNav(enabled: Boolean) {
        getPreferences().edit().putBoolean(USE_CLASSIC_NAV, enabled).apply()
    }

    @JvmStatic
    fun isSwipeBetweenTabs(): Boolean = getPreferences().getBoolean(SWIPE_BETWEEN_TABS, true)

    @JvmStatic
    fun setSwipeBetweenTabs(enabled: Boolean) {
        getPreferences().edit().putBoolean(SWIPE_BETWEEN_TABS, enabled).apply()
    }

    @JvmStatic
    fun isMotionBlurEnabled(): Boolean = getPreferences().getBoolean(MOTION_BLUR_ENABLED, false)

    @JvmStatic
    fun setMotionBlurEnabled(enabled: Boolean) {
        getPreferences().edit().putBoolean(MOTION_BLUR_ENABLED, enabled).apply()
    }

    @JvmStatic
    fun getMotionBlurIntensity(): Int = getPreferences().getInt(MOTION_BLUR_INTENSITY, 60)

    @JvmStatic
    fun setMotionBlurIntensity(value: Int) {
        getPreferences().edit().putInt(MOTION_BLUR_INTENSITY, value.coerceIn(0, 100)).apply()
    }

    @JvmStatic
    fun getMotionBlurStrength(): Int = getPreferences().getInt(MOTION_BLUR_STRENGTH, 40)

    @JvmStatic
    fun setMotionBlurStrength(value: Int) {
        getPreferences().edit().putInt(MOTION_BLUR_STRENGTH, value.coerceIn(0, 100)).apply()
    }

    @JvmStatic
    fun isAutoscaleAppEnabled(): Boolean = getPreferences().getBoolean(AUTOSCALE_APP, false)

    @JvmStatic
    fun setAutoscaleAppEnabled(enabled: Boolean) {
        getPreferences().edit().putBoolean(AUTOSCALE_APP, enabled).apply()
    }

    @JvmStatic
    fun isShizukuWhitelistEnabled(): Boolean =
        getPreferences().getBoolean(SHIZUKU_WHITELIST_MODE, false)

    @JvmStatic
    fun setShizukuWhitelistEnabled(enabled: Boolean) {
        getPreferences().edit().putBoolean(SHIZUKU_WHITELIST_MODE, enabled).apply()
    }

    @JvmStatic
    fun isDeviceOwnerWhitelistEnabled(): Boolean =
        getPreferences().getBoolean(DEVICE_OWNER_WHITELIST_MODE, false)

    @JvmStatic
    fun setDeviceOwnerWhitelistEnabled(enabled: Boolean) {
        getPreferences().edit().putBoolean(DEVICE_OWNER_WHITELIST_MODE, enabled).apply()
    }

    @JvmStatic
    fun getAuthToken(): String {
        val auth = getAuthPreferences()
        var token = auth.getString(AUTH_TOKEN, null)
        if (token.isNullOrEmpty()) {
            // Migrate a token stored by older builds inside settings.xml.
            token = getPreferences().getString(AUTH_TOKEN, null)
            if (token.isNullOrEmpty()) {
                token = newAuthToken()
            }
            auth.edit().putString(AUTH_TOKEN, token).apply()
            getPreferences().edit().remove(AUTH_TOKEN).apply()
        }
        return token
    }

    @JvmStatic
    fun generateAuthToken(): String {
        val token = newAuthToken()
        getAuthPreferences().edit().putString(AUTH_TOKEN, token).apply()
        return token
    }

    private fun newAuthToken(): String {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 24)
    }
}
