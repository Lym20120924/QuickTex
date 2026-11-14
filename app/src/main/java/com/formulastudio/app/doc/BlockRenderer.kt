package com.formulastudio.app.doc

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.ContextThemeWrapper
import com.formulastudio.app.R
import com.formulastudio.app.editor.CanvasOps
import com.formulastudio.app.editor.FormulaEditorView
import com.formulastudio.app.editor.SvgOps
import com.formulastudio.app.util.Prefs

/**
 * 把文档块渲染成位图 / SVG。
 *
 * 用固定的浅色主题创建离屏视图，保证公式「白底深字」，
 * 与 app 当前是浅色还是深色无关。
 */
object BlockRenderer {

    private fun lightContext(context: Context): Context =
        ContextThemeWrapper(context, R.style.Theme_FormulaStudio_Light)

    /**
     * 渲染公式。按**目标字号**换算缩放比，这样所有公式的视觉大小一致
     * （不会像按宽度归一化那样：单字符被拉巨大、长公式被压成小点）。
     */
    fun mathBitmap(context: Context, latex: String, inline: Boolean = false): Bitmap? {
        return try {
            val c = lightContext(context)
            val v = FormulaEditorView(c)
            // 强制黑字：离屏渲染不依赖主题解析，避免“白底白字”看起来空白
            v.applySettings(Prefs.fontScale(c), false, Color.BLACK)
            v.loadLatex(latex.replace("&", " "))
            val target = if (inline) 110f else 165f
            val s = (target / v.baseSizePx()).coerceAtLeast(0.4f)
            val bmp = v.renderToBitmap(s) ?: return null
            capWidth(bmp, 2400)
        } catch (e: Exception) {
            null
        }
    }

    /** 限制最大宽度，避免超长公式图片过大 */
    private fun capWidth(src: Bitmap, maxW: Int): Bitmap {
        if (src.width <= maxW || src.width <= 0) return src
        val h = (src.height * maxW.toFloat() / src.width).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, maxW, h, true)
    }

    fun mathSvg(context: Context, latex: String): String? {
        return try {
            val c = lightContext(context)
            val v = FormulaEditorView(c)
            v.applySettings(Prefs.fontScale(c), false)
            v.loadLatex(latex.replace("&", " "))
            v.renderToSvg()
        } catch (e: Exception) {
            null
        }
    }

    fun figureBitmap(context: Context, figure: String, src: String): Bitmap? {
        return try {
            val sz = if (figure == "pgfplots") FigureRenderer.pgfplotsSize(src)
            else FigureRenderer.tikzSize(src)
            val w = sz.w.toInt().coerceIn(1, 4096)
            val h = sz.h.toInt().coerceIn(1, 4096)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.drawColor(android.graphics.Color.WHITE)
            if (figure == "pgfplots") FigureRenderer.pgfplots(src, CanvasOps(canvas))
            else FigureRenderer.tikz(src, CanvasOps(canvas))
            capWidth(bmp, 2400)
        } catch (e: Exception) {
            null
        }
    }

    fun figureSvg(context: Context, figure: String, src: String): String? {
        return try {
            val svg = SvgOps(
                context = context,
                fontFamily = { "KaTeX_Main,KaTeX_AMS,serif" },
                italic = { false }
            )
            if (figure == "pgfplots") FigureRenderer.pgfplots(src, svg)
            else FigureRenderer.tikz(src, svg)
            val sz = if (figure == "pgfplots") FigureRenderer.pgfplotsSize(src)
            else FigureRenderer.tikzSize(src)
            svg.build(sz.w, sz.h)
        } catch (e: Exception) {
            null
        }
    }
}
