package com.example.hospital_dashboard.desktop.ui.adaptive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

enum class AdaptiveSize { Compact, Medium, Expanded }

val LocalAdaptiveSize = compositionLocalOf { AdaptiveSize.Expanded }

@Composable
fun currentAdaptiveSize(): AdaptiveSize = LocalAdaptiveSize.current

val AdaptiveSize.isCompact: Boolean get() = this == AdaptiveSize.Compact
val AdaptiveSize.isAtLeastMedium: Boolean get() = this != AdaptiveSize.Compact
val AdaptiveSize.isExpanded: Boolean get() = this == AdaptiveSize.Expanded

fun <T> AdaptiveSize.pick(compactValue: T, mediumValue: T, expandedValue: T = mediumValue): T = when (this) {
    AdaptiveSize.Compact -> compactValue
    AdaptiveSize.Medium -> mediumValue
    AdaptiveSize.Expanded -> expandedValue
}

fun Modifier.adaptiveMarquee(): Modifier = this
fun Modifier.adaptiveMarquee(enabled: Boolean): Modifier = this

fun Modifier.statusBarsPadding(): Modifier = this
fun Modifier.navigationBarsPadding(): Modifier = this

@Composable
fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    // Desktop Esc key or close buttons handle this
}

class WindowConfig(val screenWidthDp: Int = 1280, val screenHeightDp: Int = 800)
val LocalConfiguration = compositionLocalOf { WindowConfig() }

/**
 * 桌面版自適應面板，以優雅的對話框卡片呈現
 */
@Composable
fun AdaptiveSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: Any? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = modifier
                .fillMaxWidth(0.75f)
                .widthIn(max = 960.dp)
                .fillMaxHeight(0.90f)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                content()
            }
        }
    }
}
