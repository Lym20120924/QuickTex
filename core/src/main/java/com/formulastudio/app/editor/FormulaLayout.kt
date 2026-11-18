package com.formulastudio.app.editor

import com.formulastudio.core.render.TextMeasurer
import com.formulastudio.core.render.TextStyle
import kotlin.math.max
import kotlin.math.min

/**
 * 布局度量（阶段 4.4a）。
 *
 * 在 [TextMeasurer] 之外只补一样东西：**最小描边宽度**。
 * 分数线、根号横线这类笔画需要一个「再细也不能低于多少」的下限，
 * Android 端这个值来自屏幕 density，所以抽象成接口属性，
 * 布局算法就能彻底离开 Android。
 */
interface LayoutMetrics : TextMeasurer {
    val minStrokeWidth: Float
}

/** 版面盒：宽 / 高 / 基线 / 字号，外加子部件列表 */
class LayoutBox(
    var w: Float,
    var h: Float,
    var baseline: Float,
    val sz: Float
) {
    val parts = ArrayList<Part>()
}

/** 子部件：节点 + 在父盒中的偏移 + 自身盒 */
class Part(val node: MathNode, val dx: Float, var dy: Float, val box: LayoutBox)

/** 文本段：文字 + 字体标识 + 宽度（绘制时按 fontId 选回平台字体） */
class LayoutRun(val text: String, val fontId: String, val width: Float)

// ---------- 绘制辅助信息（core 只给数据，怎么画交给平台） ----------

class SqrtInfo(
    val radicalSize: Float, val radicalW: Float, val contentW: Float,
    val gapAbove: Float, val overTh: Float, val contentX: Float
)

class OpInfo(val sym: String, val size: Float, val w: Float, val baseline: Float)

class ParenInfo(val left: String, val right: String, val h: Float)

class MatrixInfo(val left: String, val right: String, val h: Float)

class CasesInfo(val h: Float)

class AccentInfo(val accent: String, val accentH: Float, val baseW: Float)

/** 需要排成正体（非斜体）的函数名与常量 */
internal val FUNCTION_NAMES = setOf(
    "sin", "cos", "tan", "cot", "sec", "csc", "log", "ln", "lg", "exp",
    "lim", "max", "min", "inf", "sup", "det", "gcd", "arg", "argmin", "argmax",
    "sinh", "cosh", "tanh", "arcsin", "arccos", "arctan", "tangent",
    "e", "d"
)

/** 运算符（大字号） */
internal val OP_LATEX = setOf(
    "\\sum", "\\prod", "\\coprod", "\\int", "\\iint", "\\iiint", "\\iiiint",
    "\\oint", "\\oiint", "\\oiiint", "\\bigcup", "\\bigcap", "\\bigoplus",
    "\\bigotimes", "\\bigodot"
)

/** 运算符但用小字号（lim/max/min 等函数名，正常大小 + 上下方标签） */
internal val SMALL_OP = setOf(
    "\\lim", "\\max", "\\min", "\\inf", "\\sup", "\\argmax", "\\argmin"
)

/** 手绘括号宽度（细线，替代字体放大导致的粗括号） */
fun bracketWidth(h: Float, minStrokeWidth: Float): Float =
    (h * 0.10f).coerceAtLeast(4f * minStrokeWidth)

/**
 * 公式排版引擎（阶段 4.4a：布局几何从 Android 搬进 core）。
 *
 * 输入一棵 [MathNode] 结构树 + 基准字号，输出一棵 [LayoutBox] 盒树。
 * 只用 [LayoutMetrics] 做文字度量，不碰任何平台 API → 可单元测试、可复用。
 */
class FormulaLayout(val metrics: LayoutMetrics) {

    /** 多行排版缓存：Group → 每行的 LayoutBox 列表 */
    val multilineLines = HashMap<MathNode.Group, List<LayoutBox>>()

    /** NewLine 分隔出的行节点列表缓存 */
    val multilineRowNodes = HashMap<MathNode.Group, List<List<MathNode>>>()

    /** Group → 盒（供命中测试 / 绘制反查） */
    val groupBoxes = HashMap<MathNode.Group, LayoutBox>()

    // 绘制辅助信息
    val sqrtInfo = HashMap<MathNode.Sqrt, SqrtInfo>()
    val opInfo = HashMap<MathNode.Op, OpInfo>()
    val parenInfo = HashMap<MathNode.Paren, ParenInfo>()
    val matrixInfo = HashMap<MathNode.Matrix, MatrixInfo>()
    val casesInfo = HashMap<MathNode.Cases, CasesInfo>()
    val accentInfo = HashMap<MathNode.Accent, AccentInfo>()

    /** 度量只关心字号与字体，颜色传 0（不影响宽度 / 行高） */
    private fun style(fontId: String, sz: Float) = TextStyle(size = sz, color = 0, fontId = fontId)

    /** 入口：含换行则按多行排版，否则单行 */
    fun layout(root: MathNode.Group, sz: Float): LayoutBox {
        groupBoxes.clear()
        multilineLines.clear()
        multilineRowNodes.clear()
        sqrtInfo.clear()
        opInfo.clear()
        parenInfo.clear()
        matrixInfo.clear()
        casesInfo.clear()
        accentInfo.clear()
        return if (root.nodes.any { it is MathNode.NewLine }) {
            measureMultiline(root, sz)
        } else {
            measureGroup(root, sz)
        }
    }

    fun measureMultiline(g: MathNode.Group, sz: Float): LayoutBox {
        val box = LayoutBox(0f, 0f, 0f, sz)
        groupBoxes[g] = box
        // 按 NewLine 分割行
        val rows = ArrayList<List<MathNode>>()
        var cur = ArrayList<MathNode>()
        for (n in g.nodes) {
            if (n is MathNode.NewLine) {
                rows.add(cur); cur = ArrayList()
            } else {
                cur.add(n)
            }
        }
        if (cur.isNotEmpty() || rows.isEmpty()) rows.add(cur)
        multilineRowNodes[g] = rows
        val lineGap = 0.4f * sz
        var totalH = 0f
        var maxW = 0f
        val lineBoxes = ArrayList<LayoutBox>()
        for ((i, nodes) in rows.withIndex()) {
            if (i > 0) totalH += lineGap
            val tmp = MathNode.Group().apply { this.nodes.addAll(nodes) }
            val lb = measureGroup(tmp, sz)
            box.parts.add(Part(tmp, 0f, totalH, lb))
            totalH += lb.h
            maxW = max(maxW, lb.w)
            lineBoxes.add(lb)
        }
        multilineLines[g] = lineBoxes
        box.w = maxW
        box.h = totalH
        box.baseline = lineBoxes.firstOrNull()?.baseline ?: ascent(sz)
        return box
    }

    fun measureGroup(g: MathNode.Group, sz: Float): LayoutBox {
        val box = LayoutBox(0f, 0f, 0f, sz)
        groupBoxes[g] = box
        if (g.nodes.isEmpty()) {
            val minW = 1.15f * sz
            box.w = minW
            box.h = lineHeight(sz)
            box.baseline = ascent(sz)
            return box
        }
        // 先量所有子节点，取最大 baseline
        val measured = ArrayList<Pair<MathNode, LayoutBox>>()
        var maxBaseline = 0f
        for (n in g.nodes) {
            val b = measureNode(n, sz)
            measured.add(n to b)
            maxBaseline = max(maxBaseline, b.baseline)
        }
        var x = 0f
        var minY = Float.MAX_VALUE
        var maxY = Float.MIN_VALUE
        for ((n, b) in measured) {
            val dy = maxBaseline - b.baseline
            box.parts.add(Part(n, x, dy, b))
            x += b.w
            if (measured.indexOf(n to b) < measured.size - 1) x += 0.06f * sz
            minY = min(minY, dy)
            maxY = max(maxY, dy + b.h)
        }
        box.w = x
        box.h = maxY - minY
        box.baseline = maxBaseline - minY
        // 调整子节点位置以对齐顶部
        for (p in box.parts) {
            p.dy = p.dy - minY
        }
        return box
    }

    fun measureNode(n: MathNode, sz: Float): LayoutBox {
        return when (n) {
            is MathNode.NewLine -> LayoutBox(0f, 0f, 0f, sz) // 换行符：占位 0 宽度
            is MathNode.Group -> measureGroup(n, sz)
            is MathNode.Text -> {
                // 分段测量：中文用宋体、函数名正体、单字母斜体
                var tw = 0f
                for (r in textRuns(n.text, sz)) tw += r.width
                LayoutBox(tw, lineHeight(sz), ascent(sz), sz)
            }
            is MathNode.Cmd -> {
                val isOp = n.latex in OP_LATEX
                val disp = CMD_DISPLAY[n.latex] ?: n.latex.trimStart('\\')
                val w = metrics.measure(disp, if (isOp) sz * 1.2f else sz, style(if (isOp) "op" else "main", if (isOp) sz * 1.2f else sz))
                LayoutBox(w, lineHeight(sz), ascent(sz), sz)
            }
            is MathNode.Raw -> {
                val w = metrics.measure(n.latex, sz * 0.85f, style("mono", sz * 0.85f))
                LayoutBox(w, lineHeight(sz), ascent(sz), sz)
            }
            is MathNode.Fraction -> {
                val num = measureGroup(n.num, sz)
                val den = measureGroup(n.den, sz)
                val gap = 0.14f * sz
                val barH = max(metrics.minStrokeWidth * 1.3f, 0.07f * sz)
                val w = max(num.w, den.w) + 0.3f * sz
                val numY = 0f
                val barY = num.h + gap
                val denY = barY + barH + gap
                val h = denY + den.h
                // 基线取分数线中心（数学轴），与相邻文字对齐，避免分数整体上移
                val box = LayoutBox(w, h, barY + barH / 2f, sz)
                box.parts.add(Part(n.num, (w - num.w) / 2f, numY, num))
                box.parts.add(Part(n.den, (w - den.w) / 2f, denY, den))
                box
            }
            is MathNode.Sqrt -> {
                val rad = measureGroup(n.radicand, sz)
                val deg = n.degree?.let { measureGroup(it, 0.62f * sz) }
                val overTh = max(metrics.minStrokeWidth * 1.3f, 0.07f * sz)
                val gapAbove = 0.3f * sz
                val radicalSize = (rad.h + gapAbove + overTh) * 1.05f
                val radicalW = metrics.measure("√", radicalSize, style("main", radicalSize)) * 0.9f
                val contentX = radicalW + 0.06f * sz
                val radY = gapAbove + overTh
                val w = contentX + rad.w
                val h = radY + rad.h
                val box = LayoutBox(w, h, radY + rad.baseline, sz)
                box.parts.add(Part(n.radicand, contentX, radY, rad))
                if (deg != null) {
                    box.parts.add(Part(n.degree!!, 0f, -0.15f * deg.h, deg))
                    // 若 degree 顶出边界，扩展盒高度
                    if (-0.15f * deg.h < 0) {
                        val extra = -0.15f * deg.h
                        box.h = h - extra
                        box.baseline = (radY + rad.baseline) - extra
                    }
                }
                sqrtInfo[n] = SqrtInfo(radicalSize, radicalW, rad.w, gapAbove, overTh, contentX)
                box
            }
            is MathNode.Script -> {
                val base = measureNode(n.base, sz)
                val sup = n.sup?.let { measureGroup(it, 0.72f * sz) }
                val sub = n.sub?.let { measureGroup(it, 0.72f * sz) }
                val gap = 0.08f * sz
                val x = base.w + gap
                var w = x
                var h = base.h
                var minY = 0f
                val box = LayoutBox(0f, 0f, base.baseline, sz)
                box.parts.add(Part(n.base, 0f, 0f, base))
                if (sup != null) {
                    // 上标放在基线之上，不覆盖基元
                    val dy = base.baseline - sup.h - 0.18f * sz
                    box.parts.add(Part(n.sup!!, x, dy, sup))
                    minY = min(minY, dy)
                    w = max(w, x + sup.w)
                }
                if (sub != null) {
                    // 下标放在基线之下，与上标分开
                    val dy = base.baseline + 0.18f * sz
                    box.parts.add(Part(n.sub!!, x, dy, sub))
                    h = max(h, dy + sub.h)
                    w = max(w, x + sub.w)
                }
                val topShift = min(0f, minY)
                for (p in box.parts) p.dy -= topShift
                box.h = h - topShift
                box.baseline = base.baseline - topShift
                box.w = w
                box
            }
            is MathNode.Op -> {
                val isSmall = n.latex in SMALL_OP
                val symSize = if (isSmall) sz else sz * 1.3f
                val sym = CMD_DISPLAY[n.latex] ?: n.latex.trimStart('\\')
                val symW = metrics.measure(sym, symSize, style("op", symSize))
                val asc = metrics.ascent(sz, style("op", sz))
                val desc = metrics.descent(sz, style("op", sz))
                val symH = desc + asc
                val symBaseline = asc
                val under = n.under?.let { measureGroup(it, 0.72f * sz) }
                val over = n.over?.let { measureGroup(it, 0.72f * sz) }
                var w = symW
                var minY = 0f
                var maxY = symH
                val box = LayoutBox(0f, 0f, symBaseline, sz)
                if (over != null) {
                    val dy = -over.h - 0.1f * sz
                    box.parts.add(Part(n.over!!, (max(w, over.w) - over.w) / 2f, dy, over))
                    minY = min(minY, dy)
                    w = max(w, over.w)
                }
                if (under != null) {
                    val dy = symH + 0.1f * sz
                    box.parts.add(Part(n.under!!, (max(w, under.w) - under.w) / 2f, dy, under))
                    maxY = max(maxY, dy + under.h)
                    w = max(w, under.w)
                }
                val topShift = min(0f, minY)
                for (p in box.parts) p.dy -= topShift
                box.h = maxY - topShift
                box.baseline = symBaseline - topShift
                box.w = w
                opInfo[n] = OpInfo(sym, symSize, symW, symBaseline - topShift)
                box
            }
            is MathNode.Func -> {
                val w = metrics.measure(n.name, sz * 0.95f, style("main", sz * 0.95f))
                LayoutBox(w, lineHeight(sz), ascent(sz), sz)
            }
            is MathNode.Paren -> {
                val inner = measureGroup(n.inner, sz)
                val parenH = inner.h + 0.2f * sz
                val parenW = bracketWidth(parenH, metrics.minStrokeWidth)
                val gap = 0.07f * sz
                val w = (if (n.left == ".") 0f else parenW) +
                    (if (n.right == ".") 0f else parenW) + inner.w + gap
                val dy = (parenH - inner.h) / 2f
                val box = LayoutBox(w, parenH, dy + inner.baseline, sz)
                box.parts.add(
                    Part(n.inner, if (n.left == ".") 0f else parenW + gap * 0.5f, dy, inner)
                )
                parenInfo[n] = ParenInfo(n.left, n.right, parenH)
                box
            }
            is MathNode.Matrix -> {
                val cellSize = 0.88f * sz
                val cells = n.rows.map { row -> row.map { measureGroup(it, cellSize) } }
                val colW = FloatArray(if (n.rows.isEmpty()) 0 else n.rows[0].size) { 0f }
                // 列宽（Float，避免取整截断导致错位）
                for (r in cells.indices) {
                    for (c in cells[r].indices) {
                        colW[c] = max(colW[c], cells[r][c].w)
                    }
                }
                val colGap = 0.42f * sz
                val rowGap = 0.12f * sz
                val (lDelim, rDelim) = when (n.delimiter) {
                    "pmatrix" -> "(" to ")"
                    "bmatrix" -> "[" to "]"
                    "vmatrix" -> "|" to "|"
                    "Bmatrix" -> "{" to "}"
                    else -> "" to ""
                }
                val delimW = if (lDelim.isEmpty()) 0f else 0.75f * sz
                var w = delimW
                var h = 0f
                val box = LayoutBox(0f, 0f, 0f, cellSize)
                var y = 0f
                for (r in cells.indices) {
                    val rowH = (cells[r].maxOfOrNull { it.h } ?: lineHeight(cellSize)) + rowGap
                    var x = delimW
                    for (c in cells[r].indices) {
                        val cell = cells[r][c]
                        box.parts.add(Part(n.rows[r][c], x, y + (rowH - cell.h) / 2f, cell))
                        x += colW[c] + colGap
                    }
                    w = max(w, x - colGap + delimW)
                    y += rowH
                }
                h = if (cells.isEmpty()) 0f else y - rowGap
                box.w = w
                box.h = h
                box.baseline = h / 2f
                matrixInfo[n] = MatrixInfo(lDelim, rDelim, h)
                box
            }
            is MathNode.Cases -> {
                val rows = n.rows.map { measureGroup(it, sz) }
                val rowGap = 0.14f * sz
                var w = 0f
                var h = 0f
                var y = 0f
                val box = LayoutBox(0f, 0f, 0f, sz)
                for (i in rows.indices) {
                    val b = rows[i]
                    box.parts.add(Part(n.rows[i], 0.6f * sz, y, b))
                    w = max(w, b.w)
                    y += b.h + rowGap
                }
                h = y - rowGap
                box.w = w + 0.6f * sz
                box.h = h
                box.baseline = rows.firstOrNull()?.baseline ?: ascent(sz)
                casesInfo[n] = CasesInfo(h)
                box
            }
            is MathNode.Accent -> {
                val base = measureGroup(n.base, sz)
                val accentH = 0.75f * sz // 加大，让 hat/点 等装饰符号字符能完整放下
                val w = base.w + 0.05f * sz
                val box = LayoutBox(w, base.h + accentH, accentH + base.baseline, sz)
                box.parts.add(Part(n.base, 0f, accentH, base))
                accentInfo[n] = AccentInfo(n.accent, accentH, base.w)
                box
            }
            is MathNode.Chem -> {
                val w = metrics.measure(n.text, sz * 0.92f, style("main", sz * 0.92f))
                LayoutBox(w, lineHeight(sz), ascent(sz), sz)
            }
        }
    }

    private fun lineHeight(sz: Float): Float = metrics.lineHeight(sz, style("main", sz))

    private fun ascent(sz: Float): Float = metrics.ascent(sz, style("main", sz))

    // ---------- 文本分段（中文 / 函数名 / 斜体变量） ----------

    private fun isCjk(c: Char): Boolean =
        (c in '\u4E00'..'\u9FFF') || (c in '\u3000'..'\u303F') || (c in '\uFF00'..'\uFFEF')

    /**
     * 把一段文本拆成若干渲染段并分别测量：
     *  - 中文/全角标点 → 宋体（cjk）
     *  - 拉丁字母：函数名（sin/cos/e…）→ 正体；其他（单字母变量或 ab 组合）→ 斜体
     *  - 数字/符号 → 正体
     */
    fun textRuns(text: String, sz: Float): List<LayoutRun> {
        val runs = ArrayList<LayoutRun>()
        var i = 0
        var prevChar: Char? = null // 跨段延续，用于 i 正体规则
        while (i < text.length) {
            val kind = when {
                isCjk(text[i]) -> 0
                text[i].isLetter() -> 1
                else -> 2
            }
            var j = i
            while (j < text.length) {
                val k = when {
                    isCjk(text[j]) -> 0
                    text[j].isLetter() -> 1
                    else -> 2
                }
                if (k != kind) break
                j++
            }
            val seg = text.substring(i, j)
            when (kind) {
                0 -> runs.add(LayoutRun(seg, "cjk", metrics.measure(seg, sz, style("cjk", sz))))
                1 -> runs.addAll(latinRuns(seg, sz, prevChar))
                else -> runs.add(LayoutRun(seg, "main", metrics.measure(seg, sz, style("main", sz))))
            }
            prevChar = seg.lastOrNull()
            i = j
        }
        return runs
    }

    /**
     * 拉丁字母段：从左到右扫描，连续识别所有函数名（sin/cos/e…）→ 正体；
     * 其余字母 → 斜体；'i'/'I' 在前一个字符是数字或 b 时 → 正体（虚数单位）。
     */
    private fun latinRuns(seg: String, sz: Float, prevChar: Char? = null): List<LayoutRun> {
        val out = ArrayList<LayoutRun>()
        val fns = FUNCTION_NAMES.sortedByDescending { it.length }
        var i = 0
        var lastChar: Char? = prevChar // 跨段延续（用于 i 正体规则）
        while (i < seg.length) {
            var matched: String? = null
            for (fn in fns) {
                if (seg.regionMatches(i, fn, 0, fn.length, ignoreCase = true)) {
                    matched = fn; break
                }
            }
            if (matched != null) {
                val head = seg.substring(i, i + matched.length)
                out.add(LayoutRun(head, "main", metrics.measure(head, sz, style("main", sz))))
                i += matched.length
                lastChar = head.lastOrNull()
            } else {
                // 斜体段：吞到下一个函数名前；同时检查 'i' 前是不是数字/b → 正体
                var j = i + 1
                while (j < seg.length) {
                    var isFn = false
                    for (fn in fns) {
                        if (seg.regionMatches(j, fn, 0, fn.length, ignoreCase = true)) {
                            isFn = true; break
                        }
                    }
                    if (isFn) break
                    j++
                }
                // 在 rest 中把 'i'/'I'（前一个字符是数字/b 的）拆出来作为正体
                var k = i
                while (k < j) {
                    val c = seg[k]
                    val isImagUnit = (c == 'i' || c == 'I') && (lastChar?.isDigit() == true
                        || lastChar == 'b' || lastChar == 'B'
                        || lastChar == 'd' || lastChar == 'D')
                    if (isImagUnit) {
                        // 先把 k 之前的斜体段（如果有）输出
                        if (k > i) {
                            val seg1 = seg.substring(i, k)
                            out.add(LayoutRun(seg1, "var", metrics.measure(seg1, sz, style("var", sz))))
                        }
                        // 输出 i 为正体
                        out.add(LayoutRun(c.toString(), "main", metrics.measure(c.toString(), sz, style("main", sz))))
                        i = k + 1
                    }
                    lastChar = c
                    k++
                }
                if (i < j) {
                    val rest = seg.substring(i, j)
                    out.add(LayoutRun(rest, "var", metrics.measure(rest, sz, style("var", sz))))
                    lastChar = rest.lastOrNull()
                    i = j
                }
            }
        }
        return out
    }
}
