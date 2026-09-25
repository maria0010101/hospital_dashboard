package com.example.hospital_dashboard.data

import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.Types

/**
 * 基於 JDBC (org.xerial:sqlite-jdbc) 的 HospitalDb 實作。
 * 支援 Windows / macOS / Linux 桌面環境及 JVM 單元測試。
 */
class JdbcHospitalDb(val dbFile: File) : HospitalDb {

    private val conn: Connection

    init {
        dbFile.parentFile?.mkdirs()
        conn = DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}")

        // 首次開啟即建立 meta 表與 vaccine_service 防呆空表
        conn.createStatement().use { stmt ->
            stmt.execute("CREATE TABLE IF NOT EXISTS _app_meta (key TEXT PRIMARY KEY, value TEXT)")
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS vaccine_service (" +
                    "ym TEXT, branch TEXT, dept_name TEXT, visit_type TEXT, " +
                    "opd_vaccine_screen_count TEXT, er_vaccine_screen_count TEXT, flu_vaccine_count TEXT, " +
                    "year TEXT, month TEXT, branch_name TEXT)"
            )
            try {
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_opd_ym_b_d ON outpatient_service (ym, branch_name, dept)")
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_vac_ym_b_d ON vaccine_service (ym, branch_name, dept_name)")
            } catch (_: Exception) {}
        }
    }

    override fun importWorkbook(
        book: XlsxBook,
        updateDateDisplay: String?,
        sourceFile: String,
        onProgress: (String, Int, Int, Int) -> Unit
    ) {
        val prevAuto = conn.autoCommit
        conn.autoCommit = false
        try {
            val total = SheetConfigs.ALL.size
            var idx = 0
            for (config in SheetConfigs.ALL) {
                idx++
                val rows = book.readRows(config.sheet)
                    .drop(1)
                    .map { row -> row.take(config.columns.size) }
                    .filter { row -> row.any { !it.isNullOrEmpty() } }
                    .map { row -> config.derive?.invoke(row) ?: row }
                importTable(config, rows)
                onProgress(config.sheet, rows.size, idx, total)
            }

            conn.createStatement().use { stmt ->
                try {
                    stmt.execute("CREATE INDEX IF NOT EXISTS idx_opd_ym_b_d ON outpatient_service (ym, branch_name, dept)")
                    stmt.execute("CREATE INDEX IF NOT EXISTS idx_vac_ym_b_d ON vaccine_service (ym, branch_name, dept_name)")
                } catch (_: Exception) {}
            }

            setMeta("source_file", sourceFile)
            setMeta("update_date", updateDateDisplay ?: "")
            setMeta("imported_at", System.currentTimeMillis().toString())

            conn.commit()
        } catch (e: Exception) {
            conn.rollback()
            throw e
        } finally {
            conn.autoCommit = prevAuto
        }
    }

    private fun importTable(config: SheetConfig, rows: List<List<String?>>) {
        val table = config.table
        conn.createStatement().use { stmt ->
            stmt.execute("DROP TABLE IF EXISTS \"$table\"")
            val cols = config.columns.joinToString(", ") { "\"$it\" TEXT" }
            stmt.execute("CREATE TABLE IF NOT EXISTS \"$table\" ($cols)")
        }

        val placeholders = config.columns.joinToString(", ") { "?" }
        val sql = "INSERT INTO \"$table\" (${config.columns.joinToString(", ") { "\"$it\"" }}) VALUES ($placeholders)"
        conn.prepareStatement(sql).use { pstmt ->
            for (row in rows) {
                pstmt.clearParameters()
                for (i in config.columns.indices) {
                    val v = row.getOrNull(i)
                    if (!v.isNullOrEmpty()) {
                        pstmt.setString(i + 1, v)
                    } else {
                        pstmt.setNull(i + 1, Types.VARCHAR)
                    }
                }
                pstmt.addBatch()
            }
            pstmt.executeBatch()
        }
    }

    override fun setMeta(key: String, value: String) {
        val sql = "INSERT OR REPLACE INTO _app_meta (key, value) VALUES (?, ?)"
        conn.prepareStatement(sql).use { pstmt ->
            pstmt.setString(1, key)
            pstmt.setString(2, value)
            pstmt.executeUpdate()
        }
    }

    override fun getMeta(key: String): String? {
        val sql = "SELECT value FROM _app_meta WHERE key=?"
        conn.prepareStatement(sql).use { pstmt ->
            pstmt.setString(1, key)
            pstmt.executeQuery().use { rs ->
                return if (rs.next()) rs.getString(1) else null
            }
        }
    }

    override fun hasImportedData(): Boolean {
        if (getMeta("imported_at") == null) return false
        val tables = SheetConfigs.ALL.map { it.table }
        val ph = tables.joinToString(",") { "?" }
        val sql = "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name IN ($ph)"
        conn.prepareStatement(sql).use { pstmt ->
            for ((i, t) in tables.withIndex()) {
                pstmt.setString(i + 1, t)
            }
            pstmt.executeQuery().use { rs ->
                val found = if (rs.next()) rs.getInt(1) else 0
                return found == tables.size
            }
        }
    }

    override fun tableRowCounts(): List<Pair<String, Long>> {
        val checkSql = "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name=?"
        return SheetConfigs.ALL.map { cfg ->
            val exists = conn.prepareStatement(checkSql).use { pstmt ->
                pstmt.setString(1, cfg.table)
                pstmt.executeQuery().use { rs ->
                    if (rs.next()) rs.getInt(1) > 0 else false
                }
            }
            cfg.table to if (exists) {
                conn.createStatement().use { stmt ->
                    stmt.executeQuery("SELECT COUNT(*) FROM \"${cfg.table}\"").use { rs ->
                        if (rs.next()) rs.getLong(1) else 0L
                    }
                }
            } else 0L
        }
    }

    override fun query(sql: String, params: Array<Any?>): List<List<Any?>> {
        val out = mutableListOf<List<Any?>>()
        conn.prepareStatement(sql).use { pstmt ->
            for ((i, p) in params.withIndex()) {
                if (p != null) pstmt.setString(i + 1, p.toString())
                else pstmt.setNull(i + 1, Types.VARCHAR)
            }
            pstmt.executeQuery().use { rs ->
                val n = rs.metaData.columnCount
                while (rs.next()) {
                    val row = ArrayList<Any?>(n)
                    for (i in 1..n) {
                        val obj = rs.getObject(i)
                        row.add(convertValue(obj))
                    }
                    out.add(row)
                }
            }
        }
        return out
    }

    private fun convertValue(v: Any?): Any? = when (v) {
        null -> null
        is Number -> {
            val d = v.toDouble()
            if (d == kotlin.math.floor(d) && !d.isInfinite() && v !is Double && v !is Float) {
                v.toLong()
            } else {
                d
            }
        }
        else -> v.toString()
    }

    override fun queryDouble(sql: String, params: Array<Any?>): Double? {
        conn.prepareStatement(sql).use { pstmt ->
            for ((i, p) in params.withIndex()) {
                if (p != null) pstmt.setString(i + 1, p.toString())
                else pstmt.setNull(i + 1, Types.VARCHAR)
            }
            pstmt.executeQuery().use { rs ->
                if (rs.next()) {
                    val d = rs.getDouble(1)
                    return if (rs.wasNull()) null else d
                }
                return null
            }
        }
    }

    override fun close() {
        if (!conn.isClosed) conn.close()
    }
}
