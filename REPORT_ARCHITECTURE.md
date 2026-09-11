# REPORT_ARCHITECTURE.md — 現行報表架構與盤點清單

> 依據 `AGENTS.md`（Rule 2, 30, 39）及 `SRS.md`（Section 27 TASK 7）要求產出之現況架構與報表盤點。
> 專案路徑：`/home/hpd/AndroidStudioProjects/hospital_dashboard`
> 基準版本：`HEAD eeec986` (versionName 0.4.1 / versionCode 4)
> 產出日期：2026-09-11

---

## 1. 專案整體分層現況

現行系統為 Android 離線 App（Kotlin + Jetpack Compose + SQLite），目前架構分層如下：

```text
[UI Layer]
  MainActivity.kt
    └─ DashboardScreen.kt (TopAppBar, FilterSheet, KPI 摘要卡片, 全螢幕放大 Dialog)
        ├─ DashboardTabs.kt (OpdTab, IpdTab, BedTab, OtherTab, BedDetailTab, PhysServiceTab, PhysIncomeTab)
        ├─ AnalysisTab.kt (AI 脫敏與串流分析)
        └─ charts/Charts.kt (Canvas 自繪圖表: LineChart, HBarChart, VBarChart, PieChart, DataTable, 明細卡片)
           ↑
[ViewModel / State Machine]
  DashboardViewModel.kt (UiState: Loading/NoData/Importing/Ready, Filters 響應流, tabIndex, zoomChart)
           ↑
[Data Query / Repository]
  DashboardRepo.kt (1,670 行：硬編碼之各報表 SQL 查詢、GROUP BY、YoY 計算、圖表物件組裝)
           ↑
[Database & Parser Layer]
  HospitalDb.kt (SQLite 封裝、DROP/CREATE/INSERT 交易批次匯入)
  XlsxReader.kt (ZipFile + XmlPullParser 輕量串流讀取)
  SheetConfig.kt (7 大 Excel 工作表與 SQLite 表結構映射)
```

---

## 2. 資料來源盤點（SQLite Tables）

7 大工作表皆由 `SheetConfig.kt` 定義，於匯入時全表覆蓋寫入 SQLite：

| 資料表名稱 | 來源工作表 | 主要維度欄位 | 主要數值指標欄位 | 備註 |
|---|---|---|---|---|
| `outpatient_service` | 門診業務資料 | `year`, `month`, `ym`, `branch_name`, `dept_div`, `dept`, `medicine_type` | `opd_visit_count`, `first_visit_count`, `return_visit_count`, `er_visit`, `total_clinic_sessions` | 門診核心表 |
| `inpatient_service` | 住院業務資料 | `year`, `month`, `ym`, `branch_name`, `dept_div`, `dept` | `admission_count`, `admission_days`, `discharge_count`, `discharge_days` | 住院核心表 |
| `bed_type_service` | 病床別業務資料 | `year`, `month`, `branch_name`, `nursing_station`, `major_category`, `category` | `registered_beds`, `actual_open_beds`, `registered_occupancy_rate`, `actual_occupancy_rate`, `actual_bed_days` | 1150826 起 24 欄（已移除樓層、科別） |
| `offsite_clinic_service` | 院外門診部服務量 | `year`, `month`, `branch_name`, `clinic_name` | `medical_visit`, `health_visit`, `checkup_count`, `total` | 院外門診 |
| `accounting_report` | 會計室報表資料 | `year`, `month`, `ym`, `branch_name` | `dialysis_count`, `opd_checkup_count`, `admission_checkup_count` | 洗腎與健檢 |
| `ops_management_indicators` | 其他營運管理指標資料 | `year`, `month`, `branch_name` | `surgery_opd_count`, `surgery_admission_count`, `delivery_count`, `total_income_opd`, `total_income_admission`, `self_pay_income_opd`, `self_pay_income_admission` | 手術、生產與收入 |
| `physician_service` | 醫師服務量 | `year`, `month`, `ym`, `branch_name`, `dept_div`, `dept`, `doctor_id`, `doctor_name` | `sessions`, `opd_visit_count`, `er_visit`, `admission_count`, `admission_days`, `opd_nhi_income`, `opd_selfpay_income`, `ipd_nhi_income`, `ipd_selfpay_income` | 年月由 ym 衍生 |

---

## 3. 現行報表盤點清單（Report Inventory）

| 編號 | 報表名稱 | 分頁位置 | 資料來源表 | 現行查詢方法 (`DashboardRepo`) | 維度 | 聚合 Grain | 現行呈現型態 |
|---|---|---|---|---|---|---|---|
| R001 | 門診人次月趨勢 | OpdTab | `outpatient_service` | `opdMonthly` | TIME, CAMPUS | `year, month, branch_name` | 折線圖 (YoY 虛線) |
| R002 | 急診人次月趨勢 | OpdTab | `outpatient_service` | `erMonthly` | TIME, CAMPUS | `year, month, branch_name` | 折線圖 |
| R003 | 科別門診人次 TOP20 | OpdTab | `outpatient_service` | `opdDeptTop` | DEPARTMENT | `dept` | 水平橫條 (千人+診次均值) |
| R004 | 初複診比例 | OpdTab | `outpatient_service` | `firstReturnPie` | TIME, CAMPUS | 全選區間總和 | 圓餅圖 |
| R005 | 各部別門診人次趨勢 | OpdTab | `outpatient_service` | `deptDivMonthly` | TIME, DEPT_DIV | `year, month, dept_div` | 折線圖 |
| R006 | 各院區門診人次總覽 | OpdTab | `outpatient_service` | `branchOpdBar` | CAMPUS | `branch_name` | 水平橫條 (降冪) |
| R007 | 住院人次月趨勢 | IpdTab | `inpatient_service` | `ipdMonthly` | TIME, CAMPUS | `year, month, branch_name` | 折線圖 (YoY 虛線) |
| R008 | 出院人次月趨勢 | IpdTab | `inpatient_service` | `dischargeMonthly` | TIME, CAMPUS | `year, month, branch_name` | 折線圖 |
| R009 | 住院 vs 出院人日趨勢 | IpdTab | `inpatient_service` | `ipdDaysMonthly` | TIME | `year, month` | 雙序列折線圖 |
| R010 | 各院區住院人次總覽 | IpdTab | `inpatient_service` | `branchIpdBar` | CAMPUS | `branch_name` | 水平橫條 |
| R011 | Top 15 科別住院人次與日數 | IpdTab | `inpatient_service` | `ipdDeptStats` | DEPARTMENT | `dept` | 垂直長條圖 |
| R012 | 實際佔床率月趨勢 | BedTab | `bed_type_service` | `bedOccMonthly` | TIME, MAJOR_CAT | `year, month, major_category` | 折線圖 (YoY 虛線) |
| R013 | 實際開床數月趨勢 | BedTab | `bed_type_service` | `bedOpenMonthly` | TIME, MAJOR_CAT | `year, month, major_category` | 折線圖 |
| R014 | 各院區實際佔床率 | BedTab | `bed_type_service` | `bedBranchOcc` | CAMPUS | `branch_name` | 水平橫條 (含85%線) |
| R015 | 各院區登記 vs 實開床數 | BedTab | `bed_type_service` | `bedBranchRegVsOpen` | CAMPUS | `branch_name` | 重疊水平橫條 |
| R016 | 各院區 × 病床類別佔床率 | BedTab | `bed_type_service` | `bedPivot` | CAMPUS, CATEGORY | `branch_name, category` | 熱力矩陣表格 |
| R017 | 去年同期佔床率比較 | BedTab | `bed_type_service` | `bedYoyCompare` | CATEGORY | `category` (本期 vs 去年) | 比較表格 |
| R018 | 各院區病床明細表 | BedDetailTab | `bed_type_service` | `bedDetail` | CAMPUS, MAJOR, WARD | 多層級明細 | 階層式展開表格 |
| R019 | 院外門診部服務量趨勢 | OtherTab | `offsite_clinic_service` | `offsiteMonthly` | TIME, CLINIC | `year, month, clinic_name` | 折線圖 |
| R020 | 各院外門診部累計服務量 | OtherTab | `offsite_clinic_service` | `offsiteClinicBar` | CLINIC | `clinic_name` (醫療+保健) | 水平累計橫條 |
| R021 | 洗腎／健檢人次月趨勢 | OtherTab | `accounting_report` | `accMonthly` | TIME | `year, month` | 折線圖 |
| R022 | 手術／生產人次月趨勢 | OtherTab | `ops_management_indicators` | `opsMonthly` | TIME | `year, month` | 堆疊長條圖 |
| R023 | 門診與住院收入趨勢 | OtherTab | `ops_management_indicators` | `incomeMonthly` | TIME | `year, month` | 雙折線圖 (總收入/自費) |
| R024 | 各院區總收入/自費累計 | OtherTab | `ops_management_indicators` | `branchIncome`, `branchSelfPay` | CAMPUS | `branch_name` | 水平橫條 (千元) |
| R025 | 科別醫師服務量 TOP20 | PhysServiceTab | `physician_service` | `physDeptVolumes` | DEPARTMENT | `dept` | 垂直長條圖 |
| R026 | 全院收入月趨勢 (醫師層級) | PhysIncomeTab | `physician_service` | `physIncomeMonthly` | TIME | `year, month` | 堆疊長條圖 |
| R027 | 當月收入結構圓餅 | PhysIncomeTab | `physician_service` | `physIncomePie` | TIME (錨點月) | 門診健保/自費/住院健保/自費 | 圓餅圖 (左明細右圓餅) |

---

## 4. 維度現況與異質性分析

| 維度類型 | 統一維度目標 | 現行資料表欄位 | 現行問題與異質性 |
|---|---|---|---|
| **TIME** | `TIME` (Year, Month, Range) | `year`, `month`, `ym` | `physician_service` 原始僅有 `ym`（11301），其他表有分開的 `year`/`month`；X 軸排版過去曾混雜民國與西元。 |
| **CAMPUS** | `CAMPUS` (Code, Name) | `branch`, `branch_name`, `merged_branch`, `merged_branch_name` | 多數表有 `branch_name` 與 `branch`，但 ViewModel 是以字串跨 6 表 UNION `branch_name` 來充當院區清單，缺乏實體定義與 Canonical Code。 |
| **DEPARTMENT** | `DEPARTMENT` (DeptDiv, Dept) | `dept_div`, `dept_div_name`, `dept` | 僅出現在門診、住院與醫師表；病床表 1150826 起已無科別欄位；部別與科別相依邏輯硬寫在 ViewModel 查詢。 |
| **DOCTOR** | `DOCTOR` | `doctor_id`, `doctor_name` | 僅存在於 `physician_service` 表。 |
| **WARD / BED** | `WARD`, `CATEGORY` | `nursing_station`, `major_category`, `category` | 僅存在於 `bed_type_service`，大類別與細項類別層級硬寫在 `bedPivot` 與 `bedDetail`。 |

---

## 5. 重複邏輯與結構性問題（SRS Problem Alignment）

1. **WHERE 條件生成重複但微異**：
   * `whereFor(f, withDept, yearOffset)` 雖然共用，但許多方法因特殊需求另起爐灶（如 `physWhere`、`bedPivot` 自行組裝 `ymCond`、`incomeMonthly` 自加過濾），造成規則分散。
2. **缺乏統一的 ReportContext**：
   * UI 層透過單一全域 `Filters` 物件傳遞，但每張圖表各自從 `Filters` 挑選欄位，UI 無法得知某報表支援「院區」還是「科別」，亦無法由設定檔宣告。
3. **無 ReportRegistry**：
   * 報表 ID、名稱、可用維度、指標定義皆隱含於程式碼中；新增報表需改動 3~4 個檔案。
4. **計算與呈現高度耦合**：
   * `DashboardRepo` 中各方法回傳特定 UI 圖表專用的資料結構（如 `LineChartData`、`HBarData`、`TableData`），而非純粹的標準報表結果（ReportResult / StandardDataset），導致同一統計邏輯無法直接轉為 CSV/Excel 或 API。
5. **YoY 計算無通用機制**：
   * 各方法自行執行一次主查詢、一次 `yearOffset = -1` 查詢，再於記憶體手動對齊並組裝虛線。
