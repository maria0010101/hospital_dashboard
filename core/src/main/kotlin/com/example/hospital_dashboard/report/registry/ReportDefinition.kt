package com.example.hospital_dashboard.report.registry

import com.example.hospital_dashboard.data.HospitalDb
import com.example.hospital_dashboard.report.model.DimensionType
import com.example.hospital_dashboard.report.model.MetricDefinition
import com.example.hospital_dashboard.report.model.ReportContext
import com.example.hospital_dashboard.report.model.ReportResult

/**
 * 報表定義介面 (ReportDefinition)。
 * 依據 SRS.md Section 11 & 16 定義。
 * 每一份納入平台的報表均需實作此介面以提供標準 metadata、支援維度與執行能力。
 */
interface ReportDefinition {
    val reportId: String
    val name: String
    val category: String
    val supportedDimensions: Set<DimensionType>
    val metrics: List<MetricDefinition>
    val primarySourceTable: String

    /**
     * 依據指定上下文執行報表資料查詢與指標聚合。
     */
    fun execute(context: ReportContext, db: HospitalDb): ReportResult
}
