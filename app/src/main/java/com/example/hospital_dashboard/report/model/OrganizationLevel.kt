package com.example.hospital_dashboard.report.model

/**
 * 組織分析層級。
 * 支援同一報表透過切換不同層級呈現全院、單一院區、特定科別或院區與科別交叉視角。
 * 遵循「Do not build four reports when you need one report with four analysis contexts」之核心原則。
 */
enum class OrganizationLevel(val displayName: String) {
    ALL("全院總體"),
    CAMPUS("院區視角"),
    DEPARTMENT("科別視角"),
    CAMPUS_DEPARTMENT("院區＋科別交叉視角")
}
