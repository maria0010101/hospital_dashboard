package com.example.hospital_dashboard.data

/**
 * 從資料檔名「業務資料彙整-1150508.xlsx」擷取民國年月日。
 * 減號後數字 = 民國年(3位) + 月(2位) + 日(2位)。
 */
object FileNameParser {

    data class UpdateDate(
        val rocYear: Int,
        val month: Int,
        val day: Int,
        val display: String, // 民國115年05月08日 (2026/05/08)
        val raw: String
    )

    fun parse(fileName: String?): UpdateDate? {
        if (fileName.isNullOrBlank()) return null
        val m = Regex("""[-_](\d{7})(?=\.\w+$)""").find(fileName.trim()) ?: return null
        val raw = m.groupValues[1]
        val y = raw.substring(0, 3).toIntOrNull() ?: return null
        val mo = raw.substring(3, 5).toIntOrNull() ?: return null
        val d = raw.substring(5, 7).toIntOrNull() ?: return null
        if (mo !in 1..12 || d !in 1..31) return null
        val ad = y + 1911
        val mm = mo.toString().padStart(2, '0')
        val dd = d.toString().padStart(2, '0')
        val display = "民國${y}年${mm}月${dd}日 ($ad/$mm/$dd)"
        return UpdateDate(y, mo, d, display, raw)
    }
}
