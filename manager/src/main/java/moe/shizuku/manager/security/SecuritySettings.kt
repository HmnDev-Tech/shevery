package moe.shizuku.manager.security

import moe.shizuku.manager.ShizukuSettings

object SecuritySettings {

    const val KEY_AUTH_ENABLED = "security_auth_enabled"
    const val KEY_AUTH_TIMEOUT_SECONDS = "security_auth_timeout_seconds"
    const val KEY_AUTH_ON_APP_OPEN = "security_auth_on_app_open"
    const val KEY_AUTH_ON_PERMISSIONS = "security_auth_on_permissions"
    const val KEY_AUTH_ON_SERVER = "security_auth_on_server"
    const val KEY_AUTH_ON_DEVICE_OWNER = "security_auth_on_device_owner"
    const val KEY_AUTH_ON_STUBS = "security_auth_on_stubs"

    enum class ProtectedAction {
        APP_OPEN,
        PERMISSIONS,
        SERVER_TOGGLE,
        DEVICE_OWNER,
        STUB_MANAGEMENT
    }

    var isAuthEnabled: Boolean
        get() = ShizukuSettings.getPreferences().getBoolean(KEY_AUTH_ENABLED, false)
        set(value) = ShizukuSettings.getPreferences().edit().putBoolean(KEY_AUTH_ENABLED, value).apply()

    var timeoutSeconds: Int
        get() = ShizukuSettings.getPreferences().getInt(KEY_AUTH_TIMEOUT_SECONDS, 0)
        set(value) = ShizukuSettings.getPreferences().edit().putInt(KEY_AUTH_TIMEOUT_SECONDS, value).apply()

    var authOnAppOpen: Boolean
        get() = ShizukuSettings.getPreferences().getBoolean(KEY_AUTH_ON_APP_OPEN, true)
        set(value) = ShizukuSettings.getPreferences().edit().putBoolean(KEY_AUTH_ON_APP_OPEN, value).apply()

    var authOnPermissions: Boolean
        get() = ShizukuSettings.getPreferences().getBoolean(KEY_AUTH_ON_PERMISSIONS, true)
        set(value) = ShizukuSettings.getPreferences().edit().putBoolean(KEY_AUTH_ON_PERMISSIONS, value).apply()

    var authOnServer: Boolean
        get() = ShizukuSettings.getPreferences().getBoolean(KEY_AUTH_ON_SERVER, true)
        set(value) = ShizukuSettings.getPreferences().edit().putBoolean(KEY_AUTH_ON_SERVER, value).apply()

    var authOnDeviceOwner: Boolean
        get() = ShizukuSettings.getPreferences().getBoolean(KEY_AUTH_ON_DEVICE_OWNER, true)
        set(value) = ShizukuSettings.getPreferences().edit().putBoolean(KEY_AUTH_ON_DEVICE_OWNER, value).apply()

    var authOnStubs: Boolean
        get() = ShizukuSettings.getPreferences().getBoolean(KEY_AUTH_ON_STUBS, true)
        set(value) = ShizukuSettings.getPreferences().edit().putBoolean(KEY_AUTH_ON_STUBS, value).apply()

    fun isActionProtected(action: ProtectedAction): Boolean {
        if (!isAuthEnabled) return false
        return when (action) {
            ProtectedAction.APP_OPEN -> authOnAppOpen
            ProtectedAction.PERMISSIONS -> authOnPermissions
            ProtectedAction.SERVER_TOGGLE -> authOnServer
            ProtectedAction.DEVICE_OWNER -> authOnDeviceOwner
            ProtectedAction.STUB_MANAGEMENT -> authOnStubs
        }
    }
}
