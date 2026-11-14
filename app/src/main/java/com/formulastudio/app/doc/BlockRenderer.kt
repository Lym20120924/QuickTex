package com.formulastudio.app.doc

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
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

    fun mathBitmap(context: Context, latex: String, scale: Float = 2f): Bitmap? {
        return try {
            val c = lightContext(context)
            val v = FormulaEditorView(c)
            v.applySettings(Prefs.fontScale(c), false)
            v.loadLatex(latex.replace("&", " "))
            v.renderToBitmap(scale)
        } catch (e: Exception) {
            null
        }
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
            bmp
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
