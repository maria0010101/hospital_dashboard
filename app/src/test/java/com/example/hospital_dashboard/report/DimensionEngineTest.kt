package com.example.hospital_dashboard.report

import com.example.hospital_dashboard.report.engine.DimensionEngine
import com.example.hospital_dashboard.report.model.DimensionType
import com.example.hospital_dashboard.report.model.OrganizationLevel
import com.example.hospital_dashboard.report.model.ReportContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DimensionEngineTest {

    @Test
    fun testWhereClause_AllLevel_WithYears() {
        val ctx = ReportContext(
            reportId = "OPD_001",
            years = listOf("113", "114"),
            organizationLevel = OrganizationLevel.ALL
        )
        val (whereSql, params) = DimensionEngine.buildWhereClause(ctx, yearOffset = 0)

        assertEquals("year IN (?,?)", whereSql)
        assertEquals(listOf("113", "114"), params.toList())
    }

    @Test
    fun testWhereClause_YoYOffset() {
        val ctx = ReportContext(
            reportId = "OPD_001",
            years = listOf("113"),
            organizationLevel = OrganizationLevel.ALL
        )
        val (whereSql, params) = DimensionEngine.buildWhereClause(ctx, yearOffset = -1)

        assertEquals("year IN (?)", whereSql)
        assertEquals(listOf("112"), params.toList())
    }

    @Test
    fun testWhereClause_CampusLevel() {
        val ctx = ReportContext(
            reportId = "OPD_001",
            years = listOf("113"),
            organizationLevel = OrganizationLevel.CAMPUS,
            campuses = listOf("甲院區", "乙院區")
        )
        val (whereSql, params) = DimensionEngine.buildWhereClause(ctx, yearOffset = 0)

        assertEquals("year IN (?) AND branch_name IN (?,?)", whereSql)
        assertEquals(listOf("113", "甲院區", "乙院區"), params.toList())
    }

    @Test
    fun testWhereClause_DepartmentLevel() {
        val ctx = ReportContext(
            reportId = "OPD_001",
            years = listOf("113"),
            organizationLevel = OrganizationLevel.DEPARTMENT,
            deptDivs = listOf("部別01"),
            departments = listOf("科別01", "科別02")
        )
        val (whereSql, params) = DimensionEngine.buildWhereClause(ctx, yearOffset = 0)

        assertTrue(whereSql.contains("dept_div IN (?)"))
        assertTrue(whereSql.contains("dept IN (?,?)"))
        assertEquals(listOf("113", "部別01", "科別01", "科別02"), params.toList())
    }

    @Test
    fun testGroupByColumns_FourLevels() {
        assertEquals(listOf("year", "month"), DimensionEngine.resolveGroupByColumns(OrganizationLevel.ALL))
        assertEquals(listOf("year", "month", "branch_name"), DimensionEngine.resolveGroupByColumns(OrganizationLevel.CAMPUS))
        assertEquals(listOf("year", "month", "dept"), DimensionEngine.resolveGroupByColumns(OrganizationLevel.DEPARTMENT))
        assertEquals(listOf("year", "month", "branch_name", "dept"), DimensionEngine.resolveGroupByColumns(OrganizationLevel.CAMPUS_DEPARTMENT))
    }
}
