package com.v4ex.android.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** 应用颜色 token。正文、弱文字、画布、浮层、选中态及插画分别承担独立语义。 */
@Immutable
data class ColorTokens(
    val Primary: Color, val Secondary: Color, val Tertiary: Color,
    val Surface: Color, val Elevated: Color, val ChipBg: Color, val Line: Color,
    val Badge: Color, val OnBadge: Color, val OnPrimary: Color,
    val Blue: Color, val Purple: Color,
    val FilterSelected: Color, val OnFilterSelected: Color,
    val ArtStroke: Color, val ArtFill: Color, val ArtHair: Color,
    val Scrim: Color = Color.Black.copy(alpha = 0.32f),
)

val LightTokens = ColorTokens(
    Primary = Color(0xFF111114), Secondary = Color(0xFF87878C), Tertiary = Color(0xFFB4B4B9),
    Surface = Color(0xFFFDFDFC), Elevated = Color.White, ChipBg = Color(0xFFF1F0ED), Line = Color(0xFFEDEBE8),
    Badge = Color(0xFFE8453C), OnBadge = Color.White, OnPrimary = Color.White,
    Blue = Color(0xFF3F6BE0), Purple = Color(0xFF8B5CF6),
    FilterSelected = Color(0xFF111114), OnFilterSelected = Color.White,
    ArtStroke = Color(0xFF9A9A9F), ArtFill = Color(0xFFF0EFEC), ArtHair = Color(0xFFD8D7D3),
)

val DarkTokens = ColorTokens(
    Primary = Color(0xFFEDEDEB), Secondary = Color(0xFFABABB2), Tertiary = Color(0xFF85858F),
    Surface = Color(0xFF151517), Elevated = Color(0xFF222225), ChipBg = Color(0xFF2B2B30), Line = Color(0xFF36363C),
    Badge = Color(0xFFFF6C63), OnBadge = Color(0xFF25100F), OnPrimary = Color(0xFF151517),
    Blue = Color(0xFF93B1FF), Purple = Color(0xFFBAA1FF),
    FilterSelected = Color(0xFF484850), OnFilterSelected = Color(0xFFF2F2F0),
    ArtStroke = Color(0xFF96969F), ArtFill = Color(0xFF2B2B30), ArtHair = Color(0xFF505058),
    Scrim = Color.Black.copy(alpha = 0.56f),
)
