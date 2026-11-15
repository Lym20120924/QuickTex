package com.formulastudio.core.render

/**
 * 与平台无关的路径命令。
 * 内核只产出这些“绘图意图”，由具体后端（Canvas / SVG / PDF / docx）去落地，
 * 这样同一份排版结果可以渲染到任意目标。
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

    /** SVG path 的 d 属性片段 */
    fun toSvg(): String = when (this) {
        is MoveTo -> "M$x $y"
        is LineTo -> "L$x $y"
        is CubicTo -> "C$x1 $y1 $x2 $y2 $x3 $y3"
        is QuadTo -> "Q$x1 $y1 $x2 $y2"
    }
}
