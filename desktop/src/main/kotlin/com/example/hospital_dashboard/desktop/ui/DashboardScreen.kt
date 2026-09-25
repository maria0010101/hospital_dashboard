package com.example.hospital_dashboard.desktop.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.hospital_dashboard.data.DashboardRepo
import com.example.hospital_dashboard.data.Fmt
import com.example.hospital_dashboard.data.KpiSet
import com.example.hospital_dashboard.desktop.DesktopViewModel
import com.example.hospital_dashboard.desktop.UiState
import com.example.hospital_dashboard.desktop.ui.adaptive.*
import com.example.hospital_dashboard.desktop.ui.charts.ZoomChartScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

val DASHBOARD_TABS = listOf("🚪 門急診", "🛏️ 住院", "🏥 病床", "📋 其他", "🤖 AI 分析")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(vm: DesktopViewModel, state: UiState.Ready) {
    val filters by vm.filters.collectAsState()
    val tabIndex by vm.tabIndex.collectAsState()
    val zoomChart by vm.zoomChart.collectAsState()
    val isDarkMode by vm.isDarkMode.collectAsState()
    var showFilters by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "🏥 醫院營運儀表板 (桌面版)",
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            "📅 資料更新日期：${state.updateDate ?: "未知"} ｜ 來源檔：${state.sourceFile ?: "本機資料庫"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { vm.setDarkMode(!isDarkMode) }) {
                        Text(if (isDarkMode) "☀️ 淺色" else "🌙 深色", maxLines = 1)
                    }
                    TextButton(onClick = { showFilters = true }) {
                        Text("⚙ 篩選${if (filters.showHospitalTotal) " (全院)" else ""}${if (filters.excludeVaccine) " [排疫苗]" else ""}", maxLines = 1)
                    }
                    TextButton(onClick = { vm.backToFilePick() }) {
                        Text("🔄 更換檔案", maxLines = 1)
                    }
                }
            )
        }
    ) { padding ->
        Row(
            Modifier.padding(padding).fillMaxSize()
        ) {
            NavigationRail(
                modifier = Modifier.width(96.dp),
                header = {
                    Spacer(Modifier.height(8.dp))
                    Text("🏥", style = MaterialTheme.typography.titleLarge)
                }
            ) {
                Spacer(Modifier.height(12.dp))
                DASHBOARD_TABS.forEachIndexed { i, t ->
                    val emoji = t.takeWhile { !it.isWhitespace() }
                    val label = t.dropWhile { !it.isWhitespace() }.trim()
                    NavigationRailItem(
                        selected = tabIndex == i,
                        onClick = { vm.tabIndex.value = i },
                        icon = { Text(emoji) },
                        label = { Text(label, maxLines = 1) }
                    )
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { showFilters = true }) {
                    Text("⚙ 篩選", maxLines = 1, style = MaterialTheme.typography.labelSmall)
                }
                TextButton(onClick = { vm.backToFilePick() }) {
                    Text("🔄 更換", maxLines = 1, style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.height(12.dp))
            }

            Column(
                Modifier.weight(1f).fillMaxHeight()
            ) {
                KpiRow(vm)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (tabIndex) {
                        0 -> OpdTab(vm, filters)
                        1 -> IpdTab(vm, filters)
                        2 -> BedTab(vm, filters)
                        3 -> OtherTab(vm, filters)
                        4 -> AnalysisTab(vm)
                    }
                }
            }
        }
    }

    if (showFilters) {
        FilterSheet(vm, filters, onDismiss = { showFilters = false })
    }

    zoomChart?.let { content ->
        Dialog(
            onDismissRequest = { vm.closeZoom() },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(Modifier.fillMaxSize()) {
                ZoomChartScreen(vm, content, onClose = { vm.closeZoom() })
            }
        }
    }
}

// ── KPI 列 ──
private data class KpiDef(val title: String, val value: Double, val delta: Double?, val fmt: (Double) -> String)

@Composable
private fun KpiRow(vm: DesktopViewModel) {
    val month by produceState<Pair<String, String>?>(null) {
        value = withContext(Dispatchers.IO) { vm.repo.latestMonth() }
    }
    val cur by produceState<KpiSet?>(null, month) {
        value = month?.let { (y, m) ->
            withContext(Dispatchers.IO) { vm.repo.kpiForMonth(y, m) }
        }
    }
    val prev by produceState<KpiSet?>(null, month) {
        value = month?.let { (y, m) ->
            val py = y.toIntOrNull()?.minus(1)?.toString()
            if (py != null) withContext(Dispatchers.IO) { vm.repo.kpiForMonth(py, m) } else null
        }
    }
    val k = cur ?: return
    val m = month
    var showSheet by remember { mutableStateOf(false) }

    fun delta(c: Double, p: Double?): Double? =
        if (p == null || p == 0.0) null else (c - p) / p * 100

    val defs = listOf(
        KpiDef("門診人次", k.opd, delta(k.opd, prev?.opd), Fmt::compact),
        KpiDef("急診人次", k.er, delta(k.er, prev?.er), Fmt::compact),
        KpiDef("總診次", k.sessions, delta(k.sessions, prev?.sessions), Fmt::compact),
        KpiDef("住院人次", k.ipdAdm, delta(k.ipdAdm, prev?.ipdAdm), Fmt::compact),
        KpiDef("住院人日", k.ipdDays, delta(k.ipdDays, prev?.ipdDays), Fmt::compact),
        KpiDef("佔床率", k.occ, if (prev?.occ != null) k.occ - prev!!.occ else null, Fmt::percent),
        KpiDef("院外門診", k.offsite, delta(k.offsite, prev?.offsite), Fmt::compact),
        KpiDef("血液透析", k.dialysis, delta(k.dialysis, prev?.dialysis), Fmt::compact),
        KpiDef("健檢人次", k.checkup, delta(k.checkup, prev?.checkup), Fmt::compact)
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clickable { showSheet = true },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "📌 全院營運概況 ｜ 最新月份：${m?.let { formatMonth(it.first, it.second) } ?: "載入中..."}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "點擊查看各院區明細 ▸",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                defs.forEach { d ->
                    Box(modifier = Modifier.weight(1f)) {
                        KpiCard(d, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }

    if (showSheet && m != null) {
        BranchSummarySheet(vm, m.first, m.second, onDismiss = { showSheet = false })
    }
}

private fun formatMonth(year: String, month: String): String {
    val y = year.toIntOrNull() ?: return "$year/$month"
    val mo = month.toIntOrNull() ?: return "$year/$month"
    return "${y}年${mo.toString().padStart(2, '0')}月 (${y + 1911}/${mo.toString().padStart(2, '0')})"
}

@Composable
private fun BranchSummarySheet(
    vm: DesktopViewModel,
    year: String,
    month: String,
    onDismiss: () -> Unit
) {
    val stats by produceState<List<DashboardRepo.BranchMonthStat>>(emptyList(), year, month) {
        value = withContext(Dispatchers.IO) { vm.repo.branchStatsForMonth(year, month) }
    }
    val priorY = year.toIntOrNull()?.minus(1)?.toString() ?: year

    AdaptiveSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
        ) {
            Text(
                "🏢 ${year}年${month.toIntOrNull()?.toString()?.padStart(2, '0') ?: month}月 各院區明細",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "與去年同期 ${priorY}年${month}月 比較",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(Modifier.height(8.dp))
            stats.forEach { s -> BranchStatCard(s) }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun BranchStatCard(s: DashboardRepo.BranchMonthStat) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text("🏢 ${s.branch}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(2.dp))
            MetricLine("門診人次", s.opd, s.opdPrior, Fmt::int)
            MetricLine("急診人次", s.er, s.erPrior, Fmt::compact)
            MetricLine("住院人次", s.ipdAdm, s.ipdAdmPrior, Fmt::compact)
            MetricLine("住院人日", s.ipdDays, s.ipdDaysPrior, Fmt::compact)
            MetricLine("平均佔床率", s.occ, s.occPrior, Fmt::percent, pp = true)
        }
    }
}

@Composable
private fun MetricLine(
    name: String,
    value: Double?,
    prior: Double?,
    fmt: (Double) -> String,
    pp: Boolean = false
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(name, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
        if (value != null) {
            Text(fmt(value), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        } else {
            Text("—", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
        }
        Spacer(Modifier.width(8.dp))
        if (prior != null && value != null) {
            Text(
                "去年 ${fmt(prior)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(Modifier.width(8.dp))
            val delta = if (pp) value - prior
            else if (prior != 0.0) (value - prior) / prior * 100.0 else null
            if (delta != null) {
                val up = delta >= 0
                Text(
                    (if (up) "▲ " else "▼ ") +
                        (if (pp) String.format("%+.1fpp", delta) else String.format("%+.1f%%", delta)),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (up) Color(0xFF1E8449) else Color(0xFFC0392B)
                )
            } else {
                Text("—", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
        } else {
            Text("—", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun KpiCard(d: KpiDef, modifier: Modifier = Modifier) {
    Card(
        modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(d.title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(2.dp))
            Text(d.fmt(d.value), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            val isPp = d.title.contains("佔床率")
            if (d.delta != null) {
                val up = d.delta >= 0
                Text(
                    (if (up) "▲ " else "▼ ") +
                        (if (isPp) String.format("%+.1fpp", d.delta) else String.format("%+.1f%%", d.delta)),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (up) Color(0xFF1E8449) else Color(0xFFC0392B)
                )
            } else {
                Text("—", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

// ── 篩選對話框 ──
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(
    vm: DesktopViewModel,
    filters: DashboardRepo.Filters,
    onDismiss: () -> Unit
) {
    var years by remember { mutableStateOf(filters.years) }
    var monthsAll by remember { mutableStateOf(filters.months.isEmpty()) }
    var months by remember { mutableStateOf(filters.months) }
    var branchesAll by remember { mutableStateOf(filters.branches.isEmpty()) }
    var branches by remember { mutableStateOf(filters.branches) }
    var divsAll by remember { mutableStateOf(filters.deptDivs.isEmpty()) }
    var divs by remember { mutableStateOf(filters.deptDivs) }
    var deptsAll by remember { mutableStateOf(filters.depts.isEmpty()) }
    var depts by remember { mutableStateOf(filters.depts) }
    var yoy by remember { mutableStateOf(filters.showYoy) }
    var showHospitalTotal by remember { mutableStateOf(filters.showHospitalTotal) }
    var excludeVaccine by remember { mutableStateOf(filters.excludeVaccine) }
    var isDark by remember { mutableStateOf(vm.isDarkMode.value) }
    var fontLevel by remember { mutableIntStateOf(vm.fontScaleLevel.value) }

    val yearOpts = remember { vm.availableYears() }
    val monthOpts = remember { vm.availableMonths() }
    val branchOpts = remember { vm.availableBranches() }
    val divOpts = remember { vm.availableDeptDivs() }
    val deptOpts by produceState(emptyList<String>(), divsAll, divs) {
        value = withContext(Dispatchers.IO) {
            vm.availableDepts(if (divsAll || divs.isEmpty()) null else divs)
        }
    }

    AdaptiveSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
        ) {
            Text("⚙ 篩選條件設定", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))

            SectionLabel("📅 年度（至少一項）")
            if (years.isEmpty()) {
                Text("⚠️ 請至少選擇一個年度", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
            }
            ChipFlow(yearOpts.map { it to it }, years.toSet()) { opt ->
                years = if (opt in years) years - opt else years + opt
            }

            SectionLabel("📆 月份")
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = monthsAll, onClick = {
                    if (monthsAll) {
                        monthsAll = false; months = emptyList()
                    } else {
                        monthsAll = true; months = emptyList()
                    }
                }, label = { Text("全選") })
            }
            if (!monthsAll) {
                ChipFlow(monthOpts.map { it to "${it}月" }, months.toSet()) { opt ->
                    months = if (opt in months) months - opt else months + opt
                    if (months.isEmpty()) monthsAll = true
                }
            }

            SectionLabel("🏢 院區")
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(selected = branchesAll && !showHospitalTotal, onClick = {
                    if (branchesAll && !showHospitalTotal) {
                        branchesAll = false; branches = emptyList()
                    } else {
                        branchesAll = true; branches = emptyList()
                    }
                    showHospitalTotal = false
                }, label = { Text("全選") })
                FilterChip(
                    selected = showHospitalTotal,
                    onClick = { showHospitalTotal = !showHospitalTotal },
                    label = { Text("顯示全院") }
                )
            }
            if (!branchesAll) {
                ChipFlow(branchOpts.map { it to it }, branches.toSet()) { opt ->
                    branches = if (opt in branches) branches - opt else branches + opt
                    if (branches.isEmpty()) branchesAll = true
                }
            }

            SectionLabel("🏥 部別")
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = divsAll, onClick = {
                    if (divsAll) {
                        divsAll = false; divs = emptyList()
                    } else {
                        divsAll = true; divs = emptyList()
                    }
                }, label = { Text("全選") })
            }
            if (!divsAll) {
                ChipFlow(divOpts.map { it to it }, divs.toSet()) { opt ->
                    divs = if (opt in divs) divs - opt else divs + opt
                    if (divs.isEmpty()) divsAll = true
                }
            }

            SectionLabel("🩺 科別")
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = deptsAll, onClick = {
                    if (deptsAll) {
                        deptsAll = false; depts = emptyList()
                    } else {
                        deptsAll = true; depts = emptyList()
                    }
                }, label = { Text("全選") })
            }
            if (!deptsAll) {
                ChipFlow(deptOpts.map { it to it }, depts.toSet()) { opt ->
                    depts = if (opt in depts) depts - opt else depts + opt
                    if (depts.isEmpty()) deptsAll = true
                }
            }

            SectionLabel("📊 去年同期比較 (YoY)")
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("顯示去年同期比較", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = yoy, onCheckedChange = { yoy = it })
            }

            SectionLabel("💉 排除疫苗施打人次")
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("排除門急診篩檢與流感疫苗施打人次", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = excludeVaccine, onCheckedChange = { excludeVaccine = it })
            }

            SectionLabel("🌙 深色模式")
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("深色配色模式", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = isDark,
                    onCheckedChange = {
                        isDark = it
                        vm.setDarkMode(it)
                    }
                )
            }

            SectionLabel("🔤 字型大小設定")
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("字型大小", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "${DesktopViewModel.FONT_SCALE_NAMES[fontLevel]} (${String.format("%.1fx", DesktopViewModel.FONT_SCALE_MULTIPLIERS[fontLevel])})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = fontLevel.toFloat(),
                        onValueChange = {
                            val newLevel = it.roundToInt().coerceIn(0, 4)
                            fontLevel = newLevel
                            vm.setFontScaleLevel(newLevel)
                        },
                        valueRange = 0f..4f,
                        steps = 3
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = {
                        years = vm.defaultYears(yearOpts)
                        monthsAll = true; months = emptyList()
                        branchesAll = true; branches = emptyList()
                        divsAll = true; divs = emptyList()
                        deptsAll = true; depts = emptyList()
                        showHospitalTotal = false
                        excludeVaccine = false
                        fontLevel = 0
                        vm.setFontScaleLevel(0)
                        isDark = false
                        vm.setDarkMode(false)
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("重設") }
                Button(
                    onClick = {
                        if (years.isEmpty()) return@Button
                        vm.setFontScaleLevel(fontLevel)
                        vm.setDarkMode(isDark)
                        vm.filters.value = DashboardRepo.Filters(
                            years = years,
                            months = if (monthsAll) emptyList() else months,
                            branches = if (branchesAll) emptyList() else branches,
                            deptDivs = if (divsAll) emptyList() else divs,
                            depts = if (deptsAll) emptyList() else depts,
                            showYoy = yoy,
                            showHospitalTotal = showHospitalTotal,
                            excludeVaccine = excludeVaccine
                        )
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("套用") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Spacer(Modifier.height(10.dp))
    Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(4.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipFlow(
    options: List<Pair<String, String>>,
    selected: Set<String>,
    onToggle: (String) -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { (key, label) ->
            FilterChip(
                selected = key in selected,
                onClick = { onToggle(key) },
                label = { Text(label) }
            )
        }
    }
}
