# 醫院營運儀表板 (Hospital Operations Dashboard) — Android

將 Python/Streamlit 版醫院業務儀表板改製為 Android APK 工具。

## 功能

- 📂 **檔案選取**：由使用者自行挑選手機中的業務資料表檔案（`業務資料彙整-YYYYMMDD.xlsx`）
- 📅 **資料更新日期**：自動從檔名擷取民國年月日（如 `業務資料彙整-1150508.xlsx` → 民國115年05月08日）並顯示於儀表板
- 🗄️ **離線匯入**：解析 xlsx 7 個工作表（門診/住院/病床/院外門診/會計/營運指標/醫師數，約 10 萬列）至內建 SQLite，無需網路
- 📊 **儀表板 5 分頁**：門急診、住院、病床利用、其他服務、各院區佔床率明細
- 📈 **KPI 快速摘要**：固定顯示資料最新一個月與去年同期比較；點擊展開各院區明細
- 🔍 **圖表橫向放大檢視**：點擊任一圖表卡片 → 橫向全螢幕顯示，支援雙指縮放/拖曳/雙擊；點擊資料點或長條顯示詳細數據與去年同期增減
- ⚙️ **篩選**：年度/月份/院區/部別/科別（與 KPI 摘要分離）

## 技術

- Kotlin + Jetpack Compose（Material 3）
- 自製輕量 xlsx 解析器（ZipFile + XmlPullParser，零第三方依賴）
- 圖表以 Compose Canvas 自繪（折線/橫條/直條/圓餅/熱力表格）

## 版本

- v0.1：初始版本（本版功能如上）

## 建置

```bash
./gradlew :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```
