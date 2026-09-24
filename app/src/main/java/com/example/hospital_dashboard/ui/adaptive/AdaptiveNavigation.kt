package com.example.hospital_dashboard.ui.adaptive

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

val DASHBOARD_TABS = listOf("🚪 門急診", "🛏️ 住院", "🏥 病床", "📋 其他", "🤖 AI 分析")

@Composable
fun AdaptiveTabs(
    size: AdaptiveSize,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onShowFilters: (() -> Unit)? = null,
    onBackToFilePick: (() -> Unit)? = null,
    filterLabel: String = "⚙ 篩選",
    backLabel: String = "🔄 更換"
) {
    when (size) {
        AdaptiveSize.Compact -> {
            PrimaryScrollableTabRow(selectedTabIndex = selected, edgePadding = 8.dp, modifier = modifier) {
                DASHBOARD_TABS.forEachIndexed { i, t ->
                    Tab(selected = selected == i, onClick = { onSelect(i) }, text = { Text(t) })
                }
            }
        }
        else -> {
            NavigationRail(
                modifier = modifier,
                header = {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "🏥",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            ) {
                Spacer(Modifier.height(8.dp))
                DASHBOARD_TABS.forEachIndexed { i, t ->
                    val emoji = t.takeWhile { !it.isWhitespace() }
                    val label = t.dropWhile { !it.isWhitespace() }.trim()
                    NavigationRailItem(
                        selected = selected == i,
                        onClick = { onSelect(i) },
                        icon = { Text(emoji) },
                        label = { Text(label, maxLines = 1) }
                    )
                }
                Spacer(Modifier.weight(1f))
                if (onShowFilters != null) {
                    TextButton(onClick = onShowFilters) {
                        Text(filterLabel, maxLines = 1, style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (onBackToFilePick != null) {
                    TextButton(onClick = onBackToFilePick) {
                        Text(backLabel, maxLines = 1, style = MaterialTheme.typography.labelSmall)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}
