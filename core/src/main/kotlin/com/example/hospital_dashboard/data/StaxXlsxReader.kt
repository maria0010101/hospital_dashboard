package com.example.hospital_dashboard.data

import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamReader

/**
 * 基於 Java 內建 StAX (javax.xml.stream) 的跨平台輕量 xlsx 讀取器。
 * 行為與 Android 版 XlsxReader 100% 一致。
 */
object StaxXlsxReader {

    private val xmlInputFactory: XMLInputFactory by lazy {
        XMLInputFactory.newInstance().apply {
            setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
            setProperty(XMLInputFactory.SUPPORT_DTD, false)
        }
    }

    fun openBook(file: File): XlsxBook = Book(ZipFile(file))

    class Book internal constructor(private val zip: ZipFile) : XlsxBook {
        private val sheetRid: List<Pair<String, String>> =
            parseWorkbookSheets(zip.getEntryText("xl/workbook.xml"))
        private val ridTarget: Map<String, String> =
            parseWorkbookRels(zip.getEntryText("xl/_rels/workbook.xml.rels"))
        private val shared: List<String> =
            parseSharedStrings(zip.getEntryText("xl/sharedStrings.xml"))

        override fun sheetNames(): List<String> = sheetRid.map { it.first }

        override fun hasSheet(name: String): Boolean = sheetRid.any { it.first == name }

        override fun readRows(sheetName: String): List<List<String?>> {
            val rid = sheetRid.firstOrNull { it.first == sheetName }?.second ?: return emptyList()
            val target = ridTarget[rid] ?: return emptyList()
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

    private fun parseWorkbookSheets(xml: String?): List<Pair<String, String>> {
        if (xml == null) return emptyList()
        val out = mutableListOf<Pair<String, String>>()
        val reader = xmlInputFactory.createXMLStreamReader(xml.reader())
        while (reader.hasNext()) {
            val event = reader.next()
            if (event == XMLStreamConstants.START_ELEMENT && reader.localName == "sheet") {
                val name = reader.getAttributeValue(null, "name")
                val rid = reader.getAttributeValue("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id")
                    ?: reader.getAttributeValue(null, "r:id")
                    ?: reader.getAttributeValue(null, "id")
                if (name != null && rid != null) {
                    out.add(name to rid)
                }
            }
        }
        return out
    }

    private fun parseWorkbookRels(xml: String?): Map<String, String> {
        if (xml == null) return emptyMap()
        val out = mutableMapOf<String, String>()
        val reader = xmlInputFactory.createXMLStreamReader(xml.reader())
        while (reader.hasNext()) {
            val event = reader.next()
            if (event == XMLStreamConstants.START_ELEMENT && reader.localName == "Relationship") {
                val id = reader.getAttributeValue(null, "Id") ?: reader.getAttributeValue(null, "id")
                val target = reader.getAttributeValue(null, "Target") ?: reader.getAttributeValue(null, "target")
                if (id != null && target != null) {
                    out[id] = target
                }
            }
        }
        return out
    }

    private fun parseSharedStrings(xml: String?): List<String> {
        if (xml == null) return emptyList()
        val out = mutableListOf<String>()
        val reader = xmlInputFactory.createXMLStreamReader(xml.reader())
        var inSi = false
        var inT = false
        val sb = StringBuilder()
        while (reader.hasNext()) {
            when (reader.next()) {
                XMLStreamConstants.START_ELEMENT -> {
                    when (reader.localName) {
                        "si" -> {
                            inSi = true
                            sb.setLength(0)
                        }
                        "t" -> inT = true
                    }
                }
                XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA -> {
                    if (inSi && inT) {
                        sb.append(reader.text)
                    }
                }
                XMLStreamConstants.END_ELEMENT -> {
                    when (reader.localName) {
                        "t" -> inT = false
                        "si" -> {
                            if (inSi) {
                                out.add(sb.toString())
                                inSi = false
                            }
                        }
                    }
                }
            }
        }
        return out
    }

    private fun parseSheet(input: InputStream, shared: List<String>): List<List<String?>> {
        val rows = mutableListOf<List<String?>>()
        val reader = xmlInputFactory.createXMLStreamReader(input, Charsets.UTF_8.name())

        var currentRow: MutableList<String?>? = null
        var cellCol = 0
        var cellType: String? = null
        var inV = false
        var inT = false
        var inInlineStr = false
        val vSb = StringBuilder()
        val inlineSb = StringBuilder()

        while (reader.hasNext()) {
            when (reader.next()) {
                XMLStreamConstants.START_ELEMENT -> {
                    when (reader.localName) {
                        "row" -> currentRow = mutableListOf()
                        "c" -> {
                            val ref = reader.getAttributeValue(null, "r")
                            cellCol = colIndexFromRef(ref)
                            cellType = reader.getAttributeValue(null, "t")
                            vSb.setLength(0)
                            inlineSb.setLength(0)
                            inV = false
                            inT = false
                            inInlineStr = false
                        }
                        "is" -> inInlineStr = true
                        "t" -> if (inInlineStr) inT = true
                        "v" -> inV = true
                    }
                }
                XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA -> {
                    if (inV) vSb.append(reader.text)
                    if (inInlineStr && inT) inlineSb.append(reader.text)
                }
                XMLStreamConstants.END_ELEMENT -> {
                    when (reader.localName) {
                        "v" -> inV = false
                        "t" -> if (inInlineStr) inT = false
                        "is" -> inInlineStr = false
                        "c" -> {
                            val row = currentRow
                            if (row != null) {
                                val cellValStr = vSb.toString()
                                val value = when (cellType) {
                                    "s" -> cellValStr.toIntOrNull()?.let { shared.getOrNull(it) }
                                    "inlineStr" -> inlineSb.toString().ifEmpty { null }
                                    else -> cellValStr.ifEmpty { null }
                                }
                                while (row.size <= cellCol) row.add(null)
                                row[cellCol] = value
                            }
                        }
                        "row" -> {
                            val row = currentRow
                            if (row != null) {
                                if (row.any { !it.isNullOrEmpty() }) {
                                    rows.add(row.toList())
                                }
                                currentRow = null
                            }
                        }
                    }
                }
            }
        }
        return rows
    }

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
