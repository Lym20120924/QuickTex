package com.formulastudio.app.editor

import com.formulastudio.core.render.TextStyle
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 命中测试与光标定位（阶段 4.4d）。
 *
 * 把 app 层的 hitTestBox / groupIndexAt / caretX / prefixWidth 搬进 core，
 * 只依赖 [FormulaLayout] 的排版结果 + [LayoutMetrics] 度量，不碰任何平台 API。
 *
 * app 层只负责把手指坐标传进来，把返回的 (group, index, char) 应用到光标模型：
 *  - group：光标所在容器（MathNode.Group）
 *  - index：该容器内第几个节点之前（0..parts.size）
 *  - char：>=0 表示落在文本节点内部，第几个字符之后；-1 表示在节点边界
 */
class FormulaHitTester(private val layout: FormulaLayout) {

    private val metrics get() = layout.metrics

    /** 度量用的平台无关样式（颜色不影响宽度，传 0） */
    private fun style(fontId: String, sz: Float) = TextStyle(size = sz, color = 0, fontId = fontId)

    /** 入口：点击坐标 (px, py) 相对于 box 原点，返回光标三元组；命中失败则回落到按 x 最近边界 */
    fun hitTest(root: MathNode.Group, box: LayoutBox, px: Float, py: Float): Triple<MathNode.Group, Int, Int> {
        val hit = hitTestBox(root, box, px, py)
        return hit ?: groupIndexAt(root, px)
    }

    private fun hitTestBox(
        node: MathNode,
        box: LayoutBox,
        px: Float,
        py: Float
    ): Triple<MathNode.Group, Int, Int>? {
        val slack = 0.5f * box.sz // 行高容差：点得稍偏上下也能命中
        // 距离当前盒太远（比如点到公式外很远的空白）→ 不响应
        if (py < -slack || py > box.h + slack) return null

        var best: Part? = null
        var bestDist = Float.MAX_VALUE
        for (p in box.parts) {
            val dyDist = abs(py - (p.dy + p.box.h / 2f))
            if (dyDist < bestDist) { bestDist = dyDist; best = p }
            // 精确命中：x、y 都在子盒内
            if (px >= p.dx - slack * 0.5f && px <= p.dx + p.box.w + slack * 0.5f &&
                py >= p.dy - slack && py <= p.dy + p.box.h + slack
            ) {
                val sub = hitTestBox(p.node, p.box, px - p.dx, py - p.dy)
                if (sub != null) return sub
            }
        }
        // 没有精确命中：
        if (node is MathNode.Group) {
            // 容器 → 直接按 x 定位（点击容器行内任意高度都行）
            return groupIndexAt(node, px)
        }
        // 结构节点（分数/根号/矩阵…）→ 进入最近的子容器（分子/分母/单元格…）
        if (best != null) {
            // 局部拷贝一份：core 的 Part.node 是跨模块属性，不能直接 smart cast
            val bestNode = best.node
            return hitTestBox(bestNode, best.box, px - best.dx, py - best.dy)
                ?: (if (bestNode is MathNode.Group) groupIndexAt(bestNode, px - best.dx) else null)
        }
        return null
    }

    /**
     * 返回 (容器, 节点下标, 节点内字符偏移)；char>=0 表示落在文本节点内部。
     * 按 x 坐标找“最近边界”：每个 part 起点、文本节点内每个字符边界、part 终点、末尾边界。
     */
    fun groupIndexAt(g: MathNode.Group, px: Float): Triple<MathNode.Group, Int, Int> {
        val parts = layout.groupBoxes[g]?.parts ?: emptyList()
        if (parts.isEmpty()) return Triple(g, 0, -1)
        val sz = layout.groupBoxes[g]?.sz ?: 0f
        val gap = 0.06f * sz
        // “最近边界”算法：计算每个候选插入位置，取与点击 x 最近者
        var bestNode = 0
        var bestChar = -1
        var bestDist = Float.MAX_VALUE
        var x = 0f
        for (i in parts.indices) {
            val p = parts[i]
            val node = g.nodes[i]
            val isText = node is MathNode.Text || node is MathNode.Chem
            // 非文本节点：part 起点候选（光标在该节点之前）
            if (!isText) {
                val d0 = abs(px - x)
                if (d0 < bestDist) { bestDist = d0; bestNode = i; bestChar = -1 }
            }
            // 文本节点内部的每个字符边界（替代 part 起点）
            if (isText) {
                val nodeText = (node as? MathNode.Text)?.text ?: (node as MathNode.Chem).text
                var cx = x
                var cnt = 0 // 前 cnt 个字符已过的候选 cursorChar
                for (r in layout.textRuns(nodeText, p.box.sz)) {
                    for (c in r.text) {
                        cnt++
                        cx += metrics.measure(c.toString(), p.box.sz, style(r.fontId, p.box.sz))
                        val d = abs(px - cx)
                        if (d < bestDist) { bestDist = d; bestNode = i; bestChar = cnt }
                    }
                }
            }
            // part 终点候选（光标在该节点之后）
            val d1 = abs(px - (x + p.box.w))
            if (d1 < bestDist) { bestDist = d1; bestNode = i + 1; bestChar = -1 }
            x += p.box.w + gap
        }
        // 末尾边界
        val dend = abs(px - (x - gap))
        if (dend < bestDist) { bestNode = parts.size; bestChar = -1 }
        return Triple(g, bestNode, bestChar)
    }

    /** 文本前 k 个字符的渲染宽度 */
    fun prefixWidth(text: String, sz: Float, k: Int): Float {
        var w = 0f
        var cnt = 0
        for (r in layout.textRuns(text, sz)) {
            val take = min(k - cnt, r.text.length)
            if (take <= 0) break
            w += metrics.measure(r.text.substring(0, take), sz, style(r.fontId, sz))
            cnt += take
        }
        return w
    }

    /**
     * 光标在容器中的 x 坐标（支持文本节点内部）。
     *
     * @param cursorIndex 光标所在 part 下标（0..parts.size）
     * @param cursorChar 文本节点内字符偏移（>=0 表示在文本内，-1 表示节点边界）
     * @param prevText 光标前一节点的文本（仅 Text/Chem 有意义，用于在文本节点内算偏移）
     */
    fun caretX(
        g: MathNode.Group,
        box: LayoutBox,
        cursorIndex: Int,
        cursorChar: Int,
        prevText: String?
    ): Float {
        var x = 0f
        val n = cursorIndex.coerceAtMost(box.parts.size)
        for (i in 0 until n) {
            x += box.parts[i].box.w
            if (i < n - 1) x += 0.06f * box.sz
        }
        if (cursorChar >= 0) {
            val t = prevText
            if (t != null) x += prefixWidth(t, box.sz, cursorChar)
        }
        return x
    }
}
