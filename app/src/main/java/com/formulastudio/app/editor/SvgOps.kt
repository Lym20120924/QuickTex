package com.formulastudio.app.editor

import android.content.Context
import android.graphics.Paint
import android.util.Base64
import java.util.Locale

/**
 * 把自绘公式的绘制命令转成 SVG（真矢量，零第三方依赖）。
 *
 * 与屏幕渲染共用 [FormulaEditorView] 的排版结果，因此导出效果与编辑器完全一致；
 * 同时把 KaTeX 字体以 base64 内嵌，保证在没有安装该字体的设备上也能正确显示。
 */
class SvgOps(
    private val context: Context,
    /** 根据 Paint 决定 font-family */
    private val fontFamily: (Paint) -> String,
    /** 根据 Paint 决定是否斜体 */
    private val italic: (Paint) -> Boolean = { false }
) : DrawOps {

    private val body = StringBuilder()
    private val stack = ArrayDeque<FloatArray>()
    private var tx = 0f
    private var ty = 0f
    private var scaleFactor = 1f

    override fun save() {
        stack.addLast(floatArrayOf(tx, ty, scaleFactor))
    }

    override fun restore() {
        val s = stack.removeLastOrNull() ?: return
        tx = s[0]; ty = s[1]; scaleFactor = s[2]
    }

    override fun translate(dx: Float, dy: Float) {
        tx += dx; ty += dy
    }

    override fun scale(sx: Float, sy: Float) {
        scaleFactor *= sx
    }

    private fun X(x: Float) = (x + tx) * scaleFactor
    private fun Y(y: Float) = (y + ty) * scaleFactor

    override fun drawText(text: String, x: Float, y: Float, paint: Paint) {
        if (text.isEmpty()) return
        val sb = StringBuilder()
        sb.append("<text x=\"").append(num(X(x))).append("\" y=\"").append(num(Y(y)))
            .append("\" font-family=\"").append(fontFamily(paint))
            .append("\" font-size=\"").append(num(paint.textSize * scaleFactor))
            .append("\" fill=\"").append(hex(paint.color)).append("\"")
        if (italic(paint)) sb.append(" font-style=\"italic\"")
        sb.append('>').append(escapeXml(text)).append("</text>\n")
        body.append(sb)
    }

    override fun drawLine(x1: Float, y1: Float, x2: Float, y2: Float, paint: Paint) {
        body.append("<line x1=\"").append(num(X(x1))).append("\" y1=\"").append(num(Y(y1)))
            .append("\" x2=\"").append(num(X(x2))).append("\" y2=\"").append(num(Y(y2)))
            .append("\" stroke=\"").append(hex(paint.color))
            .append("\" stroke-width=\"").append(num(paint.strokeWidth * scaleFactor))
            .append("\" stroke-linecap=\"round\"/>\n")
    }

    override fun drawPath(ops: List<PathOp>, paint: Paint) {
        if (ops.isEmpty()) return
        val filled = paint.style == Paint.Style.FILL
        body.append("<path d=\"").append(ops.joinToString(" ") { it.toSvg() })
            .append("\" fill=\"").append(if (filled) hex(paint.color) else "none")
            .append("\" stroke=\"").append(if (filled) "none" else hex(paint.color))
            .append("\" stroke-width=\"").append(num(paint.strokeWidth * scaleFactor))
            .append("\" stroke-linecap=\"round\" stroke-linejoin=\"round\"/>\n")
    }

    override fun drawRoundRect(
        left: Float, top: Float, right: Float, bottom: Float,
        rx: Float, ry: Float, paint: Paint
    ) {
        body.append("<rect x=\"").append(num(X(left))).append("\" y=\"").append(num(Y(top)))
            .append("\" width=\"").append(num((right - left) * scaleFactor))
            .append("\" height=\"").append(num((bottom - top) * scaleFactor))
            .append("\" rx=\"").append(num(rx * scaleFactor))
            .append("\" ry=\"").append(num(ry * scaleFactor))
            .append("\" fill=\"none\" stroke=\"").append(hex(paint.color))
            .append("\" stroke-width=\"").append(num(paint.strokeWidth * scaleFactor))
            .append("\"/>\n")
    }

    /** 组装成完整 SVG 文档 */
    fun build(width: Float, height: Float): String {
        val w = num(width)
        val h = num(height)
        return buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(w)
                .append("\" height=\"").append(h)
                .append("\" viewBox=\"0 0 ").append(w).append(' ').append(h)
                .append("\" role=\"img\" focusable=\"false\">\n")
            val css = fontFaceCss()
            if (css.isNotEmpty()) append("<style>\n").append(css).append("</style>\n")
            append(body)
            append("</svg>\n")
        }
    }

    /** 内嵌 KaTeX 字体（woff2 → base64），让 SVG 在任何设备上都长得一样 */
    private fun fontFaceCss(): String {
        val fonts = listOf(
            Triple("KaTeX_Main", "KaTeX_Main-Regular", "normal"),
            Triple("KaTeX_Main", "KaTeX_Main-Italic", "italic"),
            Triple("KaTeX_Math", "KaTeX_Math-Italic", "italic"),
            Triple("KaTeX_AMS", "KaTeX_AMS-Regular", "normal"),
            Triple("KaTeX_Size1", "KaTeX_Size1-Regular", "normal"),
            Triple("KaTeX_Size2", "KaTeX_Size2-Regular", "normal"),
            Triple("KaTeX_Size3", "KaTeX_Size3-Regular", "normal"),
            Triple("KaTeX_Size4", "KaTeX_Size4-Regular", "normal"),
            Triple("KaTeX_Caligraphic", "KaTeX_Caligraphic-Regular", "normal"),
            Triple("KaTeX_Script", "KaTeX_Script-Regular", "normal"),
            Triple("KaTeX_Fraktur", "KaTeX_Fraktur-Regular", "normal"),
            Triple("KaTeX_Typewriter", "KaTeX_Typewriter-Regular", "normal")
        )
        val sb = StringBuilder()
        for ((family, file, style) in fonts) {
            val b64 = try {
                context.assets.open("katex/fonts/$file.woff2").use { it.readBytes() }
                    .let { Base64.encodeToString(it, Base64.NO_WRAP) }
            } catch (e: Exception) {
                null
            }
            if (b64 != null) {
                sb.append("@font-face{font-family:'").append(family)
                    .append("';font-style:").append(style)
                    .append(";src:url(data:font/woff2;base64,").append(b64)
                    .append(") format('woff2');}\n")
            }
        }
        return sb.toString()
    }

    private fun num(v: Float): String =
        String.format(Locale.US, "%.2f", v)

    private fun hex(color: Int): String {
        val a = (color ushr 24) and 0xFF
        return if (a == 0xFF) {
            String.format(Locale.US, "#%06X", color and 0xFFFFFF)
        } else {
            String.format(Locale.US, "#%02X%06X", a, color and 0xFFFFFF)
        }
    }

    private fun escapeXml(s: String): String = buildString {
        for (c in s) when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&apos;")
            else -> append(c)
        }
    }
}
