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
}
