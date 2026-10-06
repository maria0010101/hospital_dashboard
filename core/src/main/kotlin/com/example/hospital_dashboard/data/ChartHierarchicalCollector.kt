package com.example.hospital_dashboard.data

import java.util.Locale

/**
 * 圖表多階層資料收集器：
 * 依據圖表篩選條件自動彙整【第 1 階層：院區】、【第 2 階層：部別】、【第 3 階層：科別】與【第 4 階層：醫師別】明細，
 * 供本機混淆脫敏後上傳至 AI 進行深度多維度歸因分析。
 */
object ChartHierarchicalCollector {

    fun collect(
        repo: DashboardRepo,
        chartTitle: String,
        filters: DashboardRepo.Filters
    ): String = buildString {
        val (y, m) = repo.anchorYm(filters) ?: (114 to 8)
        val selectedYear = filters.years.firstOrNull()?.toIntOrNull() ?: y
        val selectedMonth = filters.months.firstOrNull()?.toIntOrNull() ?: m
        val primaryBranch = filters.branches.firstOrNull()

        appendLine("【圖表基本資訊與篩選條件】")
        appendLine("- 指標標題：$chartTitle")
        appendLine("- 資料年月：${selectedYear}年${selectedMonth}月")
        appendLine("- 院區條件：${if (filters.branches.isEmpty()) "全院區（全部）" else filters.branches.joinToString("、")}")
        appendLine("- 科別條件：${if (filters.depts.isEmpty()) "全部科別" else filters.depts.joinToString("、")}")
        appendLine("- 疫苗排除：${if (filters.excludeVaccine) "已排除疫苗施打人次" else "未排除（含疫苗施打）"}")
        appendLine()

        val drillConfig = DrillConfigs.find(chartTitle)
        if (drillConfig != null) {
            val metricType = drillConfig.metricType
            val unit = drillConfig.unit

            // ── 第 1 階層：院區層級 ──
            appendLine("【第 1 階層：各院區營運概況（單位：$unit）】")
            val branchStats = repo.universalDrillStats(
                metricType = metricType,
                targetLevel = "BRANCH",
                branch = null,
                deptDiv = null,
                dept = null,
                year = selectedYear,
                month = selectedMonth,
                showYoy = true,
                excludeVaccine = filters.excludeVaccine
            ).filter { it.value > 0.0 }

            if (branchStats.isNotEmpty()) {
                branchStats.forEach { s ->
                    val priorStr = s.prior?.let { "${Fmt.compact(it)} $unit" } ?: "無同期"
                    val yoyStr = s.deltaPct?.let { String.format(Locale.TAIWAN, "%+.1f%%", it) } ?: "—"
                    val trendStr = if (s.recent3.isNotEmpty()) {
                        s.recent3.filterNotNull().joinToString(" → ") { Fmt.compact(it) }
                    } else "—"
                    appendLine("- 院區【${s.name}】：當月 ${Fmt.compact(s.value)} $unit（去年同期：$priorStr，YoY：$yoyStr；近三月走勢：$trendStr）")
                }
            } else {
                appendLine("（無院區層級非零資料）")
            }
            appendLine()

            // ── 第 2 階層：部別層級 ──
            appendLine("【第 2 階層：各部別分布明細】")
            val divStats = repo.universalDrillStats(
                metricType = metricType,
                targetLevel = "DIVISION",
                branch = primaryBranch,
                deptDiv = null,
                dept = null,
                year = selectedYear,
                month = selectedMonth,
                showYoy = true,
                excludeVaccine = filters.excludeVaccine
            ).filter { it.value > 0.0 }

            if (divStats.isNotEmpty()) {
                divStats.forEach { s ->
                    val priorStr = s.prior?.let { "${Fmt.compact(it)} $unit" } ?: "無同期"
                    val yoyStr = s.deltaPct?.let { String.format(Locale.TAIWAN, "%+.1f%%", it) } ?: "—"
                    appendLine("- 部別【${s.name}】：當月 ${Fmt.compact(s.value)} $unit（去年同期：$priorStr，YoY：$yoyStr）")
                }
            } else {
                appendLine("（無部別層級非零資料）")
            }
            appendLine()

            // ── 第 3 階層：重點科別層級 ──
            appendLine("【第 3 階層：重點科別排名與服務量】")
            val deptStats = repo.universalDrillStats(
                metricType = metricType,
                targetLevel = "DEPARTMENT",
                branch = primaryBranch,
                deptDiv = null,
                dept = null,
                year = selectedYear,
                month = selectedMonth,
                showYoy = true,
                excludeVaccine = filters.excludeVaccine
            ).filter { it.value > 0.0 }.sortedByDescending { it.value }

            val topDepts = deptStats.take(12)
            if (topDepts.isNotEmpty()) {
                topDepts.forEachIndexed { idx, s ->
                    val priorStr = s.prior?.let { "${Fmt.compact(it)} $unit" } ?: "無同期"
                    val yoyStr = s.deltaPct?.let { String.format(Locale.TAIWAN, "%+.1f%%", it) } ?: "—"
                    val secInfo = if (s.secondaryValue != null && s.secondaryLabel != null) "，${s.secondaryLabel}：${Fmt.compact(s.secondaryValue)}" else ""
                    val avgInfo = if (s.avgPerUnit != null && s.avgLabel != null) "，平均：${String.format(Locale.TAIWAN, "%.1f", s.avgPerUnit)} ${s.avgLabel}" else ""
                    appendLine("  ${idx + 1}. 科別【${s.name}】：${Fmt.compact(s.value)} $unit（同期：$priorStr，YoY：$yoyStr$secInfo$avgInfo）")
                }
            } else {
                appendLine("（無科別層級非零資料）")
            }
            appendLine()

            // ── 第 4 階層：醫師服務量層級 ──
            if (drillConfig.maxLevels == 4 && topDepts.isNotEmpty()) {
                appendLine("【第 4 階層：重點科別核心醫師服務量明細】")
                // 取前 4 個科別各展開醫師明細
                topDepts.take(4).forEach { dept ->
                    val docStats = repo.universalDrillStats(
                        metricType = metricType,
                        targetLevel = "DOCTOR",
                        branch = primaryBranch,
                        deptDiv = null,
                        dept = dept.name,
                        year = selectedYear,
                        month = selectedMonth,
                        showYoy = true,
                        excludeVaccine = filters.excludeVaccine
                    ).filter { it.value > 0.0 }.sortedByDescending { it.value }

                    if (docStats.isNotEmpty()) {
                        appendLine("- 【${dept.name}】重點醫師服務量（共 ${docStats.size} 位執業醫師）：")
                        docStats.take(6).forEach { doc ->
                            val secStr = if (doc.secondaryValue != null) "，診次：${doc.secondaryValue.toInt()} 診" else ""
                            val avgStr = if (doc.avgPerUnit != null) "，平均人次/診：${String.format(Locale.TAIWAN, "%.1f", doc.avgPerUnit)}" else ""
                            appendLine("  * 醫師【${doc.name}】：${Fmt.compact(doc.value)} $unit$secStr$avgStr")
                        }
                    }
                }
            }
        } else {
            // 針對其他非 UniversalDrill 報表（如病床、手術、自費、營運收入等）
            appendLine("【相關營運與指標深入數據】")
            val allBranches = repo.anonymizerDictionary().branches
            val targetBranches = if (filters.branches.isNotEmpty()) filters.branches else allBranches.take(3)
            targetBranches.forEach { br ->
                val branchText = repo.branchAnalysisText(br, chartTitle)
                if (branchText != null) {
                    appendLine(branchText)
                }
            }
        }
    }
}
