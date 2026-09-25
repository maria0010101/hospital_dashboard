package com.example.hospital_dashboard.report.engine

import com.example.hospital_dashboard.report.model.DimensionType
import com.example.hospital_dashboard.report.model.OrganizationLevel
import com.example.hospital_dashboard.report.model.ReportContext

/**
 * 維度引擎 (Dimension Engine)。
 * 依據 SRS.md Section 12 與 REPORT_DIMENSIONS.md 實作。
 * 統一處理 TIME、CAMPUS、DEPARTMENT 等維度轉換、WHERE 篩選條件與 GROUP BY 欄位。
 */
object DimensionEngine {

    /**
     * 產出 SQL WHERE 條件片段與參數陣列。
     * @param context 報表上下文
     * @param yearOffset 年度偏移量 (0 代表本期，-1 代表去年同期)
     * @param supportedDimensions 該報表支援之維度
     */
    fun buildWhereClause(
        context: ReportContext,
        yearOffset: Int = 0,
        supportedDimensions: Set<DimensionType> = setOf(DimensionType.TIME, DimensionType.CAMPUS, DimensionType.DEPARTMENT)
    ): Pair<String, Array<Any?>> {
        val clauses = mutableListOf<String>()
        val params = mutableListOf<Any?>()

        // 1. 時間維度 (年度)
        val targetYears = context.yearInts.map { (it + yearOffset).toString() }
        if (targetYears.isNotEmpty()) {
            clauses.add("year IN (${targetYears.joinToString(",") { "?" }})")
            params.addAll(targetYears)
        } else {
            // 防呆：無年度時防止產出無效語法
            clauses.add("1=1")
        }

        // 2. 時間維度 (月份)
        if (context.months.isNotEmpty()) {
            clauses.add("month IN (${context.months.joinToString(",") { "?" }})")
            params.addAll(context.months)
        }

        // 3. 院區維度
        if (supportedDimensions.contains(DimensionType.CAMPUS)) {
            when (context.organizationLevel) {
                OrganizationLevel.CAMPUS, OrganizationLevel.CAMPUS_DEPARTMENT -> {
                    if (context.campuses.isNotEmpty()) {
                        clauses.add("branch_name IN (${context.campuses.joinToString(",") { "?" }})")
                        params.addAll(context.campuses)
                    }
                }
                OrganizationLevel.ALL -> {
                    // 若指定了特定院區篩選
                    if (context.campuses.isNotEmpty()) {
                        clauses.add("branch_name IN (${context.campuses.joinToString(",") { "?" }})")
                        params.addAll(context.campuses)
                    }
                }
                OrganizationLevel.DEPARTMENT -> {
                    // 科別視角若有特定指定院區
                    if (context.campuses.isNotEmpty()) {
                        clauses.add("branch_name IN (${context.campuses.joinToString(",") { "?" }})")
                        params.addAll(context.campuses)
                    }
                }
            }
        }

        // 4. 部別與科別維度
        if (supportedDimensions.contains(DimensionType.DEPARTMENT) || supportedDimensions.contains(DimensionType.DEPT_DIV)) {
            if (context.deptDivs.isNotEmpty()) {
                clauses.add("dept_div IN (${context.deptDivs.joinToString(",") { "?" }})")
                params.addAll(context.deptDivs)
            }
            if (context.departments.isNotEmpty()) {
                clauses.add("dept IN (${context.departments.joinToString(",") { "?" }})")
                params.addAll(context.departments)
            }
        }

        val sql = if (clauses.isEmpty()) "1=1" else clauses.joinToString(" AND ")
        return sql to params.toTypedArray()
    }

    /**
     * 依據組織分析層級與報表要求，決定 GROUP BY 欄位清單。
     */
    fun resolveGroupByColumns(
        level: OrganizationLevel,
        includeTime: Boolean = true
    ): List<String> {
        val cols = mutableListOf<String>()
        if (includeTime) {
            cols.add("year")
            cols.add("month")
        }
        when (level) {
            OrganizationLevel.ALL -> {
                // 全院：僅以時間（若有）分組
            }
            OrganizationLevel.CAMPUS -> {
                cols.add("branch_name")
            }
            OrganizationLevel.DEPARTMENT -> {
                cols.add("dept")
            }
            OrganizationLevel.CAMPUS_DEPARTMENT -> {
                cols.add("branch_name")
                cols.add("dept")
            }
        }
        return cols
    }
}
