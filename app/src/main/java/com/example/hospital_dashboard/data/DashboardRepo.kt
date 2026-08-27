package com.example.hospital_dashboard.data

/**
 * 儀表板查詢層：移植 Python 版 hospital_dashboard.py 的資料彙總邏輯。
 * 全部以 SQL 完成 GROUP BY 彙總，再於 Kotlin 側組裝圖表資料。
 */
class DashboardRepo(private val db: HospitalDb) {

    data class Filters(
        val years: List<String>,          // 民國年(字串，與 DB 一致)
        val months: List<String> = emptyList(),    // 空 = 全選
        val branches: List<String> = emptyList(),  // 空 = 全選
        val deptDivs: List<String> = emptyList(),  // 空 = 全選(僅套用門診/住院)
        val depts: List<String> = emptyList(),     // 空 = 全選
        val showYoy: Boolean = true
    ) {
        fun withYoy(on: Boolean) = copy(showYoy = on)
    }

    /** 與 Python make_where 相同：year/month/branch(/dept_div/dept)。yearOffset 用於去年同期。 */
    fun whereFor(f: Filters, withDept: Boolean, yearOffset: Int): Pair<String, Array<Any?>> {
        val parts = mutableListOf<String>()
        val params = mutableListOf<Any?>()
        val years = f.years.mapNotNull { it.toIntOrNull()?.plus(yearOffset)?.toString() }
        if (years.isEmpty()) return "" to emptyArray()
        parts.add("year IN (${years.joinToString(",") { "?" }})")
        params.addAll(years)
        if (f.months.isNotEmpty()) {
            parts.add("month IN (${f.months.joinToString(",") { "?" }})")
            params.addAll(f.months)
        }
        if (f.branches.isNotEmpty()) {
            parts.add("branch_name IN (${f.branches.joinToString(",") { "?" }})")
            params.addAll(f.branches)
        }
        if (withDept) {
            if (f.deptDivs.isNotEmpty()) {
                parts.add("dept_div IN (${f.deptDivs.joinToString(",") { "?" }})")
                params.addAll(f.deptDivs)
            }
            if (f.depts.isNotEmpty()) {
                parts.add("dept IN (${f.depts.joinToString(",") { "?" }})")
                params.addAll(f.depts)
            }
        }
        return parts.joinToString(" AND ") to params.toTypedArray()
    }

    private fun num(v: Any?): Double? = (v as? Number)?.toDouble()

    /**
     * 數值防護(對應 pandas to_numeric errors="coerce" 的 NaN→略過)：
     * 僅接受純數字字串(至多一個小數點，無英文字母)，避免 SQLite 寬鬆 CAST 把
     * 髒資料(如 'ddddd' 或串接數字)轉成 0 而污染 AVG。
     */
    private fun numGuard(col: String): String =
        "$col NOT GLOB '*[^0-9.eE+-]*' AND $col NOT GLOB '*.*.*'"

    /** 僅對數值列做 CAST 的 AVG(其餘為 NULL，AVG 自動略過)。 */
    private fun avgCast(col: String): String =
        "AVG(CASE WHEN ${numGuard(col)} THEN CAST($col AS REAL) END)"

    private fun ymSort(y: Any?, m: Any?): Int =
        (y?.toString()?.toIntOrNull() ?: 0) * 100 + (m?.toString()?.toIntOrNull() ?: 0)

    private fun ymLabel(y: Any?, m: Any?): String {
        val yi = y?.toString()?.toIntOrNull() ?: return "${y}-${m}"
        val mi = m?.toString()?.toIntOrNull() ?: return "${y}-${m}"
        return "${yi}年${mi.toString().padStart(2, '0')}月 (${yi + 1911}/${mi.toString().padStart(2, '0')})"
    }

    /**
     * 通用月趨勢折線圖。
     * @param sql 查詢(year, month, group?, value1, value2...) ORDER 不拘
     * @param groupColIdx -1 = 不分組
     * @param valueIdxs   取值的欄位索引(取第一個非 null)
     * @param valueScale  數值倍率(如佔床率 ×100)
     * @param keepPoint   點過濾(如 er>0)
     * @param yoySql/yoyParams 去年同期查詢(選用，虛線)
     */
    private fun buildLine(
        sql: String, params: Array<Any?>,
        groupColIdx: Int, valueIdxs: IntArray, valueScale: Double = 1.0,
        keepPoint: (List<Any?>) -> Boolean = { true },
        yoySql: String? = null, yoyParams: Array<Any?> = emptyArray()
    ): LineChartData {
        val rows = db.query(sql, params)
        val yoyRows = if (yoySql != null) db.query(yoySql, yoyParams) else emptyList()
        val kept = rows.filter(keepPoint)
        val keptYoy = yoyRows.filter(keepPoint)
        val xKeys = (kept + keptYoy)
            .map { ymSort(it[0], it[1]) to ymLabel(it[0], it[1]) }
            .distinct().sortedBy { it.first }
        val xLabels = xKeys.map { it.second }
        val series = mutableListOf<LineSeries>()

        fun mkSeries(name: String, src: List<List<Any?>>, dashed: Boolean): LineSeries {
            val vals = xKeys.map { k ->
                src.firstOrNull { ymSort(it[0], it[1]) == k.first }?.let { r ->
                    valueIdxs.toList().mapNotNull { i -> num(r[i]) }.firstOrNull()?.times(valueScale)
                }
            }
            return LineSeries(name, vals, dashed)
        }

        if (groupColIdx < 0) {
            if (kept.isNotEmpty()) series.add(mkSeries("", kept, false))
        } else {
            val groups = kept.groupBy { it[groupColIdx]?.toString() ?: "" }
            for ((g, rs) in groups) series.add(mkSeries(g, rs, false))
        }
        if (yoySql != null) {
            if (groupColIdx < 0) {
                if (keptYoy.isNotEmpty()) series.add(mkSeries("去年同期", keptYoy, true))
            } else {
                val groups = keptYoy.groupBy { it[groupColIdx]?.toString() ?: "" }
                for ((g, rs) in groups) series.add(mkSeries("$g(去年)", rs, true))
            }
        }
        return LineChartData(xLabels, series)
    }

    // ══════════ 最新月份 KPI(與圖表篩選分離) ═════════
    /** 資料中最新一個月(民國年, 月)。 */
    fun latestMonth(): Pair<String, String>? {
        val rows = db.query(
            "SELECT year, month FROM outpatient_service " +
                "ORDER BY CAST(year AS INTEGER) DESC, CAST(month AS INTEGER) DESC LIMIT 1"
        )
        val r = rows.firstOrNull() ?: return null
        return (r[0]?.toString() ?: "") to (r[1]?.toString() ?: "")
    }

    /** 單一月份 KPI(僅 year/month 條件，不套用任何篩選)。 */
    fun kpiForMonth(year: String, month: String): KpiSet {
        fun sumT(t: String, col: String): Double =
            db.queryDouble(
                "SELECT SUM(CAST($col AS REAL)) FROM $t WHERE year=? AND month=?",
                arrayOf(year, month)
            ) ?: 0.0
        val occ = (db.queryDouble(
            """SELECT ${avgCast("actual_occupancy_rate")} FROM bed_type_service
               WHERE (major_category IS NULL OR major_category != '其他')
                 AND actual_occupancy_rate IS NOT NULL
                 AND CAST(actual_occupancy_rate AS REAL) > 0 AND year=? AND month=?""",
            arrayOf(year, month)
        ) ?: 0.0) * 100.0
        return KpiSet(
            opd = sumT("outpatient_service", "opd_visit_count"),
            er = sumT("outpatient_service", "er_visit"),
            sessions = sumT("outpatient_service", "total_clinic_sessions"),
            ipdAdm = sumT("inpatient_service", "admission_count"),
            ipdDays = sumT("inpatient_service", "admission_days"),
            occ = occ,
            offsite = sumT("offsite_clinic_service", "total"),
            dialysis = sumT("accounting_report", "dialysis_count"),
            checkup = sumT("accounting_report", "opd_checkup_count") +
                sumT("accounting_report", "admission_checkup_count")
        )
    }

    /** 各院區單月統計(含去年同期)。 */
    class BranchMonthStat(
        val branch: String,
        var opd: Double, var er: Double,
        var ipdAdm: Double, var ipdDays: Double,
        var occ: Double?,
        var opdPrior: Double?, var erPrior: Double?,
        var ipdAdmPrior: Double?, var ipdDaysPrior: Double?,
        var occPrior: Double?
    )

    fun branchStatsForMonth(year: String, month: String): List<BranchMonthStat> {
        val cur = monthBranchMap(year, month)
        val priorY = (year.toIntOrNull()?.minus(1))?.toString()
        val prior = if (priorY != null) monthBranchMap(priorY, month) else emptyMap()
        val branches = (cur.keys + prior.keys).sorted()
        return branches.map { b ->
            val c = cur[b]
            val p = prior[b]
            BranchMonthStat(
                branch = b,
                opd = c?.opd ?: 0.0, er = c?.er ?: 0.0,
                ipdAdm = c?.ipdAdm ?: 0.0, ipdDays = c?.ipdDays ?: 0.0,
                occ = c?.occ,
                opdPrior = p?.opd, erPrior = p?.er,
                ipdAdmPrior = p?.ipdAdm, ipdDaysPrior = p?.ipdDays,
                occPrior = p?.occ
            )
        }
    }

    private fun monthBranchMap(year: String, month: String): Map<String, BranchMonthStat> {
        val opdRows = db.query(
            "SELECT branch, SUM(CAST(opd_visit_count AS REAL)), SUM(CAST(er_visit AS REAL)) " +
                "FROM outpatient_service WHERE year=? AND month=? GROUP BY branch_name",
            arrayOf(year, month)
        )
        val ipdRows = db.query(
            "SELECT branch_name, SUM(CAST(admission_count AS REAL)), SUM(CAST(admission_days AS REAL)) " +
                "FROM inpatient_service WHERE year=? AND month=? GROUP BY branch_name",
            arrayOf(year, month)
        )
        val bedRows = db.query(
            """SELECT branch_name, ${avgCast("actual_occupancy_rate")} FROM bed_type_service
               WHERE (major_category IS NULL OR major_category != '其他')
                 AND actual_occupancy_rate IS NOT NULL
                 AND CAST(actual_occupancy_rate AS REAL) > 0 AND year=? AND month=?
               GROUP BY branch_name""",
            arrayOf(year, month)
        )
        val result = LinkedHashMap<String, BranchMonthStat>()
        fun get(b: String) = result.getOrPut(b) {
            BranchMonthStat(b, 0.0, 0.0, 0.0, 0.0, null, null, null, null, null, null)
        }
        for (r in opdRows) {
            val s = get(r[0]?.toString() ?: "")
            s.opd = num(r[1]) ?: 0.0
            s.er = num(r[2]) ?: 0.0
        }
        for (r in ipdRows) {
            val s = get(r[0]?.toString() ?: "")
            s.ipdAdm = num(r[1]) ?: 0.0
            s.ipdDays = num(r[2]) ?: 0.0
        }
        for (r in bedRows) {
            val s = get(r[0]?.toString() ?: "")
            s.occ = num(r[1])?.times(100.0)
        }
        return result
    }

    // ══════════ KPI ══════════════════════════════════
    fun kpiSet(f: Filters, offset: Int): KpiSet {
        val (wD, pD) = whereFor(f, withDept = true, offset)
        val (wN, pN) = whereFor(f, withDept = false, offset)
        if (wD.isEmpty() || wN.isEmpty()) return KpiSet.EMPTY
        fun sumT(t: String, col: String, w: String, p: Array<Any?>): Double =
            db.queryDouble("SELECT SUM(CAST($col AS REAL)) FROM $t WHERE $w", p) ?: 0.0
        val occ = (db.queryDouble(
            """SELECT ${avgCast("actual_occupancy_rate")} FROM bed_type_service
               WHERE (major_category IS NULL OR major_category != '其他')
                 AND actual_occupancy_rate IS NOT NULL
                 AND CAST(actual_occupancy_rate AS REAL) > 0 AND $wN""", pN
        ) ?: 0.0) * 100.0
        return KpiSet(
            opd = sumT("outpatient_service", "opd_visit_count", wD, pD),
            er = sumT("outpatient_service", "er_visit", wD, pD),
            sessions = sumT("outpatient_service", "total_clinic_sessions", wD, pD),
            ipdAdm = sumT("inpatient_service", "admission_count", wD, pD),
            ipdDays = sumT("inpatient_service", "admission_days", wD, pD),
            occ = occ,
            offsite = sumT("offsite_clinic_service", "total", wN, pN),
            dialysis = sumT("accounting_report", "dialysis_count", wN, pN),
            checkup = sumT("accounting_report", "opd_checkup_count", wN, pN) +
                sumT("accounting_report", "admission_checkup_count", wN, pN)
        )
    }

    // ══════════ TAB1 門急診服務 ══════════════════════
    /** 門診人次月趨勢(依院區)，含去年同期虛線。 */
    fun opdMonthly(f: Filters): LineChartData {
        val (w, p) = whereFor(f, true, 0)
        val (wy, py) = if (f.showYoy) whereFor(f, true, -1) else "" to emptyArray()
        val sql = """SELECT year, month, branch_name,
            SUM(CAST(opd_visit_count AS REAL)), SUM(CAST(total_clinic_sessions AS REAL)), SUM(CAST(er_visit AS REAL))
            FROM outpatient_service WHERE $w GROUP BY year, month, branch_name"""
        val yoy = if (f.showYoy) """SELECT year, month, branch_name, SUM(CAST(opd_visit_count AS REAL))
            FROM outpatient_service WHERE $wy GROUP BY year, month, branch_name""" else null
        return buildLine(sql, p, groupColIdx = 2, valueIdxs = intArrayOf(3),
            yoySql = yoy, yoyParams = if (f.showYoy) py else emptyArray())
    }

    /** 急診人次月趨勢(依院區)，僅 er>0 的點。 */
    fun erMonthly(f: Filters): LineChartData {
        val (w, p) = whereFor(f, true, 0)
        val sql = """SELECT year, month, branch_name,
            SUM(CAST(opd_visit_count AS REAL)), SUM(CAST(total_clinic_sessions AS REAL)), SUM(CAST(er_visit AS REAL))
            FROM outpatient_service WHERE $w GROUP BY year, month, branch_name"""
        return buildLine(sql, p, groupColIdx = 2, valueIdxs = intArrayOf(5),
            keepPoint = { num(it[5])?.let { v -> v > 0 } ?: false })
    }

    /** 科別門診人次 vs 總診次 (Top n，橫條群組)。 */
    fun opdDeptTop(f: Filters, n: Int = 20): HBarData {
        val (w, p) = whereFor(f, true, 0)
        val rows = db.query(
            "SELECT dept, SUM(CAST(opd_visit_count AS REAL)), SUM(CAST(total_clinic_sessions AS REAL)) " +
                "FROM outpatient_service WHERE $w GROUP BY dept", p)
        val items = rows.mapNotNull { r ->
            val v = num(r[1]) ?: 0.0
            if (v > 0) Triple(r[0]?.toString() ?: "", v, num(r[2]) ?: 0.0) else null
        }.sortedByDescending { it.second }.take(n).sortedBy { it.second }
        return HBarData(
            items.map { HBarRow(it.first, listOf(BarSegment("門診人次", it.second), BarSegment("總診次", it.third))) },
            grouped = true
        )
    }

    /** 初診/複診/聯醫初診 圓餅圖。 */
    fun firstReturnPie(f: Filters): PieData {
        val (w, p) = whereFor(f, true, 0)
        val rows = db.query(
            "SELECT SUM(CAST(first_visit_count AS REAL)), SUM(CAST(return_visit_count AS REAL)), " +
                "SUM(CAST(lhy_first_visit AS REAL)) FROM outpatient_service WHERE $w", p)
        if (rows.isEmpty()) return PieData.EMPTY
        val r = rows[0]
        val fv = num(r[0]) ?: 0.0
        val rv = num(r[1]) ?: 0.0
        val lv = num(r[2]) ?: 0.0
        if (fv + rv + lv <= 0) return PieData.EMPTY
        return PieData(listOf(PieSlice("初診人次", fv), PieSlice("複診人次", rv), PieSlice("聯醫初診", lv)))
    }

    /** 各部別門診人次趨勢。 */
    fun deptDivMonthly(f: Filters): LineChartData {
        val (w, p) = whereFor(f, true, 0)
        val sql = """SELECT year, month, dept_div, SUM(CAST(opd_visit_count AS REAL))
            FROM outpatient_service WHERE $w GROUP BY year, month, dept_div"""
        return buildLine(sql, p, groupColIdx = 2, valueIdxs = intArrayOf(3))
    }

    /** 各院區門診人次(橫條，由小到大)。 */
    fun branchOpdBar(f: Filters): HBarData {
        val (w, p) = whereFor(f, true, 0)
        val rows = db.query(
            "SELECT branch_name, SUM(CAST(opd_visit_count AS REAL)) FROM outpatient_service " +
                "WHERE $w GROUP BY branch_name", p)
        val items = rows.mapNotNull { r ->
            num(r[1])?.let { v -> if (v > 0) r[0]?.toString()?.let { it to v } else null }
        }.sortedBy { it.second }
        return HBarData(items.map { HBarRow(it.first, listOf(BarSegment("門診人次", it.second))) })
    }

    // ══════════ TAB2 住院服務 ════════════════════════
    private fun ipdSql(f: Filters, offset: Int): Pair<String, Array<Any?>> {
        val (w, p) = whereFor(f, true, offset)
        return """SELECT year, month, branch_name,
            SUM(CAST(admission_count AS REAL)), SUM(CAST(discharge_count AS REAL)),
            SUM(CAST(admission_days AS REAL)), SUM(CAST(discharge_days AS REAL))
            FROM inpatient_service WHERE $w GROUP BY year, month, branch_name""" to p
    }

    fun ipdMonthly(f: Filters): LineChartData {
        val (sql, p) = ipdSql(f, 0)
        val (wsql, wp) = if (f.showYoy) ipdSql(f, -1) else null to emptyArray()
        return buildLine(sql, p, groupColIdx = 2, valueIdxs = intArrayOf(3),
            yoySql = if (f.showYoy) wsql else null,
            yoyParams = if (f.showYoy) wp else emptyArray())
    }

    fun dischargeMonthly(f: Filters): LineChartData {
        val (sql, p) = ipdSql(f, 0)
        return buildLine(sql, p, groupColIdx = 2, valueIdxs = intArrayOf(4))
    }

    /** 住院 vs 出院人日趨勢(全院不分院區)。 */
    fun ipdDaysMonthly(f: Filters): LineChartData {
        val (w, p) = whereFor(f, true, 0)
        val rows = db.query(
            "SELECT year, month, SUM(CAST(admission_days AS REAL)), SUM(CAST(discharge_days AS REAL)) " +
                "FROM inpatient_service WHERE $w GROUP BY year, month", p)
        val xKeys = rows.map { ymSort(it[0], it[1]) to ymLabel(it[0], it[1]) }
            .distinct().sortedBy { it.first }
        val xLabels = xKeys.map { it.second }
        fun ser(idx: Int, name: String) = LineSeries(name,
            xKeys.map { k -> rows.firstOrNull { ymSort(it[0], it[1]) == k.first }?.let { num(it[idx]) } })
        return LineChartData(xLabels, listOf(ser(2, "住院人日"), ser(3, "出院人日")))
    }

    fun branchIpdBar(f: Filters): HBarData {
        val (w, p) = whereFor(f, true, 0)
        val rows = db.query(
            "SELECT branch, SUM(CAST(admission_count AS REAL)) FROM inpatient_service " +
                "WHERE $w GROUP BY branch_name", p)
        val items = rows.mapNotNull { r ->
            num(r[1])?.let { v -> if (v > 0) r[0]?.toString()?.let { it to v } else null }
        }.sortedBy { it.second }
        return HBarData(items.map { HBarRow(it.first, listOf(BarSegment("住院人次", it.second))) })
    }

    /** 單一科別各院區統計(含去年同期；依目前篩選區間累計)。 */
    data class DeptBranchStat(
        val branch: String,
        val adm: Double, val days: Double, val los: Double,
        val admPrior: Double?, val daysPrior: Double?, val losPrior: Double?
    )

    fun deptBranchStats(f: Filters, dept: String): List<DeptBranchStat> {
        val (w, p) = whereFor(f, true, 0)
        if (w.isEmpty()) return emptyList()
        val (wp, pp) = if (f.showYoy) whereFor(f, true, -1) else "" to emptyArray()
        val cur = db.query(
            "SELECT branch_name, SUM(CAST(admission_count AS REAL)), SUM(CAST(admission_days AS REAL)) " +
                "FROM inpatient_service WHERE $w AND dept=? GROUP BY branch_name",
            arrayOf(*p, dept)
        )
        val prior = if (f.showYoy && wp.isNotEmpty()) {
            db.query(
                "SELECT branch_name, SUM(CAST(admission_count AS REAL)), SUM(CAST(admission_days AS REAL)) " +
                    "FROM inpatient_service WHERE $wp AND dept=? GROUP BY branch_name",
                arrayOf(*pp, dept)
            )
        } else emptyList()
        val priorMap = prior.associate { (it[0]?.toString() ?: "") to (num(it[1]) to num(it[2])) }
        val branches = (cur.map { it[0]?.toString() ?: "" } + priorMap.keys).sorted()
        return branches.map { b ->
            val r = cur.firstOrNull { it[0]?.toString() == b }
            val adm = num(r?.get(1)) ?: 0.0
            val days = num(r?.get(2)) ?: 0.0
            val pv = priorMap[b]
            val pAdm = pv?.first
            val pDays = pv?.second
            DeptBranchStat(
                branch = b,
                adm = adm, days = days,
                los = if (adm > 0) days / adm else 0.0,
                admPrior = pAdm, daysPrior = pDays,
                losPrior = if (pAdm != null && pAdm > 0 && pDays != null) pDays / pAdm else null
            )
        }
    }

    /** 科別門診各院區(含去年同期；依目前篩選區間累計)。 */
    class DeptOpdBranchStat(
        val branch: String,
        var opd: Double, var sessions: Double,
        var opdPrior: Double?, var sessionsPrior: Double?
    )

    fun deptOpdBranchStats(f: Filters, dept: String): List<DeptOpdBranchStat> {
        val (w, p) = whereFor(f, true, 0)
        if (w.isEmpty()) return emptyList()
        val (wp, pp) = if (f.showYoy) whereFor(f, true, -1) else "" to emptyArray()
        val cur = db.query(
            "SELECT branch_name, SUM(CAST(opd_visit_count AS REAL)), SUM(CAST(total_clinic_sessions AS REAL)) " +
                "FROM outpatient_service WHERE $w AND dept=? GROUP BY branch_name",
            arrayOf(*p, dept)
        )
        val prior = if (f.showYoy && wp.isNotEmpty()) {
            db.query(
                "SELECT branch_name, SUM(CAST(opd_visit_count AS REAL)), SUM(CAST(total_clinic_sessions AS REAL)) " +
                    "FROM outpatient_service WHERE $wp AND dept=? GROUP BY branch_name",
                arrayOf(*pp, dept)
            )
        } else emptyList()
        val priorMap = prior.associate { (it[0]?.toString() ?: "") to (num(it[1]) to num(it[2])) }
        val branches = (cur.map { it[0]?.toString() ?: "" } + priorMap.keys).sorted()
        return branches.map { b ->
            val r = cur.firstOrNull { it[0]?.toString() == b }
            val pv = priorMap[b]
            DeptOpdBranchStat(
                branch = b,
                opd = num(r?.get(1)) ?: 0.0,
                sessions = num(r?.get(2)) ?: 0.0,
                opdPrior = pv?.first,
                sessionsPrior = pv?.second
            )
        }
    }

    /** 院區門診各科別(含去年同期；依目前篩選區間累計，依門診人次降冪)。 */
    class BranchOpdDeptStat(val dept: String, val opd: Double, val opdPrior: Double?)

    fun branchOpdDeptStats(f: Filters, branch: String, limit: Int = 20): List<BranchOpdDeptStat> {
        val (w, p) = whereFor(f, true, 0)
        if (w.isEmpty()) return emptyList()
        val (wp, pp) = if (f.showYoy) whereFor(f, true, -1) else "" to emptyArray()
        val cur = db.query(
            "SELECT dept, SUM(CAST(opd_visit_count AS REAL)) " +
                "FROM outpatient_service WHERE $w AND branch_name=? GROUP BY dept",
            arrayOf(*p, branch)
        )
        val prior = if (f.showYoy && wp.isNotEmpty()) {
            db.query(
                "SELECT dept, SUM(CAST(opd_visit_count AS REAL)) " +
                    "FROM outpatient_service WHERE $wp AND branch_name=? GROUP BY dept",
                arrayOf(*pp, branch)
            )
        } else emptyList()
        val priorMap = prior.associate { (it[0]?.toString() ?: "") to (num(it[1]) ?: 0.0) }
        return (cur.mapNotNull { r ->
            val d = r[0]?.toString() ?: return@mapNotNull null
            BranchOpdDeptStat(d, num(r[1]) ?: 0.0, priorMap[d])
        } + priorMap.keys.filter { d -> cur.none { it[0]?.toString() == d } }
            .map { BranchOpdDeptStat(it, 0.0, priorMap[it]) })
            .sortedByDescending { it.opd }
            .take(limit)
    }

    /** 各院區初診/複診統計(依目前篩選區間累計)。 */
    class BranchFirstVisitStat(
        val branch: String,
        val firstVisit: Double, val returnVisit: Double, val lhyFirst: Double
    ) {
        val total: Double get() = firstVisit + returnVisit + lhyFirst
        val firstRate: Double get() = if (total > 0) firstVisit / total * 100.0 else 0.0
    }

    fun branchFirstVisitStats(f: Filters): List<BranchFirstVisitStat> {
        val (w, p) = whereFor(f, true, 0)
        if (w.isEmpty()) return emptyList()
        val rows = db.query(
            "SELECT branch_name, SUM(CAST(first_visit_count AS REAL)), " +
                "SUM(CAST(return_visit_count AS REAL)), SUM(CAST(lhy_first_visit AS REAL)) " +
                "FROM outpatient_service WHERE $w GROUP BY branch_name", p)
        return rows.map { r ->
            BranchFirstVisitStat(
                branch = r[0]?.toString() ?: "",
                firstVisit = num(r[1]) ?: 0.0,
                returnVisit = num(r[2]) ?: 0.0,
                lhyFirst = num(r[3]) ?: 0.0
            )
        }.sortedByDescending { it.firstVisit }
    }

    /** Top15 科別住院人次與平均住院日。 */
    fun ipdDeptStats(f: Filters, n: Int = 15): List<DeptIpdStat> {
        val (w, p) = whereFor(f, true, 0)
        val rows = db.query(
            "SELECT dept, SUM(CAST(admission_count AS REAL)), SUM(CAST(admission_days AS REAL)) " +
                "FROM inpatient_service WHERE $w GROUP BY dept", p)
        return rows.mapNotNull { r ->
            val adm = num(r[1]) ?: 0.0
            val days = num(r[2]) ?: 0.0
            if (adm > 0) DeptIpdStat(r[0]?.toString() ?: "", adm, if (adm > 0) days / adm else 0.0) else null
        }.sortedByDescending { it.adm }.take(n)
    }

    // ══════════ TAB3 病床利用 ════════════════════════
    fun bedCategories(f: Filters): List<String> {
        val (w, p) = whereFor(f, false, 0)
        if (w.isEmpty()) return emptyList()
        return db.query("SELECT DISTINCT category FROM bed_type_service WHERE $w AND category IS NOT NULL ORDER BY category", p)
            .map { it[0]?.toString() ?: "" }.filter { it.isNotEmpty() }
    }

    fun bedStations(f: Filters, cats: List<String>): List<String> {
        val (w, p) = whereFor(f, false, 0)
        val (cc, cp) = catCond(cats)
        val where = if (cc.isEmpty()) w else "$w AND $cc"
        return db.query(
            "SELECT DISTINCT nursing_station FROM bed_type_service WHERE $where AND nursing_station IS NOT NULL ORDER BY nursing_station",
            arrayOf(*p, *cp)
        ).map { it[0]?.toString() ?: "" }.filter { it.isNotEmpty() }
    }

    private fun catCond(cats: List<String>): Pair<String, Array<Any?>> {
        if (cats.isEmpty()) return "" to emptyArray()
        return "category IN (${cats.joinToString(",") { "?" }})" to cats.toTypedArray()
    }

    private fun bedMonthlySql(f: Filters, offset: Int, cats: List<String>): Pair<String, Array<Any?>> {
        val (w, p) = whereFor(f, false, offset)
        val (cc, cp) = catCond(cats)
        val where = if (cc.isEmpty()) w else "$w AND $cc"
        val params = arrayOf(*p, *cp)
        return """SELECT year, month, category,
            ${avgCast("actual_occupancy_rate")}, SUM(CAST(actual_open_beds AS REAL)),
            ${avgCast("registered_occupancy_rate")}
            FROM bed_type_service WHERE $where GROUP BY year, month, category""" to params
    }

    /** 實際佔床率月趨勢(依病床類別，%)，含去年同期虛線。 */
    fun bedOccMonthly(f: Filters, cats: List<String>): LineChartData {
        val (sql, p) = bedMonthlySql(f, 0, cats)
        val (ysql, yp) = if (f.showYoy) bedMonthlySql(f, -1, cats) else "" to emptyArray()
        return buildLine(sql, p, groupColIdx = 2, valueIdxs = intArrayOf(3), valueScale = 100.0,
            yoySql = if (f.showYoy) ysql else null,
            yoyParams = if (f.showYoy) yp else emptyArray())
    }

    /** 實際開床數月趨勢(依病床類別)。 */
    fun bedOpenMonthly(f: Filters, cats: List<String>): LineChartData {
        val (sql, p) = bedMonthlySql(f, 0, cats)
        return buildLine(sql, p, groupColIdx = 2, valueIdxs = intArrayOf(4))
    }

    /** 各院區平均實際佔床率(橫條，目標 85%)。 */
    fun bedBranchOcc(f: Filters, cats: List<String>): HBarData {
        val (w, p) = whereFor(f, false, 0)
        val (cc, cp) = catCond(cats)
        val where = if (cc.isEmpty()) w else "$w AND $cc"
        val rows = db.query(
            "SELECT branch_name, ${avgCast("actual_occupancy_rate")}, SUM(CAST(actual_open_beds AS REAL)) " +
                "FROM bed_type_service WHERE $where GROUP BY branch_name", arrayOf(*p, *cp))
        val items = rows.mapNotNull { r ->
            val occ = num(r[1])?.times(100.0) ?: 0.0
            val open = num(r[2]) ?: 0.0
            if (open > 0) r[0]?.toString()?.let { it to occ } else null
        }.sortedBy { it.second }
        return HBarData(items.map { HBarRow(it.first, listOf(BarSegment("平均佔床率", it.second))) },
            targetLine = 85.0)
    }

    /** 登記 vs 實際開床數(依病床類別，Top12 橫條群組)。 */
    fun bedCatRegVsOpen(f: Filters, cats: List<String>): HBarData {
        val (w, p) = whereFor(f, false, 0)
        val (cc, cp) = catCond(cats)
        val where = if (cc.isEmpty()) w else "$w AND $cc"
        val rows = db.query(
            "SELECT category, SUM(CAST(registered_beds AS REAL)), SUM(CAST(actual_open_beds AS REAL)) " +
                "FROM bed_type_service WHERE $where GROUP BY category", arrayOf(*p, *cp))
        val items = rows.mapNotNull { r ->
            val reg = num(r[1]) ?: 0.0
            if (reg > 0) Triple(r[0]?.toString() ?: "", reg, num(r[2]) ?: 0.0) else null
        }.sortedBy { it.second }.takeLast(12)
        return HBarData(
            items.map { HBarRow(it.first, listOf(BarSegment("登記床數", it.second), BarSegment("實際開床數", it.third))) },
            grouped = true
        )
    }

    private fun bedNsSql(f: Filters, cats: List<String>, ns: List<String>): Pair<String, Array<Any?>> {
        val (w, p) = whereFor(f, false, 0)
        val (cc, cp) = catCond(cats)
        var where = if (cc.isEmpty()) w else "$w AND $cc"
        val params = mutableListOf<Any?>()
        params.addAll(p)
        params.addAll(cp)
        if (ns.isNotEmpty()) {
            where += " AND nursing_station IN (${ns.joinToString(",") { "?" }})"
            params.addAll(ns)
        }
        return where to params.toTypedArray()
    }

    /** 各護理站平均實際佔床率 (Top25，目標 85%)。 */
    fun bedStationOcc(f: Filters, cats: List<String>, ns: List<String>): HBarData {
        val (where, params) = bedNsSql(f, cats, ns)
        val rows = db.query(
            "SELECT nursing_station, ${avgCast("actual_occupancy_rate")}, SUM(CAST(actual_open_beds AS REAL)) " +
                "FROM bed_type_service WHERE $where GROUP BY nursing_station", params)
        val items = rows.mapNotNull { r ->
            val occ = num(r[1])?.times(100.0) ?: 0.0
            val open = num(r[2]) ?: 0.0
            if (open > 0) r[0]?.toString()?.let { it to occ } else null
        }.sortedBy { it.second }.takeLast(25)
        return HBarData(items.map { HBarRow(it.first, listOf(BarSegment("平均佔床率", it.second))) },
            targetLine = 85.0)
    }

    /** 各護理站登記 vs 實開床數 (Top20 群組)。 */
    fun bedStationBeds(f: Filters, cats: List<String>, ns: List<String>): HBarData {
        val (where, params) = bedNsSql(f, cats, ns)
        val rows = db.query(
            "SELECT nursing_station, SUM(CAST(registered_beds AS REAL)), SUM(CAST(actual_open_beds AS REAL)) " +
                "FROM bed_type_service WHERE $where GROUP BY nursing_station", params)
        val items = rows.mapNotNull { r ->
            val reg = num(r[1]) ?: 0.0
            if (reg > 0) Triple(r[0]?.toString() ?: "", reg, num(r[2]) ?: 0.0) else null
        }.sortedBy { it.second }.takeLast(20)
        return HBarData(
            items.map { HBarRow(it.first, listOf(BarSegment("登記床數", it.second), BarSegment("實開床數", it.third))) },
            grouped = true
        )
    }

    /** 熱力圖：index × category 平均實際佔床率(%)。 */
    fun bedPivot(f: Filters, cats: List<String>, byStation: Boolean, ns: List<String>): TableData {
        val (w, p) = whereFor(f, false, 0)
        val (cc, cp) = catCond(cats)
        var where = if (cc.isEmpty()) w else "$w AND $cc"
        val params = mutableListOf<Any?>()
        params.addAll(p)
        params.addAll(cp)
        if (byStation && ns.isNotEmpty()) {
            where += " AND nursing_station IN (${ns.joinToString(",") { "?" }})"
            params.addAll(ns)
        }
        val idxCol = if (byStation) "nursing_station" else "branch_name"
        val rows = db.query(
            "SELECT $idxCol, category, ${avgCast("actual_occupancy_rate")} " +
                "FROM bed_type_service WHERE $where GROUP BY $idxCol, category", params.toTypedArray())

        val catsSorted = rows.map { it[1]?.toString() ?: "" }.distinct().sorted()
        val byIdx = rows.groupBy { it[0]?.toString() ?: "" }
        // 依平均值排序(護理站取前 30，依平均由高到低)
        val idxOrder = byIdx.entries
            .map { (k, v) -> k to (v.mapNotNull { num(it[2]) }.averageOrNull() ?: 0.0) }
            .sortedByDescending { it.second }
        val finalIdx = (if (byStation) idxOrder.take(30) else idxOrder.sortedBy { it.first }).map { it.first }

        val cells = finalIdx.map { idx ->
            val map = byIdx[idx]!!.associate { (it[1]?.toString() ?: "") to (num(it[2])?.times(100.0) ?: 0.0) }
            catsSorted.map { c ->
                val v = map[c] ?: 0.0
                TableCell(if (v > 0) String.format("%.1f", v) else "-", occupancyColor(v))
            }
        }
        return TableData(catsSorted, cells)
    }

    /** 去年同期佔床率對照表。 */
    fun bedYoyCompare(f: Filters, cats: List<String>): List<BedYoyRow> {
        val (w, p) = whereFor(f, false, 0)
        val (wy, py) = if (f.showYoy) whereFor(f, false, -1) else "" to emptyArray()
        if (!f.showYoy) return emptyList()
        val (cc, cp) = catCond(cats)
        val wc = if (cc.isEmpty()) w else "$w AND $cc"
        val wyc = if (cc.isEmpty()) wy else "$wy AND $cc"
        val cur = db.query(
            "SELECT category, ${avgCast("actual_occupancy_rate")} FROM bed_type_service " +
                "WHERE $wc GROUP BY category", arrayOf(*p, *cp))
        val prev = db.query(
            "SELECT category, ${avgCast("actual_occupancy_rate")} FROM bed_type_service " +
                "WHERE $wyc GROUP BY category", arrayOf(*py, *cp))
        val prevMap = prev.associate { (it[0]?.toString() ?: "") to (num(it[1])?.times(100.0) ?: 0.0) }
        return cur.mapNotNull { r ->
            val cat = r[0]?.toString() ?: return@mapNotNull null
            val c = num(r[1])?.times(100.0) ?: return@mapNotNull null
            val pp = prevMap[cat] ?: return@mapNotNull null
            BedYoyRow(cat, c, pp)
        }.sortedByDescending { it.curr }
    }

    private fun List<Double>.averageOrNull(): Double? = if (isEmpty()) null else sum() / size

    // ══════════ TAB4 其他服務 ════════════════════════
    /** 院外門診部服務量趨勢。 */
    fun offsiteMonthly(f: Filters): LineChartData {
        val (w, p) = whereFor(f, false, 0)
        val rows = db.query(
            "SELECT year, month, SUM(CAST(total AS REAL)), SUM(CAST(medical_visit AS REAL)), " +
                "SUM(CAST(health_visit AS REAL)) FROM offsite_clinic_service WHERE $w GROUP BY year, month", p)
        val xKeys = rows.map { ymSort(it[0], it[1]) to ymLabel(it[0], it[1]) }.distinct().sortedBy { it.first }
        return LineChartData(xKeys.map { it.second },
            listOf(LineSeries("院外門診總人次",
                xKeys.map { k -> rows.firstOrNull { ymSort(it[0], it[1]) == k.first }?.let { num(it[2]) } })))
    }

    /** 各院外門診部服務量(醫療/保健 群組橫條)。 */
    fun offsiteClinicBar(f: Filters): HBarData {
        val (w, p) = whereFor(f, false, 0)
        val rows = db.query(
            "SELECT clinic_name, SUM(CAST(medical_visit AS REAL)), SUM(CAST(health_visit AS REAL)), " +
                "SUM(CAST(total AS REAL)) FROM offsite_clinic_service WHERE $w GROUP BY clinic_name", p)
        val items = rows.mapNotNull { r ->
            val t = num(r[3]) ?: 0.0
            if (t > 0) Triple(r[0]?.toString() ?: "", num(r[1]) ?: 0.0, num(r[2]) ?: 0.0) else null
        }.sortedBy { it.third }
        return HBarData(
            items.map { HBarRow(it.first, listOf(BarSegment("醫療門診", it.second), BarSegment("保健門診", it.third))) },
            grouped = true
        )
    }

    /** 洗腎/門診體檢/住院體檢趨勢。 */
    fun accMonthly(f: Filters): LineChartData {
        val (w, p) = whereFor(f, false, 0)
        val rows = db.query(
            "SELECT year, month, SUM(CAST(dialysis_count AS REAL)), SUM(CAST(opd_checkup_count AS REAL)), " +
                "SUM(CAST(admission_checkup_count AS REAL)) FROM accounting_report WHERE $w GROUP BY year, month", p)
        val xKeys = rows.map { ymSort(it[0], it[1]) to ymLabel(it[0], it[1]) }.distinct().sortedBy { it.first }
        val xLabels = xKeys.map { it.second }
        fun ser(idx: Int, name: String) = LineSeries(name,
            xKeys.map { k -> rows.firstOrNull { ymSort(it[0], it[1]) == k.first }?.let { num(it[idx]) } })
        return LineChartData(xLabels, listOf(ser(2, "洗腎人次"), ser(3, "門診體檢"), ser(4, "住院體檢")))
    }

    /** 手術/生產人次趨勢(堆疊直條)。 */
    fun opsMonthly(f: Filters): VBarData {
        val (w, p) = whereFor(f, false, 0)
        val rows = db.query(
            "SELECT year, month, SUM(CAST(surgery_opd_count AS REAL)), SUM(CAST(surgery_admission_count AS REAL)), " +
                "SUM(CAST(delivery_count AS REAL)) FROM ops_management_indicators WHERE $w GROUP BY year, month", p)
        val xKeys = rows.map { ymSort(it[0], it[1]) to ymLabel(it[0], it[1]) }.distinct().sortedBy { it.first }
        return VBarData(xKeys.map { k ->
            val r = rows.firstOrNull { ymSort(it[0], it[1]) == k.first }
            VBarGroup(k.second, listOf(
                BarSegment("門診手術", r?.let { num(it[2]) } ?: 0.0),
                BarSegment("住院手術", r?.let { num(it[3]) } ?: 0.0),
                BarSegment("生產人次", r?.let { num(it[4]) } ?: 0.0)
            ))
        }, stacked = true)
    }

    /** 收入趨勢：總收入(群組直條) + 自費(折線)。 */
    fun incomeMonthly(f: Filters): Pair<VBarData, LineChartData> {
        val (w, p) = whereFor(f, false, 0)
        val rows = db.query(
            "SELECT year, month, SUM(CAST(total_income_opd AS REAL)), SUM(CAST(total_income_admission AS REAL)), " +
                "SUM(CAST(self_pay_income_opd AS REAL)), SUM(CAST(self_pay_income_admission AS REAL)) " +
                "FROM ops_management_indicators WHERE $w GROUP BY year, month", p)
        val xKeys = rows.map { ymSort(it[0], it[1]) to ymLabel(it[0], it[1]) }.distinct().sortedBy { it.first }
        val xLabels = xKeys.map { it.second }
        val bar = VBarData(xKeys.map { k ->
            val r = rows.firstOrNull { ymSort(it[0], it[1]) == k.first }
            VBarGroup(k.second, listOf(
                BarSegment("門診總收入", r?.let { num(it[2]) } ?: 0.0),
                BarSegment("住院總收入", r?.let { num(it[3]) } ?: 0.0)
            ))
        })
        val line = LineChartData(xLabels, listOf(
            LineSeries("門診自費", xKeys.map { k -> rows.firstOrNull { ymSort(it[0], it[1]) == k.first }?.let { num(it[4]) } }),
            LineSeries("住院自費", xKeys.map { k -> rows.firstOrNull { ymSort(it[0], it[1]) == k.first }?.let { num(it[5]) } })
        ))
        return bar to line
    }

    /** 各院區總收入(門診+住院 堆疊橫條)。 */
    fun branchIncome(f: Filters): HBarData {
        val (w, p) = whereFor(f, false, 0)
        val rows = db.query(
            "SELECT branch_name, SUM(CAST(total_income_opd AS REAL)), SUM(CAST(total_income_admission AS REAL)) " +
                "FROM ops_management_indicators WHERE $w GROUP BY branch_name", p)
        val items = rows.mapNotNull { r ->
            val o = num(r[1]) ?: 0.0
            val a = num(r[2]) ?: 0.0
            if (o + a > 0) r[0]?.toString()?.let { it to (o to a) } else null
        }.sortedBy { it.second.first + it.second.second }
        return HBarData(items.map { HBarRow(it.first, listOf(BarSegment("門診收入", it.second.first), BarSegment("住院收入", it.second.second))) })
    }

    // ══════════ TAB5 各院區病床占床率明細 ═══════════
    fun bedDetail(f: Filters): List<BedDetailRow> {
        val (w, p) = whereFor(f, false, 0)
        if (w.isEmpty()) return emptyList()
        val rows = db.query(
            """SELECT COALESCE(branch_name,'未分類'), COALESCE(major_category,'未分類'),
               COALESCE(category,'未分類'), COALESCE(nursing_station,'未分類'),
               SUM(CAST(registered_beds AS REAL)), SUM(CAST(actual_open_beds AS REAL)),
               SUM(CAST(admission_days AS REAL)),
               ${avgCast("registered_occupancy_rate")}, ${avgCast("actual_occupancy_rate")}
               FROM bed_type_service WHERE $w
               GROUP BY branch_name, major_category, category, nursing_station""", p)
        val yoy = bedDetailYoy(f)
        return rows.map { r ->
            val key = listOf(r[0], r[1], r[2], r[3])
            val y = yoy[key]
            BedDetailRow(
                branch = r[0] as String, major = r[1] as String,
                category = r[2] as String, station = r[3] as String,
                regBeds = (num(r[4]) ?: 0.0).toLong(),
                openBeds = (num(r[5]) ?: 0.0).toLong(),
                days = (num(r[6]) ?: 0.0).toLong(),
                regOcc = (num(r[7]) ?: 0.0) * 100.0,
                actOcc = (num(r[8]) ?: 0.0) * 100.0,
                yoyRegOcc = y?.first, yoyActOcc = y?.second
            )
        }
    }

    private fun bedDetailYoy(f: Filters): Map<List<Any?>, Pair<Double?, Double?>> {
        val (w, p) = whereFor(f, false, -1)
        if (w.isEmpty()) return emptyMap()
        val rows = db.query(
            """SELECT COALESCE(branch_name,'未分類'), COALESCE(major_category,'未分類'),
               COALESCE(category,'未分類'), COALESCE(nursing_station,'未分類'),
               ${avgCast("registered_occupancy_rate")}, ${avgCast("actual_occupancy_rate")}
               FROM bed_type_service WHERE $w
               GROUP BY branch_name, major_category, category, nursing_station""", p)
        return rows.associate { r ->
            listOf(r[0], r[1], r[2], r[3]) to
                (num(r[4])?.times(100.0) to num(r[5])?.times(100.0))
        }
    }

    // ══════════ 錨點月份(近三個月/去年同期基準) ═══════════
    /** 篩選年度範圍內的最新一個月(民國年, 月)；年度為空則取全部資料最新月。 */
    fun anchorYm(f: Filters): Pair<Int, Int>? {
        val sql = if (f.years.isEmpty()) {
            "SELECT year, month FROM outpatient_service ORDER BY CAST(year AS INTEGER) DESC, CAST(month AS INTEGER) DESC LIMIT 1"
        } else {
            "SELECT year, month FROM outpatient_service WHERE year IN (${f.years.joinToString(",") { "?" }})" +
                " ORDER BY CAST(year AS INTEGER) DESC, CAST(month AS INTEGER) DESC LIMIT 1"
        }
        val r = db.query(sql, if (f.years.isEmpty()) emptyArray() else f.years.toTypedArray()).firstOrNull()
            ?: return null
        val y = r[0]?.toString()?.toIntOrNull() ?: return null
        val m = r[1]?.toString()?.toIntOrNull() ?: return null
        return y to m
    }

    /** 由錨點往回推 k 個月(跨年正確處理)。 */
    private fun monthBack(y: Int, m: Int, k: Int): Pair<Int, Int> {
        val ym = y * 12 + (m - 1) - k
        return (ym / 12) to (ym % 12 + 1)
    }

    /** 錨點起最近 3 個月(由舊到新)。 */
    fun recent3Yms(f: Filters): List<Pair<Int, Int>> {
        val a = anchorYm(f) ?: return emptyList()
        return listOf(monthBack(a.first, a.second, 2), monthBack(a.first, a.second, 1), a)
    }

    /** (year, month) IN ((?,?),(?,?)) 條件與參數。 */
    private fun ymInCond(yms: List<Pair<Int, Int>>): Pair<String, Array<Any?>> {
        if (yms.isEmpty()) return "" to emptyArray()
        val ph = yms.joinToString(",") { "(?,?)" }
        val params = mutableListOf<Any?>()
        yms.forEach { (y, m) -> params.add(y.toString()); params.add(m.toString()) }
        return "(year, month) IN ($ph)" to params.toTypedArray()
    }

    // ══════════ TAB6 醫師服務量 ═══════════════════════
    /** 醫師服務量表(physician_service)專用篩選條件。capAnchor=true 時限制在錨點月以內(排除部分月份)。 */
    private fun physWhere(f: Filters, offset: Int, capAnchor: Boolean = true): Pair<String, Array<Any?>> {
        val parts = mutableListOf<String>()
        val params = mutableListOf<Any?>()
        val years = f.years.mapNotNull { it.toIntOrNull()?.plus(offset)?.toString() }
        if (years.isEmpty()) return "" to emptyArray()
        parts.add("year IN (${years.joinToString(",") { "?" }})")
        params.addAll(years)
        if (f.months.isNotEmpty()) {
            parts.add("month IN (${f.months.joinToString(",") { "?" }})")
            params.addAll(f.months)
        }
        if (f.branches.isNotEmpty()) {
            parts.add("branch_name IN (${f.branches.joinToString(",") { "?" }})")
            params.addAll(f.branches)
        }
        if (f.deptDivs.isNotEmpty()) {
            parts.add("dept_div IN (${f.deptDivs.joinToString(",") { "?" }})")
            params.addAll(f.deptDivs)
        }
        if (f.depts.isNotEmpty()) {
            parts.add("dept IN (${f.depts.joinToString(",") { "?" }})")
            params.addAll(f.depts)
        }
        if (capAnchor && offset == 0) {
            anchorYm(f)?.let { (ay, am) ->
                parts.add("(CAST(year AS INTEGER) < ? OR (CAST(year AS INTEGER) = ? AND CAST(month AS INTEGER) <= ?))")
                params.add(ay.toString()); params.add(ay.toString()); params.add(am.toString())
            }
        }
        return parts.joinToString(" AND ") to params.toTypedArray()
    }

    /** 科別彙總資料(門診/急診/住院人次/住院人日)。 */
    class PhysDeptVol(
        val dept: String,
        val opd: Double, val er: Double, val adm: Double, val days: Double
    )

    fun physDeptVolumes(f: Filters): List<PhysDeptVol> {
        val (w, p) = physWhere(f, 0)
        if (w.isEmpty()) return emptyList()
        val rows = db.query(
            """SELECT dept,
               SUM(CAST(opd_visit_count AS REAL)), SUM(CAST(er_visit AS REAL)),
               SUM(CAST(admission_count AS REAL)), SUM(CAST(admission_days AS REAL))
               FROM physician_service WHERE $w GROUP BY dept""", p)
        return rows.mapNotNull { r ->
            val dept = r[0]?.toString()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val v = PhysDeptVol(dept, num(r[1]) ?: 0.0, num(r[2]) ?: 0.0, num(r[3]) ?: 0.0, num(r[4]) ?: 0.0)
            if (v.opd + v.er + v.adm + v.days > 0) v else null
        }
    }

    /** 單一科別醫師服務量(依門診人次降冪)。 */
    class PhysDoctorStat(
        val doctorName: String,
        val sessions: Double, val opd: Double, val er: Double, val adm: Double, val days: Double
    )

    fun physDoctorsForDept(f: Filters, dept: String, limit: Int = 60): List<PhysDoctorStat> {
        val (w, p) = physWhere(f, 0)
        if (w.isEmpty()) return emptyList()
        val rows = db.query(
            """SELECT doctor_id, doctor_name,
               SUM(CAST(sessions AS REAL)), SUM(CAST(opd_visit_count AS REAL)), SUM(CAST(er_visit AS REAL)),
               SUM(CAST(admission_count AS REAL)), SUM(CAST(admission_days AS REAL))
               FROM physician_service WHERE $w AND dept=? GROUP BY doctor_id, doctor_name""",
            arrayOf(*p, dept))
        return rows.mapNotNull { r ->
            val id = r[0]?.toString() ?: ""
            val name = r[1]?.toString()?.takeIf { it.isNotEmpty() } ?: id
            val s = PhysDoctorStat(name, num(r[2]) ?: 0.0, num(r[3]) ?: 0.0, num(r[4]) ?: 0.0,
                num(r[5]) ?: 0.0, num(r[6]) ?: 0.0)
            if (s.opd + s.er + s.adm + s.days + s.sessions > 0) s else null
        }.sortedByDescending { it.opd }.take(limit)
    }

    /** 單一月份的科別指標。 */
    class PhysTrend(val ym: Int, val opd: Double, val er: Double, val adm: Double, val days: Double)

    /** 單一科別最近三個月各指標(由舊到新)。 */
    fun physDeptTrend3(f: Filters, dept: String): List<PhysTrend> {
        val yms = recent3Yms(f)
        if (yms.isEmpty()) return emptyList()
        val (yc, yp) = ymInCond(yms)
        val parts = mutableListOf("$yc AND dept=?")
        val params = mutableListOf<Any?>(); params.addAll(yp); params.add(dept)
        if (f.branches.isNotEmpty()) {
            parts.add("branch_name IN (${f.branches.joinToString(",") { "?" }})")
            params.addAll(f.branches)
        }
        val w = parts.joinToString(" AND ")
        val rows = db.query(
            """SELECT year, month,
               SUM(CAST(opd_visit_count AS REAL)), SUM(CAST(er_visit AS REAL)),
               SUM(CAST(admission_count AS REAL)), SUM(CAST(admission_days AS REAL))
               FROM physician_service WHERE $w GROUP BY year, month""", params.toTypedArray())
        val map = rows.associate { r ->
            val y = r[0]?.toString()?.toIntOrNull() ?: 0
            val m = r[1]?.toString()?.toIntOrNull() ?: 0
            (y * 100 + m) to PhysTrend(y * 100 + m, num(r[2]) ?: 0.0, num(r[3]) ?: 0.0,
                num(r[4]) ?: 0.0, num(r[5]) ?: 0.0)
        }
        return yms.map { (y, m) -> map[y * 100 + m] ?: PhysTrend(y * 100 + m, 0.0, 0.0, 0.0, 0.0) }
    }

    /** 單一科別去年同期(錨點月 vs 去年同月)。 */
    fun physDeptYoy(f: Filters, dept: String): Pair<PhysTrend?, PhysTrend?> {
        val a = anchorYm(f) ?: return null to null
        val prior = monthBack(a.first, a.second, 12)
        fun monthStat(y: Int, m: Int): PhysTrend? {
            val parts = mutableListOf("year=? AND month=? AND dept=?")
            val params = mutableListOf<Any?>(y.toString(), m.toString(), dept)
            if (f.branches.isNotEmpty()) {
                parts.add("branch_name IN (${f.branches.joinToString(",") { "?" }})")
                params.addAll(f.branches)
            }
            val w = parts.joinToString(" AND ")
            val r = db.query(
                """SELECT SUM(CAST(opd_visit_count AS REAL)), SUM(CAST(er_visit AS REAL)),
                   SUM(CAST(admission_count AS REAL)), SUM(CAST(admission_days AS REAL))
                   FROM physician_service WHERE $w""", params.toTypedArray()).firstOrNull() ?: return null
            return PhysTrend(y * 100 + m, num(r[0]) ?: 0.0, num(r[1]) ?: 0.0, num(r[2]) ?: 0.0, num(r[3]) ?: 0.0)
        }
        return monthStat(a.first, a.second) to monthStat(prior.first, prior.second)
    }

    // ══════════ TAB7 醫師收入統計 ═══════════════════════
    /** 全院收入月趨勢(門診/住院 × 健保/自費 堆疊直條)。 */
    fun physIncomeMonthly(f: Filters): VBarData {
        val (w, p) = physWhere(f, 0)
        if (w.isEmpty()) return VBarData.EMPTY
        val rows = db.query(
            """SELECT year, month,
               SUM(CAST(opd_nhi_income AS REAL)), SUM(CAST(opd_selfpay_income AS REAL)),
               SUM(CAST(ipd_nhi_income AS REAL)), SUM(CAST(ipd_selfpay_income AS REAL))
               FROM physician_service WHERE $w GROUP BY year, month""", p)
        val xKeys = rows.map { ymSort(it[0], it[1]) to ymLabel(it[0], it[1]) }.distinct().sortedBy { it.first }
        return VBarData(xKeys.map { k ->
            val r = rows.firstOrNull { ymSort(it[0], it[1]) == k.first }
            VBarGroup(k.second, listOf(
                BarSegment("門診健保", r?.let { num(it[2]) } ?: 0.0),
                BarSegment("門診自費", r?.let { num(it[3]) } ?: 0.0),
                BarSegment("住院健保", r?.let { num(it[4]) } ?: 0.0),
                BarSegment("住院自費", r?.let { num(it[5]) } ?: 0.0)
            ))
        }, stacked = true)
    }

    /** 當月收入結構(錨點月)：門診/住院 × 健保/自費 圓餅。 */
    fun physIncomePie(f: Filters): PieData {
        val a = anchorYm(f) ?: return PieData.EMPTY
        val parts = mutableListOf("year=? AND month=?")
        val params = mutableListOf<Any?>(a.first.toString(), a.second.toString())
        if (f.branches.isNotEmpty()) {
            parts.add("branch_name IN (${f.branches.joinToString(",") { "?" }})")
            params.addAll(f.branches)
        }
        if (f.deptDivs.isNotEmpty()) {
            parts.add("dept_div IN (${f.deptDivs.joinToString(",") { "?" }})")
            params.addAll(f.deptDivs)
        }
        if (f.depts.isNotEmpty()) {
            parts.add("dept IN (${f.depts.joinToString(",") { "?" }})")
            params.addAll(f.depts)
        }
        val w = parts.joinToString(" AND ")
        val r = db.query(
            """SELECT SUM(CAST(opd_nhi_income AS REAL)), SUM(CAST(opd_selfpay_income AS REAL)),
               SUM(CAST(ipd_nhi_income AS REAL)), SUM(CAST(ipd_selfpay_income AS REAL))
               FROM physician_service WHERE $w""", params.toTypedArray()).firstOrNull() ?: return PieData.EMPTY
        val slices = listOf(
            PieSlice("門診健保收入", num(r[0]) ?: 0.0),
            PieSlice("門診自費收入", num(r[1]) ?: 0.0),
            PieSlice("住院健保收入", num(r[2]) ?: 0.0),
            PieSlice("住院自費收入", num(r[3]) ?: 0.0)
        )
        if (slices.sumOf { it.value } <= 0) return PieData.EMPTY
        return PieData(slices)
    }

    /** 單一月份某院區收入(含去年同月)。 */
    class BranchIncomeStat(
        val branch: String,
        val cur: Double, val prior: Double?, val deltaPct: Double?,
        val trend: List<Double?>, val trendDeltaPct: Double? // 近三個月(由舊到新)與最後一個月增減
    )

    /** 點擊月份長條 → 各院區收入明細(近三個月趨勢 + 去年同期成長率)。 */
    fun physBranchIncome(f: Filters, ym: Int): List<BranchIncomeStat> {
        val y = ym / 100; val m = ym % 100
        val yms = listOf(monthBack(y, m, 2), monthBack(y, m, 1), y to m)
        val (yc, yp) = ymInCond(yms)
        val parts = mutableListOf(yc)
        val params = mutableListOf<Any?>(); params.addAll(yp)
        if (f.branches.isNotEmpty()) {
            parts.add("branch_name IN (${f.branches.joinToString(",") { "?" }})")
            params.addAll(f.branches)
        }
        if (f.deptDivs.isNotEmpty()) {
            parts.add("dept_div IN (${f.deptDivs.joinToString(",") { "?" }})")
            params.addAll(f.deptDivs)
        }
        if (f.depts.isNotEmpty()) {
            parts.add("dept IN (${f.depts.joinToString(",") { "?" }})")
            params.addAll(f.depts)
        }
        val w = parts.joinToString(" AND ")
        val incSql = "SUM(CAST(opd_nhi_income AS REAL)) + SUM(CAST(opd_selfpay_income AS REAL)) + " +
            "SUM(CAST(ipd_nhi_income AS REAL)) + SUM(CAST(ipd_selfpay_income AS REAL))"
        // 近三個月(排除院外門診部：其收入皆為 0)
        val rows = db.query(
            "SELECT branch_name, year, month, $incSql FROM physician_service " +
                "WHERE $w AND branch_name NOT LIKE '%門診部' GROUP BY branch_name, year, month",
            params.toTypedArray())
        val trendMap = HashMap<String, Array<Double?>>()
        for (r in rows) {
            val br = r[0]?.toString() ?: continue
            val ry = r[1]?.toString()?.toIntOrNull() ?: 0
            val rm = r[2]?.toString()?.toIntOrNull() ?: 0
            val idx = yms.indexOfFirst { it.first == ry && it.second == rm }
            if (idx < 0) continue
            val arr = trendMap.getOrPut(br) { arrayOfNulls<Double>(3) }
            arr[idx] = num(r[3]) ?: 0.0
        }
        // 去年同期(去年同月)
        val py = y - 1
        val priorRows = db.query(
            "SELECT branch_name, $incSql FROM physician_service " +
                "WHERE year=? AND month=? AND branch_name NOT LIKE '%門診部' " +
                (if (f.branches.isNotEmpty()) "AND branch_name IN (${f.branches.joinToString(",") { "?" }})" else "") +
                (if (f.deptDivs.isNotEmpty()) "AND dept_div IN (${f.deptDivs.joinToString(",") { "?" }})" else "") +
                (if (f.depts.isNotEmpty()) "AND dept IN (${f.depts.joinToString(",") { "?" }})" else "") +
                " GROUP BY branch_name",
            buildList {
                add(py.toString()); add(m.toString())
                if (f.branches.isNotEmpty()) addAll(f.branches)
                if (f.deptDivs.isNotEmpty()) addAll(f.deptDivs)
                if (f.depts.isNotEmpty()) addAll(f.depts)
            }.toTypedArray())
        val priorMap = priorRows.associate { (it[0]?.toString() ?: "") to (num(it[1]) ?: 0.0) }
        val branches = (trendMap.keys + priorMap.keys).sorted()
        return branches.map { br ->
            val trend = trendMap[br]?.toList() ?: listOf(null, null, null)
            val cur = trend[2] ?: 0.0
            val prior = priorMap[br]
            val delta = if (prior != null && prior != 0.0 && cur != 0.0) (cur - prior) / prior * 100.0 else null
            val prev = trend[1]
            val trendDelta = if (prev != null && prev != 0.0 && cur != 0.0) (cur - prev) / prev * 100.0 else null
            BranchIncomeStat(br, cur, prior, delta, trend, trendDelta)
        }
    }

    /** 收入圓餅單一區塊明細(近三個月趨勢 + 去年同期比較)。 */
    class IncomeSliceStat(
        val label: String,
        val value: Double,           // 錨點月金額
        val recent3: List<Double?>,  // 近三個月(由舊到新，含錨點月)
        val prior: Double?,          // 去年同期同月
        val deltaPct: Double?        // 去年同期增減(%)
    )

    /** 當月收入結構圓餅各區塊明細：近三個月變化趨勢 + 去年同期增減。 */
    fun physIncomeSliceDetail(f: Filters): List<IncomeSliceStat> {
        val a = anchorYm(f) ?: return emptyList()
        val yms = recent3Yms(f)
        val (py, pm) = monthBack(a.first, a.second, 12)
        val cols = listOf(
            "門診健保收入" to "opd_nhi_income",
            "門診自費收入" to "opd_selfpay_income",
            "住院健保收入" to "ipd_nhi_income",
            "住院自費收入" to "ipd_selfpay_income"
        )
        // 與 physIncomePie 相同之篩選範圍(院區/部別/科別)
        val parts = mutableListOf<String>()
        val params = mutableListOf<Any?>()
        if (f.branches.isNotEmpty()) {
            parts.add("branch_name IN (${f.branches.joinToString(",") { "?" }})")
            params.addAll(f.branches)
        }
        if (f.deptDivs.isNotEmpty()) {
            parts.add("dept_div IN (${f.deptDivs.joinToString(",") { "?" }})")
            params.addAll(f.deptDivs)
        }
        if (f.depts.isNotEmpty()) {
            parts.add("dept IN (${f.depts.joinToString(",") { "?" }})")
            params.addAll(f.depts)
        }
        val extra = parts.joinToString(" AND ")
        val extraSql = if (extra.isEmpty()) "" else " AND $extra"
        val sel = cols.joinToString(", ") { (_, col) -> "SUM(CAST($col AS REAL))" }

        // 近三個月(由舊到新)
        val (ymc, ymp) = ymInCond(yms)
        val rows = db.query(
            "SELECT year, month, $sel FROM physician_service WHERE $ymc$extraSql GROUP BY year, month",
            arrayOf(*ymp, *params.toTypedArray()))
        val ymMap = HashMap<Int, Array<Double>>()
        for (r in rows) {
            val y = r[0]?.toString()?.toIntOrNull() ?: 0
            val m = r[1]?.toString()?.toIntOrNull() ?: 0
            ymMap[y * 100 + m] = Array(cols.size) { i -> num(r[2 + i]) ?: 0.0 }
        }
        // 去年同期同月
        val priorRow = db.query(
            "SELECT $sel FROM physician_service WHERE year=? AND month=?$extraSql",
            arrayOf(py.toString(), pm.toString(), *params.toTypedArray())).firstOrNull()
        val priorArr = priorRow?.let { r -> Array(cols.size) { i -> num(r[i]) ?: 0.0 } }

        return cols.mapIndexed { i, (label, _) ->
            val trend = yms.map { (y, m) -> ymMap[y * 100 + m]?.get(i) }
            val cur = trend.lastOrNull() ?: 0.0
            val prior = priorArr?.get(i)
            val delta = if (prior != null && prior != 0.0 && cur != 0.0) (cur - prior) / prior * 100.0 else null
            IncomeSliceStat(label, cur, trend, prior, delta)
        }
    }

    // ══════════ 近三個月趨勢(點擊細項用) ═════════════
    /** 將 (group, year, month, value) 列轉為 group → 依 yms 對齊的 3 個月數值。 */
    private fun buildRecentMap(rows: List<List<Any?>>, yms: List<Pair<Int, Int>>, valIdx: Int): Map<String, List<Double?>> {
        val map = HashMap<String, Array<Double?>>()
        for (r in rows) {
            val g = r[0]?.toString() ?: continue
            val ry = r[1]?.toString()?.toIntOrNull() ?: 0
            val rm = r[2]?.toString()?.toIntOrNull() ?: 0
            val idx = yms.indexOfFirst { it.first == ry && it.second == rm }
            if (idx < 0) continue
            val arr = map.getOrPut(g) { arrayOfNulls<Double>(yms.size) }
            arr[idx] = num(r[valIdx])
        }
        return map.mapValues { it.value.toList() }
    }

    /** 科別門診 各院區近三個月門診人次趨勢。 */
    fun deptOpdBranchRecent3(f: Filters, dept: String): Map<String, List<Double?>> {
        val yms = recent3Yms(f)
        if (yms.isEmpty()) return emptyMap()
        val (yc, yp) = ymInCond(yms)
        val params = mutableListOf<Any?>(); params.addAll(yp); params.add(dept)
        val rows = db.query(
            "SELECT branch_name, year, month, SUM(CAST(opd_visit_count AS REAL)) " +
                "FROM outpatient_service WHERE $yc AND dept=? GROUP BY branch_name, year, month",
            params.toTypedArray())
        return buildRecentMap(rows, yms, 3)
    }

    /** 院區門診 各科別近三個月門診人次趨勢。 */
    fun branchOpdDeptRecent3(f: Filters, branch: String): Map<String, List<Double?>> {
        val yms = recent3Yms(f)
        if (yms.isEmpty()) return emptyMap()
        val (yc, yp) = ymInCond(yms)
        val params = mutableListOf<Any?>(); params.addAll(yp); params.add(branch)
        val rows = db.query(
            "SELECT dept, year, month, SUM(CAST(opd_visit_count AS REAL)) " +
                "FROM outpatient_service WHERE $yc AND branch_name=? GROUP BY dept, year, month",
            params.toTypedArray())
        return buildRecentMap(rows, yms, 3)
    }

    /** 科別住院 各院區近三個月住院人次趨勢。 */
    fun deptBranchRecent3(f: Filters, dept: String): Map<String, List<Double?>> {
        val yms = recent3Yms(f)
        if (yms.isEmpty()) return emptyMap()
        val (yc, yp) = ymInCond(yms)
        val params = mutableListOf<Any?>(); params.addAll(yp); params.add(dept)
        val rows = db.query(
            "SELECT branch_name, year, month, SUM(CAST(admission_count AS REAL)) " +
                "FROM inpatient_service WHERE $yc AND dept=? GROUP BY branch_name, year, month",
            params.toTypedArray())
        return buildRecentMap(rows, yms, 3)
    }

    // ══════════ 色階 ════════════════════════════════
    /** 佔床率 0-100 → 紅-黃-綠 色階(RdYlGn)。 */
    fun occupancyColor(pct: Double): Long {
        val t = pct.coerceIn(0.0, 100.0) / 100.0
        // RdYlGn 節點: 0=(215,48,39) 50=(254,240,144) 100=(26,152,80)
        val r: Double
        val g: Double
        val b: Double
        if (t < 0.5) {
            val s = t / 0.5
            r = 215 + (254 - 215) * s
            g = 48 + (240 - 48) * s
            b = 39 + (144 - 39) * s
        } else {
            val s = (t - 0.5) / 0.5
            r = 254 + (26 - 254) * s
            g = 240 + (152 - 240) * s
            b = 144 + (80 - 144) * s
        }
        val ri = (r.toLong() and 0xFF)
        val gi = (g.toLong() and 0xFF)
        val bi = (b.toLong() and 0xFF)
        return 0xFF000000L or (ri shl 16) or (gi shl 8) or bi
    }
}
