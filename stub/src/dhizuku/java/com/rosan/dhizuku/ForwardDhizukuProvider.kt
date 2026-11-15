package com.rosan.dhizuku

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle

class ForwardDhizukuProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
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
    }
}
