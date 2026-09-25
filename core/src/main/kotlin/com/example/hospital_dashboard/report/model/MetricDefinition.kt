package com.example.hospital_dashboard.report.model

/**
 * 指標聚合方式。
 */
enum class AggregationType {
    SUM,
    COUNT,
    AVG,
    DISTINCT_COUNT,
    RATIO,
    CUSTOM
}

/**
 * 指標定義規格。
 * 依據 SRS.md Section 13 與 AGENTS.md Rule 9-10 定義。
 * 指標必須具備明確之定義、來源欄位與聚合方式，不得任意簡化或混淆。
 */
data class MetricDefinition(
    val id: String,
    val name: String,
    val sourceColumn: String,
    val aggregation: AggregationType = AggregationType.SUM,
    val format: String = "#,##0",
    val unit: String = "人次",
    val nullHandling: Double = 0.0,
    val customExpression: String? = null
)
