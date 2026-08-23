package com.example.hospital_dashboard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.hospital_dashboard.ui.DashboardScreen
import com.example.hospital_dashboard.ui.FilePickScreen
import com.example.hospital_dashboard.ui.charts.ZoomChartScreen
import com.example.hospital_dashboard.ui.theme.Hospital_dashboardTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Hospital_dashboardTheme {
                val vm: DashboardViewModel = viewModel()
                val zoom by vm.zoomChart.collectAsState()
                if (zoom != null) {
                    ZoomChartScreen(vm, zoom!!, onClose = { vm.closeZoom() })
                } else {
                    when (val s = vm.uiState.collectAsState().value) {
                        is UiState.Ready -> DashboardScreen(vm, s)
                        is UiState.NoData,
                        is UiState.Importing,
                        is UiState.ImportError -> FilePickScreen(vm)
                        UiState.Loading -> Box(
                            Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) { CircularProgressIndicator() }
                    }
                }
            }
        }
    }
}
