package com.formulastudio.core.render

/** 文字样式（与平台无关） */
data class TextStyle(
    val size: Float,
    val color: Int,
    val italic: Boolean = false,
    val bold: Boolean = false,
    val monospace: Boolean = false
)

/**
 * 渲染后端抽象。
 *
 * 内核（排版引擎）只调用这些方法，不关心目标是 Canvas / SVG / PDF 还是 Word。
 * 各后端自己决定怎么画：Canvas 用 Paint，SVG 输出 <text>/<path>，PDF 用 PdfDocument…
 */
interface RenderTarget {
    fun drawText(text: String, x: Float, y: Float, style: TextStyle)
    fun drawLine(x1: Float, y1: Float, x2: Float, y2: Float, width: Float, color: Int)
    fun drawPath(ops: List<PathOp>, strokeWidth: Float, color: Int, filled: Boolean = false)
    fun drawRoundRect(
        left: Float, top: Float, right: Float, bottom: Float,
        radius: Float, strokeWidth: Float, color: Int
    )
}
