package moe.shizuku.manager.deviceowner

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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

    fun transferOwnership(context: Context, targetComponent: ComponentName): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return false
        }
        return try {
            val dpm = getDpm(context)
            val admin = getAdminComponent(context)
            dpm.transferOwnership(admin, targetComponent, Bundle())
            LOGGER.i("Transferred Device Ownership to ${targetComponent.flattenToString()}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to transfer ownership", e)
            LOGGER.e(e, "transferOwnership")
            false
        }
    }

    fun getEligibleAdminApps(context: Context): List<ApplicationInfo> {
        val pm = context.packageManager
        val adminReceivers = pm.queryBroadcastReceivers(
            android.content.Intent(DeviceAdminReceiver.ACTION_DEVICE_ADMIN_ENABLED),
            PackageManager.GET_META_DATA
        )
        val ownPackage = context.packageName
        return adminReceivers
            .mapNotNull { it.activityInfo?.applicationInfo }
            .filter { it.packageName != ownPackage }
            .distinctBy { it.packageName }
    }
}
