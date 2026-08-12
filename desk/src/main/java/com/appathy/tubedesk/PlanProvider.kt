package com.appathy.tubedesk

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri

/**
 * EDIT_PLAN.md 6.1 の ContentProvider 方式。
 * 両アプリが同じ鍵で署名されるため signature レベルの権限で守れる。
 *
 * content://com.appathy.tubedesk.plan/plans        全件
 * content://com.appathy.tubedesk.plan/plans/<id>   1件
 */
class PlanProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?
    ): Cursor {
        val cur = MatrixCursor(arrayOf("workId", "title", "status", "json"))
        val ctx = context ?: return cur
        val store = Store(ctx)
        val wanted = uri.pathSegments.getOrNull(1)
        for (p in store.projects) {
            if (wanted != null && p.id != wanted) continue
            if (wanted == null && p.status == Project.S_IDEA) continue
            cur.addRow(arrayOf(p.id, p.title, p.status, Bridge.buildPlan(p)))
        }
        return cur
    }

    /** Cut からの結果書き戻し。values に workId と json を入れる */
    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        val ctx = context ?: return null
        val json = values?.getAsString("json") ?: return null
        val store = Store(ctx)
        if (!Bridge.applyResultTo(store, json)) return null
        return uri
    }

    override fun getType(uri: Uri): String = "vnd.android.cursor.dir/vnd.appathy.plan"

    override fun update(
        uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?
    ): Int = 0

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
