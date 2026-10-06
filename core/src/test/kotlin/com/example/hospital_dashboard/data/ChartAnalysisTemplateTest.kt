package com.example.hospital_dashboard.data

import org.junit.Assert.*
import org.junit.Test

class ChartAnalysisTemplateTest {

    @Test
    fun testGenerateLineChartReport() {
        val lineData = LineChartData(
            xLabels = listOf("114/06", "114/07", "114/08"),
            series = listOf(
                LineSeries("忠孝院區", listOf(10000.0, 11000.0, 12500.0)),
                LineSeries("忠孝院區(去年)", listOf(9500.0, 10200.0, 11500.0), dashed = true)
            )
        )
        val input = ChartAnalysisTemplate.ReportInput(
            title = "門診人次月趨勢（依院區）",
            filters = DashboardRepo.Filters(
                years = listOf("114"),
                months = listOf("08"),
                branches = listOf("忠孝院區"),
                showYoy = true,
                excludeVaccine = true
            ),
            lineData = lineData
        )
        val report = ChartAnalysisTemplate.generate(input)
        assertNotNull(report)
        assertTrue(report.contains("門診人次月趨勢（依院區）"))
        assertTrue(report.contains("忠孝院區"))
        assertTrue(report.contains("已排除（扣除同年月同院區同科別之疫苗施打人次）"))
        assertTrue(report.contains("去年同期比較 (YoY)"))
        assertTrue(report.contains("核心數據綜覽"))
        assertTrue(report.contains("營運決策與行動建議"))
    }

    @Test
    fun testGenerateHBarReport() {
        val hbarData = HBarData(
            rows = listOf(
                HBarRow("忠孝院區", listOf(BarSegment("114年08月", 15000.0), BarSegment("113年08月", 14000.0))),
                HBarRow("仁愛院區", listOf(BarSegment("114年08月", 12000.0), BarSegment("113年08月", 13000.0)))
            )
        )
        val input = ChartAnalysisTemplate.ReportInput(
            title = "各院區門診人次（8月）",
            filters = DashboardRepo.Filters(),
            hbarData = hbarData
        )
        val report = ChartAnalysisTemplate.generate(input)
        assertTrue(report.contains("各院區門診人次（8月）"))
        assertTrue(report.contains("忠孝院區"))
        assertTrue(report.contains("仁愛院區"))
        assertTrue(report.contains("集中度分析"))
    }
}
