package com.formulastudio.app.render

import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import com.formulastudio.core.render.RenderTarget

/**
 * PDF 后端：把内核的绘制意图落到 [PdfDocument] 的一页上。
 * 复用 [CanvasTarget]，因此屏幕 / SVG / PDF 三者共用同一份排版结果。
 */
class PdfTarget(
    private val doc: PdfDocument,
    private val page: PdfDocument.Page
) : RenderTarget {

    private val inner = CanvasTarget(page.canvas)

    override fun save() = inner.save()
    override fun restore() = inner.restore()
    override fun translate(dx: Float, dy: Float) = inner.translate(dx, dy)
    override fun scale(sx: Float, sy: Float) = inner.scale(sx, sy)
    override fun drawText(text: String, x: Float, y: Float, style: com.formulastudio.core.render.TextStyle) =
        inner.drawText(text, x, y, style)
    override fun drawLine(x1: Float, y1: Float, x2: Float, y2: Float, ink: com.formulastudio.core.render.Ink) =
        inner.drawLine(x1, y1, x2, y2, ink)
    override fun drawPath(ops: List<com.formulastudio.core.render.PathOp>, ink: com.formulastudio.core.render.Ink) =
        inner.drawPath(ops, ink)
    override fun drawRoundRect(
        left: Float, top: Float, right: Float, bottom: Float,
        rx: Float, ry: Float, ink: com.formulastudio.core.render.Ink
    ) = inner.drawRoundRect(left, top, right, bottom, rx, ry, ink)
    override fun drawCircle(x: Float, y: Float, r: Float, ink: com.formulastudio.core.render.Ink) =
        inner.drawCircle(x, y, r, ink)

    /** 结束本页 */
    fun finishPage() = doc.finishPage(page)
}
