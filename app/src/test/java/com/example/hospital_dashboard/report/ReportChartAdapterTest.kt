package com.example.hospital_dashboard.report

import com.example.hospital_dashboard.report.adapter.ReportChartAdapter
import com.example.hospital_dashboard.report.model.DimensionType
import com.example.hospital_dashboard.report.model.OrganizationLevel
import com.example.hospital_dashboard.report.model.ReportContext
import com.example.hospital_dashboard.report.model.ReportResult
import com.example.hospital_dashboard.report.model.ReportRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportChartAdapterTest {

    @Test
    fun testToLineChart_CampusDimension() {
        val ctx = ReportContext(
            reportId = "OPD_001",
            years = listOf("113"),
            organizationLevel = OrganizationLevel.CAMPUS,
            showYoy = true
        )

        val rows = listOf(
            ReportRow(
                dimensionValues = mapOf(
                    DimensionType.TIME to "113年01月",
                    DimensionType.CAMPUS to "甲院區"
                ),
                metrics = mapOf("opd_visit_count" to 1000.0),
                yoyMetrics = mapOf("opd_visit_count" to 900.0)
            ),
            ReportRow(
                dimensionValues = mapOf(
                    DimensionType.TIME to "113年01月",
                    DimensionType.CAMPUS to "乙院區"
                ),
                metrics = mapOf("opd_visit_count" to 800.0),
                yoyMetrics = mapOf("opd_visit_count" to 750.0)
            ),
            ReportRow(
                dimensionValues = mapOf(
                    DimensionType.TIME to "113年02月",
                    DimensionType.CAMPUS to "甲院區"
                ),
                metrics = mapOf("opd_visit_count" to 1100.0),
                yoyMetrics = mapOf("opd_visit_count" to 950.0)
            ),
            ReportRow(
                dimensionValues = mapOf(
                    DimensionType.TIME to "113年02月",
                    DimensionType.CAMPUS to "乙院區"
                ),
                metrics = mapOf("opd_visit_count" to 850.0),
                yoyMetrics = mapOf("opd_visit_count" to 800.0)
            )
        )

        val result = ReportResult(
            reportId = "OPD_001",
            context = ctx,
            activeDimensions = listOf(DimensionType.TIME, DimensionType.CAMPUS),
            rows = rows
        )

        val lineChart = ReportChartAdapter.toLineChart(result, "opd_visit_count", DimensionType.CAMPUS)

        assertEquals(listOf("113年01月", "113年02月"), lineChart.xLabels)
        // 應有 甲院區、甲院區 (去年)、乙院區、乙院區 (去年) 4 個序列
        assertEquals(4, lineChart.series.size)

        val jiaSeries = lineChart.series.first { it.name == "甲院區" }
        assertEquals(listOf(1000.0, 1100.0), jiaSeries.values)

        val jiaYoySeries = lineChart.series.first { it.name == "甲院區 (去年)" }
        assertTrue(jiaYoySeries.dashed)
        assertEquals(listOf(900.0, 950.0), jiaYoySeries.values)
    }

    @Test
    fun testToHBarChart_CampusRanking() {
        val ctx = ReportContext(
            reportId = "OPD_001",
            years = listOf("113"),
            organizationLevel = OrganizationLevel.CAMPUS
        )

        val rows = listOf(
            ReportRow(
                dimensionValues = mapOf(
                    DimensionType.TIME to "113年01月",
                    DimensionType.CAMPUS to "乙院區"
                ),
                metrics = mapOf("opd_visit_count" to 500.0)
            ),
            ReportRow(
                dimensionValues = mapOf(
                    DimensionType.TIME to "113年01月",
                    DimensionType.CAMPUS to "甲院區"
                ),
                metrics = mapOf("opd_visit_count" to 1200.0)
            )
        )

        val result = ReportResult(
            reportId = "OPD_001",
            context = ctx,
            activeDimensions = listOf(DimensionType.CAMPUS),
            rows = rows
        )

        val hBar = ReportChartAdapter.toHBarChart(result, "opd_visit_count", DimensionType.CAMPUS)

        // 應按人次降冪排序：甲院區 (1200) > 乙院區 (500)
        assertEquals(2, hBar.rows.size)
        assertEquals("甲院區", hBar.rows[0].name)
        assertEquals(1200.0, hBar.rows[0].segments[0].value, 0.0001)
        assertEquals("乙院區", hBar.rows[1].name)
        assertEquals(500.0, hBar.rows[1].segments[0].value, 0.0001)
    }
}
