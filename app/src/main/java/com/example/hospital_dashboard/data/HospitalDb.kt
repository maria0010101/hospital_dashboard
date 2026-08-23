package com.example.hospital_dashboard.data

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import java.io.File

/**
 * 醫院業務資料庫：匯入 xlsx 各工作表並提供儀表板查詢。
 * 資料表結構與 Python 版 hospital_data_tool.py 一致(全部 TEXT 欄位)。
 */
class HospitalDb(context: Context) {

    private val db: SQLiteDatabase =
        context.openOrCreateDatabase(DB_NAME, Context.MODE_PRIVATE, null)

    init {
        // 首次開啟即建立 meta 表(尚未匯入時查詢用)
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS _app_meta (key TEXT PRIMARY KEY, value TEXT)"
        )
    }

    // ── 匯入 ──────────────────────────────────────────────
    /**
     * 匯入活頁簿中 7 個目標工作表(整批單一交易，失敗自動回滾)。
     * onProgress: (工作表名稱, 該表筆數, 目前第幾個表, 總表數)
     */
    fun importWorkbook(
        book: XlsxReader.Book,
        updateDateDisplay: String?,
        sourceFile: String,
        onProgress: (String, Int, Int, Int) -> Unit
    ) {
        db.beginTransaction()
        try {
            val total = SheetConfigs.ALL.size
            var idx = 0
            for (config in SheetConfigs.ALL) {
                idx++
                val rows = book.readRows(config.sheet)          // 含表頭
                    .drop(1)                                     // 捨棄表頭列
                    .map { row -> row.take(config.columns.size) } // 只取前 N 欄
                    .filter { row -> row.any { !it.isNullOrEmpty() } } // dropna(how="all")
                importTable(config, rows)
                onProgress(config.sheet, rows.size, idx, total)
            }

            // 應用程式 meta(資料更新日期等)
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS _app_meta (key TEXT PRIMARY KEY, value TEXT)"
            )
            setMeta("source_file", sourceFile)
            setMeta("update_date", updateDateDisplay ?: "")
            setMeta("imported_at", System.currentTimeMillis().toString())
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun importTable(config: SheetConfig, rows: List<List<String?>>) {
        val table = config.table
        db.execSQL("DROP TABLE IF EXISTS \"$table\"")
        val cols = config.columns.joinToString(", ") { "\"$it\" TEXT" }
        db.execSQL("CREATE TABLE IF NOT EXISTS \"$table\" ($cols)")

        val placeholders = config.columns.joinToString(", ") { "?" }
        val sql = "INSERT INTO \"$table\" (${config.columns.joinToString(", ") { "\"$it\"" }}) VALUES ($placeholders)"
        db.beginTransaction()
        try {
            val stmt = db.compileStatement(sql)
            for (row in rows) {
                stmt.clearBindings()
                for ((i, v) in row.withIndex()) {
                    if (!v.isNullOrEmpty()) stmt.bindString(i + 1, v)
                    else stmt.bindNull(i + 1)
                }
                stmt.executeInsert()
            }
            stmt.close()
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // ── Meta ──────────────────────────────────────────────
    fun setMeta(key: String, value: String) {
        db.execSQL(
            "INSERT OR REPLACE INTO _app_meta (key, value) VALUES (?, ?)",
            arrayOf(key, value)
        )
    }

    fun getMeta(key: String): String? {
        db.rawQuery("SELECT value FROM _app_meta WHERE key=?", arrayOf(key)).use { c ->
            return if (c.moveToFirst()) c.getString(0) else null
        }
    }

    fun hasImportedData(): Boolean =
        getMeta("imported_at") != null

    /** 各表筆數(供匯入完成頁顯示)。 */
    fun tableRowCounts(): List<Pair<String, Long>> {
        return SheetConfigs.ALL.map { cfg ->
            db.rawQuery("SELECT COUNT(*) FROM \"${cfg.table}\"", null).use { c ->
                cfg.table to if (c.moveToFirst()) c.getLong(0) else 0L
            }
        }
    }

    // ── 通用查詢 ──────────────────────────────────────────
    /** 執行查詢，回傳列資料；每列為 List<Any?> (Long/Double/String/null)。 */
    fun query(sql: String, params: Array<Any?> = arrayOf()): List<List<Any?>> {
        val out = mutableListOf<List<Any?>>()
        db.rawQuery(sql, params.map { it?.toString() }.toTypedArray()).use { c ->
            val n = c.columnCount
            while (c.moveToNext()) {
                val row = ArrayList<Any?>(n)
                for (i in 0 until n) {
                    row.add(readValue(c, i))
                }
                out.add(row)
            }
        }
        return out
    }

    private fun readValue(c: Cursor, i: Int): Any? = when (c.getType(i)) {
        Cursor.FIELD_TYPE_INTEGER -> c.getLong(i)
        Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i)
        Cursor.FIELD_TYPE_STRING -> c.getString(i)
        else -> null
    }

    fun queryDouble(sql: String, params: Array<Any?> = arrayOf()): Double? {
        db.rawQuery(sql, params.map { it?.toString() }.toTypedArray()).use { c ->
            return if (c.moveToFirst() && !c.isNull(0)) c.getDouble(0) else null
        }
    }

    fun close() = db.close()

    companion object {
        const val DB_NAME = "hospital_data.db"
    }
}
