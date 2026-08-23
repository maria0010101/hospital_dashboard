package com.example.hospital_dashboard.data

/**
 * 工作表 → SQLite 資料表 對應設定。
 * 移植自 Python 版 hospital_data_tool.py 的 SHEET_CONFIG。
 * 原始 Excel 第 1 列為表頭(捨棄)，資料自第 2 列開始；
 * 每列只取前 columns.size 個欄位，依序對應 columns 名稱。
 */
data class SheetConfig(
    val sheet: String,        // Excel 工作表名稱
    val table: String,        // SQLite 資料表名稱
    val columns: List<String> // 欄位名稱(依序)
)

object SheetConfigs {
    val ALL: List<SheetConfig> = listOf(
        SheetConfig(
            sheet = "門診業務資料",
            table = "outpatient_service",
            columns = listOf(
                "year", "month", "ym", "branch", "dept",
                "lhy_first_visit", "first_visit_count", "return_visit_count",
                "er_visit", "er_day_shift", "er_evening_shift", "er_night_shift",
                "night_visit_count", "night_clinic_sessions", "day_clinic_sessions",
                "opd_visit_count", "total_clinic_sessions", "merged_branch",
                "dept_div", "dept_div_name", "medicine_type", "quarter",
                "merged_branch2", "branch_name", "merged_branch_name",
                "extra_25", "extra_26", "extra_27"
            )
        ),
        SheetConfig(
            sheet = "住院業務資料",
            table = "inpatient_service",
            columns = listOf(
                "year", "month", "ym", "branch", "dept",
                "discharge_count", "discharge_days",
                "admission_count", "admission_days",
                "merged_branch", "dept_div", "dept_div_name",
                "quarter", "branch_name", "merged_branch_name"
            )
        ),
        SheetConfig(
            sheet = "病床別業務資料",
            table = "bed_type_service",
            columns = listOf(
                "note", "year", "month", "branch", "days_in_month",
                "floor", "nursing_station", "dept",
                "major_category", "category", "registered_beds", "actual_open_beds",
                "admission_count", "discharge_count", "discharge_days", "admission_days",
                "registered_bed_days", "actual_bed_days",
                "registered_occupancy_rate", "actual_occupancy_rate",
                "merged_branch", "occupancy_nursing_station", "occupancy_bed_category",
                "branch_name", "merged_branch_name", "extra_25"
            )
        ),
        SheetConfig(
            sheet = "院外門診部服務量",
            table = "offsite_clinic_service",
            columns = listOf(
                "year", "month", "branch", "clinic_name",
                "medical_visit", "health_visit", "checkup_count", "total",
                "branch_name", "merged_branch_name",
                "medical_visit2", "health_visit2", "self_pay_checkup",
                "public_health_task", "other"
            )
        ),
        SheetConfig(
            sheet = "會計室報表資料",
            table = "accounting_report",
            columns = listOf(
                "year", "month", "ym", "branch",
                "opd_visit", "er_visit", "admission_count", "admission_days",
                "discharge_count", "discharge_days",
                "opd_checkup_count", "admission_checkup_count",
                "offsite_medical", "offsite_health",
                "dialysis_count", "km_control_visits", "km_control_items",
                "merged_branch", "quarter", "branch_name", "merged_branch_name"
            )
        ),
        SheetConfig(
            sheet = "其他營運管理指標資料",
            table = "ops_management_indicators",
            columns = listOf(
                "year", "month", "branch",
                "total_beds_registered", "acute_general_beds_registered",
                "acute_general_beds_actual", "icu_beds",
                "psychiatric_beds_acute", "psychiatric_beds_chronic",
                "psychiatric_daycare_beds", "death_count",
                "admission_checkup_count", "delivery_count",
                "surgery_opd_count", "surgery_admission_count",
                "self_pay_income_opd", "self_pay_income_admission",
                "total_income_opd", "total_income_admission",
                "er_first_visit_count", "attending_clinic_sessions",
                "attending_physician_count", "contract_clinic_sessions",
                "contract_physician_count", "special_contract_clinic_sessions",
                "special_contract_physician_count",
                "attending_patient_count", "attending_clinic_sessions2",
                "contract_patient_count", "contract_clinic_sessions2",
                "special_contract_patient_count", "special_contract_clinic_sessions2",
                "branch_name", "merged_branch_name"
            )
        ),
        SheetConfig(
            sheet = "醫師數",
            table = "physician_count",
            columns = listOf(
                "year", "month", "ym", "employee_id", "name",
                "branch", "role", "dept", "dept_org"
            )
        )
    )

    fun bySheet(name: String): SheetConfig? = ALL.firstOrNull { it.sheet == name }

    /** 檢查活頁簿是否包含全部目標工作表，回傳缺少者。 */
    fun allMissing(book: XlsxReader.Book): List<String> {
        val names = book.sheetNames().toSet()
        return ALL.map { it.sheet }.filter { it !in names }
    }
}
