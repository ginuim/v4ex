package com.v4ex.android.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

enum class ThemeMode(val label: String) {
    SYSTEM("跟随系统"), LIGHT("浅色"), DARK("深色");
    companion object {
        fun fromStored(value: String?): ThemeMode = entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}

private val LocalPalette = staticCompositionLocalOf { LightTokens }

/** 语义颜色入口；随 CompositionLocal 更新，避免全局可变主题。 */
object Ink {
    val Primary: Color @Composable get() = LocalPalette.current.Primary
    val Secondary: Color @Composable get() = LocalPalette.current.Secondary
    val Tertiary: Color @Composable get() = LocalPalette.current.Tertiary
    val Surface: Color @Composable get() = LocalPalette.current.Surface
    val Elevated: Color @Composable get() = LocalPalette.current.Elevated
    val ChipBg: Color @Composable get() = LocalPalette.current.ChipBg
    val Line: Color @Composable get() = LocalPalette.current.Line
    val Badge: Color @Composable get() = LocalPalette.current.Badge
    val OnBadge: Color @Composable get() = LocalPalette.current.OnBadge
    val OnPrimary: Color @Composable get() = LocalPalette.current.OnPrimary
    val FilterSelected: Color @Composable get() = LocalPalette.current.FilterSelected
    val OnFilterSelected: Color @Composable get() = LocalPalette.current.OnFilterSelected
    val Blue: Color @Composable get() = LocalPalette.current.Blue
    val Purple: Color @Composable get() = LocalPalette.current.Purple
    val ArtStroke: Color @Composable get() = LocalPalette.current.ArtStroke
    val ArtFill: Color @Composable get() = LocalPalette.current.ArtFill
    val ArtHair: Color @Composable get() = LocalPalette.current.ArtHair
    val Scrim: Color @Composable get() = LocalPalette.current.Scrim
}

private val Typo = Typography(
    headlineLarge = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Medium),
    titleMedium = TextStyle(fontSize = 15.5.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontSize = 13.5.sp, lineHeight = 20.sp),
    labelSmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
)

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

@Composable
fun V4exTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val tokens = if (dark) DarkTokens else LightTokens
    val colors = if (dark) darkColorScheme() else lightColorScheme()
    val scheme = colors.copy(
        primary = tokens.Primary, onPrimary = tokens.OnPrimary,
        secondary = tokens.Blue, onSecondary = tokens.Surface,
        tertiary = tokens.Purple, onTertiary = tokens.Surface,
        background = tokens.Surface, onBackground = tokens.Primary,
        surface = tokens.Surface, onSurface = tokens.Primary,
        surfaceVariant = tokens.ChipBg, onSurfaceVariant = tokens.Secondary,
        surfaceContainer = tokens.Elevated, surfaceContainerHigh = tokens.Elevated,
        surfaceContainerHighest = tokens.ChipBg, surfaceTint = Color.Transparent,
        outline = tokens.Line, outlineVariant = tokens.Line,
        error = tokens.Badge, onError = tokens.OnBadge,
    )
    val view = LocalView.current
    SideEffect {
        view.context.activity()?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    CompositionLocalProvider(LocalPalette provides tokens) {
        MaterialTheme(colorScheme = scheme, typography = Typo, content = content)
    }
}
