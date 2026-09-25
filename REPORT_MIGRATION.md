# 跨平台多模組與 Windows 桌面版本移植紀錄 (REPORT_MIGRATION.md)

## 1. 移植背景與設計原則
依據《Windows桌面版本移植開發指示文件.md》之指示，將原 Android 專案重構為多模組架構（Gradle Multi-module），並以 **Compose Multiplatform (CMP) Desktop (Kotlin/JVM)** 實作 Windows 桌面原生應用程式，同時新增全域深色模式（Dark Mode）。

核心治理原則：
1. **業務規則零猜測（Never Guess Hospital Business Rules）**：`DashboardRepo` 與 `report/**` 中的 SQL、聚合口徑、YoY 比較邏輯 100% 保持不變，雙平台共用同一份程式碼。
2. **零機敏資料入版控**：嚴禁提交任何實體 `.xlsx` 或 `.db` 檔案，所有測試採用合成資料或本機條件測試。
3. **無退行維護**：既有 47+ 項單元測試持續全綠，Android 原生功能、版本號及 APK 建置流程完全不受影響。

---

## 2. 模組分層架構
```
[settings.gradle.kts]
  ├── :core     (純 Kotlin/JVM 程式庫，零 Android 依賴)
  │     ├── data/HospitalDb.kt          (跨平台資料庫抽象契約)
  │     ├── data/JdbcHospitalDb.kt      (JDBC SQLite 交易與查詢實作)
  │     ├── data/XlsxBook.kt            (活頁簿讀取抽象契約)
  │     ├── data/StaxXlsxReader.kt      (StAX OOXML 串流高效解析器)
  │     ├── data/DashboardRepo.kt       (全量 122 個指標查詢與聚合邏輯)
  │     ├── data/SheetConfig.kt         (9 大工作表結構映射)
  │     ├── data/FileNameParser.kt      (民國日期檔名解析)
  │     ├── data/ChartModels.kt         (圖表幾何資料模型與 Fmt 格式化)
  │     ├── data/AiClient.kt            (SSE 串流 AI 客戶端)
  │     ├── data/Anonymizer.kt          (本機雙向脫敏混淆引擎)
  │     └── report/**                   (統一報表平台引擎與定義)
  │
  ├── :app      (Android 應用程式模組，依賴 :core)
  │     ├── data/AndroidHospitalDb.kt   (Android SQLiteDatabase 實作)
  │     ├── data/XlsxReader.kt          (Android XmlPullParser 實作)
  │     ├── DashboardViewModel.kt       (AndroidViewModel 狀態機與深色模式持久化)
  │     └── ui/**                       (Android Jetpack Compose UI、自適應導航、圓形開關)
  │
  └── :desktop  (Compose Multiplatform 桌面應用程式模組，依賴 :core)
        ├── DesktopViewModel.kt         (純 JVM 狀態機，本機目錄與設定檔管理)
        ├── Main.kt                     (桌面應用程式視窗進入點)
        ├── theme/Theme.kt              (深色/淺色高對比主題定義)
        └── ui/**                       (桌面版自適應 UI、自繪圖表、AWT 檔案對話框、AI 串流分析)
```

---

## 3. 深色模式 (Dark Mode) 實作
- **狀態儲存**：`DashboardViewModel` (Android `SharedPreferences`) 與 `DesktopViewModel` (本機 `settings.json`) 支援 `isDarkMode` 狀態持久化。
- **操作介面**：於「⚙ 篩選條件」面板（`FilterSheet`）之 YoY 開關下方設置「🌙 深色模式」圓形滑動開關（Material 3 `Switch`），並在「重設」與「套用」按鈕連動重置或儲存。
- **色彩適配**：
  - 定義 `DarkColorScheme`（Surface: `#141218`，SurfaceVariant: `#2B2930`，OnSurface: `#E6E0E9`）。
  - 全數自繪圖表（`Charts.kt`）、明細卡片與 Markdown 呈現器全面替換硬編碼白色，動態讀取 `MaterialTheme.colorScheme`。

---

## 4. Windows 桌面版本特色
1. **本機目錄與離線安全**：
   - 資料庫儲存於 `%LOCALAPPDATA%\HospitalDashboard\hospital_data.db`（Linux/Mac 開發環境自動 fallback 至 `~/.hospital_dashboard/`）。
   - 設定檔儲存於 `%LOCALAPPDATA%\HospitalDashboard\settings.json`。
   - 所有資料僅留存在本機 SQLite，絕不主動對外連線傳輸。
2. **檔案選取與匯入**：
   - 呼叫系統原生 AWT `FileDialog` 進行 `.xlsx` 檔案挑選。
   - 基於 `StaxXlsxReader` 串流解析，二十餘萬列資料於數秒內完成寫入並提供即時進度百分比。
3. **寬螢幕自適應**：
   - 左側常駐 `NavigationRail`，右側完整利用寬螢幕空間呈現 9 項 KPI 摘要與寬版圖表。
   - 下鑽明細與放大圖表均以置中對話框卡片（`AdaptiveSheet` / `Dialog`）呈現，避免破版拉伸。
4. **AI 分析與文字匯出**：
   - 整合本地 Ollama / vLLM 串流推論，支援一鍵複製到系統剪貼簿（自動脫敏/還原）。
   - 支援將 AI 分析決策報告另存為 `.md` Markdown 檔案。

---

## 5. GitHub Actions 線上編譯工作流程
- **配置檔案**：`.github/workflows/build-windows.yml`
- **執行環境**：`windows-latest`，JDK 17 (Temurin)
- **自動化產出**：
  1. 執行 `:core:test` 與 `:desktop:test` 自動化驗證。
  2. 透過 Compose Desktop 內建打包工具產生 Windows MSI 安裝包與 EXE 安裝包。
  3. 透過 `createDistributable` 產生內建 JRE 的免安裝綠色版，並自動壓縮為 `HospitalDashboard-windows-x64-portable.zip`。
  4. 自動上傳 Artifacts；若觸發 Git Tag（`v*`）則自動附加至 GitHub Release Assets。
