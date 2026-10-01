package com.example.hospital_dashboard.desktop.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Composable
fun positiveTextColor(): Color = if (MaterialTheme.colorScheme.surface.luminance() < 0.4f)
    Color(0xFF84D9AA) else Color(0xFF1E8449)

@Composable
fun negativeTextColor(): Color = MaterialTheme.colorScheme.error

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80,
    background = Color(0xFF141218),
    surface = Color(0xFF141218),
    onPrimary = Color(0xFF381E72),
    onSecondary = Color(0xFF332D41),
    onTertiary = Color(0xFF492532),
    onBackground = Color(0xFFE6E0E9),
    onSurface = Color(0xFFE6E0E9),
    surfaceVariant = Color(0xFF2B2930),
    onSurfaceVariant = Color(0xFFCAC4D0),
    surfaceContainer = Color(0xFF211F26),
    surfaceContainerHigh = Color(0xFF2B2930),
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF49454F)
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40,
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454E),
    outline = Color(0xFF79747E),
    outlineVariant = Color(0xFFCAC4D0)
)

val DesktopTypography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
)

@Composable
fun HospitalDashboardDesktopTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    paletteIndex: Int = 0,
    content: @Composable () -> Unit
) {
    val base = if (darkTheme) DarkColorScheme else LightColorScheme
    val colorScheme = when (paletteIndex) {
        1 -> base.copy(primary = Color(if (darkTheme) 0xFF90CAF9 else 0xFF145CA8),
            secondary = Color(if (darkTheme) 0xFFB0C9E8 else 0xFF426286),
            tertiary = Color(if (darkTheme) 0xFF8BD1E6 else 0xFF006B80))
        2 -> base.copy(primary = Color(if (darkTheme) 0xFF81D8C5 else 0xFF006B5C),
            secondary = Color(if (darkTheme) 0xFF9CCFC1 else 0xFF42685E),
            tertiary = Color(if (darkTheme) 0xFFFFCB91 else 0xFF875500))
        else -> base
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = DesktopTypography,
        content = content
    )
}
