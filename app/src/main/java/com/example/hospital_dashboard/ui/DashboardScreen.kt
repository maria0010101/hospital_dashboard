package com.example.hospital_dashboard.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.hospital_dashboard.DashboardViewModel
import com.example.hospital_dashboard.UiState
import com.example.hospital_dashboard.data.DashboardRepo
import com.example.hospital_dashboard.data.Fmt
import com.example.hospital_dashboard.data.KpiSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(vm: DashboardViewModel, state: UiState.Ready) {
    val filters by vm.filters.collectAsState()
    var tabIndex by rememberSaveable { mutableStateOf(0) }
    var showFilters by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("🏥 醫院營運儀表板", fontWeight = FontWeight.Bold)
                        Text(
                            "📅 資料更新日期：${state.updateDate ?: "未知"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { showFilters = true }) { Text("⚙ 篩選") }
                    TextButton(onClick = { vm.backToFilePick() }) { Text("🔄 更換") }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())
        ) {
            KpiRow(vm)
            PrimaryScrollableTabRow(selectedTabIndex = tabIndex, edgePadding = 8.dp) {
                listOf("🚪 門急診", "🛏️ 住院", "🏥 病床", "📋 其他", "🛏️ 佔床率", "🩺 醫師服務", "💰 醫師收入").forEachIndexed { i, t ->
                    Tab(selected = tabIndex == i, onClick = { tabIndex = i }, text = { Text(t) })
                }
            }
            when (tabIndex) {
                0 -> OpdTab(vm, filters)
                1 -> IpdTab(vm, filters)
                2 -> BedTab(vm, filters)
                3 -> OtherTab(vm, filters)
                4 -> BedDetailTab(vm, filters)
                5 -> PhysServiceTab(vm, filters)
                6 -> PhysIncomeTab(vm, filters)
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showFilters) {
        FilterSheet(vm, filters, onDismiss = { showFilters = false })
    }
}

// ── KPI 列(固定最新月份 + 去年同期，與圖表篩選分離) ──
private data class KpiDef(val title: String, val value: Double, val delta: Double?, val fmt: (Double) -> String)

@Composable
private fun KpiRow(vm: DashboardViewModel) {
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
        KpiDef("平均佔床率", k.occ, delta(k.occ, prev?.occ), Fmt::percent),
        KpiDef("院外門診", k.offsite, delta(k.offsite, prev?.offsite), Fmt::compact),
        KpiDef("洗腎人次", k.dialysis, delta(k.dialysis, prev?.dialysis), Fmt::compact),
        KpiDef("健檢人次", k.checkup, delta(k.checkup, prev?.checkup), Fmt::compact),
    )

    // 標題列：最新月份 + 點擊查看各院區
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = m != null) { showSheet = true }
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "📅 最新月份 ${m?.let { formatMonth(it.first, it.second) } ?: "—"} 與去年同期比較",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        Text("🔍 各院區明細", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary)
    }

    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(defs) { d ->
            KpiCard(d)
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

// ── 各院區單月明細(含去年同期)底部面板 ─────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BranchSummarySheet(
    vm: DashboardViewModel,
    year: String,
    month: String,
    onDismiss: () -> Unit
) {
    val stats by produceState<List<DashboardRepo.BranchMonthStat>>(emptyList(), year, month) {
        value = withContext(Dispatchers.IO) { vm.repo.branchStatsForMonth(year, month) }
    }
    val priorY = year.toIntOrNull()?.minus(1)?.toString() ?: year

    ModalBottomSheet(onDismissRequest = onDismiss) {
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
            Text("🏢 ${s.branch}", fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(2.dp))
            MetricLine("門診人次", s.opd, s.opdPrior, Fmt::compact)
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
private fun KpiCard(d: KpiDef) {
    Card(
        Modifier.width(116.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(d.title, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(d.fmt(d.value), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (d.delta != null) {
                val up = d.delta >= 0
                Text(
                    String.format("%+.1f%%", d.delta),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (up) androidx.compose.ui.graphics.Color(0xFF1E8449)
                    else androidx.compose.ui.graphics.Color(0xFFC0392B)
                )
            } else {
                Text("—", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

// ── 篩選底部面板 ─────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(
    vm: DashboardViewModel,
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

    val yearOpts = remember { vm.availableYears() }
    val monthOpts = remember { vm.availableMonths() }
    val branchOpts = remember { vm.availableBranches() }
    val divOpts = remember { vm.availableDeptDivs() }
    val deptOpts by produceState(emptyList<String>(), divsAll, divs) {
        value = withContext(Dispatchers.IO) {
            vm.availableDepts(if (divsAll || divs.isEmpty()) null else divs)
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
        ) {
            Text("⚙ 篩選條件", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))

            SectionLabel("📅 年度（至少一項）")
            if (years.isEmpty()) {
                Text("⚠️ 請至少選擇一個年度", color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall)
            }
            ChipFlow(yearOpts.map { it to it }, years.toSet()) { opt ->
                years = if (opt in years) years - opt else years + opt
            }

            SectionLabel("📆 月份")
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                FilterChip(selected = monthsAll, onClick = {
                    if (monthsAll) {
                        // 取消全選：展開選項(未勾選=全部)，點選即加入
                        monthsAll = false
                        months = emptyList()
                    } else {
                        monthsAll = true; months = emptyList()
                    }
                }, label = { Text("全選") })
                Spacer(Modifier.width(6.dp))
                if (!monthsAll) {
                    Text("點選月份取消勾選即排除", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline)
                }
            }
            if (!monthsAll) {
                ChipFlow(monthOpts.map { it to "${it}月" }, months.toSet()) { opt ->
                    months = if (opt in months) months - opt else months + opt
                    if (months.isEmpty()) monthsAll = true // 全部取消 → 回到全選
                }
            }

            SectionLabel("🏢 院區")
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                FilterChip(selected = branchesAll, onClick = {
                    if (branchesAll) {
                        // 取消全選：展開選項(未勾選=全部)，點選即加入
                        branchesAll = false
                        branches = emptyList()
                    } else {
                        branchesAll = true; branches = emptyList()
                    }
                }, label = { Text("全選") })
            }
            if (!branchesAll) {
                ChipFlow(branchOpts.map { it to it }, branches.toSet()) { opt ->
                    branches = if (opt in branches) branches - opt else branches + opt
                    if (branches.isEmpty()) branchesAll = true
                }
            }

            SectionLabel("🏥 部別")
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                FilterChip(selected = divsAll, onClick = {
                    if (divsAll) {
                        // 取消全選：展開選項(未勾選=全部)，點選即加入
                        divsAll = false
                        divs = emptyList()
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
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                FilterChip(selected = deptsAll, onClick = {
                    if (deptsAll) {
                        // 取消全選：展開選項(未勾選=全部)，點選即加入
                        deptsAll = false
                        depts = emptyList()
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
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text("顯示去年同期比較", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = yoy, onCheckedChange = { yoy = it })
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = {
                        years = yearOpts.takeLast(3) // 預設最近 3 年
                        monthsAll = true; months = emptyList()
                        branchesAll = true; branches = emptyList()
                        divsAll = true; divs = emptyList()
                        deptsAll = true; depts = emptyList()
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("重設") }
                Button(
                    onClick = {
                        if (years.isEmpty()) return@Button // 需至少一個年度
                        vm.filters.value = DashboardRepo.Filters(
                            years = years,
                            months = if (monthsAll) emptyList() else months,
                            branches = if (branchesAll) emptyList() else branches,
                            deptDivs = if (divsAll) emptyList() else divs,
                            depts = if (deptsAll) emptyList() else depts,
                            showYoy = yoy
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
