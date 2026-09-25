package com.example.hospital_dashboard.report.registry

import java.util.concurrent.ConcurrentHashMap

/**
 * 報表註冊中心 (ReportRegistry)。
 * 依據 SRS.md Section 11 (FR-001, FR-002) 實作。
 * 集中管理系統中所有已註冊報表之 Metadata、維度支援與執行實例。
 */
object ReportRegistry {
    private val reports = ConcurrentHashMap<String, ReportDefinition>()

    /** 註冊單一報表 */
    fun register(definition: ReportDefinition) {
        reports[definition.reportId] = definition
    }

    /** 依 ID 取得報表 */
    fun get(reportId: String): ReportDefinition? = reports[reportId]

    /** 取得所有已註冊報表 */
    fun all(): List<ReportDefinition> = reports.values.toList().sortedBy { it.reportId }

    /** 依類別分類取得報表 */
    fun byCategory(category: String): List<ReportDefinition> =
        reports.values.filter { it.category == category }.sortedBy { it.reportId }

    /** 取得所有報表分類清單 */
    fun categories(): List<String> =
        reports.values.map { it.category }.distinct().sorted()

    /** 清空註冊表 (主要用於測試隔離) */
    fun clear() {
        reports.clear()
    }

    /** 初始化預設報表清單 */
    fun initDefaultReports() {
        register(com.example.hospital_dashboard.report.reports.OpdMonthlyReport())
    }
}
