package moe.shizuku.manager.deviceowner

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.PersistableBundle
import android.provider.Settings
import android.util.Log
import moe.shizuku.manager.receiver.SheveryDeviceAdminReceiver
import moe.shizuku.manager.utils.Logger.LOGGER

object DeviceOwnerManager {

    private const val TAG = "DeviceOwnerManager"

    data class DelegatedScope(
        val scopeName: String,
        val label: String,
        val description: String
    )

    val ALL_SCOPES: List<DelegatedScope> = listOf(
        DelegatedScope(
            scopeName = DevicePolicyManager.DELEGATION_APP_RESTRICTIONS,
            label = "App Restrictions",
            description = "Allows configuring managed configurations and app restrictions"
        ),
        DelegatedScope(
            scopeName = DevicePolicyManager.DELEGATION_BLOCK_UNINSTALL,
            label = "Block Uninstall",
            description = "Allows preventing specified applications from being uninstalled"
        ),
        DelegatedScope(
            scopeName = DevicePolicyManager.DELEGATION_PERMISSION_GRANT,
            label = "Permission Granting",
            description = "Allows granting or revoking runtime permissions without user prompts"
        ),
        DelegatedScope(
            scopeName = DevicePolicyManager.DELEGATION_PACKAGE_ACCESS,
            label = "Package Access (Freeze)",
            description = "Allows hiding, unhiding, and suspending applications"
        ),
        DelegatedScope(
            scopeName = DevicePolicyManager.DELEGATION_ENABLE_SYSTEM_APP,
            label = "Enable System Apps",
            description = "Allows enabling pre-installed disabled system applications"
        )
    )

    fun getDpm(context: Context): DevicePolicyManager {
        return context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    }

    fun getAdminComponent(context: Context): ComponentName {
        return SheveryDeviceAdminReceiver.getComponentName(context)
    }

    fun isDeviceOwner(context: Context): Boolean {
        return try {
            val dpm = getDpm(context)
            dpm.isDeviceOwnerApp(context.packageName)
        } catch (_: Exception) {
            false
        }
    }

    fun isProfileOwner(context: Context): Boolean {
        return try {
            val dpm = getDpm(context)
            dpm.isProfileOwnerApp(context.packageName)
        } catch (_: Exception) {
            false
        }
    }

    fun isAdminActive(context: Context): Boolean {
        return try {
            val dpm = getDpm(context)
            dpm.isAdminActive(getAdminComponent(context))
        } catch (_: Exception) {
            false
        }
    }

    fun getAdbCommand(context: Context): String {
        val cn = getAdminComponent(context).flattenToShortString()
        return "dpm set-device-owner $cn"
    }

    fun enableAdbViaDpm(context: Context): Boolean {
        return try {
            val dpm = getDpm(context)
            val admin = getAdminComponent(context)
            dpm.setGlobalSetting(admin, Settings.Global.ADB_ENABLED, "1")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    dpm.setGlobalSetting(admin, "adb_wifi_enabled", "1")
                } catch (_: Exception) {}
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enable ADB via DPM", e)
            false
        }
    }

    fun getDelegatedScopes(context: Context, packageName: String): List<String> {
        return try {
            val dpm = getDpm(context)
            val admin = getAdminComponent(context)
            dpm.getDelegatedScopes(admin, packageName)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun setDelegatedScopes(context: Context, packageName: String, scopes: List<String>): Boolean {
        return try {
            val dpm = getDpm(context)
            val admin = getAdminComponent(context)
            dpm.setDelegatedScopes(admin, packageName, scopes)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set delegated scopes for $packageName", e)
            false
        }
    }

    fun setApplicationHidden(context: Context, packageName: String, hidden: Boolean): Boolean {
        return try {
            val dpm = getDpm(context)
            val admin = getAdminComponent(context)
            dpm.setApplicationHidden(admin, packageName, hidden)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set application hidden: $packageName", e)
            false
        }
    }

    fun setPermissionGrantState(
        context: Context,
        packageName: String,
        permission: String,
        grantState: Int
    ): Boolean {
        return try {
            val dpm = getDpm(context)
            val admin = getAdminComponent(context)
            dpm.setPermissionGrantState(admin, packageName, permission, grantState)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set permission grant state", e)
            false
        }
    }

    fun isStubPackage(context: Context, packageName: String): Boolean {
        if (packageName == "moe.shizuku.privileged.api") return true
        if (packageName == "com.rosan.dhizuku") {
            return try {
                val pi = context.packageManager.getPackageInfo(packageName, 0)
                pi.versionCode >= 1000000000
            } catch (_: Exception) {
                false
            }
        }
        return false
    }

    fun ensureAdminActive(context: Context, targetComponent: ComponentName): Boolean {
        val dpm = getDpm(context)
        if (dpm.isAdminActive(targetComponent)) return true

        try {
            val method = DevicePolicyManager::class.java.getMethod(
                "setActiveAdmin",
                ComponentName::class.java,
                Boolean::class.javaPrimitiveType
            )
            method.isAccessible = true
            method.invoke(dpm, targetComponent, true)
            if (dpm.isAdminActive(targetComponent)) {
                LOGGER.i("Successfully activated admin for ${targetComponent.flattenToString()} via setActiveAdmin(2 args)")
                return true
            }
        } catch (e: Throwable) {
            LOGGER.w("setActiveAdmin(2 args) failed: ${e.message}")
        }

        try {
            val method = DevicePolicyManager::class.java.getMethod(
                "setActiveAdmin",
                ComponentName::class.java,
                Boolean::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            method.isAccessible = true
            val userId = android.os.Process.myUserHandle().hashCode()
            method.invoke(dpm, targetComponent, true, userId)
            if (dpm.isAdminActive(targetComponent)) {
                LOGGER.i("Successfully activated admin for ${targetComponent.flattenToString()} via setActiveAdmin(3 args)")
                return true
            }
        } catch (e: Throwable) {
            LOGGER.w("setActiveAdmin(3 args) failed: ${e.message}")
        }

        return dpm.isAdminActive(targetComponent)
    }

    fun transferOwnership(context: Context, targetComponent: ComponentName): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return false
        }
        return try {
            val dpm = getDpm(context)
            val admin = getAdminComponent(context)
            ensureAdminActive(context, targetComponent)
            dpm.transferOwnership(admin, targetComponent, PersistableBundle())
            LOGGER.i("Transferred Device Ownership to ${targetComponent.flattenToString()}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to transfer ownership", e)
            LOGGER.e(e, "transferOwnership")
            false
        }
    }

    fun clearDeviceOwner(context: Context): Boolean {
        return try {
            val dpm = getDpm(context)
            val admin = getAdminComponent(context)
            dpm.clearDeviceOwnerApp(context.packageName)
            try {
                dpm.removeActiveAdmin(admin)
            } catch (e: Throwable) {
                LOGGER.w("removeActiveAdmin after clearDeviceOwner failed: ${e.message}")
            }
            LOGGER.i("Cleared Device Owner for ${context.packageName}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear device owner", e)
            LOGGER.e(e, "clearDeviceOwner")
            false
        }
    }

    fun isDeviceOwnerWhitelistEnabled(): Boolean {
        return moe.shizuku.manager.ShizukuSettings.isDeviceOwnerWhitelistEnabled()
    }

    fun setDeviceOwnerWhitelistEnabled(enabled: Boolean) {
        moe.shizuku.manager.ShizukuSettings.setDeviceOwnerWhitelistEnabled(enabled)
    }

    data class AdminAppInfo(
        val componentName: ComponentName,
        val packageName: String,
        val label: String,
        val icon: android.graphics.drawable.Drawable?,
        val isActiveAdmin: Boolean
    )

    data class DelegatedAppInfo(
        val packageName: String,
        val label: String,
        val icon: android.graphics.drawable.Drawable?,
        val scopes: List<String>,
        val isDeviceAdmin: Boolean
    )

    fun getEligibleAdminApps(context: Context): List<ApplicationInfo> {
        val pm = context.packageManager
        val adminReceivers = pm.queryBroadcastReceivers(
            android.content.Intent(DeviceAdminReceiver.ACTION_DEVICE_ADMIN_ENABLED),
            PackageManager.GET_META_DATA
        )
        val ownPackage = context.packageName
        return adminReceivers
            .mapNotNull { it.activityInfo?.applicationInfo }
            .filter { it.packageName != ownPackage && !isStubPackage(context, it.packageName) }
            .distinctBy { it.packageName }
    }

    fun getEligibleAdminAppsDetailed(context: Context): List<AdminAppInfo> {
        val pm = context.packageManager
        val dpm = getDpm(context)
        val receivers = pm.queryBroadcastReceivers(
            android.content.Intent(DeviceAdminReceiver.ACTION_DEVICE_ADMIN_ENABLED),
            PackageManager.GET_META_DATA
        )
        val ownPackage = context.packageName
        return receivers.mapNotNull { ri ->
            val ai = ri.activityInfo ?: return@mapNotNull null
            val appInfo = ai.applicationInfo ?: return@mapNotNull null
            if (ai.packageName == ownPackage || isStubPackage(context, ai.packageName)) return@mapNotNull null
            try {
                val adminInfo = android.app.admin.DeviceAdminInfo(context, ri)
                val cn = adminInfo.component
                val isActive = dpm.isAdminActive(cn)
                val label = adminInfo.loadLabel(pm)?.toString() ?: appInfo.loadLabel(pm).toString()
                val icon = adminInfo.loadIcon(pm) ?: appInfo.loadIcon(pm)
                AdminAppInfo(
                    componentName = cn,
                    packageName = ai.packageName,
                    label = label,
                    icon = icon,
                    isActiveAdmin = isActive
                )
            } catch (_: Exception) {
                val cn = ComponentName(ai.packageName, ai.name)
                val isActive = dpm.isAdminActive(cn)
                val label = appInfo.loadLabel(pm).toString()
                val icon = appInfo.loadIcon(pm)
                AdminAppInfo(
                    componentName = cn,
                    packageName = ai.packageName,
                    label = label,
                    icon = icon,
                    isActiveAdmin = isActive
                )
            }
        }.distinctBy { it.componentName }
    }

    fun getDelegationApps(context: Context, dhizukuOnly: Boolean = true): List<DelegatedAppInfo> {
        val pm = context.packageManager
        val ownPackage = context.packageName

        val adminReceivers = pm.queryBroadcastReceivers(
            android.content.Intent(DeviceAdminReceiver.ACTION_DEVICE_ADMIN_ENABLED),
            0
        )
        val adminPkgs = adminReceivers.mapNotNull { it.activityInfo?.packageName }.toSet()

        val dhizukuPermission = "com.rosan.dhizuku.permission.API"
        val dhizukuPermissionV2 = "com.rosan.dhizuku.permission.API_V2"

        val packages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
            .filter { it.packageName != ownPackage && !isStubPackage(context, it.packageName) }

        val filteredPackages = if (dhizukuOnly) {
            packages.filter { pi ->
                val requested = pi.requestedPermissions ?: emptyArray()
                val requestsDhizuku = requested.contains(dhizukuPermission) || requested.contains(dhizukuPermissionV2)
                val hasScopes = getDelegatedScopes(context, pi.packageName).isNotEmpty()
                val isGranted = moe.shizuku.manager.dhizuku.DhizukuAuthManager.isGranted(
                    context,
                    pi.applicationInfo?.uid ?: -1
                )
                requestsDhizuku || hasScopes || isGranted
            }
        } else {
            packages
        }

        return filteredPackages.mapNotNull { pi ->
            val app = pi.applicationInfo ?: return@mapNotNull null
            val scopes = getDelegatedScopes(context, app.packageName)
            val isAdmin = adminPkgs.contains(app.packageName)
            val label = app.loadLabel(pm).toString()
            val icon = try { app.loadIcon(pm) } catch (_: Throwable) { null }
            DelegatedAppInfo(
                packageName = app.packageName,
                label = label,
                icon = icon,
                scopes = scopes,
                isDeviceAdmin = isAdmin
            )
        }.sortedWith(
            compareByDescending<DelegatedAppInfo> { it.scopes.isNotEmpty() }
                .thenByDescending { it.isDeviceAdmin }
                .thenBy { it.label.lowercase() }
        )
    }
}
