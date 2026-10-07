package com.example.hospital_dashboard.desktop

import com.example.hospital_dashboard.data.*
import com.example.hospital_dashboard.desktop.ui.charts.ChartContent
import com.example.hospital_dashboard.desktop.ui.charts.ChartImageExporter
import org.junit.Assert.*
import org.junit.Test
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

class ChartExportTest {

    @Test
    fun testChartImageExporterSaveJpeg() {
        val width = 600
        val height = 400
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.color = Color.WHITE
        g.fillRect(0, 0, width, height)
        g.color = Color.BLUE
        g.drawLine(10, 10, 590, 390)
        g.dispose()

        val tempFile = File.createTempFile("test_chart_export", ".jpg")
        try {
            val saved = ChartImageExporter.saveJpegToFile(img, tempFile)
            assertTrue("JPEG 應成功儲存至檔案", saved)
            assertTrue("檔案應存在", tempFile.exists())
            assertTrue("檔案大小應大於 0", tempFile.length() > 0)

            // 重新讀取驗證 JPEG 格式與尺寸
            val loaded = ImageIO.read(tempFile)
            assertNotNull("應能成功以 ImageIO 讀取回 JPEG 圖檔", loaded)
            assertEquals("圖片寬度應符合", width, loaded.width)
            assertEquals("圖片高度應符合", height, loaded.height)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testChartTextReportTemplateGenerationAllTypes() {
        val filters = DashboardRepo.Filters(
            years = listOf("114"),
            months = listOf("8"),
            branches = listOf("仁愛"),
            deptDivs = emptyList(),
            depts = emptyList(),
            excludeVaccine = false
        )

        // 1. Line
        val lineData = LineChartData(
            xLabels = listOf("114年06月", "114年07月", "114年08月"),
            series = listOf(
                LineSeries("仁愛", listOf(100.0, 120.0, 150.0), dashed = false)
            )
        )
        val lineReport = ChartAnalysisTemplate.generate(
            ChartAnalysisTemplate.ReportInput(
                title = "門診人次月趨勢",
                filters = filters,
                lineData = lineData
            )
        )
        assertTrue(lineReport.contains("門診人次月趨勢"))
        assertTrue(lineReport.contains("仁愛"))

        // 2. HBar
        val hbarData = HBarData(
            rows = listOf(
                HBarRow("仁愛", listOf(BarSegment("當月", 100.0))),
                HBarRow("中興", listOf(BarSegment("當月", 85.0)))
            )
        )
        val hbarReport = ChartAnalysisTemplate.generate(
            ChartAnalysisTemplate.ReportInput(
                title = "各院區門診人次排行",
                filters = filters,
                hbarData = hbarData
            )
        )
        assertTrue(hbarReport.contains("各院區門診人次排行"))
        assertTrue(hbarReport.contains("仁愛"))

        // 3. VBar
        val vbarData = VBarData(
            groups = listOf(
                VBarGroup("內科", listOf(BarSegment("門診", 50.0))),
                VBarGroup("外科", listOf(BarSegment("門診", 40.0)))
            )
        )
        val vbarReport = ChartAnalysisTemplate.generate(
            ChartAnalysisTemplate.ReportInput(
                title = "各科別服務量比較",
                filters = filters,
                vbarData = vbarData
            )
        )
        assertTrue(vbarReport.contains("各科別服務量比較"))
        assertTrue(vbarReport.contains("內科"))

        // 4. Pie
        val pieData = PieData(
            slices = listOf(
                PieSlice("門診收入", 60.0),
                PieSlice("住院收入", 40.0)
            )
        )
        val pieReport = ChartAnalysisTemplate.generate(
            ChartAnalysisTemplate.ReportInput(
                title = "醫療收入結構比",
                filters = filters,
                pieData = pieData
            )
        )
        assertTrue(pieReport.contains("醫療收入結構比"))
        assertTrue(pieReport.contains("門診收入"))

        // 5. Table
        val tableData = TableData(
            columns = listOf("科別", "服務量", "佔比"),
            rows = listOf(
                listOf(TableCell("心臟內科"), TableCell("1,200"), TableCell("35%")),
                listOf(TableCell("胃腸肝膽科"), TableCell("800"), TableCell("25%"))
            )
        )
        val tableReport = ChartAnalysisTemplate.generate(
            ChartAnalysisTemplate.ReportInput(
                title = "門診科別佔比統計表",
                filters = filters,
                tableData = tableData
            )
        )
        assertTrue(tableReport.contains("門診科別佔比統計表"))
        assertTrue(tableReport.contains("心臟內科"))
    }

    @Test
    fun testAnonymizerAndRehydrationForAiReport() {
        val anonymizer = Anonymizer()
        anonymizer.codeOf("仁愛院區", Anonymizer.Kind.Branch)
        anonymizer.codeOf("中興院區", Anonymizer.Kind.Branch)
        anonymizer.codeOf("心臟內科", Anonymizer.Kind.Dept)
        anonymizer.codeOf("小兒科", Anonymizer.Kind.Dept)
        anonymizer.codeOf("王大明", Anonymizer.Kind.Doctor)
        anonymizer.codeOf("李小美", Anonymizer.Kind.Doctor)

        val originalText = "仁愛院區的心臟內科由王大明醫師看診，中興院區的小兒科由李小美醫師負責。"
        val obfuscated = anonymizer.obfuscate(originalText)

        assertFalse("混淆後不應包含真實醫師名字", obfuscated.contains("王大明"))
        assertFalse("混淆後不應包含真實醫師名字", obfuscated.contains("李小美"))
        assertFalse("混淆後不應包含真實院區", obfuscated.contains("仁愛院區"))

        val rehydrated = anonymizer.rehydrate(obfuscated)
        assertTrue("還原後應包含王大明", rehydrated.contains("王大明"))
        assertTrue("還原後應包含李小美", rehydrated.contains("李小美"))
        assertTrue("還原後應包含仁愛院區", rehydrated.contains("仁愛院區"))
    }

    @Test
    fun testChartContentTypes() {
        val line = ChartContent.Line(
            title = "門診趨勢",
            data = LineChartData(listOf("1月"), emptyList())
        )
        assertEquals("門診趨勢", line.title)

        val hbar = ChartContent.HBar(
            title = "各院區排行",
            data = HBarData(emptyList())
        )
        assertEquals("各院區排行", hbar.title)

        val vbar = ChartContent.VBar(
            title = "各科別服務量",
            data = VBarData(emptyList())
        )
        assertEquals("各科別服務量", vbar.title)

        val pie = ChartContent.Pie(
            title = "收入佔比",
            data = PieData(emptyList())
        )
        assertEquals("收入佔比", pie.title)

        val table = ChartContent.Table(
            title = "病床統計表",
            data = TableData(emptyList(), emptyList())
        )
        assertEquals("病床統計表", table.title)
    }
}
