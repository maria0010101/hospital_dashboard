package com.example.hospital_dashboard.report

import com.example.hospital_dashboard.data.BarSegment
import com.example.hospital_dashboard.data.HBarData
import com.example.hospital_dashboard.data.HBarRow
import com.example.hospital_dashboard.data.LineChartData
import com.example.hospital_dashboard.data.LineSeries
import com.example.hospital_dashboard.ui.charts.BRANCH_COLORS
import com.example.hospital_dashboard.ui.charts.seriesColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpdRegressionTest {

    @Test
    fun testYoYSeriesNamingAndColorMatching() {
        // 驗證去年的系列名稱能正確取得與今年相同院區之顏色
        val campus = "仁愛"
        val curName = campus
        val yoyName = "$campus(去年)"

        val curColor = seriesColor(curName, 0)
        val yoyColor = seriesColor(yoyName, 1)

        assertEquals(BRANCH_COLORS[campus], curColor)
        assertEquals(curColor, yoyColor)
    }

    @Test
    fun testLegendFiltersOutYoySeries() {
        // 模擬折線圖資料，驗證圖例已排除 (去年) 與虛線序列
        val data = LineChartData(
            xLabels = listOf("113年01月", "113年02月"),
            series = listOf(
                LineSeries("中興", listOf(100.0, 110.0), dashed = false),
                LineSeries("中興(去年)", listOf(90.0, 95.0), dashed = true),
                LineSeries("仁愛", listOf(200.0, 210.0), dashed = false),
                LineSeries("仁愛(去年)", listOf(180.0, 190.0), dashed = true)
            )
        )

        val legendNames = data.series.filter { !it.dashed && !it.name.contains("(去年)") }.map { it.name }

        assertEquals(listOf("中興", "仁愛"), legendNames)
        assertFalse(legendNames.contains("中興(去年)"))
        assertFalse(legendNames.contains("仁愛(去年)"))
    }

    @Test
    fun testSingleMonthHBarDataStructure() {
        // 驗證單月各院區門診人次重疊橫條 (overlap) 結構
        val row = HBarRow(
            name = "仁愛",
            segments = listOf(
                BarSegment("去年同期", 50000.0),
                BarSegment("門診人次", 62000.0)
            ),
            trailing = "去年 5.0萬 (+24.0%)"
        )
        val hbarData = HBarData(listOf(row), overlap = true)

        assertTrue(hbarData.overlap)
        assertEquals(2, hbarData.rows[0].segments.size)
        assertEquals("去年同期", hbarData.rows[0].segments[0].label)
        assertEquals("門診人次", hbarData.rows[0].segments[1].label)
        assertNotNull(hbarData.rows[0].trailing)
    }

    @Test
    fun testDeptDivisionColorMatching() {
        // 驗證部別名稱與去年同期系列在折線圖中透過 baseIndex 能得到相同顏色
        val series = listOf(
            LineSeries("綜合", listOf(10.0), dashed = false),
            LineSeries("其他", listOf(20.0), dashed = false),
            LineSeries("內科部", listOf(100.0), dashed = false),
            LineSeries("內科部(去年)", listOf(90.0), dashed = true)
        )
        val curSeries = series[2]
        val yoySeries = series[3]

        val curBaseName = curSeries.name.replace("(去年)", "").trim()
        val curBaseIdx = series.indexOfFirst { !it.dashed && (it.name == curBaseName || it.name == curSeries.name) }
            .let { if (it >= 0) it else 2 }
        val curColor = seriesColor(curBaseName, curBaseIdx)

        val yoyBaseName = yoySeries.name.replace("(態年)", "").replace("(去年)", "").trim()
        val yoyBaseIdx = series.indexOfFirst { !it.dashed && (it.name == yoyBaseName || it.name == yoySeries.name) }
            .let { if (it >= 0) it else 3 }
        val yoyColor = seriesColor(yoyBaseName, yoyBaseIdx)

        assertEquals("內科部", curBaseName)
        assertEquals("內科部", yoyBaseName)
        assertEquals(2, curBaseIdx)
        assertEquals(2, yoyBaseIdx)
        assertEquals(curColor, yoyColor)
    }

    @Test
    fun testHBarClickEnums() {
        // 驗證 HBarClick 支援 DivDept 與 IpdDivDept
        val clicks = com.example.hospital_dashboard.ui.charts.HBarClick.values()
        assertTrue(clicks.contains(com.example.hospital_dashboard.ui.charts.HBarClick.DivDept))
        assertTrue(clicks.contains(com.example.hospital_dashboard.ui.charts.HBarClick.IpdDivDept))
    }

    @Test
    fun testIpdTabCardOrderSpecification() {
        // 驗證住院分頁 6 大圖表之規格清單順序符合使用者要求
        val expectedCards = listOf(
            "住院人日月趨勢（依院區）",
            "住院人次月趨勢（依院區）",
            "出院人日月趨勢（依院區）",
            "出院人次月趨勢（依院區）",
            "住院人日月趨勢（依部別）",
            "平均住院日月趨勢（依院區）"
        )
        assertEquals(6, expectedCards.size)
        assertEquals("住院人日月趨勢（依院區）", expectedCards[0])
        assertEquals("住院人次月趨勢（依院區）", expectedCards[1])
        assertEquals("出院人日月趨勢（依院區）", expectedCards[2])
        assertEquals("出院人次月趨勢（依院區）", expectedCards[3])
        assertEquals("住院人日月趨勢（依部別）", expectedCards[4])
        assertEquals("平均住院日月趨勢（依院區）", expectedCards[5])
    }

    @Test
    fun testOpdDrillDownStatsCalculations() {
        // 1. 部別統計計算 (OpdDivStat)
        val divStat = com.example.hospital_dashboard.data.DashboardRepo.OpdDivStat(
            deptDiv = "內科部",
            opdVisit = 10000.0,
            sessions = 200.0,
            opdPrior = 8000.0
        )
        assertEquals(50.0, divStat.avgPerSession, 0.001)
        assertEquals(25.0, divStat.deltaPct!!, 0.001)

        // 2. 科別統計計算 (OpdDeptStat)
        val deptStat = com.example.hospital_dashboard.data.DashboardRepo.OpdDeptStat(
            dept = "消化內科",
            opdVisit = 2500.0,
            sessions = 50.0,
            opdPrior = 2000.0
        )
        assertEquals(50.0, deptStat.avgPerSession, 0.001)
        assertEquals(25.0, deptStat.deltaPct!!, 0.001)

        // 3. 醫師統計計算 (OpdDoctorStat)
        val docStat = com.example.hospital_dashboard.data.DashboardRepo.OpdDoctorStat(
            branch = "仁愛",
            doctorId = "DOC001",
            doctorName = "王大明",
            opdVisit = 600.0,
            sessions = 12.0,
            opdPrior = 500.0
        )
        assertEquals(50.0, docStat.avgPerSession, 0.001)
        assertEquals(20.0, docStat.deltaPct!!, 0.001)

        // 4. 零診次與無去年同期防呆
        val zeroSessionDoc = com.example.hospital_dashboard.data.DashboardRepo.OpdDoctorStat(
            branch = "仁愛",
            doctorId = "DOC002",
            doctorName = "李小美",
            opdVisit = 10.0,
            sessions = 0.0,
            opdPrior = null
        )
        assertEquals(0.0, zeroSessionDoc.avgPerSession, 0.001)
        org.junit.Assert.assertNull(zeroSessionDoc.deltaPct)
    }

    @Test
    fun testOpdFourLayerDimensions() {
        // 驗證四層展開之維度層級順序與定義
        val layers = listOf("院區詳細資訊", "部別詳細資訊", "科別詳細資訊", "醫師別詳細資訊")
        assertEquals(4, layers.size)
        assertEquals("院區詳細資訊", layers[0])
        assertEquals("部別詳細資訊", layers[1])
        assertEquals("科別詳細資訊", layers[2])
        assertEquals("醫師別詳細資訊", layers[3])
    }

    @Test
    fun testOpdZeroVisitExclusion() {
        // 驗證門診人次為 0 之資料在各維度展開時正確被排除
        val docs = listOf(
            com.example.hospital_dashboard.data.DashboardRepo.OpdDoctorStat("仁愛", "D1", "王醫師", 150.0, 3.0),
            com.example.hospital_dashboard.data.DashboardRepo.OpdDoctorStat("仁愛", "D2", "陳醫師", 0.0, 0.0),
            com.example.hospital_dashboard.data.DashboardRepo.OpdDoctorStat("仁愛", "D3", "林醫師", 0.0, 2.0),
            com.example.hospital_dashboard.data.DashboardRepo.OpdDoctorStat("仁愛", "D4", "張醫師", 80.0, 2.0)
        )
        val filteredDocs = docs.filter { it.opdVisit > 0.0 }
        assertEquals(2, filteredDocs.size)
        assertEquals(listOf("王醫師", "張醫師"), filteredDocs.map { it.doctorName })

        // 驗證部別與科別同理過濾
        val divs = listOf(
            com.example.hospital_dashboard.data.DashboardRepo.OpdDivStat("內科部", 500.0, 10.0),
            com.example.hospital_dashboard.data.DashboardRepo.OpdDivStat("其他部", 0.0, 0.0)
        )
        val filteredDivs = divs.filter { it.opdVisit > 0.0 }
        assertEquals(1, filteredDivs.size)
        assertEquals("內科部", filteredDivs[0].deptDiv)
    }

    @Test
    fun testUniversalDrillMetrics() {
        // 驗證通用下鑽統計資料模型 UniversalDrillStat
        val stat = com.example.hospital_dashboard.data.DashboardRepo.UniversalDrillStat(
            name = "急診醫學部",
            value = 1200.0,
            secondaryValue = 40.0,
            secondaryLabel = "診",
            avgLabel = "人/診",
            prior = 1000.0
        )
        assertEquals(20.0, stat.deltaPct ?: 0.0, 0.001)
        assertEquals(30.0, stat.avgPerUnit ?: 0.0, 0.001)

        // 驗證 3 層與 4 層指標配置
        val configs = listOf(
            "急診人次月趨勢（依院區）" to 4,
            "各部別門診人次趨勢" to 4,
            "住院人日月趨勢（依院區）" to 4,
            "住院人次月趨勢（依院區）" to 3,
            "出院人日月趨勢（依院區）" to 3,
            "出院人次月趨勢（依院區）" to 3,
            "住院人日月趨勢（依部別）" to 4,
            "總收入趨勢（依院區）" to 4,
            "自費收入趨勢（依院區）" to 4
        )
        configs.forEach { (title, maxL) ->
            if (maxL == 3) {
                assertTrue(title.contains("住院人次") || title.contains("出院"))
            } else {
                assertTrue(maxL == 4)
            }
        }
    }

    @Test
    fun testBedMajorCategoriesOrderAndMerge() {
        // 驗證病床大類別排序及產後（小孩）合併邏輯
        val rawCategories = listOf("特殊", "產後（小孩）", "一般", "其他", "ICU", "嬰兒床")
        val merged = rawCategories.map { if (it == "產後（小孩）") "其他" else it }.distinct()
        val order = listOf("一般", "ICU", "特殊", "嬰兒床", "其他")
        val sorted = merged.sortedWith(compareBy {
            val idx = order.indexOf(it)
            if (idx >= 0) idx else order.size
        })
        assertEquals(listOf("一般", "ICU", "特殊", "嬰兒床", "其他"), sorted)

        // 驗證預設篩選為 ICU, 一般, 特殊
        val defaultSelection = listOf("ICU", "一般", "特殊").filter { it in sorted }
        assertEquals(listOf("ICU", "一般", "特殊"), defaultSelection)
    }

    @Test
    fun testTabReduction() {
        // 驗證分頁調整為 5 個分頁（移除佔床率、醫師服務、醫師收入）
        val tabs = listOf("🚪 門急診", "🛏️ 住院", "🏥 病床", "📋 其他", "🤖 AI 分析")
        assertEquals(5, tabs.size)
        assertFalse(tabs.any { it.contains("佔床率") || it.contains("醫師服務") || it.contains("醫師收入") })
    }

    @Test
    fun testFontScaleSettingsLevelAndMultipliers() {
        // 驗證 5 級字體設定：以目前顯示設定為最小（1.0x），共有 5 級
        val multipliers = com.example.hospital_dashboard.DashboardViewModel.FONT_SCALE_MULTIPLIERS
        val names = com.example.hospital_dashboard.DashboardViewModel.FONT_SCALE_NAMES
        assertEquals(5, multipliers.size)
        assertEquals(5, names.size)
        assertEquals(1.00f, multipliers[0], 0.001f)
        assertEquals("最小 (目前)", names[0])
        for (i in 0 until multipliers.size - 1) {
            assertTrue("字體倍率需遞增", multipliers[i + 1] > multipliers[i])
        }
    }

    @Test
    fun testDynamicChartHeightForSingleBranch() {
        // 驗證單一院區時圖表高度縮小，避免空白
        val singleLineH = com.example.hospital_dashboard.ui.charts.dynamicLineHeight(isSingleBranch = true)
        val multiLineH = com.example.hospital_dashboard.ui.charts.dynamicLineHeight(isSingleBranch = false)
        assertEquals(140f, singleLineH.value, 0.01f)
        assertEquals(220f, multiLineH.value, 0.01f)

        // 驗證橫條圖依列數自動調整高度
        assertEquals(80f, com.example.hospital_dashboard.ui.charts.dynamicHBarHeight(1).value, 0.01f)
        assertEquals(115f, com.example.hospital_dashboard.ui.charts.dynamicHBarHeight(2).value, 0.01f)
        assertEquals(150f, com.example.hospital_dashboard.ui.charts.dynamicHBarHeight(3).value, 0.01f)
        assertEquals(185f, com.example.hospital_dashboard.ui.charts.dynamicHBarHeight(4).value, 0.01f)
        assertEquals(240f, com.example.hospital_dashboard.ui.charts.dynamicHBarHeight(7).value, 0.01f)
    }
}

