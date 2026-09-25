package com.example.hospital_dashboard.data

/**
 * 跨平台醫院業務資料庫契約介面。
 * Android 端由 AndroidHospitalDb 實作，Desktop / JVM 端由 JdbcHospitalDb 實作。
 */
interface HospitalDb : AutoCloseable {
    fun importWorkbook(
        book: XlsxBook,
        updateDateDisplay: String?,
        sourceFile: String,
        onProgress: (String, Int, Int, Int) -> Unit
    )
    fun setMeta(key: String, value: String)
    fun getMeta(key: String): String?
    fun hasImportedData(): Boolean
    fun tableRowCounts(): List<Pair<String, Long>>
    fun query(sql: String, params: Array<Any?> = arrayOf()): List<List<Any?>>
    fun queryDouble(sql: String, params: Array<Any?> = arrayOf()): Double?
    override fun close()

    companion object {
        const val DB_NAME = "hospital_data.db"
    }
}
