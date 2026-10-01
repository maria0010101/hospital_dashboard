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
                "血液透析" to latestKpi.dialysis, "健檢人次" to latestKpi.checkup
            )
            additive.forEach { (name, expected) ->
                val details = repo.branchKpiDetails(latest.first, latest.second, name)
                assertEquals("$name 院區加總", expected, details.sumOf { it.value }, 0.01)
                details.forEach { assertEquals(it.value, it.trend3.last()!!, 0.01) }
            }
            val occupancy = repo.branchKpiDetails(latest.first, latest.second, "平均佔床率")
            assertTrue(occupancy.isNotEmpty())
            assertTrue(occupancy.all { it.value in 0.0..150.0 })
            val branch = occupancy.first().branch
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
