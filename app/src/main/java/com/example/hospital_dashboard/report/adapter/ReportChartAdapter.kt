package com.example.hospital_dashboard.report.adapter

import com.example.hospital_dashboard.data.BarSegment
import com.example.hospital_dashboard.data.HBarData
import com.example.hospital_dashboard.data.HBarRow
import com.example.hospital_dashboard.data.LineChartData
import com.example.hospital_dashboard.data.LineSeries
import com.example.hospital_dashboard.report.model.DimensionType
import com.example.hospital_dashboard.report.model.ReportResult

/**
 * 報表呈現適配器 (ReportChartAdapter)。
 * 依據 SRS.md Section 16 & 25（Renderer Separation）實作。
 * 負責將純計算結果 (ReportResult) 轉換為 Compose 自繪圖表所需之資料結構。
 */
object ReportChartAdapter {

    /**
     * 將報表結果轉換為趨勢折線圖資料。
     * @param result 運算結果
     * @param metricId 欲呈現之指標 ID (如 "opd_visit_count")
     * @param seriesDimension 若需依院區或科別分不同折線序列，指定其維度
     */
    fun toLineChart(
        result: ReportResult,
        metricId: String,
        seriesDimension: DimensionType? = null
    ): LineChartData {
        val xLabels = result.rows.map { it.dim(DimensionType.TIME) }.distinct()
        if (xLabels.isEmpty()) return LineChartData.EMPTY

        val seriesList = mutableListOf<LineSeries>()

        if (seriesDimension == null) {
            // 單一全院或總體序列
            val values = xLabels.map { label ->
                val matching = result.rows.filter { it.dim(DimensionType.TIME) == label }
                if (matching.isNotEmpty()) matching.sumOf { it.metric(metricId) } else null
            }
            seriesList.add(LineSeries("當期數值", values, dashed = false))

            if (result.context.showYoy) {
                val yoyValues = xLabels.map { label ->
                    val matching = result.rows.filter { it.dim(DimensionType.TIME) == label }
                    if (matching.isNotEmpty()) matching.sumOf { it.yoyMetrics[metricId] ?: 0.0 } else null
                }
                seriesList.add(LineSeries("去年同期", yoyValues, dashed = true))
            }
        } else {
            // 依指定維度分序列 (例如依各院區)
            val seriesNames = result.rows.map { it.dim(seriesDimension) }.filter { it.isNotEmpty() }.distinct().sorted()

            for (sName in seriesNames) {
                val values = xLabels.map { label ->
                    val row = result.rows.firstOrNull { it.dim(DimensionType.TIME) == label && it.dim(seriesDimension) == sName }
                    row?.metric(metricId)
                }
                seriesList.add(LineSeries(sName, values, dashed = false))

                if (result.context.showYoy) {
                    val yoyValues = xLabels.map { label ->
                        val row = result.rows.firstOrNull { it.dim(DimensionType.TIME) == label && it.dim(seriesDimension) == sName }
                        row?.yoyMetrics?.get(metricId)
                    }
                    if (yoyValues.any { it != null && it > 0 }) {
                        seriesList.add(LineSeries("$sName (去年)", yoyValues, dashed = true))
                    }
                }
            }
        }

        return LineChartData(xLabels, seriesList)
    }

    /**
     * 將報表結果轉換為水平排名長條圖 (HBarData)。
     * @param result 運算結果
     * @param metricId 欲呈現之指標 ID
     * @param groupDimension 橫條主維度 (如 CAMPUS 或 DEPARTMENT)
     */
    fun toHBarChart(
        result: ReportResult,
        metricId: String,
        groupDimension: DimensionType
    ): HBarData {
        val groupNames = result.rows.map { it.dim(groupDimension) }.filter { it.isNotEmpty() }.distinct()

        val items = groupNames.map { gName ->
            val sumVal = result.rows.filter { it.dim(groupDimension) == gName }.sumOf { it.metric(metricId) }
            gName to sumVal
        }.filter { it.second > 0 }.sortedByDescending { it.second }

        val rows = items.map {
            HBarRow(
                name = it.first,
                segments = listOf(BarSegment(it.first, it.second))
            )
        }
        return HBarData(rows)
    }
}
