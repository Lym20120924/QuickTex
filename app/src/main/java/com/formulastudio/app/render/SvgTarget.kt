package com.formulastudio.app.render

import android.util.Base64
import com.formulastudio.core.render.Ink
import com.formulastudio.core.render.PathOp
import com.formulastudio.core.render.RenderTarget
import com.formulastudio.core.render.TextStyle
import java.util.Locale

/**
 * SVG 后端：把内核的绘制意图转成 SVG（真矢量，零第三方依赖）。
 *
 * @param fontFaceCss 提供内嵌字体 CSS（KaTeX 字体 base64），让 SVG 在任何设备上都长得一样
 */
class SvgTarget(
    private val fontFaceCss: () -> String = { "" }
) : RenderTarget {

    private val body = StringBuilder()
    private val stack = ArrayDeque<FloatArray>()
    private var tx = 0f
    private var ty = 0f
    private var scaleFactor = 1f

    override fun save() { stack.addLast(floatArrayOf(tx, ty, scaleFactor)) }
    override fun restore() {
        val s = stack.removeLastOrNull() ?: return
        tx = s[0]; ty = s[1]; scaleFactor = s[2]
    }
    override fun translate(dx: Float, dy: Float) { tx += dx; ty += dy }
    override fun scale(sx: Float, sy: Float) { scaleFactor *= sx }

    private fun X(x: Float) = (x + tx) * scaleFactor
    private fun Y(y: Float) = (y + ty) * scaleFactor

    override fun drawText(text: String, x: Float, y: Float, style: TextStyle) {
        if (text.isEmpty()) return
        body.append("<text x=\"").append(num(X(x))).append("\" y=\"").append(num(Y(y)))
            .append("\" font-family=\"").append(style.family)
            .append("\" font-size=\"").append(num(style.size * scaleFactor))
            .append("\" fill=\"").append(hex(style.color)).append("\"")
        if (style.italic) body.append(" font-style=\"italic\"")
        body.append('>').append(escapeXml(text)).append("</text>\n")
    }

    override fun drawLine(x1: Float, y1: Float, x2: Float, y2: Float, ink: Ink) {
        body.append("<line x1=\"").append(num(X(x1))).append("\" y1=\"").append(num(Y(y1)))
            .append("\" x2=\"").append(num(X(x2))).append("\" y2=\"").append(num(Y(y2)))
            .append("\" stroke=\"").append(hex(ink.color))
            .append("\" stroke-width=\"").append(num(ink.width * scaleFactor))
            .append("\" stroke-linecap=\"round\"/>\n")
    }

    override fun drawPath(ops: List<PathOp>, ink: Ink) {
        if (ops.isEmpty()) return
        body.append("<path d=\"").append(ops.joinToString(" ") { it.toSvg() })
            .append("\" fill=\"").append(if (ink.filled) hex(ink.color) else "none")
            .append("\" stroke=\"").append(if (ink.filled) "none" else hex(ink.color))
            .append("\" stroke-width=\"").append(num(ink.width * scaleFactor))
            .append("\" stroke-linecap=\"round\" stroke-linejoin=\"round\"/>\n")
    }

    override fun drawRoundRect(
        left: Float, top: Float, right: Float, bottom: Float,
        rx: Float, ry: Float, ink: Ink
    ) {
        body.append("<rect x=\"").append(num(X(left))).append("\" y=\"").append(num(Y(top)))
            .append("\" width=\"").append(num((right - left) * scaleFactor))
            .append("\" height=\"").append(num((bottom - top) * scaleFactor))
            .append("\" rx=\"").append(num(rx * scaleFactor))
            .append("\" ry=\"").append(num(ry * scaleFactor))
            .append("\" fill=\"none\" stroke=\"").append(hex(ink.color))
            .append("\" stroke-width=\"").append(num(ink.width * scaleFactor))
            .append("\"/>\n")
    }

    override fun drawCircle(x: Float, y: Float, r: Float, ink: Ink) {
        body.append("<circle cx=\"").append(num(X(x))).append("\" cy=\"").append(num(Y(y)))
            .append("\" r=\"").append(num(r * scaleFactor))
            .append("\" fill=\"").append(if (ink.filled) hex(ink.color) else "none")
            .append("\" stroke=\"").append(if (ink.filled) "none" else hex(ink.color))
            .append("\" stroke-width=\"").append(num(ink.width * scaleFactor))
            .append("\"/>\n")
    }

    /** 组装成完整 SVG 文档 */
    fun build(width: Float, height: Float): String {
        val w = num(width); val h = num(height)
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

    private fun num(v: Float): String = String.format(Locale.US, "%.2f", v)

    private fun hex(color: Int): String {
        val a = (color ushr 24) and 0xFF
        return if (a == 0xFF) String.format(Locale.US, "#%06X", color and 0xFFFFFF)
        else String.format(Locale.US, "#%02X%06X", a, color and 0xFFFFFF)
    }

    private fun escapeXml(s: String): String = buildString {
        for (c in s) when (c) {
            '&' -> append("&amp;"); '<' -> append("&lt;"); '>' -> append("&gt;")
            '"' -> append("&quot;"); '\'' -> append("&apos;"); else -> append(c)
        }
    }
}
