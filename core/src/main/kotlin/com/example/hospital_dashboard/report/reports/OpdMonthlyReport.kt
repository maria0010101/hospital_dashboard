package com.example.hospital_dashboard.report.reports

import com.example.hospital_dashboard.data.HospitalDb
import com.example.hospital_dashboard.report.engine.AggregationEngine
import com.example.hospital_dashboard.report.engine.DimensionEngine
import com.example.hospital_dashboard.report.model.AggregationType
import com.example.hospital_dashboard.report.model.DimensionType
import com.example.hospital_dashboard.report.model.MetricDefinition
import com.example.hospital_dashboard.report.model.OrganizationLevel
import com.example.hospital_dashboard.report.model.ReportContext
import com.example.hospital_dashboard.report.model.ReportResult
import com.example.hospital_dashboard.report.model.ReportRow
import com.example.hospital_dashboard.report.registry.ReportDefinition

/**
 * PoC 代表性報表 OPD_001：門診人次月趨勢與多維度分析。
 * 支援全院 (ALL)、院區 (CAMPUS)、科別 (DEPARTMENT) 及院區＋科別 (CAMPUS_DEPARTMENT) 4 種層級分析。
 * 保留現行 outpatient_service 門診人次、初診與複診統計之業務定義。
 */
class OpdMonthlyReport : ReportDefinition {

    override val reportId: String = "OPD_001"
    override val name: String = "門診人次月趨勢與多維度分析"
    override val category: String = "門診業務"
    override val primarySourceTable: String = "outpatient_service"

    override val supportedDimensions: Set<DimensionType> = setOf(
        DimensionType.TIME,
        DimensionType.CAMPUS,
        DimensionType.DEPT_DIV,
        DimensionType.DEPARTMENT
    )

    override val metrics: List<MetricDefinition> = listOf(
        MetricDefinition(
            id = "opd_visit_count",
            name = "門診人次",
            sourceColumn = "opd_visit_count",
            aggregation = AggregationType.SUM,
            unit = "人次"
        ),
        MetricDefinition(
            id = "first_visit_count",
            name = "初診人次",
            sourceColumn = "first_visit_count",
            aggregation = AggregationType.SUM,
            unit = "人次"
        ),
        MetricDefinition(
            id = "return_visit_count",
            name = "複診人次",
            sourceColumn = "return_visit_count",
            aggregation = AggregationType.SUM,
            unit = "人次"
        )
    )

    override fun execute(context: ReportContext, db: HospitalDb): ReportResult {
        context.validate().getOrThrow()

        val groupByCols = DimensionEngine.resolveGroupByColumns(context.organizationLevel, includeTime = true)
        val (whereSql, params) = DimensionEngine.buildWhereClause(context, yearOffset = 0, supportedDimensions)
        val selectMetricExprs = AggregationEngine.buildSelectExpressions(metrics)

        val selectCols = (groupByCols + selectMetricExprs).joinToString(", ")
        val groupBySql = groupByCols.joinToString(", ")
        val mainSql = "SELECT $selectCols FROM $primarySourceTable WHERE $whereSql GROUP BY $groupBySql"

        val rawRows = db.query(mainSql, params)

        // 查詢去年同期資料 (若需計算 YoY)
        val yoyDataMap = mutableMapOf<String, Map<String, Double>>()
        if (context.showYoy) {
            val (yoyWhereSql, yoyParams) = DimensionEngine.buildWhereClause(context, yearOffset = -1, supportedDimensions)
            val yoySql = "SELECT $selectCols FROM $primarySourceTable WHERE $yoyWhereSql GROUP BY $groupBySql"
            val yoyRows = db.query(yoySql, yoyParams)

            for (r in yoyRows) {
                val key = buildRelativeTimeKey(r, groupByCols)
                val mValues = extractMetricValues(r, groupByCols.size)
                yoyDataMap[key] = mValues
            }
        }

        // 解析主要結果
        val reportRows = mutableListOf<ReportRow>()
        val totals = mutableMapOf<String, Double>()
        val yoyTotals = mutableMapOf<String, Double>()

        for (m in metrics) {
            totals[m.id] = 0.0
            yoyTotals[m.id] = 0.0
        }

        val activeDims = mutableListOf(DimensionType.TIME)
        when (context.organizationLevel) {
            OrganizationLevel.ALL -> {}
            OrganizationLevel.CAMPUS -> activeDims.add(DimensionType.CAMPUS)
            OrganizationLevel.DEPARTMENT -> activeDims.add(DimensionType.DEPARTMENT)
            OrganizationLevel.CAMPUS_DEPARTMENT -> {
                activeDims.add(DimensionType.CAMPUS)
                activeDims.add(DimensionType.DEPARTMENT)
            }
        }

        for (r in rawRows) {
            val dimMap = mutableMapOf<DimensionType, String>()
            val y = r.getOrNull(0)?.toString() ?: ""
            val m = r.getOrNull(1)?.toString() ?: ""
            dimMap[DimensionType.TIME] = "${y}年${m.padStart(2, '0')}月"

            var colIdx = 2
            when (context.organizationLevel) {
                OrganizationLevel.CAMPUS -> {
                    dimMap[DimensionType.CAMPUS] = r.getOrNull(colIdx)?.toString() ?: ""
                    colIdx++
                }
                OrganizationLevel.DEPARTMENT -> {
                    dimMap[DimensionType.DEPARTMENT] = r.getOrNull(colIdx)?.toString() ?: ""
                    colIdx++
                }
                OrganizationLevel.CAMPUS_DEPARTMENT -> {
                    dimMap[DimensionType.CAMPUS] = r.getOrNull(colIdx)?.toString() ?: ""
                    colIdx++
                    dimMap[DimensionType.DEPARTMENT] = r.getOrNull(colIdx)?.toString() ?: ""
                    colIdx++
                }
                OrganizationLevel.ALL -> {}
            }

            val currentMetrics = extractMetricValues(r, groupByCols.size)
            for ((k, v) in currentMetrics) {
                totals[k] = (totals[k] ?: 0.0) + v
            }

            // 比對去年同期
            val relativeKey = buildRelativeTimeKey(r, groupByCols)
            val previousMetrics = yoyDataMap[relativeKey] ?: emptyMap()
            val diffMap = mutableMapOf<String, Double>()
            val pctMap = mutableMapOf<String, Double>()

            for (metric in metrics) {
                val curVal = currentMetrics[metric.id] ?: 0.0
                val prevVal = previousMetrics[metric.id] ?: 0.0
                yoyTotals[metric.id] = (yoyTotals[metric.id] ?: 0.0) + prevVal
                diffMap[metric.id] = AggregationEngine.calculateDiff(curVal, prevVal)
                pctMap[metric.id] = AggregationEngine.calculatePercentChange(curVal, prevVal)
            }

            reportRows.add(
                ReportRow(
                    dimensionValues = dimMap,
                    metrics = currentMetrics,
                    yoyMetrics = previousMetrics,
                    yoyDiff = diffMap,
                    yoyPct = pctMap
                )
            )
        }

        return ReportResult(
            reportId = reportId,
            context = context,
            activeDimensions = activeDims,
            rows = reportRows,
            totals = totals,
            yoyTotals = yoyTotals
        )
    }

    private fun extractMetricValues(r: List<Any?>, metricStartIdx: Int): Map<String, Double> {
        val map = mutableMapOf<String, Double>()
        for (i in metrics.indices) {
            val raw = r.getOrNull(metricStartIdx + i)
            val v = (raw as? Number)?.toDouble() ?: raw?.toString()?.toDoubleOrNull() ?: 0.0
            map[metrics[i].id] = v
        }
        return map
    }

    /**
     * 產出相對時間鍵值（忽略年，只比對月＋組織維度），以利本期與去年同期正確對齊。
     */
    private fun buildRelativeTimeKey(r: List<Any?>, groupByCols: List<String>): String {
        // r[0] 是 year, r[1] 是 month, 後面是組織欄位
        val month = r.getOrNull(1)?.toString() ?: ""
        val orgKeys = if (groupByCols.size > 2) {
            (2 until groupByCols.size).map { r.getOrNull(it)?.toString() ?: "" }
        } else {
            emptyList()
        }
        return (listOf(month) + orgKeys).joinToString("|")
    }
}
