package com.formulastudio.app.doc

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.view.ContextThemeWrapper
import com.formulastudio.app.R
import com.formulastudio.app.render.CanvasTarget
import com.formulastudio.app.editor.FormulaEditorView
import com.formulastudio.app.render.SvgTarget
import com.formulastudio.app.render.katexFontFaceCss
import com.formulastudio.app.util.Prefs
import com.formulastudio.core.model.DocBlock
import com.formulastudio.core.model.TextStyle

/** 把文档块渲染成位图 / SVG。
 *
 * 公式与图形都用**自研 Canvas 引擎**渲染，不依赖 WebView，离线且快。
 * 新增：段落文字 / 表格 / 图片 的样式渲染，用于 docx 双向闭环（A 方向）。
 */
object BlockRenderer {

    private fun lightContext(context: Context): Context =
        ContextThemeWrapper(context, R.style.Theme_FormulaStudio_Light)

    // ==================== 公式（原有，保留） ====================

    /** 公式位图：自研排版引擎（纯 Canvas，无 WebView）。 */
    fun mathBitmap(
        context: Context,
        latex: String,
        inline: Boolean,
        background: Int = Color.WHITE,
        inkColor: Int = Color.BLACK
    ): Bitmap? {
        return try {
            val c = lightContext(context)
            val v = FormulaEditorView(c)
            v.applySettings(Prefs.fontScale(c), false, inkColor)
            v.loadLatex(latex.replace("&", " "))
            val target = if (inline) 110f else 165f
            val s = (target / v.baseSizePx()).coerceAtLeast(0.4f)
            val bmp = v.renderToBitmap(s, background) ?: return null
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

    // ==================== 图形（TikZ / pgfplots，原有保留） ====================

    fun figureBitmap(context: Context, figure: String, src: String): Bitmap? {
        return try {
            val sz = if (figure == "pgfplots") FigureRenderer.pgfplotsSize(src)
            else FigureRenderer.tikzSize(src)
            val w = sz.w.toInt().coerceIn(1, 4096)
            val h = sz.h.toInt().coerceIn(1, 4096)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.drawColor(Color.WHITE)
            if (figure == "pgfplots") FigureRenderer.pgfplots(src, CanvasTarget(canvas, context.resources))
            else FigureRenderer.tikz(src, CanvasTarget(canvas, context.resources))
            capWidth(bmp, 2400)
        } catch (e: Exception) {
            null
        }
    }

    fun figureSvg(context: Context, figure: String, src: String): String? {
        return try {
            val svg = SvgTarget { katexFontFaceCss(context) }
            if (figure == "pgfplots") FigureRenderer.pgfplots(src, svg)
            else FigureRenderer.tikz(src, svg)
            val sz = if (figure == "pgfplots") FigureRenderer.pgfplotsSize(src)
            else FigureRenderer.tikzSize(src)
            svg.build(sz.w, sz.h)
        } catch (e: Exception) {
            null
        }
    }

    // ==================== 新增：段落文字样式渲染（A1/A4） ====================

    /** 把一段 [DocBlock.Text] 按 [TextStyle] 渲染成位图，供文档页预览与 docx 导出用。 */
    fun textBlockBitmap(
        context: Context,
        text: String,
        style: TextStyle,
        maxWidthPx: Int = 1000
    ): Bitmap? {
        return try {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = style.color
                textSize = style.fontSize * context.resources.displayMetrics.density
                typeface = typefaceFor(style)
                isFakeBoldText = style.bold
                textSkewX = if (style.italic) -0.25f else 0f
            }
            val lines = wrapText(text, paint, maxWidthPx.toFloat())
            val lineHeight = paint.textSize * 1.3f
            val totalH = lines.size * lineHeight + 12f
            val maxW = lines.map { paint.measureText(it) }.maxOrNull() ?: 0f
            val bw = maxW.coerceAtLeast(1f).toInt().coerceAtMost(maxWidthPx)
            val bh = totalH.coerceAtLeast(1f).toInt()
            val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.drawColor(Color.WHITE)
            var y = paint.textSize + 6f
            for (line in lines) {
                canvas.drawText(line, 0f, y, paint)
                y += lineHeight
            }
            bmp
        } catch (e: Exception) {
            null
        }
    }

    private fun typefaceFor(style: TextStyle): Typeface {
        val base = when (style.fontFamily) {
            "sans" -> Typeface.SANS_SERIF
            "mono" -> Typeface.MONOSPACE
            "cjk" -> Typeface.create("sans-serif", Typeface.NORMAL) ?: Typeface.SANS_SERIF
            else -> Typeface.SERIF
        }
        return when {
            style.bold && style.italic -> Typeface.create(base, Typeface.BOLD_ITALIC) ?: base
            style.bold -> Typeface.create(base, Typeface.BOLD) ?: base
            style.italic -> Typeface.create(base, Typeface.ITALIC) ?: base
            else -> base
        }
    }

    private fun wrapText(text: String, paint: Paint, maxW: Float): List<String> {
        val out = mutableListOf<String>()
        for (para in text.split("\n")) {
            if (para.isEmpty()) {
                out.add("")
                continue
            }
            var line = ""
            for (word in para.split(" ")) {
                val trial = if (line.isEmpty()) word else "$line $word"
                if (paint.measureText(trial) > maxW && line.isNotEmpty()) {
                    out.add(line)
                    line = word
                } else {
                    line = trial
                }
            }
            out.add(line)
        }
        return out
    }

    // ==================== 新增：表格渲染（A2/A4） ====================

    /** 把表格 [rows] 渲染成位图，带简单网格线与左对齐单元格文字。 */
    fun tableBitmap(
        context: Context,
        rows: List<List<String>>,
        style: TextStyle = TextStyle(),
        maxWidthPx: Int = 1000
    ): Bitmap? {
        return try {
            if (rows.isEmpty()) return null
            val colCount = rows.maxOf { it.size }
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = style.color
                textSize = style.fontSize * context.resources.displayMetrics.density
                typeface = typefaceFor(style)
            }
            val cellPadding = 6f
            val lineH = paint.textSize * 1.3f
            // 估算每列宽度（按最宽单元格）
            val colW = IntArray(colCount) { 0 }
            for (row in rows) {
                for (i in row.indices) {
                    val w = paint.measureText(row[i]).toInt() + cellPadding.toInt() * 2
                    colW[i] = maxOf(colW[i], w)
                }
            }
            val totalW = colW.sum().coerceAtMost(maxWidthPx)
            val rowH = lineH + cellPadding * 2
            val totalH = rows.size * rowH + 4f
            val bmp = Bitmap.createBitmap(totalW.coerceAtLeast(1), totalH.toInt().coerceAtLeast(1),
                Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.drawColor(Color.WHITE)
            val linePaint = Paint().apply {
                color = 0xFF999999.toInt()
                strokeWidth = 1f
            }
            var y = 2f
            for (row in rows) {
                var x = 0f
                for (i in row.indices) {
                    val cw = colW[i].toFloat()
                    // 单元格边框
                    canvas.drawRect(x, y, x + cw, y + rowH, linePaint)
                    // 单元格文字
                    canvas.drawText(row.getOrElse(i) { "" }, x + cellPadding, y + cellPadding + paint.textSize, paint)
                    x += cw
                }
                // 行尾补边框
                canvas.drawRect(x, y, x, y + rowH, linePaint)
                y += rowH
            }
            // 底边框
            canvas.drawRect(0f, y - rowH, totalW.toFloat(), y, linePaint)
            bmp
        } catch (e: Exception) {
            null
        }
    }

    // ==================== 新增：图片渲染（A3/A4） ====================

    /** 把图片字节解码并按给定 pt 尺寸缩放成位图（屏幕密度折算）。 */
    fun imageBitmap(
        context: Context,
        imageBytes: ByteArray,
        widthPt: Float,
        heightPt: Float
    ): Bitmap? {
        return try {
            val bmp = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size) ?: return null
            val density = context.resources.displayMetrics.density
            val targetW = (widthPt / 72f * 96f * density).coerceAtLeast(1f).toInt()
            val targetH = (heightPt / 72f * 96f * density).coerceAtLeast(1f).toInt()
            if (targetW != bmp.width || targetH != bmp.height) {
                Bitmap.createScaledBitmap(bmp, targetW, targetH, true)
            } else {
                bmp
            }
        } catch (e: Exception) {
            null
        }
    }

    // ==================== 工具 ====================

    /** 从 [DocBlock] 列表渲染成位图序列，供文档页预览。 */
    fun renderBlocksToBitmaps(
        context: Context,
        blocks: List<DocBlock>,
        imageBytes: Map<String, ByteArray> = emptyMap()
    ): List<Pair<DocBlock, Bitmap?>> {
        return blocks.map { block ->
            val bmp = when (block) {
                is DocBlock.Text -> textBlockBitmap(context, block.text, block.style)
                is DocBlock.Math -> mathBitmap(context, block.latex, !block.display)
                is DocBlock.Figure -> figureBitmap(context, block.kind, block.source)
                is DocBlock.Table -> tableBitmap(context, block.rows, TextStyle())
                is DocBlock.Image -> imageBytes[block.src]?.let {
                    imageBitmap(context, it, block.width, block.height)
                } ?: null
            }
            block to bmp
        }
    }
}
