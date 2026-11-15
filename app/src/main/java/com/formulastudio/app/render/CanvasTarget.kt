package com.formulastudio.app.render

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import com.formulastudio.core.render.Ink
import com.formulastudio.core.render.PathOp
import com.formulastudio.core.render.RenderTarget
import com.formulastudio.core.render.TextStyle

/** Android [Canvas] 后端：屏幕显示 / PNG 导出用 */
class CanvasTarget(private val canvas: Canvas) : RenderTarget {

    private fun paint(style: TextStyle) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = style.size
        color = style.color
        typeface = typefaceFor(style)
    }

    private fun paint(ink: Ink) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ink.color
        strokeWidth = ink.width
        style = if (ink.filled) Paint.Style.FILL else Paint.Style.STROKE
        if (ink.dashed) pathEffect = DashPathEffect(floatArrayOf(6f, 4f), 0f)
    }

    override fun save() { canvas.save() }
    override fun restore() { canvas.restore() }
    override fun translate(dx: Float, dy: Float) { canvas.translate(dx, dy) }
    override fun scale(sx: Float, sy: Float) { canvas.scale(sx, sy) }

    override fun drawText(text: String, x: Float, y: Float, style: TextStyle) {
        canvas.drawText(text, x, y, paint(style))
    }

    override fun drawLine(x1: Float, y1: Float, x2: Float, y2: Float, ink: Ink) {
        canvas.drawLine(x1, y1, x2, y2, paint(ink))
    }

    override fun drawPath(ops: List<PathOp>, ink: Ink) {
        if (ops.isEmpty()) return
        val p = Path()
        ops.forEach {
            when (it) {
                is PathOp.MoveTo -> p.moveTo(it.x, it.y)
                is PathOp.LineTo -> p.lineTo(it.x, it.y)
                is PathOp.CubicTo -> p.cubicTo(it.x1, it.y1, it.x2, it.y2, it.x3, it.y3)
                is PathOp.QuadTo -> p.quadTo(it.x1, it.y1, it.x2, it.y2)
            }
        }
        canvas.drawPath(p, paint(ink))
    }

    override fun drawRoundRect(
        left: Float, top: Float, right: Float, bottom: Float,
        rx: Float, ry: Float, ink: Ink
    ) {
        canvas.drawRoundRect(left, top, right, bottom, rx, ry, paint(ink))
    }

    override fun drawCircle(x: Float, y: Float, r: Float, ink: Ink) {
        canvas.drawCircle(x, y, r, paint(ink))
    }
}
