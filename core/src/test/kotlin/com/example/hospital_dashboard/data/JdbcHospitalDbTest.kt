package com.example.hospital_dashboard.data

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class JdbcHospitalDbTest {

    @Test
    fun testDbInitializationAndMeta() {
        val tempFile = File.createTempFile("test_hospital", ".db")
        tempFile.deleteOnExit()
        val db = JdbcHospitalDb(tempFile)
        try {
            assertNull(db.getMeta("source_file"))
            db.setMeta("source_file", "test.xlsx")
            assertEquals("test.xlsx", db.getMeta("source_file"))

            db.setMeta("update_date", "115年09月14日")
            assertEquals("115年09月14日", db.getMeta("update_date"))

            // Query test
            val res = db.query("SELECT 1 + 1 AS sum, CAST('123.45' AS REAL) AS val, 'hello' AS text", emptyArray())
            assertEquals(1, res.size)
            val row = res[0]
            assertEquals(3, row.size)
            // 1+1 can be 2L or 2
            assertTrue(row[0] is Number)
            assertEquals(2, (row[0] as Number).toInt())
            assertTrue(row[1] is Double)
            assertEquals(123.45, row[1] as Double, 0.001)
            assertEquals("hello", row[2])

            val d = db.queryDouble("SELECT CAST('456.78' AS REAL)", emptyArray())
            assertNotNull(d)
            assertEquals(456.78, d!!, 0.001)
        } finally {
            db.close()
            tempFile.delete()
        }
    }

    @Test
    fun testRealExcelImportIfPresent() {
        val testXlsx = System.getenv("HOSPITAL_TEST_XLSX")?.takeIf { it.isNotBlank() }?.let(::File)
        if (testXlsx == null || !testXlsx.isFile) {
            println("Skipping real Excel test: set HOSPITAL_TEST_XLSX to a local workbook")
            return
        }
        val tempDb = File.createTempFile("test_import", ".db")
        tempDb.deleteOnExit()
        val db = JdbcHospitalDb(tempDb)
        try {
            val book = StaxXlsxReader.openBook(testXlsx)
            val parsedDate = FileNameParser.parse(testXlsx.name)?.display
            db.importWorkbook(book, parsedDate, testXlsx.name) { sheet, rows, idx, total ->
                println("Imported $sheet: $rows rows ($idx/$total)")
            }
            assertTrue(db.hasImportedData())
            val counts = db.tableRowCounts()
            println("Table counts: $counts")
            assertTrue(counts.isNotEmpty())
            assertTrue(counts.all { it.second > 0 })

            // Test DashboardRepo queries
            val repo = DashboardRepo(db)
            val years = db.query("SELECT DISTINCT year FROM outpatient_service WHERE year IS NOT NULL ORDER BY year", emptyArray())
                .mapNotNull { it.firstOrNull()?.toString() }
            println("Available years: $years")
            assertTrue(years.isNotEmpty())

            val filter = DashboardRepo.Filters(years = years.takeLast(2))
            val kpi = repo.kpiSet(filter, 0)
            println("KPI opd: ${kpi.opd}")
            assertNotNull(kpi)
            assertTrue((kpi.opd ?: 0.0) > 0)

            // KPI 明細必須可回推原 KPI 總值，避免點擊後院區數字與上方卡片不一致。
            val latest = repo.latestMonth()!!
            val latestKpi = repo.kpiForMonth(latest.first, latest.second)
            val additive = mapOf(
                "門診人次" to latestKpi.opd, "急診人次" to latestKpi.er,
                "總診次" to latestKpi.sessions, "住院人次" to latestKpi.ipdAdm,
                "住院人日" to latestKpi.ipdDays, "院外門診" to latestKpi.offsite,
                "血液透析" to latestKpi.dialysis, "健檢人次" to latestKpi.checkup,
                "總收入" to latestKpi.incomeTotal, "自費收入" to latestKpi.incomeSelf
            )
            additive.forEach { (name, expected) ->
                val details = repo.branchKpiDetails(latest.first, latest.second, name)
                assertEquals("$name 院區加總", expected, details.sumOf { it.value }, 0.01)
                details.forEach { assertEquals(it.value, it.trend3.last()!!, 0.01) }
            }
            val occupancy = repo.branchKpiDetails(latest.first, latest.second, "總佔床率(實開病床)")
            assertTrue(occupancy.isNotEmpty())
            assertTrue(occupancy.all { it.value in 0.0..150.0 })
            val bedTotals = db.query(
                "SELECT SUM(CAST(admission_days AS REAL)), SUM(CAST(actual_bed_days AS REAL)) " +
                    "FROM bed_type_service WHERE year=? AND month=? AND " +
                    "(major_category IS NULL OR TRIM(major_category) NOT IN ('其他','產後（小孩）','產後(小孩)'))",
                arrayOf(latest.first, latest.second)
            ).single()
            val weighted = (bedTotals[0] as Number).toDouble() / (bedTotals[1] as Number).toDouble() * 100
            assertEquals("全院總佔床率應為住院人日／實際床日數", weighted, latestKpi.occ, 0.0001)
            val branch = occupancy.first().branch
            val anchorMonth = latest.second.toInt()
            val branchYtd = db.query(
                "SELECT SUM(CAST(admission_days AS REAL)), SUM(CAST(actual_bed_days AS REAL)) " +
                    "FROM bed_type_service WHERE branch_name=? AND year=? AND CAST(month AS INTEGER) <= ? AND " +
                    "(major_category IS NULL OR TRIM(major_category) NOT IN ('其他','產後（小孩）','產後(小孩)'))",
                arrayOf(branch, latest.first, anchorMonth)
            ).single()
            assertEquals(
                "院區年度佔床率必須先合計人日／床日再相除",
                (branchYtd[0] as Number).toDouble() / (branchYtd[1] as Number).toDouble() * 100,
                occupancy.first().ytd!!, 0.0001
            )
            val bedText = repo.branchAnalysisText(branch, "病床利用率")
            assertTrue(bedText?.contains("病床大類別") == true)
            val opdText = repo.branchAnalysisText(branch, "門急診服務")
            assertTrue(opdText?.contains("近三個月門急診") == true)
        } finally {
            db.close()
            tempDb.delete()
        }
    }
}
