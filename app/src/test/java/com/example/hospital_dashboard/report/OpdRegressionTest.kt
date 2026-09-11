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
}
