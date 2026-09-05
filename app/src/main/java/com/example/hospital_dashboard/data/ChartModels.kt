package com.example.hospital_dashboard.data

/** 折線圖單一序列(虛線用於去年同期)。 */
data class LineSeries(val name: String, val values: List<Double?>, val dashed: Boolean = false)

data class LineChartData(val xLabels: List<String>, val series: List<LineSeries>) {
    companion object { val EMPTY = LineChartData(emptyList(), emptyList()) }
}

/** 橫條圖：每一列可含多段(群組或堆疊)。 */
data class BarSegment(val label: String, val value: Double)

data class HBarRow(val name: String, val segments: List<BarSegment>, val trailing: String? = null)

data class HBarData(
    val rows: List<HBarRow>,
    val grouped: Boolean = false,
    val overlap: Boolean = false, // 重疊橫條(兩段同起點，第二段疊於第一段上方)
    val targetLine: Double? = null // 目標線(如 85% 佔床率)
) {
    companion object { val EMPTY = HBarData(emptyList()) }
}

/** 直條圖：每組多序列(群組或堆疊)。 */
data class VBarGroup(val label: String, val segments: List<BarSegment>)

data class VBarData(
    val groups: List<VBarGroup>,
    val stacked: Boolean = false
) {
    companion object { val EMPTY = VBarData(emptyList()) }
}

data class PieSlice(val label: String, val value: Double)

data class PieData(val slices: List<PieSlice>) {
    companion object { val EMPTY = PieData(emptyList()) }
}

/** 表格儲存格(可帶背景色，用於佔床率色階/熱力圖)。 */
data class TableCell(val text: String, val bgArgb: Long? = null)

data class TableData(val columns: List<String>, val rows: List<List<TableCell>>) {
    companion object { val EMPTY = TableData(emptyList(), emptyList()) }
}

/** 9 項 KPI。 */
data class KpiSet(
    val opd: Double, val er: Double, val sessions: Double,
    val ipdAdm: Double, val ipdDays: Double, val occ: Double,
    val offsite: Double, val dialysis: Double, val checkup: Double
) {
    companion object { val EMPTY = KpiSet(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0) }
}

data class DeptIpdStat(val dept: String, val adm: Double, val los: Double)

data class BedDetailRow(
    val branch: String, val major: String, val category: String, val station: String,
    val regBeds: Long, val openBeds: Long, val days: Long,
    val regOcc: Double, val actOcc: Double,          // 百分比
    val yoyRegOcc: Double?, val yoyActOcc: Double?   // 百分比
)

data class BedYoyRow(val category: String, val curr: Double, val prev: Double) {
    val change: Double get() = curr - prev
}

/** 數值格式化工具。 */
object Fmt {
    fun compact(v: Double): String = when {
        v.isNaN() -> "-"
        kotlin.math.abs(v) >= 1e8 -> String.format("%.2f億", v / 1e8)
        kotlin.math.abs(v) >= 1e4 -> String.format("%.1f萬", v / 1e4)
        else -> String.format("%,.0f", v)
    }

    fun money(v: Double): String = when {
        v.isNaN() -> "-"
        kotlin.math.abs(v) >= 1e8 -> String.format("%.2f億", v / 1e8)
        kotlin.math.abs(v) >= 1e4 -> String.format("%.1f萬", v / 1e4)
        else -> String.format("%,.0f", v)
    }

    /** 金額以「千元」為單位顯示。 */
    fun moneyK(v: Double): String =
        if (v.isNaN()) "-" else String.format("%,.0f", v / 1000.0)

    /** 計數以「千人」為單位顯示（整數＋千）。 */
    fun k(v: Double): String =
        if (v.isNaN()) "-" else String.format("%,.0f千", v / 1000.0)

    fun percent(v: Double): String = String.format("%.1f%%", v)

    fun int(v: Double): String = String.format("%,.0f", v)

    fun signed(v: Double, suffix: String = ""): String =
        String.format("%+.1f%s", v, suffix)
}
