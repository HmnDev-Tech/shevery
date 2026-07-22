package moe.shizuku.manager.connector

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import moe.shizuku.manager.module.ModuleSettings
import moe.shizuku.manager.starter.Starter

class ShizukuConnectorProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        context?.let {
            moe.shizuku.manager.ShizukuSettings.initialize(it)
        }
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        if (!ModuleSettings.isConnectorEnabled()) {
            return null
        }

        val cursor = MatrixCursor(arrayOf("command"))
        cursor.addRow(arrayOf(Starter.internalCommand))
        return cursor
    }

    override fun getType(uri: Uri): String? {
        return null
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        return null
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        return 0
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        return 0
    }
}
