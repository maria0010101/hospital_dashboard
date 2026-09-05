package com.example.hospital_dashboard.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.hospital_dashboard.DashboardViewModel
import com.example.hospital_dashboard.data.BarSegment
import com.example.hospital_dashboard.data.BedDetailRow
import com.example.hospital_dashboard.data.DashboardRepo
import com.example.hospital_dashboard.data.Fmt
import com.example.hospital_dashboard.data.HBarData
import com.example.hospital_dashboard.data.HBarRow
import com.example.hospital_dashboard.data.LineChartData
import com.example.hospital_dashboard.data.PieData
import com.example.hospital_dashboard.data.TableCell
import com.example.hospital_dashboard.data.TableData
import com.example.hospital_dashboard.data.VBarData
import com.example.hospital_dashboard.data.VBarGroup
import com.example.hospital_dashboard.ui.charts.BranchMetricDef
import com.example.hospital_dashboard.ui.charts.ChartCard
import com.example.hospital_dashboard.ui.charts.ChartContent
import com.example.hospital_dashboard.ui.charts.DataTable
import com.example.hospital_dashboard.ui.charts.EmptyHint
import com.example.hospital_dashboard.ui.charts.HBarClick
import com.example.hospital_dashboard.ui.charts.HBarChart
import com.example.hospital_dashboard.ui.charts.LineChart
import com.example.hospital_dashboard.ui.charts.PieChart
import com.example.hospital_dashboard.ui.charts.VBarClick
import com.example.hospital_dashboard.ui.charts.VBarChart
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 依 key 載入圖表資料(IO 執行緒)。 */
@Composable
private fun <T> loadChart(keys: List<Any?>, loader: suspend () -> T): T? {
    var state by remember { mutableStateOf<T?>(null) }
    LaunchedEffect(keys) {
        state = withContext(Dispatchers.IO) { loader() }
    }
    return state
}

@Composable
private fun LoadingBox() {
    Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.width(28.dp).height(28.dp))
    }
}

@Composable
private fun TabColumn(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) { content() }
}

// ── 可點擊放大之圖表卡片 ─────────────────────────────
@Composable
private fun LineCard(
    vm: DashboardViewModel, title: String, data: LineChartData?,
    height: Dp = 220.dp, fmt: (Double) -> String = Fmt::compact,
    monthDef: BranchMetricDef? = null
) {
    ChartCard(title, onClick = data?.let { { vm.openZoom(ChartContent.Line(title, it, fmt, monthDef)) } }) {
        data?.let { LineChart(it, height = height, yFormatter = fmt) } ?: LoadingBox()
    }
}

@Composable
private fun HBarCard(
    vm: DashboardViewModel, title: String, data: HBarData?,
    height: Dp = 240.dp, fmt: (Double) -> String = Fmt::compact,
    clickAction: HBarClick = HBarClick.None,
    branchMetricDef: BranchMetricDef? = null
) {
    ChartCard(
        title,
        onClick = data?.let {
            { vm.openZoom(ChartContent.HBar(title, it, fmt, clickAction, branchMetricDef)) }
        }
    ) {
        data?.let { HBarChart(it, height = height, valueFormatter = fmt) } ?: LoadingBox()
    }
}

@Composable
private fun VBarCard(
    vm: DashboardViewModel, title: String, data: VBarData?,
    height: Dp = 240.dp, fmt: (Double) -> String = Fmt::compact,
    click: VBarClick = VBarClick.None, // 放大檢視時點擊長條的明細模式
    monthDef: BranchMetricDef? = null
) {
    ChartCard(
        title,
        onClick = data?.let { { vm.openZoom(ChartContent.VBar(title, it, fmt, click, monthDef)) } }
    ) {
        data?.let { VBarChart(it, height = height, valueFormatter = fmt) } ?: LoadingBox()
    }
}

@Composable
private fun PieCard(vm: DashboardViewModel, title: String, data: PieData?) {
    ChartCard(title, onClick = data?.let { { vm.openZoom(ChartContent.Pie(title, it)) } }) {
        data?.let { PieChart(it) } ?: LoadingBox()
    }
}

@Composable
private fun TableCard(vm: DashboardViewModel, title: String, data: TableData?) {
    ChartCard(title, onClick = data?.let { { vm.openZoom(ChartContent.Table(title, it)) } }) {
        data?.let { WideTable(it) } ?: LoadingBox()
    }
}

// ══════════ TAB1 門急診服務 ═════════════════════════
@Composable
fun OpdTab(vm: DashboardViewModel, filters: DashboardRepo.Filters) {
    val opd = loadChart(listOf(filters)) { vm.repo.opdMonthly(filters) }
    val er = loadChart(listOf(filters)) { vm.repo.erMonthly(filters) }
    val deptTop = loadChart(listOf(filters)) { vm.repo.opdDeptTop(filters) }
    val pie = loadChart(listOf(filters)) { vm.repo.firstReturnPie(filters) }
    val div = loadChart(listOf(filters)) { vm.repo.deptDivMonthly(filters) }
    val brBar = loadChart(listOf(filters)) { vm.repo.branchOpdBar(filters) }

    var showFirstSheet by remember { mutableStateOf(false) }

    TabColumn {
        LineCard(vm, "門診人次月趨勢（依院區）", opd, height = 230.dp)
        LineCard(vm, "急診人次月趨勢（依院區）", er, height = 200.dp)
        HBarCard(vm, "科別門診人次（TOP20）", deptTop, height = 260.dp, fmt = Fmt::k,
            clickAction = HBarClick.DeptBranch)
        // 初診/複診比例：點擊直接顯示各院區初診人次與初診率(不放大)
        ChartCard("初診/複診比例", onClick = { showFirstSheet = true }) {
            pie?.let { PieChart(it) } ?: LoadingBox()
        }
        LineCard(vm, "各部別門診人次趨勢", div, height = 200.dp)
        HBarCard(vm, "各院區門診人次", brBar, height = 180.dp,
            clickAction = HBarClick.BranchDept)
    }

    if (showFirstSheet) {
        FirstVisitSheet(vm, filters, onDismiss = { showFirstSheet = false })
    }
}

// ── 各院區初診/複診明細面板 ─────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FirstVisitSheet(
    vm: DashboardViewModel,
    filters: DashboardRepo.Filters,
    onDismiss: () -> Unit
) {
    val stats = loadChart(listOf(filters)) { vm.repo.branchFirstVisitStats(filters) }
    val years = filters.years.mapNotNull { it.toIntOrNull() }
    val note = if (years.isNotEmpty()) "篩選區間 民國${years.min()}-${years.max()}年 累計" else ""

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
        ) {
            Text("🩺 初診/複診 各院區明細",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (note.isNotEmpty()) {
                Text(note, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline)
            }
            Spacer(Modifier.height(6.dp))
            stats?.forEach { s ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🏢 ${s.branch}", style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1f))
                    Text("初診 ${Fmt.int(s.firstVisit)}",
                        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(10.dp))
                    Text("複診 ${Fmt.int(s.returnVisit)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.width(10.dp))
                    Text("初診率 ${String.format("%.1f%%", s.firstRate)}",
                        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ══════════ TAB2 住院服務 ═════════════════════════
@Composable
fun IpdTab(vm: DashboardViewModel, filters: DashboardRepo.Filters) {
    val ipd = loadChart(listOf(filters)) { vm.repo.ipdMonthly(filters) }
    val dis = loadChart(listOf(filters)) { vm.repo.dischargeMonthly(filters) }
    val days = loadChart(listOf(filters)) { vm.repo.ipdDaysMonthly(filters) }
    val brBar = loadChart(listOf(filters)) { vm.repo.branchIpdBar(filters) }
    val stats = loadChart(listOf(filters)) { vm.repo.ipdDeptStats(filters) }

    val admBar = stats?.let {
        VBarData(it.map { s -> VBarGroup(s.dept, listOf(BarSegment("住院人次", s.adm))) })
    }
    val losBar = stats?.let {
        VBarData(it.sortedBy { s -> s.los }.map { s -> VBarGroup(s.dept, listOf(BarSegment("平均住院日", s.los))) })
    }

    TabColumn {
        LineCard(vm, "住院人次月趨勢（依院區）", ipd, height = 230.dp)
        LineCard(vm, "出院人次月趨勢（依院區）", dis, height = 200.dp)
        LineCard(vm, "住院 vs 出院人日趨勢", days, height = 200.dp)
        HBarCard(vm, "各院區住院人次", brBar, height = 180.dp)
        VBarCard(vm, "Top 15 科別住院人次", admBar, height = 240.dp, click = VBarClick.DeptBranch)
        VBarCard(vm, "Top 15 科別平均住院日", losBar, height = 240.dp, click = VBarClick.DeptBranch)
    }
}

// ══════════ TAB3 病床利用 ═════════════════════════
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BedTab(vm: DashboardViewModel, filters: DashboardRepo.Filters) {
    val allMajors by produceState(emptyList<String>(), filters) {
        value = withContext(Dispatchers.IO) { vm.repo.bedMajors(filters) }
    }
    // 大類別選擇（病床分頁篩選以「大類別」為單位；預設前 3 大類）
    var majors by rememberSaveable(stateSaver = listSaver(
        save = { it.toList() }, restore = { it.toList() }
    )) { mutableStateOf(emptyList<String>()) }
    var majorsInit by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(allMajors) {
        if (!majorsInit && allMajors.isNotEmpty()) {
            majors = allMajors.take(3)
            majorsInit = true
        }
    }
    val effMajors = if (majors.isEmpty()) allMajors else majors

    // 僅顯示最新年月 / 顯示累計年月（預設僅顯示最新年月）
    var latestOnly by rememberSaveable { mutableStateOf(true) }

    val occ = loadChart(listOf(filters, effMajors)) { vm.repo.bedOccMonthly(filters, effMajors) }
    val open = loadChart(listOf(filters, effMajors)) { vm.repo.bedOpenMonthly(filters, effMajors) }
    val brOcc = loadChart(listOf(filters, effMajors, latestOnly)) {
        vm.repo.bedBranchOcc(filters, effMajors, latestOnly)
    }
    val regOpen = loadChart(listOf(filters, effMajors, latestOnly)) {
        vm.repo.bedBranchRegVsOpen(filters, effMajors, latestOnly)
    }
    val pivot = loadChart(listOf(filters, effMajors, latestOnly)) {
        vm.repo.bedPivot(filters, effMajors, latestOnly = latestOnly)
    }
    val yoyCmp = loadChart(listOf(filters, effMajors, latestOnly)) {
        vm.repo.bedYoyCompare(filters, effMajors, latestOnly)
    }

    TabColumn {
        // 篩選列：大類別
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(12.dp)) {
                Text("篩選大類別", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = majors.isEmpty(), onClick = { majors = emptyList() }, label = { Text("全部") })
                    allMajors.forEach { c ->
                        FilterChip(selected = c in majors, onClick = {
                            majors = if (c in majors) majors - c else majors + c
                        }, label = { Text(c) })
                    }
                }
            }
        }

        LineCard(vm, "實際佔床率月趨勢（依病床類別）", occ, height = 230.dp, fmt = Fmt::percent)
        LineCard(vm, "實際開床數月趨勢（依病床類別）", open, height = 200.dp)

        // 顯示範圍切換（置於開床數趨勢圖下方，套用於框線內 4 張表）
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            RangeToggle(latestOnly, onLatest = { latestOnly = true }, onCumulative = { latestOnly = false })
        }

        // 以下 4 張表套用上方「僅顯示最新年月/顯示累計年月」切換 → 以框線標示
        Column(
            Modifier
                .fillMaxWidth()
                .border(
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    RoundedCornerShape(12.dp)
                )
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                if (latestOnly) "📦 本區套用：僅顯示最新年月資料（依大類別）"
                else "📦 本區套用：顯示累計年月資料（依大類別）",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            HBarCard(vm, "各院區實際佔床率", brOcc, height = 220.dp, fmt = Fmt::percent,
                clickAction = HBarClick.BedBranch)
            HBarCard(vm, "各院區登記 vs 實際開床數（重疊）", regOpen, height = 220.dp, fmt = Fmt::int,
                clickAction = HBarClick.BedBranch)
            TableCard(vm, "各院區 × 病床類別 實際佔床率 (%)", pivot)

            if (filters.showYoy && yoyCmp != null && yoyCmp.isNotEmpty()) {
                val data = TableData(
                    columns = listOf("病床類別", "本期佔床率", "去年同期", "變化 (pp)"),
                    rows = yoyCmp.map { r ->
                        listOf(
                            TableCell(r.category),
                            TableCell(Fmt.percent(r.curr), vm.repo.occupancyColor(r.curr)),
                            TableCell(Fmt.percent(r.prev), vm.repo.occupancyColor(r.prev)),
                            TableCell(
                                String.format("%+.1f", r.change),
                                if (r.change >= 0) 0xFFA9DFBF else 0xFFF1948A
                            )
                        )
                    }
                )
                TableCard(vm, "📊 去年同期佔床率比較", data)
            }
        }
    }
}


@Composable
private fun WideTable(data: TableData) {
    Row(Modifier.horizontalScroll(rememberScrollState())) {
        DataTable(data, Modifier.width(720.dp))
    }
}

// ══════════ TAB4 其他服務 ═════════════════════════
@Composable
fun OtherTab(vm: DashboardViewModel, filters: DashboardRepo.Filters) {
    val offsite = loadChart(listOf(filters)) { vm.repo.offsiteMonthly(filters) }
    val clinicBar = loadChart(listOf(filters)) { vm.repo.offsiteClinicBar(filters) }
    val acc = loadChart(listOf(filters)) { vm.repo.accMonthly(filters) }
    val ops = loadChart(listOf(filters)) { vm.repo.opsMonthly(filters) }
    val income = loadChart(listOf(filters)) { vm.repo.incomeMonthly(filters) }
    val brInc = loadChart(listOf(filters)) { vm.repo.branchIncome(filters) }
    val selfInc = loadChart(listOf(filters)) { vm.repo.branchSelfPay(filters) }

    TabColumn {
        LineCard(vm, "院外門診部服務量趨勢", offsite, height = 210.dp)
        HBarCard(vm, "各院外門診部服務量（累計）", clinicBar, height = 260.dp)
        // 洗腎/健檢：點月份 → 各院區明細(近三個月 + 去年同期)
        LineCard(vm, "洗腎／健檢人次趨勢", acc, height = 210.dp,
            monthDef = BranchMetricDef(
                "洗腎／健檢各院區明細", "accounting_report",
                listOf(
                    "洗腎人次" to "SUM(CAST(dialysis_count AS REAL))",
                    "健檢人次" to "SUM(CAST(opd_checkup_count AS REAL))"
                )
            ))
        // 手術/生產：點月份長條 → 各院區明細
        VBarCard(vm, "手術／生產人次趨勢", ops, height = 230.dp,
            click = VBarClick.OpsMonth,
            monthDef = BranchMetricDef(
                "手術／生產各院區明細", "ops_management_indicators",
                listOf(
                    "手術人次" to "SUM(CAST(surgery_opd_count AS REAL)) + SUM(CAST(surgery_admission_count AS REAL))",
                    "生產人次" to "SUM(CAST(delivery_count AS REAL))"
                )
            ))
        // 收入趨勢(總/自費)：改為相同折線呈現，點月份 → 各院區收入明細
        val incTotalDef = BranchMetricDef(
            "總收入各院區明細", "ops_management_indicators",
            listOf(
                "門診收入" to "SUM(CAST(total_income_opd AS REAL))",
                "住院收入" to "SUM(CAST(total_income_admission AS REAL))"
            ), fmt = Fmt::money
        )
        val incSelfDef = BranchMetricDef(
            "自費收入各院區明細", "ops_management_indicators",
            listOf(
                "門診自費" to "SUM(CAST(self_pay_income_opd AS REAL))",
                "住院自費" to "SUM(CAST(self_pay_income_admission AS REAL))"
            ), fmt = Fmt::money
        )
        LineCard(vm, "收入趨勢（總收入）", income?.first, height = 230.dp, fmt = Fmt::money,
            monthDef = incTotalDef)
        LineCard(vm, "收入趨勢（自費收入）", income?.second, height = 200.dp, fmt = Fmt::money,
            monthDef = incSelfDef)
        HBarCard(vm, "各院區總收入（累計，單位千元）", brInc, height = 240.dp, fmt = Fmt::moneyK,
            clickAction = HBarClick.BranchIncome, branchMetricDef = incTotalDef)
        HBarCard(vm, "各院區自費收入（累計，單位千元）", selfInc, height = 240.dp, fmt = Fmt::moneyK,
            clickAction = HBarClick.BranchIncome, branchMetricDef = incSelfDef)
    }
}

// ══════════ TAB6 醫師服務量 ═══════════════════════
@Composable
fun PhysServiceTab(vm: DashboardViewModel, filters: DashboardRepo.Filters) {
    val vols = loadChart(listOf(filters)) { vm.repo.physDeptVolumes(filters) }

    fun topBar(
        vols: List<DashboardRepo.PhysDeptVol>?,
        select: (DashboardRepo.PhysDeptVol) -> Double,
        segName: String
    ): VBarData? =
        vols?.filter { select(it) > 0 }?.sortedByDescending(select)?.take(20)
            ?.map { VBarGroup(it.dept, listOf(BarSegment(segName, select(it)))) }
            ?.let { VBarData(it) }

    TabColumn {
        VBarCard(vm, "科別門診人次 (Top 20)", topBar(vols, { it.opd }, "門診人次"),
            height = 240.dp, click = VBarClick.PhysDept)
        VBarCard(vm, "科別急診人次 (Top 20)", topBar(vols, { it.er }, "急診人次"),
            height = 240.dp, click = VBarClick.PhysDept)
        VBarCard(vm, "科別住院人次 (Top 20)", topBar(vols, { it.adm }, "住院人次"),
            height = 240.dp, click = VBarClick.PhysDept)
        VBarCard(vm, "科別住院人日 (Top 20)", topBar(vols, { it.days }, "住院人日"),
            height = 240.dp, click = VBarClick.PhysDept)
    }
}

// ══════════ TAB7 醫師收入統計 ═══════════════════════
@Composable
fun PhysIncomeTab(vm: DashboardViewModel, filters: DashboardRepo.Filters) {
    val income = loadChart(listOf(filters)) { vm.repo.physIncomeMonthly(filters) }
    val pie = loadChart(listOf(filters)) { vm.repo.physIncomePie(filters) }
    val anchor = loadChart(listOf(filters)) { vm.repo.anchorYm(filters) }

    val anchorLabel = anchor?.let { "${it.first}年${it.second.toString().padStart(2, '0')}月" } ?: "—"

    TabColumn {
        VBarCard(vm, "全院收入月趨勢（門診/住院 × 健保/自費）", income,
            height = 250.dp, fmt = Fmt::money, click = VBarClick.BranchIncome)
        PieCard(vm, "💰 當月收入結構（$anchorLabel）", pie)

        // 健保收入 / 住院收入 比率說明
        val p = pie
        if (p != null && p.slices.isNotEmpty()) {
            val total = p.slices.sumOf { it.value }
            if (total > 0) {
                val nhi = p.slices.filter { it.label.contains("健保") }.sumOf { it.value }
                val ipd = p.slices.filter { it.label.startsWith("住院") }.sumOf { it.value }
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Text("健保收入 ${String.format("%.1f%%", nhi / total * 100)}",
                            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary)
                        Text("自費收入 ${String.format("%.1f%%", (total - nhi) / total * 100)}",
                            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Text("住院收入 ${String.format("%.1f%%", ipd / total * 100)}",
                            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

// ══════════ TAB5 各院區病床占床率明細 ═════════════
@Composable
fun BedDetailTab(vm: DashboardViewModel, filters: DashboardRepo.Filters) {
    // 僅顯示最新年月資料 / 顯示累計年月資料（預設僅顯示最新年月）
    var latestOnly by rememberSaveable { mutableStateOf(true) }
    val detail = loadChart(listOf(filters, latestOnly)) { vm.repo.bedDetail(filters, latestOnly) }

    if (detail == null) {
        TabColumn { LoadingBox() }
        return
    }
    if (detail!!.isEmpty()) {
        TabColumn { ChartCard("各院區病床占床率明細") { EmptyHint() } }
        return
    }

    val hasYoy = detail!!.any { it.yoyActOcc != null }
    val branches = detail!!.groupBy { it.branch }.toSortedMap()

    TabColumn {
        // 顯示範圍切換（預設僅顯示最新年月，床數/人日為該月單月值）
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            RangeToggle(latestOnly, onLatest = { latestOnly = true }, onCumulative = { latestOnly = false })
        }
        branches.forEach { (br, rows) ->
            val regBeds = rows.sumOf { it.regBeds }
            val openBeds = rows.sumOf { it.openBeds }
            val days = rows.sumOf { it.days }
            val regOcc = rows.map { it.regOcc }.average()
            val actOcc = rows.map { it.actOcc }.average()
            val yoyAct = rows.mapNotNull { it.yoyActOcc }.takeIf { it.isNotEmpty() }?.average()

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("🏢 $br", fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "登記 ${Fmt.int(regBeds.toDouble())} 床 ｜ 實開 ${Fmt.int(openBeds.toDouble())} 床 ｜ " +
                            "住院人日 ${Fmt.int(days.toDouble())} ｜ 登記佔床率 ${String.format("%.1f%%", regOcc)} ｜ " +
                            "實開佔床率 ${String.format("%.1f%%", actOcc)}" +
                            (if (hasYoy && yoyAct != null)
                                " ｜ 實開佔床率變化 ${String.format("%+.1fpp", actOcc - yoyAct)}" else ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))

                    val majors = rows.groupBy { it.major }.toSortedMap()
                    majors.forEach { (mc, mrows) ->
                        var expanded by remember(mc) { mutableStateOf(false) }
                        Column {
                            Row(
                                Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(if (expanded) "▾" else "▸", color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(6.dp))
                                Text("📂 $mc", fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.weight(1f))
                                val mcBeds = mrows.sumOf { it.regBeds }
                                val mcDays = mrows.sumOf { it.days }
                                val mcOcc = mrows.map { it.actOcc }.average()
                                Text(
                                    "${Fmt.int(mcBeds.toDouble())} 床 ｜ ${Fmt.int(mcDays.toDouble())} 人日 ｜ " +
                                        "${String.format("%.1f%%", mcOcc)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            if (expanded) {
                                val sorted = mrows.sortedWith(
                                    compareBy({ it.category }, { -it.regBeds })
                                )
                                val columns = mutableListOf(
                                    "類別", "護理站", "登記床數", "實開床數", "住院人日",
                                    "登記佔床率", "實開佔床率"
                                )
                                if (hasYoy) columns += listOf("去年登記", "去年實開", "登記變化", "實開變化")
                                val table = TableData(
                                    columns = columns,
                                    rows = sorted.map { r ->
                                        val cells = mutableListOf(
                                            TableCell(r.category),
                                            TableCell(r.station),
                                            TableCell(Fmt.int(r.regBeds.toDouble())),
                                            TableCell(Fmt.int(r.openBeds.toDouble())),
                                            TableCell(Fmt.int(r.days.toDouble())),
                                            TableCell(Fmt.percent(r.regOcc), vm.repo.occupancyColor(r.regOcc)),
                                            TableCell(Fmt.percent(r.actOcc), vm.repo.occupancyColor(r.actOcc))
                                        )
                                        if (hasYoy) {
                                            cells += listOf(
                                                TableCell(r.yoyRegOcc?.let { Fmt.percent(it) } ?: "-"),
                                                TableCell(r.yoyActOcc?.let { Fmt.percent(it) } ?: "-"),
                                                TableCell(
                                                    r.yoyRegOcc?.let { String.format("%+.1f", r.regOcc - it) } ?: "-",
                                                    r.yoyRegOcc?.let {
                                                        if (r.regOcc - it >= 0) 0xFFA9DFBF else 0xFFF1948A
                                                    }
                                                ),
                                                TableCell(
                                                    r.yoyActOcc?.let { String.format("%+.1f", r.actOcc - it) } ?: "-",
                                                    r.yoyActOcc?.let {
                                                        if (r.actOcc - it >= 0) 0xFFA9DFBF else 0xFFF1948A
                                                    }
                                                )
                                            )
                                        }
                                        cells
                                    }
                                )
                                WideTable(table)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 「僅顯示最新年月／顯示累計年月」兩行式切換（選取時以主色高對比標示）。 */
@Composable
private fun RangeToggle(
    latestOnly: Boolean,
    onLatest: () -> Unit,
    onCumulative: () -> Unit,
    label: String = "📅 顯示範圍"
) {
    @Composable
    fun chip(selected: Boolean, line1: String, line2: String, onClick: () -> Unit) {
        val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
        val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        Box(
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(bg)
                .border(
                    BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                    RoundedCornerShape(10.dp)
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 5.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(line1, style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold, color = fg, textAlign = TextAlign.Center)
                Text(line2, style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold, color = fg, textAlign = TextAlign.Center)
            }
        }
    }

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        chip(latestOnly, "僅顯示最新", "年月資料") { onLatest() }
        chip(!latestOnly, "顯示累計", "年月資料") { onCumulative() }
    }
}
