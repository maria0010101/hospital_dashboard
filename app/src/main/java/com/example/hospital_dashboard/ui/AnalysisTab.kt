package com.example.hospital_dashboard.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.hospital_dashboard.DashboardViewModel
import com.example.hospital_dashboard.data.AiClient
import com.example.hospital_dashboard.data.AiConfigStore
import com.example.hospital_dashboard.data.AiProviderConfig
import com.example.hospital_dashboard.data.AiProviderType
import com.example.hospital_dashboard.data.AiTestResult
import com.example.hospital_dashboard.data.Anonymizer
import com.example.hospital_dashboard.ui.charts.findActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val DIMENSIONS = listOf("摘要建議", "趨勢預測", "異常檢測", "營收歸因分析")

private fun systemPrompt(dim: String): String = """
你是醫院營運數據分析助手。輸入資料已經過匿名化處理（院區/科別/醫師均為代號，如「甲院區」「科別01」「醫師001」），
請直接使用代號進行分析，不要猜測或還原真實名稱。
請以繁體中文回覆，並使用 Markdown 格式（可用標題、條列、表格、粗體）。

本次分析任務：$dim
""".trimIndent()

private const val PREFS = "ai_analysis"
private const val KEY_MAPPING = "mapping"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AnalysisTab(vm: DashboardViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ── AI 引擎設定 ──
    var config by remember { mutableStateOf(AiConfigStore.load(context) ?: AiProviderConfig()) }
    var showSettings by rememberSaveable { mutableStateOf(true) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<AiTestResult?>(null) }

    // ── 分析流程狀態 ──
    var inputText by rememberSaveable { mutableStateOf("") }
    var originalPreview by remember { mutableStateOf<String?>(null) }
    var obfuscatedText by rememberSaveable { mutableStateOf("") }
    var dimension by rememberSaveable { mutableStateOf(DIMENSIONS.first()) }
    var showPreview by rememberSaveable { mutableStateOf(false) }
    var showBranchSheet by remember { mutableStateOf(false) }
    var anonymizer by remember {
        mutableStateOf(Anonymizer.fromJson(
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_MAPPING, null)))
    }
    var analyzing by remember { mutableStateOf(false) }
    var streamText by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var showRealView by rememberSaveable { mutableStateOf(true) }

    fun saveMapping() {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_MAPPING, anonymizer.toJson()).apply()
    }

    fun registerDictionary() {
        scope.launch {
            val dict = withContext(Dispatchers.IO) { vm.repo.anonymizerDictionary() }
            dict.branches.forEach { anonymizer.codeOf(it, Anonymizer.Kind.Branch) }
            dict.depts.forEach { anonymizer.codeOf(it, Anonymizer.Kind.Dept) }
            dict.doctors.forEach { anonymizer.codeOf(it, Anonymizer.Kind.Doctor) }
            dict.clinics.forEach { anonymizer.codeOf(it, Anonymizer.Kind.Clinic) }
            saveMapping()
        }
    }

    // 隱藏 WebView 會破壞本機視窗渲染（MIUI 硬體合成衝突）→ 改為匯出 PDF 時才建立，用完即銷毀

    // 匯出
    val textExporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val content = if (showRealView) anonymizer.rehydrate(streamText) else streamText
        try {
            context.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
            Toast.makeText(context, "已匯出報告", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "匯出失敗：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        Modifier.fillMaxWidth().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── 隱私聲明 ──
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))) {
            Text(
                "🔒 隱私保護：混淆對照表僅暫存於本機記憶體/快取，永不隨請求上傳；上傳至 AI 伺服器的只有混淆後資料。",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF1B5E20),
                modifier = Modifier.padding(10.dp)
            )
        }

        // ── 步驟 0：AI 引擎設定 ──
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("⚙️ AI 引擎與連線設定", style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { showSettings = !showSettings }) {
                        Text(if (showSettings) "收合 ▲" else "展開 ▼")
                    }
                }
                if (showSettings) {
                    // Provider 下拉
                    var providerMenu by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = providerMenu, onExpandedChange = { providerMenu = it }) {
                        OutlinedTextField(
                            value = config.providerType.label,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Provider Type") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = providerMenu) },
                            modifier = Modifier.fillMaxWidth().menuAnchor().padding(bottom = 6.dp)
                        )
                        ExposedDropdownMenu(expanded = providerMenu, onDismissRequest = { providerMenu = false }) {
                            AiProviderType.entries.forEach { t ->
                                DropdownMenuItem(
                                    text = { Text(t.label) },
                                    onClick = { config = config.copy(providerType = t); providerMenu = false }
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = config.baseUrl,
                        onValueChange = { config = config.copy(baseUrl = it) },
                        label = { Text("Base URL（例：http://192.168.1.50:11434/v1）") },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                    )
                    OutlinedTextField(
                        value = config.apiKey,
                        onValueChange = { config = config.copy(apiKey = it) },
                        label = { Text("API Key（本地 Ollama 可留空）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                    )
                    OutlinedTextField(
                        value = config.model,
                        onValueChange = { config = config.copy(model = it) },
                        label = { Text("Model Name（例：llama3 / gpt-4o-mini）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = {
                                AiConfigStore.save(context, config)
                                Toast.makeText(context, "設定已儲存", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("💾 儲存設定") }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = {
                                testing = true; testResult = null
                                scope.launch {
                                    testResult = AiClient.testConnection(config)
                                    testing = false
                                }
                            },
                            enabled = !testing,
                            modifier = Modifier.weight(1f)
                        ) { Text(if (testing) "測試中…" else "🔌 測試連線") }
                    }
                    testResult?.let { r ->
                        Spacer(Modifier.height(6.dp))
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

        // ── 步驟 1：輸入/載入資料 ──
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("📥 步驟 1：輸入/載入資料", style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("分析維度", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DIMENSIONS.forEach { d ->
                        FilterChip(selected = dimension == d, onClick = { dimension = d }, label = { Text(d) })
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    label = { Text("貼上營運資料文字（或點下方按鈕自動產生混淆資料）") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp, max = 260.dp)
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showBranchSheet = true }, modifier = Modifier.weight(1f)) {
                        Text("📋 產生混淆資料")
                    }
                    Button(
                        onClick = {
                            if (inputText.isBlank()) {
                                Toast.makeText(context, "請先輸入或產生資料", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            // 若尚未建立對照表 → 先註冊字典並混淆
                            if (anonymizer.size == 0) {
                                registerDictionary()
                                scope.launch {
                                    val dict = withContext(Dispatchers.IO) { vm.repo.anonymizerDictionary() }
                                    dict.branches.forEach { anonymizer.codeOf(it, Anonymizer.Kind.Branch) }
                                    dict.depts.forEach { anonymizer.codeOf(it, Anonymizer.Kind.Dept) }
                                    dict.doctors.forEach { anonymizer.codeOf(it, Anonymizer.Kind.Doctor) }
                                    dict.clinics.forEach { anonymizer.codeOf(it, Anonymizer.Kind.Clinic) }
                                    saveMapping()
                                    obfuscatedText = anonymizer.obfuscate(inputText)
                                    originalPreview = inputText
                                    showPreview = true
                                }
                            } else if (obfuscatedText.isBlank() || originalPreview != inputText) {
                                obfuscatedText = anonymizer.obfuscate(inputText)
                                originalPreview = inputText
                                showPreview = true
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("🔒 脫敏並預覽") }
                    Button(
                        onClick = {
                            if (obfuscatedText.isBlank()) {
                                Toast.makeText(context, "請先完成脫敏預覽（上傳內容只會是混淆後資料）", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            scope.launch {
                                analyzing = true; errorMsg = null; streamText = ""
                                try {
                                    val messages = listOf(
                                        "system" to systemPrompt(dimension),
                                        "user" to obfuscatedText
                                    )
                                    AiClient.streamChat(config, messages).collect { chunk ->
                                        streamText += chunk
                                    }
                                } catch (e: Exception) {
                                    errorMsg = e.message ?: "分析失敗"
                                } finally {
                                    analyzing = false
                                }
                            }
                        },
                        enabled = !analyzing,
                        modifier = Modifier.weight(1f)
                    ) { Text(if (analyzing) "分析中…" else "🚀 開始分析") }
                }
            }
        }

        // ── 步驟 2：脫敏比對預覽 ──
        if (showPreview && originalPreview != null) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("🔍 步驟 2：脫敏比對預覽", style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        TextButton(onClick = { showPreview = !showPreview }) {
                            Text(if (showPreview) "收合 ▲" else "展開 ▼")
                        }
                    }
                    Text("左：原始文字（僅本機顯示）｜右：將上傳的混淆後文字",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(
                            Modifier.weight(1f).heightIn(max = 200.dp)
                                .verticalScroll(rememberScrollState())
                                .clip(RoundedCornerShape(6.dp))
                                .padding(8.dp)
                        ) {
                            Text("原始", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error)
                            Text(originalPreview!!, style = MaterialTheme.typography.labelSmall)
                        }
                        Column(
                            Modifier.weight(1f).heightIn(max = 200.dp)
                                .verticalScroll(rememberScrollState())
                                .clip(RoundedCornerShape(6.dp))
                                .padding(8.dp)
                        ) {
                            Text("混淆後", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E8449))
                            Text(obfuscatedText, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        // ── 步驟 3/4：分析中與結果 ──
        if (analyzing || streamText.isNotBlank() || errorMsg != null) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (analyzing) "⏳ 步驟 3：分析中（串流輸出）"
                            else "📊 步驟 4：分析結果",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        if (streamText.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(if (showRealView) "顯示：真實" else "顯示：脫敏",
                                    style = MaterialTheme.typography.labelSmall)
                                Spacer(Modifier.width(4.dp))
                                Switch(checked = showRealView, onCheckedChange = { showRealView = it })
                            }
                        }
                    }
                    if (analyzing) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Spacer(Modifier.height(4.dp))
                    }
                    errorMsg?.let {
                        Spacer(Modifier.height(4.dp))
                        Text("❌ $it", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                    if (streamText.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        val display = if (showRealView) anonymizer.rehydrate(streamText) else streamText
                        MarkdownView(display, Modifier.fillMaxWidth())
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                val content = if (showRealView) anonymizer.rehydrate(streamText) else streamText
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("分析報告", content))
                                Toast.makeText(context, "已複製分析報告", Toast.LENGTH_SHORT).show()
                            }, modifier = Modifier.weight(1f)) { Text("📋 一鍵複製") }
                            OutlinedButton(onClick = { textExporter.launch("分析報告.md") },
                                modifier = Modifier.weight(1f)) { Text("💾 匯出文字") }
                            OutlinedButton(onClick = {
                                val html = markdownToHtml(
                                    if (showRealView) anonymizer.rehydrate(streamText) else streamText)
                                // 僅在匯出時建立 WebView（常駐隱藏 WebView 會破壞視窗渲染）
                                val wv = WebView(context)
                                wv.webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        val activity = context.findActivity() ?: return
                                        val adapter = view?.createPrintDocumentAdapter("醫院營運分析報告") ?: return
                                        val pm = activity.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
                                        pm.print("醫院營運分析報告", adapter,
                                            PrintAttributes.Builder()
                                                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                                                .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                                                .build())
                                    }
                                }
                                wv.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
                            }, modifier = Modifier.weight(1f)) { Text("🖨️ 匯出 PDF") }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
    }

    // ── 產生混淆資料：院區選擇 ──
    if (showBranchSheet) {
        ModalBottomSheet(onDismissRequest = { showBranchSheet = false }) {
            val branches = remember { vm.availableBranches() }
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    .heightIn(max = 480.dp).verticalScroll(rememberScrollState())
            ) {
                Text("📋 產生混淆資料（選擇院區）", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
                Text("僅產生所選院區之最新月份營運摘要，並自動以代號混淆後填入輸入框。",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(8.dp))
                branches.forEach { br ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            showBranchSheet = false
                            scope.launch {
                                val dict = withContext(Dispatchers.IO) { vm.repo.anonymizerDictionary() }
                                dict.branches.forEach { anonymizer.codeOf(it, Anonymizer.Kind.Branch) }
                                dict.depts.forEach { anonymizer.codeOf(it, Anonymizer.Kind.Dept) }
                                dict.doctors.forEach { anonymizer.codeOf(it, Anonymizer.Kind.Doctor) }
                                dict.clinics.forEach { anonymizer.codeOf(it, Anonymizer.Kind.Clinic) }
                                val summary = withContext(Dispatchers.IO) { vm.repo.branchAnalysisSummary(br) }
                                if (summary != null) {
                                    val original = summary.toText()
                                    val obf = anonymizer.obfuscate(original)
                                    originalPreview = original
                                    obfuscatedText = obf
                                    inputText = obf
                                    showPreview = true
                                    saveMapping()
                                    Toast.makeText(context, "已產生「${anonymizer.rehydrate(obf.split("\n").first().substringAfter("】"))}」混淆資料", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "該院區無資料", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }.padding(vertical = 12.dp)
                    ) {
                        Text("🏢 $br", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
