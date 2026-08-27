package com.example.hospital_dashboard.data

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.util.zip.ZipFile

/**
 * 輕量 xlsx 讀取器(無第三方依賴)。
 *
 * 直接以 ZipFile + XmlPullParser 解析 OOXML：
 *  - xl/workbook.xml            → 工作表名稱 → rId
 *  - xl/_rels/workbook.xml.rels → rId → worksheet 路徑
 *  - xl/sharedStrings.xml       → 共享字串表
 *  - xl/worksheets/sheetN.xml   → 列資料
 *
 * 所有儲存格一律以字串回傳(與 Python pandas dtype=str 行為一致)：
 *  - t="s"  → 查 sharedStrings
 *  - t="str"/t="inlineStr" → 直接取值
 *  - 數值/其他 → 原始 <v> 文字
 */
object XlsxReader {

    /** 開啟活頁簿，可依工作表名稱逐表讀取(避免一次載入全部列)。 */
    fun openBook(file: File): Book = Book(ZipFile(file))

    class Book internal constructor(private val zip: ZipFile) : AutoCloseable {
        private val sheetRid: List<Pair<String, String>> =
            parseWorkbookSheets(zip.getEntryText("xl/workbook.xml"))
        private val ridTarget: Map<String, String> =
            parseWorkbookRels(zip.getEntryText("xl/_rels/workbook.xml.rels"))
        private val shared: List<String> =
            parseSharedStrings(zip.getEntryText("xl/sharedStrings.xml"))

        fun sheetNames(): List<String> = sheetRid.map { it.first }

        fun hasSheet(name: String): Boolean = sheetRid.any { it.first == name }

        /** 讀取單一工作表全部列；每列為 List<String?>。 */
        fun readRows(sheetName: String): List<List<String?>> {
            val rid = sheetRid.firstOrNull { it.first == sheetName }?.second ?: return emptyList()
            val target = ridTarget[rid] ?: return emptyList()
            // Target 可能為相對路徑(worksheets/sheet1.xml)或絕對路徑(/xl/worksheets/sheet1.xml，
            // 如 openpyxl 產出)或已含 xl/ 前綴；一律正規化為 xl/worksheets/sheetN.xml
            val t = target.removePrefix("/")
            val path = if (t.startsWith("xl/")) t else "xl/$t"
            val entry = zip.getEntry(path) ?: return emptyList()
            return parseSheet(zip.getInputStream(entry), shared)
        }

        override fun close() = zip.close()
    }

    private fun ZipFile.getEntryText(path: String): String? {
        val entry = getEntry(path) ?: return null
        return getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    /** workbook.xml：<sheet name="..." r:id="rIdN"/> */
    private fun parseWorkbookSheets(xml: String?): List<Pair<String, String>> {
        if (xml == null) return emptyList()
        val out = mutableListOf<Pair<String, String>>()
        val parser = Xml.newPullParser()
        parser.setInput(xml.reader())
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "sheet") {
                val name = parser.getAttributeValue(null, "name")
                val rid = parser.getAttributeValue("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id")
                    ?: parser.getAttributeValue(null, "r:id")
                if (name != null && rid != null) out.add(name to rid)
            }
            event = parser.next()
        }
        return out
    }

    /** workbook.xml.rels：<Relationship Id="rIdN" Target="worksheets/sheetN.xml"/> */
    private fun parseWorkbookRels(xml: String?): Map<String, String> {
        if (xml == null) return emptyMap()
        val out = mutableMapOf<String, String>()
        val parser = Xml.newPullParser()
        parser.setInput(xml.reader())
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "Relationship") {
                val id = parser.getAttributeValue(null, "Id")
                val target = parser.getAttributeValue(null, "Target")
                if (id != null && target != null) out[id] = target
            }
            event = parser.next()
        }
        return out
    }

    /** sharedStrings.xml：每個 <si> 內所有 <t> 文字串接。 */
    private fun parseSharedStrings(xml: String?): List<String> {
        if (xml == null) return emptyList()
        val out = mutableListOf<String>()
        val parser = Xml.newPullParser()
        parser.setInput(xml.reader())
        var event = parser.eventType
        var inSi = false
        val sb = StringBuilder()
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "si" -> { inSi = true; sb.setLength(0) }
                    "t" -> if (inSi) sb.append(parser.nextText())
                }
                XmlPullParser.END_TAG -> if (parser.name == "si" && inSi) {
                    out.add(sb.toString())
                    inSi = false
                }
            }
            event = parser.next()
        }
        return out
    }

    /** 解析單一工作表 → 列資料。儲存格以 r="A1" 定位欄位。 */
    private fun parseSheet(input: java.io.InputStream, shared: List<String>): List<List<String?>> {
        val rows = mutableListOf<List<String?>>()
        val parser = Xml.newPullParser()
        parser.setInput(input, Charsets.UTF_8.name())

        var currentRow: MutableList<String?>? = null
        var maxCol = 0
        var cellCol = 0
        var cellType: String? = null
        var cellValue: String? = null
        var inInlineStr = false
        val inlineSb = StringBuilder()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "row" -> { currentRow = mutableListOf(); maxCol = 0 }
                    "c" -> {
                        val ref = parser.getAttributeValue(null, "r")
                        cellCol = colIndexFromRef(ref)
                        cellType = parser.getAttributeValue(null, "t")
                        cellValue = null
                        inlineSb.setLength(0)
                        inInlineStr = false
                    }
                    "is" -> inInlineStr = true
                    "t" -> if (inInlineStr) inlineSb.append(parser.nextText())
                    "v" -> cellValue = parser.nextText()
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "c" -> {
                        val row = currentRow ?: continue
                        val value = when (cellType) {
                            "s" -> cellValue?.toIntOrNull()?.let { shared.getOrNull(it) }
                            "inlineStr" -> inlineSb.toString().ifEmpty { null }
                            else -> cellValue
                        }
                        while (row.size <= cellCol) row.add(null)
                        row[cellCol] = value
                        if (cellCol + 1 > maxCol) maxCol = cellCol + 1
                    }
                    "is" -> inInlineStr = false
                    "row" -> {
                        val row = currentRow
                        if (row != null) {
                            // 與 pandas dropna(how="all") 一致：整列皆空則略過
                            if (row.any { !it.isNullOrEmpty() }) {
                                rows.add(row.toList())
                            }
                            currentRow = null
                        }
                    }
                }
            }
            event = parser.next()
        }
        return rows
    }

    /** "A"→0, "AB"→27 */
    private fun colIndexFromRef(ref: String?): Int {
        if (ref == null) return 0
        var idx = 0
        for (ch in ref) {
            if (ch in 'A'..'Z') idx = idx * 26 + (ch - 'A' + 1)
            else break
        }
        return idx - 1
    }
}
