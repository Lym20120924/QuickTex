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

    fun mathBitmap(context: Context, latex: String, scale: Float = 2f): Bitmap? {
        return try {
            val c = lightContext(context)
            val v = FormulaEditorView(c)
            // 强制黑字：离屏渲染不依赖主题解析，避免“白底白字”看起来空白
            v.applySettings(Prefs.fontScale(c), false, Color.BLACK)
            v.loadLatex(latex.replace("&", " "))
            val bmp = v.renderToBitmap(scale) ?: return null
            normalize(bmp)
        } catch (e: Exception) {
            null
        }
    }

    /** 把图片宽度归一化，避免「大的太大、小的太小」 */
    private fun normalize(src: Bitmap, targetW: Int = 1000): Bitmap {
        if (src.width <= 0 || src.height <= 0) return src
        var w = targetW
        var h = (src.height * w.toFloat() / src.width).toInt().coerceAtLeast(1)
        if (h > 3000) {
            h = 3000
            w = (src.width * h.toFloat() / src.height).toInt().coerceAtLeast(1)
        }
        return if (w == src.width && h == src.height) src
        else Bitmap.createScaledBitmap(src, w, h, true)
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
            normalize(bmp)
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
