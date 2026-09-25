package com.example.hospital_dashboard.desktop.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hospital_dashboard.data.*
import com.example.hospital_dashboard.desktop.DesktopViewModel
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

private val DIMENSIONS = listOf(
    "🚪 門急診服務",
    "🛏️ 住院服務",
    "🏥 病床利用率",
    "💰 營運與財務",
    "🎯 綜合營運診斷"
)

private fun systemPrompt(dimension: String): String = """
你是一位資深的醫院營運分析專家與決策顧問。
使用者提供的是經過代號脫敏的醫院各院區、科別、醫師、病床之營運統計數據。
請依據使用者選擇的分析焦點（$dimension），以結構化、專業的 Markdown 格式產出深度洞察分析報告：

報告必須包含以下章節：
1. 📌 **營運現況摘要**：以關鍵數據指標（門診、住院、佔床、服務量等）歸納本期整體表現。
2. 📈 **亮點與核心增長動能**：指出表現突出之單位、專科或業務板塊，並分析其成長主因。
3. ⚠️ **營運風險與瓶頸警訊**：指出衰退、佔床率異常低、或醫師服務量失衡之痛點。
4. 💡 **具體改善與行動建議**：提供院長室與醫務行政團隊可落地執行的 3~5 項短中期策略。

請注意：
- 嚴格根據所提供的數據推導，切勿杜撰未提及的數字。
- 各單位代號請如實使用，維持分析專業客觀度。
""".trimIndent()

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AnalysisTab(vm: DesktopViewModel) {
    val scope = rememberCoroutineScope()
    var config by remember { mutableStateOf(vm.loadAiConfig()) }
    var showSettings by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<AiTestResult?>(null) }

    var dimension by remember { mutableStateOf(DIMENSIONS.first()) }
    var inputText by remember { mutableStateOf("") }
    var originalPreview by remember { mutableStateOf<String?>(null) }
    var obfuscatedText by remember { mutableStateOf("") }
    var showPreview by remember { mutableStateOf(false) }
    var showBranchSheet by remember { mutableStateOf(false) }

    var analyzing by remember { mutableStateOf(false) }
    var streamText by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var showRealView by remember { mutableStateOf(false) }
    var statusMsg by remember { mutableStateOf<String?>(null) }

    val anonymizer = vm.anonymizer

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── 頂部設定列 ──
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
        ) {
            Column(Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("🤖 AI 營運智慧分析助理", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "模型：${config.model.ifBlank { "未指定" }} ｜ 引擎：${config.providerType.label} ｜ 離線脫敏保護",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    TextButton(onClick = { showSettings = !showSettings }) {
                        Text(if (showSettings) "▲ 收合設定" else "⚙️ 連線設定")
                    }
                }

                if (showSettings) {
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(10.dp))

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = config.baseUrl,
                            onValueChange = { config = config.copy(baseUrl = it) },
                            label = { Text("Base URL（例：http://127.0.0.1:11434/v1）") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = config.model,
                            onValueChange = { config = config.copy(model = it) },
                            label = { Text("Model Name（例：llama3 / qwen2.5）") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = config.apiKey,
                        onValueChange = { config = config.copy(apiKey = it) },
                        label = { Text("API Key（本機 Ollama 可留空）") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = {
                                vm.saveAiConfig(config)
                                statusMsg = "✅ AI 連線設定已儲存"
                            }
                        ) { Text("💾 儲存設定") }
                        Spacer(Modifier.width(10.dp))
                        OutlinedButton(
                            onClick = {
                                testing = true
                                testResult = null
                                scope.launch {
                                    testResult = AiClient.testConnection(config)
                                    testing = false
                                }
                            },
                            enabled = !testing
                        ) {
                            Text(if (testing) "測試中…" else "🔌 測試連線")
                        }
                        testResult?.let { r ->
                            Spacer(Modifier.width(12.dp))
                            Text(
                                (if (r.ok) "✅ " else "❌ ") + r.message +
                                    (r.latencyMs?.let { "（延遲 ${it} ms）" } ?: ""),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (r.ok) Color(0xFF1E8449) else Color(0xFFC0392B)
                            )
                        }
                    }
                }
            }
        }

        statusMsg?.let { msg ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(msg, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    TextButton(onClick = { statusMsg = null }) { Text("✕", color = MaterialTheme.colorScheme.onPrimaryContainer) }
                }
            }
        }

        // ── 步驟 1：輸入/載入資料 ──
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("📥 步驟 1：選取分析焦點與營運資料", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DIMENSIONS.forEach { d ->
                        FilterChip(selected = dimension == d, onClick = { dimension = d }, label = { Text(d) })
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    label = { Text("營運數據文字（可直接點擊「📋 產生院區資料」自動彙整）") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 220.dp)
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { showBranchSheet = true }) {
                        Text("📋 產生院區資料")
                    }
                    Button(
                        onClick = {
                            if (inputText.isBlank()) {
                                statusMsg = "⚠️ 請先輸入或產生營運資料"
                                return@Button
                            }
                            scope.launch {
                                val dict = withContext(Dispatchers.IO) { vm.repo.anonymizerDictionary() }
                                dict.branches.forEach { anonymizer.codeOf(it, Anonymizer.Kind.Branch) }
                                dict.depts.forEach { anonymizer.codeOf(it, Anonymizer.Kind.Dept) }
                                dict.doctors.forEach { anonymizer.codeOf(it, Anonymizer.Kind.Doctor) }
                                dict.clinics.forEach { anonymizer.codeOf(it, Anonymizer.Kind.Clinic) }
                                vm.saveAnonymizerMapping()
                                obfuscatedText = anonymizer.obfuscate(inputText)
                                originalPreview = inputText
                                showPreview = true
                                statusMsg = "🔒 脫敏比對已完成，上傳內容僅含混淆代碼"
                            }
                        }
                    ) { Text("🔒 脫敏並預覽") }
                    Button(
                        onClick = {
                            if (obfuscatedText.isBlank()) {
                                statusMsg = "⚠️ 請先點擊「脫敏並預覽」確認混淆代號"
                                return@Button
                            }
                            scope.launch {
                                analyzing = true
                                errorMsg = null
                                streamText = ""
                                try {
                                    val messages = listOf(
                                        "system" to systemPrompt(dimension),
                                        "user" to obfuscatedText
                                    )
                                    AiClient.streamChat(config, messages).collect { chunk ->
                                        streamText += chunk
                                    }
                                    statusMsg = "🎉 AI 診斷分析完成！"
                                } catch (e: Exception) {
                                    errorMsg = e.message ?: "分析連線失敗"
                                } finally {
                                    analyzing = false
                                }
                            }
                        },
                        enabled = !analyzing
                    ) {
                        Text(if (analyzing) "分析中…" else "🚀 開始 AI 深度分析")
                    }
                }
            }
        }

        // ── 步驟 2：脫敏比對預覽 ──
        if (showPreview && originalPreview != null) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("🔍 步驟 2：脫敏對照檢驗（左：原始資料 ｜ 右：上傳混淆資料）", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(
                            Modifier.weight(1f).heightIn(max = 180.dp)
                                .verticalScroll(rememberScrollState())
                                .clip(RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        ) {
                            Text("原始資料（僅本機呈現）", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.height(4.dp))
                            Text(originalPreview!!, style = MaterialTheme.typography.bodySmall)
                        }
                        Column(
                            Modifier.weight(1f).heightIn(max = 180.dp)
                                .verticalScroll(rememberScrollState())
                                .clip(RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        ) {
                            Text("混淆代碼（送交 AI 模型）", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFF1E8449))
                            Spacer(Modifier.height(4.dp))
                            Text(obfuscatedText, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        // ── 步驟 3：分析成果 ──
        if (analyzing || streamText.isNotBlank() || errorMsg != null) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("📊 步驟 3：AI 營運決策建議報告", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("還原真實名稱", style = MaterialTheme.typography.labelSmall)
                            Spacer(Modifier.width(6.dp))
                            Switch(checked = showRealView, onCheckedChange = { showRealView = it })
                        }
                    }

                    if (analyzing) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }

                    errorMsg?.let { err ->
                        Spacer(Modifier.height(8.dp))
                        Text("❌ 分析錯誤：$err", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }

                    if (streamText.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        val display = if (showRealView) anonymizer.rehydrate(streamText) else streamText
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(Modifier.padding(16.dp)) {
                                MarkdownView(display, Modifier.fillMaxWidth())
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(
                                onClick = {
                                    val sel = StringSelection(display)
                                    Toolkit.getDefaultToolkit().systemClipboard.setContents(sel, sel)
                                    statusMsg = "📋 報告已複製到剪貼簿"
                                }
                            ) {
                                Text("📋 一鍵複製報告")
                            }
                            OutlinedButton(
                                onClick = {
                                    SwingUtilities.invokeLater {
                                        val dialog = FileDialog(null as Frame?, "儲存分析報告", FileDialog.SAVE)
                                        dialog.file = "醫院營運分析報告_${System.currentTimeMillis()}.md"
                                        dialog.isVisible = true
                                        val f = dialog.file
                                        val d = dialog.directory
                                        if (f != null && d != null) {
                                            val outFile = File(d, f)
                                            outFile.writeText(display, Charsets.UTF_8)
                                            statusMsg = "💾 報告已儲存至：${outFile.absolutePath}"
                                        }
                                    }
                                }
                            ) {
                                Text("💾 匯出為 Markdown 檔案 (*.md)")
                            }
                        }
                    }
                }
            }
        }
    }

    // ── 產生院區資料面板 ──
    if (showBranchSheet) {
        val branches = remember { vm.availableBranches() }
        AdaptiveSheet(onDismissRequest = { showBranchSheet = false }) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("📋 選擇產生營運摘要之院區", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("系統將擷取最新月份之門診、住院、佔床率與服務量並自動混淆填入。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(12.dp))
                branches.forEach { br ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showBranchSheet = false
                                scope.launch {
                                    val summary = withContext(Dispatchers.IO) { vm.repo.branchAnalysisSummary(br) }
                                    if (summary != null) {
                                        val original = summary.toText()
                                        val obf = anonymizer.obfuscate(original)
                                        originalPreview = original
                                        obfuscatedText = obf
                                        inputText = obf
                                        showPreview = true
                                        vm.saveAnonymizerMapping()
                                        statusMsg = "已產生「$br」營運數據摘要"
                                    } else {
                                        statusMsg = "⚠️ 該院區查無營運數據"
                                    }
                                }
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🏢 $br", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
