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
        val showYoy: Boolean = true,
        val showHospitalTotal: Boolean = false
    ) {
        fun withYoy(on: Boolean) = copy(showYoy = on)
        fun withHospitalTotal(on: Boolean) = copy(showHospitalTotal = on)
    }

    /** 與 Python make_where 相同：year/month/branch(/dept_div/dept)。yearOffset 用於去年同期。 */
    fun whereFor(f: Filters, withDept: Boolean, yearOffset: Int): Pair<String, Array<Any?>> {
        val parts = mutableListOf<String>()
        val params = mutableListOf<Any?>()
        val years = f.years.mapNotNull { it.toIntOrNull()?.plus(yearOffset)?.toString() }
        // 空年度防呆：回傳 1=1 而非空字串，避免「WHERE  GROUP BY」語法錯誤崩潰
        if (years.isEmpty()) return "1=1" to emptyArray()
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
        // 圖表 X 軸僅顯示民國年月（西元年月易致標籤重疊，不再顯示）
        return "${yi}年${mi.toString().padStart(2, '0')}月"
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
        // X 軸刻度以本期 (kept) 為準，若無本期則取 keptYoy，避免軸首出現非選定年月的刻度
        val xKeys = (if (kept.isNotEmpty()) kept else keptYoy)
            .map { ymSort(it[0], it[1]) to ymLabel(it[0], it[1]) }
            .distinct().sortedBy { it.first }
        val xLabels = xKeys.map { it.second }
        val series = mutableListOf<LineSeries>()

        fun mkSeries(name: String, src: List<List<Any?>>, dashed: Boolean): LineSeries {
            val vals = xKeys.map { k ->
                val targetYm = if (dashed) {
                    val y = k.first / 100
                    val m = k.first % 100
                    (y - 1) * 100 + m
                } else {
                    k.first
                }
                src.firstOrNull { ymSort(it[0], it[1]) == targetYm }?.let { r ->
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
                val groupNames = (kept.mapNotNull { it[groupColIdx]?.toString() } +
                    keptYoy.mapNotNull { it[groupColIdx]?.toString() }).distinct()
                val yoyGroups = keptYoy.groupBy { it[groupColIdx]?.toString() ?: "" }
                for (g in groupNames) {
                    val rs = yoyGroups[g] ?: emptyList()
                    val s = mkSeries("$g(去年)", rs, true)
                    if (s.values.any { it != null && it > 0 }) {
                        series.add(s)
                    }
                }
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
               WHERE $BED_OCC_EXCLUDE_MAJOR_SQL
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
               WHERE $BED_OCC_EXCLUDE_MAJOR_SQL
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
               WHERE $BED_OCC_EXCLUDE_MAJOR_SQL
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
        val branchCol = if (f.showHospitalTotal) "'全院' AS branch_name" else "branch_name"
        val groupCols = if (f.showHospitalTotal) "year, month" else "year, month, branch_name"
        val sql = """SELECT year, month, $branchCol,
            SUM(CAST(opd_visit_count AS REAL)), SUM(CAST(total_clinic_sessions AS REAL)), SUM(CAST(er_visit AS REAL))
            FROM outpatient_service WHERE $w GROUP BY $groupCols"""
        val yoy = if (f.showYoy) """SELECT year, month, $branchCol, SUM(CAST(opd_visit_count AS REAL))
            FROM outpatient_service WHERE $wy GROUP BY $groupCols""" else null
        return buildLine(sql, p, groupColIdx = 2, valueIdxs = intArrayOf(3),
            yoySql = yoy, yoyParams = if (f.showYoy) py else emptyArray())
    }

    /** 急診人次月趨勢(依院區)，含去年同期虛線。 */
    fun erMonthly(f: Filters): LineChartData {
        val (w, p) = whereFor(f, true, 0)
        val (wy, py) = if (f.showYoy) whereFor(f, true, -1) else "" to emptyArray()
        val branchCol = if (f.showHospitalTotal) "'全院' AS branch_name" else "branch_name"
        val groupCols = if (f.showHospitalTotal) "year, month" else "year, month, branch_name"
        val sql = """SELECT year, month, $branchCol,
            SUM(CAST(opd_visit_count AS REAL)), SUM(CAST(total_clinic_sessions AS REAL)), SUM(CAST(er_visit AS REAL))
            FROM outpatient_service WHERE $w GROUP BY $groupCols"""
        val yoySql = if (f.showYoy) """SELECT year, month, $branchCol,
            SUM(CAST(opd_visit_count AS REAL)), SUM(CAST(total_clinic_sessions AS REAL)), SUM(CAST(er_visit AS REAL))
            FROM outpatient_service WHERE $wy GROUP BY $groupCols""" else null
        return buildLine(sql, p, groupColIdx = 2, valueIdxs = intArrayOf(5),
            keepPoint = { num(it[5])?.let { v -> v > 0 } ?: false },
            yoySql = yoySql, yoyParams = py)
    }

    /** 科別門診人次 (Top n)：單一橫條由大到小 + 平均每診人次(小數 1 位)。 */
    fun opdDeptTop(f: Filters, n: Int = 20): HBarData {
        val (w, p) = whereFor(f, true, 0)
        val rows = db.query(
            "SELECT dept, SUM(CAST(opd_visit_count AS REAL)), SUM(CAST(total_clinic_sessions AS REAL)) " +
                "FROM outpatient_service WHERE $w GROUP BY dept", p)
        val items = rows.mapNotNull { r ->
            val v = num(r[1]) ?: 0.0
            val sess = num(r[2]) ?: 0.0
            if (v > 0) Triple(r[0]?.toString() ?: "", v, sess) else null
        }.sortedByDescending { it.second }.take(n)
        return HBarData(
            items.map { (name, v, sess) ->
                val avg = if (sess > 0) v / sess else 0.0
                HBarRow(name, listOf(BarSegment("門診人次", v)),
                    trailing = String.format("平均每診 %.1f 人次", avg))
            }
        )
    }

    /** 初診人次月趨勢(依院區)，含去年同期虛線。 */
    fun firstVisitMonthly(f: Filters): LineChartData {
        val (w, p) = whereFor(f, true, 0)
        val (wy, py) = if (f.showYoy) whereFor(f, true, -1) else "" to emptyArray()
        val branchCol = if (f.showHospitalTotal) "'全院' AS branch_name" else "branch_name"
        val groupCols = if (f.showHospitalTotal) "year, month" else "year, month, branch_name"
        val sql = """SELECT year, month, $branchCol, SUM(CAST(first_visit_count AS REAL))
            FROM outpatient_service WHERE $w GROUP BY $groupCols"""
        val yoySql = if (f.showYoy) """SELECT year, month, $branchCol, SUM(CAST(first_visit_count AS REAL))
            FROM outpatient_service WHERE $wy GROUP BY $groupCols""" else null
        return buildLine(sql, p, groupColIdx = 2, valueIdxs = intArrayOf(3),
            yoySql = yoySql, yoyParams = py)
    }

    /** 各院區初診人次(橫條，降冪；含去年同期半透明比對)。 */
    fun branchFirstVisitBar(f: Filters): HBarData =
        buildYoyHBar(f, "outpatient_service", "branch_name", "first_visit_count", "初診人次")

    /** 初診/複診 圓餅圖（保留相容）。 */
    fun firstReturnPie(f: Filters): PieData {
        val (w, p) = whereFor(f, true, 0)
        val rows = db.query(
            "SELECT SUM(CAST(first_visit_count AS REAL)), SUM(CAST(return_visit_count AS REAL)), " +
                "SUM(CAST(lhy_first_visit AS REAL)) FROM outpatient_service WHERE $w", p)
        if (rows.isEmpty()) return PieData.EMPTY
        val r = rows[0]
        val fv = num(r[0]) ?: 0.0
        val rv = num(r[1]) ?: 0.0
        if (fv + rv <= 0) return PieData.EMPTY
        return PieData(listOf(PieSlice("初診人次", fv), PieSlice("複診人次", rv)))
    }

    /** 各部別門診人次趨勢，含去年同期虛線。 */
    fun deptDivMonthly(f: Filters): LineChartData {
        val (w, p) = whereFor(f, true, 0)
        val (wy, py) = if (f.showYoy) whereFor(f, true, -1) else "" to emptyArray()
        val sql = """SELECT year, month, dept_div, SUM(CAST(opd_visit_count AS REAL))
            FROM outpatient_service WHERE $w AND dept_div IS NOT NULL AND dept_div != '' GROUP BY year, month, dept_div"""
        val yoySql = if (f.showYoy) """SELECT year, month, dept_div, SUM(CAST(opd_visit_count AS REAL))
            FROM outpatient_service WHERE $wy AND dept_div IS NOT NULL AND dept_div != '' GROUP BY year, month, dept_div""" else null
        return buildLine(sql, p, groupColIdx = 2, valueIdxs = intArrayOf(3),
            yoySql = yoySql, yoyParams = py)
    }

    /** 各部別門診人次(橫條，降冪；含去年同期半透明比對)。 */
    fun deptDivOpdBar(f: Filters): HBarData =
        buildYoyHBar(f, "outpatient_service", "dept_div", "opd_visit_count", "門診人次")

    data class DivDeptOpdStat(
        val dept: String,
        val opd: Double,
        val sessions: Double,
        val avgPerSession: Double,
        val opdPrior: Double?,
        val deltaPct: Double?
    )

    /** 取得指定部別下各科別門診人次明細 (含去年同期比較)。 */
    fun divOpdDeptStats(f: Filters, div: String): List<DivDeptOpdStat> {
        val (w, p) = whereFor(f, true, 0)
        val (wy, py) = if (f.showYoy) whereFor(f, true, -1) else "" to emptyArray()
        val curRows = db.query(
            "SELECT dept, SUM(CAST(opd_visit_count AS REAL)), SUM(CAST(total_clinic_sessions AS REAL)) " +
                "FROM outpatient_service WHERE $w AND dept_div=? GROUP BY dept",
            arrayOf(*p, div)
        )
        val curMap = curRows.associate {
            (it[0]?.toString() ?: "") to ((num(it[1]) ?: 0.0) to (num(it[2]) ?: 0.0))
        }
        val priorMap = if (f.showYoy && wy.isNotEmpty()) {
            val pRows = db.query(
                "SELECT dept, SUM(CAST(opd_visit_count AS REAL)) " +
                    "FROM outpatient_service WHERE $wy AND dept_div=? GROUP BY dept",
                arrayOf(*py, div)
            )
            pRows.associate { (it[0]?.toString() ?: "") to (num(it[1]) ?: 0.0) }
        } else emptyMap()

        val allDepts = (curMap.keys + priorMap.keys).filter { it.isNotEmpty() }.distinct()
        return allDepts.map { dept ->
            val (curOpd, sess) = curMap[dept] ?: (0.0 to 0.0)
            val prevOpd = priorMap[dept]
            val avg = if (sess > 0) curOpd / sess else 0.0
            val deltaPct = if (prevOpd != null && prevOpd > 0) (curOpd - prevOpd) / prevOpd * 100.0 else null
            DivDeptOpdStat(dept, curOpd, sess, avg, prevOpd, deltaPct)
        }.sortedByDescending { it.opd }
    }

    /** 通用重疊橫條圖查詢 (依維度欄位加總數值欄位，含去年同期半透明比對)。 */
    private fun buildYoyHBar(
        f: Filters,
        table: String,
        dimCol: String,
        valCol: String,
        curLabel: String,
        unit: String = "",
        customFormatter: ((Double) -> String)? = null
    ): HBarData {
        val isBranchTotal = f.showHospitalTotal && dimCol == "branch_name"
        val selDim = if (isBranchTotal) "'全院' AS branch_name" else dimCol
        val grpDim = if (isBranchTotal) "" else "GROUP BY $dimCol"
        val (w, p) = whereFor(f, true, 0)
        val whereClause = if (!isBranchTotal) "$w AND $dimCol IS NOT NULL AND $dimCol != ''" else w
        val rows = db.query(
            "SELECT $selDim, SUM(CAST($valCol AS REAL)) FROM $table " +
                "WHERE $whereClause $grpDim", p)
        val currentMap = rows.mapNotNull { r ->
            val d = r.getOrNull(0)?.toString() ?: return@mapNotNull null
            val v = num(r.getOrNull(1)) ?: 0.0
            d to v
        }.toMap()

        val yoyMap = if (f.showYoy) {
            val (wy, py) = whereFor(f, true, -1)
            val whereY = if (!isBranchTotal) "$wy AND $dimCol IS NOT NULL AND $dimCol != ''" else wy
            val yrows = db.query(
                "SELECT $selDim, SUM(CAST($valCol AS REAL)) FROM $table " +
                    "WHERE $whereY $grpDim", py)
            yrows.mapNotNull { r ->
                val d = r.getOrNull(0)?.toString() ?: return@mapNotNull null
                val v = num(r.getOrNull(1)) ?: 0.0
                d to v
            }.toMap()
        } else emptyMap()

        val allDims = (currentMap.keys + yoyMap.keys).filter { it.isNotEmpty() }.distinct()
        val items = allDims.map { d ->
            val cur = currentMap[d] ?: 0.0
            val prev = yoyMap[d] ?: 0.0
            Triple(d, cur, prev)
        }.filter { it.second > 0 || it.third > 0 }
            .sortedByDescending { it.second }

        if (f.showYoy && yoyMap.isNotEmpty()) {
            val hbarRows = items.map { (d, cur, prev) ->
                val deltaPct = if (prev > 0) (cur - prev) / prev * 100.0 else null
                val prevStr = customFormatter?.invoke(prev) ?: Fmt.compact(prev)
                val trailing = if (deltaPct != null) {
                    val sign = if (deltaPct >= 0) "+" else ""
                    "去年 $prevStr$unit ($sign${String.format("%.1f%%", deltaPct)})"
                } else if (prev > 0) {
                    "去年 $prevStr$unit"
                } else null

                HBarRow(
                    name = d,
                    segments = listOf(
                        BarSegment("去年同期", prev),
                        BarSegment(curLabel, cur)
                    ),
                    trailing = trailing
                )
            }
            return HBarData(hbarRows, overlap = true)
        } else {
            return HBarData(items.map { HBarRow(it.first, listOf(BarSegment(curLabel, it.second))) })
        }
    }

    /** 各院區門診人次(橫條，降冪；含去年同期半透明比對)。 */
    fun branchOpdBar(f: Filters): HBarData =
        buildYoyHBar(f, "outpatient_service", "branch_name", "opd_visit_count", "門診人次")

    /** 各院區急診人次(橫條，降冪；含去年同期半透明比對)。 */
    fun branchErBar(f: Filters): HBarData =
        buildYoyHBar(f, "outpatient_service", "branch_name", "er_visit", "急診人次")

    // ══════════ TAB2 住院服務 ════════════════════════
    private fun ipdSql(f: Filters, offset: Int): Pair<String, Array<Any?>> {
        val (w, p) = whereFor(f, true, offset)
        val branchCol = if (f.showHospitalTotal) "'全院' AS branch_name" else "branch_name"
        val groupCols = if (f.showHospitalTotal) "year, month" else "year, month, branch_name"
        return """SELECT year, month, $branchCol,
            SUM(CAST(admission_count AS REAL)), SUM(CAST(discharge_count AS REAL)),
            SUM(CAST(admission_days AS REAL)), SUM(CAST(discharge_days AS REAL))
            FROM inpatient_service WHERE $w GROUP BY $groupCols""" to p
    }

    /** 住院人次月趨勢(依院區)，含去年同期虛線。 */
    fun ipdMonthly(f: Filters): LineChartData {
        val (sql, p) = ipdSql(f, 0)
        val (wsql, wp) = if (f.showYoy) ipdSql(f, -1) else null to emptyArray()
        return buildLine(sql, p, groupColIdx = 2, valueIdxs = intArrayOf(3),
            yoySql = if (f.showYoy) wsql else null,
            yoyParams = if (f.showYoy) wp else emptyArray())
    }

    /** 各院區住院人次(橫條，降冪；含去年同期半透明比對)。 */
    fun branchIpdBar(f: Filters): HBarData =
        buildYoyHBar(f, "inpatient_service", "branch_name", "admission_count", "住院人次")

    /** 出院人次月趨勢(依院區)，含去年同期虛線。 */
    fun dischargeMonthly(f: Filters): LineChartData {
        val (sql, p) = ipdSql(f, 0)
        val (wsql, wp) = if (f.showYoy) ipdSql(f, -1) else null to emptyArray()
        return buildLine(sql, p, groupColIdx = 2, valueIdxs = intArrayOf(4),
            yoySql = if (f.showYoy) wsql else null,
            yoyParams = if (f.showYoy) wp else emptyArray())
    }

    /** 各院區出院人次(橫條，降冪；含去年同期半透明比對)。 */
    fun branchDischargeBar(f: Filters): HBarData =
        buildYoyHBar(f, "inpatient_service", "branch_name", "discharge_count", "出院人次")

    /** 住院人日月趨勢(依院區)，含去年同期虛線。 */
    fun ipdAdmissionDaysMonthly(f: Filters): LineChartData {
        val (sql, p) = ipdSql(f, 0)
        val (wsql, wp) = if (f.showYoy) ipdSql(f, -1) else null to emptyArray()
        return buildLine(sql, p, groupColIdx = 2, valueIdxs = intArrayOf(5),
            yoySql = if (f.showYoy) wsql else null,
            yoyParams = if (f.showYoy) wp else emptyArray())
    }

    /** 各院區住院人日(橫條，降冪；含去年同期半透明比對)。 */
    fun branchIpdDaysBar(f: Filters): HBarData =
        buildYoyHBar(f, "inpatient_service", "branch_name", "admission_days", "住院人日")

    /** 出院人日月趨勢(依院區)，含去年同期虛線。 */
    fun ipdDischargeDaysMonthly(f: Filters): LineChartData {
        val (sql, p) = ipdSql(f, 0)
        val (wsql, wp) = if (f.showYoy) ipdSql(f, -1) else null to emptyArray()
        return buildLine(sql, p, groupColIdx = 2, valueIdxs = intArrayOf(6),
            yoySql = if (f.showYoy) wsql else null,
            yoyParams = if (f.showYoy) wp else emptyArray())
    }

    /** 各院區出院人日(橫條，降冪；含去年同期半透明比對)。 */
    fun branchDischargeDaysBar(f: Filters): HBarData =
        buildYoyHBar(f, "inpatient_service", "branch_name", "discharge_days", "出院人日")

    /** 住院人日月趨勢(依部別)，含去年同期虛線。 */
    fun ipdDeptDivDaysMonthly(f: Filters): LineChartData {
        val (w, p) = whereFor(f, true, 0)
        val (wy, py) = if (f.showYoy) whereFor(f, true, -1) else "" to emptyArray()
        val sql = """SELECT year, month, dept_div, SUM(CAST(admission_days AS REAL))
            FROM inpatient_service WHERE $w AND dept_div IS NOT NULL AND dept_div != '' GROUP BY year, month, dept_div"""
        val yoySql = if (f.showYoy) """SELECT year, month, dept_div, SUM(CAST(admission_days AS REAL))
            FROM inpatient_service WHERE $wy AND dept_div IS NOT NULL AND dept_div != '' GROUP BY year, month, dept_div""" else null
        return buildLine(sql, p, groupColIdx = 2, valueIdxs = intArrayOf(3),
            yoySql = yoySql, yoyParams = py)
    }

    /** 各部別住院人日(橫條，降冪；含去年同期半透明比對)。 */
    fun ipdDeptDivDaysBar(f: Filters): HBarData =
        buildYoyHBar(f, "inpatient_service", "dept_div", "admission_days", "住院人日")

    data class DivDeptIpdStat(
        val dept: String,
        val days: Double,
        val adm: Double,
        val los: Double,
        val daysPrior: Double?,
        val deltaPct: Double?
    )

    /** 取得指定部別下各科別住院明細 (人日、人次、平均住院日、去年同期比較)。 */
    fun ipdDivDeptDaysStats(f: Filters, div: String): List<DivDeptIpdStat> {
        val (w, p) = whereFor(f, true, 0)
        val (wy, py) = if (f.showYoy) whereFor(f, true, -1) else "" to emptyArray()
        val curRows = db.query(
            "SELECT dept, SUM(CAST(admission_days AS REAL)), SUM(CAST(admission_count AS REAL)) " +
                "FROM inpatient_service WHERE $w AND dept_div=? GROUP BY dept",
            arrayOf(*p, div)
        )
        val curMap = curRows.associate {
            (it[0]?.toString() ?: "") to ((num(it[1]) ?: 0.0) to (num(it[2]) ?: 0.0))
        }
        val priorMap = if (f.showYoy && wy.isNotEmpty()) {
            val pRows = db.query(
                "SELECT dept, SUM(CAST(admission_days AS REAL)) " +
                    "FROM inpatient_service WHERE $wy AND dept_div=? GROUP BY dept",
                arrayOf(*py, div)
            )
            pRows.associate { (it[0]?.toString() ?: "") to (num(it[1]) ?: 0.0) }
        } else emptyMap()

        val allDepts = (curMap.keys + priorMap.keys).filter { it.isNotEmpty() }.distinct()
        return allDepts.map { dept ->
            val (days, adm) = curMap[dept] ?: (0.0 to 0.0)
            val prevDays = priorMap[dept]
            val los = if (adm > 0) days / adm else 0.0
            val deltaPct = if (prevDays != null && prevDays > 0) (days - prevDays) / prevDays * 100.0 else null
            DivDeptIpdStat(dept, days, adm, los, prevDays, deltaPct)
        }.sortedByDescending { it.days }
    }

    /** 平均住院日月趨勢(依院區)，含去年同期虛線。 */
    fun alosMonthly(f: Filters): LineChartData {
        val (w, p) = whereFor(f, true, 0)
        val (wy, py) = if (f.showYoy) whereFor(f, true, -1) else "" to emptyArray()
        val branchCol = if (f.showHospitalTotal) "'全院' AS branch_name" else "branch_name"
        val groupCols = if (f.showHospitalTotal) "year, month" else "year, month, branch_name"
        val sql = """SELECT year, month, $branchCol,
            ROUND(SUM(CAST(admission_days AS REAL)) / NULLIF(SUM(CAST(admission_count AS REAL)), 0), 1)
            FROM inpatient_service WHERE $w GROUP BY $groupCols"""
        val yoySql = if (f.showYoy) """SELECT year, month, $branchCol,
            ROUND(SUM(CAST(admission_days AS REAL)) / NULLIF(SUM(CAST(admission_count AS REAL)), 0), 1)
            FROM inpatient_service WHERE $wy GROUP BY $groupCols""" else null
        return buildLine(sql, p, groupColIdx = 2, valueIdxs = intArrayOf(3),
            yoySql = yoySql, yoyParams = py)
    }

    /** 各院區平均住院日(橫條，含去年同期半透明比對)。 */
    fun branchAlosBar(f: Filters): HBarData {
        val isBranchTotal = f.showHospitalTotal
        val selBranch = if (isBranchTotal) "'全院' AS branch_name" else "branch_name"
        val grpBranch = if (isBranchTotal) "" else "GROUP BY branch_name"
        val (w, p) = whereFor(f, true, 0)
        val whereClause = if (!isBranchTotal) "$w AND branch_name IS NOT NULL AND branch_name != ''" else w
        val rows = db.query(
            "SELECT $selBranch, SUM(CAST(admission_days AS REAL)), SUM(CAST(admission_count AS REAL)) " +
                "FROM inpatient_service WHERE $whereClause $grpBranch", p)
        val currentMap = rows.mapNotNull { r ->
            val b = r.getOrNull(0)?.toString() ?: return@mapNotNull null
            val days = num(r.getOrNull(1)) ?: 0.0
            val adm = num(r.getOrNull(2)) ?: 0.0
            if (adm > 0) b to (days / adm) else null
        }.toMap()

        val yoyMap = if (f.showYoy) {
            val (wy, py) = whereFor(f, true, -1)
            val whereY = if (!isBranchTotal) "$wy AND branch_name IS NOT NULL AND branch_name != ''" else wy
            val yrows = db.query(
                "SELECT $selBranch, SUM(CAST(admission_days AS REAL)), SUM(CAST(admission_count AS REAL)) " +
                    "FROM inpatient_service WHERE $whereY $grpBranch", py)
            yrows.mapNotNull { r ->
                val b = r.getOrNull(0)?.toString() ?: return@mapNotNull null
                val days = num(r.getOrNull(1)) ?: 0.0
                val adm = num(r.getOrNull(2)) ?: 0.0
                if (adm > 0) b to (days / adm) else null
            }.toMap()
        } else emptyMap()

        val allBranches = (currentMap.keys + yoyMap.keys).filter { it.isNotEmpty() }.distinct()
        val items = allBranches.map { b ->
            val cur = currentMap[b] ?: 0.0
            val prev = yoyMap[b] ?: 0.0
            Triple(b, cur, prev)
        }.filter { it.second > 0 || it.third > 0 }
            .sortedByDescending { it.second }

        if (f.showYoy && yoyMap.isNotEmpty()) {
            val hbarRows = items.map { (b, cur, prev) ->
                val deltaPct = if (prev > 0) (cur - prev) / prev * 100.0 else null
                val trailing = if (deltaPct != null) {
                    val sign = if (deltaPct >= 0) "+" else ""
                    "去年 ${String.format("%.1f日", prev)} ($sign${String.format("%.1f%%", deltaPct)})"
                } else if (prev > 0) {
                    "去年 ${String.format("%.1f日", prev)}"
                } else null

                HBarRow(
                    name = b,
                    segments = listOf(
                        BarSegment("去年同期", prev),
                        BarSegment("平均住院日", cur)
                    ),
                    trailing = trailing
                )
            }
            return HBarData(hbarRows, overlap = true)
        } else {
            return HBarData(items.map { HBarRow(it.first, listOf(BarSegment("平均住院日", it.second))) })
        }
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
    }

    /** 院區門診各部別統計 (依門診人次降冪)。 */
    data class OpdDivStat(
        val deptDiv: String,
        val opdVisit: Double,
        val sessions: Double,
        val opdPrior: Double? = null
    ) {
        val avgPerSession: Double
            get() = if (sessions > 0) opdVisit / sessions else 0.0
        val deltaPct: Double?
            get() = if (opdPrior != null && opdPrior > 0) (opdVisit - opdPrior) / opdPrior * 100.0 else null
    }

    /** 院區某部別門診各科別統計 (依門診人次降冪)。 */
    data class OpdDeptStat(
        val dept: String,
        val opdVisit: Double,
        val sessions: Double,
        val opdPrior: Double? = null
    ) {
        val avgPerSession: Double
            get() = if (sessions > 0) opdVisit / sessions else 0.0
        val deltaPct: Double?
            get() = if (opdPrior != null && opdPrior > 0) (opdVisit - opdPrior) / opdPrior * 100.0 else null
    }

    /** 某院區某部別某科別各醫師服務量 (依門診人次降冪)。 */
    data class OpdDoctorStat(
        val branch: String,
        val doctorId: String,
        val doctorName: String,
        val opdVisit: Double,
        val sessions: Double,
        val opdPrior: Double? = null
    ) {
        val avgPerSession: Double
            get() = if (sessions > 0) opdVisit / sessions else 0.0
        val deltaPct: Double?
            get() = if (opdPrior != null && opdPrior > 0) (opdVisit - opdPrior) / opdPrior * 100.0 else null
    }

    /** 通用圖表下鑽維度統計資料 (院區/部別/科別/醫師別)。 */
    data class UniversalDrillStat(
        val name: String,
        val value: Double,
        val secondaryValue: Double? = null,
        val secondaryLabel: String? = null,
        val avgLabel: String? = null,
        val prior: Double? = null,
        val extraId: String? = null,
        val tag: String? = null,
        val recent3: List<Double?> = emptyList(),
        val recentDeltaPct: Double? = null
    ) {
        val deltaPct: Double?
            get() = if (prior != null && prior > 0) (value - prior) / prior * 100.0 else null
        val avgPerUnit: Double?
            get() = if (secondaryValue != null && secondaryValue > 0) value / secondaryValue else null
    }

    private data class MetricSpec(
        val table: String,
        val valExpr: String,
        val secExpr: String? = null,
        val secLbl: String? = null,
        val avgLbl: String? = null
    )

    /** 查詢指定院區與年月之各部別門診人次統計。 */
    fun opdBranchDivStats(
        branch: String,
        year: Int,
        month: Int,
        showYoy: Boolean = true
    ): List<OpdDivStat> {
        val cleanBranch = branch.replace("院區", "").trim()
        val isAll = cleanBranch == "全院" || cleanBranch == "全部"
        val branchCond = if (!isAll) "AND branch_name = ?" else ""
        val curParams = if (!isAll) {
            arrayOf<Any?>(year, month, cleanBranch)
        } else {
            arrayOf<Any?>(year, month)
        }
        val curRows = db.query(
            "SELECT dept_div, SUM(CAST(opd_visit_count AS REAL)), SUM(CAST(total_clinic_sessions AS REAL)) " +
                "FROM outpatient_service WHERE CAST(year AS INTEGER) = ? AND CAST(month AS INTEGER) = ? $branchCond " +
                "AND dept_div IS NOT NULL AND dept_div != '' GROUP BY dept_div",
            curParams
        )
        val priorMap = if (showYoy) {
            val priorParams = if (!isAll) {
                arrayOf<Any?>(year - 1, month, cleanBranch)
            } else {
                arrayOf<Any?>(year - 1, month)
            }
            val priorRows = db.query(
                "SELECT dept_div, SUM(CAST(opd_visit_count AS REAL)) " +
                    "FROM outpatient_service WHERE CAST(year AS INTEGER) = ? AND CAST(month AS INTEGER) = ? $branchCond " +
                    "AND dept_div IS NOT NULL AND dept_div != '' GROUP BY dept_div",
                priorParams
            )
            priorRows.associate { (it[0]?.toString() ?: "") to (num(it[1]) ?: 0.0) }
        } else emptyMap()

        return curRows.mapNotNull { r ->
            val div = r[0]?.toString() ?: return@mapNotNull null
            val opd = num(r[1]) ?: 0.0
            if (opd <= 0.0) return@mapNotNull null
            val sess = num(r[2]) ?: 0.0
            OpdDivStat(
                deptDiv = div,
                opdVisit = opd,
                sessions = sess,
                opdPrior = priorMap[div]
            )
        }.sortedByDescending { it.opdVisit }
    }

    /** 查詢指定院區、部別與年月之各科別門診人次統計。 */
    fun opdBranchDivDeptStats(
        branch: String,
        deptDiv: String,
        year: Int,
        month: Int,
        showYoy: Boolean = true
    ): List<OpdDeptStat> {
        val cleanBranch = branch.replace("院區", "").trim()
        val isAll = cleanBranch == "全院" || cleanBranch == "全部"
        val branchCond = if (!isAll) "AND branch_name = ?" else ""
        val curParams = if (!isAll) {
            arrayOf<Any?>(year, month, cleanBranch, deptDiv)
        } else {
            arrayOf<Any?>(year, month, deptDiv)
        }
        val curRows = db.query(
            "SELECT dept, SUM(CAST(opd_visit_count AS REAL)), SUM(CAST(total_clinic_sessions AS REAL)) " +
                "FROM outpatient_service WHERE CAST(year AS INTEGER) = ? AND CAST(month AS INTEGER) = ? $branchCond AND dept_div = ? " +
                "AND dept IS NOT NULL AND dept != '' GROUP BY dept",
            curParams
        )
        val priorMap = if (showYoy) {
            val priorParams = if (!isAll) {
                arrayOf<Any?>(year - 1, month, cleanBranch, deptDiv)
            } else {
                arrayOf<Any?>(year - 1, month, deptDiv)
            }
            val priorRows = db.query(
                "SELECT dept, SUM(CAST(opd_visit_count AS REAL)) " +
                    "FROM outpatient_service WHERE CAST(year AS INTEGER) = ? AND CAST(month AS INTEGER) = ? $branchCond AND dept_div = ? " +
                    "AND dept IS NOT NULL AND dept != '' GROUP BY dept",
                priorParams
            )
            priorRows.associate { (it[0]?.toString() ?: "") to (num(it[1]) ?: 0.0) }
        } else emptyMap()

        return curRows.mapNotNull { r ->
            val dept = r[0]?.toString() ?: return@mapNotNull null
            val opd = num(r[1]) ?: 0.0
            if (opd <= 0.0) return@mapNotNull null
            val sess = num(r[2]) ?: 0.0
            OpdDeptStat(
                dept = dept,
                opdVisit = opd,
                sessions = sess,
                opdPrior = priorMap[dept]
            )
        }.sortedByDescending { it.opdVisit }
    }

    /** 查詢指定院區、部別、科別與年月之各醫師門診人次服務量。 */
    fun opdDoctorStats(
        branch: String,
        deptDiv: String,
        dept: String,
        year: Int,
        month: Int,
        showYoy: Boolean = true
    ): List<OpdDoctorStat> {
        return try {
            val cleanBranch = branch.replace("院區", "").trim()
            val isAll = cleanBranch == "全院" || cleanBranch == "全部"
            val branchCond = if (!isAll) "AND branch_name = ?" else ""
            val curParams = if (!isAll) {
                arrayOf<Any?>(year, month, cleanBranch, dept)
            } else {
                arrayOf<Any?>(year, month, dept)
            }
            val curRows = db.query(
                "SELECT branch_name, doctor_id, doctor_name, SUM(CAST(opd_visit_count AS REAL)), SUM(CAST(sessions AS REAL)) " +
                    "FROM physician_service WHERE CAST(year AS INTEGER) = ? AND CAST(month AS INTEGER) = ? $branchCond AND dept = ? " +
                    "AND doctor_name IS NOT NULL AND doctor_name != '' " +
                    "GROUP BY branch_name, doctor_id, doctor_name",
                curParams
            )
            val priorMap = if (showYoy) {
                val priorParams = if (!isAll) {
                    arrayOf<Any?>(year - 1, month, cleanBranch, dept)
                } else {
                    arrayOf<Any?>(year - 1, month, dept)
                }
                val priorRows = db.query(
                    "SELECT branch_name, doctor_id, doctor_name, SUM(CAST(opd_visit_count AS REAL)) " +
                        "FROM physician_service WHERE CAST(year AS INTEGER) = ? AND CAST(month AS INTEGER) = ? $branchCond AND dept = ? " +
                        "AND doctor_name IS NOT NULL AND doctor_name != '' " +
                        "GROUP BY branch_name, doctor_id, doctor_name",
                    priorParams
                )
                priorRows.associate {
                    val b = it[0]?.toString() ?: ""
                    val id = it[1]?.toString() ?: ""
                    val name = it[2]?.toString() ?: ""
                    "$b-$id-$name" to (num(it[3]) ?: 0.0)
                }
            } else emptyMap()

            curRows.mapNotNull { r ->
                val b = r[0]?.toString() ?: ""
                val id = r[1]?.toString() ?: ""
                val name = r[2]?.toString() ?: return@mapNotNull null
                val opd = num(r[3]) ?: 0.0
                if (opd <= 0.0) return@mapNotNull null
                val sess = num(r[4]) ?: 0.0
                val key = "$b-$id-$name"
                OpdDoctorStat(
                    branch = b,
                    doctorId = id,
                    doctorName = name,
                    opdVisit = opd,
                    sessions = sess,
                    opdPrior = priorMap[key]
                )
            }.sortedWith(compareByDescending<OpdDoctorStat> { it.opdVisit }.thenByDescending { it.sessions })
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 通用多維度下鑽統計查詢。
     * @param metricType "OPD", "ER", "IPD_DAYS", "IPD_COUNT", "DIS_DAYS", "DIS_COUNT", "INC_TOTAL", "INC_SELF"
     * @param targetLevel "BRANCH", "DIVISION", "DEPARTMENT", "DOCTOR"
     */
    fun universalDrillStats(
        metricType: String,
        targetLevel: String,
        branch: String?,
        deptDiv: String?,
        dept: String?,
        year: Int,
        month: Int,
        showYoy: Boolean = true
    ): List<UniversalDrillStat> {
        return try {
            val cleanBranch = branch?.replace("院區", "")?.trim() ?: ""
            val isAllBranch = cleanBranch.isEmpty() || cleanBranch == "全院" || cleanBranch == "全部"
            val bCond = if (!isAllBranch) "AND branch_name = ?" else ""

            val yms = listOf(
                monthBack(year, month, 2),
                monthBack(year, month, 1),
                Pair(year, month)
            )
            val ym1 = yms[0].first * 100 + yms[0].second
            val ym2 = yms[1].first * 100 + yms[1].second
            val ym3 = yms[2].first * 100 + yms[2].second
            val ymInSql = "(CAST(year AS INTEGER) * 100 + CAST(month AS INTEGER)) IN ($ym1, $ym2, $ym3)"

            fun parseRecentMap(
                rows: List<List<Any?>>,
                keyFn: (List<Any?>) -> String,
                yearIdx: Int,
                monthIdx: Int,
                valIdx: Int
            ): Map<String, List<Double?>> {
                val raw = HashMap<String, Array<Double?>>()
                for (r in rows) {
                    val k = keyFn(r)
                    if (k.isEmpty()) continue
                    val y = r[yearIdx]?.toString()?.toIntOrNull() ?: 0
                    val m = r[monthIdx]?.toString()?.toIntOrNull() ?: 0
                    val idx = yms.indexOfFirst { it.first == y && it.second == m }
                    if (idx < 0) continue
                    val arr = raw.getOrPut(k) { arrayOfNulls(3) }
                    arr[idx] = num(r[valIdx])
                }
                return raw.mapValues { it.value.toList() }
            }

            when (targetLevel) {
                "BRANCH" -> {
                    // 用於 Flow B 第二層：在固定部別 (deptDiv) 下展開各院區
                    val spec = when (metricType) {
                        "OPD" -> MetricSpec("outpatient_service", "SUM(CAST(opd_visit_count AS REAL))", "SUM(CAST(total_clinic_sessions AS REAL))", "診", "人/診")
                        "IPD_DAYS" -> MetricSpec("inpatient_service", "SUM(CAST(admission_days AS REAL))", "SUM(CAST(admission_count AS REAL))", "人次", "日/人次")
                        else -> MetricSpec("outpatient_service", "SUM(CAST(opd_visit_count AS REAL))", "SUM(CAST(total_clinic_sessions AS REAL))", "診", "人/診")
                    }
                    val curRows = db.query(
                        "SELECT branch_name, ${spec.valExpr} ${if (spec.secExpr != null) ", ${spec.secExpr}" else ""} FROM ${spec.table} " +
                            "WHERE CAST(year AS INTEGER) = ? AND CAST(month AS INTEGER) = ? AND dept_div = ? " +
                            "AND branch_name IS NOT NULL AND branch_name != '' GROUP BY branch_name",
                        arrayOf<Any?>(year, month, deptDiv)
                    )
                    val priorMap = if (showYoy) {
                        val priorRows = db.query(
                            "SELECT branch_name, ${spec.valExpr} FROM ${spec.table} " +
                                "WHERE CAST(year AS INTEGER) = ? AND CAST(month AS INTEGER) = ? AND dept_div = ? " +
                                "AND branch_name IS NOT NULL AND branch_name != '' GROUP BY branch_name",
                            arrayOf<Any?>(year - 1, month, deptDiv)
                        )
                        priorRows.associate { (it[0]?.toString() ?: "") to (num(it[1]) ?: 0.0) }
                    } else emptyMap()

                    val recentRows = db.query(
                        "SELECT branch_name, year, month, ${spec.valExpr} FROM ${spec.table} " +
                            "WHERE $ymInSql AND dept_div = ? " +
                            "AND branch_name IS NOT NULL AND branch_name != '' GROUP BY branch_name, year, month",
                        arrayOf<Any?>(deptDiv)
                    )
                    val recentMap = parseRecentMap(recentRows, { it[0]?.toString() ?: "" }, 1, 2, 3)

                    curRows.mapNotNull { r ->
                        val b = r[0]?.toString() ?: return@mapNotNull null
                        val v = num(r[1]) ?: 0.0
                        if (v <= 0.0) return@mapNotNull null
                        val s = if (spec.secExpr != null) num(r[2]) ?: 0.0 else null

                        val recent = recentMap[b] ?: emptyList()
                        val trend = if (recent.isNotEmpty()) {
                            listOf(recent.getOrNull(0), recent.getOrNull(1), recent.getOrNull(2) ?: v)
                        } else {
                            listOf(null, null, v)
                        }
                        val prev = trend[1]
                        val trendDelta = if (prev != null && prev > 0.0 && v > 0.0) (v - prev) / prev * 100.0 else null

                        UniversalDrillStat(
                            name = b,
                            value = v,
                            secondaryValue = s,
                            secondaryLabel = spec.secLbl,
                            avgLabel = spec.avgLbl,
                            prior = priorMap[b],
                            recent3 = trend,
                            recentDeltaPct = trendDelta
                        )
                    }.sortedByDescending { it.value }
                }

                "DIVISION" -> {
                    // 用於 Flow A 第二層：在固定院區 (branch) 下展開各部別
                    val curParams = if (!isAllBranch) arrayOf<Any?>(year, month, cleanBranch) else arrayOf<Any?>(year, month)
                    val priorParams = if (!isAllBranch) arrayOf<Any?>(year - 1, month, cleanBranch) else arrayOf<Any?>(year - 1, month)

                    val spec = when (metricType) {
                        "OPD" -> MetricSpec("outpatient_service", "SUM(CAST(opd_visit_count AS REAL))", "SUM(CAST(total_clinic_sessions AS REAL))", "診", "人/診")
                        "ER" -> MetricSpec("outpatient_service", "SUM(CAST(er_visit AS REAL))", "SUM(CAST(total_clinic_sessions AS REAL))", "診", "人/診")
                        "IPD_DAYS" -> MetricSpec("inpatient_service", "SUM(CAST(admission_days AS REAL))", "SUM(CAST(admission_count AS REAL))", "人次", "日/人次")
                        "IPD_COUNT" -> MetricSpec("inpatient_service", "SUM(CAST(admission_count AS REAL))", "SUM(CAST(admission_days AS REAL))", "人日", "人日/人次")
                        "DIS_DAYS" -> MetricSpec("inpatient_service", "SUM(CAST(discharge_days AS REAL))", "SUM(CAST(discharge_count AS REAL))", "人次", "日/人次")
                        "DIS_COUNT" -> MetricSpec("inpatient_service", "SUM(CAST(discharge_count AS REAL))", "SUM(CAST(discharge_days AS REAL))", "人日", "人日/人次")
                        "INC_TOTAL" -> MetricSpec("physician_service", "(SUM(CAST(opd_nhi_income AS REAL)) + SUM(CAST(opd_selfpay_income AS REAL)) + SUM(CAST(ipd_nhi_income AS REAL)) + SUM(CAST(ipd_selfpay_income AS REAL)))")
                        "INC_SELF" -> MetricSpec("physician_service", "(SUM(CAST(opd_selfpay_income AS REAL)) + SUM(CAST(ipd_selfpay_income AS REAL)))")
                        else -> MetricSpec("outpatient_service", "SUM(CAST(opd_visit_count AS REAL))", "SUM(CAST(total_clinic_sessions AS REAL))", "診", "人/診")
                    }

                    val curRows = db.query(
                        "SELECT dept_div, ${spec.valExpr} ${if (spec.secExpr != null) ", ${spec.secExpr}" else ""} FROM ${spec.table} " +
                            "WHERE CAST(year AS INTEGER) = ? AND CAST(month AS INTEGER) = ? $bCond " +
                            "AND dept_div IS NOT NULL AND dept_div != '' GROUP BY dept_div",
                        curParams
                    )
                    val priorMap = if (showYoy) {
                        val priorRows = db.query(
                            "SELECT dept_div, ${spec.valExpr} FROM ${spec.table} " +
                                "WHERE CAST(year AS INTEGER) = ? AND CAST(month AS INTEGER) = ? $bCond " +
                                "AND dept_div IS NOT NULL AND dept_div != '' GROUP BY dept_div",
                            priorParams
                        )
                        priorRows.associate { (it[0]?.toString() ?: "") to (num(it[1]) ?: 0.0) }
                    } else emptyMap()

                    val recentParams = mutableListOf<Any?>()
                    if (!isAllBranch) recentParams.add(cleanBranch)
                    val recentRows = db.query(
                        "SELECT dept_div, year, month, ${spec.valExpr} FROM ${spec.table} " +
                            "WHERE $ymInSql $bCond " +
                            "AND dept_div IS NOT NULL AND dept_div != '' GROUP BY dept_div, year, month",
                        recentParams.toTypedArray()
                    )
                    val recentMap = parseRecentMap(recentRows, { it[0]?.toString() ?: "" }, 1, 2, 3)

                    curRows.mapNotNull { r ->
                        val div = r[0]?.toString() ?: return@mapNotNull null
                        val v = num(r[1]) ?: 0.0
                        if (v <= 0.0) return@mapNotNull null
                        val s = if (spec.secExpr != null) num(r[2]) ?: 0.0 else null

                        val recent = recentMap[div] ?: emptyList()
                        val trend = if (recent.isNotEmpty()) {
                            listOf(recent.getOrNull(0), recent.getOrNull(1), recent.getOrNull(2) ?: v)
                        } else {
                            listOf(null, null, v)
                        }
                        val prev = trend[1]
                        val trendDelta = if (prev != null && prev > 0.0 && v > 0.0) (v - prev) / prev * 100.0 else null

                        UniversalDrillStat(
                            name = div,
                            value = v,
                            secondaryValue = s,
                            secondaryLabel = spec.secLbl,
                            avgLabel = spec.avgLbl,
                            prior = priorMap[div],
                            recent3 = trend,
                            recentDeltaPct = trendDelta
                        )
                    }.sortedByDescending { it.value }
                }

                "DEPARTMENT" -> {
                    // 用於第三層：在固定院區 (branch) 與部別 (deptDiv) 下展開各科別
                    val dCond = if (!deptDiv.isNullOrEmpty()) "AND dept_div = ?" else ""
                    val curParams = mutableListOf<Any?>(year, month)
                    if (!isAllBranch) curParams.add(cleanBranch)
                    if (!deptDiv.isNullOrEmpty()) curParams.add(deptDiv)

                    val priorParams = mutableListOf<Any?>(year - 1, month)
                    if (!isAllBranch) priorParams.add(cleanBranch)
                    if (!deptDiv.isNullOrEmpty()) priorParams.add(deptDiv)

                    val spec = when (metricType) {
                        "OPD" -> MetricSpec("outpatient_service", "SUM(CAST(opd_visit_count AS REAL))", "SUM(CAST(total_clinic_sessions AS REAL))", "診", "人/診")
                        "ER" -> MetricSpec("outpatient_service", "SUM(CAST(er_visit AS REAL))", "SUM(CAST(total_clinic_sessions AS REAL))", "診", "人/診")
                        "IPD_DAYS" -> MetricSpec("inpatient_service", "SUM(CAST(admission_days AS REAL))", "SUM(CAST(admission_count AS REAL))", "人次", "日/人次")
                        "IPD_COUNT" -> MetricSpec("inpatient_service", "SUM(CAST(admission_count AS REAL))", "SUM(CAST(admission_days AS REAL))", "人日", "人日/人次")
                        "DIS_DAYS" -> MetricSpec("inpatient_service", "SUM(CAST(discharge_days AS REAL))", "SUM(CAST(discharge_count AS REAL))", "人次", "日/人次")
                        "DIS_COUNT" -> MetricSpec("inpatient_service", "SUM(CAST(discharge_count AS REAL))", "SUM(CAST(discharge_days AS REAL))", "人日", "人日/人次")
                        "INC_TOTAL" -> MetricSpec("physician_service", "(SUM(CAST(opd_nhi_income AS REAL)) + SUM(CAST(opd_selfpay_income AS REAL)) + SUM(CAST(ipd_nhi_income AS REAL)) + SUM(CAST(ipd_selfpay_income AS REAL)))")
                        "INC_SELF" -> MetricSpec("physician_service", "(SUM(CAST(opd_selfpay_income AS REAL)) + SUM(CAST(ipd_selfpay_income AS REAL)))")
                        else -> MetricSpec("outpatient_service", "SUM(CAST(opd_visit_count AS REAL))", "SUM(CAST(total_clinic_sessions AS REAL))", "診", "人/診")
                    }

                    val curRows = db.query(
                        "SELECT dept, ${spec.valExpr} ${if (spec.secExpr != null) ", ${spec.secExpr}" else ""} FROM ${spec.table} " +
                            "WHERE CAST(year AS INTEGER) = ? AND CAST(month AS INTEGER) = ? $bCond $dCond " +
                            "AND dept IS NOT NULL AND dept != '' GROUP BY dept",
                        curParams.toTypedArray()
                    )
                    val priorMap = if (showYoy) {
                        val priorRows = db.query(
                            "SELECT dept, ${spec.valExpr} FROM ${spec.table} " +
                                "WHERE CAST(year AS INTEGER) = ? AND CAST(month AS INTEGER) = ? $bCond $dCond " +
                                "AND dept IS NOT NULL AND dept != '' GROUP BY dept",
                            priorParams.toTypedArray()
                        )
                        priorRows.associate { (it[0]?.toString() ?: "") to (num(it[1]) ?: 0.0) }
                    } else emptyMap()

                    val recentParams = mutableListOf<Any?>()
                    if (!isAllBranch) recentParams.add(cleanBranch)
                    if (!deptDiv.isNullOrEmpty()) recentParams.add(deptDiv)
                    val recentRows = db.query(
                        "SELECT dept, year, month, ${spec.valExpr} FROM ${spec.table} " +
                            "WHERE $ymInSql $bCond $dCond " +
                            "AND dept IS NOT NULL AND dept != '' GROUP BY dept, year, month",
                        recentParams.toTypedArray()
                    )
                    val recentMap = parseRecentMap(recentRows, { it[0]?.toString() ?: "" }, 1, 2, 3)

                    curRows.mapNotNull { r ->
                        val d = r[0]?.toString() ?: return@mapNotNull null
                        val v = num(r[1]) ?: 0.0
                        if (v <= 0.0) return@mapNotNull null
                        val s = if (spec.secExpr != null) num(r[2]) ?: 0.0 else null

                        val recent = recentMap[d] ?: emptyList()
                        val trend = if (recent.isNotEmpty()) {
                            listOf(recent.getOrNull(0), recent.getOrNull(1), recent.getOrNull(2) ?: v)
                        } else {
                            listOf(null, null, v)
                        }
                        val prev = trend[1]
                        val trendDelta = if (prev != null && prev > 0.0 && v > 0.0) (v - prev) / prev * 100.0 else null

                        UniversalDrillStat(
                            name = d,
                            value = v,
                            secondaryValue = s,
                            secondaryLabel = spec.secLbl,
                            avgLabel = spec.avgLbl,
                            prior = priorMap[d],
                            recent3 = trend,
                            recentDeltaPct = trendDelta
                        )
                    }.sortedByDescending { it.value }
                }

                "DOCTOR" -> {
                    // 用於第四層：在固定院區、科別下展開各醫師
                    val dCond = if (!dept.isNullOrEmpty()) "AND dept = ?" else ""
                    val curParams = mutableListOf<Any?>(year, month)
                    if (!isAllBranch) curParams.add(cleanBranch)
                    if (!dept.isNullOrEmpty()) curParams.add(dept)

                    val priorParams = mutableListOf<Any?>(year - 1, month)
                    if (!isAllBranch) priorParams.add(cleanBranch)
                    if (!dept.isNullOrEmpty()) priorParams.add(dept)

                    val spec = when (metricType) {
                        "OPD" -> MetricSpec("physician_service", "SUM(CAST(opd_visit_count AS REAL))", "SUM(CAST(sessions AS REAL))", "診", "人/診")
                        "ER" -> MetricSpec("physician_service", "SUM(CAST(er_visit AS REAL))", "SUM(CAST(sessions AS REAL))", "診", "人/診")
                        "IPD_DAYS" -> MetricSpec("physician_service", "SUM(CAST(admission_days AS REAL))", "SUM(CAST(admission_count AS REAL))", "人次", "日/人次")
                        "INC_TOTAL" -> MetricSpec("physician_service", "(SUM(CAST(opd_nhi_income AS REAL)) + SUM(CAST(opd_selfpay_income AS REAL)) + SUM(CAST(ipd_nhi_income AS REAL)) + SUM(CAST(ipd_selfpay_income AS REAL)))")
                        "INC_SELF" -> MetricSpec("physician_service", "(SUM(CAST(opd_selfpay_income AS REAL)) + SUM(CAST(ipd_selfpay_income AS REAL)))")
                        else -> MetricSpec("physician_service", "SUM(CAST(opd_visit_count AS REAL))", "SUM(CAST(sessions AS REAL))", "診", "人/診")
                    }

                    val secColSql = if (spec.secExpr != null) ", ${spec.secExpr}" else ""
                    val curRows = db.query(
                        "SELECT branch_name, doctor_id, doctor_name, ${spec.valExpr} $secColSql " +
                            "FROM physician_service WHERE CAST(year AS INTEGER) = ? AND CAST(month AS INTEGER) = ? $bCond $dCond " +
                            "AND doctor_name IS NOT NULL AND doctor_name != '' " +
                            "GROUP BY branch_name, doctor_id, doctor_name",
                        curParams.toTypedArray()
                    )
                    val priorMap = if (showYoy) {
                        val priorRows = db.query(
                            "SELECT branch_name, doctor_id, doctor_name, ${spec.valExpr} " +
                                "FROM physician_service WHERE CAST(year AS INTEGER) = ? AND CAST(month AS INTEGER) = ? $bCond $dCond " +
                                "AND doctor_name IS NOT NULL AND doctor_name != '' " +
                                "GROUP BY branch_name, doctor_id, doctor_name",
                            priorParams.toTypedArray()
                        )
                        priorRows.associate {
                            val b = it[0]?.toString() ?: ""
                            val id = it[1]?.toString() ?: ""
                            val name = it[2]?.toString() ?: ""
                            "$b-$id-$name" to (num(it[3]) ?: 0.0)
                        }
                    } else emptyMap()

                    val recentParams = mutableListOf<Any?>()
                    if (!isAllBranch) recentParams.add(cleanBranch)
                    if (!dept.isNullOrEmpty()) recentParams.add(dept)
                    val recentRows = db.query(
                        "SELECT branch_name, doctor_id, doctor_name, year, month, ${spec.valExpr} " +
                            "FROM physician_service WHERE $ymInSql $bCond $dCond " +
                            "AND doctor_name IS NOT NULL AND doctor_name != '' " +
                            "GROUP BY branch_name, doctor_id, doctor_name, year, month",
                        recentParams.toTypedArray()
                    )
                    val recentMap = parseRecentMap(recentRows, { "${it[0]}-${it[1]}-${it[2]}" }, 3, 4, 5)

                    curRows.mapNotNull { r ->
                        val b = r[0]?.toString() ?: ""
                        val id = r[1]?.toString() ?: ""
                        val name = r[2]?.toString() ?: return@mapNotNull null
                        val v = num(r[3]) ?: 0.0
                        if (v <= 0.0) return@mapNotNull null
                        val s = if (spec.secExpr != null) num(r[4]) ?: 0.0 else null
                        val key = "$b-$id-$name"

                        val recent = recentMap[key] ?: emptyList()
                        val trend = if (recent.isNotEmpty()) {
                            listOf(recent.getOrNull(0), recent.getOrNull(1), recent.getOrNull(2) ?: v)
                        } else {
                            listOf(null, null, v)
                        }
                        val prev = trend[1]
                        val trendDelta = if (prev != null && prev > 0.0 && v > 0.0) (v - prev) / prev * 100.0 else null

                        UniversalDrillStat(
                            name = name,
                            extraId = id,
                            tag = if (isAllBranch) b else null,
                            value = v,
                            secondaryValue = s,
                            secondaryLabel = spec.secLbl,
                            avgLabel = spec.avgLbl,
                            prior = priorMap[key],
                            recent3 = trend,
                            recentDeltaPct = trendDelta
                        )
                    }.sortedWith(compareByDescending<UniversalDrillStat> { it.value }.thenByDescending { it.secondaryValue ?: 0.0 })
                }

                else -> emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
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
    /** 大類別選項（病床分頁篩選以「大類別」為單位，產後（小孩）與其他合併，排序：一般、ICU、特殊、嬰兒床、其他）。 */
    fun bedMajors(f: Filters): List<String> {
        val (w, p) = whereFor(f, false, 0)
        if (w.isEmpty()) return emptyList()
        val rows = db.query(
            "SELECT DISTINCT CASE WHEN TRIM(major_category) IN ('產後（小孩）', '產後(小孩)') THEN '其他' ELSE major_category END " +
                "FROM bed_type_service WHERE $w AND major_category IS NOT NULL", p
        )
        val raw = rows.mapNotNull { it[0]?.toString() }.filter { it.isNotEmpty() }.distinct()
        val order = listOf("一般", "ICU", "特殊", "嬰兒床", "其他")
        return raw.sortedWith(compareBy {
            val idx = order.indexOf(it)
            if (idx >= 0) idx else order.size
        })
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
        // 病床分頁篩選改以「大類別(major_category)」為單位，產後（小孩）與其他合併
        val expanded = cats.flatMap {
            if (it == "其他") listOf("其他", "產後（小孩）", "產後(小孩)") else listOf(it)
        }.distinct()
        return "major_category IN (${expanded.joinToString(",") { "?" }})" to expanded.toTypedArray()
    }

    /** 篩選區間內病床資料的最新 (年, 月)。 */
    private fun bedLatestYm(f: Filters): Pair<Int, Int>? {
        val parts = mutableListOf<String>()
        val params = mutableListOf<Any?>()
        if (f.years.isNotEmpty()) {
            parts.add("year IN (${f.years.joinToString(",") { "?" }})")
            params.addAll(f.years)
        }
        if (f.months.isNotEmpty()) {
            parts.add("month IN (${f.months.joinToString(",") { "?" }})")
            params.addAll(f.months)
        }
        val w = if (parts.isNotEmpty()) parts.joinToString(" AND ") else "1=1"
        val r = db.query(
            "SELECT year, month FROM bed_type_service WHERE $w " +
                "ORDER BY CAST(year AS INTEGER) DESC, CAST(month AS INTEGER) DESC LIMIT 1",
            params.toTypedArray()
        ).firstOrNull() ?: return null
        val y = r[0]?.toString()?.toIntOrNull() ?: return null
        val m = r[1]?.toString()?.toIntOrNull() ?: return null
        return y to m
    }

    /** 依最新年月模式附加年月條件。 */
    private fun ymCond(f: Filters, latestOnly: Boolean): Pair<String, Array<Any?>> {
        if (!latestOnly) return "" to emptyArray()
        val (y, m) = bedLatestYm(f) ?: return "" to emptyArray()
        return "year=? AND month=?" to arrayOf(y.toString(), m.toString())
    }

    private fun bedMonthlySql(f: Filters, offset: Int, cats: List<String>): Pair<String, Array<Any?>> {
        val (w, p) = whereFor(f, false, offset)
        val (cc, cp) = catCond(cats)
        val where = if (cc.isEmpty()) w else "$w AND $cc"
        val params = arrayOf(*p, *cp)
        return """SELECT year, month, category,
            ${bedActualOccSql()}, SUM(CAST(actual_open_beds AS REAL)),
            ${bedRegOccSql()}
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

    /** 各病床類別實際佔床率(橫條，降冪；含去年同期半透明比對)。 */
    fun bedCategoryOccBar(f: Filters, cats: List<String>): HBarData {
        val (w, p) = whereFor(f, false, 0)
        val (cc, cp) = catCond(cats)
        val where = if (cc.isEmpty()) w else "$w AND $cc"
        val curRows = db.query(
            "SELECT category, ${bedActualOccSql()} FROM bed_type_service " +
                "WHERE $where AND category IS NOT NULL AND category != '' GROUP BY category",
            arrayOf(*p, *cp)
        )
        val curMap = curRows.associate {
            (it[0]?.toString() ?: "") to ((num(it[1]) ?: 0.0) * 100.0)
        }
        val yoyMap = if (f.showYoy) {
            val (wy, py) = whereFor(f, false, -1)
            val whereY = if (cc.isEmpty()) wy else "$wy AND $cc"
            val yRows = db.query(
                "SELECT category, ${bedActualOccSql()} FROM bed_type_service " +
                    "WHERE $whereY AND category IS NOT NULL AND category != '' GROUP BY category",
                arrayOf(*py, *cp)
            )
            yRows.associate {
                (it[0]?.toString() ?: "") to ((num(it[1]) ?: 0.0) * 100.0)
            }
        } else emptyMap()

        val allCats = (curMap.keys + yoyMap.keys).filter { it.isNotEmpty() }.distinct()
        val items = allCats.map { c ->
            val cur = curMap[c] ?: 0.0
            val prev = yoyMap[c] ?: 0.0
            Triple(c, cur, prev)
        }.filter { it.second > 0 || it.third > 0 }.sortedByDescending { it.second }

        if (f.showYoy && yoyMap.isNotEmpty()) {
            val hbarRows = items.map { (c, cur, prev) ->
                val delta = cur - prev
                val sign = if (delta >= 0) "+" else ""
                val trailing = if (prev > 0) "去年 ${String.format("%.1f%%", prev)} ($sign${String.format("%.1f%%", delta)})" else null
                HBarRow(
                    name = c,
                    segments = listOf(BarSegment("去年同期", prev), BarSegment("實際佔床率", cur)),
                    trailing = trailing
                )
            }
            return HBarData(hbarRows, overlap = true, targetLine = 85.0)
        } else {
            return HBarData(items.map { HBarRow(it.first, listOf(BarSegment("實際佔床率", it.second))) }, targetLine = 85.0)
        }
    }

    /** 實際開床率月趨勢(依病床類別，%)，含去年同期虛線。計算方式：實際床數 / 登記床數。 */
    fun bedOpenRateMonthly(f: Filters, cats: List<String>): LineChartData {
        val (w, p) = whereFor(f, false, 0)
        val (cc, cp) = catCond(cats)
        val where = if (cc.isEmpty()) w else "$w AND $cc"
        val sql = """SELECT year, month, category,
            ROUND(SUM(CAST(actual_open_beds AS REAL)) * 100.0 / NULLIF(SUM(CAST(registered_beds AS REAL)), 0), 1)
            FROM bed_type_service WHERE $where GROUP BY year, month, category"""
        val yoySql = if (f.showYoy) {
            val (wy, _) = whereFor(f, false, -1)
            val whereY = if (cc.isEmpty()) wy else "$wy AND $cc"
            """SELECT year, month, category,
                ROUND(SUM(CAST(actual_open_beds AS REAL)) * 100.0 / NULLIF(SUM(CAST(registered_beds AS REAL)), 0), 1)
                FROM bed_type_service WHERE $whereY GROUP BY year, month, category"""
        } else null
        val yoyParams = if (f.showYoy) {
            val (_, py) = whereFor(f, false, -1)
            arrayOf(*py, *cp)
        } else emptyArray()
        return buildLine(sql, arrayOf(*p, *cp), groupColIdx = 2, valueIdxs = intArrayOf(3),
            yoySql = yoySql, yoyParams = yoyParams)
    }

    /** 各病床類別實際開床率(單月橫條：半透明當月登記床數，實色實開床數，後方顯示開床率，不必顯示去年同期)。 */
    fun bedCategoryOpenRateBar(f: Filters, cats: List<String>): HBarData {
        val (w, p) = whereFor(f, false, 0)
        val (cc, cp) = catCond(cats)
        val where = if (cc.isEmpty()) w else "$w AND $cc"
        val rows = db.query(
            "SELECT category, SUM(CAST(registered_beds AS REAL)), SUM(CAST(actual_open_beds AS REAL)) " +
                "FROM bed_type_service WHERE $where AND category IS NOT NULL AND category != '' GROUP BY category",
            arrayOf(*p, *cp)
        )
        val items = rows.mapNotNull { r ->
            val cat = r.getOrNull(0)?.toString() ?: return@mapNotNull null
            val reg = num(r.getOrNull(1)) ?: 0.0
            val open = num(r.getOrNull(2)) ?: 0.0
            if (reg > 0) Triple(cat, reg, open) else null
        }.sortedByDescending { it.second }

        val hbarRows = items.map { (cat, reg, open) ->
            val rate = if (reg > 0) open / reg * 100.0 else 0.0
            HBarRow(
                name = cat,
                segments = listOf(
                    BarSegment("登記床數", reg),
                    BarSegment("實開床數", open)
                ),
                trailing = String.format("開床率 %.1f%%", rate)
            )
        }
        return HBarData(hbarRows, overlap = true)
    }

    /** 實際床佔床率月趨勢(依院區，%)，含去年同期虛線。 */
    fun branchBedOccMonthly(f: Filters, cats: List<String> = emptyList()): LineChartData {
        val (w, p) = whereFor(f, false, 0)
        val (cc, cp) = catCond(cats)
        val where = if (cc.isEmpty()) w else "$w AND $cc"
        val branchCol = if (f.showHospitalTotal) "'全院' AS branch_name" else "branch_name"
        val groupCols = if (f.showHospitalTotal) "year, month" else "year, month, branch_name"
        val sql = """SELECT year, month, $branchCol,
            ${bedActualOccSql()}
            FROM bed_type_service WHERE $where GROUP BY $groupCols"""
        val yoySql = if (f.showYoy) {
            val (wy, _) = whereFor(f, false, -1)
            val whereY = if (cc.isEmpty()) wy else "$wy AND $cc"
            """SELECT year, month, $branchCol,
                ${bedActualOccSql()}
                FROM bed_type_service WHERE $whereY GROUP BY $groupCols"""
        } else null
        val yoyParams = if (f.showYoy) {
            val (_, py) = whereFor(f, false, -1)
            arrayOf(*py, *cp)
        } else emptyArray()
        return buildLine(sql, arrayOf(*p, *cp), groupColIdx = 2, valueIdxs = intArrayOf(3), valueScale = 100.0,
            yoySql = yoySql, yoyParams = yoyParams)
    }

    /** 各院區實際佔床率(橫條，降冪；含去年同期半透明比對)。 */
    fun branchBedOccBar(f: Filters, cats: List<String> = emptyList()): HBarData {
        val isBranchTotal = f.showHospitalTotal
        val selBranch = if (isBranchTotal) "'全院' AS branch_name" else "branch_name"
        val grpBranch = if (isBranchTotal) "" else "GROUP BY branch_name"
        val (w, p) = whereFor(f, false, 0)
        val (cc, cp) = catCond(cats)
        val where = if (cc.isEmpty()) w else "$w AND $cc"
        val whereClause = if (!isBranchTotal) "$where AND branch_name IS NOT NULL AND branch_name != ''" else where
        val curRows = db.query(
            "SELECT $selBranch, ${bedActualOccSql()} FROM bed_type_service " +
                "WHERE $whereClause $grpBranch",
            arrayOf(*p, *cp)
        )
        val curMap = curRows.associate {
            (it[0]?.toString() ?: "") to ((num(it[1]) ?: 0.0) * 100.0)
        }
        val yoyMap = if (f.showYoy) {
            val (wy, py) = whereFor(f, false, -1)
            val whereY = if (cc.isEmpty()) wy else "$wy AND $cc"
            val whereYClause = if (!isBranchTotal) "$whereY AND branch_name IS NOT NULL AND branch_name != ''" else whereY
            val yRows = db.query(
                "SELECT $selBranch, ${bedActualOccSql()} FROM bed_type_service " +
                    "WHERE $whereYClause $grpBranch",
                arrayOf(*py, *cp)
            )
            yRows.associate {
                (it[0]?.toString() ?: "") to ((num(it[1]) ?: 0.0) * 100.0)
            }
        } else emptyMap()

        val allBranches = (curMap.keys + yoyMap.keys).filter { it.isNotEmpty() }.distinct()
        val items = allBranches.map { b ->
            val cur = curMap[b] ?: 0.0
            val prev = yoyMap[b] ?: 0.0
            Triple(b, cur, prev)
        }.filter { it.second > 0 || it.third > 0 }.sortedByDescending { it.second }

        if (f.showYoy && yoyMap.isNotEmpty()) {
            val hbarRows = items.map { (b, cur, prev) ->
                val delta = cur - prev
                val sign = if (delta >= 0) "+" else ""
                val trailing = if (prev > 0) "去年 ${String.format("%.1f%%", prev)} ($sign${String.format("%.1f%%", delta)})" else null
                HBarRow(
                    name = b,
                    segments = listOf(BarSegment("去年同期", prev), BarSegment("實際佔床率", cur)),
                    trailing = trailing
                )
            }
            return HBarData(hbarRows, overlap = true, targetLine = 85.0)
        } else {
            return HBarData(items.map { HBarRow(it.first, listOf(BarSegment("實際佔床率", it.second))) }, targetLine = 85.0)
        }
    }

    /** 實際開床率月趨勢(依院區，%)，含去年同期虛線。計算方式：實際床數 / 登記床數。 */
    fun branchBedOpenRateMonthly(f: Filters, cats: List<String> = emptyList()): LineChartData {
        val (w, p) = whereFor(f, false, 0)
        val (cc, cp) = catCond(cats)
        val where = if (cc.isEmpty()) w else "$w AND $cc"
        val branchCol = if (f.showHospitalTotal) "'全院' AS branch_name" else "branch_name"
        val groupCols = if (f.showHospitalTotal) "year, month" else "year, month, branch_name"
        val sql = """SELECT year, month, $branchCol,
            ROUND(SUM(CAST(actual_open_beds AS REAL)) * 100.0 / NULLIF(SUM(CAST(registered_beds AS REAL)), 0), 1)
            FROM bed_type_service WHERE $where GROUP BY $groupCols"""
        val yoySql = if (f.showYoy) {
            val (wy, _) = whereFor(f, false, -1)
            val whereY = if (cc.isEmpty()) wy else "$wy AND $cc"
            """SELECT year, month, $branchCol,
                ROUND(SUM(CAST(actual_open_beds AS REAL)) * 100.0 / NULLIF(SUM(CAST(registered_beds AS REAL)), 0), 1)
                FROM bed_type_service WHERE $whereY GROUP BY $groupCols"""
        } else null
        val yoyParams = if (f.showYoy) {
            val (_, py) = whereFor(f, false, -1)
            arrayOf(*py, *cp)
        } else emptyArray()
        return buildLine(sql, arrayOf(*p, *cp), groupColIdx = 2, valueIdxs = intArrayOf(3),
            yoySql = yoySql, yoyParams = yoyParams)
    }

    /** 各院區實際開床率(單月橫條：半透明當月登記床數，實色實開床數，後方顯示開床率，不必顯示去年同期)。 */
    fun branchBedOpenRateBar(f: Filters, cats: List<String> = emptyList()): HBarData {
        val isBranchTotal = f.showHospitalTotal
        val selBranch = if (isBranchTotal) "'全院' AS branch_name" else "branch_name"
        val grpBranch = if (isBranchTotal) "" else "GROUP BY branch_name"
        val (w, p) = whereFor(f, false, 0)
        val (cc, cp) = catCond(cats)
        val where = if (cc.isEmpty()) w else "$w AND $cc"
        val whereClause = if (!isBranchTotal) "$where AND branch_name IS NOT NULL AND branch_name != ''" else where
        val rows = db.query(
            "SELECT $selBranch, SUM(CAST(registered_beds AS REAL)), SUM(CAST(actual_open_beds AS REAL)) " +
                "FROM bed_type_service WHERE $whereClause $grpBranch",
            arrayOf(*p, *cp)
        )
        val items = rows.mapNotNull { r ->
            val b = r.getOrNull(0)?.toString() ?: return@mapNotNull null
            val reg = num(r.getOrNull(1)) ?: 0.0
            val open = num(r.getOrNull(2)) ?: 0.0
            if (reg > 0) Triple(b, reg, open) else null
        }.sortedByDescending { it.second }

        val hbarRows = items.map { (b, reg, open) ->
            val rate = if (reg > 0) open / reg * 100.0 else 0.0
            HBarRow(
                name = b,
                segments = listOf(
                    BarSegment("登記床數", reg),
                    BarSegment("實開床數", open)
                ),
                trailing = String.format("開床率 %.1f%%", rate)
            )
        }
        return HBarData(hbarRows, overlap = true)
    }

    data class BranchBedCategoryHeatmap(
        val branchName: String,
        val latestYm: Pair<Int, Int>?,
        val categories: List<Pair<String, Double>> // (category, rate)
    )

    data class BedStationOccDetail(
        val branch: String,
        val nursingStation: String,
        val occupancyRate: Double?, // % e.g. 95.2, null if no valid occupancy rate recorded
        val openBeds: Double,
        val registeredBeds: Double,
        val inpatientDays: Double = 0.0,
        val openBedDays: Double = 0.0
    ) {
        val openRate: Double
            get() = if (registeredBeds > 0) (openBeds / registeredBeds * 100.0) else 0.0
    }

    /** 某某院區病床類別實際佔床率（％）熱力圖資料：最新年月佔床率。若 excludeOther=true，則排除「其他」與「產後護理之家」。若篩選不含該院區則不包含。 */
    fun branchBedCategoryHeatmaps(
        f: Filters,
        cats: List<String> = emptyList(),
        excludeOther: Boolean = true
    ): List<BranchBedCategoryHeatmap> {
        val ym = bedLatestYm(f) ?: return emptyList()
        val (y, m) = ym
        val (cc, cp) = catCond(cats)
        val catFilter = if (cc.isNotEmpty()) "AND $cc" else ""
        val otherFilter = if (excludeOther) {
            "AND major_category != '其他' AND category != '其他' AND category NOT LIKE '%產後護理之家%'"
        } else ""

        val result = mutableListOf<BranchBedCategoryHeatmap>()

        if (f.showHospitalTotal) {
            val totalRows = db.query(
                "SELECT category, ${bedActualOccSql()} FROM bed_type_service " +
                    "WHERE year=? AND month=? AND category IS NOT NULL AND category != '' $otherFilter $catFilter " +
                    "GROUP BY category",
                arrayOf(y.toString(), m.toString(), *cp)
            )
            val totalCats = totalRows.mapNotNull { r ->
                val c = r[0]?.toString() ?: return@mapNotNull null
                val rate = (num(r[1]) ?: 0.0) * 100.0
                c to rate
            }.sortedByDescending { it.second }
            if (totalCats.isNotEmpty()) {
                result.add(BranchBedCategoryHeatmap("全院", ym, totalCats))
            }
        }

        val branchFilter = if (f.branches.isNotEmpty()) {
            "AND branch_name IN (${f.branches.joinToString(",") { "?" }})"
        } else ""
        val branchParams = if (f.branches.isNotEmpty()) {
            arrayOf(y.toString(), m.toString(), *cp, *f.branches.toTypedArray())
        } else {
            arrayOf(y.toString(), m.toString(), *cp)
        }

        val rows = db.query(
            "SELECT branch_name, category, ${bedActualOccSql()} FROM bed_type_service " +
                "WHERE year=? AND month=? AND category IS NOT NULL AND category != '' $otherFilter $catFilter $branchFilter " +
                "GROUP BY branch_name, category",
            branchParams
        )

        val byBranch = rows.groupBy { it[0]?.toString() ?: "" }
        val sortedBranches = byBranch.keys.filter { it.isNotEmpty() }.sorted()
        for (b in sortedBranches) {
            val bRows = byBranch[b] ?: continue
            val catList = bRows.mapNotNull { r ->
                val c = r[1]?.toString() ?: return@mapNotNull null
                val rate = (num(r[2]) ?: 0.0) * 100.0
                c to rate
            }.sortedByDescending { it.second }
            if (catList.isNotEmpty()) {
                result.add(BranchBedCategoryHeatmap(b, ym, catList))
            }
        }

        return result
    }

    /** 某院區某病床類別之各護理站佔床率明細（依佔床率由大到小排序）。若 branch 為 "全院"，則列出所有院區之該類別護理站。 */
    fun bedCategoryStations(
        branch: String,
        category: String,
        ym: Pair<Int, Int>? = null
    ): List<BedStationOccDetail> {
        val targetYm = ym ?: run {
            val r = db.query(
                "SELECT year, month FROM bed_type_service ORDER BY CAST(year AS INTEGER) DESC, CAST(month AS INTEGER) DESC LIMIT 1"
            ).firstOrNull() ?: return emptyList()
            val y = r[0]?.toString()?.toIntOrNull() ?: return emptyList()
            val m = r[1]?.toString()?.toIntOrNull() ?: return emptyList()
            y to m
        }
        val (y, m) = targetYm

        val isAll = branch == "全院"
        val branchCond = if (!isAll) "AND branch_name = ?" else ""
        val params = if (!isAll) {
            arrayOf<Any?>(y.toString(), m.toString(), category, branch)
        } else {
            arrayOf<Any?>(y.toString(), m.toString(), category)
        }

        val rows = db.query(
            "SELECT branch_name, nursing_station, ${bedActualOccSql()}, " +
                "SUM(CAST(actual_open_beds AS REAL)), SUM(CAST(registered_beds AS REAL)) " +
                "FROM bed_type_service " +
                "WHERE year = ? AND month = ? AND category = ? " +
                "AND nursing_station IS NOT NULL AND nursing_station != '' $branchCond " +
                "GROUP BY branch_name, nursing_station " +
                "ORDER BY ${bedActualOccSql()} DESC, nursing_station ASC",
            params
        )

        return rows.mapNotNull { r ->
            val b = r[0]?.toString() ?: return@mapNotNull null
            val st = r[1]?.toString() ?: return@mapNotNull null
            val occ = num(r[2])?.let { it * 100.0 }
            val open = num(r[3]) ?: 0.0
            val reg = num(r[4]) ?: 0.0
            BedStationOccDetail(
                branch = b,
                nursingStation = st,
                occupancyRate = occ,
                openBeds = open,
                registeredBeds = reg
            )
        }
    }

    private fun tableExists(name: String): Boolean {
        return try {
            db.query(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name=?",
                arrayOf<Any?>(name)
            ).firstOrNull()?.getOrNull(0)?.toString()?.toIntOrNull()?.let { it > 0 } ?: false
        } catch (_: Exception) {
            false
        }
    }

    /** 篩選區間內差額病床資料的最新 (年, 月)。 */
    fun diffBedLatestYm(f: Filters): Pair<Int, Int>? {
        if (!tableExists("diff_bed_service")) return null
        val parts = mutableListOf<String>()
        val params = mutableListOf<Any?>()
        if (f.years.isNotEmpty()) {
            parts.add("year IN (${f.years.joinToString(",") { "?" }})")
            params.addAll(f.years)
        }
        if (f.months.isNotEmpty()) {
            parts.add("month IN (${f.months.joinToString(",") { "?" }})")
            params.addAll(f.months)
        }
        val w = if (parts.isNotEmpty()) parts.joinToString(" AND ") else "1=1"
        val r = db.query(
            "SELECT year, month FROM diff_bed_service WHERE $w " +
                "ORDER BY CAST(year AS INTEGER) DESC, CAST(month AS INTEGER) DESC LIMIT 1",
            params.toTypedArray()
        ).firstOrNull() ?: return null
        val y = r[0]?.toString()?.toIntOrNull() ?: return null
        val m = r[1]?.toString()?.toIntOrNull() ?: return null
        return y to m
    }

    /** 差額病床各院區各類別實際佔床率（％）熱力圖資料：最新年月佔床率。以住院人日(K)除以實開床天數(Q)計算。 */
    fun branchDiffBedCategoryHeatmaps(f: Filters): List<BranchBedCategoryHeatmap> {
        if (!tableExists("diff_bed_service")) return emptyList()
        val ym = diffBedLatestYm(f) ?: return emptyList()
        val (y, m) = ym
        val result = mutableListOf<BranchBedCategoryHeatmap>()

        // 全院合計
        if (f.showHospitalTotal) {
            val totalRows = db.query(
                "SELECT category, " +
                    "SUM(CAST(inpatient_days AS REAL)), " +
                    "SUM(CAST(open_bed_days AS REAL)) " +
                    "FROM diff_bed_service " +
                    "WHERE year=? AND month=? AND category IS NOT NULL AND category != '' " +
                    "GROUP BY category " +
                    "HAVING (SUM(CAST(open_bed_days AS REAL)) > 0 OR SUM(CAST(inpatient_days AS REAL)) > 0)",
                arrayOf<Any?>(y.toString(), m.toString())
            )
            val totalCats = totalRows.mapNotNull { r ->
                val c = r[0]?.toString() ?: return@mapNotNull null
                val k = num(r[1]) ?: 0.0
                val q = num(r[2]) ?: 0.0
                val rate = if (q > 0) (k / q * 100.0) else 0.0
                c to rate
            }.sortedByDescending { it.second }
            if (totalCats.isNotEmpty()) {
                result.add(BranchBedCategoryHeatmap("全院", ym, totalCats))
            }
        }

        val branchFilter = if (f.branches.isNotEmpty()) {
            "AND branch_name IN (${f.branches.joinToString(",") { "?" }})"
        } else ""
        val branchParams = if (f.branches.isNotEmpty()) {
            arrayOf<Any?>(y.toString(), m.toString(), *f.branches.toTypedArray())
        } else {
            arrayOf<Any?>(y.toString(), m.toString())
        }

        val rows = db.query(
            "SELECT branch_name, category, " +
                "SUM(CAST(inpatient_days AS REAL)), " +
                "SUM(CAST(open_bed_days AS REAL)) " +
                "FROM diff_bed_service " +
                "WHERE year=? AND month=? AND category IS NOT NULL AND category != '' $branchFilter " +
                "GROUP BY branch_name, category " +
                "HAVING (SUM(CAST(open_bed_days AS REAL)) > 0 OR SUM(CAST(inpatient_days AS REAL)) > 0)",
            branchParams
        )

        val byBranch = rows.groupBy { it[0]?.toString() ?: "" }
        val sortedBranches = byBranch.keys.filter { it.isNotEmpty() }.sorted()
        for (b in sortedBranches) {
            val bRows = byBranch[b] ?: continue
            val catList = bRows.mapNotNull { r ->
                val c = r[1]?.toString() ?: return@mapNotNull null
                val k = num(r[2]) ?: 0.0
                val q = num(r[3]) ?: 0.0
                val rate = if (q > 0) (k / q * 100.0) else 0.0
                c to rate
            }.sortedByDescending { it.second }
            if (catList.isNotEmpty()) {
                result.add(BranchBedCategoryHeatmap(b, ym, catList))
            }
        }

        return result
    }

    /** 某院區差額病床某類別之各護理站佔床率明細（依佔床率由大到小排序）。若 branch 為 "全院"，則列出所有院區之該類別護理站。 */
    fun diffBedCategoryStations(
        branch: String,
        category: String,
        ym: Pair<Int, Int>? = null
    ): List<BedStationOccDetail> {
        if (!tableExists("diff_bed_service")) return emptyList()
        val targetYm = ym ?: run {
            val r = db.query(
                "SELECT year, month FROM diff_bed_service ORDER BY CAST(year AS INTEGER) DESC, CAST(month AS INTEGER) DESC LIMIT 1"
            ).firstOrNull() ?: return emptyList()
            val y = r[0]?.toString()?.toIntOrNull() ?: return emptyList()
            val m = r[1]?.toString()?.toIntOrNull() ?: return emptyList()
            y to m
        }
        val (y, m) = targetYm

        val isAll = branch == "全院"
        val branchCond = if (!isAll) "AND branch_name = ?" else ""
        val params = if (!isAll) {
            arrayOf<Any?>(y.toString(), m.toString(), category, branch)
        } else {
            arrayOf<Any?>(y.toString(), m.toString(), category)
        }

        val rows = db.query(
            "SELECT branch_name, nursing_station_name, " +
                "SUM(CAST(inpatient_days AS REAL)), " +
                "SUM(CAST(open_bed_days AS REAL)), " +
                "SUM(CAST(open_beds AS REAL)), " +
                "SUM(CAST(registered_beds AS REAL)) " +
                "FROM diff_bed_service " +
                "WHERE year = ? AND month = ? AND category = ? " +
                "AND nursing_station_name IS NOT NULL AND nursing_station_name != '' $branchCond " +
                "GROUP BY branch_name, nursing_station_name " +
                "HAVING (SUM(CAST(open_bed_days AS REAL)) > 0 OR SUM(CAST(inpatient_days AS REAL)) > 0) " +
                "ORDER BY (CASE WHEN SUM(CAST(open_bed_days AS REAL)) > 0 THEN SUM(CAST(inpatient_days AS REAL)) / SUM(CAST(open_bed_days AS REAL)) ELSE 0 END) DESC, nursing_station_name ASC",
            params
        )

        return rows.mapNotNull { r ->
            val b = r[0]?.toString() ?: return@mapNotNull null
            val st = r[1]?.toString() ?: return@mapNotNull null
            val k = num(r[2]) ?: 0.0
            val q = num(r[3]) ?: 0.0
            val open = num(r[4]) ?: 0.0
            val reg = num(r[5]) ?: 0.0
            val occ = if (q > 0) (k / q * 100.0) else 0.0
            BedStationOccDetail(
                branch = b,
                nursingStation = st,
                occupancyRate = occ,
                openBeds = open,
                registeredBeds = reg,
                inpatientDays = k,
                openBedDays = q
            )
        }
    }

    /** 各院區實際佔床率(橫條，目標 85%，由大到小；可僅顯示最新年月)。 */
    fun bedBranchOcc(f: Filters, cats: List<String>, latestOnly: Boolean = false): HBarData {
        val (w, p) = whereFor(f, false, 0)
        val (cc, cp) = catCond(cats)
        val (yc, yp) = ymCond(f, latestOnly)
        var where = if (cc.isEmpty()) w else "$w AND $cc"
        if (yc.isNotEmpty()) where += " AND $yc"
        val params = arrayOf(*p, *cp, *yp)
        val rows = db.query(
            "SELECT branch_name, ${bedActualOccSql()}, SUM(CAST(actual_open_beds AS REAL)) " +
                "FROM bed_type_service WHERE $where GROUP BY branch_name", params)
        val items = rows.mapNotNull { r ->
            val occ = num(r[1])?.times(100.0) ?: 0.0
            val open = num(r[2]) ?: 0.0
            if (open > 0) r[0]?.toString()?.let { it to occ } else null
        }.sortedByDescending { it.second }
        return HBarData(items.map { HBarRow(it.first, listOf(BarSegment("實際佔床率", it.second))) },
            targetLine = 85.0)
    }

    /** 各院區 登記 vs 實際開床數(重疊橫條，尾端開床率；可僅顯示最新年月)。 */
    fun bedBranchRegVsOpen(f: Filters, cats: List<String>, latestOnly: Boolean = false): HBarData {
        val (w, p) = whereFor(f, false, 0)
        val (cc, cp) = catCond(cats)
        val (yc, yp) = ymCond(f, latestOnly)
        var where = if (cc.isEmpty()) w else "$w AND $cc"
        if (yc.isNotEmpty()) where += " AND $yc"
        val params = arrayOf(*p, *cp, *yp)
        val rows = db.query(
            "SELECT branch_name, SUM(CAST(registered_beds AS REAL)), SUM(CAST(actual_open_beds AS REAL)) " +
                "FROM bed_type_service WHERE $where GROUP BY branch_name", params)
        val items = rows.mapNotNull { r ->
            val reg = num(r[1]) ?: 0.0
            val open = num(r[2]) ?: 0.0
            if (reg > 0) r[0]?.toString()?.let { it to (reg to open) } else null
        }.sortedByDescending { it.second.first }
        return HBarData(
            items.map { (b, v) ->
                val occ = if (v.first > 0) v.second / v.first * 100.0 else 0.0
                HBarRow(b, listOf(BarSegment("登記床數", v.first), BarSegment("實際開床數", v.second)),
                    trailing = if (v.first > 0) String.format("開床率 %.1f%%", occ) else null)
            },
            overlap = true
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
            "SELECT nursing_station, ${bedActualOccSql()}, SUM(CAST(actual_open_beds AS REAL)) " +
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
    fun bedPivot(f: Filters, cats: List<String>, byStation: Boolean = false, ns: List<String> = emptyList(), latestOnly: Boolean = false): TableData {
        val (w, p) = whereFor(f, false, 0)
        val (cc, cp) = catCond(cats)
        val (yc, yp) = ymCond(f, latestOnly)
        var where = if (cc.isEmpty()) w else "$w AND $cc"
        if (yc.isNotEmpty()) where += " AND $yc"
        val params = mutableListOf<Any?>()
        params.addAll(p)
        params.addAll(cp)
        params.addAll(yp)
        if (byStation && ns.isNotEmpty()) {
            where += " AND nursing_station IN (${ns.joinToString(",") { "?" }})"
            params.addAll(ns)
        }
        val idxCol = if (byStation) "nursing_station" else "branch_name"
        val rows = db.query(
            "SELECT $idxCol, category, ${bedActualOccSql()} " +
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

    /** 去年同期佔床率對照表（可僅以最新年月與去年同月比較）。 */
    fun bedYoyCompare(f: Filters, cats: List<String>, latestOnly: Boolean = false): List<BedYoyRow> {
        val (w, p) = whereFor(f, false, 0)
        if (!f.showYoy) return emptyList()
        val (cc, cp) = catCond(cats)
        val (yc, yp) = ymCond(f, latestOnly)
        var wc = if (cc.isEmpty()) w else "$w AND $cc"
        if (yc.isNotEmpty()) wc += " AND $yc"
        val params = arrayOf(*p, *cp, *yp)
        val cur = db.query(
            "SELECT category, ${bedActualOccSql()} FROM bed_type_service " +
                "WHERE $wc GROUP BY category", params)
        // 去年同月(僅在最新年月模式下才有明確單月)；累計模式沿用去年同區間
        val prev = if (latestOnly) {
            val lm = bedLatestYm(f)
            if (lm == null) emptyList() else {
                val (y, m) = lm
                val (wpp, ppp) = whereFor(f, false, -1)
                if (wpp.isEmpty()) emptyList() else db.query(
                    "SELECT category, ${bedActualOccSql()} FROM bed_type_service " +
                        "WHERE $wpp${if (cc.isEmpty()) "" else " AND $cc"} AND year=? AND month=? GROUP BY category",
                    arrayOf(*ppp, *cp, (y - 1).toString(), m.toString()))
            }
        } else {
            val (wy, py) = whereFor(f, false, -1)
            if (wy.isEmpty()) emptyList() else db.query(
                "SELECT category, ${bedActualOccSql()} FROM bed_type_service " +
                    "WHERE $wy${if (cc.isEmpty()) "" else " AND $cc"} GROUP BY category", arrayOf(*py, *cp))
        }
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
    /** 院外門診部服務量趨勢(依院區)，含去年同期虛線。 */
    fun offsiteBranchMonthly(f: Filters): LineChartData {
        val (w, p) = whereFor(f, false, 0)
        val branchCol = if (f.showHospitalTotal) "'全院' AS branch_name" else "branch_name"
        val groupCols = if (f.showHospitalTotal) "year, month" else "year, month, branch_name"
        val sql = """SELECT year, month, $branchCol, SUM(CAST(total AS REAL))
            FROM offsite_clinic_service WHERE $w GROUP BY $groupCols"""
        val yoySql = if (f.showYoy) {
            val (wy, _) = whereFor(f, false, -1)
            """SELECT year, month, $branchCol, SUM(CAST(total AS REAL))
                FROM offsite_clinic_service WHERE $wy GROUP BY $groupCols"""
        } else null
        val yoyParams = if (f.showYoy) {
            val (_, py) = whereFor(f, false, -1)
            py
        } else emptyArray()
        return buildLine(sql, p, groupColIdx = 2, valueIdxs = intArrayOf(3),
            yoySql = yoySql, yoyParams = yoyParams)
    }

    /** 各院區院外門診部服務量(橫條，降冪；含去年同期半透明比對)。 */
    fun offsiteBranchBar(f: Filters): HBarData =
        buildYoyHBar(f, "offsite_clinic_service", "branch_name", "total", "門診人次")

    data class OffsiteClinicStat(
        val clinicName: String,
        val medical: Double,
        val health: Double,
        val total: Double,
        val totalPrior: Double?,
        val deltaPct: Double?
    )

    /** 取得指定院區下各院外門診部明細 (醫療/保健/合計，含去年同期比較)。 */
    fun offsiteBranchClinics(f: Filters, branch: String): List<OffsiteClinicStat> {
        val (w, p) = whereFor(f, false, 0)
        if (w.isEmpty()) return emptyList()
        val (wy, py) = if (f.showYoy) whereFor(f, false, -1) else "" to emptyArray()
        val isTotal = branch == "全院"
        val branchCond = if (isTotal) "" else "AND branch_name=?"
        val curParams = if (isTotal) p else arrayOf(*p, branch)
        val curRows = db.query(
            "SELECT clinic_name, SUM(CAST(medical_visit AS REAL)), SUM(CAST(health_visit AS REAL)), SUM(CAST(total AS REAL)) " +
                "FROM offsite_clinic_service WHERE $w $branchCond GROUP BY clinic_name",
            curParams
        )
        val curMap = curRows.associate {
            (it[0]?.toString() ?: "") to Triple(num(it[1]) ?: 0.0, num(it[2]) ?: 0.0, num(it[3]) ?: 0.0)
        }
        val priorMap = if (f.showYoy && wy.isNotEmpty()) {
            val priorParams = if (isTotal) py else arrayOf(*py, branch)
            val pRows = db.query(
                "SELECT clinic_name, SUM(CAST(total AS REAL)) " +
                    "FROM offsite_clinic_service WHERE $wy $branchCond GROUP BY clinic_name",
                priorParams
            )
            pRows.associate { (it[0]?.toString() ?: "") to (num(it[1]) ?: 0.0) }
        } else emptyMap()

        val allClinics = (curMap.keys + priorMap.keys).filter { it.isNotEmpty() }.distinct()
        return allClinics.map { c ->
            val (med, health, tot) = curMap[c] ?: Triple(0.0, 0.0, 0.0)
            val prevTot = priorMap[c]
            val deltaPct = if (prevTot != null && prevTot > 0) (tot - prevTot) / prevTot * 100.0 else null
            OffsiteClinicStat(c, med, health, tot, prevTot, deltaPct)
        }.sortedByDescending { it.total }
    }

    /** 洗腎人次月趨勢(依院區)，含去年同期虛線。 */
    fun dialysisBranchMonthly(f: Filters): LineChartData {
        val (w, p) = whereFor(f, false, 0)
        val branchCol = if (f.showHospitalTotal) "'全院' AS branch_name" else "branch_name"
        val groupCols = if (f.showHospitalTotal) "year, month" else "year, month, branch_name"
        val sql = """SELECT year, month, $branchCol, SUM(CAST(dialysis_count AS REAL))
            FROM accounting_report WHERE $w GROUP BY $groupCols"""
        val yoySql = if (f.showYoy) {
            val (wy, _) = whereFor(f, false, -1)
            """SELECT year, month, $branchCol, SUM(CAST(dialysis_count AS REAL))
                FROM accounting_report WHERE $wy GROUP BY $groupCols"""
        } else null
        val yoyParams = if (f.showYoy) {
            val (_, py) = whereFor(f, false, -1)
            py
        } else emptyArray()
        return buildLine(sql, p, groupColIdx = 2, valueIdxs = intArrayOf(3),
            yoySql = yoySql, yoyParams = yoyParams)
    }

    /** 各院區洗腎人次(橫條，降冪；含去年同期半透明比對)。 */
    fun dialysisBranchBar(f: Filters): HBarData =
        buildYoyHBar(f, "accounting_report", "branch_name", "dialysis_count", "洗腎人次")

    /** 健檢人次月趨勢(依院區)，含去年同期虛線。 */
    fun checkupBranchMonthly(f: Filters): LineChartData {
        val (w, p) = whereFor(f, false, 0)
        val branchCol = if (f.showHospitalTotal) "'全院' AS branch_name" else "branch_name"
        val groupCols = if (f.showHospitalTotal) "year, month" else "year, month, branch_name"
        val sql = """SELECT year, month, $branchCol, SUM(CAST(opd_checkup_count AS REAL))
            FROM accounting_report WHERE $w GROUP BY $groupCols"""
        val yoySql = if (f.showYoy) {
            val (wy, _) = whereFor(f, false, -1)
            """SELECT year, month, $branchCol, SUM(CAST(opd_checkup_count AS REAL))
                FROM accounting_report WHERE $wy GROUP BY $groupCols"""
        } else null
        val yoyParams = if (f.showYoy) {
            val (_, py) = whereFor(f, false, -1)
            py
        } else emptyArray()
        return buildLine(sql, p, groupColIdx = 2, valueIdxs = intArrayOf(3),
            yoySql = yoySql, yoyParams = yoyParams)
    }

    /** 各院區健檢人次(橫條，降冪；含去年同期半透明比對)。 */
    fun checkupBranchBar(f: Filters): HBarData =
        buildYoyHBar(f, "accounting_report", "branch_name", "opd_checkup_count", "健檢人次")

    /** 手術人次月趨勢(依院區，門診手術+住院手術)，含去年同期虛線。 */
    fun surgeryBranchMonthly(f: Filters): LineChartData {
        val (w, p) = whereFor(f, false, 0)
        val branchCol = if (f.showHospitalTotal) "'全院' AS branch_name" else "branch_name"
        val groupCols = if (f.showHospitalTotal) "year, month" else "year, month, branch_name"
        val valExpr = "(SUM(CAST(surgery_opd_count AS REAL)) + SUM(CAST(surgery_admission_count AS REAL)))"
        val sql = """SELECT year, month, $branchCol, $valExpr
            FROM ops_management_indicators WHERE $w GROUP BY $groupCols"""
        val yoySql = if (f.showYoy) {
            val (wy, _) = whereFor(f, false, -1)
            """SELECT year, month, $branchCol, $valExpr
                FROM ops_management_indicators WHERE $wy GROUP BY $groupCols"""
        } else null
        val yoyParams = if (f.showYoy) {
            val (_, py) = whereFor(f, false, -1)
            py
        } else emptyArray()
        return buildLine(sql, p, groupColIdx = 2, valueIdxs = intArrayOf(3),
            yoySql = yoySql, yoyParams = yoyParams)
    }

    /** 各院區手術人次(橫條，降冪；含去年同期半透明比對)。 */
    fun surgeryBranchBar(f: Filters): HBarData =
        buildYoyHBar(f, "ops_management_indicators", "branch_name", "surgery_opd_count + surgery_admission_count", "手術人次")

    /** 生產人次月趨勢(依院區)，含去年同期虛線。 */
    fun deliveryBranchMonthly(f: Filters): LineChartData {
        val (w, p) = whereFor(f, false, 0)
        val branchCol = if (f.showHospitalTotal) "'全院' AS branch_name" else "branch_name"
        val groupCols = if (f.showHospitalTotal) "year, month" else "year, month, branch_name"
        val sql = """SELECT year, month, $branchCol, SUM(CAST(delivery_count AS REAL))
            FROM ops_management_indicators WHERE $w GROUP BY $groupCols"""
        val yoySql = if (f.showYoy) {
            val (wy, _) = whereFor(f, false, -1)
            """SELECT year, month, $branchCol, SUM(CAST(delivery_count AS REAL))
                FROM ops_management_indicators WHERE $wy GROUP BY $groupCols"""
        } else null
        val yoyParams = if (f.showYoy) {
            val (_, py) = whereFor(f, false, -1)
            py
        } else emptyArray()
        return buildLine(sql, p, groupColIdx = 2, valueIdxs = intArrayOf(3),
            yoySql = yoySql, yoyParams = yoyParams)
    }

    /** 各院區生產人次(橫條，降冪；含去年同期半透明比對)。 */
    fun deliveryBranchBar(f: Filters): HBarData =
        buildYoyHBar(f, "ops_management_indicators", "branch_name", "delivery_count", "生產人次")

    /** 總收入趨勢(依院區，門診收入+住院收入)，含去年同期虛線。 */
    fun incomeTotalBranchMonthly(f: Filters): LineChartData {
        val (w, p) = whereFor(f, false, 0)
        val branchCol = if (f.showHospitalTotal) "'全院' AS branch_name" else "branch_name"
        val groupCols = if (f.showHospitalTotal) "year, month" else "year, month, branch_name"
        val valExpr = "(SUM(CAST(total_income_opd AS REAL)) + SUM(CAST(total_income_admission AS REAL)))"
        val sql = """SELECT year, month, $branchCol, $valExpr
            FROM ops_management_indicators WHERE $w GROUP BY $groupCols"""
        val yoySql = if (f.showYoy) {
            val (wy, _) = whereFor(f, false, -1)
            """SELECT year, month, $branchCol, $valExpr
                FROM ops_management_indicators WHERE $wy GROUP BY $groupCols"""
        } else null
        val yoyParams = if (f.showYoy) {
            val (_, py) = whereFor(f, false, -1)
            py
        } else emptyArray()
        return buildLine(sql, p, groupColIdx = 2, valueIdxs = intArrayOf(3),
            yoySql = yoySql, yoyParams = yoyParams)
    }

    /** 各院區總收入(單月橫條，降冪；含去年同期半透明比對)。 */
    fun incomeTotalBranchBar(f: Filters): HBarData =
        buildYoyHBar(f, "ops_management_indicators", "branch_name", "total_income_opd + total_income_admission", "總收入", unit = "元", customFormatter = Fmt::money)

    /** 自費收入趨勢(依院區，門診自費+住院自費)，含去年同期虛線。 */
    fun incomeSelfBranchMonthly(f: Filters): LineChartData {
        val (w, p) = whereFor(f, false, 0)
        val branchCol = if (f.showHospitalTotal) "'全院' AS branch_name" else "branch_name"
        val groupCols = if (f.showHospitalTotal) "year, month" else "year, month, branch_name"
        val valExpr = "(SUM(CAST(self_pay_income_opd AS REAL)) + SUM(CAST(self_pay_income_admission AS REAL)))"
        val sql = """SELECT year, month, $branchCol, $valExpr
            FROM ops_management_indicators WHERE $w GROUP BY $groupCols"""
        val yoySql = if (f.showYoy) {
            val (wy, _) = whereFor(f, false, -1)
            """SELECT year, month, $branchCol, $valExpr
                FROM ops_management_indicators WHERE $wy GROUP BY $groupCols"""
        } else null
        val yoyParams = if (f.showYoy) {
            val (_, py) = whereFor(f, false, -1)
            py
        } else emptyArray()
        return buildLine(sql, p, groupColIdx = 2, valueIdxs = intArrayOf(3),
            yoySql = yoySql, yoyParams = yoyParams)
    }

    /** 各院區自費收入(單月橫條，降冪；含去年同期半透明比對)。 */
    fun incomeSelfBranchBar(f: Filters): HBarData =
        buildYoyHBar(f, "ops_management_indicators", "branch_name", "self_pay_income_opd + self_pay_income_admission", "自費收入", unit = "元", customFormatter = Fmt::money)

    /** 各院區總收入(累計，單位千元；重疊橫條含去年同期半透明比對)。 */
    fun branchTotalIncomeYoyBar(f: Filters): HBarData {
        val isBranchTotal = f.showHospitalTotal
        val selDim = if (isBranchTotal) "'全院' AS branch_name" else "branch_name"
        val grpDim = if (isBranchTotal) "" else "GROUP BY branch_name"
        val (w, p) = whereFor(f, false, 0)
        val whereClause = if (!isBranchTotal) "$w AND branch_name IS NOT NULL AND branch_name != ''" else w
        val curRows = db.query(
            "SELECT $selDim, (SUM(CAST(total_income_opd AS REAL)) + SUM(CAST(total_income_admission AS REAL))) / 1000.0 " +
                "FROM ops_management_indicators WHERE $whereClause $grpDim", p)
        val curMap = curRows.associate { (it[0]?.toString() ?: "") to (num(it[1]) ?: 0.0) }

        val yoyMap = if (f.showYoy) {
            val (wy, py) = whereFor(f, false, -1)
            val whereY = if (!isBranchTotal) "$wy AND branch_name IS NOT NULL AND branch_name != ''" else wy
            val yRows = db.query(
                "SELECT $selDim, (SUM(CAST(total_income_opd AS REAL)) + SUM(CAST(total_income_admission AS REAL))) / 1000.0 " +
                    "FROM ops_management_indicators WHERE $whereY $grpDim", py)
            yRows.associate { (it[0]?.toString() ?: "") to (num(it[1]) ?: 0.0) }
        } else emptyMap()

        val allBranches = (curMap.keys + yoyMap.keys).filter { it.isNotEmpty() }.distinct()
        val items = allBranches.map { b ->
            val cur = curMap[b] ?: 0.0
            val prev = yoyMap[b] ?: 0.0
            Triple(b, cur, prev)
        }.filter { it.second > 0 || it.third > 0 }.sortedByDescending { it.second }

        if (f.showYoy && yoyMap.isNotEmpty()) {
            val rows = items.map { (b, cur, prev) ->
                val deltaPct = if (prev > 0) (cur - prev) / prev * 100.0 else null
                val sign = if ((deltaPct ?: 0.0) >= 0) "+" else ""
                val trailing = if (deltaPct != null) {
                    "去年 ${Fmt.moneyK(prev)} ($sign${String.format("%.1f%%", deltaPct)})"
                } else if (prev > 0) {
                    "去年 ${Fmt.moneyK(prev)}"
                } else null
                HBarRow(
                    name = b,
                    segments = listOf(
                        BarSegment("去年同期", prev),
                        BarSegment("總收入", cur)
                    ),
                    trailing = trailing
                )
            }
            return HBarData(rows, overlap = true)
        } else {
            return HBarData(items.map { HBarRow(it.first, listOf(BarSegment("總收入", it.second))) })
        }
    }

    /** 各院區自費收入(累計，單位千元；重疊橫條含去年同期半透明比對)。 */
    fun branchSelfPayIncomeYoyBar(f: Filters): HBarData {
        val isBranchTotal = f.showHospitalTotal
        val selDim = if (isBranchTotal) "'全院' AS branch_name" else "branch_name"
        val grpDim = if (isBranchTotal) "" else "GROUP BY branch_name"
        val (w, p) = whereFor(f, false, 0)
        val whereClause = if (!isBranchTotal) "$w AND branch_name IS NOT NULL AND branch_name != ''" else w
        val curRows = db.query(
            "SELECT $selDim, (SUM(CAST(self_pay_income_opd AS REAL)) + SUM(CAST(self_pay_income_admission AS REAL))) / 1000.0 " +
                "FROM ops_management_indicators WHERE $whereClause $grpDim", p)
        val curMap = curRows.associate { (it[0]?.toString() ?: "") to (num(it[1]) ?: 0.0) }

        val yoyMap = if (f.showYoy) {
            val (wy, py) = whereFor(f, false, -1)
            val whereY = if (!isBranchTotal) "$wy AND branch_name IS NOT NULL AND branch_name != ''" else wy
            val yRows = db.query(
                "SELECT $selDim, (SUM(CAST(self_pay_income_opd AS REAL)) + SUM(CAST(self_pay_income_admission AS REAL))) / 1000.0 " +
                    "FROM ops_management_indicators WHERE $whereY $grpDim", py)
            yRows.associate { (it[0]?.toString() ?: "") to (num(it[1]) ?: 0.0) }
        } else emptyMap()

        val allBranches = (curMap.keys + yoyMap.keys).filter { it.isNotEmpty() }.distinct()
        val items = allBranches.map { b ->
            val cur = curMap[b] ?: 0.0
            val prev = yoyMap[b] ?: 0.0
            Triple(b, cur, prev)
        }.filter { it.second > 0 || it.third > 0 }.sortedByDescending { it.second }

        if (f.showYoy && yoyMap.isNotEmpty()) {
            val rows = items.map { (b, cur, prev) ->
                val deltaPct = if (prev > 0) (cur - prev) / prev * 100.0 else null
                val sign = if ((deltaPct ?: 0.0) >= 0) "+" else ""
                val trailing = if (deltaPct != null) {
                    "去年 ${Fmt.moneyK(prev)} ($sign${String.format("%.1f%%", deltaPct)})"
                } else if (prev > 0) {
                    "去年 ${Fmt.moneyK(prev)}"
                } else null
                HBarRow(
                    name = b,
                    segments = listOf(
                        BarSegment("去年同期", prev),
                        BarSegment("自費收入", cur)
                    ),
                    trailing = trailing
                )
            }
            return HBarData(rows, overlap = true)
        } else {
            return HBarData(items.map { HBarRow(it.first, listOf(BarSegment("自費收入", it.second))) })
        }
    }
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

    /** 各院外門診部服務量(醫療/保健 累計橫條，相連繪製)。 */
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
            items.map { HBarRow(it.first, listOf(BarSegment("醫療門診", it.second), BarSegment("保健門診", it.third))) }
        )
    }

    /** 洗腎/健檢(門診體檢)趨勢（移除住院體檢項目）。 */
    fun accMonthly(f: Filters): LineChartData {
        val (w, p) = whereFor(f, false, 0)
        val rows = db.query(
            "SELECT year, month, SUM(CAST(dialysis_count AS REAL)), SUM(CAST(opd_checkup_count AS REAL)), " +
                "SUM(CAST(admission_checkup_count AS REAL)) FROM accounting_report WHERE $w GROUP BY year, month", p)
        val xKeys = rows.map { ymSort(it[0], it[1]) to ymLabel(it[0], it[1]) }.distinct().sortedBy { it.first }
        val xLabels = xKeys.map { it.second }
        fun ser(idx: Int, name: String) = LineSeries(name,
            xKeys.map { k -> rows.firstOrNull { ymSort(it[0], it[1]) == k.first }?.let { num(it[idx]) } })
        return LineChartData(xLabels, listOf(ser(2, "洗腎人次"), ser(3, "健檢人次")))
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

    /** 收入趨勢：總收入(折線) + 自費(折線)，兩者呈現一致。 */
    fun incomeMonthly(f: Filters): Pair<LineChartData, LineChartData> {
        val (w, p) = whereFor(f, false, 0)
        val rows = db.query(
            "SELECT year, month, SUM(CAST(total_income_opd AS REAL)), SUM(CAST(total_income_admission AS REAL)), " +
                "SUM(CAST(self_pay_income_opd AS REAL)), SUM(CAST(self_pay_income_admission AS REAL)) " +
                "FROM ops_management_indicators WHERE $w GROUP BY year, month", p)
        val xKeys = rows.map { ymSort(it[0], it[1]) to ymLabel(it[0], it[1]) }.distinct().sortedBy { it.first }
        val xLabels = xKeys.map { it.second }
        fun ser(idx: Int, name: String) = LineSeries(name,
            xKeys.map { k -> rows.firstOrNull { ymSort(it[0], it[1]) == k.first }?.let { num(it[idx]) } })
        val total = LineChartData(xLabels, listOf(ser(2, "門診收入"), ser(3, "住院收入")))
        val selfpay = LineChartData(xLabels, listOf(ser(4, "門診自費"), ser(5, "住院自費")))
        return total to selfpay
    }

    /** 各院區總收入(門診+住院 累計橫條，由大到小)。 */
    fun branchIncome(f: Filters): HBarData {
        val (w, p) = whereFor(f, false, 0)
        val rows = db.query(
            "SELECT branch_name, SUM(CAST(total_income_opd AS REAL)), SUM(CAST(total_income_admission AS REAL)) " +
                "FROM ops_management_indicators WHERE $w GROUP BY branch_name", p)
        val items = rows.mapNotNull { r ->
            val o = num(r[1]) ?: 0.0
            val a = num(r[2]) ?: 0.0
            if (o + a > 0) r[0]?.toString()?.let { it to (o to a) } else null
        }.sortedByDescending { it.second.first + it.second.second }
        return HBarData(
            items.map {
                HBarRow(it.first, listOf(
                    BarSegment("門診收入", it.second.first),
                    BarSegment("住院收入", it.second.second)
                ))
            }
        )
    }

    /** 各院區自費收入(門診自費+住院自費 累計橫條，由大到小)。 */
    fun branchSelfPay(f: Filters): HBarData {
        val (w, p) = whereFor(f, false, 0)
        val rows = db.query(
            "SELECT branch_name, SUM(CAST(self_pay_income_opd AS REAL)), SUM(CAST(self_pay_income_admission AS REAL)) " +
                "FROM ops_management_indicators WHERE $w GROUP BY branch_name", p)
        val items = rows.mapNotNull { r ->
            val o = num(r[1]) ?: 0.0
            val a = num(r[2]) ?: 0.0
            if (o + a > 0) r[0]?.toString()?.let { it to (o to a) } else null
        }.sortedByDescending { it.second.first + it.second.second }
        return HBarData(
            items.map {
                HBarRow(it.first, listOf(
                    BarSegment("門診自費", it.second.first),
                    BarSegment("住院自費", it.second.second)
                ))
            }
        )
    }

    // ══════════ 其他服務分頁：指定月份各院區多指標明細(近三個月+去年同期) ═══════════
    data class BranchMetricRow(
        val branch: String,
        val metricNames: List<String>,
        val cur: List<Double>,          // 錨點月各指標值
        val trend3: List<List<Double?>>, // 各指標近三個月(含錨點月，由舊到新)
        val prior: List<Double?>         // 各指標去年同月
    )

    /**
     * 通用查詢：指定月份各院區多指標明細。
     * metrics = (顯示名, SQL SUM 算式)。branch 指定時僅回該院區。
     */
    fun branchMonthMetrics(
        f: Filters, ym: Int, table: String, metrics: List<Pair<String, String>>, branch: String? = null
    ): List<BranchMetricRow> {
        if (metrics.isEmpty()) return emptyList()
        val (w, p) = whereFor(f, false, 0)
        if (w.isEmpty()) return emptyList()
        val y = ym / 100
        val m = ym % 100
        val targets = listOf(monthBack(y, m, 2), monthBack(y, m, 1), y to m, (y - 1) to m)
        val ymc = ymInCond(targets)
        var where = "$w AND ${ymc.first}"
        if (branch != null) where += " AND branch_name=?"
        val cols = metrics.joinToString(", ") { it.second }
        val params = if (branch != null) arrayOf(*p, *ymc.second, branch) else arrayOf(*p, *ymc.second)
        val rows = db.query(
            "SELECT year, month, branch_name, $cols FROM $table WHERE $where GROUP BY year, month, branch_name",
            params)
        val byBranch = rows.groupBy { it[2]?.toString() ?: "" }
        fun keyOf(r: List<Any?>): Pair<Int, Int> =
            (r[0]?.toString()?.toIntOrNull() ?: 0) to (r[1]?.toString()?.toIntOrNull() ?: 0)
        val anchorKey = y to m
        return byBranch.mapNotNull { (b, rs) ->
            val curRow = rs.firstOrNull { keyOf(it) == anchorKey } ?: return@mapNotNull null
            val curVals = metrics.indices.map { i -> num(curRow[3 + i]) ?: 0.0 }
            val trend3 = metrics.indices.map { i ->
                (0..2).map { k ->
                    rs.firstOrNull { keyOf(it) == monthBack(y, m, 2 - k) }?.let { num(it[3 + i]) }
                }
            }
            val priorVals = metrics.indices.map { i ->
                rs.firstOrNull { keyOf(it) == ((y - 1) to m) }?.let { num(it[3 + i]) }
            }
            BranchMetricRow(b, metrics.map { it.first }, curVals, trend3, priorVals)
        }.sortedBy { it.branch }
    }

    // ══════════ TAB5 各院區病床占床率明細 ═══════════
    /** 佔床率明細（最新Only=僅最新年月；否則篩選區間累計）。 */
    fun bedDetail(f: Filters, latestOnly: Boolean = false): List<BedDetailRow> {
        val (w, p) = whereFor(f, false, 0)
        if (w.isEmpty()) return emptyList()
        val (yc, yp) = ymCond(f, latestOnly)
        var where = w
        if (yc.isNotEmpty()) where += " AND $yc"
        val params = arrayOf(*p, *yp)
        val rows = db.query(
            """SELECT COALESCE(branch_name,'未分類'), COALESCE(major_category,'未分類'),
               COALESCE(category,'未分類'), COALESCE(nursing_station,'未分類'),
               SUM(CAST(registered_beds AS REAL)), SUM(CAST(actual_open_beds AS REAL)),
               SUM(CAST(admission_days AS REAL)),
               ${bedRegOccSql()}, ${bedActualOccSql()}
               FROM bed_type_service WHERE $where
               GROUP BY branch_name, major_category, category, nursing_station""", params)
        val yoy = bedDetailYoy(f, latestOnly)
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

    private fun bedDetailYoy(f: Filters, latestOnly: Boolean = false): Map<List<Any?>, Pair<Double?, Double?>> {
        val (w, p) = whereFor(f, false, -1)
        if (w.isEmpty()) return emptyMap()
        val (cc, cp) = catCond(emptyList())
        // 最新年月模式：去年同月；累計模式：去年同篩選區間
        val (yc, yp) = if (latestOnly) {
            val lm = bedLatestYm(f)
            if (lm == null) "" to emptyArray() else {
                val (y, m) = lm
                "year=? AND month=?" to arrayOf((y - 1).toString(), m.toString())
            }
        } else "" to emptyArray()
        var where = w
        if (yc.isNotEmpty()) where += " AND $yc"
        val rows = db.query(
            """SELECT COALESCE(branch_name,'未分類'), COALESCE(major_category,'未分類'),
               COALESCE(category,'未分類'), COALESCE(nursing_station,'未分類'),
               ${bedRegOccSql()}, ${bedActualOccSql()}
               FROM bed_type_service WHERE $where
               GROUP BY branch_name, major_category, category, nursing_station""",
            arrayOf(*p, *yp))
        return rows.associate { r ->
            listOf(r[0], r[1], r[2], r[3]) to
                (num(r[4])?.times(100.0) to num(r[5])?.times(100.0))
        }
    }

    /** 床明細單列(近數月實際佔床率趨勢)供明細卡使用。 */
    data class BedMonthTrend(val ym: Int, val label: String, val actOcc: Double, val openBeds: Double)

    /** 某院區近 k 個月(由舊到新)實際佔床率趨勢與去年同期同月值。 */
    fun bedBranchTrend(
        f: Filters, cats: List<String>, branch: String, k: Int = 3
    ): Pair<List<BedMonthTrend>, Double?> {
        val (w, p) = whereFor(f, false, 0)
        val (cc, cp) = catCond(cats)
        var where = if (cc.isEmpty()) w else "$w AND $cc"
        if (where.isNotEmpty()) where += " AND branch_name=?" else where = "branch_name=?"
        val params = arrayOf(*p, *cp, branch)
        val rows = db.query(
            "SELECT year, month, ${bedActualOccSql()}, SUM(CAST(actual_open_beds AS REAL)) " +
                "FROM bed_type_service WHERE $where GROUP BY year, month " +
                "ORDER BY CAST(year AS INTEGER) DESC, CAST(month AS INTEGER) DESC LIMIT ?",
            arrayOf(*params, k.toString()))
        val trend = rows.reversed().mapNotNull { r ->
            val y = r[0]?.toString()?.toIntOrNull() ?: return@mapNotNull null
            val m = r[1]?.toString()?.toIntOrNull() ?: return@mapNotNull null
            val occ = num(r[2])?.times(100.0) ?: 0.0
            BedMonthTrend(y * 100 + m, ymLabel(y, m), occ, num(r[3]) ?: 0.0)
        }
        // 去年同期(以最新月之去年同月)
        val latest = rows.firstOrNull()
        val prior = latest?.let { r ->
            val y = r[0]?.toString()?.toIntOrNull() ?: return@let null
            val m = r[1]?.toString()?.toIntOrNull() ?: return@let null
            val (wy, wpp) = whereFor(f, false, -1)
            if (wy.isEmpty()) null else db.query(
                "SELECT ${bedActualOccSql()} FROM bed_type_service " +
                    "WHERE $wy${if (cc.isEmpty()) "" else " AND $cc"} AND branch_name=? AND year=? AND month=?",
                arrayOf(*wpp, *cp, branch, (y - 1).toString(), m.toString()))
                    .firstOrNull()?.let { q -> num(q[0])?.times(100.0) }
        }
        return trend to prior
    }

    /** 病床類別各院區明細（近三個月實際佔床率 + 去年同月），供去年同期表列點擊使用。 */
    data class BedCatBranchRow(
        val branch: String,
        val trend: List<BedMonthTrend>,
        val prior: Double?
    )

    fun bedCategoryBranchDetail(f: Filters, category: String): List<BedCatBranchRow> {
        val (w, p) = whereFor(f, false, 0)
        if (w.isEmpty()) return emptyList()
        val branches = db.query(
            "SELECT DISTINCT branch_name FROM bed_type_service WHERE $w AND category=? ORDER BY branch_name",
            arrayOf(*p, category)).map { it[0]?.toString() ?: "" }.filter { it.isNotEmpty() }
        return branches.mapNotNull { b ->
            val cur = db.query(
                "SELECT year, month, ${bedActualOccSql()}, SUM(CAST(actual_open_beds AS REAL)) " +
                    "FROM bed_type_service WHERE $w AND category=? AND branch_name=? GROUP BY year, month " +
                    "ORDER BY CAST(year AS INTEGER) DESC, CAST(month AS INTEGER) DESC LIMIT 3",
                arrayOf(*p, category, b))
            if (cur.isEmpty()) return@mapNotNull null
            val trend = cur.reversed().mapNotNull { r ->
                val y = r[0]?.toString()?.toIntOrNull() ?: return@mapNotNull null
                val m = r[1]?.toString()?.toIntOrNull() ?: return@mapNotNull null
                BedMonthTrend(y * 100 + m, ymLabel(y, m), (num(r[2]) ?: 0.0) * 100.0, num(r[3]) ?: 0.0)
            }
            val latest = cur.first()
            val ly = latest[0]?.toString()?.toIntOrNull() ?: return@mapNotNull null
            val lm = latest[1]?.toString()?.toIntOrNull() ?: return@mapNotNull null
            val (wp, pp) = whereFor(f, false, -1)
            val prior = if (wp.isEmpty()) null else db.query(
                "SELECT ${bedActualOccSql()} FROM bed_type_service " +
                    "WHERE $wp AND category=? AND branch_name=? AND year=? AND month=?",
                arrayOf(*pp, category, b, (ly - 1).toString(), lm.toString()))
                .firstOrNull()?.let { q -> num(q[0])?.times(100.0) }
            BedCatBranchRow(b, trend, prior)
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

    // ══════════ AI 分析（混淆資料生成） ═════════════
    /** 單一院區錨點月營運摘要（真實名稱，供本地混淆後再上傳 AI）。 */
    class BranchAnalysisSummary(
        val branch: String,
        val anchorLabel: String,
        val kpis: List<Triple<String, Double, Double?>>,   // 指標名, 本期, 去年同期
        val deptOpd: List<Pair<String, Double>>,
        val income: List<Pair<String, Double>>,
        val doctors: List<Pair<String, Double>>
    ) {
        fun toText(): String = buildString {
            appendLine("【院區】$branch")
            appendLine("【基準月份】$anchorLabel")
            appendLine("【營運概況】")
            kpis.forEach { (name, cur, prior) ->
                val yoy = if (prior != null && prior != 0.0) {
                    val d = (cur - prior) / prior * 100.0
                    if (d >= 0) "（去年同期 ${Fmt.int(prior)}，▲+${String.format("%.1f%%", d)}）"
                    else "（去年同期 ${Fmt.int(prior)}，▼${String.format("%.1f%%", d)}）"
                } else ""
                appendLine("- $name：${Fmt.int(cur)}$yoy")
            }
            appendLine("【科別門診人次 Top8】")
            deptOpd.forEach { (d, v) -> appendLine("- $d：${Fmt.int(v)} 人次") }
            appendLine("【當月收入結構】")
            income.forEach { (n, v) -> appendLine("- $n：${Fmt.money(v)}") }
            appendLine("【醫師服務量 Top5】")
            doctors.forEach { (n, v) -> appendLine("- $n：門診 ${Fmt.int(v)} 人次") }
        }
    }

    /** 單一院區最新月份營運摘要（含去年同期比較）。 */
    fun branchAnalysisSummary(branch: String): BranchAnalysisSummary? {
        val a = anchorYm(Filters(years = emptyList())) ?: return null
        val (y, m) = a
        val py = (y - 1).toString(); val ys = y.toString(); val ms = m.toString()

        fun row(sql: String, vararg params: String): List<Any?>? =
            db.query(sql, params.toList().toTypedArray()).firstOrNull()

        // 門診 KPI
        val opd = row(
            "SELECT SUM(CAST(opd_visit_count AS REAL)), SUM(CAST(er_visit AS REAL)), SUM(CAST(total_clinic_sessions AS REAL)) " +
                "FROM outpatient_service WHERE branch_name=? AND year=? AND month=?",
            branch, ys, ms)
        // 住院 KPI
        val ipd = row(
            "SELECT SUM(CAST(admission_count AS REAL)), SUM(CAST(admission_days AS REAL)) " +
                "FROM inpatient_service WHERE branch_name=? AND year=? AND month=?",
            branch, ys, ms)
        // 平均實際佔床率
        val occ = row(
            "SELECT AVG(CASE WHEN ${numGuard("actual_occupancy_rate")} THEN CAST(actual_occupancy_rate AS REAL) END) " +
                "FROM bed_type_service WHERE branch_name=? AND $BED_OCC_EXCLUDE_MAJOR_SQL AND year=? AND month=?",
            branch, ys, ms)
        // 收入結構
        val inc = row(
            "SELECT SUM(CAST(opd_nhi_income AS REAL)), SUM(CAST(opd_selfpay_income AS REAL)), " +
                "SUM(CAST(ipd_nhi_income AS REAL)), SUM(CAST(ipd_selfpay_income AS REAL)) " +
                "FROM physician_service WHERE branch_name=? AND year=? AND month=?",
            branch, ys, ms)
        // 去年同期
        val opdP = row(
            "SELECT SUM(CAST(opd_visit_count AS REAL)), SUM(CAST(er_visit AS REAL)), SUM(CAST(total_clinic_sessions AS REAL)) " +
                "FROM outpatient_service WHERE branch_name=? AND year=? AND month=?",
            branch, py, ms)
        val ipdP = row(
            "SELECT SUM(CAST(admission_count AS REAL)), SUM(CAST(admission_days AS REAL)) " +
                "FROM inpatient_service WHERE branch_name=? AND year=? AND month=?",
            branch, py, ms)
        val occP = row(
            "SELECT AVG(CASE WHEN ${numGuard("actual_occupancy_rate")} THEN CAST(actual_occupancy_rate AS REAL) END) " +
                "FROM bed_type_service WHERE branch_name=? AND $BED_OCC_EXCLUDE_MAJOR_SQL AND year=? AND month=?",
            branch, py, ms)

        val kpis = mutableListOf<Triple<String, Double, Double?>>()
        kpis.add(Triple("門診人次", num(opd?.get(0)) ?: 0.0, num(opdP?.get(0))))
        kpis.add(Triple("急診人次", num(opd?.get(1)) ?: 0.0, num(opdP?.get(1))))
        kpis.add(Triple("總診次", num(opd?.get(2)) ?: 0.0, num(opdP?.get(2))))
        kpis.add(Triple("住院人次", num(ipd?.get(0)) ?: 0.0, num(ipdP?.get(0))))
        kpis.add(Triple("住院人日", num(ipd?.get(1)) ?: 0.0, num(ipdP?.get(1))))
        val occV = num(occ?.get(0))?.let { it * 100.0 }
        val occPV = num(occP?.get(0))?.let { it * 100.0 }
        if (occV != null) kpis.add(Triple("平均實際佔床率", occV, occPV))

        // 科別門診 Top8
        val deptRows = db.query(
            "SELECT dept, SUM(CAST(opd_visit_count AS REAL)) FROM outpatient_service " +
                "WHERE branch_name=? AND year=? AND month=? AND dept IS NOT NULL AND dept != '' " +
                "GROUP BY dept ORDER BY 2 DESC LIMIT 8",
            arrayOf(branch, ys, ms))
        val deptOpd = deptRows.mapNotNull { r ->
            val v = num(r[1]) ?: 0.0
            if (v <= 0) null else (r[0].toString() to v)
        }
        // 醫師 Top5
        val docRows = db.query(
            "SELECT doctor_name, SUM(CAST(opd_visit_count AS REAL)) FROM physician_service " +
                "WHERE branch_name=? AND year=? AND month=? AND doctor_name IS NOT NULL AND doctor_name != '' " +
                "GROUP BY doctor_name ORDER BY 2 DESC LIMIT 5",
            arrayOf(branch, ys, ms))
        val doctors = docRows.mapNotNull { r ->
            val v = num(r[1]) ?: 0.0
            if (v <= 0) null else (r[0].toString() to v)
        }
        val income = listOf(
            "門診健保收入" to (num(inc?.get(0)) ?: 0.0),
            "門診自費收入" to (num(inc?.get(1)) ?: 0.0),
            "住院健保收入" to (num(inc?.get(2)) ?: 0.0),
            "住院自費收入" to (num(inc?.get(3)) ?: 0.0)
        ).filter { it.second > 0 }

        return BranchAnalysisSummary(
            branch = branch,
            anchorLabel = "$y 年${m.toString().padStart(2, '0')}月",
            kpis = kpis, deptOpd = deptOpd, income = income, doctors = doctors
        )
    }

    /** 供貼文混淆用的實體名稱字典（院區/科別/醫師/門診部）。 */
    class EntityDictionary(
        val branches: List<String>,
        val depts: List<String>,
        val doctors: List<String>,
        val clinics: List<String>
    )

    fun anonymizerDictionary(): EntityDictionary {
        // 院區順序固定（與樣本資料對照一致：忠孝→甲、中興→乙、和平→丙），其餘依筆畫排序
        val preferred = listOf("忠孝", "中興", "和平")
        val branchRows = db.query(
            """SELECT branch_name FROM outpatient_service WHERE branch_name IS NOT NULL AND branch_name != ''
               UNION SELECT branch_name FROM inpatient_service WHERE branch_name IS NOT NULL AND branch_name != ''
               UNION SELECT branch_name FROM bed_type_service WHERE branch_name IS NOT NULL AND branch_name != ''
               UNION SELECT branch_name FROM offsite_clinic_service WHERE branch_name IS NOT NULL AND branch_name != ''
               UNION SELECT branch_name FROM accounting_report WHERE branch_name IS NOT NULL AND branch_name != ''
               UNION SELECT branch_name FROM ops_management_indicators WHERE branch_name IS NOT NULL AND branch_name != ''""")
        val branches = branchRows.map { it[0].toString() }.filter { it.isNotEmpty() }.distinct()
            .sortedWith(compareBy({ if (it in preferred) preferred.indexOf(it) else 99 }, { it }))
        val depts = db.query(
            """SELECT DISTINCT dept FROM (
                 SELECT dept FROM outpatient_service WHERE dept IS NOT NULL AND dept != ''
                 UNION ALL SELECT dept FROM physician_service WHERE dept IS NOT NULL AND dept != ''
                 UNION ALL SELECT dept FROM inpatient_service WHERE dept IS NOT NULL AND dept != ''
               )""").map { it[0].toString() }.filter { it.isNotEmpty() }.distinct().sorted()
        val doctors = db.query(
            "SELECT DISTINCT doctor_name FROM physician_service " +
                "WHERE doctor_name IS NOT NULL AND LENGTH(TRIM(doctor_name)) >= 2")
            .map { it[0].toString() }.filter { it.isNotEmpty() }.sorted()
        val clinics = db.query(
            "SELECT DISTINCT clinic_name FROM offsite_clinic_service " +
                "WHERE clinic_name IS NOT NULL AND LENGTH(TRIM(clinic_name)) >= 2")
            .map { it[0].toString() }.filter { it.isNotEmpty() }.sorted()
        return EntityDictionary(branches, depts, doctors, clinics)
    }

    // ══════════ 色階 ════════════════════════════════
    /** 佔床率 0-100 → 紅-黃-綠 色階(RdYlGn)。 */
    fun occupancyColor(pct: Double): Long = Companion.occupancyColor(pct)

    companion object {
        /** 佔床率計算排除非急性病床大類別：排除「其他」與「產後（小孩）」(含全形/半形括號)。 */
        const val BED_OCC_EXCLUDE_MAJOR_SQL =
            "(major_category IS NULL OR (TRIM(major_category) != '其他' AND TRIM(major_category) NOT IN ('產後（小孩）', '產後(小孩)') AND TRIM(major_category) NOT LIKE '產後%'))"

        /** 病床實際佔床率 SQL 表達式：住院人日合計 / 實際床日數合計（避免站床率簡單平均導致失真） */
        fun bedActualOccSql(admCol: String = "admission_days", bedCol: String = "actual_bed_days"): String =
            "SUM(CAST($admCol AS REAL)) / NULLIF(SUM(CAST($bedCol AS REAL)), 0)"

        /** 病床登記佔床率 SQL 表達式：住院人日合計 / 登記床日數合計 */
        fun bedRegOccSql(admCol: String = "admission_days", bedCol: String = "registered_bed_days"): String =
            "SUM(CAST($admCol AS REAL)) / NULLIF(SUM(CAST($bedCol AS REAL)), 0)"

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
}
