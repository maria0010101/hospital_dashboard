# REPORT_DESIGN.md — 目標統一維度與設定驅動報表平台架構設計

> 依據 `AGENTS.md`（Rule 1, 7–13, 30, 38）及 `SRS.md`（Section 7–17, Section 27 TASK 8）產出之目標設計文件。
> 專案路徑：`/home/hpd/AndroidStudioProjects/hospital_dashboard`
> 產出日期：2026-09-11

---

## 1. 核心設計願景（Core Architecture）

依據 SRS 核心原則：「**不為四種分析情境寫四份報表，而是一份報表支援四種 ReportContext**」。

```text
                  ┌────────────────────────┐
                  │  ReportConfiguration   │ (報表設定與持久化)
                  └───────────┬────────────┘
                              ↓
                  ┌────────────────────────┐
                  │     ReportContext      │ (執行期上下文：期間/組織層級/維度篩選)
                  └───────────┬────────────┘
                              ↓
                  ┌────────────────────────┐
                  │     ReportRegistry     │ (報表註冊中心：Metadata/指標/維度)
                  └───────────┬────────────┘
                              ↓
        ┌─────────────────────┼─────────────────────┐
        ↓                     ↓                     ↓
┌───────────────┐     ┌───────────────┐     ┌───────────────┐
│DimensionEngine│     │Query & Dataset│     │ Aggregation   │
│(正規化維度解析)│     │(下推 SQL 查詢) │     │ Engine(指標聚合)│
└───────┬───────┘     └───────┬───────┘     └───────┬───────┘
        └─────────────────────┼─────────────────────┘
                              ↓
                  ┌────────────────────────┐
                  │      ReportResult      │ (純資料結果物件：列/指標/小計/合計)
                  └───────────┬────────────┘
                              ↓
                  ┌────────────────────────┐
                  │    Report Renderer     │ (呈現解耦：Compose Canvas / Table / CSV)
                  └────────────────────────┘
```

---

## 2. 領域模型設計（Domain Models）

### 2.1 組織分析層級 (`OrganizationLevel`)

```kotlin
package com.example.hospital_dashboard.report.model

enum class OrganizationLevel {
    ALL,                 // 全院總體
    CAMPUS,              // 單一或多院區視角
    DEPARTMENT,          // 單一或多科別視角
    CAMPUS_DEPARTMENT    // 院區 × 科別交叉視角
}
```

### 2.2 統一維度標識 (`DimensionType`)

```kotlin
enum class DimensionType {
    TIME,                // 時間（年、月、區間）
    CAMPUS,              // 院區（如甲院區、乙院區）
    DEPT_DIV,            // 部別（如內科部、外科部）
    DEPARTMENT,          // 科別（如心臟內科、一般外科）
    DOCTOR,              // 醫師（選用）
    MAJOR_CATEGORY,      // 病床大類別（選用）
    CATEGORY,            // 病床類別（選用）
    NURSING_STATION      // 護理站（選用）
}
```

### 2.3 執行上下文 (`ReportContext`)

```kotlin
data class ReportContext(
    val reportId: String,
    val years: List<String>,                      // 如 listOf("113", "114")
    val months: List<String> = emptyList(),       // 空表示全選
    val organizationLevel: OrganizationLevel = OrganizationLevel.ALL,
    val campuses: List<String> = emptyList(),     // 選定院區 (空 = ALL 或全部可用)
    val deptDivs: List<String> = emptyList(),     // 選定部別
    val departments: List<String> = emptyList(),  // 選定科別
    val showYoy: Boolean = true,                  // 是否計算去年同期
    val showTotal: Boolean = true,                // 是否產生合計
    val customFilters: Map<String, Any?> = emptyMap()
)
```

### 2.4 指標定義 (`MetricDefinition`)

```kotlin
enum class AggregationType {
    SUM, COUNT, AVG, DISTINCT_COUNT, RATIO, CUSTOM
}

data class MetricDefinition(
    val id: String,
    val name: String,
    val sourceColumn: String,
    val aggregation: AggregationType,
    val format: String = "#,##0",
    val unit: String = "人次",
    val nullHandling: Double = 0.0
)
```

---

## 3. 報表註冊中心 (`ReportRegistry`)

透過單一入口管理所有可供查詢的報表定義：

```kotlin
interface ReportDefinition {
    val reportId: String
    val name: String
    val category: String
    val supportedDimensions: Set<DimensionType>
    val metrics: List<MetricDefinition>
    val primarySourceTable: String
    
    fun execute(context: ReportContext, db: HospitalDb): ReportResult
}
```

`ReportRegistry` 提供動態查詢、依 category 歸納與預設 context 生成的能力。

---

## 4. 呈現層解耦（Renderer Separation）

`ReportResult` 僅攜帶純結構化統計矩陣：
```kotlin
data class ReportResult(
    val reportId: String,
    val context: ReportContext,
    val dimensions: List<DimensionType>,
    val headers: List<String>,
    val rows: List<ReportRow>,
    val totals: Map<String, Double> = emptyMap(),
    val yoyComparison: Map<String, Double>? = null
)
```

Compose UI 圖表元件（如 `LineChartData`、`HBarData`）由獨立的適配器（Adapter / Renderer）自 `ReportResult` 轉換生成，不再於查詢層混雜繪圖邏輯。

---

## 5. 代表性 PoC 報表選定（PoC Candidate）

### 選定報表：`R001 / OPD_001` 門診人次月趨勢與院區/科別分析

* **來源資料表**：`outpatient_service`
* **支援分析層級**：
  1. `ALL`：全院門診人次月趨勢
  2. `CAMPUS`：依院區分群之門診人次趨勢與各院區累計橫條
  3. `DEPARTMENT`：特定部別／科別之門診人次趨勢
  4. `CAMPUS_DEPARTMENT`：特定院區 × 特定期別之門診人次交叉趨勢
* **指標**：
  * `opd_visit_count`（門診總人次，SUM）
  * `first_visit_count`（初診人次，SUM）
  * `return_visit_count`（複診人次，SUM）
* **驗證方式**：
  * 比對既有 `DashboardRepo.opdMonthly(f)` 與 `DashboardRepo.branchOpdBar(f)` 之數值，確保在相同篩選下的總計、各院區數值與 YoY 增減百分之百吻合。
