package com.rosan.dhizuku

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.Process

class ForwardDhizukuProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method == METHOD_SEND_BINDER || method == METHOD_SEND_USER_SERVICE) {
            enforceCallingUid(Binder.getCallingUid(), Process.myUid())
            return null
        }
        try {
            val ctx = context ?: return null
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val uri = Uri.parse("content://$SHEVERY_AUTHORITY")
                ctx.contentResolver.call(uri, method, arg, extras)
            } else {
                ctx.contentResolver.call(SHEVERY_AUTHORITY, method, arg, extras)
            }
        } catch (_: Throwable) {
        }
        return null
    }

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?
    ): Int = 0

    companion object {
        const val SHEVERY_AUTHORITY = "com.hamondev.shevery.dhizuku_server.provider"
        const val METHOD_SEND_BINDER = "sendBinder"
        const val METHOD_SEND_USER_SERVICE = "sendUserService"

        @JvmStatic
        fun isTrustedCallingUid(callingUid: Int, myUid: Int): Boolean =
            callingUid == 0 || callingUid == 2000 || callingUid == myUid

        @JvmStatic
        fun enforceCallingUid(callingUid: Int, myUid: Int) {
            if (!isTrustedCallingUid(callingUid, myUid)) {
                throw SecurityException("Unauthorized binder delivery from uid=$callingUid")
            }
        }
    }
}
