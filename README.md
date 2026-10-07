# 醫院營運儀表板（Hospital Operations Dashboard）

[![版本 0.8.4](https://img.shields.io/badge/version-0.8.4-blue)](https://github.com/maria0010101/hospital_dashboard/releases/tag/v0.8.4)
[![平台 Android｜Windows](https://img.shields.io/badge/platform-Android%20%7C%20Windows-green)](https://github.com/maria0010101/hospital_dashboard/releases/tag/v0.8.4)

以本機 Excel 業務報表建立醫院營運儀表板，提供 **Android 手機／平板**與 **Windows 桌面版**。匯入後可離線瀏覽 KPI、趨勢、院區比較及多層明細；AI 分析是選用功能，是否需要網路取決於所選 AI 服務。兩平台共用工作表定義、資料查詢與指標計算，分別實作檔案讀取、資料庫及適合螢幕尺寸的介面。

## 下載與開始使用

目前版本為 **0.8.4**，所有安裝檔均收錄於 [GitHub Release](https://github.com/maria0010101/hospital_dashboard/releases/tag/v0.8.4)。

| 平台 | 安裝檔 |
| --- | --- |
| Android | [下載 APK](https://github.com/maria0010101/hospital_dashboard/releases/download/v0.8.4/hospital_dashboard_v0.8.4.apk)（Android 7.0／API 24 起） |
| Windows 安裝版 | [EXE](https://github.com/maria0010101/hospital_dashboard/releases/download/v0.8.4/HospitalDashboard-0.8.4.exe) 或 [MSI](https://github.com/maria0010101/hospital_dashboard/releases/download/v0.8.4/HospitalDashboard-0.8.4.msi) |
| Windows 免安裝版 | [Portable ZIP](https://github.com/maria0010101/hospital_dashboard/releases/download/v0.8.4/HospitalDashboard-windows-x64-portable.zip) |
| 檔案校驗 | [SHA256SUMS.txt](https://github.com/maria0010101/hospital_dashboard/releases/download/v0.8.4/SHA256SUMS.txt) |

1. 安裝應用程式；Windows 免安裝版請解壓縮 ZIP 後執行程式。
2. 在程式內選取**符合本專案工作表結構**的本機 `.xlsx` 業務報表。程式會將資料匯入裝置上的 SQLite 資料庫，原始活頁簿不會因此被改寫。
3. 使用「篩選」調整年度、月份、院區、部別、科別與去年同期比較；點擊 KPI 或圖表可查看明細。
4. 如需 AI 分析，再到「AI 分析」頁面選擇服務與分析焦點、檢查脫敏預覽後送出。

匯入器目前對應 **9 張工作表**：門診業務資料、住院業務資料、病床別業務資料、院外門診部服務量、會計室報表資料、其他營運管理指標資料、醫師服務量、差額病床業務資料、門診篩檢疫苗人次。程式與 Release **不附真實業務活頁簿或資料庫**；資料需由使用者自行提供。

## 目前功能

### 全院 KPI 與資料口徑

頂部提供 **11 項 KPI**：門診人次、急診人次、總診次、住院人次、住院人日、總佔床率（實開病床）、院外門診、洗腎人次、健檢人次、總收入與自費收入。點擊單一指標，只展開該項的各院區明細，包含本月數據、去年同月、近三個月變化，以及今年與去年各自 **1 月至當月**的累計和差異。

- **總佔床率（實開病床）**＝納入病床的住院人日合計 ÷ 實際床日數合計 × 100%；排除病床大類「其他」與「產後（小孩）」。累計佔床率也先各自加總人日及床日，再相除，**不平均各月份百分比**。
- **總收入／自費收入**由營運管理指標中的門診與住院收入欄位加總。「其他」分頁的各院區收入累計圖以選定的最新月份為截止點；去年同期也只累計到**去年同一月份**，不使用去年全年金額。
- 不同圖表可能有各自的篩選與統計口徑；以上公式特指頂部 KPI 與對應的院區明細。

### 五個主要分頁

| 分頁 | 內容 |
| --- | --- |
| 門急診 | 門診、急診、初診與部別趨勢；部分圖表可切換扣除疫苗施打人次，並可依資料層級下鑽。醫師層級因疫苗來源沒有醫師代碼，不進行該項扣除。 |
| 住院 | 住院／出院人次與人日、院區和部別比較、平均住院日及多層明細。 |
| 病床 | 病床類別與院區的佔床、開床趨勢；病床類別及差額病床熱力圖，可查看護理站明細。 |
| 其他 | 院外門診、洗腎、健檢、手術、生產、總收入及自費收入趨勢與院區累計比較。 |
| AI 分析 | 載入或貼上資料、依分析焦點產生混淆內容、預覽脫敏、連線分析，以及貼回外部混淆報告於本機還原。 |

圖表支援去年同期對照、點擊放大與適用圖表的階層式下鑽；單月與多月篩選會使用相應的圖表呈現方式。手機、平板與桌面版採自適應版面；較寬螢幕依可用寬度排列圖表，不固定為三欄。篩選頁面也提供深色模式、三種配色（典雅紫、專業藍、清新青）與五級字體大小。

### AI 分析與資料保護

可選擇本地 Ollama、自建 vLLM、Google Gemini、OpenAI、Claude、DeepSeek 或自訂 AI。線上服務使用程式設定的服務網址；本地／自架／自訂服務可輸入 Base URL。使用者可測試連線、選取分析焦點，從本機資料產生混淆後的分析文字，也可貼上其他工具產出的混淆報告，依本機對照表還原名稱。Android 支援複製及匯出文字／PDF；桌面版支援複製及匯出 Markdown。

**一般儀表板查詢與匯入不需要上傳資料。** 呼叫線上 AI 時，送出的雖是經混淆的文字、對照表不隨請求上傳，但內容仍可能包含營運數值；請先確認脫敏預覽及所選服務的資料使用範圍。AI 設定、混淆對照表及匯入後資料保存在本機，不納入 Git 版本控制或 Release 資產。

## 專案結構與開發

| 模組 | 用途 |
| --- | --- |
| `:core` | 共用工作表定義與讀取／資料庫介面、`DashboardRepo` 指標查詢、AI 連線與混淆邏輯 |
| `:app` | Android／平板的 Jetpack Compose 介面、XLSX 讀取及 SQLite 實作 |
| `:desktop` | Windows 桌面版的 Compose Multiplatform 介面、XLSX 讀取、JDBC SQLite 實作與安裝包 |

開發環境使用 **JDK 17**；Android 建置另需 Android SDK（目前 `compileSdk = 37`）。常用指令：

```bash
# 核心、桌面與 Android 單元測試
./gradlew :core:test :desktop:test :app:testDebugUnitTest

# Android APK
./gradlew :app:assembleDebug :app:assembleRelease

# Windows 安裝包需在 Windows 環境執行；GitHub Actions 亦使用 Windows runner
./gradlew :desktop:packageDistributionForCurrentOS :desktop:createDistributable
```

Android Release APK 目前沿用專案既有的 debug keystore 簽章設定。Windows 的線上建置流程見 [build-windows.yml](.github/workflows/build-windows.yml)，0.8.2 的 [CI 執行紀錄](https://github.com/maria0010101/hospital_dashboard/actions/runs/36955749559) 包含核心／桌面測試與 EXE、MSI、ZIP 產出。

設計目標與現況盤點分別見 [SRS.md](SRS.md) 與 [REPORT_ARCHITECTURE.md](REPORT_ARCHITECTURE.md)；**SRS 的目標架構不等於目前全部已實作的功能**。跨平台移植紀錄見 [REPORT_MIGRATION.md](REPORT_MIGRATION.md)。

## 版本摘要

- **[0.8.4](https://github.com/maria0010101/hospital_dashboard/releases/tag/0.8.4)**：Windows 桌面版同步實現文字選取複製（全院 KPI、圖表明細卡片、下鑽展開、表格全面支援滑鼠選取與複製文字）與圖表匯出功能（匯出 JPEG 圖片、圖表分析模板 Markdown 文字報告、AI 混淆深度分析報告）；全平台通過 JVM 運作與測試。
- **[0.8.3](https://github.com/maria0010101/hospital_dashboard/releases/tag/0.8.3)**：放大圖表右上角新增匯出功能（匯出 JPEG 圖片、圖表分析模板 Markdown 文字報告、AI 混淆深度分析報告）；全院 KPI、圖表明細與多層展開資料全面支援滑鼠選取與長按文字複製。
- **[0.8.2](https://github.com/maria0010101/hospital_dashboard/releases/tag/0.8.2)**：11 項 KPI、加權總佔床率及同月累計比較、收入同比區間修正、深色放大圖表文字修正。
- **[0.8.1](https://github.com/maria0010101/hospital_dashboard/releases/tag/0.8.1)**：平板／桌面版面調整、逐項 KPI 院區明細、AI 服務與混淆還原流程。
- **[v0.8.0](https://github.com/maria0010101/hospital_dashboard/releases/tag/v0.8.0)**：門急診疫苗人次排除與對應資料匯入。

更早版本與完整變更紀錄請見 [Releases](https://github.com/maria0010101/hospital_dashboard/releases)。
