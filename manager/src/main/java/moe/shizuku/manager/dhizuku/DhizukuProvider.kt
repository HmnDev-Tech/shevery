package moe.shizuku.manager.dhizuku

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Parcel
import com.rosan.dhizuku.shared.DhizukuVariables
import moe.shizuku.manager.deviceowner.DeviceOwnerManager
import moe.shizuku.manager.utils.Logger.LOGGER

class DhizukuProvider : ContentProvider() {

    private inner class DhizukuBinder : Binder() {
        override fun getInterfaceDescriptor(): String = "com.rosan.dhizuku.aidl.IDhizuku"

        private fun isCallerAuthorized(): Boolean {
            val callingUid = Binder.getCallingUid()
            val ctx = context ?: return false
            return DhizukuAuthManager.isGranted(ctx, callingUid)
        }

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            val callingUid = Binder.getCallingUid()
            val ctx = context ?: return false

            if (!isCallerAuthorized()) {
                LOGGER.w("DhizukuProvider: Unauthorized transact call from UID $callingUid")
                return false
            }

            if (code == DhizukuVariables.TRANSACT_CODE_REMOTE_BINDER) {
                data.setDataPosition(0)
                data.readString() // skip interface descriptor token
                val targetBinder = data.readStrongBinder() ?: return false
                val targetCode = data.readInt()
                val targetFlags = data.readInt()
                return targetBinder.transact(targetCode, data, reply, targetFlags)
            }

            if (code in FIRST_CALL_TRANSACTION..(FIRST_CALL_TRANSACTION + 20)) {
                try {
                    data.enforceInterface("com.rosan.dhizuku.aidl.IDhizuku")
                } catch (_: SecurityException) {
                    data.setDataPosition(0)
                    try {
                        data.enforceInterface("com.rosan.dhizuku.IDhizuku")
                    } catch (_: SecurityException) {}
                }
                when (code) {
                    FIRST_CALL_TRANSACTION + 0 -> { // getVersionCode
                        reply?.writeNoException()
                        reply?.writeInt(5)
                        return true
                    }
                    FIRST_CALL_TRANSACTION + 1 -> { // getVersionName
                        reply?.writeNoException()
                        reply?.writeString("2.5.4")
                        return true
                    }
                    FIRST_CALL_TRANSACTION + 2 -> { // isPermissionGranted
                        reply?.writeNoException()
                        reply?.writeInt(if (isCallerAuthorized()) 1 else 0)
                        return true
                    }
                    FIRST_CALL_TRANSACTION + 15 -> { // getDelegatedScopes
                        val pkg = data.readString()
                        val scopes = if (!pkg.isNullOrEmpty()) {
                            DeviceOwnerManager.getDelegatedScopes(ctx, pkg)
                        } else emptyList()
                        reply?.writeNoException()
                        reply?.writeStringArray(scopes.toTypedArray())
                        return true
                    }
                    FIRST_CALL_TRANSACTION + 16 -> { // setDelegatedScopes
                        val pkg = data.readString()
                        val scopes = data.createStringArray()?.toList() ?: emptyList()
                        if (!pkg.isNullOrEmpty()) {
                            DeviceOwnerManager.setDelegatedScopes(ctx, pkg, scopes)
                        }
                        reply?.writeNoException()
                        return true
                    }
                }
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
