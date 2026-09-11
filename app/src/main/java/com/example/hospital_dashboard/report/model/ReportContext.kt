package com.example.hospital_dashboard.report.model

/**
 * 報表執行上下文 (ReportContext)。
 * 依據 SRS.md Section 9 與 AGENTS.md Rule 8 定義。
 * 封裝報表執行的全部維度條件與控制選項，禁止讓底層報表自行解析 UI 控制項。
 */
data class ReportContext(
    val reportId: String,
    val years: List<String>,                             // 民國年字串清單，如 ["113", "114"]
    val months: List<String> = emptyList(),              // 月份清單（空 = 全選）
    val organizationLevel: OrganizationLevel = OrganizationLevel.ALL,
    val campuses: List<String> = emptyList(),            // 院區名稱（空 = 依層級處理）
    val deptDivs: List<String> = emptyList(),            // 編制部別（空 = 全選）
    val departments: List<String> = emptyList(),         // 科別名稱（空 = 全選）
    val showYoy: Boolean = true,                         // 是否計算去年同期比對
    val showTotal: Boolean = true,                       // 是否計算合計值
    val customFilters: Map<String, Any?> = emptyMap()    // 自訂擴充條件
) {
    /** 驗證上下文基本有效性，防止空年度或非法設定。 */
    fun validate(): Result<Unit> {
        if (reportId.isBlank()) {
            return Result.failure(IllegalArgumentException("ReportContext: reportId 不得為空"))
        }
        if (years.isEmpty()) {
            return Result.failure(IllegalArgumentException("ReportContext: 必須至少指定一個統計年度 (years)"))
        }
        return Result.success(Unit)
    }

    /** 取得民國年整數清單 */
    val yearInts: List<Int>
        get() = years.mapNotNull { it.toIntOrNull() }.sorted()

    /** 取得月份整數清單 */
    val monthInts: List<Int>
        get() = months.mapNotNull { it.toIntOrNull() }.sorted()
}
