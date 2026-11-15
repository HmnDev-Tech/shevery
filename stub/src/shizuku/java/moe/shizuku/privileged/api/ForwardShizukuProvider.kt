package moe.shizuku.privileged.api

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.Process
import moe.shizuku.api.BinderContainer

/**
 * Compatibility ContentProvider inside the Shizuku Stub (`moe.shizuku.privileged.api`).
 *
 * Offloads legacy `moe.shizuku.privileged.api.shizuku` and `moe.shizuku.privileged.api.connector`
 * provider authorities from the main Shevery APK and forwards binder/connector requests to
 * `com.hamondev.shevery`.
 */
class ForwardShizukuProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method != METHOD_REQUEST_BINDER) {
            enforceCallingUid(Binder.getCallingUid(), Process.myUid())
        }
        val ctx = context ?: return null
        extras?.classLoader = BinderContainer::class.java.classLoader
        val forwardExtras = (extras?.let(::Bundle) ?: Bundle()).apply {
            putString("com.hamondev.shevery.intent.extra.PERMISSION_CHANNEL", "shizuku_stub")
            putString("com.hamondev.shevery.intent.extra.REQUESTED_PERMISSION", "moe.shizuku.manager.permission.API_V23")
        }

        val targetAuthorities = arrayOf(
            SHEVERY_PROVIDER_AUTHORITY,
            SHEVERY_LEGACY_PROVIDER_AUTHORITY
        )
        for (authority in targetAuthorities) {
            try {
                val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ctx.contentResolver.call(Uri.parse("content://$authority"), method, arg, forwardExtras)
                } else {
                    ctx.contentResolver.call(authority, method, arg, forwardExtras)
                }
                if (result != null) {
                    return result
                }
            } catch (_: Throwable) {
            }
        }
        return null
    }

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? {
        val ctx = context ?: return null
        return try {
            val targetUri = Uri.parse("content://$SHEVERY_CONNECTOR_AUTHORITY")
            ctx.contentResolver.query(targetUri, projection, selection, selectionArgs, sortOrder)
        } catch (_: Throwable) {
            null
        }
    }

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
        const val SHEVERY_PACKAGE = "com.hamondev.shevery"
        const val SHEVERY_PROVIDER_AUTHORITY = "$SHEVERY_PACKAGE.shevery"
        const val SHEVERY_LEGACY_PROVIDER_AUTHORITY = "$SHEVERY_PACKAGE.shizuku"
        const val SHEVERY_CONNECTOR_AUTHORITY = "$SHEVERY_PACKAGE.connector"
        const val METHOD_REQUEST_BINDER = "requestBinder"

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
