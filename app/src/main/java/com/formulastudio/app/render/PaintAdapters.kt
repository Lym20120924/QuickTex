package com.formulastudio.app.render

import android.content.res.Resources
import android.graphics.Paint
import android.graphics.Typeface
import com.formulastudio.app.R
import com.formulastudio.core.render.Ink
import com.formulastudio.core.render.TextStyle

/** Paint → 平台无关样式，让内核排版结果可以输出到任意 RenderTarget */

internal fun Paint.textStyle(): TextStyle = TextStyle(
    size = textSize,
    color = color,
    italic = typeface?.isItalic == true,
    family = when {
        typeface === Typeface.MONOSPACE -> "monospace"
        else -> "KaTeX_Main,KaTeX_AMS,KaTeX_Size1,KaTeX_Size2,KaTeX_Size3," +
            "KaTeX_Size4,KaTeX_Caligraphic,KaTeX_Script,KaTeX_Fraktur," +
            "KaTeX_Typewriter,serif"
    },
    // 精确标记字体，保证 TextMeasurer 选回同一字体（度量才和原来一致）
    fontId = when (typeface) {
        AppFonts.main -> "main"
        AppFonts.varFont -> "var"
        AppFonts.op -> "op"
        AppFonts.cjk -> "cjk"
        else -> if (typeface === Typeface.MONOSPACE) "mono" else "main"
    }
)

internal fun Paint.ink(): Ink = Ink(
    color = color,
    width = strokeWidth,
    filled = style == Paint.Style.FILL
)

/**
 * 把平台无关的 TextStyle 映射回 KaTeX 字体，
 * 保证 Canvas 后端与编辑器视觉完全一致。
 */
internal fun typefaceFor(res: Resources, style: TextStyle): Typeface = when (style.fontId) {
    "var" -> AppFonts.varFont
    "op" -> try {
        res.getFont(R.font.katex_op)
    } catch (e: Throwable) { AppFonts.main }
    "cjk" -> AppFonts.cjk
    "mono" -> Typeface.MONOSPACE
    else -> AppFonts.main
}
