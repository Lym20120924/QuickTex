package com.formulastudio.app.doc

import android.app.Activity
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
 * 公式与图形都用**自研 Canvas 引擎**渲染，不依赖 WebView，离线且快。
 */
object BlockRenderer {

    private fun lightContext(context: Context): Context =
        ContextThemeWrapper(context, R.style.Theme_FormulaStudio_Light)

    /** 公式位图：自研排版引擎（纯 Canvas，无 WebView），白底黑字 */
    fun mathBitmap(context: Context, latex: String, inline: Boolean): Bitmap? {
        return try {
            val c = lightContext(context)
            val v = FormulaEditorView(c)
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

    /** 公式 SVG（自研引擎，真矢量）。失败返回 null */
    fun mathSvg(context: Context, latex: String): String? {
        return try {
            val c = lightContext(context)
            val v = FormulaEditorView(c)
            v.applySettings(Prefs.fontScale(c), false, Color.BLACK)
            v.loadLatex(latex.replace("&", " "))
            v.renderToSvg()
        } catch (e: Exception) {
            null
        }
    }

    /** 回退：自研排版引擎（离屏视图） */
    private fun fallbackMathBitmap(context: Context, latex: String, inline: Boolean): Bitmap? {
        return try {
            val c = lightContext(context)
            val v = FormulaEditorView(c)
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

    /** 判断位图是不是一张纯白（空白）图 */
    private fun isBlank(bmp: Bitmap): Boolean {
        val w = bmp.width.coerceAtMost(60)
        val h = bmp.height.coerceAtMost(60)
        if (w <= 0 || h <= 0) return true
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        for (c in px) {
            if (Color.red(c) < 245 || Color.green(c) < 245 || Color.blue(c) < 245) return false
        }
        return true
    }

    /** 限制最大宽度，避免超长公式图片过大 */
    private fun capWidth(src: Bitmap, maxW: Int): Bitmap {
        if (src.width <= maxW || src.width <= 0) return src
        val h = (src.height * maxW.toFloat() / src.width).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, maxW, h, true)
    }

    // ---------- 图形（TikZ / pgfplots）----------

    fun figureBitmap(context: Context, figure: String, src: String): Bitmap? {
        return try {
            val sz = if (figure == "pgfplots") FigureRenderer.pgfplotsSize(src)
            else FigureRenderer.tikzSize(src)
            val w = sz.w.toInt().coerceIn(1, 4096)
            val h = sz.h.toInt().coerceIn(1, 4096)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.drawColor(Color.WHITE)
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
