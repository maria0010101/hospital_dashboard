package com.example.hospital_dashboard.report.model

/**
 * 統一報表平台支援之標準維度類型。
 * 依據 SRS.md Section 7 與 REPORT_DIMENSIONS.md 定義。
 */
enum class DimensionType(val displayName: String) {
    TIME("時間維度"),
    CAMPUS("院區維度"),
    DEPT_DIV("部別維度"),
    DEPARTMENT("科別維度"),
    DOCTOR("醫師維度"),
    MAJOR_CATEGORY("病床大類別"),
    CATEGORY("病床細項類別"),
    NURSING_STATION("護理站維度")
}
