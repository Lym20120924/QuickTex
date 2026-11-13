package com.formulastudio.app.editor

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path

/**
 * 路径命令。
 *
 * 让同一套绘制逻辑既能画到 Android [Canvas]（屏幕显示 / PNG），
 * 也能输出成 SVG（矢量导出），避免为导出再实现一遍排版。
 */
sealed class PathOp {
    data class MoveTo(val x: Float, val y: Float) : PathOp()
    data class LineTo(val x: Float, val y: Float) : PathOp()
    data class CubicTo(
        val x1: Float, val y1: Float,
        val x2: Float, val y2: Float,
        val x3: Float, val y3: Float
    ) : PathOp()
    data class QuadTo(
        val x1: Float, val y1: Float,
        val x2: Float, val y2: Float
    ) : PathOp()

    fun applyTo(p: Path) {
        when (this) {
            is MoveTo -> p.moveTo(x, y)
            is LineTo -> p.lineTo(x, y)
            is CubicTo -> p.cubicTo(x1, y1, x2, y2, x3, y3)
            is QuadTo -> p.quadTo(x1, y1, x2, y2)
        }
    }

    /** SVG path 的 d 属性片段 */
    fun toSvg(): String = when (this) {
        is MoveTo -> "M$x $y"
        is LineTo -> "L$x $y"
        is CubicTo -> "C$x1 $y1 $x2 $y2 $x3 $y3"
        is QuadTo -> "Q$x1 $y1 $x2 $y2"
    }
}

/** 绘制抽象层：屏幕 Canvas 与 SVG 输出共用 */
interface DrawOps {
    fun save()
    fun restore()
    fun translate(dx: Float, dy: Float)
    fun scale(sx: Float, sy: Float)
    fun drawText(text: String, x: Float, y: Float, paint: Paint)
    fun drawLine(x1: Float, y1: Float, x2: Float, y2: Float, paint: Paint)
    fun drawPath(ops: List<PathOp>, paint: Paint)
    fun drawRoundRect(
        left: Float, top: Float, right: Float, bottom: Float,
        rx: Float, ry: Float, paint: Paint
    )
}

/** Android [Canvas] 实现（屏幕显示 / PNG 导出用） */
class CanvasOps(private val canvas: Canvas) : DrawOps {
    override fun save() { canvas.save() }
    override fun restore() { canvas.restore() }
    override fun translate(dx: Float, dy: Float) { canvas.translate(dx, dy) }
    override fun scale(sx: Float, sy: Float) { canvas.scale(sx, sy) }
    override fun drawText(text: String, x: Float, y: Float, paint: Paint) {
        canvas.drawText(text, x, y, paint)
    }
    override fun drawLine(x1: Float, y1: Float, x2: Float, y2: Float, paint: Paint) {
        canvas.drawLine(x1, y1, x2, y2, paint)
    }
    override fun drawPath(ops: List<PathOp>, paint: Paint) {
        val p = Path()
        ops.forEach { it.applyTo(p) }
        canvas.drawPath(p, paint)
    }
    override fun drawRoundRect(
        left: Float, top: Float, right: Float, bottom: Float,
        rx: Float, ry: Float, paint: Paint
    ) {
        canvas.drawRoundRect(left, top, right, bottom, rx, ry, paint)
    }
}
