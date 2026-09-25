package com.example.hospital_dashboard.desktop

import com.example.hospital_dashboard.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.io.File

sealed interface UiState {
    data object Loading : UiState
    data object NoData : UiState
    data class Importing(
        val currentSheet: String, val rowCount: Int,
        val index: Int, val total: Int
    ) : UiState
    data class Ready(
        val updateDate: String?,
        val sourceFile: String?,
        val tableCounts: List<Pair<String, Long>>
    ) : UiState
    data class ImportError(val message: String) : UiState
}

fun getDesktopAppDataDir(): File {
    val localAppData = System.getenv("LOCALAPPDATA")
    val baseDir = if (!localAppData.isNullOrBlank()) {
        File(localAppData, "HospitalDashboard")
    } else {
        File(System.getProperty("user.home"), ".hospital_dashboard")
    }
    if (!baseDir.exists()) {
        baseDir.mkdirs()
    }
    return baseDir
}

class DesktopViewModel(val appDir: File = getDesktopAppDataDir()) {

    val dbFile = File(appDir, "hospital_data.db")
    val settingsFile = File(appDir, "settings.json")

    val db: HospitalDb by lazy { JdbcHospitalDb(dbFile) }
    val repo: DashboardRepo by lazy { DashboardRepo(db) }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState

    val filters = MutableStateFlow(DashboardRepo.Filters())
    val tabIndex = MutableStateFlow(0)

    val fontScaleLevel = MutableStateFlow(0)
    val isDarkMode = MutableStateFlow(false)

    private val _zoomChart = MutableStateFlow<com.example.hospital_dashboard.desktop.ui.charts.ChartContent?>(null)
    val zoomChart: StateFlow<com.example.hospital_dashboard.desktop.ui.charts.ChartContent?> = _zoomChart

    fun openZoom(content: com.example.hospital_dashboard.desktop.ui.charts.ChartContent) { _zoomChart.value = content }
    fun closeZoom() { _zoomChart.value = null }

    val aiConfigFile = File(appDir, "ai_config.json")

    fun loadAiConfig(): AiProviderConfig {
        return if (aiConfigFile.exists()) {
            AiProviderConfig.fromJson(aiConfigFile.readText()) ?: AiProviderConfig()
        } else AiProviderConfig()
    }

    fun saveAiConfig(cfg: AiProviderConfig) {
        aiConfigFile.writeText(cfg.toJson())
    }

    val anonymizerMappingFile = File(appDir, "anonymizer_map.json")
    val anonymizer = if (anonymizerMappingFile.exists()) {
        try {
            Anonymizer.fromJson(anonymizerMappingFile.readText())
        } catch (e: Exception) {
            Anonymizer()
        }
    } else {
        Anonymizer()
    }

    fun saveAnonymizerMapping() {
        try {
            anonymizerMappingFile.writeText(anonymizer.toJson())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        val FONT_SCALE_MULTIPLIERS = listOf(1.00f, 1.10f, 1.20f, 1.30f, 1.40f)
        val FONT_SCALE_NAMES = listOf("最小 (目前)", "較小", "標準", "較大", "最大")

        fun resolveDefaultYears(allYears: List<String>): List<String> {
            val non113 = allYears.filter { it != "113" }
            val target = non113.filter { it == "114" || it == "115" }
            return if (target.isNotEmpty()) target
            else if (non113.isNotEmpty()) non113.takeLast(2)
            else allYears.takeLast(2)
        }
    }

    init {
        loadSettings()
        refresh()
    }

    private fun loadSettings() {
        if (settingsFile.exists()) {
            try {
                val json = JSONObject(settingsFile.readText())
                isDarkMode.value = json.optBoolean("dark_mode", false)
                fontScaleLevel.value = json.optInt("font_scale_level", 0).coerceIn(0, 4)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun saveSettings() {
        try {
            val json = JSONObject()
            json.put("dark_mode", isDarkMode.value)
            json.put("font_scale_level", fontScaleLevel.value)
            settingsFile.writeText(json.toString(2))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setDarkMode(enabled: Boolean) {
        isDarkMode.value = enabled
        saveSettings()
    }

    fun setFontScaleLevel(level: Int) {
        fontScaleLevel.value = level.coerceIn(0, 4)
        saveSettings()
    }

    fun defaultYears(allYears: List<String> = availableYears()): List<String> =
        resolveDefaultYears(allYears)

    fun refresh() {
        if (db.hasImportedData()) {
            if (filters.value.years.isEmpty()) {
                filters.value = DashboardRepo.Filters(years = defaultYears())
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

    fun backToFilePick() {
        _uiState.value = UiState.NoData
    }

    fun importFromFile(file: File) {
        scope.launch {
            _uiState.value = UiState.Importing("準備匯入...", 0, 0, SheetConfigs.ALL.size)
            try {
                val updateDateDisplay = FileNameParser.parse(file.name)?.display
                withContext(Dispatchers.IO) {
                    val book = StaxXlsxReader.openBook(file)
                    db.importWorkbook(book, updateDateDisplay, file.name) { sheet, count, idx, total ->
                        _uiState.value = UiState.Importing(sheet, count, idx, total)
                    }
                }
                filters.value = DashboardRepo.Filters(years = defaultYears())
                _uiState.value = UiState.Ready(
                    updateDate = db.getMeta("update_date"),
                    sourceFile = db.getMeta("source_file"),
                    tableCounts = db.tableRowCounts()
                )
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = UiState.ImportError(e.message ?: "匯入失敗")
            }
        }
    }

    fun availableYears(): List<String> =
        db.query("SELECT DISTINCT year FROM outpatient_service WHERE year IS NOT NULL ORDER BY year", emptyArray())
            .mapNotNull { it.firstOrNull()?.toString() }

    fun availableMonths(): List<String> {
        return db.query("SELECT DISTINCT month FROM outpatient_service ORDER BY CAST(month AS INTEGER)", emptyArray())
            .map { it[0]?.toString() ?: "" }.filter { it.isNotEmpty() }
    }

    fun availableBranches(): List<String> {
        return db.query(
            """SELECT branch_name FROM outpatient_service WHERE branch_name IS NOT NULL AND branch_name != ''
               UNION SELECT branch_name FROM inpatient_service WHERE branch_name IS NOT NULL AND branch_name != ''
               UNION SELECT branch_name FROM bed_type_service WHERE branch_name IS NOT NULL AND branch_name != ''
               UNION SELECT branch_name FROM offsite_clinic_service WHERE branch_name IS NOT NULL AND branch_name != ''
               UNION SELECT branch_name FROM accounting_report WHERE branch_name IS NOT NULL AND branch_name != ''
               UNION SELECT branch_name FROM ops_management_indicators WHERE branch_name IS NOT NULL AND branch_name != ''
               ORDER BY branch_name""",
            emptyArray()
        ).map { it[0]?.toString() ?: "" }.filter { it.isNotEmpty() }
    }

    fun availableDeptDivs(): List<String> {
        val cond = "dept_div IS NOT NULL AND dept_div != '' AND dept_div NOT IN ('#N/A','NULL')"
        return db.query(
            """SELECT DISTINCT dept_div FROM outpatient_service WHERE $cond
               UNION SELECT DISTINCT dept_div FROM inpatient_service WHERE $cond
               UNION SELECT DISTINCT dept_div FROM physician_service WHERE $cond
               ORDER BY dept_div""",
            emptyArray()
        ).map { it[0]?.toString() ?: "" }.filter { it.isNotEmpty() }
    }

    fun availableDepts(divs: List<String>? = null): List<String> {
        val divCond = if (divs.isNullOrEmpty()) "" else "AND dept_div IN (${divs.joinToString(",") { "?" }})"
        val sql = """SELECT DISTINCT dept FROM (
            SELECT dept, dept_div FROM outpatient_service WHERE dept IS NOT NULL AND dept != ''
            UNION ALL SELECT dept, dept_div FROM physician_service WHERE dept IS NOT NULL AND dept != ''
            UNION ALL SELECT dept, dept_div FROM inpatient_service WHERE dept IS NOT NULL AND dept != ''
        ) WHERE 1=1 $divCond ORDER BY dept"""
        return db.query(sql, if (divs.isNullOrEmpty()) emptyArray() else divs.toTypedArray())
            .map { it[0]?.toString() ?: "" }.filter { it.isNotEmpty() }
    }

    fun close() {
        scope.cancel()
        db.close()
    }
}
