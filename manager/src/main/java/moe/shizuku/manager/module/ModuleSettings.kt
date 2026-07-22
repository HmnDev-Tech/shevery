package moe.shizuku.manager.module

import androidx.annotation.StringRes
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object ModuleSettings {

    private const val KEY_ACCESS_MODE = "adb_modules_access_mode"
    private const val KEY_ALLOW_BACKGROUND_ACTIONS = "adb_modules_allow_background_actions"
    private const val KEY_CUSTOM_ACTION = "adb_modules_custom_action"
    private const val KEY_UPDATE_FREQUENCY = "adb_modules_update_frequency"
    private const val KEY_INSTALL_MODE = "adb_modules_install_mode"
    private const val KEY_CATALOG_ENABLED = "adb_modules_catalog_enabled"
    private const val KEY_CUSTOM_SERVICE = "adb_modules_custom_service"
    private const val KEY_CUSTOM_WEB_BRIDGE = "adb_modules_custom_web_bridge"
    private const val KEY_CUSTOM_WEB_NETWORK = "adb_modules_custom_web_network"
    private const val KEY_CUSTOM_WEB_DOWNLOAD = "adb_modules_custom_web_download"
    private const val KEY_RECOMMAND_WEBUI = "adb_modules_recommand_webui"
    private const val KEY_RECOMMAND_ACTION = "adb_modules_recommand_action"
    private const val KEY_TRUSTED_MODULES = "adb_modules_trusted_modules"
    private const val KEY_CONNECTOR_ENABLED = "shizuku_connector_enabled"
    private const val KEY_DHIZUKU_ENABLED = "shizuku_dhizuku_enabled"
    private const val KEY_KEEP_ALIVE = "shizuku_keep_alive"
    private const val KEY_VERBOSE_LOGGING = "shizuku_verbose_logging"
    private const val KEY_AUTO_RESTART = "shizuku_auto_restart_on_crash"
    private const val KEY_NOTIFY_DEATH = "shizuku_notify_service_death"
    private const val KEY_ERROR_PROTECT = "shizuku_error_protect"


    enum class AccessMode(
        val value: String,
        @param:StringRes val labelRes: Int,
        @param:StringRes val summaryRes: Int
    ) {
        SAFE(
            "safe",
            R.string.modules_access_mode_safe,
            R.string.modules_access_mode_safe_summary
        ),
        CUSTOM(
            "custom",
            R.string.modules_access_mode_custom,
            R.string.modules_access_mode_custom_summary
        ),
        FULL(
            "full",
            R.string.modules_access_mode_full,
            R.string.modules_access_mode_full_summary
        );

        companion object {
            fun fromValue(value: String?): AccessMode {
                return entries.firstOrNull { it.value == value } ?: SAFE
            }
        }
    }

    data class CustomPermissions(
        val action: Boolean,
        val service: Boolean,
        val webBridge: Boolean,
        val webNetwork: Boolean,
        val webDownload: Boolean
    )

    fun getAccessMode(): AccessMode {
        return AccessMode.fromValue(
            ShizukuSettings.getPreferences().getString(KEY_ACCESS_MODE, AccessMode.SAFE.value)
        )
    }

    fun setAccessMode(mode: AccessMode) {
        ShizukuSettings.getPreferences().edit().putString(KEY_ACCESS_MODE, mode.value).apply()
    }

    fun allowBackgroundActions(): Boolean {
        return ShizukuSettings.getPreferences().getBoolean(KEY_ALLOW_BACKGROUND_ACTIONS, false)
    }

    fun setAllowBackgroundActions(value: Boolean) {
        ShizukuSettings.getPreferences().edit().putBoolean(KEY_ALLOW_BACKGROUND_ACTIONS, value).apply()
    }

    fun getCustomPermissions(): CustomPermissions {
        val prefs = ShizukuSettings.getPreferences()
        return CustomPermissions(
            action = prefs.getBoolean(KEY_CUSTOM_ACTION, false),
            service = prefs.getBoolean(KEY_CUSTOM_SERVICE, false),
            webBridge = prefs.getBoolean(KEY_CUSTOM_WEB_BRIDGE, false),
            webNetwork = prefs.getBoolean(KEY_CUSTOM_WEB_NETWORK, false),
            webDownload = prefs.getBoolean(KEY_CUSTOM_WEB_DOWNLOAD, false)
        )
    }

    fun setCustomPermissions(value: CustomPermissions) {
        ShizukuSettings.getPreferences().edit()
            .putBoolean(KEY_CUSTOM_ACTION, value.action)
            .putBoolean(KEY_CUSTOM_SERVICE, value.service)
            .putBoolean(KEY_CUSTOM_WEB_BRIDGE, value.webBridge)
            .putBoolean(KEY_CUSTOM_WEB_NETWORK, value.webNetwork)
            .putBoolean(KEY_CUSTOM_WEB_DOWNLOAD, value.webDownload)
            .apply()
    }

    fun canRunAction(): Boolean {
        return when (getAccessMode()) {
            AccessMode.SAFE -> false
            AccessMode.FULL -> true
            AccessMode.CUSTOM -> getCustomPermissions().action
        }
    }

    fun canRunAction(module: AdbModule): Boolean {
        return isModuleTrusted(module.id) || canRunAction()
    }

    fun canRunService(): Boolean {
        return when (getAccessMode()) {
            AccessMode.SAFE -> false
            AccessMode.FULL -> true
            AccessMode.CUSTOM -> getCustomPermissions().service
        }
    }

    fun canRunService(module: AdbModule): Boolean {
        return isModuleTrusted(module.id) || canRunService()
    }

    fun canExposeWebBridge(): Boolean {
        return when (getAccessMode()) {
            AccessMode.SAFE -> false
            AccessMode.FULL -> true
            AccessMode.CUSTOM -> getCustomPermissions().webBridge
        }
    }

    fun canExposeWebBridge(module: AdbModule): Boolean {
        return isModuleTrusted(module.id) || canExposeWebBridge()
    }

    fun canUseWebNetwork(): Boolean {
        return when (getAccessMode()) {
            AccessMode.SAFE -> false
            AccessMode.FULL -> false
            AccessMode.CUSTOM -> getCustomPermissions().webNetwork
        }
    }

    fun canUseWebNetwork(module: AdbModule): Boolean {
        return isModuleTrusted(module.id) || canUseWebNetwork()
    }

    fun canDownloadWebFiles(): Boolean {
        return when (getAccessMode()) {
            AccessMode.SAFE -> false
            AccessMode.FULL -> true
            AccessMode.CUSTOM -> getCustomPermissions().webDownload
        }
    }

    fun canDownloadWebFiles(module: AdbModule): Boolean {
        return isModuleTrusted(module.id) || canDownloadWebFiles()
    }

    fun canRunBackground(module: AdbModule): Boolean {
        return isModuleTrusted(module.id) || allowBackgroundActions()
    }

    fun isModuleTrusted(moduleId: String): Boolean {
        return ShizukuSettings.getPreferences()
            .getStringSet(KEY_TRUSTED_MODULES, emptySet())
            ?.contains(moduleId)
            ?: false
    }

    fun setModuleTrusted(moduleId: String, trusted: Boolean) {
        val prefs = ShizukuSettings.getPreferences()
        val current = prefs.getStringSet(KEY_TRUSTED_MODULES, emptySet()).orEmpty().toMutableSet()
        if (trusted) {
            current += moduleId
        } else {
            current -= moduleId
        }
        prefs.edit().putStringSet(KEY_TRUSTED_MODULES, current).apply()
    }

    fun recommandForWebUi(): Boolean {
        return ShizukuSettings.getPreferences().getBoolean(KEY_RECOMMAND_WEBUI, true)
    }

    fun setRecommandForWebUi(value: Boolean) {
        ShizukuSettings.getPreferences().edit().putBoolean(KEY_RECOMMAND_WEBUI, value).apply()
    }

    fun recommandForAction(): Boolean {
        return ShizukuSettings.getPreferences().getBoolean(KEY_RECOMMAND_ACTION, true)
    }

    fun setRecommandForAction(value: Boolean) {
        ShizukuSettings.getPreferences().edit().putBoolean(KEY_RECOMMAND_ACTION, value).apply()
    }

    fun isConnectorEnabled(): Boolean {
        return ShizukuSettings.getPreferences().getBoolean(KEY_CONNECTOR_ENABLED, false)
    }

    fun setConnectorEnabled(value: Boolean) {
        ShizukuSettings.getPreferences().edit().putBoolean(KEY_CONNECTOR_ENABLED, value).apply()
    }

    fun isDhizukuEnabled(): Boolean {
        return ShizukuSettings.getPreferences().getBoolean(KEY_DHIZUKU_ENABLED, false)
    }

    fun setDhizukuEnabled(value: Boolean) {
        ShizukuSettings.getPreferences().edit().putBoolean(KEY_DHIZUKU_ENABLED, value).apply()
    }


    fun isKeepAlive(): Boolean {
        return ShizukuSettings.getPreferences().getBoolean(KEY_KEEP_ALIVE, false)
    }

    fun setKeepAlive(value: Boolean) {
        ShizukuSettings.getPreferences().edit().putBoolean(KEY_KEEP_ALIVE, value).apply()
    }

    fun isVerboseLogging(): Boolean {
        return ShizukuSettings.getPreferences().getBoolean(KEY_VERBOSE_LOGGING, false)
    }

    fun setVerboseLogging(value: Boolean) {
        ShizukuSettings.getPreferences().edit().putBoolean(KEY_VERBOSE_LOGGING, value).apply()
    }

    fun isAutoRestartOnCrash(): Boolean {
        return ShizukuSettings.getPreferences().getBoolean(KEY_AUTO_RESTART, false)
    }

    fun setAutoRestartOnCrash(value: Boolean) {
        ShizukuSettings.getPreferences().edit().putBoolean(KEY_AUTO_RESTART, value).apply()
    }

    fun isNotifyOnServiceDeath(): Boolean {
        return ShizukuSettings.getPreferences().getBoolean(KEY_NOTIFY_DEATH, true)
    }

    fun setNotifyOnServiceDeath(value: Boolean) {
        ShizukuSettings.getPreferences().edit().putBoolean(KEY_NOTIFY_DEATH, value).apply()
    }

    fun isErrorProtectEnabled(): Boolean {
        return ShizukuSettings.getPreferences().getBoolean(KEY_ERROR_PROTECT, true)
    }

    fun setErrorProtectEnabled(value: Boolean) {
        ShizukuSettings.getPreferences().edit().putBoolean(KEY_ERROR_PROTECT, value).apply()
    }

    // Comput Settings
    private const val KEY_COMPUT_API_KEY = "comput_api_key"
    private const val KEY_COMPUT_RECOMMAND = "comput_recommand"
    private const val KEY_COMPUT_AI_EXPLAIN = "comput_ai_explain"
    private const val KEY_COMPUT_GEMINI_MODEL = "comput_gemini_model"

    private const val PROVIDER = "AndroidKeyStore"
    private const val ALIAS = "SheveryGeminiKey"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    private fun getSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }
        val key = keyStore.getKey(ALIAS, null) as? SecretKey
        if (key != null) return key

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        val spec = KeyGenParameterSpec.Builder(
            ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return ""
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
        val iv = cipher.iv
        val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val ivString = Base64.encodeToString(iv, Base64.NO_WRAP)
        val encryptedString = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
        return "$ivString:$encryptedString"
    }

    private fun decrypt(cipherText: String): String {
        if (cipherText.isEmpty()) return ""
        val parts = cipherText.split(":")
        if (parts.size != 2) return ""
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val encryptedBytes = Base64.decode(parts[1], Base64.NO_WRAP)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)
        val decryptedBytes = cipher.doFinal(encryptedBytes)
        return String(decryptedBytes, Charsets.UTF_8)
    }

    fun getComputApiKey(): String {
        val raw = ShizukuSettings.getPreferences().getString(KEY_COMPUT_API_KEY, "") ?: ""
        if (raw.isEmpty()) return ""
        if (!raw.contains(":")) {
            // It was plain text before, let's encrypt and save it now
            try {
                val encrypted = encrypt(raw)
                ShizukuSettings.getPreferences().edit().putString(KEY_COMPUT_API_KEY, encrypted).apply()
                return raw
            } catch (e: Throwable) {
                return raw
            }
        }
        return try {
            decrypt(raw)
        } catch (e: Throwable) {
            ""
        }
    }

    fun setComputApiKey(value: String) {
        val encrypted = try {
            encrypt(value)
        } catch (e: Throwable) {
            value
        }
        ShizukuSettings.getPreferences().edit().putString(KEY_COMPUT_API_KEY, encrypted).apply()
    }

    fun getComputGeminiModel(): String {
        return ShizukuSettings.getPreferences().getString(KEY_COMPUT_GEMINI_MODEL, "gemini-3.5-flash") ?: "gemini-3.5-flash"
    }

    fun setComputGeminiModel(value: String) {
        ShizukuSettings.getPreferences().edit().putString(KEY_COMPUT_GEMINI_MODEL, value).apply()
    }

    fun isComputRecommandEnabled(): Boolean {
        return ShizukuSettings.getPreferences().getBoolean(KEY_COMPUT_RECOMMAND, true)
    }

    fun setComputRecommandEnabled(value: Boolean) {
        ShizukuSettings.getPreferences().edit().putBoolean(KEY_COMPUT_RECOMMAND, value).apply()
    }

    fun isComputAiExplainEnabled(): Boolean {
        return ShizukuSettings.getPreferences().getBoolean(KEY_COMPUT_AI_EXPLAIN, true)
    }

    fun setComputAiExplainEnabled(value: Boolean) {
        ShizukuSettings.getPreferences().edit().putBoolean(KEY_COMPUT_AI_EXPLAIN, value).apply()
    }

    private const val KEY_COMPUT_MACROS = "comput_macros"

    fun getComputMacros(): String {
        return ShizukuSettings.getPreferences().getString(KEY_COMPUT_MACROS, "{}") ?: "{}"
    }

    fun setComputMacros(value: String) {
        ShizukuSettings.getPreferences().edit().putString(KEY_COMPUT_MACROS, value).apply()
    }

    enum class UpdateFrequency(val value: String) {
        MANUAL("manual"),
        DAILY("daily"),
        WEEKLY("weekly");

        companion object {
            fun fromValue(value: String?): UpdateFrequency {
                return entries.firstOrNull { it.value == value } ?: MANUAL
            }
        }
    }

    enum class InstallMode(val value: String) {
        SOURCES("sources"),
        RELEASE("release");

        companion object {
            fun fromValue(value: String?): InstallMode {
                return entries.firstOrNull { it.value == value } ?: SOURCES
            }
        }
    }

    fun getUpdateFrequency(): UpdateFrequency {
        return UpdateFrequency.fromValue(
            ShizukuSettings.getPreferences().getString(KEY_UPDATE_FREQUENCY, UpdateFrequency.MANUAL.value)
        )
    }

    fun setUpdateFrequency(freq: UpdateFrequency) {
        ShizukuSettings.getPreferences().edit().putString(KEY_UPDATE_FREQUENCY, freq.value).apply()
    }

    fun getInstallMode(): InstallMode {
        return InstallMode.fromValue(
            ShizukuSettings.getPreferences().getString(KEY_INSTALL_MODE, InstallMode.SOURCES.value)
        )
    }

    fun setInstallMode(mode: InstallMode) {
        ShizukuSettings.getPreferences().edit().putString(KEY_INSTALL_MODE, mode.value).apply()
    }

    fun isCatalogEnabled(): Boolean {
        return ShizukuSettings.getPreferences().getBoolean(KEY_CATALOG_ENABLED, true)
    }

    fun setCatalogEnabled(enabled: Boolean) {
        ShizukuSettings.getPreferences().edit().putBoolean(KEY_CATALOG_ENABLED, enabled).apply()
    }
}
