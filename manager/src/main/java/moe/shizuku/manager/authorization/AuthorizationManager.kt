package moe.shizuku.manager.authorization

import android.content.pm.PackageInfo
import android.os.Parcel
import rikka.shizuku.server.ServerConstants
import rikka.parcelablelist.ParcelableListSlice
import rikka.shizuku.Shizuku

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

    /**
     * Client applications, resolved by the bundled server through the Shevery API
     * (native `com.hamondev.shevery.permission.API` clients and legacy clients served by the
     * Shizuku stub). The manager never inspects the legacy permission itself.
     */
    fun getPackages(): List<PackageInfo> = getApplications(-1)

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
        return (Shizuku.getFlagsForUid(uid, MASK_PERMISSION) and FLAG_ALLOWED) == FLAG_ALLOWED
    }

    fun grant(packageName: String, uid: Int) {
        sessionGrantedUids.remove(uid)
        Shizuku.updateFlagsForUid(uid, MASK_PERMISSION, FLAG_ALLOWED)
    }

    fun revoke(packageName: String, uid: Int) {
        sessionGrantedUids.remove(uid)
        Shizuku.updateFlagsForUid(uid, MASK_PERMISSION, 0)
    }
}
