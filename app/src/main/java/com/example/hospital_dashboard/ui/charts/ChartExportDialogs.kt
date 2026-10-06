package com.example.hospital_dashboard.ui.charts

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.hospital_dashboard.DashboardViewModel
import com.example.hospital_dashboard.data.AiClient
import com.example.hospital_dashboard.data.AiConfigStore
import com.example.hospital_dashboard.data.AiProviderConfig
import com.example.hospital_dashboard.data.Anonymizer
import com.example.hospital_dashboard.data.ChartAnalysisTemplate
import com.example.hospital_dashboard.data.ChartHierarchicalCollector
import com.example.hospital_dashboard.ui.MarkdownView
import com.example.hospital_dashboard.ui.adaptive.AdaptiveSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 圖表匯出選項底部面板：提供 (a) 匯出 JPEG 圖片、(b) 圖表文字分析報告、(c) 圖表 AI 深度分析。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartExportMenuSheet(
    activity: Activity,
    chartBounds: Rect?,
    content: ChartContent,
    onDismiss: () -> Unit,
    onOpenTextReport: () -> Unit,
    onOpenAiReport: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
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
            Spacer(Modifier.height(16.dp))

            // 選項 a: 輸出 JPEG 圖片
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onDismiss()
                        ChartImageExporter.exportChartAsJpeg(
                            activity = activity,
                            chartBounds = chartBounds,
                            chartTitle = content.title
                        )
                    },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            ) {
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("📷", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("儲存 JPEG 圖片檔案", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text("將當前放大的圖表繪製畫面輸出為高畫質 JPEG 圖片存入裝置相簿", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // 選項 b: 圖表文字分析報告 (模板)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onDismiss()
                        onOpenTextReport()
                    },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            ) {
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("📝", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("圖表文字分析報告（模板產製）", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text("套用營運分析模板，依當前圖表數據與篩選內容自動生成結構化分析報告", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // 選項 c: 圖表 AI 深度分析 (多階層展開 + 混淆脫敏)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onDismiss()
                        onOpenAiReport()
                    },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
            ) {
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🤖", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("圖表 AI 深度分析（多階層展開）", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text("彙整院區/部別/科別/醫師多層級明細，本機脫敏後呼叫 AI 進行深度多維度歸因分析", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
        }
    }
}

/**
 * 圖表文字分析報告對話框 (基於模板與當前圖表篩選產製)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartTextReportDialog(
    vm: DashboardViewModel,
    content: ChartContent,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val filters = vm.filters.value

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

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(reportText.toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(context, "✅ 已成功匯出 Markdown 分析報告", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "❌ 匯出失敗：${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
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
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("圖表分析報告", reportText))
                        Toast.makeText(context, "📋 已複製分析報告至剪貼簿", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.padding(end = 6.dp)
                ) {
                    Text("📋 複製")
                }

                // 匯出檔案
                Button(
                    onClick = {
                        val safeTitle = content.title.replace(Regex("[^a-zA-Z0-9\\u4e00-\\u9fa5_\\-]"), "_").take(15)
                        exportLauncher.launch("ChartReport_${safeTitle}.md")
                    },
                    modifier = Modifier.padding(end = 6.dp)
                ) {
                    Text("💾 匯出")
                }

                // 分享
                OutlinedButton(
                    onClick = {
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "【圖表分析報告】${content.title}")
                            putExtra(Intent.EXTRA_TEXT, reportText)
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "分享報告"))
                    },
                    modifier = Modifier.padding(end = 6.dp)
                ) {
                    Text("📤 分享")
                }

                // 關閉
                TextButton(onClick = onDismiss) {
                    Text("✕ 關閉")
                }
            }

            Spacer(Modifier.height(8.dp))

            // 報告內容區域（包在 SelectionContainer 內支援長按/滑鼠選取與複製）
            SelectionContainer {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(max = 580.dp)
                        .verticalScroll(rememberScrollState())
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                        .padding(14.dp)
                ) {
                    MarkdownView(reportText, Modifier.fillMaxWidth())
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * 圖表 AI 深度分析對話框 (彙整多階層展開資料 + 本機混淆脫敏 + AI API 串流分析)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartAiReportDialog(
    vm: DashboardViewModel,
    content: ChartContent,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val filters = vm.filters.value

    var config by remember { mutableStateOf(AiConfigStore.load(context) ?: AiProviderConfig()) }
    var analyzing by remember { mutableStateOf(false) }
    var streamText by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var showRealView by remember { mutableStateOf(true) }
    var rawHierarchicalData by remember { mutableStateOf("") }
    var obfuscatedData by remember { mutableStateOf("") }
    var showRawDataSheet by remember { mutableStateOf(false) }

    val PREFS = "ai_analysis"
    val KEY_MAPPING = "mapping"
    var anonymizer by remember {
        mutableStateOf(Anonymizer.fromJson(
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_MAPPING, null)))
    }

    fun saveMapping() {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_MAPPING, anonymizer.toJson()).apply()
    }

    fun startAnalysis() {
        scope.launch {
            analyzing = true
            errorMsg = null
            streamText = ""
            try {
                // 1. 同步最新實體字典
                val dict = withContext(Dispatchers.IO) { vm.repo.anonymizerDictionary() }
                dict.branches.forEach { anonymizer.codeOf(it, Anonymizer.Kind.Branch) }
                dict.depts.forEach { anonymizer.codeOf(it, Anonymizer.Kind.Dept) }
                dict.doctors.forEach { anonymizer.codeOf(it, Anonymizer.Kind.Doctor) }
                dict.clinics.forEach { anonymizer.codeOf(it, Anonymizer.Kind.Clinic) }
                saveMapping()

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

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri ->
        if (uri != null) {
            val contentToExport = if (showRealView) anonymizer.rehydrate(streamText) else streamText
            try {
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(contentToExport.toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(context, "✅ 已成功匯出 AI 分析報告", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "❌ 匯出失敗：${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val displayText = remember(streamText, showRealView) {
        if (showRealView) anonymizer.rehydrate(streamText) else streamText
    }

    AdaptiveSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // 頂部標題列
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "🤖 圖表 AI 深度分析",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                // 複製
                OutlinedButton(
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("AI 分析報告", displayText))
                        Toast.makeText(context, "📋 已複製 AI 報告至剪貼簿", Toast.LENGTH_SHORT).show()
                    },
                    enabled = displayText.isNotBlank(),
                    modifier = Modifier.padding(end = 6.dp)
                ) {
                    Text("📋 複製")
                }

                // 匯出
                Button(
                    onClick = {
                        val safeTitle = content.title.replace(Regex("[^a-zA-Z0-9\\u4e00-\\u9fa5_\\-]"), "_").take(15)
                        exportLauncher.launch("AiReport_${safeTitle}.md")
                    },
                    enabled = displayText.isNotBlank(),
                    modifier = Modifier.padding(end = 6.dp)
                ) {
                    Text("💾 匯出")
                }

                // 關閉
                TextButton(onClick = onDismiss) {
                    Text("✕ 關閉")
                }
            }

            // 控制列：真實/代號切換 + 重新分析
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("顯示真實名稱", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.width(6.dp))
                    Switch(
                        checked = showRealView,
                        onCheckedChange = { showRealView = it },
                        modifier = Modifier.padding(end = 12.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (obfuscatedData.isNotBlank()) {
                        TextButton(onClick = { showRawDataSheet = !showRawDataSheet }) {
                            Text(if (showRawDataSheet) "收合匿名資料 ▲" else "檢視匿名資料 ▼", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    OutlinedButton(
                        onClick = { startAnalysis() },
                        enabled = !analyzing
                    ) {
                        Text(if (analyzing) "分析中…" else "🔄 重新分析", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            if (analyzing) {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 4.dp))
            }

            errorMsg?.let { err ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Text("⚠️ 分析發生錯誤：$err", color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
                        Text("請至「AI 分析」分頁檢查 AI 引擎設定與 API Key 連線。", color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            if (showRawDataSheet && obfuscatedData.isNotBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
                ) {
                    SelectionContainer {
                        Column(Modifier.padding(8.dp).heightIn(max = 140.dp).verticalScroll(rememberScrollState())) {
                            Text("🔒 送出至 AI 之匿名多階層資料：", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Text(obfuscatedData, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            // 報告內容區域（以 SelectionContainer 包裹，支援長按與滑鼠選取複製）
            SelectionContainer {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(max = 560.dp)
                        .verticalScroll(rememberScrollState())
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                        .padding(14.dp)
                ) {
                    if (displayText.isNotBlank()) {
                        MarkdownView(displayText, Modifier.fillMaxWidth())
                    } else if (analyzing) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.padding(end = 12.dp))
                            Text("正在整合多階層資料並由 AI 產製深度分析報告中，請稍候…", style = MaterialTheme.typography.bodyMedium)
                        }
                    } else {
                        Text("尚未產生報告，請點擊「重新分析」。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
