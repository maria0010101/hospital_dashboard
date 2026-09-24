package com.example.hospital_dashboard.ui.adaptive

import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration

/**
 * 平板自適應尺寸級別（與既有 screenWidthDp >= 600 相容）。
 * Compact: < 600dp（手機直向，完全維持原樣）
 * Medium: 600..839dp（手機橫向、小平板）
 * Expanded: >= 840dp（大平板、橫向大螢幕）
 */
enum class AdaptiveSize { Compact, Medium, Expanded }

/** 由 WindowSizeClass 推導專案用級別。 */
fun WindowSizeClass.toAdaptiveSize(): AdaptiveSize = when (widthSizeClass) {
    WindowWidthSizeClass.Compact -> AdaptiveSize.Compact
    WindowWidthSizeClass.Medium -> AdaptiveSize.Medium
    else -> AdaptiveSize.Expanded
}

/** 提供 CompositionLocal 注入（若有需要），預設為 null */
val LocalWindowSize = compositionLocalOf<WindowSizeClass?> { null }

/** 由寬度 dp 純值推導 AdaptiveSize（便於無 UI 環境進行單元測試）。 */
fun widthDpToAdaptiveSize(widthDp: Int): AdaptiveSize = when {
    widthDp >= 840 -> AdaptiveSize.Expanded
    widthDp >= 600 -> AdaptiveSize.Medium
    else -> AdaptiveSize.Compact
}

/** 取得當前自適應級別（優先使用 LocalWindowSize，若無則降級讀取 LocalConfiguration）。 */
@Composable
fun currentAdaptiveSize(): AdaptiveSize {
    val windowSizeClass = LocalWindowSize.current
    if (windowSizeClass != null) {
        return windowSizeClass.toAdaptiveSize()
    }
    return widthDpToAdaptiveSize(LocalConfiguration.current.screenWidthDp)
}

val AdaptiveSize.isCompact: Boolean get() = this == AdaptiveSize.Compact
val AdaptiveSize.isAtLeastMedium: Boolean get() = this != AdaptiveSize.Compact
val AdaptiveSize.isExpanded: Boolean get() = this == AdaptiveSize.Expanded

/** 依尺寸回傳 A/B/C 值；Compact 一律回傳 compactValue（保證手機不變）。 */
fun <T> AdaptiveSize.pick(compactValue: T, mediumValue: T, expandedValue: T = mediumValue): T = when (this) {
    AdaptiveSize.Compact -> compactValue
    AdaptiveSize.Medium -> mediumValue
    AdaptiveSize.Expanded -> expandedValue
}
