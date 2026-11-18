package com.formulastudio.app.render

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.sp
import com.formulastudio.core.render.Ink
import com.formulastudio.core.render.PathOp
import com.formulastudio.core.render.RenderTarget
import com.formulastudio.core.render.TextStyle as CoreTextStyle

/**
 * Compose [DrawScope] 后端（阶段 4.4b）。
 *
 * 让 core 的 [com.formulastudio.core.render.RenderTarget] 也能落到 Compose 画布上，
 * 这样公式绘制逻辑（FormulaRenderer）在 View / SVG / PDF / Compose 之间完全共用。
 *
 * 注意：core 的坐标单位是 px，而 Compose 的 TextStyle 用 sp → 这里按 density 换算。
 */
class ComposeTarget(
    private val ds: DrawScope,
    private val measurer: TextMeasurer
) : RenderTarget {

    private val density: Float get() = ds.density

    /**
     * core 的尺寸单位是 px，而 Compose 的 `.sp` 会再乘系统字体缩放（fontScale）。
     * Android 的 Paint 不吃 fontScale，所以这里必须把它除掉，
     * 否则同一公式在「设置 → 字体大小」不是默认值时，Compose 后端会整体缩/放。
     */
    private val fontScale: Float get() = ds.fontScale

    override fun save() { ds.drawContext.canvas.save() }
    override fun restore() { ds.drawContext.canvas.restore() }
    override fun translate(dx: Float, dy: Float) { ds.drawContext.canvas.translate(dx, dy) }
    override fun scale(sx: Float, sy: Float) { ds.drawContext.canvas.scale(sx, sy) }

    override fun drawText(text: String, x: Float, y: Float, style: CoreTextStyle) {
        val ts = TextStyle(
            fontFamily = ComposeFonts.familyOf(style.fontId),
            fontSize = TextUnit(style.size / (density * fontScale), TextUnitType.Sp),
            fontStyle = if (style.italic) FontStyle.Italic else FontStyle.Normal,
            // 与 Android Paint 对齐：Paint 默认字距为 0，Compose 未指定时可能走字体内置值
            letterSpacing = 0.sp
        )
        val layout = measurer.measure(text, ts)
        // core 的 y 是基线；Compose 的 topLeft 是文本顶部。
        // getLineBaseline(0) 是基线到文本顶部的距离（正值）→ topLeft.y = y - baseline
        val baseline = layout.multiParagraph.getLineBaseline(0)
        ds.drawText(
            textLayoutResult = layout,
            color = Color(style.color),
            topLeft = Offset(x, y - baseline)
        )
    }

    override fun drawLine(x1: Float, y1: Float, x2: Float, y2: Float, ink: Ink) {
        ds.drawLine(
            color = Color(ink.color),
            start = Offset(x1, y1),
            end = Offset(x2, y2),
            strokeWidth = ink.width,
            cap = if (ink.roundCap) StrokeCap.Round else StrokeCap.Butt,
            pathEffect = if (ink.dashed) PathEffect.dashPathEffect(floatArrayOf(6f, 4f)) else null
        )
    }

    override fun drawPath(ops: List<PathOp>, ink: Ink) {
        if (ops.isEmpty()) return
        val p = Path()
        ops.forEach {
            when (it) {
                is PathOp.MoveTo -> p.moveTo(it.x, it.y)
                is PathOp.LineTo -> p.lineTo(it.x, it.y)
                is PathOp.CubicTo -> p.cubicTo(it.x1, it.y1, it.x2, it.y2, it.x3, it.y3)
                is PathOp.QuadTo -> p.quadraticBezierTo(it.x1, it.y1, it.x2, it.y2)
            }
        }
        ds.drawPath(p, color = Color(ink.color), style = if (ink.filled) Fill else stroke(ink))
    }

    override fun drawRoundRect(
        left: Float, top: Float, right: Float, bottom: Float,
        rx: Float, ry: Float, ink: Ink
    ) {
        ds.drawRoundRect(
            color = Color(ink.color),
            topLeft = Offset(left, top),
            size = Size(right - left, bottom - top),
            cornerRadius = CornerRadius(rx, ry),
            style = if (ink.filled) Fill else stroke(ink)
        )
    }

    override fun drawCircle(x: Float, y: Float, r: Float, ink: Ink) {
        ds.drawCircle(
            color = Color(ink.color),
            radius = r,
            center = Offset(x, y),
            style = if (ink.filled) Fill else stroke(ink)
        )
    }

    private fun stroke(ink: Ink): Stroke = Stroke(
        width = ink.width,
        cap = if (ink.roundCap) StrokeCap.Round else StrokeCap.Butt,
        join = if (ink.roundJoin) StrokeJoin.Round else StrokeJoin.Miter,
        pathEffect = if (ink.dashed) PathEffect.dashPathEffect(floatArrayOf(6f, 4f)) else null
    )
}
