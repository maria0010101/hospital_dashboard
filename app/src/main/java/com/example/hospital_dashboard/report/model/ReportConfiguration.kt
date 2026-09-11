package com.example.hospital_dashboard.report.model

/**
 * 報表持久化設定 (ReportConfiguration)。
 * 依據 SRS.md Section 10 (FR-008)。
 * 可轉換為 JSON 儲存於本機，支援套用、載入、修改與重設。
 */
data class ReportConfiguration(
    val reportId: String,
    val organizationLevel: String = OrganizationLevel.ALL.name,
    val years: List<String> = emptyList(),
    val months: List<String> = emptyList(),
    val campuses: List<String> = emptyList(),
    val deptDivs: List<String> = emptyList(),
    val departments: List<String> = emptyList(),
    val showYoy: Boolean = true,
    val showTotal: Boolean = true
) {
    fun toContext(): ReportContext {
        val orgLevel = try {
            OrganizationLevel.valueOf(organizationLevel)
        } catch (_: Exception) {
            OrganizationLevel.ALL
        }
        return ReportContext(
            reportId = reportId,
            years = years,
            months = months,
            organizationLevel = orgLevel,
            campuses = campuses,
            deptDivs = deptDivs,
            departments = departments,
            showYoy = showYoy,
            showTotal = showTotal
        )
    }

    companion object {
        fun fromContext(context: ReportContext): ReportConfiguration {
            return ReportConfiguration(
                reportId = context.reportId,
                organizationLevel = context.organizationLevel.name,
                years = context.years,
                months = context.months,
                campuses = context.campuses,
                deptDivs = context.deptDivs,
                departments = context.departments,
                showYoy = context.showYoy,
                showTotal = context.showTotal
            )
        }
    }
}
