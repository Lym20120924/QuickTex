package com.formulastudio.app.editor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.Selection
import android.text.TextWatcher
import android.util.AttributeSet
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import com.formulastudio.app.R
import com.google.android.material.color.MaterialColors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 所见即所得公式编辑器（自绘 Canvas）。
 *
 * 公式 = 结构树；空容器画成虚线占位框；光标以竖线表示，点击任意位置定位。
 */
class FormulaEditorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    val formula = Formula()
    private val serializer = LatexSerializer()
    private val parser = LatexParser()

    // ---------- 字体与画笔 ----------

    private val density = resources.displayMetrics.density
    /** 公式基准字号（会乘以用户设置的缩放倍率） */
    private var baseSize = 36f * density
    private var fontScale = 1f
    /** 是否显示点阵草稿背景 */
    private var showGrid = false

    /** 点阵背景画笔 */
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x1F999999
    }

    /** 字体族：KaTeX 各套字体通过 res/font 的 font-family XML 做字形级回退，保证全部用标准 LaTeX 字体 */
    private fun family(resId: Int, fallback: Typeface): Typeface = try {
        resources.getFont(resId)
    } catch (e: Throwable) {
        fallback
    }

    private val mainFont: Typeface = family(R.font.katex_math, Typeface.create("serif", Typeface.NORMAL))
    private val varFont: Typeface = family(R.font.katex_math_var, Typeface.create("serif", Typeface.ITALIC))
    private val opFont: Typeface = family(R.font.katex_op, mainFont)

    /**
     * 从当前主题取颜色（支持深色模式）。
     * 必须用 MaterialColors：Material3 里 textColorPrimary 等属性是 ColorStateList，
     * 直接用 TypedValue.data 会拿到 0（透明），导致公式看不见。
     */
    private fun themeColor(attr: Int, fallback: Int): Int {
        val c = MaterialColors.getColor(context, attr, fallback)
        // 万一解析出全透明色，公式会“看不见”，这里兜底
        return if (Color.alpha(c) == 0) fallback else c
    }

    /** 公式“墨迹”颜色：跟随主题，深色模式下自动变浅 */
    private val inkColor: Int = themeColor(android.R.attr.textColorPrimary, Color.BLACK)
    /** 未识别命令（Raw）的提示色 */
    private val rawColor: Int = themeColor(android.R.attr.textColorSecondary, Color.rgb(140, 90, 90))

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = mainFont
        color = inkColor
    }
    private val varPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = varFont
        color = inkColor
    }
    private val opPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = opFont
        color = inkColor
    }
    /** 中文字体：Noto Serif（宋体风格），用于公式中的汉字与全角标点 */
    private val cjkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("serif", Typeface.NORMAL)
        color = inkColor
    }
    /** 手绘括号（细线，替代字体放大导致的粗括号） */
    private val bracketPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = inkColor
        style = Paint.Style.STROKE
        strokeWidth = 1.7f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val caretPaint = Paint().apply {
        color = 0xFF1565C0.toInt()
        strokeWidth = 2.6f * density
        isAntiAlias = true
    }
    private val focusFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x241565C0 // 淡蓝：光标所在容器高亮
        style = Paint.Style.FILL
    }
    private val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(158, 158, 158)
        style = Paint.Style.STROKE
        strokeWidth = 1.3f * density
        pathEffect = DashPathEffect(floatArrayOf(5f * density, 4f * density), 0f)
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = inkColor
        style = Paint.Style.STROKE
        strokeWidth = 1.4f * density
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = themeColor(android.R.attr.textColorHint, Color.rgb(150, 150, 150))
        textSize = 18f * density
        textAlign = Paint.Align.CENTER
    }
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = mainFont
        color = inkColor
    }
    private val rawPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        color = rawColor
    }

    // ---------- 布局结果 ----------

    class LayoutBox(
        var w: Float,
        var h: Float,
        var baseline: Float,
        val sz: Float
    ) {
        val parts = ArrayList<Part>()
    }

    class Part(val node: MathNode, val dx: Float, var dy: Float, val box: LayoutBox)

    private var layoutRoot: LayoutBox? = null
    private val groupBoxes = HashMap<MathNode.Group, LayoutBox>()
    private val groupOrigins = HashMap<MathNode.Group, Pair<Float, Float>>()
    private var contentW = 0f
    private var contentH = 0f

    // ---------- 光标 ----------

    private var blinkOn = true
    private val caretHandler = Handler(Looper.getMainLooper())

    /** 光标是否为"插入模板后自动进入空槽"（用于连续插模板时并列，而不是无限嵌套） */
    private var cursorAutoPlaced = false

    private val blinkRunnable = object : Runnable {
        override fun run() {
            blinkOn = !blinkOn
            invalidate()
            caretHandler.postDelayed(this, 520L)
        }
    }

    // ---------- 撤销/重做 ----------

    private val undoStack = ArrayDeque<String>()
    private val redoStack = ArrayDeque<String>()

    // ---------- 回调 ----------

    private var listener: ((String) -> Unit)? = null

    /** 公式变化回调（编辑器 → 外部） */
    fun setOnFormulaChanged(l: (String) -> Unit) { listener = l }

    // ---------- 触摸 ----------

    private var downX = 0f
    private var downY = 0f
    private var downScrollX = 0
    private var downScrollY = 0
    private var dragging = false

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        // 背景跟随主题
        val tv = android.util.TypedValue()
        context.theme.resolveAttribute(android.R.attr.colorBackground, tv, true)
        setBackgroundColor(tv.data)
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: android.graphics.Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        blinkOn = gainFocus
        if (gainFocus) {
            caretHandler.removeCallbacks(blinkRunnable)
            caretHandler.postDelayed(blinkRunnable, 520L)
        } else {
            caretHandler.removeCallbacks(blinkRunnable)
        }
        invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        caretHandler.removeCallbacks(blinkRunnable)
    }

    // ---------- 布局 ----------

    private fun measureText(paint: Paint, text: String, sz: Float): Float {
        paint.textSize = sz
        return paint.measureText(text)
    }

    private fun lineHeight(sz: Float): Float {
        textPaint.textSize = sz
        val fm = textPaint.fontMetrics
        return fm.descent - fm.ascent
    }

    private fun ascent(sz: Float): Float {
        textPaint.textSize = sz
        return -textPaint.fontMetrics.ascent
    }

    private fun descent(sz: Float): Float {
        textPaint.textSize = sz
        return textPaint.fontMetrics.descent
    }

    // ---------- 文本分段渲染（中文/函数名/斜体变量） ----------

    /** 需要排成正体（非斜体）的函数名与常量 */
    private val FUNCTION_NAMES = setOf(
        "sin", "cos", "tan", "cot", "sec", "csc", "log", "ln", "lg", "exp",
        "lim", "max", "min", "inf", "sup", "det", "gcd", "arg", "argmin", "argmax",
        "sinh", "cosh", "tanh", "arcsin", "arccos", "arctan", "tangent",
        "e", "d"
    )

    private fun isCjk(c: Char): Boolean =
        (c in '\u4E00'..'\u9FFF') || (c in '\u3000'..'\u303F') || (c in '\uFF00'..'\uFFEF')

    private class TextRun(val text: String, val paint: Paint, val width: Float)

    /**
     * 把一段文本拆成若干渲染段并分别测量：
     *  - 中文/全角标点 → 宋体（cjkPaint）
     *  - 拉丁字母：函数名（sin/cos/e…）→ 正体；其他（单字母变量或 ab 组合）→ 斜体
     *  - 数字/符号 → 正体
     */
    private fun textRuns(text: String, sz: Float): List<TextRun> {
        val runs = ArrayList<TextRun>()
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
                0 -> runs.add(TextRun(seg, cjkPaint, measureText(cjkPaint, seg, sz)))
                1 -> runs.addAll(latinRuns(seg, sz, prevChar))
                else -> runs.add(TextRun(seg, textPaint, measureText(textPaint, seg, sz)))
            }
            // 记录上一个字符（用于 i 正体规则：前一个字符是数字或 b 时 i 视为虚数单位）
            prevChar = if (kind == 1) seg.lastOrNull() else seg.lastOrNull()
            i = j
        }
        return runs
    }

    /**
     * 拉丁字母段：从左到右扫描，连续识别所有函数名（sin/cos/e…）→ 正体；
     * 其余字母 → 斜体；'i'/'I' 在前一个字符是数字或 b 时 → 正体（虚数单位）。
     */
    private fun latinRuns(seg: String, sz: Float, prevChar: Char? = null): List<TextRun> {
        val out = ArrayList<TextRun>()
        val fns = FUNCTION_NAMES.sortedByDescending { it.length }
        var i = 0
        var lastChar: Char? = prevChar // 跨段延续（用于 i 正体规则）
        while (i < seg.length) {
            var matched: String? = null
            for (fn in fns) {
                if (seg.regionMatches(i, fn, 0, fn.length, ignoreCase = true)) { matched = fn; break }
            }
            if (matched != null) {
                val head = seg.substring(i, i + matched.length)
                out.add(TextRun(head, textPaint, measureText(textPaint, head, sz)))
                i += matched.length
                lastChar = head.lastOrNull()
            } else {
                // 斜体段：吞到下一个函数名前；同时检查 'i' 前是不是数字/b → 正体
                var j = i + 1
                while (j < seg.length) {
                    var isFn = false
                    for (fn in fns) {
                        if (seg.regionMatches(j, fn, 0, fn.length, ignoreCase = true)) { isFn = true; break }
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
                            out.add(TextRun(seg1, varPaint, measureText(varPaint, seg1, sz)))
                        }
                        // 输出 i 为正体
                        out.add(TextRun(c.toString(), textPaint, measureText(textPaint, c.toString(), sz)))
                        i = k + 1
                    }
                    lastChar = c
                    k++
                }
                if (i < j) {
                    val rest = seg.substring(i, j)
                    out.add(TextRun(rest, varPaint, measureText(varPaint, rest, sz)))
                    lastChar = rest.lastOrNull()
                    i = j
                }
            }
        }
        return out
    }

    private fun bracketW(h: Float): Float =
        (h * 0.10f).coerceAtLeast(4f * density)

    /** 手绘细括号，替代字体放大导致的粗括号 */
    private fun drawBracket(canvas: DrawOps, ch: String, x: Float, y: Float, h: Float) {
        if (ch == "." || ch.isEmpty()) return
        val w = bracketW(h)
        when (ch) {
            "(" -> canvas.drawPath(
                listOf(
                    PathOp.MoveTo(x + w, y),
                    PathOp.CubicTo(x, y + h * 0.25f, x, y + h * 0.75f, x + w, y + h)
                ), bracketPaint
            )
            ")" -> canvas.drawPath(
                listOf(
                    PathOp.MoveTo(x, y),
                    PathOp.CubicTo(x + w, y + h * 0.25f, x + w, y + h * 0.75f, x, y + h)
                ), bracketPaint
            )
            "[" -> {
                canvas.drawLine(x, y, x, y + h, bracketPaint)
                canvas.drawLine(x, y, x + w, y, bracketPaint)
                canvas.drawLine(x, y + h, x + w, y + h, bracketPaint)
            }
            "]" -> {
                canvas.drawLine(x + w, y, x + w, y + h, bracketPaint)
                canvas.drawLine(x, y, x + w, y, bracketPaint)
                canvas.drawLine(x, y + h, x + w, y + h, bracketPaint)
            }
            "|" -> canvas.drawLine(x + w / 2f, y, x + w / 2f, y + h, bracketPaint)
            "\\{", "{" -> canvas.drawPath(
                listOf(
                    PathOp.MoveTo(x + w * 0.85f, y),
                    PathOp.QuadTo(x, y + h * 0.14f, x, y + h * 0.42f),
                    PathOp.QuadTo(x, y + h * 0.5f, x + w * 0.26f, y + h * 0.5f),
                    PathOp.QuadTo(x, y + h * 0.5f, x, y + h * 0.58f),
                    PathOp.QuadTo(x, y + h * 0.86f, x + w * 0.85f, y + h)
                ), bracketPaint
            )
            "\\}", "}" -> canvas.drawPath(
                listOf(
                    PathOp.MoveTo(x + w * 0.15f, y),
                    PathOp.QuadTo(x + w, y + h * 0.14f, x + w, y + h * 0.42f),
                    PathOp.QuadTo(x + w, y + h * 0.5f, x + w * 0.74f, y + h * 0.5f),
                    PathOp.QuadTo(x + w, y + h * 0.5f, x + w, y + h * 0.58f),
                    PathOp.QuadTo(x + w, y + h * 0.86f, x + w * 0.15f, y + h)
                ), bracketPaint
            )
            else -> {
                textPaint.textSize = h * 0.85f
                val fm = textPaint.fontMetrics
                canvas.drawText(ch, x, y + h - fm.descent - (h * 0.05f), textPaint)
            }
        }
    }

    private fun relayout() {
        groupBoxes.clear()
        multilineLines.clear()
        if (formula.root.nodes.any { it is MathNode.NewLine }) {
            layoutRoot = measureMultiline(formula.root, baseSize)
        } else {
            layoutRoot = measureGroup(formula.root, baseSize)
        }
        contentW = (layoutRoot?.w ?: 0f) + paddingLeft + paddingRight
        contentH = (layoutRoot?.h ?: 0f) + paddingTop + paddingBottom
    }

    /** 多行排版缓存：Group → 每行的 LayoutBox 列表 */
    private val multilineLines = HashMap<MathNode.Group, List<LayoutBox>>()
    /** NewLine 分隔出的行节点列表缓存 */
    private val multilineRowNodes = HashMap<MathNode.Group, List<List<MathNode>>>()

    private fun measureMultiline(g: MathNode.Group, sz: Float): LayoutBox {
        val box = LayoutBox(0f, 0f, 0f, sz)
        groupBoxes[g] = box
        // 按 NewLine 分割行
        val rows = ArrayList<List<MathNode>>()
        var cur = ArrayList<MathNode>()
        for (n in g.nodes) {
            if (n is MathNode.NewLine) { rows.add(cur); cur = ArrayList() }
            else cur.add(n)
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

    private fun measureGroup(g: MathNode.Group, sz: Float): LayoutBox {
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

    private fun measureNode(n: MathNode, sz: Float): LayoutBox {
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
                val disp = CMD_DISPLAY[n.latex] ?: n.latex.trimStart('\\')
                val paint = if (n.latex in OP_LATEX) opPaint else textPaint
                val w = measureText(paint, disp, if (n.latex in OP_LATEX) sz * 1.2f else sz)
                LayoutBox(w, lineHeight(sz), ascent(sz), sz)
            }
            is MathNode.Raw -> {
                val w = measureText(rawPaint, n.latex, sz * 0.85f)
                LayoutBox(w, lineHeight(sz), ascent(sz), sz)
            }
            is MathNode.Fraction -> {
                val num = measureGroup(n.num, sz)
                val den = measureGroup(n.den, sz)
                val gap = 0.14f * sz
                val barH = max(1.3f * density, 0.07f * sz)
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
                val overTh = max(1.3f * density, 0.07f * sz)
                val gapAbove = 0.3f * sz
                val radicalSize = (rad.h + gapAbove + overTh) * 1.05f
                val radicalW = measureText(textPaint, "√", radicalSize) * 0.9f
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
                // 记住绘制信息
                sqrtDrawData[n] = SqrtDrawData(radicalSize, radicalW, rad.w, gapAbove, overTh, contentX)
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
                opPaint.textSize = symSize
                val symW = opPaint.measureText(sym)
                opPaint.textSize = sz
                val symFm = opPaint.fontMetrics
                val symH = symFm.descent - symFm.ascent
                val symBaseline = -symFm.ascent
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
                val w = measureText(textPaint, n.name, sz * 0.95f)
                LayoutBox(w, lineHeight(sz), ascent(sz), sz)
            }
            is MathNode.Paren -> {
                val inner = measureGroup(n.inner, sz)
                val parenH = inner.h + 0.2f * sz
                val parenW = bracketW(parenH)
                val gap = 0.07f * sz
                val w = (if (n.left == ".") 0f else parenW) + (if (n.right == ".") 0f else parenW) + inner.w + gap
                val dy = (parenH - inner.h) / 2f
                val box = LayoutBox(w, parenH, dy + inner.baseline, sz)
                box.parts.add(Part(n.inner, if (n.left == ".") 0f else parenW + gap * 0.5f, dy, inner))
                parenInfo[n] = ParenInfo(n.left, n.right, parenH)
                box
            }
            is MathNode.Matrix -> {
                val cellSize = 0.88f * sz
                val cells = n.rows.map { row -> row.map { measureGroup(it, cellSize) } }
                val colW = FloatArray(if (n.rows.isEmpty()) 0 else n.rows[0].size) { 0f }
                // 列宽（Float，避免取整截断导致错位）
                for (r in cells.indices) for (c in cells[r].indices) {
                    colW[c] = max(colW[c], cells[r][c].w)
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
                val w = measureText(textPaint, n.text, sz * 0.92f)
                LayoutBox(w, lineHeight(sz), ascent(sz), sz)
            }
        }
    }

    // 绘制辅助数据
    private class SqrtDrawData(val radicalSize: Float, val radicalW: Float, val contentW: Float, val gapAbove: Float, val overTh: Float, val contentX: Float)
    private class OpInfo(val sym: String, val size: Float, val w: Float, val baseline: Float)
    private class ParenInfo(val left: String, val right: String, val h: Float)
    private class MatrixInfo(val left: String, val right: String, val h: Float)
    private class CasesInfo(val h: Float)
    private class AccentInfo(val accent: String, val accentH: Float, val baseW: Float)

    private val sqrtDrawData = HashMap<MathNode.Sqrt, SqrtDrawData>()
    private val opInfo = HashMap<MathNode.Op, OpInfo>()
    private val parenInfo = HashMap<MathNode.Paren, ParenInfo>()
    private val matrixInfo = HashMap<MathNode.Matrix, MatrixInfo>()
    private val casesInfo = HashMap<MathNode.Cases, CasesInfo>()
    private val accentInfo = HashMap<MathNode.Accent, AccentInfo>()

    // ---------- 绘制 ----------

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // 每次绘制都重新排版：保证任何输入/删除/结构变化都立刻反映在界面上
        relayout()
        val layout = layoutRoot ?: return

        canvas.save()
        canvas.translate(paddingLeft - scrollX.toFloat(), paddingTop - scrollY.toFloat())
        if (showGrid) drawGrid(canvas)

        if (formula.root.nodes.isEmpty()) {
            // 空公式提示
            canvas.restore()
            hintPaint.textAlign = Paint.Align.CENTER
            val cy = height / 2f
            val hint = context.getString(R.string.hint_start)
            canvas.drawText(hint, width / 2f, cy, hintPaint)
            return
        }

        // 多行模式：逐行绘制
        val lines = multilineRowNodes[formula.root]
        val lineBoxes = multilineLines[formula.root]
        if (lines != null && lineBoxes != null) {
            val lineGap = 0.4f * baseSize
            var yOff = 0f
            for ((i, lg) in lines.withIndex()) {
                val tmp = MathNode.Group().apply { nodes.addAll(lg) }
                val lb = lineBoxes[i]
                drawNode(CanvasOps(canvas), tmp, lb, 0f, yOff)
                yOff += lb.h + lineGap
            }
            // 光标
            if (hasFocus()) {
                val gBox = groupBoxes[formula.cursorContainer]
                val origin = groupOrigins[formula.cursorContainer]
                if (gBox != null && origin != null) {
                    val ox = origin.first
                    val oy = origin.second
                    canvas.drawRoundRect(
                        RectF(ox - 2f * density, oy - 2f * density, ox + gBox.w + 2f * density, oy + gBox.h + 2f * density),
                        5f * density, 5f * density, focusFillPaint
                    )
                    if (blinkOn) {
                        val x = ox + caretX(formula.cursorContainer, gBox)
                        val top = oy + gBox.baseline - ascent(gBox.sz)
                        val bottom = oy + gBox.baseline + descent(gBox.sz)
                        canvas.drawLine(x, top, x, bottom, caretPaint)
                    }
                }
            }
            canvas.restore()
            return
        }
        drawNode(CanvasOps(canvas), formula.root, layout, 0f, 0f)

        // 光标所在容器高亮 + 光标
        if (hasFocus()) {
            val gBox = groupBoxes[formula.cursorContainer]
            val origin = groupOrigins[formula.cursorContainer]
            if (gBox != null && origin != null) {
                val ox = origin.first
                val oy = origin.second
                // 淡蓝高亮容器，明确"点进去了"
                canvas.drawRoundRect(
                    RectF(ox - 2f * density, oy - 2f * density, ox + gBox.w + 2f * density, oy + gBox.h + 2f * density),
                    5f * density, 5f * density, focusFillPaint
                )
                if (blinkOn) {
                    val x = ox + caretX(formula.cursorContainer, gBox)
                    val top = oy + gBox.baseline - ascent(gBox.sz)
                    val bottom = oy + gBox.baseline + descent(gBox.sz)
                    canvas.drawLine(x, top, x, bottom, caretPaint)
                }
            }
        }
        canvas.restore()
    }

    /** 画点阵草稿背景（随内容滚动） */
    private fun drawGrid(canvas: Canvas) {
        val step = 0.8f * baseSize
        if (step < 4f) return
        val left = scrollX.toFloat() - paddingLeft
        val top = scrollY.toFloat() - paddingTop
        val right = left + width
        val bottom = top + height
        val r = (0.9f * density * (0.5f + 0.5f * fontScale)).coerceAtLeast(0.8f)
        val offX = paddingLeft - scrollX.toFloat()
        val offY = paddingTop - scrollY.toFloat()
        var cy = top - floorMod(top, step)
        while (cy < bottom) {
            var cx = left - floorMod(left, step)
            while (cx < right) {
                canvas.drawCircle(cx + offX, cy + offY, r, gridPaint)
                cx += step
            }
            cy += step
        }
    }

    /** 不小于 0 的取模（用于点阵对齐） */
    private fun floorMod(a: Float, b: Float): Float {
        val r = a % b
        return if (r < 0f) r + b else r
    }

    /** 应用用户设置（字号缩放 / 点阵背景） */
    fun applySettings(fontScale: Float, showGrid: Boolean) {
        this.fontScale = fontScale.coerceIn(0.6f, 1.8f)
        this.showGrid = showGrid
        baseSize = 36f * density * this.fontScale
        gridPaint.color = (themeColor(android.R.attr.textColorSecondary, 0xFF999999.toInt()) and 0x00FFFFFF) or 0x22000000
        relayout()
        invalidate()
    }

    private fun drawNode(canvas: DrawOps, n: MathNode, box: LayoutBox, x: Float, y: Float) {
        // 先画各结构节点自身的装饰
        when (n) {
            is MathNode.Fraction -> {
                val barY = y + (box.parts.firstOrNull()?.box?.h ?: 0f)
                val gap = 0.14f * n.num.let { box.sz }
                val barH = max(1.3f * density, 0.07f * box.sz)
                linePaint.strokeWidth = barH
                canvas.drawLine(x, y + barY + gap + barH / 2f, x + box.w, y + barY + gap + barH / 2f, linePaint)
                linePaint.strokeWidth = 1.4f * density
            }
            is MathNode.Sqrt -> {
                val d = sqrtDrawData[n] ?: return
                textPaint.textSize = d.radicalSize
                canvas.drawText("√", x, y + d.radicalSize * 0.82f, textPaint)
                linePaint.strokeWidth = max(1.3f * density, 0.07f * box.sz)
                canvas.drawLine(
                    x + d.radicalW + 0.02f * box.sz, y + d.gapAbove,
                    x + d.radicalW + 0.02f * box.sz + d.contentW, y + d.gapAbove,
                    linePaint
                )
                linePaint.strokeWidth = 1.4f * density
                textPaint.textSize = box.sz
            }
            is MathNode.Op -> {
                val info = opInfo[n] ?: return
                opPaint.textSize = info.size
                canvas.drawText(info.sym, x + (box.w - info.w) / 2f, y + info.baseline, opPaint)
            }
            is MathNode.Paren -> {
                val info = parenInfo[n] ?: return
                drawBracket(canvas, info.left, x, y, info.h)
                drawBracket(canvas, info.right, x + box.w - bracketW(info.h), y, info.h)
            }
            is MathNode.Matrix -> {
                val info = matrixInfo[n] ?: return
                if (info.left.isNotEmpty()) {
                    drawBracket(canvas, info.left, x, y, info.h)
                    drawBracket(canvas, info.right, x + box.w - bracketW(info.h), y, info.h)
                }
            }
            is MathNode.Cases -> {
                val info = casesInfo[n] ?: return
                drawBracket(canvas, "{", x, y, info.h)
            }
            is MathNode.Accent -> {
                val info = accentInfo[n] ?: return
                val cx = x + box.w / 2f
                // 下移避免符号溢出 box 顶部
                val ay = y + info.accentH * 0.92f
                when (info.accent) {
                    "vec" -> {
                        // 向量：细横线 + 右箭头尖，宽度 = base 全宽
                        val lx = x + 0.05f * box.sz
                        val rx = x + box.w - 0.05f * box.sz
                        accentPaint.strokeWidth = max(1.0f * density, 0.035f * box.sz)
                        canvas.drawLine(lx, ay, rx, ay, accentPaint)
                        accentPaint.textSize = 0.45f * box.sz
                        accentPaint.textAlign = Paint.Align.LEFT
                        canvas.drawText("▶", rx - 0.04f * box.sz, ay, accentPaint)
                        accentPaint.strokeWidth = 1.4f * density
                    }
                    "overrightarrow" -> {
                        val lx = x + 0.05f * box.sz
                        val rx = x + box.w - 0.05f * box.sz
                        accentPaint.strokeWidth = max(1.0f * density, 0.035f * box.sz)
                        canvas.drawLine(lx, ay, rx, ay, accentPaint)
                        accentPaint.textSize = 0.5f * box.sz
                        accentPaint.textAlign = Paint.Align.LEFT
                        canvas.drawText("▶", rx - 0.04f * box.sz, ay, accentPaint)
                        accentPaint.textAlign = Paint.Align.LEFT
                        accentPaint.strokeWidth = 1.4f * density
                    }
                    "hat" -> {
                        accentPaint.textSize = 0.68f * box.sz
                        canvas.drawText("^", cx, ay, accentPaint)
                    }
                    "bar", "overline" -> {
                        accentPaint.strokeWidth = max(1.2f * density, 0.06f * box.sz)
                        canvas.drawLine(x + 0.02f * box.sz, ay, x + box.w - 0.02f * box.sz, ay, accentPaint)
                        accentPaint.strokeWidth = 1.4f * density
                    }
                    "dot" -> {
                        accentPaint.textSize = 0.65f * box.sz
                        canvas.drawText("·", cx, ay, accentPaint)
                    }
                    "widetilde", "widehat" -> {
                        accentPaint.textSize = 0.60f * box.sz
                        canvas.drawText("~", cx, ay, accentPaint)
                    }
                    else -> {
                        accentPaint.textSize = 0.65f * box.sz
                        canvas.drawText(info.accent.take(1), cx, ay, accentPaint)
                    }
                }
            }
            else -> {}
        }

        // 递归画子节点
        for (p in box.parts) {
            drawNode(canvas, p.node, p.box, x + p.dx, y + p.dy)
        }

        // 文本/符号/占位框
        when (n) {
            is MathNode.Text -> {
                // 分段绘制：中文宋体、函数名正体、变量斜体
                var cx = x
                for (r in textRuns(n.text, box.sz)) {
                    r.paint.textSize = box.sz
                    canvas.drawText(r.text, cx, y + box.baseline, r.paint)
                    cx += r.width
                }
            }
            is MathNode.Cmd -> {
                val disp = CMD_DISPLAY[n.latex] ?: n.latex.trimStart('\\')
                if (n.latex in OP_LATEX) {
                    opPaint.textSize = box.sz * 1.2f
                    canvas.drawText(disp, x, y + box.baseline, opPaint)
                } else {
                    textPaint.textSize = box.sz
                    canvas.drawText(disp, x, y + box.baseline, textPaint)
                }
            }
            is MathNode.Raw -> {
                rawPaint.textSize = box.sz * 0.85f
                canvas.drawText(n.latex, x, y + box.baseline, rawPaint)
            }
            is MathNode.Func -> {
                textPaint.textSize = box.sz * 0.95f
                canvas.drawText(n.name, x, y + box.baseline, textPaint)
            }
            is MathNode.Chem -> {
                textPaint.textSize = box.sz * 0.92f
                canvas.drawText(n.text, x, y + box.baseline, textPaint)
            }
            is MathNode.Group -> {
                groupOrigins[n] = x to y
                if (n.nodes.isEmpty()) {
                    // 占位框
                    canvas.drawRoundRect(
                        x, y + box.baseline - ascent(box.sz),
                        x + box.w, y + box.baseline + descent(box.sz),
                        3f * density, 3f * density, placeholderPaint
                    )
                }
            }
            else -> {}
        }
    }

    /** 光标在容器中的 x 坐标（支持文本节点内部） */
    private fun caretX(g: MathNode.Group, box: LayoutBox): Float {
        var x = 0f
        val n = formula.cursorIndex.coerceAtMost(box.parts.size)
        for (i in 0 until n) {
            x += box.parts[i].box.w
            if (i < n - 1) x += 0.06f * box.sz
        }
        if (formula.cursorChar >= 0) {
            val prev = formula.prevNode()
            val t = when (prev) {
                is MathNode.Text -> prev.text
                is MathNode.Chem -> prev.text
                else -> null
            }
            if (t != null) x += prefixWidth(t, box.sz, formula.cursorChar)
        }
        return x
    }

    /** 文本前 k 个字符的渲染宽度 */
    private fun prefixWidth(text: String, sz: Float, k: Int): Float {
        var w = 0f
        var cnt = 0
        for (r in textRuns(text, sz)) {
            val take = min(k - cnt, r.text.length)
            if (take <= 0) break
            w += measureText(r.paint, r.text.substring(0, take), sz)
            cnt += take
        }
        return w
    }

    // ---------- 触摸 ----------

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                downScrollX = scrollX
                downScrollY = scrollY
                dragging = false
                requestFocus()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - downX
                val dy = event.y - downY
                val slop = 16f * density
                if (abs(dx) > slop || abs(dy) > slop) dragging = true
                if (dragging) {
                    var sx = (downScrollX - dx).toInt()
                    var sy = (downScrollY - dy).toInt()
                    sx = sx.coerceIn(0, max(0, (contentW - width).toInt()))
                    sy = sy.coerceIn(0, max(0, (contentH - height).toInt()))
                    scrollTo(sx, sy)
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!dragging) {
                    tapToCursor(event.x, event.y)
                    return true
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun tapToCursor(tx: Float, ty: Float) {
        relayout()
        val layout = layoutRoot ?: return
        val px = tx - paddingLeft + scrollX
        val py = ty - paddingTop + scrollY
        val hit = hitTestBox(formula.root, layout, px, py)
        if (hit != null) {
            applyHit(hit)
            return
        }
        // 点击在公式行外（画布大片空白）：直接用 x 坐标映射到 root 的最近字符边界
        if (formula.root.nodes.isEmpty()) return
        val (g, idx, char) = groupIndexAt(formula.root, px)
        applyHit(Triple(g, idx, char))
    }

    private fun applyHit(hit: Triple<MathNode.Group, Int, Int>) {
        val (g, idx, char) = hit
        formula.cursorContainer = g
        if (char >= 0) {
            formula.cursorIndex = idx + 1
            formula.cursorChar = char
        } else {
            formula.cursorIndex = idx
            formula.cursorChar = -1
        }
        formula.clampCursor()
        cursorAutoPlaced = false
        ensureCursorVisible()
        performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        invalidate()
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
            return hitTestBox(best.node, best.box, px - best.dx, py - best.dy)
                ?: (if (best.node is MathNode.Group) groupIndexAt(best.node, px - best.dx) else null)
        }
        return null
    }

    /** 返回 (容器, 节点下标, 节点内字符偏移)；char>=0 表示落在文本节点内部 */
    private fun groupIndexAt(g: MathNode.Group, px: Float): Triple<MathNode.Group, Int, Int> {
        val parts = groupBoxes[g]?.parts ?: emptyList()
        if (parts.isEmpty()) return Triple(g, 0, -1)
        val gap = 0.06f * (groupBoxes[g]?.sz ?: baseSize)
        // "最近边界"算法：计算每个候选插入位置，取与点击 x 最近者
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
                for (r in textRuns(nodeText, p.box.sz)) {
                    for (c in r.text) {
                        cnt++
                        cx += measureText(r.paint, c.toString(), p.box.sz)
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

    private fun ensureCursorVisible() {
        val gBox = groupBoxes[formula.cursorContainer] ?: return
        val x = caretX(formula.cursorContainer, gBox) + paddingLeft
        val y = gBox.baseline + paddingTop
        val vw = width
        val vh = height
        // 先把滚动范围钳制在内容范围内，避免内容被滚到画面之外而“看起来空白”
        val maxX = max(0, (contentW - vw).toInt())
        val maxY = max(0, (contentH - vh).toInt())
        if (scrollX > maxX || scrollY > maxY) {
            scrollTo(scrollX.coerceIn(0, maxX), scrollY.coerceIn(0, maxY))
        }
        if (x < scrollX) scrollTo(x.toInt() - 8, scrollY)
        else if (x > scrollX + vw) scrollTo((x - vw + 8).toInt(), scrollY)
        if (y < scrollY) scrollTo(scrollX, max(0, y.toInt() - 8))
        else if (y > scrollY + vh) scrollTo(scrollX, max(0, (y - vh + 8).toInt()))
    }

    // ---------- 输入连接（系统键盘） ----------

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_ACTION_DONE
        return object : BaseInputConnection(this, true) {
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                if (!text.isNullOrEmpty()) insertUserText(text.toString())
                return true
            }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                // 某些输入法（如微信输入法）会一次性请求删除多个字符，这里按节点逐个删
                if (afterLength > 0) {
                    repeat(afterLength) { deleteForward() }
                } else {
                    repeat(beforeLength) { backspace() }
                }
                return true
            }

            override fun sendKeyEvent(event: KeyEvent): Boolean {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    when (event.keyCode) {
                        KeyEvent.KEYCODE_DEL -> backspace()
                        KeyEvent.KEYCODE_FORWARD_DEL -> deleteForward()
                        KeyEvent.KEYCODE_DPAD_LEFT -> moveLeft()
                        KeyEvent.KEYCODE_DPAD_RIGHT -> moveRight()
                        KeyEvent.KEYCODE_DPAD_UP -> moveUp()
                        KeyEvent.KEYCODE_DPAD_DOWN -> moveDown()
                        KeyEvent.KEYCODE_ENTER -> return true
                        else -> return super.sendKeyEvent(event)
                    }
                }
                return true
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DEL -> { backspace(); return true }
            KeyEvent.KEYCODE_FORWARD_DEL -> { deleteForward(); return true }
            KeyEvent.KEYCODE_DPAD_LEFT -> { moveLeft(); return true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { moveRight(); return true }
            KeyEvent.KEYCODE_DPAD_UP -> { moveUp(); return true }
            KeyEvent.KEYCODE_DPAD_DOWN -> { moveDown(); return true }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun insertUserText(s: String) {
        pushUndo()
        for (c in s) {
            if (c == '\n') {
                formula.insert(MathNode.NewLine())
                continue
            }
            formula.insertText(c.toString()) // 自动处理文本/化学式合并与节点内插入
        }
        cursorAutoPlaced = false
        relayout()
        ensureCursorVisible()
        invalidate()
        notifyChanged()
    }

    private fun pushUndo() {
        undoStack.addLast(getLatex())
        if (undoStack.size > 100) undoStack.removeFirst()
        redoStack.clear()
    }

    private fun backspace() {
        if (formula.root.nodes.isEmpty()) return
        pushUndo()
        formula.backspace()
        cursorAutoPlaced = false
        relayout()
        ensureCursorVisible()
        invalidate()
        notifyChanged()
    }

    private fun deleteForward() {
        if (formula.root.nodes.isEmpty()) return
        pushUndo()
        formula.deleteForward()
        cursorAutoPlaced = false
        relayout()
        ensureCursorVisible()
        invalidate()
        notifyChanged()
    }

    private fun moveLeft() {
        formula.moveLeft()
        formula.clampCursor()
        ensureCursorVisible()
        invalidate()
    }

    private fun moveRight() {
        formula.moveRight()
        formula.clampCursor()
        ensureCursorVisible()
        invalidate()
    }

    private fun moveUp() {
        formula.moveUp()
        formula.clampCursor()
        ensureCursorVisible()
        invalidate()
    }

    private fun moveDown() {
        formula.moveDown()
        formula.clampCursor()
        ensureCursorVisible()
        invalidate()
    }

    private fun notifyChanged() {
        listener?.invoke(getLatex())
    }

    // ---------- 对外 API ----------

    fun getLatex(): String = serializer.clean(formula)

    /** 从 LaTeX 载入（公式框手改 → 图形），不回调 */
    fun loadLatex(src: String) {
        val g = parser.parse(src)
        formula.root.nodes.clear()
        formula.root.nodes.addAll(g.nodes)
        formula.cursorContainer = formula.root
        formula.cursorIndex = formula.root.nodes.size
        formula.cursorChar = -1
        formula.rebuildParents()
        relayout()
        ensureCursorVisible()
        invalidate()
    }

    /** 把当前公式画到一张白底位图上（用于导出 PNG，公式居中带留白） */
    fun renderToBitmap(scale: Float = 3f): android.graphics.Bitmap? {
        if (formula.root.nodes.isEmpty()) return null
        relayout()
        val l = layoutRoot ?: return null
        // 留白：四周各加内容高度的一半（最少 40px）
        val margin = (max(l.w, l.h) * 0.25f * scale).coerceAtLeast(40f)
        var w = (l.w * scale + margin * 2).toInt().coerceAtLeast(1)
        var h = (l.h * scale + margin * 2).toInt().coerceAtLeast(1)
        // 防止超大图 OOM
        val maxW = 4096
        val s = if (w > maxW) maxW.toFloat() / w else scale
        w = (w * s).toInt().coerceAtLeast(1)
        h = (h * s).toInt().coerceAtLeast(1)
        val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(android.graphics.Color.WHITE)
        // 内容水平/垂直居中
        val ox = (w - l.w * s) / 2f
        val oy = (h - l.h * s) / 2f
        c.translate(ox, oy)
        c.scale(s, s)
        drawNode(CanvasOps(c), formula.root, l, 0f, 0f)
        return bmp
    }

    /**
     * 把当前公式输出为 SVG（纯矢量，可无限放大）。
     * 复用与屏幕完全相同的排版结果，所以导出效果与编辑器一致。
     */
    fun renderToSvg(scale: Float = 1f): String? {
        if (formula.root.nodes.isEmpty()) return null
        relayout()
        val l = layoutRoot ?: return null
        val svg = SvgOps(
            context = context,
            fontFamily = { p ->
                if (p.typeface === rawPaint.typeface) "monospace"
                else "KaTeX_Main,KaTeX_AMS,KaTeX_Size1,KaTeX_Size2,KaTeX_Size3," +
                    "KaTeX_Size4,KaTeX_Caligraphic,KaTeX_Script,KaTeX_Fraktur," +
                    "KaTeX_Typewriter,serif"
            },
            italic = { p -> p.typeface === varFont }
        )
        // 按 density 归一化，让 SVG 尺寸与设备无关
        val s = scale / density
        svg.scale(s, s)
        drawNode(svg, formula.root, l, 0f, 0f)
        return svg.build(l.w * s, l.h * s)
    }

    fun clear() {
        pushUndo()
        formula.root.nodes.clear()
        formula.cursorContainer = formula.root
        formula.cursorIndex = 0
        formula.cursorChar = -1
        formula.rebuildParents()
        relayout()
        invalidate()
        notifyChanged()
    }

    fun undo(): String? {
        if (undoStack.isEmpty()) return null
        redoStack.addLast(getLatex())
        val l = undoStack.removeLast()
        loadLatex(l)
        notifyChanged()
        return l
    }

    fun redo(): String? {
        if (redoStack.isEmpty()) return null
        undoStack.addLast(getLatex())
        val l = redoStack.removeLast()
        loadLatex(l)
        notifyChanged()
        return l
    }

    /** 插入符号/结构模板 */
    fun insertAction(a: SymbolAction) {
        when (a) {
            is SymbolAction.Command -> {
                pushUndo()
                if (a.latex.startsWith("\\")) formula.insert(MathNode.Cmd(a.latex))
                else formula.insertText(a.latex)
                cursorAutoPlaced = false
                relayout(); ensureCursorVisible(); invalidate(); notifyChanged()
            }
            is SymbolAction.Text -> {
                pushUndo()
                formula.insertText(a.s)
                cursorAutoPlaced = false
                relayout(); ensureCursorVisible(); invalidate(); notifyChanged()
            }
            is SymbolAction.Fraction -> {
                val f = MathNode.Fraction(MathNode.Group(), MathNode.Group())
                insertTemplate(f, f.num)
            }
            is SymbolAction.Sqrt -> {
                val s = MathNode.Sqrt(MathNode.Group())
                insertTemplate(s, s.radicand)
            }
            is SymbolAction.NRoot -> {
                val s = MathNode.Sqrt(MathNode.Group(), MathNode.Group())
                insertTemplate(s, s.degree)
            }
            is SymbolAction.Sup -> insertScript(MathNode.Group(), null)
            is SymbolAction.Sub -> insertScript(null, MathNode.Group())
            is SymbolAction.BothScripts -> insertScript(MathNode.Group(), MathNode.Group())
            is SymbolAction.Paren -> insertParen("(", ")")
            is SymbolAction.Bracket -> insertParen("[", "]")
            is SymbolAction.Curly -> insertParen("\\{", "\\}")
            is SymbolAction.Abs -> insertParen("|", "|")
            is SymbolAction.SumLimits -> insertOp("\\sum")
            is SymbolAction.ProdLimits -> insertOp("\\prod")
            is SymbolAction.IntLimits -> insertOp("\\int")
            is SymbolAction.Lim -> insertOp("\\lim")
            is SymbolAction.Matrix2 -> insertMatrix(2, 2, "pmatrix")
            is SymbolAction.Matrix3 -> insertMatrix(3, 3, "pmatrix")
            is SymbolAction.Det2 -> insertMatrix(2, 2, "vmatrix")
            is SymbolAction.Cases2 -> insertCases(2)
            is SymbolAction.Cases3 -> insertCases(3)
            is SymbolAction.Vec -> insertAccent("vec")
            is SymbolAction.Hat -> insertAccent("hat")
            is SymbolAction.Bar -> insertAccent("bar")
            is SymbolAction.Dot -> insertAccent("dot")
            is SymbolAction.Tilde -> insertAccent("widetilde")
            is SymbolAction.WideHat -> insertAccent("widehat")
            is SymbolAction.OverArrow -> insertAccent("overrightarrow")
            is SymbolAction.Ce -> {
                pushUndo()
                val c = MathNode.Chem("")
                formula.insert(c)
                formula.cursorContainer = formula.root
                formula.cursorIndex = formula.root.nodes.indexOf(c) + 1
                cursorAutoPlaced = false
                formula.rebuildParents(); relayout(); ensureCursorVisible(); invalidate(); notifyChanged()
            }
            is SymbolAction.ChemText -> {
                pushUndo()
                val c = MathNode.Chem(a.s)
                formula.insert(c)
                formula.cursorContainer = formula.root
                formula.cursorIndex = formula.root.nodes.indexOf(c) + 1
                cursorAutoPlaced = false
                formula.rebuildParents(); relayout(); ensureCursorVisible(); invalidate(); notifyChanged()
            }
            is SymbolAction.Prime -> {
                pushUndo()
                formula.insertText("′")
                cursorAutoPlaced = false
                relayout(); ensureCursorVisible(); invalidate(); notifyChanged()
            }
            is SymbolAction.NewLine -> {
                pushUndo()
                // 换行一律落在根层级，避免插进分数/矩阵内部
                if (formula.cursorContainer !== formula.root) {
                    formula.cursorContainer = formula.root
                    formula.cursorIndex = formula.root.nodes.size
                    formula.cursorChar = -1
                }
                formula.insert(MathNode.NewLine())
                cursorAutoPlaced = false
                relayout(); ensureCursorVisible(); invalidate(); notifyChanged()
            }
            is SymbolAction.MoveLeft -> moveLeft()
            is SymbolAction.MoveRight -> moveRight()
            is SymbolAction.MoveUp -> moveUp()
            is SymbolAction.MoveDown -> moveDown()
            is SymbolAction.Undo -> undo()
            is SymbolAction.Redo -> redo()
            is SymbolAction.Backspace -> backspace()
            is SymbolAction.Clear -> clear()
            is SymbolAction.ToggleIme -> { /* 由外部处理 */ }
        }
    }

    /**
     * 插入结构模板。
     *
     * 若光标正处于"刚插入模板自动进入的空槽"（用户还没点过任何位置、也没输过字），
     * 则把新模板并列插到该结构之后——避免连续点"矩阵/行列式"时无限嵌套、越套越大。
     * 用户手动点进某个格子后再插模板，则正常插入到光标处。
     */
    private fun insertTemplate(node: MathNode, firstSlot: MathNode.Group?) {
        pushUndo()
        val cc = formula.cursorContainer
        val autoJump = cursorAutoPlaced && cc.nodes.isEmpty() && formula.groupOwner[cc] != null
        if (autoJump) {
            val owner = formula.groupOwner[cc]!!
            val parentG = formula.nodeParent[owner] ?: formula.root
            val idx = parentG.nodes.indexOf(owner)
            parentG.nodes.add(idx + 1, node)
            formula.cursorContainer = parentG
            formula.cursorIndex = idx + 2
            formula.cursorChar = -1
            cursorAutoPlaced = false
        } else {
            formula.insert(node)
            if (firstSlot != null) {
                formula.cursorContainer = firstSlot
                formula.cursorIndex = 0
                formula.cursorChar = -1
                cursorAutoPlaced = true
            } else {
                cursorAutoPlaced = false
            }
        }
        formula.rebuildParents()
        relayout(); ensureCursorVisible(); invalidate(); notifyChanged()
    }

    private fun insertScript(sup: MathNode.Group?, sub: MathNode.Group?) {
        pushUndo()
        // 附着到当前光标前一个节点；若没有则先放一个文本占位
        val base: MathNode = if (formula.cursorIndex > 0) {
            formula.cursorContainer.nodes[formula.cursorIndex - 1]
        } else {
            formula.insertText("x")
            formula.cursorContainer.nodes[formula.cursorIndex - 1]
        }
        val sc = MathNode.Script(base, sup, sub)
        formula.cursorContainer.nodes.remove(base)
        formula.cursorContainer.nodes.add(formula.cursorIndex - 1, sc)
        formula.cursorContainer = if (sup != null) sc.sup!! else sc.sub!!
        formula.cursorIndex = 0
        formula.cursorChar = -1
        cursorAutoPlaced = true
        formula.rebuildParents()
        relayout(); ensureCursorVisible(); invalidate(); notifyChanged()
    }

    private fun insertParen(l: String, r: String) {
        val p = MathNode.Paren(MathNode.Group(), l, r)
        insertTemplate(p, p.inner)
    }

    private fun insertOp(latex: String) {
        val op = MathNode.Op(latex, MathNode.Group(), MathNode.Group())
        insertTemplate(op, op.under)
    }

    private fun insertMatrix(rows: Int, cols: Int, delimiter: String) {
        val m = MathNode.Matrix(
            delimiter,
            MutableList(rows) { MutableList(cols) { MathNode.Group() } }
        )
        insertTemplate(m, m.rows[0][0])
    }

    private fun insertCases(n: Int) {
        val c = MathNode.Cases(MutableList(n) { MathNode.Group() })
        insertTemplate(c, c.rows[0])
    }

    private fun insertAccent(accent: String) {
        val a = MathNode.Accent(MathNode.Group(), accent)
        insertTemplate(a, a.base)
    }

    companion object {
        val OP_LATEX = setOf(
            "\\sum", "\\prod", "\\coprod", "\\int", "\\iint", "\\iiint", "\\iiiint",
            "\\oint", "\\oiint", "\\oiiint", "\\bigcup", "\\bigcap", "\\bigoplus",
            "\\bigotimes", "\\bigodot"
        )
        /** 运算符但用小字号（lim/max/min 等函数名，正常大小 + 上下方标签） */
        val SMALL_OP = setOf(
            "\\lim", "\\max", "\\min", "\\inf", "\\sup", "\\argmax", "\\argmin"
        )
    }
}
