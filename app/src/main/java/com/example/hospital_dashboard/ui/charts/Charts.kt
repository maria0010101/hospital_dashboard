package com.example.hospital_dashboard.ui.charts

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.zIndex
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hospital_dashboard.DashboardViewModel
import com.example.hospital_dashboard.data.BarSegment
import com.example.hospital_dashboard.data.DashboardRepo
import com.example.hospital_dashboard.data.Fmt
import com.example.hospital_dashboard.data.HBarData
import com.example.hospital_dashboard.data.LineChartData
import com.example.hospital_dashboard.data.PieData
import com.example.hospital_dashboard.data.TableData
import com.example.hospital_dashboard.data.VBarData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// ── 顏色 ──────────────────────────────────────────────
val BRANCH_COLORS = mapOf(
    "仁愛" to Color(0xFFE74C3C), "中興" to Color(0xFF3498DB), "和平" to Color(0xFF2ECC71),
    "婦幼" to Color(0xFF9B59B6), "忠孝" to Color(0xFFF39C12), "陽明" to Color(0xFF1ABC9C),
    "松德" to Color(0xFFE67E22), "林森" to Color(0xFF95A5A6), "中醫" to Color(0xFFD35400),
    "昆明" to Color(0xFF7F8C8D), "院本部" to Color(0xFF34495E)
)

val PALETTE = listOf(
    Color(0xFFE74C3C), Color(0xFF3498DB), Color(0xFF2ECC71), Color(0xFF9B59B6),
    Color(0xFFF39C12), Color(0xFF1ABC9C), Color(0xFFE67E22), Color(0xFF95A5A6),
    Color(0xFFD35400), Color(0xFF7F8C8D), Color(0xFF34495E), Color(0xFF16A085),
    Color(0xFFC0392B), Color(0xFF2980B9), Color(0xFF27AE60), Color(0xFF8E44AD)
)

fun seriesColor(name: String, index: Int): Color =
    BRANCH_COLORS[name] ?: PALETTE[index % PALETTE.size]

// ── 圖表內容(可全螢幕橫向放大檢視) ──────────────────
/** 橫條圖點擊列的明細模式。 */
enum class HBarClick { None, DeptBranch, BranchDept }

sealed class ChartContent {
    abstract val title: String

    data class Line(
        override val title: String,
        val data: LineChartData,
        val yFormatter: (Double) -> String = Fmt::compact
    ) : ChartContent()

    data class HBar(
        override val title: String,
        val data: HBarData,
        val valueFormatter: (Double) -> String = Fmt::compact,
        val clickAction: HBarClick = HBarClick.None // 點擊列 → 科別各院區 / 院區各科別
    ) : ChartContent()

    data class VBar(
        override val title: String,
        val data: VBarData,
        val valueFormatter: (Double) -> String = Fmt::compact,
        val clickable: Boolean = false // true = 點擊長條顯示該科別各院區明細
    ) : ChartContent()

    data class Pie(override val title: String, val data: PieData) : ChartContent()

    data class Table(override val title: String, val data: TableData) : ChartContent()
}

// ── 圖表卡片外框(onClick 提供時可點擊放大) ─────────
@Composable
fun ChartCard(
    title: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val clickMod = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Card(
        modifier = modifier.fillMaxWidth().then(clickMod),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f))
                if (onClick != null) {
                    Text("🔍 點擊放大", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(6.dp))
            content()
        }
    }
}

@Composable
fun EmptyHint(text: String = "📭 無資料") {
    Box(Modifier.fillMaxWidth().height(72.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.outline)
    }
}

// ── 全螢幕橫向圖表檢視(可縮放) ─────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZoomChartScreen(vm: DashboardViewModel, content: ChartContent, onClose: () -> Unit) {
    val context = LocalContext.current
    val activity = context.findActivity()

    // 轉為橫向；關閉時恢復直向
    LaunchedEffect(Unit) {
        activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }
    DisposableEffect(Unit) {
        onDispose {
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }
    BackHandler(onBack = onClose)

    // 標題列在上、圖表在下：兩者完全不重疊，避免手勢偵測干擾關閉按鈕
    Column(Modifier.fillMaxSize().background(Color.White)) {
        // 整條標題列皆可點擊關閉(含 ✕ 按鈕)
        // statusBarsPadding：橫向時狀態列會蓋住頂部 0~104px，需將標題列下推避免點擊被攔截
        // zIndex：圖表放大時 graphicsLayer 會擴大觸控範圍，需確保標題列優先接收點擊
        Row(
            Modifier
                .fillMaxWidth()
                .zIndex(1f)
                .background(Color(0xFFF2F2F2))
                .statusBarsPadding()
                .clickable(onClick = onClose)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "📊 ${content.title}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
            Text(
                "✕ 關閉",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
        }

        // 圖表區域(依可用高度填滿)
        var heightPx by remember { mutableIntStateOf(0) }
        val density = LocalDensity.current
        val chartHeight = with(density) { heightPx.toDp() }

        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clipToBounds() // 縮放時限制於圖表區，避免蓋住標題列
                .onSizeChanged { heightPx = it.height }
        ) {
            var pointInfo by remember(content) { mutableStateOf<PointInfo?>(null) }

            when (content) {
                is ChartContent.Line -> {
                    ZoomableBox {
                        LineChart(
                            content.data,
                            height = chartHeight.coerceAtLeast(160.dp),
                            yFormatter = content.yFormatter,
                            interactive = true,
                            onPointSelected = { pointInfo = it }
                        )
                    }
                    // 點擊資料點 → 詳細數據工具卡(固定不隨縮放)
                    pointInfo?.let { info ->
                        LineTooltipCard(
                            info, content.yFormatter,
                            modifier = Modifier.align(Alignment.BottomCenter),
                            onDismiss = { pointInfo = null }
                        )
                    }
                }
                is ChartContent.HBar -> {
                    var selectedRow by remember(content) { mutableStateOf<String?>(null) }
                    ZoomableBox {
                        HBarChart(
                            content.data,
                            height = chartHeight.coerceAtLeast(160.dp),
                            valueFormatter = content.valueFormatter,
                            interactive = content.clickAction != HBarClick.None,
                            onRowSelected = { idx ->
                                val name = content.data.rows.getOrNull(idx)?.name
                                if (name != null && content.clickAction != HBarClick.None) {
                                    selectedRow = name
                                }
                            }
                        )
                    }
                    when (content.clickAction) {
                        HBarClick.DeptBranch -> selectedRow?.let { dept ->
                            DeptOpdCard(
                                vm, dept,
                                modifier = Modifier.align(Alignment.BottomCenter),
                                onDismiss = { selectedRow = null }
                            )
                        }
                        HBarClick.BranchDept -> selectedRow?.let { branch ->
                            BranchDeptCard(
                                vm, branch,
                                modifier = Modifier.align(Alignment.BottomCenter),
                                onDismiss = { selectedRow = null }
                            )
                        }
                        HBarClick.None -> {}
                    }
                }
                is ChartContent.VBar -> {
                    var selectedDept by remember(content) { mutableStateOf<String?>(null) }
                    ZoomableBox {
                        VBarChart(
                            content.data,
                            height = chartHeight.coerceAtLeast(160.dp),
                            valueFormatter = content.valueFormatter,
                            interactive = content.clickable,
                            onBarSelected = { idx ->
                                if (content.clickable) {
                                    selectedDept = content.data.groups.getOrNull(idx)?.label
                                }
                            }
                        )
                    }
                    // 點擊科別長條 → 各院區明細(與去年同期比較)
                    selectedDept?.let { dept ->
                        DeptBranchCard(
                            vm, dept,
                            modifier = Modifier.align(Alignment.BottomCenter),
                            onDismiss = { selectedDept = null }
                        )
                    }
                }
                is ChartContent.Pie ->
                    ZoomableBox {
                        PieChart(content.data, height = chartHeight.coerceAtLeast(160.dp))
                    }
                is ChartContent.Table ->
                    TableZoom(content.data)
            }

        }

        // 底部提示(置於圖表外頁尾，避免與 X 軸標籤重疊)
        Text(
            if (content is ChartContent.Table) "可上下左右捲動檢視" else "雙指縮放 ｜ 拖曳移動 ｜ 雙擊還原 ｜ 點擊資料點/長條看明細",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 4.dp),
            textAlign = TextAlign.Center
        )
    }
}

/** 可縮放/平移容器：雙指縮放(1~8x)、拖曳、雙擊切換 2.5x。 */
@Composable
private fun ZoomableBox(content: @Composable () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offsetX
                translationY = offsetY
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 8f)
                    offsetX += pan.x
                    offsetY += pan.y
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    if (scale > 1f) {
                        scale = 1f; offsetX = 0f; offsetY = 0f
                    } else {
                        scale = 2.5f; offsetX = 0f; offsetY = 0f
                    }
                })
            },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/** 表格類內容：橫向全螢幕 + 可捲動。 */
@Composable
private fun TableZoom(data: TableData) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(top = 44.dp, bottom = 40.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            DataTable(data, Modifier.width(960.dp))
        }
    }
}

/** 資料點工具卡：顯示該 x 點各序列數值與去年同期增減比率。 */
@Composable
private fun LineTooltipCard(
    info: PointInfo,
    yFormatter: (Double) -> String,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit
) {
    Card(
        modifier
            .fillMaxWidth(0.94f)
            .padding(10.dp)
            .clickable(onClick = onDismiss), // 點工具卡任意處即關閉
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFFFF)),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            Modifier
                .heightIn(max = 240.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "📅 ${info.xLabel}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "✕",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.outline,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(onClick = onDismiss).padding(4.dp)
                )
            }
            Spacer(Modifier.height(4.dp))
            info.items.forEachIndexed { i, item ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(9.dp).clip(RoundedCornerShape(2.dp)).background(seriesColor(item.seriesName, i)))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        item.seriesName,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        yFormatter(item.value),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(8.dp))
                    if (item.prior != null) {
                        Text(
                            "去年 ${yFormatter(item.prior)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    if (item.deltaPct != null) {
                        val up = item.deltaPct >= 0
                        Text(
                            (if (up) "▲ " else "▼ ") + String.format("%+.1f%%", item.deltaPct),
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
    }
}

/** 科別門診各院區明細卡片(點擊「科別門診人次 vs 診次」科別列顯示)。 */
@Composable
private fun DeptOpdCard(
    vm: DashboardViewModel,
    dept: String,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit
) {
    val stats by produceState<List<DashboardRepo.DeptOpdBranchStat>>(emptyList(), dept) {
        value = withContext(Dispatchers.IO) { vm.repo.deptOpdBranchStats(vm.filters.value, dept) }
    }
    val periodNote = periodNoteOf(vm)

    Card(
        modifier
            .fillMaxWidth(0.94f)
            .padding(10.dp)
            .clickable(onClick = onDismiss),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFFFF)),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            Modifier
                .heightIn(max = 320.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "🏥 $dept 各院區明細",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text("✕", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.outline, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(onClick = onDismiss).padding(4.dp))
            }
            if (periodNote.isNotEmpty()) {
                Text(periodNote, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline)
            }
            Spacer(Modifier.height(4.dp))
            stats.forEach { s ->
                Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text("🏢 ${s.branch}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    DeptMetricLine("門診人次", s.opd, s.opdPrior, Fmt::compact, pct = true)
                    DeptMetricLine("總診次", s.sessions, s.sessionsPrior, Fmt::compact, pct = true)
                }
            }
        }
    }
}

/** 院區門診各科別明細卡片(點擊「各院區門診人次」院區列顯示)。 */
@Composable
private fun BranchDeptCard(
    vm: DashboardViewModel,
    branch: String,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit
) {
    val stats by produceState<List<DashboardRepo.BranchOpdDeptStat>>(emptyList(), branch) {
        value = withContext(Dispatchers.IO) { vm.repo.branchOpdDeptStats(vm.filters.value, branch) }
    }
    val periodNote = periodNoteOf(vm)

    Card(
        modifier
            .fillMaxWidth(0.94f)
            .padding(10.dp)
            .clickable(onClick = onDismiss),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFFFF)),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            Modifier
                .heightIn(max = 320.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "🏢 $branch 各科別門診人次",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text("✕", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.outline, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(onClick = onDismiss).padding(4.dp))
            }
            if (periodNote.isNotEmpty()) {
                Text(periodNote, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline)
            }
            Spacer(Modifier.height(4.dp))
            stats.forEach { s ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(s.dept, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                    Text(Fmt.compact(s.opd), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    if (s.opdPrior != null) {
                        Text("去年 ${Fmt.compact(s.opdPrior)}", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline)
                        Spacer(Modifier.width(8.dp))
                        if (s.opdPrior != 0.0) {
                            val d = (s.opd - s.opdPrior) / s.opdPrior * 100.0
                            val up = d >= 0
                            Text(
                                (if (up) "▲ " else "▼ ") + String.format("%+.1f%%", d),
                                style = MaterialTheme.typography.labelSmall,
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
        }
    }
}

/** 篩選區間說明文字。 */
private fun periodNoteOf(vm: DashboardViewModel): String {
    val years = vm.filters.value.years.mapNotNull { it.toIntOrNull() }
    return if (years.isNotEmpty()) {
        val minY = years.min(); val maxY = years.max()
        if (vm.filters.value.showYoy) {
            "篩選區間 民國${minY}-${maxY}年 累計 ｜ 與去年同期 民國${minY - 1}-${maxY - 1}年 比較"
        } else {
            "篩選區間 民國${minY}-${maxY}年 累計"
        }
    } else ""
}

/** 科別各院區明細卡片(點擊科別長條顯示)。 */
@Composable
private fun DeptBranchCard(
    vm: DashboardViewModel,
    dept: String,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit
) {
    val stats by produceState<List<DashboardRepo.DeptBranchStat>>(emptyList(), dept) {
        value = withContext(Dispatchers.IO) { vm.repo.deptBranchStats(vm.filters.value, dept) }
    }
    val periodNote = periodNoteOf(vm)

    Card(
        modifier
            .fillMaxWidth(0.94f)
            .padding(10.dp)
            .clickable(onClick = onDismiss),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFFFF)),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            Modifier
                .heightIn(max = 320.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "🏥 $dept 各院區明細",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "✕",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.outline,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(onClick = onDismiss).padding(4.dp)
                )
            }
            if (periodNote.isNotEmpty()) {
                Text(periodNote, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline)
            }
            Spacer(Modifier.height(4.dp))
            stats.forEach { s ->
                Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text("🏢 ${s.branch}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    DeptMetricLine("住院人次", s.adm, s.admPrior, Fmt::compact, pct = true)
                    DeptMetricLine("住院人日", s.days, s.daysPrior, Fmt::compact, pct = true)
                    DeptMetricLine(
                        "平均住院日", s.los, s.losPrior,
                        { String.format("%.1f天", it) },
                        pct = false
                    )
                }
            }
        }
    }
}

@Composable
private fun DeptMetricLine(
    name: String,
    value: Double,
    prior: Double?,
    fmt: (Double) -> String,
    pct: Boolean
) {
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, top = 1.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(name, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
        Text(fmt(value), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        if (prior != null) {
            Text("去年 ${fmt(prior)}", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.width(8.dp))
            val delta = if (pct) {
                if (prior != 0.0) (value - prior) / prior * 100.0 else null
            } else value - prior
            if (delta != null) {
                val up = delta >= 0
                Text(
                    (if (up) "▲ " else "▼ ") +
                        (if (pct) String.format("%+.1f%%", delta) else String.format("%+.1f天", delta)),
                    style = MaterialTheme.typography.labelSmall,
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

/** 從 Context 找回 Activity(用於切換螢幕方向)。 */
fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend(names: List<String>) {
    if (names.isEmpty()) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        names.forEachIndexed { i, n ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp))
                    .background(seriesColor(n, i)))
                Spacer(Modifier.width(4.dp))
                Text(n, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

// ── 折線圖 ───────────────────────────────────────────

/** 單一序列在該 x 點的資料(含去年同期增減)。 */
data class PointItem(
    val seriesName: String,
    val value: Double,
    val prior: Double?,
    val deltaPct: Double? // 去年同期增減比率(%)
)

/** 某 x 點的詳細資料(所有本期序列)。 */
data class PointInfo(val xLabel: String, val items: List<PointItem>)

/** 折線圖幾何參數(繪圖與點擊命中共用)。 */
private class LineGeo(
    val labelW: Float, val chartW: Float, val topPad: Float, val chartH: Float,
    val bottomH: Float, val yMin: Double, val yMax: Double, val yRange: Double
) {
    fun xPos(i: Int, n: Int): Float = labelW + chartW * i / (n - 1).coerceAtLeast(1)
    fun yPos(v: Double): Float = topPad + chartH * ((yMax - v) / yRange).toFloat()
}

private fun computeLineGeo(size: Size, data: LineChartData, density: Density): LineGeo {
    val labelW = with(density) { 46.dp.toPx() }
    val bottomH = with(density) { 18.dp.toPx() }
    val topPad = with(density) { 8.dp.toPx() }
    val chartW = size.width - labelW - with(density) { 4.dp.toPx() }
    val chartH = size.height - bottomH - topPad
    val allVals = data.series.flatMap { it.values }.filterNotNull()
    val rawMin = allVals.minOrNull() ?: 0.0
    val rawMax = allVals.maxOrNull() ?: 1.0
    val range = (rawMax - rawMin).coerceAtLeast(1e-9)
    // 資料全為正值時從 0 開始，避免負數軸標籤
    val yMin = if (rawMin >= 0) 0.0 else rawMin - range * 0.08
    val yMax = rawMax + range * 0.08
    val yRange = (yMax - yMin).coerceAtLeast(1e-9)
    return LineGeo(labelW, chartW, topPad, chartH, bottomH, yMin, yMax, yRange)
}

/**
 * 被動監聽單指點擊(不 consume 事件，避免干擾外層縮放/雙擊手勢)。
 * 僅在單指按下→放開且移動極小時觸發 onTap。
 */
private suspend fun AwaitPointerEventScope.passiveTapListener(onTap: (Offset) -> Unit) {
    var downPos: Offset? = null
    var downCount = 0
    var multi = false
    while (true) {
        val event = awaitPointerEvent()
        val pressed = event.changes.count { it.pressed }
        val newDown = event.changes.filter { it.pressed && !it.previousPressed }
        val newUp = event.changes.filter { !it.pressed && it.previousPressed }
        if (newDown.isNotEmpty()) {
            if (downCount > 0 || pressed > 1) multi = true
            downCount = pressed
            downPos = newDown.first().position
        }
        if (newUp.isNotEmpty() && !multi && downPos != null) {
            val pos = newUp.first().position
            if ((pos - downPos).getDistance() <= 24f) onTap(pos)
            downPos = null; downCount = 0; multi = false
        }
        if (pressed == 0) { downPos = null; downCount = 0; multi = false }
    }
}

/** 依點擊位置找最近的資料點(只以本期序列為目標)，回傳該 x 的全部序列明細。 */
private fun findNearestPoint(data: LineChartData, tap: Offset, size: IntSize, density: Density): PointInfo? {
    if (size.width <= 0 || size.height <= 0) return null
    val geo = computeLineGeo(Size(size.width.toFloat(), size.height.toFloat()), data, density)
    val n = data.xLabels.size
    val xSpacing = geo.chartW / (n - 1).coerceAtLeast(1)

    var bestIdx = -1
    var bestDist = Float.MAX_VALUE
    for (s in data.series) {
        if (s.dashed) continue
        for (xi in s.values.indices) {
            val v = s.values[xi] ?: continue
            val dx = tap.x - geo.xPos(xi, n)
            if (abs(dx) > xSpacing * 0.6f) continue // 需靠近該 x 欄位
            val dy = tap.y - geo.yPos(v)
            if (abs(dy) > 90f) continue // 需靠近線條(離線太遠不選點)
            val d = dx * dx + dy * dy * 1.2f
            if (d < bestDist) { bestDist = d; bestIdx = xi }
        }
    }
    if (bestIdx < 0) return null

    // 去年同期 = (年-1, 同月) 的 x 索引(圖表 x 軸為 民國年月)
    val ym = parseYmLabel(data.xLabels.getOrNull(bestIdx) ?: "")
    val priorIdx = ym?.let { (y, m) ->
        data.xLabels.indexOfFirst { it.startsWith("${y - 1}年${m}月") }
    } ?: -1

    val items = data.series.filter { !it.dashed }.mapNotNull { s ->
        val v = s.values.getOrNull(bestIdx) ?: return@mapNotNull null
        val priorSeries = data.series.firstOrNull { it.dashed && it.name == "${s.name}(去年)" }
        val p = if (priorIdx >= 0) priorSeries?.values?.getOrNull(priorIdx) else null
        val delta = if (v != 0.0 && p != null && p != 0.0 && !p.isNaN()) (v - p) / p * 100.0 else null
        PointItem(s.name, v, p, delta)
    }
    return PointInfo(data.xLabels[bestIdx], items)
}

/** 解析「114年11月 (2025/11)」→ (114, 11)。 */
private fun parseYmLabel(label: String): Pair<Int, Int>? {
    val m = Regex("""^(\d+)年(\d+)月""").find(label) ?: return null
    val y = m.groupValues[1].toIntOrNull() ?: return null
    val mo = m.groupValues[2].toIntOrNull() ?: return null
    return y to mo
}

@Composable
fun LineChart(
    data: LineChartData,
    height: Dp = 220.dp,
    yFormatter: (Double) -> String = Fmt::compact,
    interactive: Boolean = false,
    onPointSelected: ((PointInfo?) -> Unit)? = null
) {
    if (data.xLabels.isEmpty() || data.series.isEmpty()) {
        EmptyHint(); return
    }
    Column {
        Legend(data.series.map { it.name })
        Spacer(Modifier.height(4.dp))
        val textMeasurer = rememberTextMeasurer()
        val labelStyle = TextStyle(fontSize = 9.sp, color = MaterialTheme.colorScheme.outline)
        var canvasSize by remember { mutableStateOf(IntSize.Zero) }
        val density = LocalDensity.current

        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
                .onSizeChanged { canvasSize = it }
                .then(
                    if (interactive) Modifier.pointerInput(data, canvasSize) {
                        awaitPointerEventScope {
                            passiveTapListener { pos ->
                                onPointSelected?.invoke(
                                    findNearestPoint(data, pos, canvasSize, density)
                                )
                            }
                        }
                    } else Modifier
                )
        ) {
            val geo = computeLineGeo(size, data, this)
            val labelH = 16.dp.toPx()

            // 水平網格 + Y 標籤
            val gridN = 4
            for (i in 0..gridN) {
                val t = i.toFloat() / gridN
                val y = geo.topPad + geo.chartH * t
                val v = geo.yMax - (geo.yMax - geo.yMin) * t
                drawLine(Color(0xFFE0E0E0), Offset(geo.labelW, y), Offset(geo.labelW + geo.chartW, y), strokeWidth = 1f)
                drawText(textMeasurer, yFormatter(v), topLeft = Offset(2.dp.toPx(), y - labelH / 2), style = labelStyle)
            }

            // X 標籤(最多 6 個)
            val n = data.xLabels.size
            val step = max(1, n / 6)
            for (i in 0 until n step step) {
                val x = geo.xPos(i, n)
                drawText(textMeasurer, data.xLabels[i], topLeft = Offset(x - 18.dp.toPx(), size.height - geo.bottomH + 2.dp.toPx()), style = labelStyle)
            }

            // 序列
            data.series.forEachIndexed { si, s ->
                val color = seriesColor(s.name, si)
                val dash = if (s.dashed) PathEffect.dashPathEffect(floatArrayOf(10f, 10f)) else null
                val path = androidx.compose.ui.graphics.Path()
                var started = false
                s.values.forEachIndexed { i, v ->
                    if (v != null) {
                        val x = geo.xPos(i, n); val y = geo.yPos(v)
                        if (!started) { path.moveTo(x, y); started = true } else path.lineTo(x, y)
                        if (!s.dashed) drawCircle(color, radius = 2.6.dp.toPx(), center = Offset(x, y))
                    } else started = false
                }
                drawPath(path, color, style = Stroke(width = 2.dp.toPx(), pathEffect = dash))
            }
        }
    }
}

// ── 橫條圖 ───────────────────────────────────────────

/** 依點擊位置找橫條圖的列索引。 */
private fun findRowIndex(data: HBarData, tap: Offset, size: IntSize, density: Density): Int? {
    if (size.width <= 0 || size.height <= 0 || data.rows.isEmpty()) return null
    val nameW = with(density) { 88.dp.toPx() }
    val gap = with(density) { 6.dp.toPx() }
    val barH = (size.height / data.rows.size).toFloat().coerceAtMost(with(density) { 26.dp.toPx() })
    val topPad = ((size.height - barH * data.rows.size) / 2).coerceAtLeast(0f)
    if (tap.x < nameW + gap) return null
    val idx = ((tap.y - topPad) / barH).toInt()
    if (idx < 0 || idx >= data.rows.size) return null
    return idx
}

@Composable
fun HBarChart(
    data: HBarData,
    height: Dp = 240.dp,
    valueFormatter: (Double) -> String = Fmt::compact,
    interactive: Boolean = false,
    onRowSelected: ((Int) -> Unit)? = null
) {
    if (data.rows.isEmpty()) { EmptyHint(); return }
    val segNames = data.rows.flatMap { it.segments.map { s -> s.label } }.distinct()
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    Column {
        if (segNames.size > 1) { Legend(segNames); Spacer(Modifier.height(4.dp)) }
        val textMeasurer = rememberTextMeasurer()
        val nameStyle = TextStyle(fontSize = 10.sp, color = onSurfaceColor)
        val valStyle = TextStyle(fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold)
        val targetStyle = TextStyle(fontSize = 9.sp, color = Color(0xFFE74C3C))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
                .onSizeChanged { canvasSize = it }
                .then(
                    if (interactive) Modifier.pointerInput(data, canvasSize) {
                        awaitPointerEventScope {
                            passiveTapListener { pos ->
                                onRowSelected?.invoke(findRowIndex(data, pos, canvasSize, density) ?: return@passiveTapListener)
                            }
                        }
                    } else Modifier
                )
        ) {
            val nameW = 88.dp.toPx()
            val gap = 6.dp.toPx()
            val barH = (size.height / data.rows.size).coerceAtMost(26.dp.toPx())
            val topPad = ((size.height - barH * data.rows.size) / 2).coerceAtLeast(0f)
            val chartW = size.width - nameW - gap - 6.dp.toPx()

            val maxVal = data.rows.flatMap { it.segments.map { s -> s.value } }.maxOrNull()?.coerceAtLeast(1e-9) ?: 1.0
            val target = data.targetLine
            val scaleMax = max(maxVal, target ?: 0.0) * 1.15

            fun xOf(v: Double) = (v / scaleMax * chartW).toFloat()

            // 目標線
            if (target != null) {
                val tx = xOf(target)
                drawLine(Color(0xFFE74C3C), Offset(nameW + gap + tx, 0f), Offset(nameW + gap + tx, size.height),
                    strokeWidth = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
                drawText(textMeasurer, "目標 $target", topLeft = Offset(nameW + gap + tx + 3.dp.toPx(), 2.dp.toPx()), style = targetStyle)
            }

            data.rows.forEachIndexed { ri, row ->
                val y = topPad + ri * barH
                drawText(textMeasurer, row.name, topLeft = Offset(2.dp.toPx(), y + barH / 2 - 7.dp.toPx()), style = nameStyle)

                val barW = xOf(row.segments.sumOf { it.value })
                val colors = row.segments.mapIndexed { si, _ -> seriesColor(row.segments[si].label, si) }
                if (data.grouped) {
                    // 群組：各段並排
                    val segW = barW / row.segments.size
                    row.segments.forEachIndexed { si, s ->
                        val x = nameW + gap + si * segW
                        val w = xOf(s.value)
                        drawRoundRect(colors[si], Offset(x, y + 3.dp.toPx()), Size(w.coerceAtLeast(1f), barH - 6.dp.toPx()),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
                        if (w > 30.dp.toPx()) drawText(textMeasurer, valueFormatter(s.value), topLeft = Offset(x + 3.dp.toPx(), y + barH / 2 - 7.dp.toPx()), style = valStyle)
                    }
                } else {
                    // 堆疊或單段
                    var acc = 0f
                    row.segments.forEachIndexed { si, s ->
                        val x = nameW + gap + acc
                        val w = xOf(s.value)
                        drawRoundRect(colors[si], Offset(x, y + 3.dp.toPx()), Size(w.coerceAtLeast(1f), barH - 6.dp.toPx()),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
                        acc += w
                    }
                    if (barW > 34.dp.toPx() && row.segments.size == 1) {
                        drawText(textMeasurer, valueFormatter(row.segments[0].value),
                            topLeft = Offset(nameW + gap + barW + 4.dp.toPx(), y + barH / 2 - 7.dp.toPx()),
                            style = TextStyle(fontSize = 9.sp, color = onSurfaceColor))
                    }
                }
            }
        }
    }
}

// ── 直條圖 ───────────────────────────────────────────

/** 依點擊位置找直條圖的群組索引。 */
private fun findBarIndex(data: VBarData, tap: Offset, size: IntSize, density: Density): Int? {
    if (size.width <= 0 || size.height <= 0 || data.groups.isEmpty()) return null
    val labelW = with(density) { 46.dp.toPx() }
    val bottomH = with(density) { 18.dp.toPx() }
    val topPad = with(density) { 10.dp.toPx() }
    val chartW = size.width - labelW - with(density) { 4.dp.toPx() }
    val chartH = size.height - bottomH - topPad
    if (tap.x < labelW || tap.x > labelW + chartW) return null
    if (tap.y < topPad || tap.y > topPad + chartH) return null
    val n = data.groups.size
    val idx = ((tap.x - labelW) / (chartW / n)).toInt().coerceIn(0, n - 1)
    return idx
}

@Composable
fun VBarChart(
    data: VBarData,
    height: Dp = 240.dp,
    valueFormatter: (Double) -> String = Fmt::compact,
    interactive: Boolean = false,
    onBarSelected: ((Int) -> Unit)? = null
) {
    if (data.groups.isEmpty()) { EmptyHint(); return }
    val segNames = data.groups.flatMap { it.segments.map { s -> s.label } }.distinct()
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    Column {
        if (segNames.size > 1) { Legend(segNames); Spacer(Modifier.height(4.dp)) }
        val textMeasurer = rememberTextMeasurer()
        val labelStyle = TextStyle(fontSize = 9.sp, color = MaterialTheme.colorScheme.outline)
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
                .onSizeChanged { canvasSize = it }
                .then(
                    if (interactive) Modifier.pointerInput(data, canvasSize) {
                        awaitPointerEventScope {
                            passiveTapListener { pos ->
                                onBarSelected?.invoke(findBarIndex(data, pos, canvasSize, density) ?: return@passiveTapListener)
                            }
                        }
                    } else Modifier
                )
        ) {
            val labelW = 46.dp.toPx()
            val bottomH = 18.dp.toPx()
            val topPad = 10.dp.toPx()
            val chartW = size.width - labelW - 4.dp.toPx()
            val chartH = size.height - bottomH - topPad

            val maxVal = data.groups.flatMap { it.segments.map { s -> s.value } }.maxOrNull()?.coerceAtLeast(1e-9) ?: 1.0
            val scaleMax = maxVal * 1.15
            val n = data.groups.size
            val groupW = chartW / n.coerceAtLeast(1)
            val barW = (groupW * 0.6f) / if (data.stacked) 1 else segNames.size.coerceAtLeast(1)

            // 網格 + Y 標籤
            for (i in 0..4) {
                val t = i / 4f
                val y = topPad + chartH * t
                val v = scaleMax * (1 - t)
                drawLine(Color(0xFFE0E0E0), Offset(labelW, y), Offset(labelW + chartW, y), strokeWidth = 1f)
                drawText(textMeasurer, valueFormatter(v), topLeft = Offset(2.dp.toPx(), y - 7.dp.toPx()), style = labelStyle)
            }
            // X 標籤：依標籤寬度與可用寬度自動決定間距(空間足夠時顯示全部科別名稱)
            val maxLabelW = data.groups.maxOfOrNull { g ->
                textMeasurer.measure(AnnotatedString(g.label), labelStyle).size.width
            } ?: 0
            val step = max(1, kotlin.math.ceil(maxLabelW / groupW.coerceAtLeast(1f)).toInt())
            for (i in 0 until n step step) {
                val x = labelW + groupW * i + groupW / 2
                drawText(textMeasurer, data.groups[i].label, topLeft = Offset(x - 18.dp.toPx(), size.height - bottomH + 2.dp.toPx()), style = labelStyle)
            }

            data.groups.forEachIndexed { gi, g ->
                val x0 = labelW + groupW * gi
                if (data.stacked) {
                    var acc = 0f
                    g.segments.forEachIndexed { si, s ->
                        val h = (s.value / scaleMax * chartH).toFloat()
                        val color = seriesColor(s.label, si)
                        drawRect(color, Offset(x0 + groupW * 0.2f, topPad + chartH - acc - h),
                            Size(groupW * 0.6f, h.coerceAtLeast(0.5f)))
                        acc += h
                    }
                } else {
                    g.segments.forEachIndexed { si, s ->
                        val h = (s.value / scaleMax * chartH).toFloat()
                        val color = seriesColor(s.label, si)
                        drawRect(color, Offset(x0 + groupW * 0.2f + si * barW, topPad + chartH - h),
                            Size(barW * 0.8f, h.coerceAtLeast(0.5f)))
                    }
                }
            }
        }
    }
}

// ── 圓餅圖 ───────────────────────────────────────────
@Composable
fun PieChart(data: PieData, height: Dp = 180.dp) {
    if (data.slices.isEmpty()) { EmptyHint(); return }
    val total = data.slices.sumOf { it.value }.coerceAtLeast(1e-9)
    Column {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val r = min(size.width, size.height) / 2 - 6.dp.toPx()
            val c = Offset(size.width / 2, size.height / 2)
            var start = -90f
            data.slices.forEachIndexed { i, s ->
                val sweep = (s.value / total * 360f).toFloat()
                drawArc(
                    color = seriesColor(s.label, i),
                    startAngle = start, sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(c.x - r, c.y - r),
                    size = Size(r * 2, r * 2),
                    style = Stroke(width = r * 0.42f)
                )
                start += sweep
            }
        }
        data.slices.forEachIndexed { i, s ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(seriesColor(s.label, i)))
                Spacer(Modifier.width(6.dp))
                Text(s.label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                Text(Fmt.int(s.value), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text(String.format("%.1f%%", s.value / total * 100),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

// ── 資料表(含色階儲存格) ────────────────────────────
@Composable
fun DataTable(data: TableData, modifier: Modifier = Modifier) {
    if (data.rows.isEmpty()) { EmptyHint(); return }
    val textMeasurer = rememberTextMeasurer()
    val headerStyle = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold)
    val cellStyle = TextStyle(fontSize = 11.sp)

    // 計算欄寬權重：依表頭與最長內容(上限 6 字元)
    val weights = data.columns.indices.map { ci ->
        val maxLen = (listOf(data.columns[ci]) + data.rows.map { it.getOrNull(ci)?.text ?: "" })
            .maxOf { it.length }.coerceIn(2, 6)
        maxLen.toFloat()
    }
    val weightSum = weights.sum().coerceAtLeast(1f)

    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant).padding(vertical = 6.dp)
        ) {
            data.columns.forEachIndexed { ci, c ->
                Box(Modifier.weight(weights[ci] / weightSum)) {
                    Text(c, style = headerStyle, modifier = Modifier.padding(horizontal = 6.dp))
                }
            }
        }
        data.rows.forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                row.forEachIndexed { ci, cell ->
                    Box(
                        Modifier.weight(weights[ci] / weightSum)
                            .clip(RoundedCornerShape(4.dp))
                            .then(if (cell.bgArgb != null) Modifier.background(Color(cell.bgArgb)) else Modifier)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(cell.text, style = cellStyle,
                            color = if (cell.bgArgb != null) Color.Black else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}
