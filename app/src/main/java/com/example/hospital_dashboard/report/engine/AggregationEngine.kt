package com.example.hospital_dashboard.report.engine

import com.example.hospital_dashboard.report.model.AggregationType
import com.example.hospital_dashboard.report.model.MetricDefinition

/**
 * 聚合引擎 (Aggregation Engine)。
 * 依據 SRS.md Section 13 實作。
 * 負責指標聚合 SQL 產出、NULL/髒資料防護與 YoY 比對運算。
 */
object AggregationEngine {

    /** 純數值驗證防護，避免 SQLite 將字串髒資料強制轉成 0 破壞 AVG/SUM */
    fun numGuard(col: String): String =
        "$col NOT GLOB '*[^0-9.eE+-]*' AND $col NOT GLOB '*.*.*'"

    /**
     * 產出 SQL SELECT 指標聚合表達式清單。
     */
    fun buildSelectExpressions(metrics: List<MetricDefinition>): List<String> {
        return metrics.map { m ->
            when (m.aggregation) {
                AggregationType.SUM ->
                    "COALESCE(SUM(CASE WHEN ${numGuard(m.sourceColumn)} THEN CAST(${m.sourceColumn} AS REAL) ELSE 0.0 END), 0.0) AS ${m.id}"
                AggregationType.COUNT ->
                    "COUNT(${m.sourceColumn}) AS ${m.id}"
                AggregationType.DISTINCT_COUNT ->
                    "COUNT(DISTINCT ${m.sourceColumn}) AS ${m.id}"
                AggregationType.AVG ->
                    "COALESCE(AVG(CASE WHEN ${numGuard(m.sourceColumn)} THEN CAST(${m.sourceColumn} AS REAL) END), 0.0) AS ${m.id}"
                AggregationType.CUSTOM ->
                    "${m.customExpression ?: m.sourceColumn} AS ${m.id}"
                AggregationType.RATIO ->
                    "${m.customExpression ?: "0.0"} AS ${m.id}"
            }
        }
    }

    /** 計算同期增減值 (當期 - 去年同期) */
    fun calculateDiff(current: Double, previous: Double): Double = current - previous

    /** 計算同期增減百分比 ((當期 - 去年同期) / 去年同期 * 100) */
    fun calculatePercentChange(current: Double, previous: Double): Double {
        return if (previous != 0.0) {
            ((current - previous) / kotlin.math.abs(previous)) * 100.0
        } else {
            0.0
        }
    }
}
