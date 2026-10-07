package com.example.hospital_dashboard.desktop.ui.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.hospital_dashboard.data.AiClient
import com.example.hospital_dashboard.data.Anonymizer
import com.example.hospital_dashboard.data.ChartAnalysisTemplate
import com.example.hospital_dashboard.data.ChartHierarchicalCollector
import com.example.hospital_dashboard.desktop.DesktopViewModel
import com.example.hospital_dashboard.desktop.ui.MarkdownView
import com.example.hospital_dashboard.desktop.ui.adaptive.AdaptiveSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import javax.swing.SwingUtilities

/**
 * 桌面版圖表匯出選單對話框：提供 (a) 匯出 JPEG 圖片、(b) 圖表文字分析報告、(c) 圖表 AI 深度分析。
 */
@Composable
fun ChartExportMenuDialog(
    graphicsLayer: GraphicsLayer?,
    content: ChartContent,
    onDismiss: () -> Unit,
    onOpenTextReport: () -> Unit,
    onOpenAiReport: () -> Unit,
    onStatusMessage: (String) -> Unit = {}
) {
    val scope = rememberCoroutineScope()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth(0.6f)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "📤 圖表匯出與深入分析",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "指標：${content.title}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Text(
                        "✕",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable(onClick = onDismiss)
                            .padding(4.dp)
                    )
                }
                Spacer(Modifier.height(16.dp))

                // 選項 a: 輸出 JPEG 圖片
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            onDismiss()
                            if (graphicsLayer != null) {
                                scope.launch {
                                    val bmp = ChartImageExporter.captureToBufferedImage(graphicsLayer)
                                    if (bmp != null) {
                                        ChartImageExporter.exportChartAsJpeg(
                                            bufferedImage = bmp,
                                            chartTitle = content.title,
                                            onSuccess = { path ->
                                                onStatusMessage("📷 圖表圖片已儲存至：$path")
                                            },
                                            onError = { err ->
                                                onStatusMessage("❌ 儲存圖片失敗：$err")
                                            }
                                        )
                                    } else {
                                        onStatusMessage("❌ 圖表畫面擷取失敗")
                                    }
                                }
                            } else {
                                onStatusMessage("❌ 尚未取得圖表繪製圖層")
                            }
                        },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                ) {
                    Row(
                        Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("📷", style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("儲存 JPEG 圖片檔案", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("將當前圖表畫面輸出為高解析度 JPEG 圖片檔並自選儲存位置", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // 選項 b: 圖表文字分析報告 (模板)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            onDismiss()
                            onOpenTextReport()
                        },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                ) {
                    Row(
                        Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("📝", style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("圖表文字分析報告（模板產製）", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("套用營運分析模板，依當前圖表數據與篩選內容自動生成結構化 Markdown 分析報告", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // 選項 c: 圖表 AI 深度分析 (多階層展開 + 混淆脫敏)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            onDismiss()
                            onOpenAiReport()
                        },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                ) {
                    Row(
                        Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🤖", style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("圖表 AI 深度分析（多階層展開）", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text("彙整院區/部別/科別/醫師多層級明細，本機脫敏後呼叫 AI 進行深度歸因與決策分析", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/**
 * 桌面版圖表文字分析報告對話框 (基於模板與當前圖表篩選產製)
 */
@Composable
fun ChartTextReportDialog(
    vm: DesktopViewModel,
    content: ChartContent,
    onDismiss: () -> Unit
) {
    val filters = vm.filters.value
    var statusMsg by remember { mutableStateOf<String?>(null) }

    val reportText = remember(content, filters) {
        val input = ChartAnalysisTemplate.ReportInput(
            title = content.title,
            filters = filters,
            lineData = (content as? ChartContent.Line)?.data,
            hbarData = (content as? ChartContent.HBar)?.data,
            vbarData = (content as? ChartContent.VBar)?.data,
            pieData = (content as? ChartContent.Pie)?.data,
            tableData = (content as? ChartContent.Table)?.data
        )
        ChartAnalysisTemplate.generate(input)
    }

    AdaptiveSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // 頂部列
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "📝 圖表文字分析報告",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                // 複製按鈕
                OutlinedButton(
                    onClick = {
                        try {
                            val sel = StringSelection(reportText)
                            Toolkit.getDefaultToolkit().systemClipboard.setContents(sel, null)
                            statusMsg = "📋 已複製分析報告至剪貼簿"
                        } catch (e: Exception) {
                            statusMsg = "❌ 複製失敗：${e.message}"
                        }
                    },
                    modifier = Modifier.padding(end = 6.dp)
                ) {
                    Text("📋 複製")
                }

                // 匯出檔案
                Button(
                    onClick = {
                        SwingUtilities.invokeLater {
                            try {
                                val safeTitle = content.title.replace(Regex("[^a-zA-Z0-9\\u4e00-\\u9fa5_\\-]"), "_").take(15)
                                val dialog = FileDialog(null as Frame?, "儲存分析報告 Markdown 檔案", FileDialog.SAVE)
                                dialog.file = "ChartReport_${safeTitle}_${System.currentTimeMillis()}.md"
                                dialog.isVisible = true
                                val f = dialog.file
                                val d = dialog.directory
                                if (f != null && d != null) {
                                    val outFile = File(d, f)
                                    outFile.writeText(reportText, Charsets.UTF_8)
                                    statusMsg = "💾 已匯出報告至：${outFile.absolutePath}"
                                }
                            } catch (e: Exception) {
                                statusMsg = "❌ 匯出失敗：${e.message}"
                            }
                        }
                    },
                    modifier = Modifier.padding(end = 6.dp)
                ) {
                    Text("💾 匯出")
                }

                // 關閉
                TextButton(onClick = onDismiss) {
                    Text("✕ 關閉")
                }
            }

            if (statusMsg != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    statusMsg!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(8.dp))

            // 報告內容區域（包在 SelectionContainer 內支援滑鼠選取與複製）
            SelectionContainer {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                        .padding(14.dp)
                ) {
                    MarkdownView(reportText, Modifier.fillMaxWidth())
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

/**
 * 桌面版圖表 AI 深度分析對話框 (彙整多階層展開資料 + 本機混淆脫敏 + AI API 串流分析)
 */
@Composable
fun ChartAiReportDialog(
    vm: DesktopViewModel,
    content: ChartContent,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val filters = vm.filters.value

    var config by remember { mutableStateOf(vm.loadAiConfig()) }
    var analyzing by remember { mutableStateOf(false) }
    var streamText by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var showRealView by remember { mutableStateOf(true) }
    var rawHierarchicalData by remember { mutableStateOf("") }
    var obfuscatedData by remember { mutableStateOf("") }
    var showRawData by remember { mutableStateOf(false) }
    var statusMsg by remember { mutableStateOf<String?>(null) }

    val anonymizer = vm.anonymizer

    fun startAnalysis() {
        scope.launch {
            analyzing = true
            errorMsg = null
            streamText = ""
            statusMsg = null
            try {
                // 1. 同步最新實體字典
                val dict = withContext(Dispatchers.IO) { vm.repo.anonymizerDictionary() }
                dict.branches.forEach { anonymizer.codeOf(it, Anonymizer.Kind.Branch) }
                dict.depts.forEach { anonymizer.codeOf(it, Anonymizer.Kind.Dept) }
                dict.doctors.forEach { anonymizer.codeOf(it, Anonymizer.Kind.Doctor) }
                dict.clinics.forEach { anonymizer.codeOf(it, Anonymizer.Kind.Clinic) }
                vm.saveAnonymizerMapping()

                // 2. 收集多階層展開明細
                val raw = withContext(Dispatchers.IO) {
                    ChartHierarchicalCollector.collect(vm.repo, content.title, filters)
                }
                rawHierarchicalData = raw

                // 3. 本機混淆脫敏
                val obf = anonymizer.obfuscate(raw)
                obfuscatedData = obf

                // 4. 呼叫 AI API 串流
                val systemPrompt = """
                    你是頂尖醫院營運管理顧問與醫療數據分析專家。
                    本次分析對象為醫院經營指標【${content.title}】的完整多階層展開數據（涵蓋院區、部別、科別與醫師服務量）。
                    重要隱私保護聲明：輸入資料中院區、科別、醫師姓名已全部替換為匿名代號（如「甲院區」「科別01」「醫師001」），請直接使用這些代號分析，絕對不要猜測或還原真實實體名稱。
                    請以繁體中文撰寫專業決策報告（使用 Markdown 標題、條列、表格、粗體）。
                    報告架構需包含：
                    1. 【多階層營運現況與結構透視】：分析院區、部別、科別、醫師各階層的貢獻度與集中度。
                    2. 【關鍵成長與衰退歸因分析】：指出帶動成長或導致衰退的核心部別/科別/醫師，並分析可能因素。
                    3. 【異常波動與潛在瓶頸識別】：分析不正常落差、人力配置或病患流向異常。
                    4. 【具體行動與管理決策建議】：提供門診排程、跨院區資源整合、績效激勵與營運優化的可落地具體方案。
                """.trimIndent()

                val messages = listOf(
                    "system" to systemPrompt,
                    "user" to obf
                )

                AiClient.streamChat(config, messages).collect { chunk ->
                    streamText += chunk
                }
            } catch (e: Exception) {
                errorMsg = e.message ?: "AI 分析連線或生成失敗"
            } finally {
                analyzing = false
            }
        }
    }

    LaunchedEffect(Unit) {
        startAnalysis()
    }

    AdaptiveSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // 頂部功能列
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "🤖 AI 深度分析：${content.title}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (analyzing) {
                            Spacer(Modifier.width(8.dp))
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(6.dp))
                            Text("分析中...", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Text(
                        "提供者：${config.providerType.label} ｜ 模型：${config.model.ifBlank { config.providerType.defaultModel }}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                // 真實 / 脫敏切換開關
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(end = 12.dp)
                ) {
                    Text(
                        if (showRealView) "👁️ 真實名稱" else "🔒 匿名代號",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(6.dp))
                    Switch(
                        checked = showRealView,
                        onCheckedChange = { showRealView = it },
                        modifier = Modifier.height(28.dp)
                    )
                }

                // 重新分析
                OutlinedButton(
                    onClick = { if (!analyzing) startAnalysis() },
                    enabled = !analyzing,
                    modifier = Modifier.padding(end = 6.dp)
                ) {
                    Text("🔄 重試")
                }

                // 複製
                OutlinedButton(
                    onClick = {
                        val displayText = if (showRealView) anonymizer.rehydrate(streamText) else streamText
                        if (displayText.isNotBlank()) {
                            val sel = StringSelection(displayText)
                            Toolkit.getDefaultToolkit().systemClipboard.setContents(sel, null)
                            statusMsg = "📋 已複製 AI 分析報告"
                        }
                    },
                    enabled = streamText.isNotBlank(),
                    modifier = Modifier.padding(end = 6.dp)
                ) {
                    Text("📋 複製")
                }

                // 匯出
                Button(
                    onClick = {
                        val displayText = if (showRealView) anonymizer.rehydrate(streamText) else streamText
                        if (displayText.isNotBlank()) {
                            SwingUtilities.invokeLater {
                                try {
                                    val safeTitle = content.title.replace(Regex("[^a-zA-Z0-9\\u4e00-\\u9fa5_\\-]"), "_").take(15)
                                    val dialog = FileDialog(null as Frame?, "儲存 AI 分析報告", FileDialog.SAVE)
                                    dialog.file = "AiAnalysis_${safeTitle}_${System.currentTimeMillis()}.md"
                                    dialog.isVisible = true
                                    val f = dialog.file
                                    val d = dialog.directory
                                    if (f != null && d != null) {
                                        val outFile = File(d, f)
                                        outFile.writeText(displayText, Charsets.UTF_8)
                                        statusMsg = "💾 已匯出報告至：${outFile.absolutePath}"
                                    }
                                } catch (e: Exception) {
                                    statusMsg = "❌ 匯出失敗：${e.message}"
                                }
                            }
                        }
                    },
                    enabled = streamText.isNotBlank(),
                    modifier = Modifier.padding(end = 6.dp)
                ) {
                    Text("💾 匯出")
                }

                // 關閉
                TextButton(onClick = onDismiss) {
                    Text("✕ 關閉")
                }
            }

            if (statusMsg != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    statusMsg!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            // 進度條
            if (analyzing) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            // 錯誤訊息提示
            if (errorMsg != null) {
                Spacer(Modifier.height(6.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "⚠️ $errorMsg",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // 展開/收合收集到的原始明細資料
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (showRawData) "▼ 隱藏上傳明細資料" else "▶ 查看本次分析輸入明細資料 (${if (showRealView) "真實" else "脫敏"}，共 ${rawHierarchicalData.lines().size} 行)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { showRawData = !showRawData }
                        .padding(vertical = 4.dp, horizontal = 2.dp)
                )
            }

            if (showRawData) {
                SelectionContainer {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 160.dp)
                            .verticalScroll(rememberScrollState())
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .padding(8.dp)
                    ) {
                        Text(
                            if (showRealView) rawHierarchicalData else obfuscatedData,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // 報告內容區域（以 SelectionContainer 包裹，支援滑鼠拖曳選取與複製）
            SelectionContainer {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                        .padding(14.dp)
                ) {
                    val displayText = if (showRealView) anonymizer.rehydrate(streamText) else streamText
                    if (displayText.isBlank() && analyzing) {
                        Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(Modifier.size(32.dp))
                                Spacer(Modifier.height(12.dp))
                                Text("AI 顧問正在研讀多階層營運數據並產出分析報告...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    } else if (displayText.isBlank()) {
                        Text("尚未生成分析報告內容", color = MaterialTheme.colorScheme.outline)
                    } else {
                        MarkdownView(displayText, Modifier.fillMaxWidth())
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}
