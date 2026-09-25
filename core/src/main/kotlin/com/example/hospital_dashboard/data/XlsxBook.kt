package com.example.hospital_dashboard.data

/**
 * 跨平台活頁簿介面，由 Android (XmlPullParser) 與 Desktop (StAX) 分別實作。
 */
interface XlsxBook : AutoCloseable {
    fun sheetNames(): List<String>
    fun hasSheet(name: String): Boolean
    fun readRows(sheetName: String): List<List<String?>>
}
