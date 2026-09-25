package moe.shizuku.manager.dhizuku

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Parcel
import android.os.ServiceManager
import com.rosan.dhizuku.aidl.IDhizuku
import com.rosan.dhizuku.aidl.IDhizukuRemoteProcess
import com.rosan.dhizuku.aidl.IDhizukuUserServiceConnection
import com.rosan.dhizuku.shared.DhizukuVariables
import moe.shizuku.manager.deviceowner.DeviceOwnerManager
import moe.shizuku.manager.utils.Logger.LOGGER

class DhizukuProvider : ContentProvider() {

    private inner class DhizukuBinder : IDhizuku.Stub() {

        private fun isCallerAuthorized(): Boolean {
            val callingUid = Binder.getCallingUid()
            if (callingUid == android.os.Process.myUid()) return true
            val ctx = context ?: return false
            return DhizukuAuthManager.isGranted(ctx, callingUid)
        }

        override fun getVersionCode(): Int = 5

        override fun getVersionName(): String = "2.5.4"

        override fun isPermissionGranted(): Boolean = isCallerAuthorized()

        override fun remoteProcess(cmd: Array<out String>?, env: Array<out String>?, dir: String?): IDhizukuRemoteProcess? {
            if (!isCallerAuthorized()) throw SecurityException("Unauthorized UID ${Binder.getCallingUid()}")
            return null
        }

        override fun bindUserService(connection: IDhizukuUserServiceConnection?, bundle: Bundle?) {
            if (!isCallerAuthorized()) throw SecurityException("Unauthorized UID ${Binder.getCallingUid()}")
        }

        override fun unbindUserService(bundle: Bundle?) {
            if (!isCallerAuthorized()) throw SecurityException("Unauthorized UID ${Binder.getCallingUid()}")
        }

        override fun getDelegatedScopes(packageName: String?): Array<String> {
            val ctx = context ?: return emptyArray()
            if (packageName.isNullOrEmpty()) return emptyArray()
            return DeviceOwnerManager.getDelegatedScopes(ctx, packageName).toTypedArray()
        }

        override fun setDelegatedScopes(packageName: String?, scopes: Array<out String>?) {
            val callingUid = Binder.getCallingUid()
            val ctx = context ?: return
            if (!isCallerAuthorized()) {
                throw SecurityException("Dhizuku: Unauthorized setDelegatedScopes from UID $callingUid")
            }
            if (!packageName.isNullOrEmpty() && scopes != null) {
                DeviceOwnerManager.setDelegatedScopes(ctx, packageName, scopes.toList())
            }
        }

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == DhizukuVariables.TRANSACT_CODE_REMOTE_BINDER) {
                val callingUid = Binder.getCallingUid()
                val ctx = context ?: return false
                if (!isCallerAuthorized()) {
                    LOGGER.w("DhizukuProvider: Unauthorized remoteTransact call from UID $callingUid")
                    return false
                }

                val targetData = Parcel.obtain()
                try {
                    try {
                        data.enforceInterface(DhizukuVariables.BINDER_DESCRIPTOR)
                    } catch (_: SecurityException) {
                        data.setDataPosition(0)
                        try {
                            data.enforceInterface("com.rosan.dhizuku.aidl.IDhizuku")
                        } catch (_: SecurityException) {
                            data.setDataPosition(0)
                            try {
                                data.readInt()
                                data.readString()
                            } catch (_: Throwable) {
                                data.setDataPosition(0)
                            }
                        }
                    }
                    val targetBinder = data.readStrongBinder() ?: return false
                    val targetCode = data.readInt()
                    val targetFlags = data.readInt()
                    targetData.appendFrom(data, data.dataPosition(), data.dataAvail())
                    val id = Binder.clearCallingIdentity()
                    return try {
                        targetBinder.transact(targetCode, targetData, reply, targetFlags)
                    } finally {
                        Binder.restoreCallingIdentity(id)
                    }
                } finally {
                    targetData.recycle()
                }
            }

            // Fallback for v1 / legacy Dhizuku AIDL transactions
            if (code in FIRST_CALL_TRANSACTION..(FIRST_CALL_TRANSACTION + 3)) {
                var isV2 = true
                try {
                    data.enforceInterface("com.rosan.dhizuku.aidl.IDhizuku")
                } catch (_: SecurityException) {
                    data.setDataPosition(0)
                    try {
                        data.enforceInterface("com.rosan.dhizuku.IDhizuku")
                        isV2 = false
                    } catch (_: SecurityException) {}
                }
                when (code) {
                    FIRST_CALL_TRANSACTION + 0 -> { // getVersionCode / getVersion
                        reply?.writeNoException()
                        reply?.writeInt(if (isV2) 5 else 1)
                        return true
                    }
                    FIRST_CALL_TRANSACTION + 1 -> { // getVersionName / getBinder
                        reply?.writeNoException()
                        if (isV2) {
                            reply?.writeString("2.5.4")
                        } else {
                            val binder = if (isCallerAuthorized()) {
                                try {
                                    ServiceManager.getService(android.content.Context.DEVICE_POLICY_SERVICE)
                                } catch (_: Throwable) {
                                    null
                                }
                            } else null
                            reply?.writeStrongBinder(binder)
                        }
                        return true
                    }
                    FIRST_CALL_TRANSACTION + 2 -> { // isPermissionGranted
                        reply?.writeNoException()
                        reply?.writeInt(if (isCallerAuthorized()) 1 else 0)
                        return true
                    }
                    FIRST_CALL_TRANSACTION + 3 -> {
                        reply?.writeNoException()
                        reply?.writeBundle(Bundle())
                        return true
                    }
                }
            }

            return super.onTransact(code, data, reply, flags)
        }
    }

    private val binder = DhizukuBinder()

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
        val ctx = context
        if (DhizukuVariables.PROVIDER_METHOD_CLIENT == method) {
            val bundle = Bundle()
            bundle.putBinder(DhizukuVariables.PARAM_DHIZUKU_BINDER, binder)
            if (ctx != null) {
                val ownerComponent = DeviceOwnerManager.getAdminComponent(ctx)
                bundle.putParcelable(DhizukuVariables.PARAM_COMPONENT, ownerComponent)
            }
            return bundle
        }
        if ("getBinder" == method) {
            val bundle = Bundle()
            bundle.putBinder("binder", binder)
            return bundle
        }
        if ("get_owner_component" == method || "getOwnerComponent" == method) {
            if (ctx != null) {
                val bundle = Bundle()
                val ownerComponent = DeviceOwnerManager.getAdminComponent(ctx)
                bundle.putParcelable("component", ownerComponent)
                bundle.putParcelable("owner_component", ownerComponent)
                return bundle
            }
        }
        if ("is_permission_granted" == method || "isPermissionGranted" == method) {
            if (ctx != null) {
                val bundle = Bundle()
                val callingUid = Binder.getCallingUid()
                bundle.putBoolean("granted", DhizukuAuthManager.isGranted(ctx, callingUid))
                return bundle
            }
        }
        if ("get_version_code" == method || "getVersionCode" == method) {
            val bundle = Bundle()
            bundle.putInt("version_code", 5)
            return bundle
        }
        return null
    }
}
