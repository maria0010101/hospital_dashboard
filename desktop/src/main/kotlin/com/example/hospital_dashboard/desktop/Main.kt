package com.example.hospital_dashboard.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.example.hospital_dashboard.desktop.theme.HospitalDashboardDesktopTheme
import com.example.hospital_dashboard.desktop.ui.DashboardScreen
import com.example.hospital_dashboard.desktop.ui.FilePickScreen

fun main() = application {
    val vm = remember { DesktopViewModel() }
    val uiState by vm.uiState.collectAsState()
    val isDarkMode by vm.isDarkMode.collectAsState()

    val windowState = rememberWindowState(width = 1280.dp, height = 820.dp)

    Window(
        onCloseRequest = {
            vm.close()
            exitApplication()
        },
        state = windowState,
        title = "🏥 醫院營運業務儀表板"
    ) {
        HospitalDashboardDesktopTheme(darkTheme = isDarkMode) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                when (val state = uiState) {
                    is UiState.Ready -> DashboardScreen(vm, state)
                    else -> FilePickScreen(vm, state)
                }
            }
        }
    }
}
