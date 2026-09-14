package com.example.hospital_dashboard.report

import androidx.compose.ui.graphics.Color
import com.example.hospital_dashboard.data.BarSegment
import com.example.hospital_dashboard.data.DashboardRepo
import com.example.hospital_dashboard.data.HBarRow
import com.example.hospital_dashboard.ui.charts.BRANCH_COLORS
import com.example.hospital_dashboard.ui.charts.HBarClick
import com.example.hospital_dashboard.ui.charts.seriesColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BedOtherRegressionTest {

    @Test
    fun testFiltersShowHospitalTotal() {
        val defaultFilters = DashboardRepo.Filters(years = listOf("2024", "2025"))
        assertEquals(false, defaultFilters.showHospitalTotal)

        val totalFilters = defaultFilters.withHospitalTotal(true)
        assertEquals(true, totalFilters.showHospitalTotal)
        assertEquals(listOf("2024", "2025"), totalFilters.years)

        val resetFilters = totalFilters.withHospitalTotal(false)
        assertEquals(false, resetFilters.showHospitalTotal)
    }

    @Test
    fun testBranchColorsIncludeHospitalTotal() {
        val totalColor = BRANCH_COLORS["全院"]
        assertNotNull("全院 color should be defined in BRANCH_COLORS", totalColor)
        assertEquals(Color(0xFF1565C0), totalColor)

        val resolvedColor = seriesColor("全院", 0)
        assertEquals(Color(0xFF1565C0), resolvedColor)

        val resolvedYoyColor = seriesColor("全院(去年)", 0)
        assertEquals(Color(0xFF1565C0), resolvedYoyColor)
    }

    @Test
    fun testBedOpenRateFormula() {
        val registered = 250.0
        val actualOpen = 238.0
        val openRate = if (registered > 0) actualOpen / registered * 100.0 else 0.0
        assertEquals(95.2, openRate, 0.01)

        val trailingText = String.format("開床率 %.1f%%", openRate)
        assertEquals("開床率 95.2%", trailingText)

        // Zero registered beds edge case
        val zeroReg = 0.0
        val zeroRate = if (zeroReg > 0) 10.0 / zeroReg * 100.0 else 0.0
        assertEquals(0.0, zeroRate, 0.001)
    }

    @Test
    fun testOccupancyColorScale() {
        // Test 0%, 50%, 100% color interpolation
        val c0 = DashboardRepo.occupancyColor(0.0)
        // Red component of 0% should be 215, Green 48, Blue 39
        val r0 = (c0 shr 16) and 0xFFL
        val g0 = (c0 shr 8) and 0xFFL
        val b0 = c0 and 0xFFL
        assertEquals(215L, r0)
        assertEquals(48L, g0)
        assertEquals(39L, b0)

        val c100 = DashboardRepo.occupancyColor(100.0)
        val r100 = (c100 shr 16) and 0xFFL
        val g100 = (c100 shr 8) and 0xFFL
        val b100 = c100 and 0xFFL
        assertEquals(26L, r100)
        assertEquals(152L, g100)
        assertEquals(80L, b100)

        // Coerce bounds
        assertEquals(c0, DashboardRepo.occupancyColor(-10.0))
        assertEquals(c100, DashboardRepo.occupancyColor(120.0))
    }

    @Test
    fun testBedOpenRateBarRowStructure() {
        val regBeds = 300.0
        val openBeds = 270.0
        val rate = openBeds / regBeds * 100.0
        val row = HBarRow(
            name = "急性一般病床",
            segments = listOf(
                BarSegment("登記床數", regBeds),
                BarSegment("實開床數", openBeds)
            ),
            trailing = String.format("開床率 %.1f%%", rate)
        )

        assertEquals(2, row.segments.size)
        assertTrue(row.segments.any { it.label.contains("登記") })
        assertEquals("登記床數", row.segments[0].label)
        assertEquals("實開床數", row.segments[1].label)
        assertEquals("開床率 90.0%", row.trailing)
    }

    @Test
    fun testSurgeryAggregationFormula() {
        val opSurgery = 1540.0
        val ipSurgery = 820.0
        val totalSurgery = opSurgery + ipSurgery
        assertEquals(2360.0, totalSurgery, 0.001)
    }

    @Test
    fun testOffsiteBranchClickEnum() {
        val clickType = HBarClick.OffsiteBranch
        var handled = false
        when (clickType) {
            HBarClick.OffsiteBranch -> handled = true
            else -> handled = false
        }
        assertTrue("HBarClick.OffsiteBranch must be correctly handled", handled)
    }

    @Test
    fun testBedStationOccDetailData() {
        val detail = DashboardRepo.BedStationOccDetail(
            branch = "仁愛",
            nursingStation = "十一西",
            occupancyRate = 98.6667,
            openBeds = 40.0,
            registeredBeds = 66.0
        )
        assertEquals("仁愛", detail.branch)
        assertEquals("十一西", detail.nursingStation)
        assertEquals(98.6667, detail.occupancyRate!!, 0.001)
        assertEquals(40.0, detail.openBeds, 0.001)
        assertEquals(66.0, detail.registeredBeds, 0.001)
        assertEquals(60.606, detail.openRate, 0.01)

        // Test null occupancy rate (e.g. ward with 0 open beds)
        val nullDetail = DashboardRepo.BedStationOccDetail(
            branch = "仁愛",
            nursingStation = "七西",
            occupancyRate = null,
            openBeds = 0.0,
            registeredBeds = 0.0
        )
        assertEquals(null, nullDetail.occupancyRate)
        assertEquals(0.0, nullDetail.openRate, 0.001)
    }

    @Test
    fun testBedCategoryHeatmapExcludeOtherFilterPredicate() {
        fun buildOtherFilter(excludeOther: Boolean): String =
            if (excludeOther) {
                "AND major_category != '其他' AND category != '其他' AND category NOT LIKE '%產後護理之家%'"
            } else ""

        val excluded = buildOtherFilter(true)
        assertTrue(excluded.contains("major_category != '其他'"))
        assertTrue(excluded.contains("category NOT LIKE '%產後護理之家%'"))
        assertTrue(excluded.contains("category != '其他'"))

        val allShown = buildOtherFilter(false)
        assertEquals("", allShown)
    }

    @Test
    fun testDiffBedSheetConfigExists() {
        val cfg = com.example.hospital_dashboard.data.SheetConfigs.bySheet("差額病床業務資料")
        assertNotNull("差額病床業務資料 config should exist", cfg)
        assertEquals("diff_bed_service", cfg?.table)
        assertEquals(18, cfg?.columns?.size)
        assertEquals("branch_name", cfg?.columns?.get(1)) // B欄 院區
        assertEquals("category", cfg?.columns?.get(4))    // E欄 類別
        assertEquals("inpatient_days", cfg?.columns?.get(10)) // K欄 住院人日
        assertEquals("open_bed_days", cfg?.columns?.get(16))  // Q欄 實開床天數

        // Test derive logic (ym -> year/month if missing)
        val rowWithNullYm = List(18) { i ->
            when (i) {
                0 -> "11405"
                1 -> "中興"
                else -> null
            }
        }
        val derived = cfg?.derive?.invoke(rowWithNullYm)
        assertNotNull(derived)
        assertEquals("114", derived?.get(12)) // M欄 year
        assertEquals("5", derived?.get(13))   // N欄 month
    }

    @Test
    fun testDiffBedOccupancyFormulaAndDetail() {
        // 住院人日 K = 1121, 實開床天數 Q = 1426
        val inpatientDays = 1121.0
        val openBedDays = 1426.0
        val rate = if (openBedDays > 0) (inpatientDays / openBedDays * 100.0) else 0.0
        assertEquals(78.6115, rate, 0.001)

        val detail = DashboardRepo.BedStationOccDetail(
            branch = "中興",
            nursingStation = "7B病房(外)",
            occupancyRate = 119.35,
            openBeds = 2.0,
            registeredBeds = 3.0,
            inpatientDays = 74.0,
            openBedDays = 62.0
        )
        assertEquals("中興", detail.branch)
        assertEquals("7B病房(外)", detail.nursingStation)
        assertEquals(119.35, detail.occupancyRate!!, 0.01)
        assertEquals(74.0, detail.inpatientDays, 0.01)
        assertEquals(62.0, detail.openBedDays, 0.01)
        assertEquals(66.67, detail.openRate, 0.01)
    }
}
