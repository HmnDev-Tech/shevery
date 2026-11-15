package rikka.shizuku.server

import com.hamondev.shevery.api.SheveryApiConstants

/** Server-side constants and client/manager permission resolution helpers. */
object ServerConstants {

    const val MANAGER_APP_NOT_FOUND = 50
    const val MANAGER_APP_LOOKUP_UNAVAILABLE = 51

    const val MANAGER_APPLICATION_ID = SheveryApiConstants.MANAGER_APPLICATION_ID
    const val SHIZUKU_STUB_APPLICATION_ID = SheveryApiConstants.SHIZUKU_STUB_APPLICATION_ID

    // Primary Shevery Privilege API permissions
    const val PERMISSION = SheveryApiConstants.PERMISSION_PRIVILEGED_API
    const val PERMISSION_MANAGER = SheveryApiConstants.PERMISSION_MANAGER

    // Legacy Shizuku permissions (isolated to Shizuku Stub moe.shizuku.privileged.api)
    const val LEGACY_SHIZUKU_PERMISSION = SheveryApiConstants.LEGACY_SHIZUKU_PERMISSION
    const val LEGACY_PERMISSION_MANAGER = SheveryApiConstants.LEGACY_SHIZUKU_MANAGER_PERMISSION

    const val REQUEST_PERMISSION_ACTION = "$MANAGER_APPLICATION_ID.intent.action.REQUEST_PERMISSION"

    const val SHEVERY_PROVIDER_SUFFIX = SheveryApiConstants.PROVIDER_SUFFIX
    const val SHIZUKU_PROVIDER_SUFFIX = SheveryApiConstants.LEGACY_PROVIDER_SUFFIX

    const val EXTRA_BINDER_SHEVERY = SheveryApiConstants.EXTRA_BINDER
    const val EXTRA_BINDER_SHIZUKU = SheveryApiConstants.LEGACY_EXTRA_BINDER

    const val META_DATA_SHEVERY_CLIENT = SheveryApiConstants.META_DATA_CLIENT_SUPPORT
    const val META_DATA_SHIZUKU_CLIENT = SheveryApiConstants.LEGACY_META_DATA_CLIENT_SUPPORT

    const val BINDER_TRANSACTION_getApplications = 10001
    const val BINDER_TRANSACTION_setErrorProtectEnabled = 10002
    const val BINDER_TRANSACTION_getErrorProtectEnabled = 10003

    @JvmStatic
    fun hasSheveryPermission(requestedPermissions: Array<String>?): Boolean {
        return requestedPermissions != null && requestedPermissions.contains(PERMISSION)
    }

    @JvmStatic
    fun hasLegacyShizukuPermission(requestedPermissions: Array<String>?): Boolean {
        return requestedPermissions != null && requestedPermissions.contains(LEGACY_SHIZUKU_PERMISSION)
    }

    @JvmStatic
    fun hasAnyClientPermission(requestedPermissions: Array<String>?): Boolean {
        return hasSheveryPermission(requestedPermissions) || hasLegacyShizukuPermission(requestedPermissions)
    }

    @JvmStatic
    fun resolveClientPermission(requestedPermissions: Array<String>?): String? {
        return when {
            hasSheveryPermission(requestedPermissions) -> PERMISSION
            hasLegacyShizukuPermission(requestedPermissions) -> LEGACY_SHIZUKU_PERMISSION
            else -> null
        }
    }

    @JvmStatic
    fun resolveManagerPermission(requestedPermissions: Array<String>?): String? {
        if (requestedPermissions == null) {
            return null
        }
        return when {
            requestedPermissions.contains(PERMISSION_MANAGER) -> PERMISSION_MANAGER
            requestedPermissions.contains(LEGACY_PERMISSION_MANAGER) -> LEGACY_PERMISSION_MANAGER
            else -> null
        }
    }
}
