package moe.shizuku.manager.module

import androidx.annotation.StringRes
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.commandium.AiProviderRepository
import moe.shizuku.manager.comput.agent.AgentSafetyMode

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
    private const val KEY_VERBOSE_LOGGING = "shizuku_verbose_logging"
    private const val KEY_NOTIFY_DEATH = "shizuku_notify_service_death"
    private const val KEY_NOTIFY_RECOVERY = "shizuku_notify_recovery"
    private const val KEY_AUTO_REFRESH_RESUME = "shizuku_auto_refresh_resume"
    private const val KEY_ERROR_PROTECT = "shizuku_error_protect"
    // Legacy watchdog prefs from before the toggle consolidation (PR #186).
    private const val KEY_LEGACY_KEEP_ALIVE = "shizuku_keep_alive"
    private const val KEY_LEGACY_AUTO_RESTART = "shizuku_auto_restart_on_crash"
    private const val KEY_COMPAT_STUB = "shizuku_compat_stub"
    private const val KEY_WIFI_REASSERT = "shizuku_wifi_adb_reassert"


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

    fun clearTrustedModules() {
        ShizukuSettings.getPreferences().edit().remove(KEY_TRUSTED_MODULES).apply()
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


    fun isVerboseLogging(): Boolean {
        return ShizukuSettings.getPreferences().getBoolean(KEY_VERBOSE_LOGGING, false)
    }

    fun setVerboseLogging(value: Boolean) {
        ShizukuSettings.getPreferences().edit().putBoolean(KEY_VERBOSE_LOGGING, value).apply()
    }

    fun isNotifyOnServiceDeath(): Boolean {
        return ShizukuSettings.getPreferences().getBoolean(KEY_NOTIFY_DEATH, true)
    }

    fun setNotifyOnServiceDeath(value: Boolean) {
        ShizukuSettings.getPreferences().edit().putBoolean(KEY_NOTIFY_DEATH, value).apply()
    }

    fun isNotifyOnRecovery(): Boolean {
        return ShizukuSettings.getPreferences().getBoolean(KEY_NOTIFY_RECOVERY, false)
    }

    fun setNotifyOnRecovery(value: Boolean) {
        ShizukuSettings.getPreferences().edit().putBoolean(KEY_NOTIFY_RECOVERY, value).apply()
    }

    fun isAutoRefreshOnResume(): Boolean {
        return ShizukuSettings.getPreferences().getBoolean(KEY_AUTO_REFRESH_RESUME, true)
    }

    fun setAutoRefreshOnResume(value: Boolean) {
        ShizukuSettings.getPreferences().edit().putBoolean(KEY_AUTO_REFRESH_RESUME, value).apply()
    }

    fun isWatchdogEnabled(): Boolean {
        return ShizukuSettings.getPreferences().getBoolean(KEY_ERROR_PROTECT, true)
    }

    fun setWatchdogEnabled(value: Boolean) {
        ShizukuSettings.getPreferences().edit().putBoolean(KEY_ERROR_PROTECT, value).apply()
    }

    // Maps pre-consolidation watchdog prefs (PR #186（ into the single master toggle.
    // Users who had the legacy keep-alive or auto-restart prefs enabled but Watchdog
    // off would otherwise silently lose watchdog coverage after updating.

    fun migrateLegacyWatchdogPrefs() {
        val prefs = ShizukuSettings.getPreferences()
        if (prefs.getBoolean(KEY_ERROR_PROTECT, true)) return
        val legacyEnabled = prefs.getBoolean(KEY_LEGACY_KEEP_ALIVE, false) ||
            prefs.getBoolean(KEY_LEGACY_AUTO_RESTART, false)
        if (legacyEnabled) {
            prefs.edit().putBoolean(KEY_ERROR_PROTECT, true)
                .remove(KEY_LEGACY_KEEP_ALIVE)
                .remove(KEY_LEGACY_AUTO_RESTART)
                .apply()
        }
    }

    fun isCompatibilityStubEnabled(): Boolean {
        return ShizukuSettings.getPreferences().getBoolean(KEY_COMPAT_STUB, false)
    }

    fun setCompatibilityStubEnabled(value: Boolean) {
        ShizukuSettings.getPreferences().edit().putBoolean(KEY_COMPAT_STUB, value).apply()
    }

    // Off by default: only ROMs that silently clear adb_wifi_enabled (legacy
    // TCP mode in use, or on lock) need the 0 -> 1 toggle during ADB start.
    fun isWifiReassertEnabled(): Boolean {
        return ShizukuSettings.getPreferences().getBoolean(KEY_WIFI_REASSERT, false)
    }

    fun setWifiReassertEnabled(value: Boolean) {
        ShizukuSettings.getPreferences().edit().putBoolean(KEY_WIFI_REASSERT, value).apply()
    }

    // Comput Settings
    private const val KEY_COMPUT_RECOMMAND = "comput_recommand"
    private const val KEY_COMPUT_AI_EXPLAIN = "comput_ai_explain"
    private const val KEY_COMPUT_GEMINI_MODEL = "comput_gemini_model"
    // Comput AI provider settings (generic OpenAI-compatible endpoint)
    private const val KEY_COMPUT_AI_NAME = "comput_ai_name"
    private const val KEY_COMPUT_AI_BASE_URL = "comput_ai_base_url"
    private const val KEY_COMPUT_AI_MODEL = "comput_ai_model"

    fun getComputApiKey(): String {
        return AiProviderRepository.getActiveKey()
    }

    fun setComputApiKey(value: String) {
        AiProviderRepository.setActiveKey(value)
    }

    fun getComputAiName(): String {
        migrateComputAiPrefsIfNeeded()
        return AiProviderRepository.getActive()?.name ?: ""
    }

    fun setComputAiName(value: String) {
        AiProviderRepository.getActive()?.let { AiProviderRepository.update(it.copy(name = value)) }
    }

    fun getComputAiBaseUrl(): String {
        migrateComputAiPrefsIfNeeded()
        return AiProviderRepository.getActive()?.baseUrl ?: "https://openrouter.ai/api/v1/"
    }

    fun setComputAiBaseUrl(value: String) {
        val trimmed = value.trim()
        require(trimmed.startsWith("http://") || trimmed.startsWith("https://")) { "Base URL must start with http:// or https://" }
        AiProviderRepository.getActive()?.let { AiProviderRepository.update(it.copy(baseUrl = trimmed)) }
    }

    fun getComputAiModel(): String {
        migrateComputAiPrefsIfNeeded()
        return AiProviderRepository.getActive()?.model ?: ""
    }

    fun setComputAiModel(value: String) {
        AiProviderRepository.getActive()?.let { AiProviderRepository.update(it.copy(model = value)) }
    }

    // One-time migration on first read: a user who had a custom Gemini model
    // gets it copied onto the new generic provider pref (named "Gemini", pointed at
    // Google's OpenAI-compatible endpoint), keeping the existing Keystore-encrypted key.

    private fun migrateComputAiPrefsIfNeeded() {
        val prefs = ShizukuSettings.getPreferences()
        if (!prefs.contains(KEY_COMPUT_GEMINI_MODEL)) return
        if (prefs.contains(KEY_COMPUT_AI_MODEL)) return
        val legacyModel = prefs.getString(KEY_COMPUT_GEMINI_MODEL, "") ?: ""
        if (legacyModel.isBlank()) return
        prefs.edit()
            .putString(KEY_COMPUT_AI_MODEL, legacyModel)
            .putString(KEY_COMPUT_AI_NAME, "Gemini")
            .putString(KEY_COMPUT_AI_BASE_URL, "https://generativelanguage.googleapis.com/v1beta/openai/")
            .apply()
    }

    @Deprecated("Use getComputAiModel()")
    fun getComputGeminiModel(): String {
        return getComputAiModel()
    }

    @Deprecated("Use setComputAiModel()")
    fun setComputGeminiModel(value: String) {
        setComputAiModel(value)
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

    // Comput Agent: agentic loop over shell + module services on the AI provider.
    private const val KEY_AGENT_SAFETY_MODE = "comput_agent_safety_mode"
    private const val KEY_AGENT_MAX_STEPS = "comput_agent_max_steps"
    private const val KEY_AGENT_ASK_SHELL = "comput_agent_ask_shell"
    private const val KEY_AGENT_ASK_MODULE = "comput_agent_ask_module"

    fun getAgentSafetyMode(): AgentSafetyMode {
        return AgentSafetyMode.fromValue(
            ShizukuSettings.getPreferences().getString(KEY_AGENT_SAFETY_MODE, AgentSafetyMode.SECURE.value)
        )
    }

    fun setAgentSafetyMode(mode: AgentSafetyMode) {
        ShizukuSettings.getPreferences().edit().putString(KEY_AGENT_SAFETY_MODE, mode.value).apply()
    }

    fun getAgentMaxSteps(): Int {
        return ShizukuSettings.getPreferences()
            .getInt(KEY_AGENT_MAX_STEPS, 5)
            .coerceIn(1, moe.shizuku.manager.comput.agent.ComputAgent.MAX_STEPS_HARD)
    }

    fun setAgentMaxSteps(value: Int) {
        ShizukuSettings.getPreferences().edit()
            .putInt(KEY_AGENT_MAX_STEPS, value.coerceIn(1, moe.shizuku.manager.comput.agent.ComputAgent.MAX_STEPS_HARD))
            .apply()
    }

    fun isAgentAskShell(): Boolean {
        return ShizukuSettings.getPreferences().getBoolean(KEY_AGENT_ASK_SHELL, true)
    }

    fun setAgentAskShell(value: Boolean) {
        ShizukuSettings.getPreferences().edit().putBoolean(KEY_AGENT_ASK_SHELL, value).apply()
    }

    fun isAgentAskModuleService(): Boolean {
        return ShizukuSettings.getPreferences().getBoolean(KEY_AGENT_ASK_MODULE, true)
    }

    fun setAgentAskModuleService(value: Boolean) {
        ShizukuSettings.getPreferences().edit().putBoolean(KEY_AGENT_ASK_MODULE, value).apply()
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

    // Shevery App Update Settings
    private const val KEY_APP_UPDATE_CHANNEL = "app_update_channel"
    private const val KEY_APP_UPDATE_AUTO_CHECK = "app_update_auto_check"
    private const val KEY_APP_UPDATE_FREQUENCY = "app_update_frequency"
    private const val KEY_APP_LAST_CHECK_TIME = "app_last_check_time"

    enum class AppUpdateChannel(
        val value: String,
        @param:StringRes val labelRes: Int
    ) {
        STABLE("stable", R.string.app_update_channel_stable),
        BETA_PRE_RELEASE("beta", R.string.app_update_channel_beta);

        companion object {
            fun fromValue(value: String?): AppUpdateChannel {
                return entries.firstOrNull { it.value == value } ?: STABLE
            }
        }
    }

    fun getAppUpdateChannel(): AppUpdateChannel {
        return AppUpdateChannel.fromValue(
            ShizukuSettings.getPreferences().getString(KEY_APP_UPDATE_CHANNEL, AppUpdateChannel.STABLE.value)
        )
    }

    fun setAppUpdateChannel(channel: AppUpdateChannel) {
        ShizukuSettings.getPreferences().edit().putString(KEY_APP_UPDATE_CHANNEL, channel.value).apply()
    }

    fun isAppUpdateAutoCheckEnabled(): Boolean {
        return ShizukuSettings.getPreferences().getBoolean(KEY_APP_UPDATE_AUTO_CHECK, true)
    }

    fun setAppUpdateAutoCheckEnabled(enabled: Boolean) {
        ShizukuSettings.getPreferences().edit().putBoolean(KEY_APP_UPDATE_AUTO_CHECK, enabled).apply()
    }

    fun getAppUpdateFrequency(): UpdateFrequency {
        return UpdateFrequency.fromValue(
            ShizukuSettings.getPreferences().getString(KEY_APP_UPDATE_FREQUENCY, UpdateFrequency.DAILY.value)
        )
    }

    fun setAppUpdateFrequency(freq: UpdateFrequency) {
        ShizukuSettings.getPreferences().edit().putString(KEY_APP_UPDATE_FREQUENCY, freq.value).apply()
    }

    fun getAppLastCheckTime(): Long {
        return ShizukuSettings.getPreferences().getLong(KEY_APP_LAST_CHECK_TIME, 0L)
    }

    fun setAppLastCheckTime(time: Long) {
        ShizukuSettings.getPreferences().edit().putLong(KEY_APP_LAST_CHECK_TIME, time).apply()
    }

    // Persisted pending update (survives process death)
    private const val KEY_APP_PENDING_UPDATE_VERSION = "app_pending_update_version"
    private const val KEY_APP_PENDING_UPDATE_URL = "app_pending_update_url"
    private const val KEY_APP_PENDING_UPDATE_DETECTED_AT = "app_pending_update_detected_at"

    fun setPendingUpdate(version: String, url: String, detectedAt: Long) {
        ShizukuSettings.getPreferences().edit()
            .putString(KEY_APP_PENDING_UPDATE_VERSION, version)
            .putString(KEY_APP_PENDING_UPDATE_URL, url)
            .putLong(KEY_APP_PENDING_UPDATE_DETECTED_AT, detectedAt)
            .apply()
    }

    fun getPendingUpdateVersion(): String? {
        return ShizukuSettings.getPreferences().getString(KEY_APP_PENDING_UPDATE_VERSION, null)
            ?.takeIf { it.isNotBlank() }
    }

    fun getPendingUpdateUrl(): String? {
        return ShizukuSettings.getPreferences().getString(KEY_APP_PENDING_UPDATE_URL, null)
            ?.takeIf { it.isNotBlank() }
    }

    fun getPendingUpdateDetectedAt(): Long {
        return ShizukuSettings.getPreferences().getLong(KEY_APP_PENDING_UPDATE_DETECTED_AT, 0L)
    }

    fun clearPendingUpdate() {
        ShizukuSettings.getPreferences().edit()
            .remove(KEY_APP_PENDING_UPDATE_VERSION)
            .remove(KEY_APP_PENDING_UPDATE_URL)
            .remove(KEY_APP_PENDING_UPDATE_DETECTED_AT)
            .apply()
    }
}
