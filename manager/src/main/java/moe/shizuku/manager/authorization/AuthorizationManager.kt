package moe.shizuku.manager.authorization

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Parcel
import com.hamondev.shevery.api.SheveryApiConstants
import moe.shizuku.manager.BuildConfig
import moe.shizuku.manager.Manifest
import moe.shizuku.manager.compat.StubManager
import moe.shizuku.manager.utils.Logger.LOGGER
import moe.shizuku.manager.utils.ShizukuSystemApis
import rikka.parcelablelist.ParcelableListSlice
import rikka.shizuku.Shizuku
import rikka.shizuku.server.ServerConstants
import java.util.ArrayList

object AuthorizationManager {

    private const val FLAG_ALLOWED = 1 shl 1
    private const val FLAG_DENIED = 1 shl 2
    private const val MASK_PERMISSION = FLAG_ALLOWED or FLAG_DENIED

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

    fun getPackages(): List<PackageInfo> {
        val packages: MutableList<PackageInfo> = ArrayList()
        if (Shizuku.isPreV11() || (Shizuku.getVersion() == 11 && Shizuku.getServerPatchVersion() < 3)) {
            val allPackages: MutableList<PackageInfo> = ArrayList()
            for (user in ShizukuSystemApis.getUsers(useCache = false)) {
                try {
                    allPackages.addAll(ShizukuSystemApis.getInstalledPackages((PackageManager.GET_META_DATA or PackageManager.GET_PERMISSIONS).toLong(), user.id))
                } catch (e: Throwable) {
                    LOGGER.w(e, "getInstalledPackages")
                }
            }
            for (pi in allPackages) {
                if (BuildConfig.APPLICATION_ID == pi.packageName) continue
                if (StubManager.STUB_PACKAGE == pi.packageName) continue
                val metaData = pi.applicationInfo?.metaData
                val isSheveryClient = metaData?.getBoolean(SheveryApiConstants.META_DATA_CLIENT_SUPPORT) == true
                        || pi.requestedPermissions?.contains(Manifest.permission.PRIVILEGED_API) == true
                val isLegacyShizukuClient = metaData?.getBoolean(SheveryApiConstants.LEGACY_META_DATA_CLIENT_SUPPORT) == true
                        && pi.requestedPermissions?.contains(Manifest.permission.API_V23) == true
                if (!isSheveryClient && !isLegacyShizukuClient) continue

                packages.add(pi)
            }
        } else {
            packages.addAll(getApplications(-1))
        }
        return packages
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
}
