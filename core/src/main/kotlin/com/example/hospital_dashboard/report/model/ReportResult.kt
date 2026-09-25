package com.example.hospital_dashboard.report.model

/**
 * 單一資料列 (ReportRow)。
 */
data class ReportRow(
    val dimensionValues: Map<DimensionType, String>,
    val metrics: Map<String, Double>,
    val yoyMetrics: Map<String, Double> = emptyMap(),
    val yoyDiff: Map<String, Double> = emptyMap(),
    val yoyPct: Map<String, Double> = emptyMap()
) {
    fun dim(type: DimensionType): String = dimensionValues[type] ?: ""
    fun metric(id: String): Double = metrics[id] ?: 0.0
}

/**
 * 報表運算結果 (ReportResult)。
 * 依據 SRS.md Section 8 & 16，實現資料計算與 UI 呈現分離。
 * 純粹攜帶統計矩陣，可適配為 Compose Canvas 圖表、表格或匯出檔案。
 */
data class ReportResult(
    val reportId: String,
    val context: ReportContext,
    val activeDimensions: List<DimensionType>,
    val rows: List<ReportRow>,
    val totals: Map<String, Double> = emptyMap(),
    val yoyTotals: Map<String, Double> = emptyMap()
)
