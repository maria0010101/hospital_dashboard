# Hospital Report Unified Dimension & Configurable Reporting Platform
## Software Requirements Specification (SRS) / Agent AI Implementation Specification

- **Version**: 1.0
- **Date**: 2026-09-11
- **用途**: 提供 Claude Code / Cursor Agent / Coding Agent 進行程式碼盤點、架構重構與功能實作

---

## 1. 文件目的

本文件定義醫院報表平台的目標、功能需求、資料與維度模型、設定機制、報表註冊、聚合、呈現、測試、Migration Strategy 與 Agent AI 執行規則。Agent 必須先理解既有專案，再依本規格逐步實作，不得直接大規模改寫。

---

## 2. 核心願景

將目前散落於不同模組、各自定義統計維度與呈現方式的報表，重構為「**Metric（指標）＋ Dimension（維度）＋ Filter（篩選）＋ Aggregation（聚合）＋ Renderer（呈現）**」的設定驅動報表平台。

```
資料來源
  ↓
Data / Query Layer
  ↓
Standardized Dataset
  ↓
Dimension Engine
  ↓
Aggregation Engine
  ↓
Report Renderer
  ↓
Web / HTML / Excel / PDF / Dashboard
```

---

## 3. 問題定義

- 不同報表欄位名稱與資料維度不一致。
- 院區、科別、時間等判斷邏輯散落於各報表。
- 相同統計邏輯被複製到多份程式碼。
- 全院、單一院區、單一科別常被做成不同報表。
- 報表入口與設定分散，使用者難以從同一處管理。
- 新增報表容易產生複製貼上的技術債。

---

## 4. 非目標（Non-Goals）

- 本次不要求改變既有醫療統計口徑。
- 本次不要求更換既有資料庫，除非 Agent 分析後確認必要且提出方案。
- 不得因 UI 重構而修改原始資料。
- 不得為了統一而自行猜測未知商業規則。
- 不得一次刪除所有 Legacy 報表；必須採漸進式 Migration。

---

## 5. 使用者與主要情境

- **全院管理者**：查看全院營運指標。
- **院區管理者**：查看特定院區營運指標。
- **科部主管**：查看特定科別指標。
- **報表維護者**：建立／註冊報表、設定可用維度與預設值。
- **系統管理者**：管理共用維度、院區、科別與設定。

---

## 6. 核心功能需求

| ID | 功能 | 需求 |
| :--- | :--- | :--- |
| **FR-001** | 報表中心 | 建立統一報表入口，依類別／模組／關鍵字瀏覽報表。 |
| **FR-002** | 報表註冊 | 每份報表透過 ReportRegistry 註冊 metadata、可用維度與 renderer。 |
| **FR-003** | 統一時間維度 | 至少支援年、季、月、日期／期間；依既有專案資料來源決定實際欄位。 |
| **FR-004** | 組織維度 | 至少支援 ALL、CAMPUS、DEPARTMENT、CAMPUS_DEPARTMENT。 |
| **FR-005** | 院區切換 | 同一報表可依設定呈現全院或單一／多院區。 |
| **FR-006** | 科別切換 | 同一報表可依設定呈現全院或單一／多科別。 |
| **FR-007** | 複合維度 | 支援院區＋科別交叉分析。 |
| **FR-008** | 設定持久化 | 設定可儲存、載入、修改、重設；若專案適合，支援匯入／匯出。 |
| **FR-009** | 聚合引擎 | 依 Dimension 與 Metric 決定 GROUP BY、SUM、COUNT、AVG 等統計行為。 |
| **FR-010** | 資料與呈現分離 | Renderer 不得自行判斷院區／科別商業規則。 |
| **FR-011** | Legacy 相容 | Migration 期間保留既有報表，並可與新引擎結果比較。 |
| **FR-012** | 稽核可追溯 | 報表結果應可追溯至 report_id、設定、期間與資料來源。 |

---

## 7. 統一維度模型

### OrganizationLevel
- `ALL`
- `CAMPUS`
- `DEPARTMENT`
- `CAMPUS_DEPARTMENT`

### Standard dimensions
- `TIME`
- `CAMPUS`
- `DEPARTMENT`
- `ORGANIZATION`
- *(未來可擴充：`DOCTOR`、`WARD`、`TEAM`、`CASE_TYPE`...)*

> **規範**：維度定義應集中於共用 Dimension Registry，不得由單一報表自行硬編碼。

---

## 8. Standard Dataset

報表 Engine 應盡可能在聚合前使用一致的標準資料模型，例如：

```json
{
  "period": "...",
  "campus_code": "...",
  "campus_name": "...",
  "department_code": "...",
  "department_name": "...",
  "metric fields...": "..."
}
```

> **規範**：實際欄位名稱必須依既有專案資料模型調整；不得假設不存在的欄位。

---

## 9. ReportContext

```python
ReportContext(
    report_id,
    date_from,
    date_to,
    organization_level,
    campus_code=None,
    department_code=None,
    filters={},
    show_total=True
)
```

> **規範**：所有報表執行時應取得同一格式的 Context；報表不得自行解析 UI 控制項。

---

## 10. ReportConfiguration

```json
{
  "report_id": "OPD_001",
  "organization_level": "CAMPUS",
  "campus_code": null,
  "department_code": null,
  "date_from": "2026-01",
  "date_to": "2026-08",
  "show_total": true
}
```

> **規範**：設定儲存位置（JSON/YAML/DB）由 Agent 根據現有專案架構決定，但必須集中管理。

---

## 11. ReportRegistry

```python
ReportRegistry.register(
    report_id="OPD_001",
    name="門診營運分析",
    category="門診",
    dimensions=["TIME", "CAMPUS", "DEPARTMENT"],
    renderer="default"
)
```

> **規範**：Registry 應能提供報表清單、metadata、可用維度與預設設定。

---

## 12. Dimension Engine

Dimension Engine 負責把使用者設定轉成一致的資料維度。它必須集中處理院區／科別代碼、名稱、ALL、篩選與維度組合，不得讓各報表重複實作。

```
dimension_engine.apply(dataset, context)
  → normalized dataset

aggregation_engine.aggregate(dataset, context)
  → report dataset
```

---

## 13. Aggregation Engine

Aggregation Engine 應將統計行為與呈現方式分離。Metric 應能描述：

### Metric 定義規格
- `id`
- `name`
- `source field / expression`
- `aggregation`: `SUM` / `COUNT` / `AVG` / `DISTINCT_COUNT` / `CUSTOM`
- `format`
- `null handling`
- `validation rule`

> **規範**：如既有報表有特殊統計口徑，應建立明確的 Custom Metric，而非偷偷改變通用 SUM/COUNT。

---

## 14. 報表維度轉換範例

| 報表視角 | 呈現欄位結構 |
| :--- | :--- |
| **原始資料** | 月份 \| 院區 \| 科別 \| 人次 \| 金額 |
| **全院** | 月份 \| 人次 \| 金額 |
| **院區** | 月份 \| 院區 \| 人次 \| 金額 |
| **科別** | 月份 \| 科別 \| 人次 \| 金額 |
| **院區＋科別** | 月份 \| 院區 \| 科別 \| 人次 \| 金額 |

---

## 15. UI / UX 需求

- 報表名稱與統計期間。
- 分析維度：全院／院區／科別／院區＋科別。
- 院區下拉或多選。
- 科別下拉或多選。
- 是否顯示合計。
- 套用設定、儲存設定、重設。
- **限制**：UI 不應直接控制 SQL；UI 只修改 `ReportContext` / `Configuration`。

---

## 16. 架構分層要求

```
UI / Presentation
        ↓
Report Application Service
        ↓
ReportContext / Configuration
        ↓
ReportRegistry
        ↓
Dimension Engine
        ↓
Aggregation Engine
        ↓
Data / Query Layer
        ↓
Database / Files / APIs
```

> **規範**：Renderer 由 Application Layer 呼叫，不得反向耦合 Data Layer。

---

## 17. API / Service 介面（概念）

```http
GET    /reports
GET    /reports/{report_id}
GET    /reports/{report_id}/config
POST   /reports/{report_id}/config
POST   /reports/{report_id}/run
POST   /reports/{report_id}/compare-legacy
GET    /dimensions/campuses
GET    /dimensions/departments
```

> **規範**：若既有專案不是 HTTP API，請保留概念介面，以 Service/Class/Function 實作。不得為了套 API 而破壞既有架構。

---

## 18. 報表 Migration Strategy

- **Phase 1 — Codebase Discovery**：完整盤點專案、報表、資料來源、設定、SQL、重複邏輯。
- **Phase 2 — Architecture Design**：建立 `REPORT_ARCHITECTURE.md` 與 `REPORT_DESIGN.md`。
- **Phase 3 — Core Layer**：先建立 `ReportContext`、`ReportConfiguration`、`DimensionEngine`、`AggregationEngine`、`ReportRegistry`。
- **Phase 4 — PoC**：挑選一份同時具有時間＋院區＋科別＋Metric 的代表性報表。
- **Phase 5 — Regression**：Legacy vs New Engine 逐項比對。
- **Phase 6 — Migration**：逐份報表移轉，不一次重寫。
- **Phase 7 — Cleanup**：所有報表完成驗證後，才清理確定不再使用的重複程式碼。

---

## 19. Legacy vs New Result 比對

至少比較以下項目：
1. 總筆數
2. 各月份
3. 各院區
4. 各科別
5. 總人次／件數
6. 總金額／點數
7. NULL／0 處理
8. 特殊統計規則

> **規範**：任何差異不得直接視為新系統錯誤；Agent 必須判斷差異來自既有 Bug、統計口徑或重構錯誤，並標記 `NEED_CONFIRMATION`。

---

## 20. 資料正確性與醫療營運要求

- 不得修改原始資料。
- 不得擅自改變統計口徑。
- 不得將缺失值自動轉為 0，除非既有規則明確如此。
- 不得自行推測院區或科別 mapping。
- 所有 mapping 應集中管理並可追蹤。
- 重要統計應保留來源、期間、設定與 report_id。

---

## 21. Error Handling

- **無效 report_id**：回傳明確錯誤。
- **不存在的院區／科別**：不可靜默忽略，應提示。
- **維度不支援**：明確告知報表不支援該維度。
- **日期格式錯誤**：阻止執行並提示。
- **資料欄位缺失**：Fail Fast，指出缺失欄位。
- **Aggregation 發生例外**：保留可追蹤的 report_id/context。

---

## 22. Security / Privacy

- 不得在 log 寫入不必要的病人身分識別資訊。
- 不得把完整個資、病歷資料或敏感資料放入例外訊息。
- SQL 必須避免字串拼接造成 Injection。
- 權限控制應沿用既有專案機制。
- 設定檔若含敏感連線資訊，不得提交至 Git。

---

## 23. Performance Requirements

- 避免每個報表重複查詢同一份基礎資料。
- 能在資料層完成的聚合，優先於大量載入後再於 UI 計算。
- Dimension filter 應盡可能下推至 Query Layer。
- 避免 N+1 Query。
- 必要時建立 cache，但不得讓 cache 造成資料正確性問題。

---

## 24. Testing Requirements

- **Unit Test**：Dimension mapping。
- **Unit Test**：Aggregation。
- **Unit Test**：ReportContext validation。
- **Unit Test**：Configuration serialization。
- **Integration Test**：資料來源 → Engine → Renderer。
- **Regression Test**：Legacy vs New。
- **E2E Test**：UI 設定全院／院區／科別／複合維度。

---

## 25. Acceptance Criteria

- [ ] 同一份代表性報表可以在不修改程式碼的情況下切換全院／院區／科別／院區＋科別。
- [ ] 院區與科別判斷不再散落於各報表。
- [ ] 新增報表不需要重新實作維度切換。
- [ ] 報表設定可儲存並重新載入。
- [ ] Legacy 與 New Engine 的主要統計結果一致。
- [ ] 所有重要差異都有文件化原因。
- [ ] 至少一份完整 PoC 報表通過自動化測試。
- [ ] Agent 能提供完整變更清單與測試結果。

---

## 26. Agent AI 強制工作規則

1. 先讀 README、專案結構、package/dependency、entry points、測試與設定檔。
2. 先產出架構盤點，不得第一步就大量修改。
3. 先找出現有報表共通邏輯，再抽象。
4. 不要猜測商業規則；未知規則使用 `TODO` / `NEED_CONFIRMATION`。
5. 優先新增共用層，再逐份 migration。
6. 每次修改保持最小可驗證範圍。
7. 修改後必須執行現有測試與新增測試。
8. 不得刪除 Legacy 程式，除非已完成 Migration 且有驗證證據。
9. 所有新命名遵循既有專案 coding convention。
10. 不要為了符合本 SRS 而強行導入與既有技術棧不相容的框架。

---

## 27. Agent 首輪任務指令

將本 SRS 提供給 Agent 後，第一輪只要求完成以下工作，不立即進入全面實作：

- **TASK 1**. Inspect the entire repository.
- **TASK 2**. Identify all report-related modules and data sources.
- **TASK 3**. Build a report inventory.
- **TASK 4**. Identify existing dimensions: time, campus, department, etc.
- **TASK 5**. Identify duplicated aggregation/filter logic.
- **TASK 6**. Identify current configuration mechanisms.
- **TASK 7**. Produce `REPORT_ARCHITECTURE.md`.
- **TASK 8**. Produce `REPORT_DESIGN.md` with a proposed migration architecture.
- **TASK 9**. Select one representative PoC report.
- **TASK 10**. **STOP and report findings before modifying production code.**

> **注意**：Agent 必須在第一階段完成後等待下一步指示；除非使用者明確要求，禁止直接重構整個專案。

---

## 28. 建議產生的文件

- `REPORT_ARCHITECTURE.md`：現況盤點。
- `REPORT_DESIGN.md`：目標架構。
- `REPORT_DIMENSIONS.md`：維度與 mapping 定義。
- `REPORT_MIGRATION.md`：每份報表 migration 狀態。
- `REPORT_TEST_MATRIX.md`：Legacy / New 比對矩陣。
- `REPORT_CHANGELOG.md`：每次重構紀錄。

---

## 29. 未來擴充方向

本架構應預留未來增加更多分析維度的能力，例如醫師、病房、病區、醫療團隊、案件分類、支付別、健保申報分類等。新增維度應以 Registry / Metadata / Engine 方式擴充，而不是複製報表。

---

## 30. 最終設計原則

> **「報表不是資料本身，而是資料的管理視角。」**

```
Metric + Dimension + Filter + Aggregation + Renderer
                         ↓
                Configurable Report
                         ↓
       全院 / 院區 / 科別 / 院區＋科別
```

最終目標是讓醫院可以建立一套一致的報表基礎平台：同一份資料、同一套指標定義、同一套組織維度，透過設定切換不同管理視角，降低報表散落、統計口徑漂移與重複開發。
