package moe.shizuku.manager.authorization

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Parcel
import com.hamondev.shevery.api.SheveryApiConstants
import moe.shizuku.manager.BuildConfig
import moe.shizuku.manager.Manifest
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.compat.StubManager
import moe.shizuku.manager.utils.Logger.LOGGER
import moe.shizuku.manager.utils.ShizukuSystemApis
import rikka.parcelablelist.ParcelableListSlice
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuApiConstants.REQUEST_PERMISSION_REPLY_ALLOWED
import rikka.shizuku.ShizukuApiConstants.REQUEST_PERMISSION_REPLY_IS_ONETIME
import rikka.shizuku.server.ServerConstants
import java.util.ArrayList

object AuthorizationManager {

    private const val FLAG_ALLOWED = 1 shl 1
    private const val FLAG_DENIED = 1 shl 2
    private const val MASK_PERMISSION = FLAG_ALLOWED or FLAG_DENIED
    private const val KEY_MANUAL_PACKAGES = "manual_added_packages"

    fun getManualPackages(): Set<String> {
        return ShizukuSettings.getPreferences()
            .getStringSet(KEY_MANUAL_PACKAGES, emptySet())
            .orEmpty()
            .toSet()
    }

    fun isManualPackage(packageName: String): Boolean {
        return getManualPackages().contains(packageName)
    }

    fun addManualPackage(packageName: String) {
        val pkg = packageName.trim()
        if (pkg.isEmpty()) return
        val updated = LinkedHashSet(getManualPackages())
        updated.add(pkg)
        ShizukuSettings.getPreferences()
            .edit()
            .putStringSet(KEY_MANUAL_PACKAGES, updated)
            .apply()
    }

    fun removeManualPackage(packageName: String) {
        val pkg = packageName.trim()
        if (pkg.isEmpty()) return
        val updated = LinkedHashSet(getManualPackages())
        if (updated.remove(pkg)) {
            ShizukuSettings.getPreferences()
                .edit()
                .putStringSet(KEY_MANUAL_PACKAGES, updated)
                .apply()
        }
    }

    private fun getApplications(userId: Int): List<PackageInfo> {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken("moe.shizuku.server.IShizukuService")
            data.writeInt(userId)
            try {
                Shizuku.getBinder()!!.transact(ServerConstants.BINDER_TRANSACTION_getApplications, data, reply, 0)
            } catch (e: Throwable) {
                throw RuntimeException(e)
            }
            reply.readException()
            @Suppress("UNCHECKED_CAST")
            (ParcelableListSlice.CREATOR.createFromParcel(reply) as ParcelableListSlice<PackageInfo>).list!!
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    fun resolvePackagePermission(pi: PackageInfo?): String? {
        val perms = pi?.requestedPermissions ?: return null
        if (perms.contains(Manifest.permission.PRIVILEGED_API)) {
            return Manifest.permission.PRIVILEGED_API
        }
        if (perms.contains(Manifest.permission.API_V23)) {
            return Manifest.permission.API_V23
        }
        return null
    }

    private fun resolveTargetPermission(packageName: String, userId: Int): String {
        val pi = runCatching {
            ShizukuSystemApis.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS.toLong(), userId)
        }.getOrNull()
        return resolvePackagePermission(pi) ?: Manifest.permission.PRIVILEGED_API
    }

    fun isClientPackage(pi: PackageInfo?): Boolean {
        if (pi == null) return false
        val metaData = pi.applicationInfo?.metaData
        val isSheveryClient = metaData?.getBoolean(SheveryApiConstants.META_DATA_CLIENT_SUPPORT) == true
                || pi.requestedPermissions?.contains(Manifest.permission.PRIVILEGED_API) == true
        val isLegacyShizukuClient = metaData?.getBoolean(SheveryApiConstants.LEGACY_META_DATA_CLIENT_SUPPORT) == true
                || pi.requestedPermissions?.contains(Manifest.permission.API_V23) == true
        return isSheveryClient || isLegacyShizukuClient
    }

    data class AppSuggestion(
        val packageName: String,
        val label: String,
        val uid: Int
    )

    fun findAppSuggestions(query: String, maxResults: Int = 5): List<AppSuggestion> {
        val trimmed = query.trim()
        if (trimmed.length < 2) return emptyList()
        val localPm = runCatching { moe.shizuku.manager.application.packageManager }.getOrNull() ?: return emptyList()
        val apps = runCatching { localPm.getInstalledApplications(0) }.getOrDefault(emptyList())
        val result = mutableListOf<AppSuggestion>()
        for (app in apps) {
            if (app.packageName == BuildConfig.APPLICATION_ID) continue
            if (app.packageName == StubManager.STUB_PACKAGE) continue
            if (app.packageName == "com.rosan.dhizuku") continue
            val pkg = app.packageName
            val label = runCatching { app.loadLabel(localPm).toString() }.getOrDefault(pkg).ifBlank { pkg }
            if (label.contains(trimmed, ignoreCase = true) || pkg.contains(trimmed, ignoreCase = true)) {
                result.add(AppSuggestion(pkg, label, app.uid))
                if (result.size >= maxResults) break
            }
        }
        return result
    }

    fun getPackages(): List<PackageInfo> {
        val seen = LinkedHashMap<String, PackageInfo>()
        val pmFlags = PackageManager.GET_META_DATA or PackageManager.GET_PERMISSIONS
        val flagsLong = pmFlags.toLong()
        val manualPackages = getManualPackages()

        fun addPackage(pi: PackageInfo?) {
            val appInfo = pi?.applicationInfo ?: return
            val pkg = pi.packageName ?: return
            if (BuildConfig.APPLICATION_ID == pkg) return
            if (StubManager.STUB_PACKAGE == pkg) return
            if ("com.rosan.dhizuku" == pkg) return
            val uid = appInfo.uid

            // Only include apps that the user explicitly added or that have non-zero permission flags
            val isManual = manualPackages.contains(pkg)
            val isSession = isOneTime(uid)
            val hasFlags = runCatching {
                !Shizuku.isPreV11() && Shizuku.getFlagsForUid(uid, MASK_PERMISSION) != 0
            }.getOrDefault(false)
            val isGranted = runCatching { granted(pkg, uid) }.getOrDefault(false)

            if (!isManual && !isSession && !hasFlags && !isGranted) {
                return
            }

            val key = "$pkg:$uid"
            if (!seen.containsKey(key)) {
                seen[key] = pi
            }
        }

        val localPm = runCatching { moe.shizuku.manager.application.packageManager }.getOrNull()

        // 1. Add packages explicitly added by the user
        for (pkg in manualPackages) {
            val pi = runCatching {
                localPm?.getPackageInfo(pkg, pmFlags)
            }.getOrNull() ?: runCatching {
                ShizukuSystemApis.getPackageInfo(pkg, flagsLong, 0)
            }.getOrNull()
            addPackage(pi)
        }

        // 2. Add session-granted packages
        for (uid in sessionGrantedUids) {
            val pkgs = runCatching { localPm?.getPackagesForUid(uid) }.getOrNull().orEmpty()
            for (pkg in pkgs) {
                val pi = runCatching { localPm?.getPackageInfo(pkg, pmFlags) }.getOrNull()
                addPackage(pi)
            }
        }

        // 3. Query server getApplications(-1), but strictly filter only privileged entries
        if (!Shizuku.isPreV11() && !(Shizuku.getVersion() == 11 && Shizuku.getServerPatchVersion() < 3)) {
            try {
                for (pi in getApplications(-1)) {
                    addPackage(pi)
                }
            } catch (e: Throwable) {
                LOGGER.w(e, "getApplications")
            }
        }

        return ArrayList(seen.values)
    }

    // Tracks UIDs granted for this session only (one-time)
    private val sessionGrantedUids = java.util.concurrent.ConcurrentHashMap.newKeySet<Int>()

    fun isOneTime(uid: Int): Boolean {
        return sessionGrantedUids.contains(uid)
    }

    fun markOneTime(uid: Int, onetime: Boolean) {
        if (onetime) {
            sessionGrantedUids.add(uid)
        } else {
            sessionGrantedUids.remove(uid)
        }
    }

    fun granted(packageName: String, uid: Int): Boolean {
        if (sessionGrantedUids.contains(uid)) return true
        return if (Shizuku.isPreV11()) {
            val userId = uid / 100000
            val targetPerm = resolveTargetPermission(packageName, userId)
            ShizukuSystemApis.checkPermission(targetPerm, packageName, userId) == PackageManager.PERMISSION_GRANTED
        } else {
            (Shizuku.getFlagsForUid(uid, MASK_PERMISSION) and FLAG_ALLOWED) == FLAG_ALLOWED
        }
    }

    /**
     * Grants access for the current server session only. The server applies the flag to the
     * live client records but must not persist it to the config, so a restart forgets it.
     */
    fun grantSessionOnly(packageName: String, uid: Int, allowed: Boolean = true) {
        if (allowed) {
            sessionGrantedUids.add(uid)
        } else {
            sessionGrantedUids.remove(uid)
        }

        if (Shizuku.isPreV11()) {
            val userId = uid / 100000
            val targetPerm = resolveTargetPermission(packageName, userId)
            if (allowed) {
                ShizukuSystemApis.grantRuntimePermission(packageName, targetPerm, userId)
            } else {
                ShizukuSystemApis.revokeRuntimePermission(packageName, targetPerm, userId)
            }
            return
        }

        val data = Bundle()
        data.putBoolean(REQUEST_PERMISSION_REPLY_ALLOWED, allowed)
        data.putBoolean(REQUEST_PERMISSION_REPLY_IS_ONETIME, true)
        Shizuku.dispatchPermissionConfirmationResult(uid, -1, -1, data)
    }

    fun grant(packageName: String, uid: Int) {
        sessionGrantedUids.remove(uid)
        if (Shizuku.isPreV11()) {
            val userId = uid / 100000
            val targetPerm = resolveTargetPermission(packageName, userId)
            ShizukuSystemApis.grantRuntimePermission(packageName, targetPerm, userId)
        } else {
            Shizuku.updateFlagsForUid(uid, MASK_PERMISSION, FLAG_ALLOWED)
        }
    }

    fun revoke(packageName: String, uid: Int) {
        sessionGrantedUids.remove(uid)
        if (Shizuku.isPreV11()) {
            val userId = uid / 100000
            val targetPerm = resolveTargetPermission(packageName, userId)
            ShizukuSystemApis.revokeRuntimePermission(packageName, targetPerm, userId)
        } else {
            Shizuku.updateFlagsForUid(uid, MASK_PERMISSION, 0)
        }
    }

    fun deny(packageName: String, uid: Int) {
        sessionGrantedUids.remove(uid)
        if (Shizuku.isPreV11()) {
            val userId = uid / 100000
            val targetPerm = resolveTargetPermission(packageName, userId)
            ShizukuSystemApis.revokeRuntimePermission(packageName, targetPerm, userId)
        } else {
            Shizuku.updateFlagsForUid(uid, MASK_PERMISSION, FLAG_DENIED)
        }
    }
}
