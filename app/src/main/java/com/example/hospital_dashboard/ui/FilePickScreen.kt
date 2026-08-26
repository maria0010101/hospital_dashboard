package com.example.hospital_dashboard.ui

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hospital_dashboard.DashboardViewModel
import com.example.hospital_dashboard.UiState
import com.example.hospital_dashboard.data.FileNameParser

/**
 * 檔案選擇畫面：使用者自行挑選手機中的「業務資料彙整-YYYYMMDD.xlsx」，
 * 並顯示由檔名擷取的資料更新日期。
 */
@Composable
fun FilePickScreen(vm: DashboardViewModel) {
    val context = LocalContext.current
    val state = vm.uiState.collectAsState().value
    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    var pickedName by remember { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pickedUri = uri
            pickedName = resolveDisplayName(context, uri)
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(48.dp))
        Text("🏥", fontSize = 56.sp)
        Spacer(Modifier.height(8.dp))
        Text("醫院營運儀表板", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Android 版", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(24.dp))

        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(16.dp)) {
                Text("📂 選擇業務資料表檔案", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("請由手機中選取「業務資料彙整-1150826.xlsx」資料表檔案。", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                Text("檔名減號後的數字為民國年月日(資料更新日期)，例如 1150826 = 民國115年08月26日，系統會自動擷取並顯示於儀表板。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(20.dp))

        if (state is UiState.Importing) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("匯入中… ${state.index}/${state.total}", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("工作表：${state.currentSheet}（${state.rowCount} 筆）",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { state.index.toFloat() / state.total.coerceAtLeast(1) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("首次匯入約需數秒，請稍候…", style = MaterialTheme.typography.labelSmall)
                }
            }
        } else {
            Button(
                onClick = { launcher.launch(arrayOf(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "application/octet-stream",
                    "*/*"
                )) },
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text("📁 選擇資料表檔案（.xlsx）", fontWeight = FontWeight.Bold)
            }
        }

        if (pickedName != null && state !is UiState.Importing) {
            Spacer(Modifier.height(16.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("已選擇檔案", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.height(4.dp))
                    Text(pickedName!!, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    val date = FileNameParser.parse(pickedName)
                    if (date != null) {
                        Text("📅 資料更新日期：${date.display}", fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary)
                    } else {
                        Text("⚠️ 無法從檔名判斷更新日期，請確認格式為「業務資料彙整-YYYYMMDD.xlsx」",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { pickedUri?.let { vm.importFromUri(it) } },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("開始匯入並開啟儀表板")
                    }
                }
            }
        }

        if (state is UiState.ImportError) {
            Spacer(Modifier.height(16.dp))
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(Modifier.padding(16.dp)) {
                    Text("❌ 匯入失敗", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                    Spacer(Modifier.height(4.dp))
                    Text(state.message, color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { launcher.launch(arrayOf("*/*")) }) {
                            Text("重新選擇檔案")
                        }
                        if (pickedUri != null) {
                            Button(onClick = { pickedUri?.let { vm.importFromUri(it) } }) {
                                Text("重試")
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))
        Text("匯入後資料儲存於本機，無需網路連線。",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center)
    }
}

private fun resolveDisplayName(context: android.content.Context, uri: Uri): String? {
    context.contentResolver.query(uri, null, null, null, null)?.use { c ->
        val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (idx >= 0 && c.moveToFirst()) return c.getString(idx)
    }
    return null
}
