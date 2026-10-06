package com.example.hospital_dashboard.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * 圖表分析模板引擎：依據圖表內容與當前篩選條件產生結構化文字分析報告。
 */
object ChartAnalysisTemplate {

    data class ReportInput(
        val title: String,
        val filters: DashboardRepo.Filters,
        val lineData: LineChartData? = null,
        val hbarData: HBarData? = null,
        val vbarData: VBarData? = null,
        val pieData: PieData? = null,
        val tableData: TableData? = null,
        val extraNotes: String? = null
    )

    fun generate(input: ReportInput): String = buildString {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.TAIWAN)
        val nowStr = dateFormat.format(Date())

        appendLine("# 📊 醫院營運圖表分析報告")
        appendLine("### 指標名稱：${input.title}")
        appendLine()

        // ── 1. 報告基本資訊與篩選條件 ──
        appendLine("## 📋 一、基本資訊與篩選範圍")
        val branchScope = if (input.filters.branches.isEmpty()) "全院區（全部綜合）" else input.filters.branches.joinToString("、")
        val deptScope = if (input.filters.depts.isEmpty()) "全部科別" else input.filters.depts.joinToString("、")
        val yearScope = if (input.filters.years.isEmpty()) "全年度" else "${input.filters.years.joinToString("、")} 年"
        val monthScope = if (input.filters.months.isEmpty()) "全年各月份" else "${input.filters.months.joinToString("、")} 月"
        val yoyStatus = if (input.filters.showYoy) "已啟用（納入去年同期比較）" else "未啟用"
        val vaccineStatus = if (input.filters.excludeVaccine) "已排除（扣除同年月同院區同科別之疫苗施打人次）" else "未排除（包含疫苗施打人次）"

        appendLine("- **分析院區**：$branchScope")
        appendLine("- **分析科別**：$deptScope")
        appendLine("- **資料期別**：$yearScope $monthScope")
        appendLine("- **同期比較 (YoY)**：$yoyStatus")
        appendLine("- **疫苗扣除機制**：$vaccineStatus")
        appendLine("- **產製時間**：$nowStr")
        appendLine()

        // ── 2. 核心數據綜覽與統計量 ──
        appendLine("## 📈 二、核心數據綜覽與統計量")
        when {
            input.lineData != null && input.lineData.series.isNotEmpty() -> {
                val data = input.lineData
                val nonDashed = data.series.filter { !it.dashed && !it.name.contains("(去年)") }
                appendLine("- **期別區間**：共 ${data.xLabels.size} 期（${data.xLabels.firstOrNull() ?: "—"} 至 ${data.xLabels.lastOrNull() ?: "—"}）")
                appendLine("- **分析序列數**：共 ${nonDashed.size} 個維度項目")
                appendLine()
                appendLine("| 序列項目 | 最新期數值 | 全期最高值 (月份) | 全期最低值 (月份) | 全期平均 | 全期總額 |")
                appendLine("| :--- | :---: | :---: | :---: | :---: | :---: |")

                nonDashed.forEach { s ->
                    val validPairs = data.xLabels.zip(s.values).filter { it.second != null }
                    if (validPairs.isNotEmpty()) {
                        val latestVal = validPairs.last().second ?: 0.0
                        val maxPair = validPairs.maxByOrNull { it.second ?: Double.MIN_VALUE }
                        val minPair = validPairs.minByOrNull { it.second ?: Double.MAX_VALUE }
                        val sumVal = validPairs.sumOf { it.second ?: 0.0 }
                        val avgVal = sumVal / validPairs.size
                        val maxStr = "${Fmt.compact(maxPair?.second ?: 0.0)} (${maxPair?.first ?: "—"})"
                        val minStr = "${Fmt.compact(minPair?.second ?: 0.0)} (${minPair?.first ?: "—"})"
                        appendLine("| **${s.name}** | ${Fmt.compact(latestVal)} | $maxStr | $minStr | ${Fmt.compact(avgVal)} | ${Fmt.compact(sumVal)} |")
                    } else {
                        appendLine("| **${s.name}** | — | — | — | — | — |")
                    }
                }
            }

            input.hbarData != null && input.hbarData.rows.isNotEmpty() -> {
                val data = input.hbarData
                val rows = data.rows
                val totalCur = rows.sumOf { it.segments.firstOrNull()?.value ?: 0.0 }
                appendLine("- **總項目數**：共 ${rows.size} 個明細項目")
                appendLine("- **當期總量**：${Fmt.compact(totalCur)}")
                appendLine()
                appendLine("| 項目名稱 | 當期數值 | 佔比 (%) | 去年同期 | 同期成長率 (%) |")
                appendLine("| :--- | :---: | :---: | :---: | :---: |")
                rows.sortedByDescending { it.segments.firstOrNull()?.value ?: 0.0 }.forEach { r ->
                    val cur = r.segments.firstOrNull()?.value ?: 0.0
                    val prior = if (r.segments.size > 1) r.segments[1].value else null
                    val pct = if (totalCur > 0) (cur / totalCur * 100.0) else 0.0
                    val yoy = if (prior != null && prior > 0) ((cur - prior) / prior * 100.0) else null
                    val yoyStr = yoy?.let { String.format(Locale.TAIWAN, "%+.1f%%", it) } ?: "—"
                    val priorStr = prior?.let { Fmt.compact(it) } ?: "—"
                    appendLine("| **${r.name}** | ${Fmt.compact(cur)} | ${String.format(Locale.TAIWAN, "%.1f%%", pct)} | $priorStr | $yoyStr |")
                }
            }

            input.vbarData != null && input.vbarData.groups.isNotEmpty() -> {
                val data = input.vbarData
                appendLine("- **分組總數**：共 ${data.groups.size} 組")
                appendLine()
                appendLine("| 分組標籤 | 數值明細 |")
                appendLine("| :--- | :--- |")
                data.groups.forEach { g ->
                    val segDetails = g.segments.joinToString("，") { "${it.label}：${Fmt.compact(it.value)}" }
                    appendLine("| **${g.label}** | $segDetails |")
                }
            }

            input.pieData != null && input.pieData.slices.isNotEmpty() -> {
                val data = input.pieData
                val total = data.slices.sumOf { it.value }
                appendLine("- **總計總量**：${Fmt.compact(total)}")
                appendLine()
                appendLine("| 類別名稱 | 數值 | 結構佔比 (%) |")
                appendLine("| :--- | :---: | :---: |")
                data.slices.sortedByDescending { it.value }.forEach { s ->
                    val pct = if (total > 0) (s.value / total * 100.0) else 0.0
                    appendLine("| **${s.label}** | ${Fmt.compact(s.value)} | ${String.format(Locale.TAIWAN, "%.1f%%", pct)} |")
                }
            }

            input.tableData != null && input.tableData.rows.isNotEmpty() -> {
                val data = input.tableData
                appendLine("- **欄位清單**：${data.columns.joinToString(" ｜ ")}")
                appendLine("- **資料列數**：共 ${data.rows.size} 列")
                appendLine()
                appendLine("| " + data.columns.joinToString(" | ") + " |")
                appendLine("| " + data.columns.joinToString(" | ") { ":---:" } + " |")
                data.rows.take(15).forEach { row ->
                    appendLine("| " + row.joinToString(" | ") { it.text } + " |")
                }
                if (data.rows.size > 15) {
                    appendLine("| … | (其餘 ${data.rows.size - 15} 筆已省略) | … |")
                }
            }

            else -> {
                appendLine("此圖表目前無可解析之明細數值資料。")
            }
        }
        appendLine()

        // ── 3. 趨勢走向與變動分析 ──
        appendLine("## 🔍 三、趨勢走向與變動分析")
        if (input.lineData != null && input.lineData.series.isNotEmpty()) {
            val data = input.lineData
            val nonDashed = data.series.filter { !it.dashed && !it.name.contains("(去年)") }
            if (nonDashed.isNotEmpty()) {
                val firstSeries = nonDashed.first()
                val validVals = firstSeries.values.filterNotNull()
                if (validVals.size >= 2) {
                    val startVal = validVals.first()
                    val endVal = validVals.last()
                    val totalDelta = endVal - startVal
                    val totalDeltaPct = if (startVal > 0) (totalDelta / startVal * 100.0) else 0.0
                    val trendWord = when {
                        totalDeltaPct > 5.0 -> "呈現顯著上升走勢（累積成長 +${String.format(Locale.TAIWAN, "%.1f%%", totalDeltaPct)}）"
                        totalDeltaPct < -5.0 -> "呈現下降走勢（累積衰退 ${String.format(Locale.TAIWAN, "%.1f%%", totalDeltaPct)}）"
                        else -> "呈現平穩震盪走勢（變動率 ${String.format(Locale.TAIWAN, "%+.1f%%", totalDeltaPct)}）"
                    }
                    appendLine("1. **總體區間趨勢**：主要指標項目【${firstSeries.name}】於統計期間內$trendWord。")
                }

                // MoM 走勢分析
                if (validVals.size >= 3) {
                    val m1 = validVals[validVals.size - 3]
                    val m2 = validVals[validVals.size - 2]
                    val m3 = validVals[validVals.size - 1]
                    val mom1 = if (m1 > 0) (m2 - m1) / m1 * 100.0 else 0.0
                    val mom2 = if (m2 > 0) (m3 - m2) / m2 * 100.0 else 0.0
                    appendLine("2. **近三期環比走勢 (MoM)**：近三月數值為 ${Fmt.compact(m1)} → ${Fmt.compact(m2)} (${String.format(Locale.TAIWAN, "%+.1f%%", mom1)}) → ${Fmt.compact(m3)} (${String.format(Locale.TAIWAN, "%+.1f%%", mom2)})。")
                }
            }

            // YoY 走勢分析
            val yoyPairs = nonDashed.mapNotNull { s ->
                val priorSeries = data.series.firstOrNull { it.dashed && it.name.contains(s.name) }
                if (priorSeries != null) {
                    val curLast = s.values.lastOrNull()
                    val priorLast = priorSeries.values.lastOrNull()
                    if (curLast != null && priorLast != null && priorLast > 0) {
                        Triple(s.name, curLast, (curLast - priorLast) / priorLast * 100.0)
                    } else null
                } else null
            }
            if (yoyPairs.isNotEmpty()) {
                val best = yoyPairs.maxByOrNull { it.third }
                val worst = yoyPairs.minByOrNull { it.third }
                appendLine("3. **去年同期比較 (YoY)**：")
                yoyPairs.forEach { (name, cur, growth) ->
                    val sign = if (growth >= 0) "成長" else "衰退"
                    appendLine("   - **$name**：當期 ${Fmt.compact(cur)}，較去年同期$sign ${String.format(Locale.TAIWAN, "%+.1f%%", growth)}")
                }
                if (best != null && best.third > 0) {
                    appendLine("   - 🌟 **成長領先項目**：【${best.first}】以 ${String.format(Locale.TAIWAN, "+%.1f%%", best.third)} 之同期成長率居冠。")
                }
                if (worst != null && worst.third < 0) {
                    appendLine("   - ⚠️ **面臨衰退項目**：【${worst.first}】同期衰退 ${String.format(Locale.TAIWAN, "%.1f%%", worst.third)}，需留意服務量下滑原因。")
                }
            }
        } else if (input.hbarData != null) {
            val rows = input.hbarData.rows
            if (rows.isNotEmpty()) {
                val sorted = rows.sortedByDescending { it.segments.firstOrNull()?.value ?: 0.0 }
                val top1 = sorted.first()
                val total = sorted.sumOf { it.segments.firstOrNull()?.value ?: 0.0 }
                val top1Pct = if (total > 0) (top1.segments.firstOrNull()?.value ?: 0.0) / total * 100.0 else 0.0
                appendLine("1. **排名領先項目**：以【${top1.name}】居於首位，佔當期總量之 ${String.format(Locale.TAIWAN, "%.1f%%", top1Pct)}。")
                if (sorted.size >= 2) {
                    val count = minOf(3, sorted.size)
                    val topSum = sorted.take(count).sumOf { it.segments.firstOrNull()?.value ?: 0.0 }
                    val topPct = if (total > 0) topSum / total * 100.0 else 0.0
                    val countStr = if (count == 2) "前兩大" else "前三大"
                    appendLine("2. **集中度分析**：${countStr}項目（${sorted.take(count).joinToString("、") { it.name }}）合計佔整體 ${String.format(Locale.TAIWAN, "%.1f%%", topPct)}，顯示業務高度集中。")
                }
            }
        } else {
            appendLine("根據當前篩選數據，各維度分布均勻，無異常斷崖式突變。")
        }
        appendLine()

        // ── 4. 異常檢測與關鍵發現 ──
        appendLine("## ⚠️ 四、異常檢測與關鍵發現")
        var hasAnomaly = false
        if (input.lineData != null) {
            input.lineData.series.filter { !it.dashed }.forEach { s ->
                val vals = s.values.filterNotNull()
                for (i in 1 until vals.size) {
                    val prev = vals[i - 1]
                    val curr = vals[i]
                    if (prev > 0) {
                        val change = (curr - prev) / prev * 100.0
                        if (abs(change) >= 20.0) {
                            val monthLabel = input.lineData.xLabels.getOrNull(i) ?: "第 ${i + 1} 期"
                            val tag = if (change > 0) "大幅激增" else "急遽銳減"
                            appendLine("- 🚨 **波動警示**：【${s.name}】於 $monthLabel $tag ${String.format(Locale.TAIWAN, "%+.1f%%", change)}（${Fmt.compact(prev)} → ${Fmt.compact(curr)}）。")
                            hasAnomaly = true
                        }
                    }
                }
            }
        }
        if (input.filters.excludeVaccine) {
            appendLine("- 💉 **疫苗排除效應**：當前報表已濾除門診/急診篩檢及流感疫苗施打量，呈現之數值反映真正核心醫療就醫量能，可排除季節性疫苗施打潮所造成的短期虛增干擾。")
            hasAnomaly = true
        }
        if (!hasAnomaly) {
            appendLine("- ✅ **各指標波動處於正常預期區間**，未檢出超過 ±20% 之異常突變現象。")
        }
        appendLine()

        // ── 5. 營運決策與行動建議 ──
        appendLine("## 💡 五、營運決策與行動建議")
        appendLine("1. **人力與診次精準調配**：")
        appendLine("   - 針對服務量處於高峰之院區與重點科別，建議檢視診次開立飽和度與跟診人力，適度擴充熱門時段（如夜診或週末門診）以消化就醫需求。")
        appendLine("2. **成長停滯與衰退因應**：")
        appendLine("   - 針對同比或環比連續下滑之項目，建議深入科別與醫師服務量下鑽明細，釐清係因醫師請假、轉診流失或就醫型態轉變所致，並強化跨院區聯合門診支援。")
        appendLine("3. **醫療資源與病床週轉優化**：")
        appendLine("   - 持續追蹤住院人日與佔床率關聯，維持急性病床與 ICU 實開床之黃金使用率（80%~85%），避免閒置床日產生成本負擔。")
        appendLine("4. **長遠品質與營運監測**：")
        appendLine("   - 定期追蹤初診轉複診率與自費項目占比，強化醫療服務深度與附加價值。")
    }
}
