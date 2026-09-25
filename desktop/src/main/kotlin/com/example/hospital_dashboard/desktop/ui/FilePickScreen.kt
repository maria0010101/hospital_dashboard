package com.example.hospital_dashboard.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hospital_dashboard.desktop.DesktopViewModel
import com.example.hospital_dashboard.desktop.UiState
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.SwingUtilities

@Composable
fun FilePickScreen(vm: DesktopViewModel, state: UiState) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        when (state) {
            is UiState.Importing -> {
                Card(
                    modifier = Modifier.widthIn(max = 500.dp).padding(24.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(56.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 5.dp
                        )
                        Text(
                            "正在匯入業務資料...",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "工作表：${state.currentSheet} (${state.index} / ${state.total})",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (state.rowCount > 0) {
                            Text(
                                "已載入 ${state.rowCount} 列資料",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        LinearProgressIndicator(
                            progress = { if (state.total > 0) state.index.toFloat() / state.total.toFloat() else 0f },
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            is UiState.ImportError -> {
                Card(
                    modifier = Modifier.widthIn(max = 500.dp).padding(24.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            "⚠️ 匯入失敗",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Button(
                            onClick = { vm.backToFilePick() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("重新選擇檔案")
                        }
                    }
                }
            }
            else -> {
                Card(
                    modifier = Modifier.widthIn(max = 540.dp).padding(24.dp),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(36.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        Text("🏥", fontSize = 48.sp)
                        Text(
                            "醫院營運業務儀表板",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "請選取包含「門診、住院、病床、醫師、疫苗」等 9 大業務工作表之 Excel 檔案 (*.xlsx)，系統將自動解析並載入本機資料庫。",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 22.sp
                        )

                        Spacer(Modifier.height(8.dp))

                        Button(
                            onClick = {
                                SwingUtilities.invokeLater {
                                    val dialog = FileDialog(null as Frame?, "選擇業務資料 Excel 檔案", FileDialog.LOAD)
                                    dialog.file = "*.xlsx"
                                    dialog.setFilenameFilter { _, name -> name.lowercase().endsWith(".xlsx") }
                                    dialog.isVisible = true
                                    val file = dialog.file
                                    val dir = dialog.directory
                                    if (file != null && dir != null) {
                                        val picked = File(dir, file)
                                        if (picked.exists()) {
                                            vm.importFromFile(picked)
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("選擇業務資料 Excel 檔案 (*.xlsx)", fontWeight = FontWeight.Bold)
                        }

                        Text(
                            "🔒 本系統採純本機離線處理，所有資料庫儲存於本機使用目錄，絕不主動對外傳輸業務資料。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
