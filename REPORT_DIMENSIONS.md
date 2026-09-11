# REPORT_DIMENSIONS.md — 醫院報表共用維度與 Mapping 規範

> 依據 `AGENTS.md`（Rule 5, 6, 40）及 `SRS.md`（Section 7, 28）產出之維度規範文件。
> 專案路徑：`/home/hpd/AndroidStudioProjects/hospital_dashboard`
> 產出日期：2026-09-11

---

## 1. 維度階層結構（Dimension Hierarchy）

```text
Organization (全院)
    ↓
Campus (院區：甲院區 / 乙院區 / 丙院區)
    ↓
DeptDiv (部別：部別01..14)
    ↓
Department (科別：科別01..43)
    ↓
Doctor (醫師：醫師001..1616，僅醫師服務量)
```

病床專屬維度階層：
```text
Campus (院區)
    ↓
MajorCategory (大類別01..06)
    ↓
Category (病床類別01..13)
    ↓
NursingStation (護理站01..49)
```

---

## 2. 維度與資料表欄位正規化對照表

| 標準維度標識 | 標準概念名稱 | 各資料表對應欄位 | 備註與規範 |
|---|---|---|---|
| `TIME_YEAR` | 統計年度 (民國年) | `year` (多數表), `ym / 100` (醫師表) | 統以民國年字串處理（如 `"113"`、`"114"`）。 |
| `TIME_MONTH` | 統計月份 | `month` (多數表), `ym % 100` (醫師表) | 統以雙位數字串處理（如 `"01"`..`"12"`）。 |
| `TIME_YM` | 統計年月 | `ym` (門診/住院/會計/醫師) 或 `year`+`month` 拼接 | 內部排序鍵：`year.toInt() * 100 + month.toInt()`。 |
| `CAMPUS` | 院區名稱 | `branch_name` | 統一以 `branch_name` 為準（混淆後為「甲院區」、「乙院區」、「丙院區」）。禁止混用 `branch`。 |
| `DEPT_DIV` | 部別名稱 | `dept_div` | 如「部別01」..「部別14」。排除 `'#N/A'` 與 `'NULL'`。 |
| `DEPARTMENT` | 科別名稱 | `dept` | 如「科別01」..「科別43」。 |
| `DOCTOR` | 醫師代號與姓名 | `doctor_id`, `doctor_name` | 僅存於 `physician_service`。 |
| `MAJOR_CAT` | 病床大類別 | `major_category` | 僅存於 `bed_type_service`（如「大類別01」..「大類別06」）。 |
| `BED_CAT` | 病床細項類別 | `category` | 僅存於 `bed_type_service`（如「病床類別01」..「病床類別13」）。 |
| `WARD` | 護理站 | `nursing_station` | 僅存於 `bed_type_service`（如「護理站01」..「護理站49」）。 |

---

## 3. 維度過濾與下推規則

1. **時間維度**：
   * 當 `years` 非空時，必定產生 `year IN (...)`。
   * 當 `months` 非空時，產生 `month IN (...)`。
   * YoY（去年同期）計算時，自動衍生 `years.map { it - 1 }`，且與主查詢使用相同之 month 與組織條件。
2. **組織層級維度下推**：
   * `OrganizationLevel.ALL`：不附加 `branch_name` 或 `dept` 條件，GROUP BY 僅依時間或總體指標。
   * `OrganizationLevel.CAMPUS`：依據 `campuses` 條件篩選；若 `campuses` 為空則代表「全院區各別 GROUP BY」。
   * `OrganizationLevel.DEPARTMENT`：附加 `dept_div` 與 `dept` 條件，GROUP BY `dept`。
   * `OrganizationLevel.CAMPUS_DEPARTMENT`：同時篩選/群組 `branch_name` 與 `dept`。
