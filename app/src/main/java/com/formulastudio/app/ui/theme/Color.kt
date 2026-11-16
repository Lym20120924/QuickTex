package com.formulastudio.app.ui.theme

import androidx.compose.ui.graphics.Color
import com.formulastudio.app.util.Prefs

/** 配色风格（对应外观设置中心的「配色风格」） */
enum class Palette { DYNAMIC, CLASSIC, FRESH, DEEP }

fun paletteOf(style: Int): Palette = when (style) {
    Prefs.COLOR_CLASSIC -> Palette.CLASSIC
    Prefs.COLOR_FRESH -> Palette.FRESH
    Prefs.COLOR_DEEP -> Palette.DEEP
    else -> Palette.DYNAMIC
}

/** 各风格的种子色 */
fun Palette.seed(): Color = when (this) {
    Palette.DYNAMIC -> Color(0xFF1565C0)   // 经典蓝
    Palette.CLASSIC -> Color(0xFF1565C0)
    Palette.FRESH -> Color(0xFF2E7D32)     // 清新绿
    Palette.DEEP -> Color(0xFF6A1B9A)      // 深邃紫
}

/** 一组完整色板 */
data class QuickTexPalette(
    val seed: Color,
    val secondary: Color,
    val tertiary: Color
)

fun palette(style: Int): QuickTexPalette {
    val p = paletteOf(style)
    return when (p) {
        Palette.FRESH -> QuickTexPalette(p.seed(), Color(0xFF00897B), Color(0xFFF9A825))
        Palette.DEEP -> QuickTexPalette(p.seed(), Color(0xFF4527A0), Color(0xFF00ACC1))
        else -> QuickTexPalette(p.seed(), Color(0xFF455A64), Color(0xFF00897B))
    }
}

// 设计系统预览页用的一组强调色
val BluePrimary = Color(0xFF1565C0)
val OrangePrimary = Color(0xFFEF6C00)
val TealPrimary = Color(0xFF00897B)
val VioletPrimary = Color(0xFF6A1B9A)
