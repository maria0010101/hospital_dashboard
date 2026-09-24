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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
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
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.collectAsState
import com.example.hospital_dashboard.ui.adaptive.AdaptiveSheet
import com.example.hospital_dashboard.ui.adaptive.AdaptiveSize
import com.example.hospital_dashboard.ui.adaptive.adaptiveMarquee
import com.example.hospital_dashboard.ui.adaptive.currentAdaptiveSize
import com.example.hospital_dashboard.ui.adaptive.isCompact
import com.example.hospital_dashboard.ui.adaptive.isExpanded
import com.example.hospital_dashboard.ui.adaptive.pick
import com.example.hospital_dashboard.ui.charts.dynamicHBarHeight
import com.example.hospital_dashboard.ui.charts.dynamicLineHeight
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

@Composable
private fun TabGrid(
    columns: Int,
    modifier: Modifier = Modifier,
    content: LazyGridScope.() -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content
    )
}

// ── 可點擊放大之圖表卡片 ─────────────────────────────
@Composable
private fun LineCard(
    vm: DashboardViewModel, title: String, data: LineChartData?,
    height: Dp = 220.dp, fmt: (Double) -> String = Fmt::compact,
    monthDef: BranchMetricDef? = null,
    clickAction: HBarClick = HBarClick.None,
    dynamicHeight: Boolean = true
) {
    val filters by vm.filters.collectAsState()
    val isSingleBranch = (filters.branches.size == 1 || filters.showHospitalTotal) && !title.contains("部別")
    val size = currentAdaptiveSize()
    val effHeight = if (dynamicHeight && isSingleBranch && size.isCompact) dynamicLineHeight(true, height) else height
    ChartCard(title, onClick = data?.let { { vm.openZoom(ChartContent.Line(title, it, fmt, monthDef, clickAction)) } }) {
        data?.let { LineChart(it, height = effHeight, yFormatter = fmt) } ?: LoadingBox()
    }
}

@Composable
private fun HBarCard(
    vm: DashboardViewModel, title: String, data: HBarData?,
    height: Dp = 240.dp, fmt: (Double) -> String = Fmt::compact,
    clickAction: HBarClick = HBarClick.None,
    branchMetricDef: BranchMetricDef? = null,
    dynamicHeight: Boolean = true
) {
    val filters by vm.filters.collectAsState()
    val isSingleBranch = (filters.branches.size == 1 || filters.showHospitalTotal) && !title.contains("部別") && !title.contains("病床類別")
    val size = currentAdaptiveSize()
    val effHeight = when {
        !dynamicHeight || !size.isCompact -> height
        data != null && data.rows.size <= 4 -> dynamicHBarHeight(data.rows.size, height)
        isSingleBranch -> 80.dp
        else -> height
    }
    ChartCard(
        title,
        onClick = data?.let {
            { vm.openZoom(ChartContent.HBar(title, it, fmt, clickAction, branchMetricDef)) }
        }
    ) {
        data?.let { HBarChart(it, height = effHeight, valueFormatter = fmt) } ?: LoadingBox()
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
    val brOpdBar = loadChart(listOf(filters)) { vm.repo.branchOpdBar(filters) }

    val er = loadChart(listOf(filters)) { vm.repo.erMonthly(filters) }
    val brErBar = loadChart(listOf(filters)) { vm.repo.branchErBar(filters) }

    val div = loadChart(listOf(filters)) { vm.repo.deptDivMonthly(filters) }
    val divBar = loadChart(listOf(filters)) { vm.repo.deptDivOpdBar(filters) }

    val firstVisit = loadChart(listOf(filters)) { vm.repo.firstVisitMonthly(filters) }
    val brFirstVisitBar = loadChart(listOf(filters)) { vm.repo.branchFirstVisitBar(filters) }

    var showFirstSheet by remember { mutableStateOf(false) }

    // 判斷是否為單月篩選：使用者篩選單一月份，或資料結果僅單一月份
    val isSingleMonth = filters.months.size == 1 || (opd != null && opd.xLabels.size == 1)
    val monthSuffix = if (filters.months.size == 1) {
        val yStr = if (filters.years.size == 1) "民國${filters.years.first()}年" else ""
        "（$yStr${filters.months.first()}月）"
    } else ""

    val size = currentAdaptiveSize()
    if (size.isCompact) {
        TabColumn {
            // 1. 門診人次月趨勢 / 各院區門診人次
            if (isSingleMonth) {
                HBarCard(
                    vm = vm,
                    title = "各院區門診人次$monthSuffix",
                    data = brOpdBar,
                    height = 240.dp,
                    clickAction = HBarClick.BranchDept
                )
            } else {
                LineCard(vm, "門診人次月趨勢（依院區）", opd, height = 230.dp)
            }

            // 2. 急診人次月趨勢 / 各院區急診人次
            if (isSingleMonth) {
                HBarCard(
                    vm = vm,
                    title = "各院區急診人次$monthSuffix",
                    data = brErBar,
                    height = 240.dp,
                    clickAction = HBarClick.BranchDept
                )
            } else {
                LineCard(vm, "急診人次月趨勢（依院區）", er, height = 200.dp)
            }

            // 3. 各部別門診人次趨勢 (整併原先的科別門診人次 TOP20，改以部別呈現，細項再呈現科別)
            if (isSingleMonth) {
                HBarCard(
                    vm = vm,
                    title = "各部別門診人次$monthSuffix",
                    data = divBar,
                    height = 240.dp,
                    clickAction = HBarClick.DivDept
                )
            } else {
                LineCard(vm, "各部別門診人次趨勢", div, height = 200.dp, clickAction = HBarClick.DivDept)
            }

            // 4. 初診人次 (替代原初診/複診比例圓餅圖)
            if (isSingleMonth) {
                HBarCard(
                    vm = vm,
                    title = "各院區初診人次$monthSuffix",
                    data = brFirstVisitBar,
                    height = 240.dp,
                    clickAction = HBarClick.BranchDept
                )
            } else {
                LineCard(vm, "初診人次月趨勢（依院區）", firstVisit, height = 200.dp)
            }
        }
    } else {
        TabGrid(columns = if (size.isExpanded) 3 else 2) {
            item {
                if (isSingleMonth) {
                    HBarCard(vm = vm, title = "各院區門診人次$monthSuffix", data = brOpdBar, height = 240.dp, clickAction = HBarClick.BranchDept)
                } else {
                    LineCard(vm, "門診人次月趨勢（依院區）", opd, height = 240.dp)
                }
            }
            item {
                if (isSingleMonth) {
                    HBarCard(vm = vm, title = "各院區急診人次$monthSuffix", data = brErBar, height = 240.dp, clickAction = HBarClick.BranchDept)
                } else {
                    LineCard(vm, "急診人次月趨勢（依院區）", er, height = 240.dp)
                }
            }
            item {
                if (isSingleMonth) {
                    HBarCard(vm = vm, title = "各部別門診人次$monthSuffix", data = divBar, height = 240.dp, clickAction = HBarClick.DivDept)
                } else {
                    LineCard(vm, "各部別門診人次趨勢", div, height = 240.dp, clickAction = HBarClick.DivDept)
                }
            }
            item {
                if (isSingleMonth) {
                    HBarCard(vm = vm, title = "各院區初診人次$monthSuffix", data = brFirstVisitBar, height = 240.dp, clickAction = HBarClick.BranchDept)
                } else {
                    LineCard(vm, "初診人次月趨勢（依院區）", firstVisit, height = 240.dp)
                }
            }
        }
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

    AdaptiveSheet(onDismissRequest = onDismiss) {
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
                        modifier = Modifier.weight(1f).adaptiveMarquee(), maxLines = 1)
                    Text("初診 ${Fmt.int(s.firstVisit)}",
                        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                    Spacer(Modifier.width(8.dp))
                    Text("複診 ${Fmt.int(s.returnVisit)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline, maxLines = 1)
                    Spacer(Modifier.width(8.dp))
                    Text("初診率 ${String.format("%.1f%%", s.firstRate)}",
                        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary, maxLines = 1)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ══════════ TAB2 住院服務 ═════════════════════════
@Composable
fun IpdTab(vm: DashboardViewModel, filters: DashboardRepo.Filters) {
    // 1. 住院人日（依院區）
    val admDays = loadChart(listOf(filters)) { vm.repo.ipdAdmissionDaysMonthly(filters) }
    val brAdmDaysBar = loadChart(listOf(filters)) { vm.repo.branchIpdDaysBar(filters) }

    // 2. 住院人次（依院區）
    val ipd = loadChart(listOf(filters)) { vm.repo.ipdMonthly(filters) }
    val brIpdBar = loadChart(listOf(filters)) { vm.repo.branchIpdBar(filters) }

    // 3. 出院人日（依院區）
    val disDays = loadChart(listOf(filters)) { vm.repo.ipdDischargeDaysMonthly(filters) }
    val brDisDaysBar = loadChart(listOf(filters)) { vm.repo.branchDischargeDaysBar(filters) }

    // 4. 出院人次（依院區）
    val dis = loadChart(listOf(filters)) { vm.repo.dischargeMonthly(filters) }
    val brDisBar = loadChart(listOf(filters)) { vm.repo.branchDischargeBar(filters) }

    // 5. 住院人日（依部別）
    val divDays = loadChart(listOf(filters)) { vm.repo.ipdDeptDivDaysMonthly(filters) }
    val brDivDaysBar = loadChart(listOf(filters)) { vm.repo.ipdDeptDivDaysBar(filters) }

    // 6. 平均住院日（依院區）
    val alos = loadChart(listOf(filters)) { vm.repo.alosMonthly(filters) }
    val brAlosBar = loadChart(listOf(filters)) { vm.repo.branchAlosBar(filters) }

    val isSingleMonth = filters.months.size == 1 || (ipd != null && ipd.xLabels.size == 1)
    val monthSuffix = if (filters.months.size == 1) {
        val yStr = if (filters.years.size == 1) "民國${filters.years.first()}年" else ""
        "（$yStr${filters.months.first()}月）"
    } else ""

    val size = currentAdaptiveSize()
    if (size.isCompact) {
        TabColumn {
            // 1. 住院人日月趨勢（依院區）
            if (isSingleMonth) {
                HBarCard(
                    vm = vm,
                    title = "各院區住院人日$monthSuffix",
                    data = brAdmDaysBar,
                    height = 240.dp,
                    clickAction = HBarClick.BranchDept
                )
            } else {
                LineCard(vm, "住院人日月趨勢（依院區）", admDays, height = 220.dp)
            }

            // 2. 住院人次月趨勢（依院區）
            if (isSingleMonth) {
                HBarCard(
                    vm = vm,
                    title = "各院區住院人次$monthSuffix",
                    data = brIpdBar,
                    height = 240.dp,
                    clickAction = HBarClick.BranchDept
                )
            } else {
                LineCard(vm, "住院人次月趨勢（依院區）", ipd, height = 200.dp)
            }

            // 3. 出院人日月趨勢（依院區）
            if (isSingleMonth) {
                HBarCard(
                    vm = vm,
                    title = "各院區出院人日$monthSuffix",
                    data = brDisDaysBar,
                    height = 240.dp,
                    clickAction = HBarClick.BranchDept
                )
            } else {
                LineCard(vm, "出院人日月趨勢（依院區）", disDays, height = 200.dp)
            }

            // 4. 出院人次月趨勢（依院區）
            if (isSingleMonth) {
                HBarCard(
                    vm = vm,
                    title = "各院區出院人次$monthSuffix",
                    data = brDisBar,
                    height = 240.dp,
                    clickAction = HBarClick.BranchDept
                )
            } else {
                LineCard(vm, "出院人次月趨勢（依院區）", dis, height = 200.dp)
            }

            // 5. 住院人日月趨勢（依部別）
            if (isSingleMonth) {
                HBarCard(
                    vm = vm,
                    title = "各部別住院人日$monthSuffix",
                    data = brDivDaysBar,
                    height = 240.dp,
                    clickAction = HBarClick.IpdDivDept
                )
            } else {
                LineCard(vm, "住院人日月趨勢（依部別）", divDays, height = 200.dp, clickAction = HBarClick.IpdDivDept)
            }

            // 6. 平均住院日月趨勢（依院區）
            if (isSingleMonth) {
                HBarCard(
                    vm = vm,
                    title = "各院區平均住院日$monthSuffix",
                    data = brAlosBar,
                    height = 240.dp,
                    fmt = { String.format("%.1f日", it) },
                    clickAction = HBarClick.BranchDept
                )
            } else {
                LineCard(vm, "平均住院日月趨勢（依院區）", alos, height = 200.dp, fmt = { String.format("%.1f日", it) })
            }
        }
    } else {
        TabGrid(columns = if (size.isExpanded) 3 else 2) {
            item {
                if (isSingleMonth) {
                    HBarCard(vm = vm, title = "各院區住院人日$monthSuffix", data = brAdmDaysBar, height = 240.dp, clickAction = HBarClick.BranchDept)
                } else {
                    LineCard(vm, "住院人日月趨勢（依院區）", admDays, height = 240.dp)
                }
            }
            item {
                if (isSingleMonth) {
                    HBarCard(vm = vm, title = "各院區住院人次$monthSuffix", data = brIpdBar, height = 240.dp, clickAction = HBarClick.BranchDept)
                } else {
                    LineCard(vm, "住院人次月趨勢（依院區）", ipd, height = 240.dp)
                }
            }
            item {
                if (isSingleMonth) {
                    HBarCard(vm = vm, title = "各院區出院人日$monthSuffix", data = brDisDaysBar, height = 240.dp, clickAction = HBarClick.BranchDept)
                } else {
                    LineCard(vm, "出院人日月趨勢（依院區）", disDays, height = 240.dp)
                }
            }
            item {
                if (isSingleMonth) {
                    HBarCard(vm = vm, title = "各院區出院人次$monthSuffix", data = brDisBar, height = 240.dp, clickAction = HBarClick.BranchDept)
                } else {
                    LineCard(vm, "出院人次月趨勢（依院區）", dis, height = 240.dp)
                }
            }
            item {
                if (isSingleMonth) {
                    HBarCard(vm = vm, title = "各部別住院人日$monthSuffix", data = brDivDaysBar, height = 240.dp, clickAction = HBarClick.IpdDivDept)
                } else {
                    LineCard(vm, "住院人日月趨勢（依部別）", divDays, height = 240.dp, clickAction = HBarClick.IpdDivDept)
                }
            }
            item {
                if (isSingleMonth) {
                    HBarCard(vm = vm, title = "各院區平均住院日$monthSuffix", data = brAlosBar, height = 240.dp, fmt = { String.format("%.1f日", it) }, clickAction = HBarClick.BranchDept)
                } else {
                    LineCard(vm, "平均住院日月趨勢（依院區）", alos, height = 240.dp, fmt = { String.format("%.1f日", it) })
                }
            }
        }
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
            val defaultOrder = listOf("ICU", "一般", "特殊")
            val chosen = defaultOrder.filter { it in allMajors }
            majors = if (chosen.isNotEmpty()) chosen else allMajors.take(3)
            majorsInit = true
        }
    }
    val effMajors = if (majors.isEmpty()) allMajors else majors

    // 1. 實際佔床率月趨勢（依病床類別）
    val occ = loadChart(listOf(filters, effMajors)) { vm.repo.bedOccMonthly(filters, effMajors) }
    val catOccBar = loadChart(listOf(filters, effMajors)) { vm.repo.bedCategoryOccBar(filters, effMajors) }

    // 2. 實際開床率月趨勢（依病床類別）
    val openRate = loadChart(listOf(filters, effMajors)) { vm.repo.bedOpenRateMonthly(filters, effMajors) }
    val catOpenRateBar = loadChart(listOf(filters, effMajors)) { vm.repo.bedCategoryOpenRateBar(filters, effMajors) }

    // 3. 實際床佔床率月趨勢（依院區）
    val brOcc = loadChart(listOf(filters, effMajors)) { vm.repo.branchBedOccMonthly(filters, effMajors) }
    val brOccBar = loadChart(listOf(filters, effMajors)) { vm.repo.branchBedOccBar(filters, effMajors) }

    // 4. 實際開床率月趨勢（依院區）
    val brOpenRate = loadChart(listOf(filters, effMajors)) { vm.repo.branchBedOpenRateMonthly(filters, effMajors) }
    val brOpenRateBar = loadChart(listOf(filters, effMajors)) { vm.repo.branchBedOpenRateBar(filters, effMajors) }

    // 熱力圖篩選（全部顯示 / 排除其他）與下鑽護理站狀態
    var excludeOther by rememberSaveable { mutableStateOf(true) }
    var selectedBedCat by remember { mutableStateOf<SelectedBedCat?>(null) }
    var selectedDiffBedCat by remember { mutableStateOf<SelectedBedCat?>(null) }

    // 5. 各院區病床類別實際佔床率（％）熱力圖卡片清單（排除其他/全部顯示，最新年月）
    val heatmaps = loadChart(listOf(filters, effMajors, excludeOther)) {
        vm.repo.branchBedCategoryHeatmaps(filters, effMajors, excludeOther)
    }

    // 6. 各院區差額病床實際佔床率（％）熱力圖卡片清單（最新年月，住院人日/實開床天數）
    val diffHeatmaps = loadChart(listOf(filters)) {
        vm.repo.branchDiffBedCategoryHeatmaps(filters)
    }

    // 單月切換判斷
    val isSingleMonth = filters.months.size == 1 || (occ != null && occ.xLabels.size == 1)
    val monthSuffix = if (filters.months.size == 1) {
        val yStr = if (filters.years.size == 1) "民國${filters.years.first()}年" else ""
        "（$yStr${filters.months.first()}月）"
    } else ""

    val size = currentAdaptiveSize()
    if (size.isCompact) {
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

            // 1. 實際床佔床率月趨勢（依病床類別）
            if (isSingleMonth) {
                HBarCard(
                    vm = vm,
                    title = "各病床類別實際佔床率$monthSuffix",
                    data = catOccBar,
                    height = 240.dp,
                    fmt = Fmt::percent
                )
            } else {
                LineCard(vm, "實際佔床率月趨勢（依病床類別）", occ, height = 230.dp, fmt = Fmt::percent)
            }

            // 2. 實際開床率月趨勢（依病床類別）
            if (isSingleMonth) {
                HBarCard(
                    vm = vm,
                    title = "各病床類別實際開床率$monthSuffix",
                    data = catOpenRateBar,
                    height = 240.dp,
                    fmt = Fmt::int
                )
            } else {
                LineCard(vm, "實際開床率月趨勢（依病床類別）", openRate, height = 200.dp, fmt = Fmt::percent)
            }

            // 3. 實際床佔床率月趨勢（依院區）
            if (isSingleMonth) {
                HBarCard(
                    vm = vm,
                    title = "各院區實際佔床率$monthSuffix",
                    data = brOccBar,
                    height = 240.dp,
                    fmt = Fmt::percent,
                    clickAction = HBarClick.BedBranch
                )
            } else {
                LineCard(vm, "實際床佔床率月趨勢（依院區）", brOcc, height = 220.dp, fmt = Fmt::percent)
            }

            // 4. 實際開床率月趨勢（依院區）
            if (isSingleMonth) {
                HBarCard(
                    vm = vm,
                    title = "各院區實際開床率$monthSuffix",
                    data = brOpenRateBar,
                    height = 240.dp,
                    fmt = Fmt::int,
                    clickAction = HBarClick.BedBranch
                )
            } else {
                LineCard(vm, "實際開床率月趨勢（依院區）", brOpenRate, height = 200.dp, fmt = Fmt::percent)
            }

            // 5. 各院區病床類別實際佔床率（％）熱力圖卡片（各院區分開呈現，若篩選不含則不呈現）
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "病床類別熱力圖",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = !excludeOther,
                            onClick = { excludeOther = false },
                            label = { Text("全部顯示") }
                        )
                        FilterChip(
                            selected = excludeOther,
                            onClick = { excludeOther = true },
                            label = { Text("排除其他") }
                        )
                    }
                }
            }

            if (heatmaps != null && heatmaps.isNotEmpty()) {
                heatmaps.forEach { hm ->
                    BedCategoryHeatCard(vm, hm) { cat ->
                        selectedBedCat = SelectedBedCat(hm.branchName, cat, hm.latestYm)
                    }
                }
            }

            // 6. 各院區差額病床實際佔床率（％）熱力圖卡片（各院區分開呈現，若篩選不含則不呈現）
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "差額病床熱力圖",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (diffHeatmaps != null && diffHeatmaps.isNotEmpty()) {
                diffHeatmaps.forEach { hm ->
                    DiffBedCategoryHeatCard(vm, hm) { cat ->
                        selectedDiffBedCat = SelectedBedCat(hm.branchName, cat, hm.latestYm)
                    }
                }
            } else if (diffHeatmaps != null && diffHeatmaps.isEmpty()) {
                EmptyHint("📭 無差額病床資料")
            }
        }
    } else {
        TabGrid(columns = if (size.isExpanded) 3 else 2) {
            item(span = { GridItemSpan(maxLineSpan) }) {
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
            }
            item {
                if (isSingleMonth) {
                    HBarCard(vm = vm, title = "各病床類別實際佔床率$monthSuffix", data = catOccBar, height = 240.dp, fmt = Fmt::percent)
                } else {
                    LineCard(vm, "實際佔床率月趨勢（依病床類別）", occ, height = 240.dp, fmt = Fmt::percent)
                }
            }
            item {
                if (isSingleMonth) {
                    HBarCard(vm = vm, title = "各病床類別實際開床率$monthSuffix", data = catOpenRateBar, height = 240.dp, fmt = Fmt::int)
                } else {
                    LineCard(vm, "實際開床率月趨勢（依病床類別）", openRate, height = 240.dp, fmt = Fmt::percent)
                }
            }
            item {
                if (isSingleMonth) {
                    HBarCard(vm = vm, title = "各院區實際佔床率$monthSuffix", data = brOccBar, height = 240.dp, fmt = Fmt::percent, clickAction = HBarClick.BedBranch)
                } else {
                    LineCard(vm, "實際床佔床率月趨勢（依院區）", brOcc, height = 240.dp, fmt = Fmt::percent)
                }
            }
            item {
                if (isSingleMonth) {
                    HBarCard(vm = vm, title = "各院區實際開床率$monthSuffix", data = brOpenRateBar, height = 240.dp, fmt = Fmt::int, clickAction = HBarClick.BedBranch)
                } else {
                    LineCard(vm, "實際開床率月趨勢（依院區）", brOpenRate, height = 240.dp, fmt = Fmt::percent)
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("病床類別熱力圖", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(selected = !excludeOther, onClick = { excludeOther = false }, label = { Text("全部顯示") })
                            FilterChip(selected = excludeOther, onClick = { excludeOther = true }, label = { Text("排除其他") })
                        }
                    }
                }
            }
            if (heatmaps != null && heatmaps.isNotEmpty()) {
                heatmaps.forEach { hm ->
                    item(span = { GridItemSpan(if (size.isExpanded) 1 else maxLineSpan) }) {
                        BedCategoryHeatCard(vm, hm) { cat ->
                            selectedBedCat = SelectedBedCat(hm.branchName, cat, hm.latestYm)
                        }
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("差額病床熱力圖", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (diffHeatmaps != null && diffHeatmaps.isNotEmpty()) {
                diffHeatmaps.forEach { hm ->
                    item(span = { GridItemSpan(if (size.isExpanded) 1 else maxLineSpan) }) {
                        DiffBedCategoryHeatCard(vm, hm) { cat ->
                            selectedDiffBedCat = SelectedBedCat(hm.branchName, cat, hm.latestYm)
                        }
                    }
                }
            } else if (diffHeatmaps != null && diffHeatmaps.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyHint("📭 無差額病床資料")
                }
            }
        }
    }

    selectedBedCat?.let { sel ->
        BedStationOccSheet(
            vm = vm,
            branch = sel.branch,
            category = sel.category,
            ym = sel.ym,
            onDismiss = { selectedBedCat = null }
        )
    }

    selectedDiffBedCat?.let { sel ->
        DiffBedStationOccSheet(
            vm = vm,
            branch = sel.branch,
            category = sel.category,
            ym = sel.ym,
            onDismiss = { selectedDiffBedCat = null }
        )
    }
}

data class SelectedBedCat(
    val branch: String,
    val category: String,
    val ym: Pair<Int, Int>?
)

/** 院區病床類別實際佔床率熱力圖卡片 */
@Composable
fun BedCategoryHeatCard(
    vm: DashboardViewModel,
    heatmap: DashboardRepo.BranchBedCategoryHeatmap,
    onCategoryClick: ((String) -> Unit)? = null
) {
    val branch = heatmap.branchName
    val campusTitle = if (branch.endsWith("院區") || branch == "全院") "${branch}病床類別實際佔床率（％）" else "${branch}院區病床類別實際佔床率（％）"
    val ymStr = heatmap.latestYm?.let { "最新年月：${it.first}年${it.second.toString().padStart(2, '0')}月" } ?: ""

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    campusTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f).adaptiveMarquee(),
                    maxLines = 1
                )
                if (ymStr.isNotEmpty()) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        ymStr,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1
                    )
                }
            }
            Text(
                "💡 點擊病床類別可查看各護理站佔床率明細",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(Modifier.height(8.dp))

            if (heatmap.categories.isEmpty()) {
                EmptyHint("📭 無病床類別資料")
            } else {
                val rows = heatmap.categories.chunked(2)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    rows.forEach { rowPairs ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            rowPairs.forEach { (cat, rate) ->
                                val bgArgb = vm.repo.occupancyColor(rate)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(bgArgb))
                                        .clickable(enabled = onCategoryClick != null) { onCategoryClick?.invoke(cat) }
                                        .padding(horizontal = 8.dp, vertical = 7.dp)
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            cat,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.Black,
                                            modifier = Modifier.weight(1f).adaptiveMarquee(),
                                            maxLines = 1
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            String.format("%.1f%%", rate),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.Black,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                            if (rowPairs.size == 1) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 護理站佔床率明細底層彈窗（依佔床率由大到小排序） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BedStationOccSheet(
    vm: DashboardViewModel,
    branch: String,
    category: String,
    ym: Pair<Int, Int>?,
    onDismiss: () -> Unit
) {
    val stations = loadChart(listOf(branch, category, ym)) {
        vm.repo.bedCategoryStations(branch, category, ym)
    }
    val ymStr = ym?.let { "民國${it.first}年${it.second.toString().padStart(2, '0')}月" } ?: ""
    val titleBranch = if (branch.endsWith("院區") || branch == "全院") branch else "${branch}院區"
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val maxSheetHeight = (LocalConfiguration.current.screenHeightDp * 0.82f).dp

    AdaptiveSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                "🛏️ $titleBranch · $category 護理站佔床率明細",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (ymStr.isNotEmpty()) {
                Text(
                    "最新年月：$ymStr",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Spacer(Modifier.height(10.dp))

            if (stations == null) {
                Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            } else if (stations.isEmpty()) {
                EmptyHint("📭 無護理站佔床率資料")
            } else {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                ) {
                    stations.forEach { st ->
                        val bgArgb = st.occupancyRate?.let { vm.repo.occupancyColor(it) } ?: 0xFFE0E0E0L
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    val stationLabel = if (branch == "全院") "${st.branch} · ${st.nursingStation}" else st.nursingStation
                                    Text(
                                        stationLabel,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        "實開床數 ${st.openBeds.toInt()} / 登記床數 ${st.registeredBeds.toInt()}" +
                                            if (st.registeredBeds > 0) " (開床率 ${String.format("%.1f%%", st.openRate)})" else "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(bgArgb))
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        st.occupancyRate?.let { String.format("%.1f%%", it) } ?: "-",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(28.dp))
                }
            }
        }
    }
}

/** 院區差額病床實際佔床率熱力圖卡片 */
@Composable
fun DiffBedCategoryHeatCard(
    vm: DashboardViewModel,
    heatmap: DashboardRepo.BranchBedCategoryHeatmap,
    onCategoryClick: ((String) -> Unit)? = null
) {
    val branch = heatmap.branchName
    val campusTitle = if (branch.endsWith("院區") || branch == "全院") "${branch}差額病床實際佔床率（％）" else "${branch}院區差額病床實際佔床率（％）"
    val ymStr = heatmap.latestYm?.let { "最新年月：${it.first}年${it.second.toString().padStart(2, '0')}月" } ?: ""

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    campusTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f).adaptiveMarquee(),
                    maxLines = 1
                )
                if (ymStr.isNotEmpty()) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        ymStr,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1
                    )
                }
            }
            Text(
                "💡 點擊病床可查看各護理站佔床率明細",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(Modifier.height(8.dp))

            if (heatmap.categories.isEmpty()) {
                EmptyHint("📭 無差額病床資料")
            } else {
                val rows = heatmap.categories.chunked(2)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    rows.forEach { rowPairs ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            rowPairs.forEach { (cat, rate) ->
                                val bgArgb = vm.repo.occupancyColor(rate)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(bgArgb))
                                        .clickable(enabled = onCategoryClick != null) { onCategoryClick?.invoke(cat) }
                                        .padding(horizontal = 8.dp, vertical = 7.dp)
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            cat,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.Black,
                                            modifier = Modifier.weight(1f).adaptiveMarquee(),
                                            maxLines = 1
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            String.format("%.1f%%", rate),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.Black,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                            if (rowPairs.size == 1) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 差額病床護理站佔床率明細底層彈窗（依佔床率由大到小排序） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiffBedStationOccSheet(
    vm: DashboardViewModel,
    branch: String,
    category: String,
    ym: Pair<Int, Int>?,
    onDismiss: () -> Unit
) {
    val stations = loadChart(listOf(branch, category, ym)) {
        vm.repo.diffBedCategoryStations(branch, category, ym)
    }
    val ymStr = ym?.let { "民國${it.first}年${it.second.toString().padStart(2, '0')}月" } ?: ""
    val titleBranch = if (branch.endsWith("院區") || branch == "全院") branch else "${branch}院區"
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val maxSheetHeight = (LocalConfiguration.current.screenHeightDp * 0.82f).dp

    AdaptiveSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                "🛏️ $titleBranch · $category 差額病床護理站佔床率明細",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (ymStr.isNotEmpty()) {
                Text(
                    "最新年月：$ymStr",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Spacer(Modifier.height(10.dp))

            if (stations == null) {
                Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            } else if (stations.isEmpty()) {
                EmptyHint("📭 無護理站佔床率資料")
            } else {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                ) {
                    stations.forEach { st ->
                        val bgArgb = st.occupancyRate?.let { vm.repo.occupancyColor(it) } ?: 0xFFE0E0E0L
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    val stationLabel = if (branch == "全院") "${st.branch} · ${st.nursingStation}" else st.nursingStation
                                    Text(
                                        stationLabel,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        "住院人日 ${st.inpatientDays.toInt()} / 實開床天數 ${st.openBedDays.toInt()}" +
                                            if (st.registeredBeds > 0 || st.openBeds > 0) " (實開床數 ${st.openBeds.toInt()} / 登記床數 ${st.registeredBeds.toInt()})" else "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(bgArgb))
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        st.occupancyRate?.let { String.format("%.1f%%", it) } ?: "-",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(28.dp))
                }
            }
        }
    }
}

@Composable
private fun WideTable(data: TableData) {
    val size = currentAdaptiveSize()
    if (size.isCompact) {
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            DataTable(data, Modifier.width(720.dp))
        }
    } else {
        DataTable(data, Modifier.fillMaxWidth())
    }
}

// ══════════ TAB4 其他服務 ═════════════════════════
@Composable
fun OtherTab(vm: DashboardViewModel, filters: DashboardRepo.Filters) {
    // 1. 院外門診部服務量趨勢（依院區）
    val offsite = loadChart(listOf(filters)) { vm.repo.offsiteBranchMonthly(filters) }
    val brOffsiteBar = loadChart(listOf(filters)) { vm.repo.offsiteBranchBar(filters) }

    // 2. 洗腎人次（依院區）
    val dialysis = loadChart(listOf(filters)) { vm.repo.dialysisBranchMonthly(filters) }
    val brDialysisBar = loadChart(listOf(filters)) { vm.repo.dialysisBranchBar(filters) }

    // 3. 健檢人次（依院區）
    val checkup = loadChart(listOf(filters)) { vm.repo.checkupBranchMonthly(filters) }
    val brCheckupBar = loadChart(listOf(filters)) { vm.repo.checkupBranchBar(filters) }

    // 4. 手術人次（依院區）
    val surgery = loadChart(listOf(filters)) { vm.repo.surgeryBranchMonthly(filters) }
    val brSurgeryBar = loadChart(listOf(filters)) { vm.repo.surgeryBranchBar(filters) }

    // 5. 生產人次（依院區）
    val delivery = loadChart(listOf(filters)) { vm.repo.deliveryBranchMonthly(filters) }
    val brDeliveryBar = loadChart(listOf(filters)) { vm.repo.deliveryBranchBar(filters) }

    // 6. 總收入趨勢（依院區）
    val incTotal = loadChart(listOf(filters)) { vm.repo.incomeTotalBranchMonthly(filters) }
    val brIncTotalBar = loadChart(listOf(filters)) { vm.repo.incomeTotalBranchBar(filters) }

    // 7. 自費收入趨勢（依院區）
    val incSelf = loadChart(listOf(filters)) { vm.repo.incomeSelfBranchMonthly(filters) }
    val brIncSelfBar = loadChart(listOf(filters)) { vm.repo.incomeSelfBranchBar(filters) }

    // 8. 各院區總收入（累計，單位千元；重疊橫條含去年同期半透明比對）
    val brIncYoy = loadChart(listOf(filters)) { vm.repo.branchTotalIncomeYoyBar(filters) }

    // 9. 各院區自費收入（累計，單位千元；重疊橫條含去年同期半透明比對）
    val brSelfIncYoy = loadChart(listOf(filters)) { vm.repo.branchSelfPayIncomeYoyBar(filters) }

    // 單月切換判斷
    val isSingleMonth = filters.months.size == 1 || (offsite != null && offsite.xLabels.size == 1)
    val monthSuffix = if (filters.months.size == 1) {
        val yStr = if (filters.years.size == 1) "民國${filters.years.first()}年" else ""
        "（$yStr${filters.months.first()}月）"
    } else ""

    val size = currentAdaptiveSize()
    if (size.isCompact) {
        TabColumn {
            // 1. 院外門診部服務量趨勢（依院區）
            if (isSingleMonth) {
                HBarCard(
                    vm = vm,
                    title = "各院區院外門診部服務量$monthSuffix",
                    data = brOffsiteBar,
                    height = 240.dp,
                    clickAction = HBarClick.OffsiteBranch
                )
            } else {
                LineCard(vm, "院外門診部服務量趨勢（依院區）", offsite, height = 210.dp, clickAction = HBarClick.OffsiteBranch)
            }

            // 2. 洗腎人次（依院區）
            if (isSingleMonth) {
                HBarCard(
                    vm = vm,
                    title = "各院區洗腎人次$monthSuffix",
                    data = brDialysisBar,
                    height = 240.dp,
                    clickAction = HBarClick.BranchDept
                )
            } else {
                LineCard(vm, "洗腎人次月趨勢（依院區）", dialysis, height = 210.dp)
            }

            // 3. 健檢人次（依院區）
            if (isSingleMonth) {
                HBarCard(
                    vm = vm,
                    title = "各院區健檢人次$monthSuffix",
                    data = brCheckupBar,
                    height = 240.dp,
                    clickAction = HBarClick.BranchDept
                )
            } else {
                LineCard(vm, "健檢人次月趨勢（依院區）", checkup, height = 210.dp)
            }

            // 4. 手術人次（依院區）
            if (isSingleMonth) {
                HBarCard(
                    vm = vm,
                    title = "各院區手術人次$monthSuffix",
                    data = brSurgeryBar,
                    height = 240.dp,
                    clickAction = HBarClick.BranchDept
                )
            } else {
                LineCard(vm, "手術人次月趨勢（依院區）", surgery, height = 210.dp)
            }

            // 5. 生產人次（依院區）
            if (isSingleMonth) {
                HBarCard(
                    vm = vm,
                    title = "各院區生產人次$monthSuffix",
                    data = brDeliveryBar,
                    height = 240.dp,
                    clickAction = HBarClick.BranchDept
                )
            } else {
                LineCard(vm, "生產人次月趨勢（依院區）", delivery, height = 210.dp)
            }

            // 6. 總收入趨勢（依院區）
            if (isSingleMonth) {
                HBarCard(
                    vm = vm,
                    title = "各院區總收入$monthSuffix",
                    data = brIncTotalBar,
                    height = 240.dp,
                    clickAction = HBarClick.BranchDept,
                    fmt = Fmt::money
                )
            } else {
                LineCard(vm, "總收入趨勢（依院區）", incTotal, height = 230.dp, fmt = Fmt::money)
            }

            // 7. 自費收入趨勢（依院區）
            if (isSingleMonth) {
                HBarCard(
                    vm = vm,
                    title = "各院區自費收入$monthSuffix",
                    data = brIncSelfBar,
                    height = 240.dp,
                    clickAction = HBarClick.BranchDept,
                    fmt = Fmt::money
                )
            } else {
                LineCard(vm, "自費收入趨勢（依院區）", incSelf, height = 200.dp, fmt = Fmt::money)
            }

            // 8. 各院區總收入（累計，單位千元；重疊橫條含去年同期半透明比對）
            HBarCard(
                vm = vm,
                title = "各院區總收入（累計，單位千元）",
                data = brIncYoy,
                height = 240.dp,
                fmt = Fmt::moneyK
            )

            // 9. 各院區自費收入（累計，單位千元；重疊橫條含去年同期半透明比對）
            HBarCard(
                vm = vm,
                title = "各院區自費收入（累計，單位千元）",
                data = brSelfIncYoy,
                height = 240.dp,
                fmt = Fmt::moneyK
            )
        }
    } else {
        TabGrid(columns = if (size.isExpanded) 3 else 2) {
            item {
                if (isSingleMonth) {
                    HBarCard(vm = vm, title = "各院區院外門診部服務量$monthSuffix", data = brOffsiteBar, height = 240.dp, clickAction = HBarClick.OffsiteBranch)
                } else {
                    LineCard(vm, "院外門診部服務量趨勢（依院區）", offsite, height = 240.dp, clickAction = HBarClick.OffsiteBranch)
                }
            }
            item {
                if (isSingleMonth) {
                    HBarCard(vm = vm, title = "各院區洗腎人次$monthSuffix", data = brDialysisBar, height = 240.dp, clickAction = HBarClick.BranchDept)
                } else {
                    LineCard(vm, "洗腎人次月趨勢（依院區）", dialysis, height = 240.dp)
                }
            }
            item {
                if (isSingleMonth) {
                    HBarCard(vm = vm, title = "各院區健檢人次$monthSuffix", data = brCheckupBar, height = 240.dp, clickAction = HBarClick.BranchDept)
                } else {
                    LineCard(vm, "健檢人次月趨勢（依院區）", checkup, height = 240.dp)
                }
            }
            item {
                if (isSingleMonth) {
                    HBarCard(vm = vm, title = "各院區手術人次$monthSuffix", data = brSurgeryBar, height = 240.dp, clickAction = HBarClick.BranchDept)
                } else {
                    LineCard(vm, "手術人次月趨勢（依院區）", surgery, height = 240.dp)
                }
            }
            item {
                if (isSingleMonth) {
                    HBarCard(vm = vm, title = "各院區生產人次$monthSuffix", data = brDeliveryBar, height = 240.dp, clickAction = HBarClick.BranchDept)
                } else {
                    LineCard(vm, "生產人次月趨勢（依院區）", delivery, height = 240.dp)
                }
            }
            item {
                if (isSingleMonth) {
                    HBarCard(vm = vm, title = "各院區總收入$monthSuffix", data = brIncTotalBar, height = 240.dp, clickAction = HBarClick.BranchDept, fmt = Fmt::money)
                } else {
                    LineCard(vm, "總收入趨勢（依院區）", incTotal, height = 240.dp, fmt = Fmt::money)
                }
            }
            item {
                if (isSingleMonth) {
                    HBarCard(vm = vm, title = "各院區自費收入$monthSuffix", data = brIncSelfBar, height = 240.dp, clickAction = HBarClick.BranchDept, fmt = Fmt::money)
                } else {
                    LineCard(vm, "自費收入趨勢（依院區）", incSelf, height = 240.dp, fmt = Fmt::money)
                }
            }
            item {
                HBarCard(vm = vm, title = "各院區總收入（累計，單位千元）", data = brIncYoy, height = 240.dp, fmt = Fmt::moneyK)
            }
            item {
                HBarCard(vm = vm, title = "各院區自費收入（累計，單位千元）", data = brSelfIncYoy, height = 240.dp, fmt = Fmt::moneyK)
            }
        }
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
