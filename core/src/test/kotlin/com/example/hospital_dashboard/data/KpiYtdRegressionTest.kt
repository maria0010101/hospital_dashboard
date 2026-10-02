package com.example.hospital_dashboard.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.File
import java.sql.DriverManager

class KpiYtdRegressionTest {
    @Test
    fun occupancyUsesBedDaysForMonthAndYtdAndExcludesOnlySpecifiedMajors() = withRepo { repo ->
        val current = repo.kpiForMonth("115", "8")
        assertEquals((40.0 + 10.0) / (50.0 + 100.0) * 100, current.occ, 0.0001)

        val details = repo.branchKpiDetails("115", "8", "總佔床率(實開病床)")
        val a = details.single { it.branch == "甲院區" }
        assertEquals(80.0, a.value, 0.0001)
        assertEquals(40.0, a.ytd!!, 0.0001) // (1 月 20 + 8 月 40) / (100 + 50)
        assertEquals(50.0, a.priorYtd!!, 0.0001) // 去年 1–8 月，不納入 12 月
        assertEquals(50.0, a.prior!!, 0.0001)
    }

    @Test
    fun incomeKpisAndCumulativeChartsUseTheSameJanuaryToAnchorMonthWindow() = withRepo { repo ->
        val kpi = repo.kpiForMonth("115", "8")
        assertEquals(310_000.0, kpi.incomeTotal, 0.01)
        assertEquals(30_000.0, kpi.incomeSelf, 0.01)
        val total = repo.branchKpiDetails("115", "8", "總收入").single { it.branch == "甲院區" }
        assertEquals(250_000.0, total.value, 0.01)
        assertEquals(370_000.0, total.ytd!!, 0.01)
        assertEquals(200_000.0, total.priorYtd!!, 0.01)
        val self = repo.branchKpiDetails("115", "8", "自費收入").single { it.branch == "甲院區" }
        assertEquals(25_000.0, self.value, 0.01)
        assertEquals(40_000.0, self.ytd!!, 0.01)
        assertEquals(20_000.0, self.priorYtd!!, 0.01)

        val f = DashboardRepo.Filters(years = listOf("114", "115"))
        val totalBar = repo.branchTotalIncomeYoyBar(f).rows.single { it.name == "甲院區" }
        assertEquals(200.0, totalBar.segments[0].value, 0.01)
        assertEquals(370.0, totalBar.segments[1].value, 0.01)
        val selfBar = repo.branchSelfPayIncomeYoyBar(f).rows.single { it.name == "甲院區" }
        assertEquals(20.0, selfBar.segments[0].value, 0.01)
        assertEquals(40.0, selfBar.segments[1].value, 0.01)

        val hospital = repo.branchTotalIncomeYoyBar(f.copy(showHospitalTotal = true)).rows.single()
        assertEquals(230.0, hospital.segments[0].value, 0.01)
        assertEquals(430.0, hospital.segments[1].value, 0.01)
        val january = repo.branchTotalIncomeYoyBar(f.copy(months = listOf("1"))).rows.single { it.name == "甲院區" }
        assertEquals(80.0, january.segments[0].value, 0.01)
        assertEquals(120.0, january.segments[1].value, 0.01)
    }

    private fun withRepo(block: (DashboardRepo) -> Unit) {
        val file = File.createTempFile("hospital_kpi_ytd", ".db")
        try {
            DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use { conn ->
                conn.createStatement().use { s ->
                    s.execute("CREATE TABLE outpatient_service(year TEXT, month TEXT, branch_name TEXT, opd_visit_count TEXT, er_visit TEXT, total_clinic_sessions TEXT)")
                    s.execute("CREATE TABLE inpatient_service(year TEXT, month TEXT, branch_name TEXT, admission_count TEXT, admission_days TEXT)")
                    s.execute("CREATE TABLE bed_type_service(year TEXT, month TEXT, branch_name TEXT, major_category TEXT, admission_days TEXT, actual_bed_days TEXT)")
                    s.execute("CREATE TABLE offsite_clinic_service(year TEXT, month TEXT, branch_name TEXT, total TEXT)")
                    s.execute("CREATE TABLE accounting_report(year TEXT, month TEXT, branch_name TEXT, dialysis_count TEXT, opd_checkup_count TEXT, admission_checkup_count TEXT)")
                    s.execute("CREATE TABLE ops_management_indicators(year TEXT, month TEXT, branch_name TEXT, total_income_opd TEXT, total_income_admission TEXT, self_pay_income_opd TEXT, self_pay_income_admission TEXT)")
                    s.execute("INSERT INTO outpatient_service VALUES('115','8','甲院區','1','1','1')")
                    s.execute("INSERT INTO bed_type_service VALUES('115','1','甲院區','一般','20','100')")
                    s.execute("INSERT INTO bed_type_service VALUES('115','8','甲院區','一般','40','50')")
                    s.execute("INSERT INTO bed_type_service VALUES('115','8','乙院區','一般','10','100')")
                    s.execute("INSERT INTO bed_type_service VALUES('115','8','甲院區','其他','500','10')")
                    s.execute("INSERT INTO bed_type_service VALUES('115','8','甲院區','產後（小孩）','500','10')")
                    s.execute("INSERT INTO bed_type_service VALUES('114','1','甲院區','一般','20','40')")
                    s.execute("INSERT INTO bed_type_service VALUES('114','8','甲院區','一般','30','60')")
                    s.execute("INSERT INTO bed_type_service VALUES('114','12','甲院區','一般','1000','1000')")
                    s.execute("INSERT INTO ops_management_indicators VALUES('115','1','甲院區','100000','20000','10000','5000')")
                    s.execute("INSERT INTO ops_management_indicators VALUES('115','8','甲院區','200000','50000','20000','5000')")
                    s.execute("INSERT INTO ops_management_indicators VALUES('115','8','乙院區','50000','10000','4000','1000')")
                    s.execute("INSERT INTO ops_management_indicators VALUES('114','1','甲院區','70000','10000','8000','2000')")
                    s.execute("INSERT INTO ops_management_indicators VALUES('114','8','甲院區','100000','20000','9000','1000')")
                    s.execute("INSERT INTO ops_management_indicators VALUES('114','8','乙院區','25000','5000','2000','1000')")
                    s.execute("INSERT INTO ops_management_indicators VALUES('114','12','甲院區','900000','100000','400000','100000')")
                }
            }
            JdbcHospitalDb(file).use { db ->
                val repo = DashboardRepo(db)
                assertNotNull(repo.latestMonth())
                block(repo)
            }
        } finally {
            file.delete()
        }
    }
}
