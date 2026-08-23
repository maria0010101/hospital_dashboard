package com.example.hospital_dashboard

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.hospital_dashboard.data.DashboardRepo
import com.example.hospital_dashboard.data.FileNameParser
import com.example.hospital_dashboard.data.HospitalDb
import com.example.hospital_dashboard.data.SheetConfigs
import com.example.hospital_dashboard.data.XlsxReader
import com.example.hospital_dashboard.ui.charts.ChartContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface UiState {
    data object Loading : UiState
    data object NoData : UiState                       // 尚未匯入 → 顯示檔案選擇
    data class Importing(
        val currentSheet: String, val rowCount: Int,
        val index: Int, val total: Int
    ) : UiState
    data class Ready(
        val updateDate: String?,                       // 資料更新日期(由檔名擷取)
        val sourceFile: String?,
        val tableCounts: List<Pair<String, Long>>
    ) : UiState
    data class ImportError(val message: String) : UiState
}

class DashboardViewModel(app: Application) : AndroidViewModel(app) {

    val db: HospitalDb by lazy { HospitalDb(getApplication()) }
    val repo: DashboardRepo by lazy { DashboardRepo(db) }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState

    /** 儀表板篩選條件。 */
    val filters = MutableStateFlow(DashboardRepo.Filters(years = emptyList()))

    /** 全螢幕放大的圖表(null = 未開啟)。 */
    private val _zoomChart = MutableStateFlow<ChartContent?>(null)
    val zoomChart: StateFlow<ChartContent?> = _zoomChart

    fun openZoom(content: ChartContent) { _zoomChart.value = content }

    fun closeZoom() { _zoomChart.value = null }

    init { refresh() }

    fun refresh() {
        if (db.hasImportedData()) {
            // App 重啟後 ViewModel 重建：若無篩選條件，預設最近 3 年
            if (filters.value.years.isEmpty()) {
                filters.value = DashboardRepo.Filters(years = availableYears().takeLast(3))
            }
            _uiState.value = UiState.Ready(
                updateDate = db.getMeta("update_date"),
                sourceFile = db.getMeta("source_file"),
                tableCounts = db.tableRowCounts()
            )
        } else {
            _uiState.value = UiState.NoData
        }
    }

    /** 使用者從檔案選擇器選取的 xlsx → 複製到內部儲存 → 匯入。 */
    fun importFromUri(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = UiState.Importing("準備中…", 0, 0, 7)
            try {
                val ctx = getApplication<Application>()
                val name = queryDisplayName(ctx, uri) ?: "業務資料彙整.xlsx"
                val file = File(ctx.filesDir, name)
                withContext(Dispatchers.IO) {
                    ctx.contentResolver.openInputStream(uri)?.use { input ->
                        file.outputStream().use { out -> input.copyTo(out) }
                    } ?: throw IllegalStateException("無法讀取所選檔案")
                }
                val date = FileNameParser.parse(name)

                withContext(Dispatchers.IO) {
                    XlsxReader.openBook(file).use { book ->
                        // 驗證目標工作表是否存在
                        val missing = SheetConfigs.allMissing(book)
                        if (missing.isNotEmpty()) {
                            throw IllegalStateException(
                                "檔案中缺少工作表: ${missing.joinToString("、")}"
                            )
                        }
                        db.importWorkbook(book, date?.display, name) { sheet, cnt, idx, total ->
                            _uiState.value = UiState.Importing(sheet, cnt, idx, total)
                        }
                    }
                }
                // 預設篩選：最近 3 年
                val years = availableYears()
                filters.value = DashboardRepo.Filters(years = years.takeLast(3))
                _uiState.value = UiState.Ready(date?.display, name, db.tableRowCounts())
            } catch (e: Exception) {
                _uiState.value = UiState.ImportError(e.message ?: "匯入失敗")
            }
        }
    }

    /** 回到檔案選擇(不刪除舊資料，匯入為整批交易會自動取代)。 */
    fun backToFilePick() {
        _uiState.value = UiState.NoData
    }

    private fun queryDisplayName(ctx: Context, uri: Uri): String? {
        ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) return c.getString(idx)
        }
        return null
    }

    fun availableYears(): List<String> {
        return db.query("SELECT DISTINCT year FROM outpatient_service ORDER BY CAST(year AS INTEGER)")
            .map { it[0]?.toString() ?: "" }.filter { it.isNotEmpty() }
    }

    fun availableMonths(): List<String> {
        return db.query("SELECT DISTINCT month FROM outpatient_service ORDER BY CAST(month AS INTEGER)")
            .map { it[0]?.toString() ?: "" }.filter { it.isNotEmpty() }
    }

    fun availableBranches(): List<String> {
        return db.query("SELECT DISTINCT branch FROM outpatient_service WHERE branch IS NOT NULL ORDER BY branch")
            .map { it[0]?.toString() ?: "" }.filter { it.isNotEmpty() }
    }

    fun availableDeptDivs(): List<String> {
        return db.query("SELECT DISTINCT dept_div FROM outpatient_service WHERE dept_div IS NOT NULL ORDER BY dept_div")
            .map { it[0]?.toString() ?: "" }.filter { it.isNotEmpty() }
    }

    fun availableDepts(divs: List<String>? = null): List<String> {
        val sql = if (divs.isNullOrEmpty()) {
            "SELECT DISTINCT dept FROM outpatient_service WHERE dept IS NOT NULL ORDER BY dept"
        } else {
            val ph = divs.joinToString(",") { "?" }
            "SELECT DISTINCT dept FROM outpatient_service WHERE dept IS NOT NULL AND dept_div IN ($ph) ORDER BY dept"
        }
        return db.query(sql, if (divs.isNullOrEmpty()) emptyArray() else divs.toTypedArray())
            .map { it[0]?.toString() ?: "" }.filter { it.isNotEmpty() }
    }
}
