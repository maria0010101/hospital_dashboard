package com.example.hospital_dashboard.report

import com.example.hospital_dashboard.report.model.OrganizationLevel
import com.example.hospital_dashboard.report.model.ReportConfiguration
import com.example.hospital_dashboard.report.model.ReportContext
import com.example.hospital_dashboard.report.registry.ReportRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ReportContextAndRegistryTest {

    @Before
    fun setUp() {
        ReportRegistry.clear()
    }

    @Test
    fun testReportContext_Validation() {
        val validCtx = ReportContext(reportId = "OPD_001", years = listOf("113"))
        assertTrue(validCtx.validate().isSuccess)

        val emptyYearCtx = ReportContext(reportId = "OPD_001", years = emptyList())
        assertTrue(emptyYearCtx.validate().isFailure)

        val emptyIdCtx = ReportContext(reportId = "", years = listOf("113"))
        assertTrue(emptyIdCtx.validate().isFailure)
    }

    @Test
    fun testReportConfiguration_RoundTrip() {
        val ctx = ReportContext(
            reportId = "OPD_001",
            years = listOf("113", "114"),
            months = listOf("01", "02"),
            organizationLevel = OrganizationLevel.CAMPUS,
            campuses = listOf("甲院區"),
            showYoy = true
        )
        val config = ReportConfiguration.fromContext(ctx)
        val restoredCtx = config.toContext()

        assertEquals(ctx.reportId, restoredCtx.reportId)
        assertEquals(ctx.years, restoredCtx.years)
        assertEquals(ctx.months, restoredCtx.months)
        assertEquals(ctx.organizationLevel, restoredCtx.organizationLevel)
        assertEquals(ctx.campuses, restoredCtx.campuses)
        assertEquals(ctx.showYoy, restoredCtx.showYoy)
    }

    @Test
    fun testReportRegistry_RegisterAndRetrieve() {
        assertEquals(0, ReportRegistry.all().size)

        ReportRegistry.initDefaultReports()

        val reports = ReportRegistry.all()
        assertTrue(reports.isNotEmpty())

        val opdReport = ReportRegistry.get("OPD_001")
        assertNotNull(opdReport)
        assertEquals("OPD_001", opdReport?.reportId)
        assertEquals("門診業務", opdReport?.category)
        assertTrue(opdReport?.metrics?.any { it.id == "opd_visit_count" } == true)
    }
}
