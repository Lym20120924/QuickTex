package com.formulastudio.core.render

/**
 * 平台无关的文字样式。
 * 内核排版只描述“要画什么”，不关心目标是 Canvas / SVG / PDF。
 */
data class TextStyle(
    val size: Float,
    val color: Int,
    val italic: Boolean = false,
    val family: String = "KaTeX_Main,KaTeX_AMS,serif"
)

/** 平台无关的描边 / 填充描述 */
data class Ink(
    val color: Int,
    val width: Float,
    val filled: Boolean = false,
    val dashed: Boolean = false
)

/**
 * 渲染后端抽象。
 *
 * 内核（排版引擎）只调用这些方法，不关心目标是 Canvas / SVG / PDF 还是 Word。
 * 各后端自己决定怎么画：Canvas 用 Paint，SVG 输出 <text>/<path>，PDF 用 PdfDocument…
 */
interface RenderTarget {
    fun save()
    fun restore()
    fun translate(dx: Float, dy: Float)
    fun scale(sx: Float, sy: Float)
    fun drawText(text: String, x: Float, y: Float, style: TextStyle)
    fun drawLine(x1: Float, y1: Float, x2: Float, y2: Float, ink: Ink)
    fun drawPath(ops: List<PathOp>, ink: Ink)
    fun drawRoundRect(
        left: Float, top: Float, right: Float, bottom: Float,
        rx: Float, ry: Float, ink: Ink
    )
    fun drawCircle(x: Float, y: Float, r: Float, ink: Ink)
}
