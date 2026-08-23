package com.example.hospital_dashboard.ui

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
import com.example.hospital_dashboard.ui.charts.ChartCard
import com.example.hospital_dashboard.ui.charts.ChartContent
import com.example.hospital_dashboard.ui.charts.DataTable
import com.example.hospital_dashboard.ui.charts.EmptyHint
import com.example.hospital_dashboard.ui.charts.HBarClick
import com.example.hospital_dashboard.ui.charts.HBarChart
import com.example.hospital_dashboard.ui.charts.LineChart
import com.example.hospital_dashboard.ui.charts.PieChart
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
    height: Dp = 220.dp, fmt: (Double) -> String = Fmt::compact
) {
    ChartCard(title, onClick = data?.let { { vm.openZoom(ChartContent.Line(title, it, fmt)) } }) {
        data?.let { LineChart(it, height = height, yFormatter = fmt) } ?: LoadingBox()
    }
}

@Composable
private fun HBarCard(
    vm: DashboardViewModel, title: String, data: HBarData?,
    height: Dp = 240.dp, fmt: (Double) -> String = Fmt::compact,
    clickAction: HBarClick = HBarClick.None
) {
    ChartCard(
        title,
        onClick = data?.let { { vm.openZoom(ChartContent.HBar(title, it, fmt, clickAction)) } }
    ) {
        data?.let { HBarChart(it, height = height, valueFormatter = fmt) } ?: LoadingBox()
    }
}

@Composable
private fun VBarCard(
    vm: DashboardViewModel, title: String, data: VBarData?,
    height: Dp = 240.dp, fmt: (Double) -> String = Fmt::compact,
    clickable: Boolean = false // true = 放大檢視時點科別顯示各院區明細
) {
    ChartCard(
        title,
        onClick = data?.let { { vm.openZoom(ChartContent.VBar(title, it, fmt, clickable)) } }
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
        HBarCard(vm, "科別門診人次 vs 診次 (Top 20)", deptTop, height = 260.dp,
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
        VBarCard(vm, "Top 15 科別住院人次", admBar, height = 240.dp, clickable = true)
        VBarCard(vm, "Top 15 科別平均住院日", losBar, height = 240.dp, clickable = true)
    }
}

// ══════════ TAB3 病床利用 ═════════════════════════
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BedTab(vm: DashboardViewModel, filters: DashboardRepo.Filters) {
    val allCats by produceState(emptyList<String>(), filters) {
        value = withContext(Dispatchers.IO) { vm.repo.bedCategories(filters) }
    }
    // 病床類別選擇(預設：急性/加護/一般/呼吸 前 5)
    var cats by rememberSaveable(stateSaver = listSaver(
        save = { it.toList() }, restore = { it.toList() }
    )) { mutableStateOf(emptyList<String>()) }
    var catsInit by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(allCats) {
        if (!catsInit && allCats.isNotEmpty()) {
            val d = allCats.filter { c -> listOf("急性", "加護", "一般", "呼吸").any { c.contains(it) } }.take(5)
            cats = if (d.isNotEmpty()) d else allCats.take(3)
            catsInit = true
        }
    }
    val effectiveCats = if (cats.isEmpty()) allCats else cats

    var useNs by rememberSaveable { mutableStateOf(false) }
    val allNs by produceState(emptyList<String>(), filters, effectiveCats) {
        value = withContext(Dispatchers.IO) { vm.repo.bedStations(filters, effectiveCats) }
    }
    var ns by rememberSaveable(stateSaver = listSaver(
        save = { it.toList() }, restore = { it.toList() }
    )) { mutableStateOf(emptyList<String>()) }
    val effectiveNs = if (useNs && ns.isNotEmpty()) ns else emptyList()

    val occ = loadChart(listOf(filters, effectiveCats)) { vm.repo.bedOccMonthly(filters, effectiveCats) }
    val open = loadChart(listOf(filters, effectiveCats)) { vm.repo.bedOpenMonthly(filters, effectiveCats) }
    val brOcc = loadChart(listOf(filters, effectiveCats)) { vm.repo.bedBranchOcc(filters, effectiveCats) }
    val catRegOpen = loadChart(listOf(filters, effectiveCats)) { vm.repo.bedCatRegVsOpen(filters, effectiveCats) }
    val pivot = loadChart(listOf(filters, effectiveCats)) { vm.repo.bedPivot(filters, effectiveCats, byStation = false, ns = emptyList()) }
    val stationOcc = loadChart(listOf(filters, effectiveCats, effectiveNs, useNs)) {
        if (useNs) vm.repo.bedStationOcc(filters, effectiveCats, effectiveNs) else HBarData.EMPTY
    }
    val stationBeds = loadChart(listOf(filters, effectiveCats, effectiveNs, useNs)) {
        if (useNs) vm.repo.bedStationBeds(filters, effectiveCats, effectiveNs) else HBarData.EMPTY
    }
    val pivotNs = loadChart(listOf(filters, effectiveCats, effectiveNs, useNs)) {
        if (useNs) vm.repo.bedPivot(filters, effectiveCats, byStation = true, ns = effectiveNs) else TableData.EMPTY
    }
    val yoyCmp = loadChart(listOf(filters, effectiveCats)) { vm.repo.bedYoyCompare(filters, effectiveCats) }

    TabColumn {
        // 篩選列
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(12.dp)) {
                Text("篩選病床類別", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = cats.isEmpty(), onClick = { cats = emptyList() }, label = { Text("全部") })
                    allCats.forEach { c ->
                        FilterChip(selected = c in cats, onClick = {
                            cats = if (c in cats) cats - c else cats + c
                        }, label = { Text(c) })
                    }
                }
                Spacer(Modifier.height(6.dp))
                FilterChip(selected = useNs, onClick = { useNs = !useNs }, label = { Text("🔍 護理站維度分析") })
                if (useNs) {
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = ns.isEmpty(), onClick = { ns = emptyList() }, label = { Text("全部護理站") })
                        allNs.take(40).forEach { n ->
                            FilterChip(selected = n in ns, onClick = {
                                ns = if (n in ns) ns - n else ns + n
                            }, label = { Text(n) })
                        }
                    }
                }
            }
        }

        LineCard(vm, "實際佔床率月趨勢（依病床類別）", occ, height = 230.dp, fmt = Fmt::percent)
        LineCard(vm, "實際開床數月趨勢（依病床類別）", open, height = 200.dp)

        if (useNs) {
            HBarCard(vm, "各護理站平均實際佔床率 (Top 25)", stationOcc, height = 320.dp, fmt = Fmt::percent)
            HBarCard(vm, "各護理站登記 vs 實開床數 (Top 20)", stationBeds, height = 280.dp)
            TableCard(vm, "護理站 × 病床類別 實際佔床率 (%)", pivotNs)
        }

        HBarCard(vm, "各院區平均實際佔床率", brOcc, height = 220.dp, fmt = Fmt::percent)
        HBarCard(vm, "登記 vs 實際開床數（依病床類別）", catRegOpen, height = 240.dp)
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

    TabColumn {
        LineCard(vm, "院外門診部服務量趨勢", offsite, height = 210.dp)
        HBarCard(vm, "各院外門診部服務量", clinicBar, height = 260.dp)
        LineCard(vm, "洗腎／健檢人次趨勢", acc, height = 210.dp)
        VBarCard(vm, "手術／生產人次趨勢", ops, height = 230.dp)
        VBarCard(vm, "收入趨勢（總收入）", income?.first, height = 230.dp, fmt = Fmt::money)
        LineCard(vm, "收入趨勢（自費收入）", income?.second, height = 200.dp, fmt = Fmt::money)
        HBarCard(vm, "各院區總收入", brInc, height = 220.dp, fmt = Fmt::money)
    }
}

// ══════════ TAB5 各院區病床占床率明細 ═════════════
@Composable
fun BedDetailTab(vm: DashboardViewModel, filters: DashboardRepo.Filters) {
    val detail = loadChart(listOf(filters)) { vm.repo.bedDetail(filters) }

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
