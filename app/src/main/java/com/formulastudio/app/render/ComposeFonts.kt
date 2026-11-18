package com.formulastudio.app.render

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import com.formulastudio.app.R

/**
 * Compose 侧的字体族（阶段 4.4b 的 DrawScope 后端用）。
 *
 * 与 Android 端 res/font 目录下的 font-family 配置保持一致：
 *  - main → KaTeX_Main（正文）
 *  - var  → KaTeX_Main-Italic（变量，度量与正文同源）
 *  - op   → Main + Size 系列回退（大型运算符）
 *  - cjk  → Serif（汉字 / 全角标点）
 *  - mono → Monospace（未识别命令）
 */
object ComposeFonts {

    private fun f(res: Int, weight: FontWeight = FontWeight.Normal, style: FontStyle = FontStyle.Normal) =
        Font(res, weight, style)

    val main: FontFamily = FontFamily(
        f(R.font.katex_main_regular),
        f(R.font.katex_ams_regular),
        f(R.font.katex_size1_regular),
        f(R.font.katex_size2_regular),
        f(R.font.katex_size3_regular),
        f(R.font.katex_size4_regular)
    )

    val varFont: FontFamily = FontFamily(
        f(R.font.katex_main_italic, style = FontStyle.Italic),
        f(R.font.katex_main_regular),
        f(R.font.katex_ams_regular)
    )

    val op: FontFamily = FontFamily(
        f(R.font.katex_main_regular),
        f(R.font.katex_ams_regular),
        f(R.font.katex_size1_regular),
        f(R.font.katex_size2_regular),
        f(R.font.katex_size3_regular),
        f(R.font.katex_size4_regular)
    )

    val cjk: FontFamily = FontFamily.Serif
    val mono: FontFamily = FontFamily.Monospace

    /** fontId → 字体族 */
    fun familyOf(fontId: String): FontFamily = when (fontId) {
        "var" -> varFont
        "op" -> op
        "cjk" -> cjk
        "mono" -> mono
        else -> main
    }
}
