# Android 平板版面配置開發指示文件

> **文件版本**：v1.0（對應 App `versionCode 9 / versionName 0.6.3`）
> **撰寫日期**：2026-09-24
> **專案路徑**：`/home/hpd/hospital_dashboard`
> **文件性質**：實作指示（Implementation Directive）。本文件由評估階段產出，**不對專案做任何變更**；所有程式碼片段均為「建議新增」內容，需由實作 Agent 在後續版本落地並回歸驗證。
> **治理依據**：根目錄 `AGENTS.md`。本文件所有施工步驟均須服從其 §23 Backward Compatibility、§28 Change Scope Control、§29 No Mass Deletion、§30 Documentation Is Part of the Implementation、§34 Git / Commit Discipline、§35 Stop Conditions、§36 Definition of Done。

---

## 1. 文件目的、適用對象與範圍

### 1.1 文件目的

本文件指示另一個 Agent AI 在 **不破壞既有手機（compact）版面與統計口徑** 的前提下，為 `hospital_dashboard` 導入平板（tablet / 大螢幕）自適應版面能力。內容包含：

1. 以實際原始碼逐項盤點「現行手機版面的假設」與「在平板上的具體問題」（附檔案路徑與行號）。
2. 定義目標平板版面設計（尺寸分級、導覽、KPI、圖表、面板、縮放、字型）。
3. 提供可驗收的分階段實作步驟（步驟 A～F），含關鍵 Kotlin 範例。
4. 定義回歸與驗收方式，確保手機不退化。
5. 列出風險、交付流程與附錄模板。

### 1.2 適用對象

- 負責實作平板版面的 Agent AI（主要讀者）。
- 專案維護者／Code Reviewer（用於驗收）。
- 需要理解為何必須保留既有統計口徑的審查者。

### 1.3 In Scope（本任務允許的變更）

| 類別 | 內容 |
|---|---|
| 新增排版能力 | 依視窗寬度分級切換「導覽、分頁呈現、圖表欄數、面板型態」的**新增**程式碼。 |
| 新增抽象 | 新增尺寸類別抽象層（`WindowSize` / `AdaptiveLayout`），集中管理 breakpoint，避免散落 `if (width > 600)`。 |
| 依賴新增 | `libs.versions.toml` 與 `app/build.gradle.kts` **新增**相依（見步驟 A）。 |
| 平板資源 | 視需要新增 `res/values-sw600dp`、`values-sw840dp` 等限定資源（目前不存在）。 |
| 測試 | 新增 UI／單元測試與多尺寸 `@Preview`（目前專案無任何 `@Preview`）。 |

### 1.4 Out of Scope（**明確禁止**）

- **不得變更既有手機（compact）版面的視覺與互動結果**。這是回歸底線。
- **不得變更任何統計口徑**：包含但不限於佔床率加權演算法、YoY 比較基準、零值排除原則、院區／部別／科別維度定義、下鑽層級（見 `README.md` v0.6.3 / v0.6.2 / v0.6 版本歷程，行 120、134、176 附近）。平板化僅為 **presentation 層** 重排。
- **不得刪除舊碼**（`AGENTS.md` §29）：含舊 Composable、舊 BottomSheet、舊高度函式。只能「新增分支」或「包一層」。
- **不得新增硬編碼的院區／報表分支**（`AGENTS.md` §12）。
- **不得破壞 drill-down 行為**：`UniversalDrillDownCard` 的 1～4 層返回、麵包屑、`BackHandler` 逐層返回邏輯（`Charts.kt:882`、`:897`、`:950` 區段）。
- **不得變更資料層**：`data/*`、`report/engine/*`、`report/reports/*`、SQL 一律唯讀。
- **不得更改機敏資料治理**：`.gitignore` 對 `*.xlsx/*.db` 之排除規則不得放寬（`AGENTS.md` §21）。

> ⚠️ **若施工中發現「需變更統計口徑才能實現某個平板效果」→ 立即停止並依 `AGENTS.md` §35 回報**，不得自行決定。

---

## 2. 現況盤點（逐項附檔案路徑與行號）

### 2.1 版本與建置現況

| 項目 | 實際值 | 來源（檔案:行號） |
|---|---|---|
| versionCode / versionName | `9` / `"0.6.3"` | `app/build.gradle.kts:16`、`:17` |
| AGP | `9.3.2` | `gradle/libs.versions.toml:2` |
| Kotlin | `2.2.10` | `gradle/libs.versions.toml:9` |
| Compose BOM | `2026.02.01` | `gradle/libs.versions.toml:10` |
| minSdk / targetSdk / compileSdk | `24` / `37` / `37`（`release(37)`） | `app/build.gradle.kts:14`、`:15`、`:9` |
| Compose 相依 | BOM + activity-compose + material3 + ui + tooling | `app/build.gradle.kts:41`–`:57`；`gradle/libs.versions.toml:20`–`:27` |
| 橫向／平板相依 | **無** `material3-window-size-class`、**無** `androidx.compose.material3.adaptive` | 全檔檢視 `gradle/libs.versions.toml`、`app/build.gradle.kts`（無此二項） |
| 平板限定資源 | **無** `res/values-sw600dp` 等 | `app/src/main/res/` 僅 `drawable/ mipmap-*/ values/ xml/` |

### 2.2 進入點與字型縮放（`MainActivity.kt`，共 60 行）

| 現行假設 | 說明 | 平板問題 | 路徑:行號 |
|---|---|---|---|
| `LocalDensity` 僅覆寫 `fontScale` | 以 `Density(density=base.density, fontScale=base.fontScale*multiplier)` 注入 5 級字型 | 只放大 `sp`，但**同時**會被業務畫面當成「間距」的來源之一（見 2.5）；且平板通常不需要放大字級，卻仍沿用同一滑桿 | `MainActivity.kt:33`–`:39`，`CompositionLocalProvider` 於 `:40` |
| 5 級字型倍率 | `FONT_SCALE_MULTIPLIERS = [1.00, 1.10, 1.20, 1.30, 1.40]` | 平板若沿用 1.4x，寬度已足夠，反而造成圖表空間浪費 | `DashboardViewModel.kt:71`–`:72`；UI 於 `MainActivity.kt:31`–`:32` |
| 依 `UiState` 單純分派畫面 | `Ready→DashboardScreen`、其餘→`FilePickScreen`、`Loading→CircularProgressIndicator` | 平板大螢幕仍顯示置中轉圈，無 skeleton；非阻斷性 | `MainActivity.kt:45`–`:54` |
| 全螢幕圖表 | `ZoomChartScreen` 取代整個畫面 | 詳 2.6 | `MainActivity.kt:41`–`:43` |

### 2.3 主畫面骨架（`ui/DashboardScreen.kt`，共 622 行）

| 現行假設 | 平板問題 | 路徑:行號 |
|---|---|---|
| `Scaffold` + `TopAppBar`（標題＋2 個 `TextButton`：篩選／更換） | 平板可用左側導覽承載「更換資料／篩選」，`TopAppBar` 動作區過於稀疏 | `DashboardScreen.kt:73`、`:75`–`:101` |
| 標題與副標題皆 `basicMarquee()` | 平板寬度大，跑馬燈無必要且持續重繪 | `DashboardScreen.kt:82`、`:89` |
| 整個內容為**單一垂直 `Column` + `verticalScroll`** | 大螢幕無法左右分割；所有卡片一律單欄全寬 | `DashboardScreen.kt:103` |
| `PrimaryScrollableTabRow` 5 分頁（門急診／住院／病床／其他／AI 分析） | 平板應改左側 `NavigationRail` 或 `PermanentNavigationDrawer`，釋放垂直空間 | `DashboardScreen.kt:106`–`:110`；分派於 `:111`–`:117` |
| `KpiRow`：`LazyRow` 水平捲動 9 張 KPI 卡 | 平板應改**多欄格線**（一次看完全部 KPI） | `DashboardScreen.kt:131`（`KpiRow`）、`:186`（`LazyRow`）、`:304`（`KpiCard`） |
| `KpiCard` 固定 `widthIn(min=116.dp, max=150.dp)` | 平板若維持 150dp 上限會出現大量留白 | `DashboardScreen.kt:306` |
| `FilterSheet` 為 `ModalBottomSheet` | 平板應改 `SideSheet`／`Dialog`，避免遮住全部內容 | `DashboardScreen.kt:345`、`:372` |
| `BranchSummarySheet` 為 `ModalBottomSheet`（`Column.verticalScroll`） | 同上；且內容為院區卡片清單，適合側欄 | `DashboardScreen.kt:209`、`:220`、`:222` |
| `ChipFlow` 使用 `FlowRow` | 已是自適應（良好）；平板僅需調整欄距 | `DashboardScreen.kt:605` |
| 底部固定 `Spacer(height=24.dp)` | 平板上多餘留白 | `DashboardScreen.kt` 內 `DashboardScreen` 尾端 `Spacer` |

### 2.4 分頁內容（`ui/DashboardTabs.kt`，共 1462 行）

| 現行假設 | 平板問題 | 路徑:行號 |
|---|---|---|
| 所有分頁內容統一包在 `TabColumn`：`Column(padding(12.dp), spacedBy(12.dp))` | 平板為**單欄全寬堆疊**，圖表被拉寬但高度固定，比例失真 | `DashboardTabs.kt:100`–`:106` |
| 卡片建構子 `LineCard/HBarCard/VBarCard/PieCard/TableCard` 接受固定 `height`（220/240/200dp 等） | 雙欄後每欄變窄，固定高度需重新審視 X 軸標籤密度 | `DashboardTabs.kt:109`、`:125`、`:151`、`:166`、`:173` |
| `OpdTab`（行 181）、`IpdTab`（行 311）、`BedTab`（行 427）、`OtherTab`（行 1076）各自呼叫 `TabColumn` 並以 `if (isSingleMonth) HBarCard else LineCard` 切換 | 平板應以**網格**並排多張圖，而非逐張往下 | `DashboardTabs.kt:181`、`:311`、`:427`、`:1076` |
| 熱力圖卡 `BedCategoryHeatCard`（行 655）、`DiffBedCategoryHeatCard`（行 862） | 卡片內為橫向格線，平板可並排 | `DashboardTabs.kt:655`、`:862` |
| `BedStationOccSheet`（行 753）、`DiffBedStationOccSheet`（行 958）為 `ModalBottomSheet`（`skipPartiallyExpanded=true`） | 平板應改側欄／對話框 | `DashboardTabs.kt:765`、`:768`、`:972`、`:975` |
| `WideTable`：`Row(horizontalScroll)` 包 `DataTable(width=720.dp)` | 平板寬度足夠，不需水平捲動 | `DashboardTabs.kt:1068`–`:1070` |
| `BedDetailTab`（行 1301）以 `branches.forEach` 逐院區卡片，內部展開 `WideTable` | 平板可多欄並排院區卡片 | `DashboardTabs.kt:1301`、`:1423`（`RangeToggle`） |
| `PhysServiceTab`（行 1234）、`PhysIncomeTab`（行 1260） | 同上 | `DashboardTabs.kt:1234`、`:1260` |
| AI 分析分頁 | 於獨立檔案 `ui/AnalysisTab.kt`（共 503 行） | `MainActivity.kt:111` 分派 index 4 |

### 2.5 圖表層（`ui/charts/Charts.kt`，共 3507 行）

| 現行假設 | 平板問題 | 路徑:行號 |
|---|---|---|
| `ChartCard`：`Card().fillMaxWidth()` + `Column(padding(12.dp))` + 標題 `basicMarquee()` | 圖卡永遠全寬、標題跑馬燈在大螢幕無必要 | `Charts.kt:183`、`:195`、`:201` |
| **已存在**寬螢幕判斷：`LocalConfiguration.current.screenWidthDp >= 600` | 這是目前**唯一**的 breakpoint，且只用在 drill-down 卡片內 | `Charts.kt:897` |
| `UniversalDrillDownCard`：`fillMaxWidth(0.96f)`、`heightIn(max=260.dp)`、`isWide` 分支 | 平板下 0.96f 幾乎全寬，但高度上限 260dp 偏低；drill-down 覆蓋層可改並排 | `Charts.kt:882`、`:944`、`:950`、`:1159`、`:1365`、`:1417`、`:1474` |
| `DrillStatCard(isWide: Boolean, …)` 依 `isWide` 切換 Row/Column 排版 | 此為既有良好模式，可**沿用**並擴充為三級 | `Charts.kt:1489`、`:1505` |
| 各明細卡固定 `heightIn(max=320/340/300dp)` 多處硬編碼 | 平板可放大，但需避免與雙欄衝突 | `Charts.kt:1761`、`:1819`、`:1895`、`:1969`、`:2043`、`:2203`、`:2286`、`:2407` |
| `dynamicLineHeight(isSingleBranch, normal=220dp)`：單院區縮為 140dp | 雙欄後「單院區縮高」語意改變（欄變窄，縮高會更扁） | `Charts.kt:167` |
| `dynamicHBarHeight(rowCount, default=240dp)`：依列數 80/115/150/185/240dp | 同上，雙欄下每列可視寬度減半，需重算 | `Charts.kt:171` |
| `LineChart` X 標籤依實測寬度自適應間距（`step = ceil((maxW+pad)/xSpacing)`） | 已是自適應；平板欄寬大→自動顯示更多標籤（良好） | `Charts.kt:2840`、`:2901`–`:2925` |
| `VBarChart` X 標籤同以 `groupW` 自適應 | 同上（良好） | `Charts.kt:3200`、`:3263`–`:3273` |
| `HBarChart`：院區名稱固定寬 + 目標線 | 需注意長院區名在窄欄的截斷 | `Charts.kt:2977`、`:3037`–`:3043` |
| `PieChart(height=160.dp)`、`DataTable(modifier, onRowClick)` | 平板可並排「圓餅＋圖例清單」 | `Charts.kt:3311`、`:3463` |
| `ZoomChartScreen`：`requestedOrientation = SCREEN_ORIENTATION_SENSOR_LANDSCAPE`（開啟）／`PORTRAIT`（關閉） | 平板使用者常持橫向；強制轉向會與平板自然姿勢衝突 | `Charts.kt:230`、`:236`、`:240` |
| `XWindowZoom`、`ZoomableBox`、`TableZoom(width=960.dp)` | 縮放互動可沿用；平板應改雙欄（圖＋明細） | `Charts.kt:615`、`:660`、`:698`、`:706` |

### 2.6 系統與資源層

| 現行假設 | 平板問題 | 路徑:行號 |
|---|---|---|
| `AndroidManifest.xml` **無** `android:screenOrientation` | App 可自由旋轉（良好）；但平板旋轉後版面未做區分 | `app/src/main/AndroidManifest.xml:17`–`:22` |
| `windowSoftInputMode="adjustResize"` | 平板輸入法佔比較小，無問題 | `app/src/main/AndroidManifest.xml:22` |
| 無 `sw600dp` / `land` 限定資源 | 平板上無法以資源切換 `dimen`/`style` | `app/src/main/res/`（僅 `values/`） |
| 無任何 `@Preview` | 無法在 IDE 快速驗證多尺寸 | 全 `app/src/main/java` 檢視無 `@Preview` |
| 現有測試 | `app/src/test/.../report/` 6 個單元測試＋`app/src/androidTest/.../ExampleInstrumentedTest.kt` | `app/src/test/java/com/example/hospital_dashboard/report/`、`app/src/androidTest/...` |

### 2.7 關鍵結論（盤點摘要）

1. **已有單一 breakpoint 雛型**（`Charts.kt:897` 的 `>= 600`），但**未抽象**、且只用於 drill-down；主畫面、分頁、圖表網格完全沒有寬螢幕分支。
2. **沒有平板相依、沒有平板資源、沒有 Preview**，因此可在「零干擾」狀態下新增自適應層。
3. **最大風險來源是 `LocalDensity` 的 `fontScale` 放大**（`MainActivity.kt:33`–`:39`）：它同時影響 `sp` 文字與既有多處以字級推導的視覺密度，平板需與 breakpoint 一併設計（見 §3.7、§6.1）。

---

## 3. 目標平板版面設計

### 3.1 尺寸分級（Window Size Class）

建議採用 **三段式**，並與現有 `>= 600` 判斷**相容**（600 仍為 medium 的起點）：

| 級別 | 寬度（dp） | 對應裝置 | 版面策略 |
|---|---|---|---|
| `Compact` | `< 600` | 手機直向 | **維持現況**（逐項不得變更） |
| `Medium` | `600–839` | 手機橫向、小平板、折疊展開 | 分頁保留 `TabRow`；KPI 多欄；圖表雙欄；面板可維持 BottomSheet 或改 Dialog |
| `Expanded` | `>= 840` | 平板橫向、大平板 | 左側 `NavigationRail`／`PermanentNavigationDrawer`；分頁改導覽項；KPI 4～6 欄；圖表 2～3 欄；面板改 `SideSheet` |

**相容性規則**：`screenWidthDp >= 600`（既有 `Charts.kt:897`）等同 `Medium` 或以上。實作時把既有硬編碼 `>= 600` 改為呼叫新抽象（如 `isAtLeastMedium()`），**語意不變**。

> 建議優先用 `material3-window-size-class` 的 `WindowWidthSizeClass`（`COMPACT/MEDIUM/EXPANDED`）與 `calculateWindowSizeClass()`，因它由 BOM 管理、與現有 material3 同版本線，風險最低。`androidx.compose.material3.adaptive`（`ListDetailPaneScaffold` 等）為獨立版本線，可作為選配（步驟 A 提供兩案）。

### 3.2 導覽（Navigation）

| 級別 | 設計 |
|---|---|
| Compact | 保留 `TopAppBar` + `PrimaryScrollableTabRow`（**現況不動**），`DashboardScreen.kt:75`、`:106` |
| Medium | `TopAppBar` 保留；分頁可保留 `TabRow`，或改 `NavigationRail`（建議以 `NavigationRail`，讓分頁與動作垂直並列） |
| Expanded | `PermanentNavigationDrawer`（左側固定抽屜，含 5 分頁 +「🤖 AI 分析」+ 底部動作「🔄 更換資料」），主內容區為 `Scaffold` body |

**要點**：分頁索引來源為 `vm.tabIndex`（`DashboardViewModel.kt:55`），故導覽切換**不需新增狀態**，只要把「設定 `vm.tabIndex.value`」的 UI 由 `Tab` 換成 `NavigationRailItem`／`NavigationDrawerItem` 即可。`tabIndex` 放 VM 的理由（`DashboardViewModel.kt:52`–`:54` 註解）在平板同樣成立。

**篩選／更換動作**：Compact 維持 `TopAppBar.actions`（`DashboardScreen.kt:85`–`:94`）；Medium/Expanded 移入導覽列底部，避免占用頂部主內容高度。

### 3.3 KPI 列（LazyRow → 多欄格線 / FlowRow）

- **Compact**：維持 `LazyRow`（`DashboardScreen.kt:186`）。
- **Medium**：`KpiRow` 改用 `FlowRow`（既已 import `FlowRow`，`DashboardScreen.kt:16`）或 `LazyVerticalGrid(GridCells.Fixed(3))`。
- **Expanded**：`LazyVerticalGrid(GridCells.Fixed(5))`（9 項 KPI 於 840dp 約 2 排看完）。
- **KpiCard 寬度**：現為 `widthIn(min=116.dp, max=150.dp)`（`DashboardScreen.kt:306`）；建議以 `Modifier.weight(1f)` 配 Grid 儲存格寬，移除 150dp 上限（僅在 Compact 保留，以維持手機外觀）。

> ⚠️ **語意凍結**：KPI **固定為「最新月份 + 去年同期」**，與圖表篩選分離（`DashboardScreen.kt:129` 註解）。平板化**不得**改變這個口徑或改成與 filter 連動。

### 3.4 圖表（單欄 → LazyVerticalGrid 雙欄／三欄）

- **Compact**：維持 `TabColumn` 單欄（`DashboardTabs.kt:100`）。
- **Medium**：`TabColumn` 改 `LazyVerticalGrid(GridCells.Fixed(2))`，圖卡各佔一欄、`spacedBy(12.dp)`。
- **Expanded**：`GridCells.Fixed(3)` 或 `Adaptive(minSize = 360.dp)`（依可用寬度自動）。
- **實作方式**：**不要**改寫每個 `XxxTab` 內部結構；建議在 `TabColumn` 這一層新增 `gridMode` 參數，或在 `TabColumn` 內依 window size 決定 Column/Grid。這樣 `OpdTab`（行 181）、`IpdTab`（行 311）、`BedTab`（行 427）、`OtherTab`（行 1076）等呼叫端**幾乎不用改**。

### 3.5 篩選與明細（BottomSheet → SideSheet / Dialog）

| 面板 | Compact | Medium | Expanded |
|---|---|---|---|
| `FilterSheet`（`DashboardScreen.kt:345`） | `ModalBottomSheet`（現況） | `ModalBottomSheet` 或 `Dialog`（`usePlatformDefaultWidth=false`） | 右側 `SideSheet`／`PermanentDrawerSheet`（常駐篩選） |
| `BranchSummarySheet`（`DashboardScreen.kt:209`） | `ModalBottomSheet`（現況） | `Dialog` | `SideSheet` |
| `BedStationOccSheet`（`DashboardTabs.kt:753`） | `ModalBottomSheet`（現況） | `Dialog` | `SideSheet` |
| `DiffBedStationOccSheet`（`DashboardTabs.kt:958`） | 同上 | `Dialog` | `SideSheet` |
| `UniversalDrillDownCard`（`Charts.kt:882`） | 覆蓋層卡片（現況） | 覆蓋層＋更寬（拉高 `heightIn`） | 可與圖表**並排**（圖左／明細右），見 3.6 |

**實作方式**：抽出 `AdaptiveSheet`（新檔），內部依 window size 選擇 `ModalBottomSheet` / `Dialog` / `SideSheet`，對外維持 `(onDismiss, content)` 簽章，**避免逐一改寫 5 個 Sheet 的使用處**。

### 3.6 ZoomChartScreen 平板雙欄（圖＋明細）

現況：`ZoomChartScreen` 強制橫向（`Charts.kt:236`），整屏僅圖表；`TableZoom` 為 `width=960.dp`（`Charts.kt:706`）。

| 級別 | 設計 |
|---|---|
| Compact | 維持現況：`SENSOR_LANDSCAPE` + 單欄圖表（**不得動**） |
| Medium | 不強制轉向；圖上／明細下，或圖左 65%／明細右 35% |
| Expanded | **雙欄**：左欄圖表（`LineChart`/`HBarChart`/`VBarChart`/`PieChart`），右欄明細（`TableZoom`/`UniversalDrillDownCard`）。`TableZoom` 固定 960dp 應改 `fillMaxWidth()` |

**重要**：`requestedOrientation` 的設定／還原（`Charts.kt:236`、`:240`）在平板應「不強制」，且 `DisposableEffect` 還原為 `PORTRAIT` 的行為不得套用到平板（否則平板橫向使用後被強制轉直向）。

### 3.7 圖表高度與 X 軸標籤密度

- `dynamicLineHeight`（`Charts.kt:167`）與 `dynamicHBarHeight`（`Charts.kt:171`）為**手機情境**設計：
  - 雙欄後每欄寬度約減半 → **不建議**再套用「單院區縮為 140dp」（會更扁）。
  - 建議：在 Medium/Expanded 時，`dynamicHeight` 傳入 `false`，或另建 `adaptiveChartHeight(...)` 依 window size 回傳較高預設（如雙欄 260dp、三欄 240dp）。
- X 軸標籤自適應（`Charts.kt:2901`、`:3263`）**已足夠**，雙欄變窄會自動減少標籤數；平板變寬會自動增加 → 無需改動邏輯，但需在 `@Preview` 驗證不重疊。
- `HBarChart` 院區名稱寬度（`Charts.kt:2977` 起）在窄欄需確認不截斷；必要時縮小 `nameStyle`。

### 3.8 marquee 在寬螢幕的處理

`basicMarquee()` 出現於：`DashboardScreen.kt:82`、`:89`、`:178`、`:315`、`:322`、`:332`；`Charts.kt:201`；`DashboardTabs.kt:75`、`:291`、`:675`、`:725`、`:882`、`:932`。

**建議**：新增 `Modifier.adaptiveMarquee(enabled: Boolean)` 擴充函式；`enabled = windowSize == Compact`。平板（Medium/Expanded）停用 marquee，改 `maxLines=1 + TextOverflow.Ellipsis`（或允許換行）。理由：marquee 為持續動畫，大螢幕上無必要且增加重繪成本（`AGENTS.md` §22 Performance）。

### 3.9 字型縮放與平板間距互動

- 現行 5 級字型（`DashboardViewModel.kt:71`–`:72`）經 `LocalDensity.fontScale` 注入（`MainActivity.kt:37`）。
- **已知副作用**：`fontScale` 只影響 `sp`；但 UI 中大量以 `padding(12.dp)` / `height(240.dp)` 等 **dp** 固定間距，故放大字級會讓文字相對間距變擠（即：字變大、dp 不變）。反過來，若為了平板放大 dp，會與 fontScale 疊加。
- **建議**：
  1. 平板（Medium/Expanded）**不改變** fontScale 邏輯，但 UI 應以 `weight`/`Grid` 讓容器隨寬度伸展，避免「字大 + 固定窄盒」。
  2. 新增依 window size 的 **dimen 建議值**（如卡片內距 Compact 12dp / Expanded 16dp），但此類調整**僅限平板分支**，不得改動 Compact 既有值。
  3. 保留使用者字型滑桿（`DashboardScreen.kt:522`）功能，不因平板而移除。

### 3.10 橫向／直向切換

- Manifest 目前無鎖定（`AndroidManifest.xml:17`–`:22`）→ 允許旋轉。
- 平板直向通常即 `Medium`（600–839），橫向即 `Expanded`（>=840）。因此 **window size class 已隱含方向處理**，不需另以 `LocalConfiguration.orientation` 判斷。
- 旋轉時應保持：目前分頁（`vm.tabIndex`，`DashboardViewModel.kt:55`）、篩選條件（`vm.filters`）、字型級別（`vm.fontScaleLevel`）。**這些都已放 VM/SharedPreferences，天然存活**；新增的 window size 狀態應使用 `rememberSaveable` 或由 `calculateWindowSizeClass` 重新計算（無狀態）。
- `showFilters` 目前為 `rememberSaveable`（`DashboardScreen.kt:71`）→ 旋轉後仍會彈出，行為合理。

---

## 4. 實作步驟（分階段、可驗收）

> 每個步驟結尾都有 **驗收條件（Acceptance）**。務必**逐步 commit**（`AGENTS.md` §34），每步都要「手機不退化」。

### 步驟 A：新增相依與尺寸類別（不改任何 UI 行為）

**A-1. `gradle/libs.versions.toml` 新增（建議方案，BOM 管理）**

```toml
# [libraries] 區段新增
androidx-material3-window-size-class = { group = "androidx.compose.material3", name = "material3-window-size-class" }
```

> `material3-window-size-class` 由 Compose BOM（`2026.02.01`，`libs.versions.toml:10`）管理版本，**不需**另寫 version。

**A-2.（選配）若需 `ListDetailPaneScaffold` 等 adaptive API**

```toml
# [versions] 新增（adaptive 為獨立版本線，需明確指定版本；請以官方最新穩定版為準）
material3Adaptive = "<latest-stable>"

# [libraries] 新增
androidx-material3-adaptive = { group = "androidx.compose.material3.adaptive", name = "adaptive", version.ref = "material3Adaptive" }
androidx-material3-adaptive-layout = { group = "androidx.compose.material3.adaptive", name = "adaptive-layout", version.ref = "material3Adaptive" }
```

> ⚠️ adaptive 與 BOM 非同版本線；**若無明確需求，僅採 A-1**，以降低相依風險（`AGENTS.md` §27 Avoid Premature Abstraction）。

**A-3. `app/build.gradle.kts` 新增相依（`dependencies` 區段，行 40 之後）**

```kotlin
implementation(libs.androidx.material3.window.size.class)
// 若採 A-2：
// implementation(libs.androidx.material3.adaptive)
// implementation(libs.androidx.material3.adaptive.layout)
```

**驗收 A**：`./gradlew :app:assembleDebug` 成功；App 行為與 v0.6.3 完全相同（尚無任何 UI 改動）。

---

### 步驟 B：建立 AdaptiveLayout / WindowSize 抽象

**目的**：把「散落的 `if (width >= 600)`」集中，避免重複（`Charts.kt:897` 為首個要收斂的點）。

**B-1. 新增檔案 `app/src/main/java/com/example/hospital_dashboard/ui/adaptive/WindowSize.kt`**

```kotlin
package com.example.hospital_dashboard.ui.adaptive

import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

/** 平板自適應尺寸級別（與既有 screenWidthDp >= 600 相容）。 */
enum class AdaptiveSize { Compact, Medium, Expanded }

/** 由 WindowSizeClass 推導專案用級別。 */
fun WindowSizeClass.toAdaptiveSize(): AdaptiveSize = when (widthSizeClass) {
    WindowWidthSizeClass.Compact -> AdaptiveSize.Compact
    WindowWidthSizeClass.Medium -> AdaptiveSize.Medium
    else -> AdaptiveSize.Expanded
}

/** 無 WindowSizeClass 時的降級判斷（保留既有 600dp 語意）。 */
@Composable
fun currentAdaptiveSize(): AdaptiveSize {
    val w = LocalConfiguration.current.screenWidthDp
    return when {
        w >= 840 -> AdaptiveSize.Expanded
        w >= 600 -> AdaptiveSize.Medium
        else -> AdaptiveSize.Compact
    }
}

val AdaptiveSize.isCompact: Boolean get() = this == AdaptiveSize.Compact
val AdaptiveSize.isAtLeastMedium: Boolean get() = this != AdaptiveSize.Compact
val AdaptiveSize.isExpanded: Boolean get() = this == AdaptiveSize.Expanded

/** 依尺寸回傳 A/B 值；Compact 一律回傳 compactValue（保證手機不變）。 */
fun <T> AdaptiveSize.pick(compactValue: T, mediumValue: T, expandedValue: T = mediumValue): T = when (this) {
    AdaptiveSize.Compact -> compactValue
    AdaptiveSize.Medium -> mediumValue
    AdaptiveSize.Expanded -> expandedValue
}
```

**B-2. （建議）在 `MainActivity.kt` 提供 window size**

`MainActivity.kt` 目前結構（`:28`–`:57`）為 `setContent { Hospital_dashboardTheme { … } }`。建議在 `setContent` 內以 `calculateWindowSizeClass(this)` 取得並以 `CompositionLocal` 提供（**新增**，不動既有 `LocalDensity` 注入）：

```kotlin
val windowSizeClass = calculateWindowSizeClass(this)
CompositionLocalProvider(
    LocalDensity provides customDensity,
    LocalWindowSize provides windowSizeClass   // 新增
) { /* 既有分派邏輯不變 */ }
```

> 若導入 `calculateWindowSizeClass`，需 `@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)`。**替代方案**：完全不引入 `WindowSizeClass`，只用 `currentAdaptiveSize()`（純 `LocalConfiguration`），可做到「零新相依」——建議作為 fallback。

**B-3. 收斂既有硬編碼**

把 `Charts.kt:897` 的 `val isWide = LocalConfiguration.current.screenWidthDp >= 600` 改為：

```kotlin
// 語意等價，僅抽換來源
val isWide = currentAdaptiveSize().isAtLeastMedium
```

**驗收 B**：`Charts.kt:897` 改動後，drill-down 在手機（<600dp）與 >=600dp 的行為**逐像素一致**；`assembleDebug` 通過。

---

### 步驟 C：導覽列改造（僅 Medium/Expanded 生效）

**C-1. 抽出導覽元件（新檔 `ui/adaptive/AdaptiveNavigation.kt`）**

對外維持與現有 `PrimaryScrollableTabRow` 相同的「切換 `vm.tabIndex`」語意：

```kotlin
private val TABS = listOf("🚪 門急診", "🛏️ 住院", "🏥 病床", "📋 其他", "🤖 AI 分析")

@Composable
fun AdaptiveTabs(
    size: AdaptiveSize,
    selected: Int,
    onSelect: (Int) -> Unit,
    onShowFilters: () -> Unit,
    onBackToFilePick: () -> Unit,
) {
    when (size) {
        AdaptiveSize.Compact -> {
            // 完全沿用現況：PrimaryScrollableTabRow(edgePadding = 8.dp)
            PrimaryScrollableTabRow(selectedTabIndex = selected, edgePadding = 8.dp) {
                TABS.forEachIndexed { i, t ->
                    Tab(selected = selected == i, onClick = { onSelect(i) }, text = { Text(t) })
                }
            }
        }
        else -> {
            NavigationRail {
                TABS.forEachIndexed { i, t ->
                    NavigationRailItem(
                        selected = selected == i,
                        onClick = { onSelect(i) },
                        icon = { /* 對應 emoji 或 icon */ },
                        label = { Text(t) }
                    )
                }
            }
        }
    }
}
```

**C-2. 修改 `DashboardScreen.kt`（行 68–117）**

- 以 `Row` 包住「導覽 + 內容」：
  - Compact：`Column { KpiRow(...); AdaptiveTabs(...) /* 即原 TabRow */; when(tabIndex){...} }`（**外觀不變**）。
  - Medium/Expanded：`Row { NavigationRail(...); Column(weight(1f)) { KpiRow(...); when(tabIndex){...} } }`；`TopAppBar.actions` 的篩選／更換移入導覽列。
- **注意**：目前 `Scaffold` body 為 `Column(...).verticalScroll(...)`（`DashboardScreen.kt:103`）。Expanded 若改 Grid，垂直捲動應由 Grid 自身處理（見步驟 D），避免 `LazyVerticalGrid` 嵌套於 `verticalScroll`（會拋 `IllegalStateException`：無限高度約束）。

**驗收 C**：Compact（手機）畫面與 v0.6.3 **完全一致**；Medium/Expanded 出現左側導覽且可切換 5 分頁，切換後 `vm.tabIndex` 正確。

---

### 步驟 D：圖表網格化

**D-1. 改造 `TabColumn`（`DashboardTabs.kt:100`）**

不要逐一改 `OpdTab`/`IpdTab`/`BedTab`/`OtherTab`，改在 `TabColumn` 內分流：

```kotlin
@Composable
private fun TabColumn(
    content: @Composable () -> Unit
) {
    val size = currentAdaptiveSize()
    if (size.isCompact) {
        // 完全保留現況
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
    } else {
        // 平板：改以網格排版（欄數依尺寸）
        // 由於 content() 為單一 lambda，建議改為接受「子項清單」版本：
        //   見 D-2 的 TabGrid 建議
    }
}
```

> ⚠️ 問題：現有 `TabColumn { …多個卡片… }` 傳入的是**單一 lambda**，`LazyVerticalGrid` 需要「逐項」。這是本步驟最需要謹慎之處。

**D-2.（建議做法）新增 `TabGrid`，並「部分」改寫分頁內容**

新增：

```kotlin
@Composable
fun TabGrid(
    columns: Int,
    modifier: Modifier = Modifier,
    content: LazyGridScope.() -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content
    )
}
```

並在每個分頁把 `TabColumn { card1(); card2(); … }` 改為：

```kotlin
if (size.isCompact) {
    TabColumn { card1(); card2(); … }   // 原樣保留
} else {
    TabGrid(columns = size.pick(1, 2, 3)) {
        item { card1() }
        item { card2() }
        …
    }
}
```

> 這是**新增分支**，既有 `TabColumn` 呼叫與內容**一字不動**，符合 `AGENTS.md` §29。
> ⚠️ 分頁高度：`LazyVerticalGrid` 於 `Scaffold` body 內需有「有限高度」。若外層仍為 `Column(...).verticalScroll(...)`（`DashboardScreen.kt:103`），需改為 `Modifier.weight(1f)` 的固定高度容器（步驟 C 已述）。

**D-3. 圖表高度調整**

平板分支呼叫 `LineCard/HBarCard`（`DashboardTabs.kt:109`、`:125`）時，將 `dynamicHeight` 傳 `false`，並以 window size 決定 `height`（雙欄 ≈ 260dp、三欄 ≈ 240dp）。**Compact 一律維持原 `height` 與 `dynamicHeight=true`**。

**驗收 D**：手機各分頁外觀不變；平板雙欄／三欄並排、無巢狀捲動崩潰、圖表不變形。

---

### 步驟 E：面板 Sheet → Side / Dialog 自適應

**E-1. 新增 `ui/adaptive/AdaptiveSheet.kt`**

對外維持 `(onDismissRequest, content)` 語意，內部三選一：

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdaptiveSheet(
    size: AdaptiveSize,
    onDismissRequest: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(),
    content: @Composable ColumnScope.() -> Unit
) {
    when (size) {
        AdaptiveSize.Compact -> ModalBottomSheet(onDismissRequest = onDismissRequest, sheetState = sheetState) { content() }
        AdaptiveSize.Medium -> Dialog(onDismissRequest = onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Card(Modifier.fillMaxWidth(0.8f).fillMaxHeight(0.85f)) { Column(Modifier.verticalScroll(rememberScrollState())) { content() } }
        }
        AdaptiveSize.Expanded -> ModalBottomSheet(onDismissRequest = onDismissRequest) { /* 或改用 SideSheet/常駐抽屜 */ content() }
    }
}
```

**E-2. 逐處抽換（**僅新增 `when`/參數**，不改內容）**

| 檔案:行號 | 現況 | 動作 |
|---|---|---|
| `DashboardScreen.kt:372` `FilterSheet` | `ModalBottomSheet` | 換為 `AdaptiveSheet`；Expanded 可改常駐右欄 |
| `DashboardScreen.kt:220` `BranchSummarySheet` | `ModalBottomSheet` | 換為 `AdaptiveSheet` |
| `DashboardTabs.kt:274` `FirstVisitSheet` | `ModalBottomSheet` | 換為 `AdaptiveSheet` |
| `DashboardTabs.kt:768` `BedStationOccSheet` | `ModalBottomSheet`（`skipPartiallyExpanded=true`，`:765`） | 換為 `AdaptiveSheet`，保留 sheetState 參數 |
| `DashboardTabs.kt:975` `DiffBedStationOccSheet` | `ModalBottomSheet`（`:972`） | 同上 |
| `Charts.kt:220` 等 drill-down 覆蓋層 | `Card` 覆蓋 | 平板改「圖左／明細右」並排（見 E-3） |

> **風險**：`rememberModalBottomSheetState(skipPartiallyExpanded = true)`（`DashboardTabs.kt:765`、`:972`）在 Dialog 分支無意義；`AdaptiveSheet` 需允許傳入並在非 BottomSheet 分支忽略之。

**E-3. drill-down 平板並排（`Charts.kt:882`）**

`UniversalDrillDownCard` 已有 `isWide`（`:897`）與 `heightIn(max=260.dp)`（`:950`）。平板分支可：
- 提高高度上限（例如 Expanded 用 420dp），
- 或將卡片改為「左圖表 / 右明細」的 `Row`（圖表由呼叫端提供）。

⚠️ **不得改動** 1～4 層返回、麵包屑、`BackHandler`（`Charts.kt` 於 `:921` 起）邏輯。

**驗收 E**：手機各面板仍為 BottomSheet；平板為 Dialog／側欄；所有明細內容與數值與手機完全相同。

---

### 步驟 F：字型／高度／標籤微調

- **F-1** 依 §3.8 導入 `adaptiveMarquee(enabled)`，並把 `DashboardScreen.kt:82/:89/:178/:315/:322/:332`、`Charts.kt:201`、`DashboardTabs.kt` 各處 `basicMarquee()` 改為 `adaptiveMarquee(size.isCompact)`。**Compact 行為不變**。
- **F-2** 卡片內距依 window size 給值（Compact 12dp 不變、Expanded 16dp），可透過新增 `res/values-sw840dp/dimens.xml`（**新增**，不修改既有 `values/`）。
- **F-3** `ZoomChartScreen`（`Charts.kt:230`）：平板不強制 `SENSOR_LANDSCAPE`、不還原為 `PORTRAIT`；`TableZoom`（`:698`、`:706`）在平板改 `fillMaxWidth()`。
- **F-4** `WideTable`（`DashboardTabs.kt:1068`）在平板移除 `horizontalScroll` + 720dp 固定寬。

**驗收 F**：平板無跑馬燈動畫、無強制轉向、表格不需橫捲；手機不受影響。

---

## 5. 回歸與驗收

### 5.1 手機（Compact）不得退化 — 必查清單

| 項目 | 驗證方式 |
|---|---|
| 5 分頁外觀與順序 | 與 v0.6.3 逐頁比對（`DashboardScreen.kt:106`） |
| KPI 9 項數值與 YoY | 與 v0.6.3 相同（`DashboardScreen.kt:152`–`:160`） |
| 單月／多月圖表切換 | `if (isSingleMonth) HBarCard else LineCard`（`DashboardTabs.kt:203` 等）行為一致 |
| 5 個 BottomSheet | 可正常彈出／關閉／捲動（`DashboardScreen.kt:220/:372`、`DashboardTabs.kt:274/:768/:975`） |
| drill-down 1～4 層 | 返回鍵逐層退回（`Charts.kt:915` 起） |
| ZoomChartScreen | 開啟轉橫向、關閉轉直向（`Charts.kt:236/:240`） |
| 字型 5 級 | 滑桿（`DashboardScreen.kt:522`）與 `MainActivity.kt:33` 行為一致 |

### 5.2 既有測試（不得刪改其斷言）

- `app/src/test/java/com/example/hospital_dashboard/report/AggregationEngineTest.kt`
- `DimensionEngineTest.kt`
- `OpdRegressionTest.kt`
- `ReportChartAdapterTest.kt`
- `ReportContextAndRegistryTest.kt`
- `BedOtherRegressionTest.kt`
- `app/src/androidTest/java/com/example/hospital_dashboard/ExampleInstrumentedTest.kt`

**命令**：`./gradlew test` 與 `./gradlew connectedAndroidTest`（若有裝置）。

### 5.3 需新增的測試

| 類型 | 建議內容 | 位置 |
|---|---|---|
| 單元 | `WindowSize.toAdaptiveSize()` 邊界：599→Compact、600→Medium、839→Medium、840→Expanded | `app/src/test/.../ui/adaptive/` |
| 單元 | `AdaptiveSize.pick()`：Compact 一律回 compactValue | 同上 |
| 單元 | 圖表高度選擇函式（若新增 `adaptiveChartHeight`）之三段回傳值 | 同上 |
| UI（instrumented） | Compact/Medium/Expanded 下，`AdaptiveTabs` 分別為 TabRow / NavigationRail；選取切換後 `vm.tabIndex` 正確 | `app/src/androidTest/.../ui/` |
| UI | 面板在 Compact 為 BottomSheet、Expanded 為非 BottomSheet | 同上 |
| 回歸 | 分頁內容卡片數量與標題不因尺寸改變 | 同上 |

### 5.4 多尺寸 `@Preview`（目前專案為 0 個）

建議於 `DashboardScreen.kt` / `DashboardTabs.kt` / `Charts.kt` 新增 `@Preview`（使用 `@Preview(device = ...)` 或 `@Preview(widthDp = 411, heightDp = 891)` 等）：

```kotlin
@Preview(name = "Compact 411dp", widthDp = 411, heightDp = 891)
@Preview(name = "Medium 700dp", widthDp = 700, heightDp = 1000)
@Preview(name = "Expanded 1000dp", widthDp = 1000, heightDp = 1400)
@Composable
private fun DashboardPreview() { /* 以假資料組合 */ }
```

> Preview 需可編譯：若依賴 VM，建議抽出無 VM 的純呈現子元件供 Preview。此屬新增，不影響運行時。

### 5.5 平板實機檢查清單

- [ ] `sw600dp` 裝置（Medium）：左側導覽或 TabRow 正常、KPI 多欄、圖表雙欄。
- [ ] `sw840dp` 裝置（Expanded）：導覽固定抽屜、KPI 5 欄、圖表 3 欄、面板為側欄／對話框。
- [ ] 橫向↔直向切換：分頁、篩選、字型級別皆保留。
- [ ] 折疊機展開／收合跨級別時無崩潰、無狀態遺失。
- [ ] 大螢幕縮放（`adb shell wm size` / `wm density`）模擬各級。

### 5.6 效能注意（`AGENTS.md` §22）

- 資料來源為十餘萬列 xlsx（`XlsxReader`、`HospitalDb`），圖表以 `Canvas` + `textMeasurer` 手繪（`Charts.kt:2894` 起）。**網格化後同時渲染多張 Canvas**，需確認：
  - `LazyVerticalGrid` 只組合可見項（延遲載入），避免一次繪製數十張圖。
  - `produceState` / `LaunchedEffect(keys)`（`DashboardTabs.kt:84`）之 key 不含 window size 以外的無關變數，避免尺寸變化觸發重複查詢。
  - `basicMarquee` 關閉（§3.8）可減少持續重繪。
  - 圖表 `dynamicHeight=false` 時避免每幀重算高度。

---

## 6. 風險

| # | 風險 | 說明 | 對策 |
|---|---|---|---|
| 6.1 | **`LocalDensity` 放大 dp 的副作用** | `fontScale` 只影響 `sp`；畫面多處固定 dp 間距（如 `padding(12.dp)`、`height(240.dp)`）不隨之放大，字級越大越顯擁擠。平板若再調 dp，會與使用者字級疊加 | 平板以 `weight`/Grid 讓容器伸展；dimen 調整僅套用於平板分支；Compact 值不動 |
| 6.2 | **BottomSheet 高度限制** | 多處 `heightIn(max=260/300/320/340dp)`（`Charts.kt:950`、`:1761`、`:1819`、`:1895`、`:1969`、`:2043`、`:2203`、`:2286`、`:2407`）在平板視覺過扁；`ModalBottomSheet` 亦有系統最大高度 | 平板分支改 SideSheet/Dialog 或提高上限；**不改** Compact 數值 |
| 6.3 | **`basicMarquee` 在寬螢幕** | 持續動畫、大螢幕無必要、增加重繪 | 以 `adaptiveMarquee(enabled=isCompact)` 停用 |
| 6.4 | **動態高度函式在雙欄下的語意** | `dynamicLineHeight`（`Charts.kt:167`）與 `dynamicHBarHeight`（`:171`）為「單欄全寬」設計；雙欄後每欄寬減半，沿用會更扁 | 平板分支停用 `dynamicHeight`，改固定較高值或新函式 |
| 6.5 | **不得破壞統計與 drill-down 行為** | drill-down 覆蓋層（`Charts.kt:882`）、返回邏輯（`:921` 起）、`BackHandler` 若在重排中被改動，將破壞既有下鑽 | 平板僅「外框並排」，**核心 4 層邏輯與資料來源不動**；改動後必須回歸 drill-down |
| 6.6 | **巢狀捲動崩潰** | `LazyVerticalGrid` 放入 `verticalScroll` 的 `Column`（`DashboardScreen.kt:103`）會因無限高度約束崩潰 | 平板分支改固定高度／`weight(1f)` 容器，Grid 自帶捲動 |
| 6.7 | **強制轉向衝突** | `ZoomChartScreen` 強制橫向（`Charts.kt:236`）、關閉轉直向（`:240`），平板自然姿勢可能衝突 | 平板分支不強制轉向、不還原方向 |
| 6.8 | **相依版本風險** | `material3-window-size-class` 由 BOM 管理（安全）；`adaptive` 為獨立版本線 | 優先僅採 BOM 管理項；`adaptive` 非必要不引入 |
| 6.9 | **統計口徑漂移** | 任何「為了排版而改動數字呈現」都是 red line | 平板化僅 presentation；`data/*`、`report/*` 唯讀；驗收比對數值 |

---

## 7. 交付流程（依 `AGENTS.md`）

### 7.1 文件更新（§30 Documentation Is Part of the Implementation）

架構性變更視為 Definition of Done 的一部分，至少更新：

- `README.md`：新增版本歷程條目（見 7.3）。
- 若屬架構變更：`REPORT_ARCHITECTURE.md`（現況：`REPORT_ARCHITECTURE.md` 存在於根目錄）。
- 本指示文件本身（`/home/hpd/下載/Android平板版面配置開發指示文件.md`）建議納入專案 `docs/`（目前 `docs/` 已存在）。

> ⚠️ 依 §30「Do not create documentation filled with generic architecture theory」：更新內容須描述**本專案實際**檔案與行為。

### 7.2 版本提升

- `app/build.gradle.kts:16` `versionCode`：`9` → `10`（每次釋出遞增）。
- `app/build.gradle.kts:17` `versionName`：`0.6.3` → 建議 `0.7.0`（新增平板能力屬 feature）。
- 依既有慣例：release 以 debug keystore 簽章（`app/build.gradle.kts:24`–`:25` 註解）。

### 7.3 README 版本歷程

於 `README.md`「📝 版本演進歷程」（行 118 起）新增 `### **v0.7.0 (Latest)**`，格式比照 v0.6.3（行 120）之條列風格；同時更新：

- 行 3 版本徽章（`version-0.6.3` → `version-0.7.0`）。
- 行 12–15 下載區塊（路徑與 Release 連結）。

### 7.4 Release APK 命名

依現況 `release/hospital_dashboard_v0.6.3.apk`（另有 `v0.6.1`、`v0.6.2`、`v0.6.apk`）：

- 產出 `release/hospital_dashboard_v0.7.0.apk`。
- **保留**舊 APK（§29 No Mass Deletion）。

### 7.5 Commit 訊息風格（§34）

小而獨立、單一目的；建議切分：

```text
build: add material3-window-size-class dependency
feat: introduce AdaptiveSize / WindowSize abstraction
refactor: route tablet navigation through AdaptiveTabs
feat: grid layout for dashboard tabs on large screens
feat: adaptive panels (sheet/dialog/sidesheet)
feat: adaptive marquee and zoom layout for tablets
test: add adaptive size class unit/UI coverage
docs: document tablet layout support (README v0.7.0)
```

> 不得把「無關清理」與「平板功能」混在同一 commit（§34）。不得改寫 Git 歷史。

### 7.6 停止條件（§35）

遇到以下情形**必須停止並回報**（格式見 §35：Problem / Evidence / Possible options / Recommended option / Question）：

- 某個平板效果**必須**改動統計口徑才能達成。
- drill-down 行為需變更。
- 需放寬安全／隱私設定。
- 需刪除舊碼才能施工。
- 與 SRS 或既有已驗證行為衝突。

---

## 8. 附錄

### 8.1 變更檔案清單模板

| # | 檔案 | 變更型態（新增/修改） | 步驟 | 說明 | 回歸測試 |
|---|---|---|---|---|---|
| 1 | `gradle/libs.versions.toml` | 修改 | A | 新增 window-size-class 相依 | assembleDebug |
| 2 | `app/build.gradle.kts` | 修改 | A | 新增 implementation | assembleDebug |
| 3 | `ui/adaptive/WindowSize.kt` | 新增 | B | 尺寸級別抽象 | 單元測試 |
| 4 | `MainActivity.kt` | 修改 | B | 提供 LocalWindowSize（選配） | 手動 |
| 5 | `ui/adaptive/AdaptiveNavigation.kt` | 新增 | C | TabRow / Rail | UI 測試 |
| 6 | `ui/DashboardScreen.kt` | 修改 | C/E/F | 導覽、Sheet、marquee | 手動＋UI |
| 7 | `ui/DashboardTabs.kt` | 修改 | D/E/F | TabGrid、Sheet、WideTable | UI |
| 8 | `ui/charts/Charts.kt` | 修改 | B/D/E/F | 收斂 isWide、Zoom 雙欄 | UI＋手動 |
| 9 | `ui/adaptive/AdaptiveSheet.kt` | 新增 | E | 面板三態 | UI |
| 10 | `res/values-sw840dp/dimens.xml` | 新增 | F | 平板 dimen | 手動 |
| 11 | `README.md` | 修改 | 交付 | 版本歷程 | 文件審閱 |
| 12 | `release/hospital_dashboard_v0.7.0.apk` | 新增 | 交付 | 發行檔 | 安裝驗證 |

### 8.2 驗收表格模板

| 級別 | 裝置/模擬寬度 | 導覽 | KPI | 圖表欄數 | 面板型態 | 結果 |
|---|---|---|---|---|---|---|
| Compact | 411dp | TabRow | LazyRow | 1 | BottomSheet | ☐ |
| Medium | 700dp | Rail/TabRow | Grid 3 | 2 | Dialog | ☐ |
| Expanded | 1000dp | PermanentDrawer | Grid 5 | 3 | SideSheet | ☐ |
| 旋轉 | 平板 直↔橫 | 保留分頁/篩選/字型 | — | — | — | ☐ |
| 回歸 | 手機所有分頁 | 與 v0.6.3 相同 | 相同 | 相同 | 相同 | ☐ |

### 8.3 參考檔案索引（路徑:行號）

**建置／治理**

- `app/build.gradle.kts:9,14,15,16,17,24-25,41-57`
- `gradle/libs.versions.toml:2,9,10,20-27`
- `app/src/main/AndroidManifest.xml:17-22`
- `AGENTS.md`：§21(776) §22(799) §23(829) §24(850) §25(892) §27(941) §28(955) §29(977) §30(1003) §34(1199) §35(1220) §36(1248) §37(1279)
- `README.md:3,12-15,118,120,134,149,176`
- `.gitignore`（機敏資料排除段）

**進入點／VM**

- `MainActivity.kt:24-59`（`:31-32` 字型、`:33-39` Density、`:40` CompositionLocalProvider、`:41-55` 分派）
- `DashboardViewModel.kt:21-31`（UiState）、`:52-55`（tabIndex）、`:58-60`（zoomChart）、`:63-64`（fontScaleLevel）、`:71-72`（倍率）

**主畫面**

- `ui/DashboardScreen.kt:68,73,75-101,82,89,103,106,111,131,178,186,209,220,222,304,306,315,345,372,605`

**分頁**

- `ui/DashboardTabs.kt:100,109,125,151,166,173,181,203-207,274,291,311,427,655,675,725,753,765,768,862,882,932,958,972,975,1068,1076,1132,1234,1260,1301,1423`
- `ui/AnalysisTab.kt`（AI 分析分頁，共 503 行）

**圖表**

- `ui/charts/Charts.kt:167,171,183,195,201,221,230,236,240,615,660,698,706,729,882,897,915-990,1159,1489,1505,1761,1819,1895,1969,2043,2203,2286,2407,2840,2894-2925,2977,3037-3043,3200,3263-3273,3311,3463`

**測試**

- `app/src/test/java/com/example/hospital_dashboard/report/{AggregationEngineTest,DimensionEngineTest,OpdRegressionTest,ReportChartAdapterTest,ReportContextAndRegistryTest,BedOtherRegressionTest}.kt`
- `app/src/androidTest/java/com/example/hospital_dashboard/ExampleInstrumentedTest.kt`

---

## 結語（給實作 Agent 的最後提醒）

> **先讀、再盤點、再設計、後變更、最後驗證；永遠保持文件同步。**
> —— 對齊 `AGENTS.md` Final Rule。

本任務的核心不是「把畫面變漂亮」，而是**在不改變任何醫院營運數據意義的前提下，讓同一份報表在更大螢幕上呈現得更有效率**。任何與此牴觸的改動，都應先停下來回報。
