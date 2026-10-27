package com.v4ex.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** 参考图那套：白底、几乎无卡片、靠留白和字重分区。 */
object Ink {
    val Primary = Color(0xFF111114)
    val Secondary = Color(0xFF87878C)
    val Tertiary = Color(0xFFB4B4B9)
    val Surface = Color(0xFFFDFDFC)
    val ChipBg = Color(0xFFF1F0ED)
    val Line = Color(0xFFEDEBE8)
    val Badge = Color(0xFFE8453C)
    val Blue = Color(0xFF3F6BE0)
    val Purple = Color(0xFF8B5CF6)
}

private val Colors = lightColorScheme(
    primary = Ink.Primary,
    onPrimary = Color.White,
    background = Ink.Surface,
    onBackground = Ink.Primary,
    surface = Ink.Surface,
    onSurface = Ink.Primary,
    surfaceVariant = Ink.ChipBg,
    onSurfaceVariant = Ink.Secondary,
    outline = Ink.Line,
    outlineVariant = Ink.Line,
    error = Ink.Badge,
)

private val Typo = Typography(
    headlineLarge = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Medium, color = Ink.Primary),
    titleMedium = TextStyle(fontSize = 15.5.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium, color = Ink.Primary),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 25.sp, color = Ink.Primary),
    bodyMedium = TextStyle(fontSize = 13.5.sp, lineHeight = 20.sp, color = Ink.Primary),
    labelSmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, color = Ink.Secondary),
)

@Composable
fun V4exTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = Typo, content = content)
}
