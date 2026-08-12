package com.appathy.tubecut

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri

/**
 * EDIT_PLAN.md 6.1 の ContentProvider 方式。
 * content://com.appathy.tubecut.result/results
 */
class ResultProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?
    ): Cursor {
        val cur = MatrixCursor(arrayOf("workId", "json"))
        val ctx = context ?: return cur
        val store = Store(ctx)
        val wanted = uri.pathSegments.getOrNull(1)
        for (p in store.projects) {
            if (p.workId.isBlank()) continue
            if (p.outputUri.isBlank()) continue
            if (wanted != null && p.workId != wanted) continue
            cur.addRow(arrayOf(p.workId, Bridge.buildResult(p)))
        }
        return cur
    }

    override fun getType(uri: Uri): String = "vnd.android.cursor.dir/vnd.appathy.result"

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun update(
        uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?
    ): Int = 0

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
