package com.formulastudio.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.formulastudio.app.util.Prefs

/**
 * QuickTex 的 Compose 设计系统入口。
 *
 * 与旧的外观设置中心共用同一套 [Prefs]：
 * 界面风格 / 配色风格 / 强调色 / 对比度 / 显示模式（含纯黑 AMOLED）
 * ——所以 Compose 页面和 View 页面外观一致。
 */
@Composable
fun QuickTexTheme(
    darkTheme: Boolean? = null,
    content: @Composable () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // 以应用自身的显示模式为准（外观设置中心可强制浅色/深色/AMOLED），
    // 不能只依赖 isSystemInDarkTheme()，否则会出现「背景深色 + 文字深色」的反转
    val dark = darkTheme ?: shouldUseDark(context)
    val style = Prefs.colorStyle(context)
    val pal = palette(style)

    // 强调色：优先用用户自选，否则用配色风格的种子色
    val accent = Color(Prefs.accentColor(context))
    val useCustomAccent = Prefs.colorStyle(context) == Prefs.COLOR_DYNAMIC &&
        Prefs.accentColor(context) != 0xFF1565C0.toInt()

    val primary = if (useCustomAccent) accent else pal.seed
    val secondary = pal.secondary
    val tertiary = pal.tertiary

    // 对比度：>1 提高对比，<1 降低对比
    val contrast = Prefs.contrast(context).coerceIn(0.6f, 1.6f)
    fun adjust(c: Color): Color = if (contrast == 1f) c else lerp(c, if (dark) Color.White else Color.Black, (contrast - 1f) * 0.5f)

    val scheme = if (dark) {
        val amoled = Prefs.displayMode(context) == Prefs.DISPLAY_AMOLED
        val bg = if (amoled) Color.Black else Color(0xFF121212)
        val surface = if (amoled) Color.Black else Color(0xFF1E1E1E)
        darkColorScheme(
            primary = adjust(primary),
            secondary = adjust(secondary),
            tertiary = adjust(tertiary),
            background = bg,
            surface = surface,
            surfaceVariant = if (amoled) Color(0xFF0A0A0A) else Color(0xFF2A2A2A)
        )
    } else {
        lightColorScheme(
            primary = adjust(primary),
            secondary = adjust(secondary),
            tertiary = adjust(tertiary),
            background = Color(0xFFF7F9FC),
            surface = Color.White,
            surfaceVariant = Color(0xFFEDF1F6)
        )
    }

    // 显式提供 LocalContentColor：否则未指定颜色的文字会回落到默认黑色，
    // 在深色背景下几乎不可见（真机截图确认过这个问题）
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.material3.LocalContentColor provides scheme.onSurface
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = QuickTexTypography,
            content = content
        )
    }
}

/** 当前是否应为深色（供非 Compose 代码判断） */
fun shouldUseDark(context: android.content.Context): Boolean = when (Prefs.displayMode(context)) {
    Prefs.DISPLAY_LIGHT -> false
    Prefs.DISPLAY_DARK, Prefs.DISPLAY_AMOLED -> true
    else -> when (Prefs.darkMode(context)) {
        Prefs.DARK_LIGHT -> false
        Prefs.DARK_DARK -> true
        // 真正读取系统夜间模式（Configuration 的 uiMode），而不是默认返回 false
        else -> (context.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
    }
}
