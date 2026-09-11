# REPORT_MIGRATION.md — 報表遷移追蹤與回歸驗證矩陣

> 依據 `AGENTS.md`（Rule 17, 36, 40）及 `SRS.md`（Section 18, 28）產出之遷移狀態追蹤表。
> 專案路徑：`/home/hpd/AndroidStudioProjects/hospital_dashboard`
> 產出日期：2026-09-11

---

## 1. 遷移狀態總覽表

| 報表代碼 | 報表名稱 | 來源資料表 | 狀態 | 目標架構 | 統一維度 | 回歸測試 | 生產驗證 | 備註 |
|---|---|---|---|---|---|---|---|---|
| **OPD_001** | 門診人次月趨勢與分析 | `outpatient_service` | **VALIDATED** | 是 | 是 | Pass (5 tests) | 待上線 | **首份代表性 PoC 報表驗證通過** |
| OPD_002 | 急診人次月趨勢 | `outpatient_service` | DISCOVERED | 否 | 否 | 待執行 | 否 | 待 PoC 完成後依序排入 |
| OPD_003 | 科別門診人次 TOP20 | `outpatient_service` | DISCOVERED | 否 | 否 | 待執行 | 否 | — |
| OPD_004 | 初複診比例 | `outpatient_service` | DISCOVERED | 否 | 否 | 待執行 | 否 | — |
| OPD_005 | 各部別門診人次趨勢 | `outpatient_service` | DISCOVERED | 否 | 否 | 待執行 | 否 | — |
| OPD_006 | 各院區門診人次總覽 | `outpatient_service` | DISCOVERED | 否 | 否 | 待執行 | 否 | — |
| IPD_001 | 住院人次月趨勢 | `inpatient_service` | DISCOVERED | 否 | 否 | 待執行 | 否 | — |
| IPD_002 | 出院人次月趨勢 | `inpatient_service` | DISCOVERED | 否 | 否 | 待執行 | 否 | — |
| IPD_003 | 住院 vs 出院人日趨勢 | `inpatient_service` | DISCOVERED | 否 | 否 | 待執行 | 否 | — |
| IPD_004 | 各院區住院人次總覽 | `inpatient_service` | DISCOVERED | 否 | 否 | 待執行 | 否 | — |
| IPD_005 | Top 15 科別住院人次與日數 | `inpatient_service` | DISCOVERED | 否 | 否 | 待執行 | 否 | — |
| BED_001 | 實際佔床率月趨勢 | `bed_type_service` | DISCOVERED | 否 | 否 | 待執行 | 否 | — |
| BED_002 | 實際開床數月趨勢 | `bed_type_service` | DISCOVERED | 否 | 否 | 待執行 | 否 | — |
| BED_003 | 各院區實際佔床率 | `bed_type_service` | DISCOVERED | 否 | 否 | 待執行 | 否 | — |
| BED_004 | 各院區登記 vs 實開床數 | `bed_type_service` | DISCOVERED | 否 | 否 | 待執行 | 否 | — |
| BED_005 | 各院區 × 病床類別佔床率 | `bed_type_service` | DISCOVERED | 否 | 否 | 待執行 | 否 | — |
| BED_006 | 去年同期佔床率比較 | `bed_type_service` | DISCOVERED | 否 | 否 | 待執行 | 否 | — |
| BED_007 | 各院區病床明細表 | `bed_type_service` | DISCOVERED | 否 | 否 | 待執行 | 否 | — |
| OTH_001 | 院外門診部服務量趨勢 | `offsite_clinic_service` | DISCOVERED | 否 | 否 | 待執行 | 否 | — |
| OTH_002 | 各院外門診部累計服務量 | `offsite_clinic_service` | DISCOVERED | 否 | 否 | 待執行 | 否 | — |
| OTH_003 | 洗腎／健檢人次月趨勢 | `accounting_report` | DISCOVERED | 否 | 否 | 待執行 | 否 | — |
| OTH_004 | 手術／生產人次月趨勢 | `ops_management_indicators` | DISCOVERED | 否 | 否 | 待執行 | 否 | — |
| OTH_005 | 門診與住院收入趨勢 | `ops_management_indicators` | DISCOVERED | 否 | 否 | 待執行 | 否 | — |
| OTH_006 | 各院區總收入/自費累計 | `ops_management_indicators` | DISCOVERED | 否 | 否 | 待執行 | 否 | — |
| PHY_001 | 科別醫師服務量 TOP20 | `physician_service` | DISCOVERED | 否 | 否 | 待執行 | 否 | — |
| PHY_002 | 全院收入月趨勢 | `physician_service` | DISCOVERED | 否 | 否 | 待執行 | 否 | — |
| PHY_003 | 當月收入結構圓餅 | `physician_service` | DISCOVERED | 否 | 否 | 待執行 | 否 | — |

狀態標籤標準：`DISCOVERED` $\to$ `DESIGNED` $\to$ `POC` $\to$ `IN_MIGRATION` $\to$ `REGRESSION` $\to$ `VALIDATED` $\to$ `DONE`

---

## 2. 首份 PoC 遷移規劃（OPD_001）

- **原始碼路徑**：`DashboardRepo.kt`（`opdMonthly`, `branchOpdBar`, `opdDeptTop`）
- **目標架構路徑**：`com.example.hospital_dashboard.report.engine.*`、`com.example.hospital_dashboard.report.reports.OpdMonthlyReport`
- **驗證指標**：
  - 門診人次總和（`SUM(opd_visit_count)`）
  - 各月份、各院區數值
  - 去年同期（YoY）數值與增減比率
  - 缺失值/Null 防護

---

## 3. 驗證紀錄與證據 (Evidence)

- **2026-09-11 (PoC 通過)**:
  - 單元測試：`DimensionEngineTest`（全院、院區、科別、複合維度及 YoY offset WHERE/GROUP BY 測試全數通過）。
  - 單元測試：`AggregationEngineTest`（SUM、COUNT、AVG 髒資料防護及差異比率運算通過）。
  - 單元測試：`ReportContextAndRegistryTest`（Context 驗證、Configuration 序列化反序列化、Registry 註冊與清單取得通過）。
  - 單元測試：`ReportChartAdapterTest`（多序列趨勢折線與 YoY 虛線、水平排名長條圖降冪適配轉換通過）。
  - 建置測試：`./gradlew test assembleDebug` 執行通過（42 actionable tasks, BUILD SUCCESSFUL）。

- **2026-09-12 (病床分頁、其他分頁與全院加總篩選驗證通過)**:
  - **病床分頁 (BedTab)**:
    - `實際床佔床率月趨勢（依病床類別）`：多月折線（去年同期虛線同顏色，圖例無`(去年)`）；單月重疊橫條含去年同期半透明比對與 85% 目標線。
    - `實際開床率月趨勢（依病床類別）`：公式 `actual_open / registered * 100%`，單月重疊橫條以半透明登記床數 + 實色實開床數 + 尾標開床率（無去年同期）。
    - 新增 `實際床佔床率月趨勢（依院區）`（多月折線 + 單月重疊橫條含 YoY）。
    - 新增 `實際開床率月趨勢（依院區）`（多月折線 + 單月橫條：半透明登記床數 + 實色實開床數 + 開床率尾標）。
    - 移除原有 3 張報表與顯示範圍開關區塊。
    - 各院區獨立卡片熱力圖：排除「其他」，呈現各病床類別佔床率色階（RdYlGn）。
  - **其他分頁 (OtherTab)**:
    - `院外門診部服務量趨勢（依院區）`：多月折線 + 單月重疊橫條，整合點擊放大細項至各門診部明細卡片。
    - 獨立分拆：`洗腎人次（依院區）`、`健檢人次（依院區）`、`手術人次（依院區，門診+住院手術）`、`生產人次（依院區）`。
    - 獨立分拆：`總收入趨勢（依院區）`、`自費收入趨勢（依院區）`。
    - 累計收入：`各院區總收入（累計，單位千元）` 與 `各院區自費收入（累計，單位千元）` 增加半透明去年同期重疊比較。
  - **篩選底部面板 (FilterSheet)**:
    - 院區篩選新增「顯示全院」FilterChip，選取時各院區數據合併加總為「全院」序列，頂部按鈕標註 `⚙ 篩選 (全院)`。
  - **測試與實機驗證**:
    - 單元測試：`BedOtherRegressionTest`（7 項測試全數通過）。
    - 建置：`./gradlew test assembleRelease` 建置通過。
    - 實機驗證：安裝於 Xiaomi 11T (`ea6879f4`)，單月、多月、全院切換、放大細項下鑽均通過畫面與功能驗證。
