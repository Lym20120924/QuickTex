package com.formulastudio.app.editor

import com.formulastudio.core.render.Ink
import com.formulastudio.core.render.PathOp
import com.formulastudio.core.render.RenderTarget
import com.formulastudio.core.render.TextStyle
import kotlin.math.max

/**
 * 公式绘制器（阶段 4.4b）。
 *
 * 把 [FormulaLayout] 算出的盒树画到任意 [RenderTarget]。
 * 只依赖排版结果 + 度量 + 颜色，不碰任何平台 API
 * → 屏幕（Canvas）、导出（SVG / PDF）可以共用同一套绘制逻辑。
 */
class FormulaRenderer(
    private val layout: FormulaLayout,
    /** 公式“墨迹”颜色（跟随主题） */
    private val inkColor: Int
) {

    /** 占位框（空容器虚线框）颜色 */
    private val placeholderColor: Int = 0xFF9E9E9E.toInt()

    /** Group → 绘制原点（供命中测试 / 光标定位反查） */
    val groupOrigins = HashMap<MathNode.Group, Pair<Float, Float>>()

    private val metrics get() = layout.metrics
    private val minStroke get() = metrics.minStrokeWidth

    private fun style(fontId: String, size: Float, align: String = "left") =
        TextStyle(size = size, color = inkColor, fontId = fontId, align = align)

    private fun ink(width: Float, roundCap: Boolean = false, roundJoin: Boolean = false) =
        Ink(color = inkColor, width = width, roundCap = roundCap, roundJoin = roundJoin)

    /** 入口：多行公式逐行绘制，单行直接画 */
    fun draw(target: RenderTarget, root: MathNode.Group, box: LayoutBox) {
        groupOrigins.clear()
        val lines = layout.multilineRowNodes[root]
        val lineBoxes = layout.multilineLines[root]
        if (lines != null && lineBoxes != null) {
            val lineGap = 0.4f * box.sz
            var yOff = 0f
            for ((i, lg) in lines.withIndex()) {
                val tmp = MathNode.Group().apply { nodes.addAll(lg) }
                drawNode(target, tmp, lineBoxes[i], 0f, yOff)
                yOff += lineBoxes[i].h + lineGap
            }
            return
        }
        drawNode(target, root, box, 0f, 0f)
    }

    private fun drawNode(target: RenderTarget, n: MathNode, box: LayoutBox, x: Float, y: Float) {
        // 先画各结构节点自身的装饰
        when (n) {
            is MathNode.Fraction -> {
                val barY = y + (box.parts.firstOrNull()?.box?.h ?: 0f)
                val gap = 0.14f * box.sz
                val barH = max(minStroke * 1.3f, 0.07f * box.sz)
                target.drawLine(
                    x, barY + gap + barH / 2f, x + box.w, barY + gap + barH / 2f, ink(barH)
                )
            }
            is MathNode.Sqrt -> {
                val d = layout.sqrtInfo[n] ?: return
                target.drawText("√", x, y + d.radicalSize * 0.82f, style("main", d.radicalSize))
                // 横线必须贴着“√”字符的顶部，否则看起来是分离的两笔。
                // 字符顶部 = 基线 - ascent，用同一套度量算出来。
                val sqrtTop = y + d.radicalSize * 0.82f -
                    metrics.ascent(d.radicalSize, style("main", d.radicalSize))
                target.drawLine(
                    x + d.radicalW + 0.02f * box.sz, sqrtTop,
                    x + d.radicalW + 0.02f * box.sz + d.contentW, sqrtTop,
                    ink(max(minStroke * 1.3f, 0.07f * box.sz))
                )
            }
            is MathNode.Op -> {
                val info = layout.opInfo[n] ?: return
                target.drawText(
                    info.sym, x + (box.w - info.w) / 2f, y + info.baseline, style("op", info.size)
                )
            }
            is MathNode.Paren -> {
                val info = layout.parenInfo[n] ?: return
                drawBracket(target, info.left, x, y, info.h)
                drawBracket(target, info.right, x + box.w - bracketWidth(info.h, minStroke), y, info.h)
            }
            is MathNode.Matrix -> {
                val info = layout.matrixInfo[n] ?: return
                if (info.left.isNotEmpty()) {
                    drawBracket(target, info.left, x, y, info.h)
                    drawBracket(target, info.right, x + box.w - bracketWidth(info.h, minStroke), y, info.h)
                }
            }
            is MathNode.Cases -> {
                val info = layout.casesInfo[n] ?: return
                drawBracket(target, "{", x, y, info.h)
            }
            is MathNode.Accent -> {
                val info = layout.accentInfo[n] ?: return
                val cx = x + box.w / 2f
                // 下移避免符号溢出 box 顶部
                val ay = y + info.accentH * 0.92f
                when (info.accent) {
                    "vec" -> {
                        // 向量：细横线 + 右箭头尖，宽度 = base 全宽
                        val lx = x + 0.05f * box.sz
                        val rx = x + box.w - 0.05f * box.sz
                        target.drawLine(lx, ay, rx, ay, ink(max(minStroke, 0.035f * box.sz)))
                        target.drawText("▶", rx - 0.04f * box.sz, ay, style("main", 0.45f * box.sz))
                    }
                    "overrightarrow" -> {
                        val lx = x + 0.05f * box.sz
                        val rx = x + box.w - 0.05f * box.sz
                        target.drawLine(lx, ay, rx, ay, ink(max(minStroke, 0.035f * box.sz)))
                        target.drawText("▶", rx - 0.04f * box.sz, ay, style("main", 0.5f * box.sz))
                    }
                    "hat" -> {
                        target.drawText("^", cx, ay, style("main", 0.68f * box.sz))
                    }
                    "bar", "overline" -> {
                        target.drawLine(
                            x + 0.02f * box.sz, ay, x + box.w - 0.02f * box.sz, ay,
                            ink(max(minStroke * 1.2f, 0.06f * box.sz))
                        )
                    }
                    "dot" -> {
                        target.drawText("·", cx, ay, style("main", 0.65f * box.sz))
                    }
                    "widetilde", "widehat" -> {
                        target.drawText("~", cx, ay, style("main", 0.60f * box.sz))
                    }
                    else -> {
                        target.drawText(info.accent.take(1), cx, ay, style("main", 0.65f * box.sz))
                    }
                }
            }
            else -> {}
        }

        // 递归画子节点
        for (p in box.parts) {
            drawNode(target, p.node, p.box, x + p.dx, y + p.dy)
        }

        // 文本/符号/占位框
        when (n) {
            is MathNode.Text -> {
                // 分段绘制：中文宋体、函数名正体、变量斜体
                var cx = x
                for (r in layout.textRuns(n.text, box.sz)) {
                    target.drawText(r.text, cx, y + box.baseline, style(r.fontId, box.sz))
                    cx += r.width
                }
            }
            is MathNode.Cmd -> {
                val disp = CMD_DISPLAY[n.latex] ?: n.latex.trimStart('\\')
                if (n.latex in OP_LATEX) {
                    target.drawText(disp, x, y + box.baseline, style("op", box.sz * 1.2f))
                } else {
                    target.drawText(disp, x, y + box.baseline, style("main", box.sz))
                }
            }
            is MathNode.Raw -> {
                target.drawText(n.latex, x, y + box.baseline, style("mono", box.sz * 0.85f))
            }
            is MathNode.Func -> {
                target.drawText(n.name, x, y + box.baseline, style("main", box.sz * 0.95f))
            }
            is MathNode.Chem -> {
                target.drawText(n.text, x, y + box.baseline, style("main", box.sz * 0.92f))
            }
            is MathNode.Group -> {
                groupOrigins[n] = x to y
                if (n.nodes.isEmpty()) {
                    // 占位框
                    target.drawRoundRect(
                        x, y + box.baseline - metrics.ascent(box.sz, style("main", box.sz)),
                        x + box.w, y + box.baseline + metrics.descent(box.sz, style("main", box.sz)),
                        3f * minStroke, 3f * minStroke,
                        Ink(color = placeholderColor, width = 1.3f * minStroke, dashed = true)
                    )
                }
            }
            else -> {}
        }
    }

    /** 手绘细括号，替代字体放大导致的粗括号 */
    private fun drawBracket(target: RenderTarget, ch: String, x: Float, y: Float, h: Float) {
        if (ch == "." || ch.isEmpty()) return
        val w = bracketWidth(h, minStroke)
        val bracketInk = ink(1.7f * minStroke, roundCap = true, roundJoin = true)
        when (ch) {
            "(" -> target.drawPath(
                listOf(
                    PathOp.MoveTo(x + w, y),
                    PathOp.CubicTo(x, y + h * 0.25f, x, y + h * 0.75f, x + w, y + h)
                ), bracketInk
            )
            ")" -> target.drawPath(
                listOf(
                    PathOp.MoveTo(x, y),
                    PathOp.CubicTo(x + w, y + h * 0.25f, x + w, y + h * 0.75f, x, y + h)
                ), bracketInk
            )
            "[" -> {
                target.drawLine(x, y, x, y + h, bracketInk)
                target.drawLine(x, y, x + w, y, bracketInk)
                target.drawLine(x, y + h, x + w, y + h, bracketInk)
            }
            "]" -> {
                target.drawLine(x + w, y, x + w, y + h, bracketInk)
                target.drawLine(x, y, x + w, y, bracketInk)
                target.drawLine(x, y + h, x + w, y + h, bracketInk)
            }
            "|" -> target.drawLine(x + w / 2f, y, x + w / 2f, y + h, bracketInk)
            "\\{", "{" -> target.drawPath(
                listOf(
                    PathOp.MoveTo(x + w * 0.85f, y),
                    PathOp.QuadTo(x, y + h * 0.14f, x, y + h * 0.42f),
                    PathOp.QuadTo(x, y + h * 0.5f, x + w * 0.26f, y + h * 0.5f),
                    PathOp.QuadTo(x, y + h * 0.5f, x, y + h * 0.58f),
                    PathOp.QuadTo(x, y + h * 0.86f, x + w * 0.85f, y + h)
                ), bracketInk
            )
            "\\}", "}" -> target.drawPath(
                listOf(
                    PathOp.MoveTo(x + w * 0.15f, y),
                    PathOp.QuadTo(x + w, y + h * 0.14f, x + w, y + h * 0.42f),
                    PathOp.QuadTo(x + w, y + h * 0.5f, x + w * 0.74f, y + h * 0.5f),
                    PathOp.QuadTo(x + w, y + h * 0.5f, x + w, y + h * 0.58f),
                    PathOp.QuadTo(x + w, y + h * 0.86f, x + w * 0.15f, y + h)
                ), bracketInk
            )
            else -> {
                val sz = h * 0.85f
                val desc = metrics.descent(sz, style("main", sz))
                target.drawText(ch, x, y + h - desc - (h * 0.05f), style("main", sz))
            }
        }
    }
}
