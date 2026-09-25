package moe.shizuku.manager.dhizuku

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.Parcel
import com.rosan.dhizuku.aidl.IDhizuku
import com.rosan.dhizuku.aidl.IDhizukuRemoteProcess
import com.rosan.dhizuku.aidl.IDhizukuUserServiceConnection
import com.rosan.dhizuku.shared.DhizukuVariables
import moe.shizuku.manager.deviceowner.DeviceOwnerManager
import moe.shizuku.manager.utils.Logger.LOGGER

class DhizukuProvider : ContentProvider() {

    private inner class DhizukuBinder : IDhizuku.Stub() {

        override fun getVersionCode(): Int = 5

        override fun getVersionName(): String = "2.5.4"

        override fun isPermissionGranted(): Boolean {
            val callingUid = Binder.getCallingUid()
            val ctx = context ?: return false
            return DhizukuAuthManager.isGranted(ctx, callingUid)
        }

        override fun getDelegatedScopes(packageName: String?): Array<String> {
            val ctx = context ?: return emptyArray()
            if (packageName.isNullOrEmpty()) return emptyArray()
            return DeviceOwnerManager.getDelegatedScopes(ctx, packageName).toTypedArray()
        }

        override fun setDelegatedScopes(packageName: String?, scopes: Array<out String>?) {
            val ctx = context ?: return
            if (packageName.isNullOrEmpty() || scopes == null) return
            DeviceOwnerManager.setDelegatedScopes(ctx, packageName, scopes.toList())
        }

        override fun remoteProcess(
            cmd: Array<out String>?,
            env: Array<out String>?,
            dir: String?
        ): IDhizukuRemoteProcess? = null

        override fun bindUserService(
            connection: IDhizukuUserServiceConnection?,
            bundle: Bundle?
        ) {}

        override fun unbindUserService(bundle: Bundle?) {}

        override fun unbindUserServiceByConnection(
            connection: IDhizukuUserServiceConnection?,
            bundle: Bundle?
        ) {}

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            val callingUid = Binder.getCallingUid()
            val ctx = context
            if (ctx != null && !DhizukuAuthManager.isGranted(ctx, callingUid)) {
                LOGGER.w("DhizukuProvider: Unauthorized transact call from UID $callingUid")
                return false
            }

            if (code == DhizukuVariables.TRANSACT_CODE_REMOTE_BINDER) {
                data.setDataPosition(0)
                data.readString() // skip interface token
                val targetBinder = data.readStrongBinder() ?: return false
                val targetCode = data.readInt()
                val targetFlags = data.readInt()
                return targetBinder.transact(targetCode, data, reply, targetFlags)
            }

            return super.onTransact(code, data, reply, flags)
        }
    }

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (DhizukuVariables.PROVIDER_METHOD_CLIENT == method) {
            val bundle = Bundle()
            bundle.putBinder(DhizukuVariables.PARAM_DHIZUKU_BINDER, DhizukuBinder())
            return bundle
        }
        if ("getBinder" == method) {
            val bundle = Bundle()
            bundle.putBinder("binder", DhizukuBinder())
            return bundle
        }
        return null
    }
}
