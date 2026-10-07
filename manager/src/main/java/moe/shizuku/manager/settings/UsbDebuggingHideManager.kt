package moe.shizuku.manager.settings

import android.app.AppOpsManager
import android.content.Context
import android.os.Process
import android.provider.Settings
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.adb.AdbArm

object UsbDebuggingHideManager {

    private const val KEY_TARGET_PACKAGES = "usb_debugging_hide_packages"
    private const val KEY_ORIGINAL_ENABLED = "usb_debugging_hide_original_enabled"

    fun targetPackages(): Set<String> =
        ShizukuSettings.getPreferences()
            .getStringSet(KEY_TARGET_PACKAGES, emptySet())
            .orEmpty()
            .toSet()

    fun hasPendingRestore(): Boolean =
        ShizukuSettings.getPreferences().contains(KEY_ORIGINAL_ENABLED)

    fun setTarget(context: Context, packageName: String, enabled: Boolean) {
        val targets = targetPackages().toMutableSet()
        if (enabled) targets.add(packageName) else targets.remove(packageName)
        ShizukuSettings.getPreferences().edit()
            .putStringSet(KEY_TARGET_PACKAGES, targets)
            .apply()
        UsbDebuggingHideService.refresh(context)
    }

    fun retainInstalledTargets(context: Context, installedPackages: Set<String>) {
        val targets = targetPackages()
        val retained = targets intersect installedPackages
        if (targets == retained) return
        ShizukuSettings.getPreferences().edit()
            .putStringSet(KEY_TARGET_PACKAGES, retained)
            .apply()
        UsbDebuggingHideService.refresh(context)
    }

    fun hasUsageAccess(context: Context): Boolean = runCatching {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        ) == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    fun hide(context: Context): Boolean = runCatching {
        val preferences = ShizukuSettings.getPreferences()
        if (!preferences.contains(KEY_ORIGINAL_ENABLED)) {
            val enabled = Settings.Global.getInt(
                context.contentResolver,
                Settings.Global.ADB_ENABLED,
                0
            )
            if (enabled == 0) return@runCatching true
            if (!preferences.edit().putInt(KEY_ORIGINAL_ENABLED, enabled).commit()) {
                return@runCatching false
            }
        }

        Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 0 ||
            AdbArm.setUsbDebuggingEnabled(context, false)
    }.getOrDefault(false)

    fun restore(context: Context): Boolean = runCatching {
        val preferences = ShizukuSettings.getPreferences()
        if (!preferences.contains(KEY_ORIGINAL_ENABLED)) return@runCatching true

        val original = preferences.getInt(KEY_ORIGINAL_ENABLED, 0)
        val current = Settings.Global.getInt(
            context.contentResolver,
            Settings.Global.ADB_ENABLED,
            0
        )
        if (current == 0 && original != 0 && !AdbArm.setUsbDebuggingEnabled(context, true)) {
            return@runCatching false
        }

        preferences.edit().remove(KEY_ORIGINAL_ENABLED).commit()
    }.getOrDefault(false)
}
