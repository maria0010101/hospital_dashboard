package com.example.hospital_dashboard.data

enum class DrillHierarchyType {
    BRANCH_FIRST,
    DIVISION_FIRST
}

enum class UniversalDrillLevel {
    LEVEL_1, LEVEL_2, LEVEL_3, LEVEL_4
}

data class DrillMetricConfig(
    val titleMatch: String,
    val metricType: String,
    val unit: String,
    val hierarchy: DrillHierarchyType = DrillHierarchyType.BRANCH_FIRST,
    val maxLevels: Int = 4,
    val yFormatter: ((Double) -> String)? = null,
    val aliases: List<String> = emptyList()
)

object DrillConfigs {
    val ALL = listOf(
        // DIVISION_FIRST 優先比對，避免「各部別」被「門診人次」或「住院人日」誤判為 BRANCH_FIRST
        DrillMetricConfig("各部別門診人次趨勢", "OPD", "人次", DrillHierarchyType.DIVISION_FIRST, 4, aliases = listOf("各部別門診人次")),
        DrillMetricConfig("住院人日月趨勢（依部別）", "IPD_DAYS", "人日", DrillHierarchyType.DIVISION_FIRST, 4, aliases = listOf("各部別住院人日")),
        DrillMetricConfig("門診人次月趨勢（依院區）", "OPD", "人次", DrillHierarchyType.BRANCH_FIRST, 4, aliases = listOf("各院區門診人次", "門診人次")),
        DrillMetricConfig("急診人次月趨勢（依院區）", "ER", "人次", DrillHierarchyType.BRANCH_FIRST, 4, aliases = listOf("各院區急診人次", "急診人次")),
        DrillMetricConfig("初診人次月趨勢（依院區）", "FIRST_VISIT", "人次", DrillHierarchyType.BRANCH_FIRST, 3, aliases = listOf("各院區初診人次", "初診人次")),
        DrillMetricConfig("住院人日月趨勢（依院區）", "IPD_DAYS", "人日", DrillHierarchyType.BRANCH_FIRST, 4, aliases = listOf("各院區住院人日", "住院人日")),
        DrillMetricConfig("住院人次月趨勢（依院區）", "IPD_COUNT", "人次", DrillHierarchyType.BRANCH_FIRST, 3, aliases = listOf("各院區住院人次", "住院人次")),
        DrillMetricConfig("出院人日月趨勢（依院區）", "DIS_DAYS", "人日", DrillHierarchyType.BRANCH_FIRST, 3, aliases = listOf("各院區出院人日", "出院人日")),
        DrillMetricConfig("出院人次月趨勢（依院區）", "DIS_COUNT", "人次", DrillHierarchyType.BRANCH_FIRST, 3, aliases = listOf("各院區出院人次", "出院人次")),
        DrillMetricConfig("總收入趨勢（依院區）", "INC_TOTAL", "元", DrillHierarchyType.BRANCH_FIRST, 4, Fmt::money, aliases = listOf("各院區總收入", "總收入")),
        DrillMetricConfig("自費收入趨勢（依院區）", "INC_SELF", "元", DrillHierarchyType.BRANCH_FIRST, 4, Fmt::money, aliases = listOf("各院區自費收入", "自費收入"))
    )

    fun find(title: String): DrillMetricConfig? {
        if (title.contains("累計")) return null
        ALL.firstOrNull { title == it.titleMatch || title.contains(it.titleMatch) }?.let { return it }
        return ALL.firstOrNull { cfg -> cfg.aliases.any { alias -> title.contains(alias) } }
    }
}
