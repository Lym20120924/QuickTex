package com.formulastudio.app.doc

import android.graphics.Paint
import android.graphics.Typeface
import com.formulastudio.app.editor.CMD_DISPLAY
import com.formulastudio.app.editor.DrawOps
import com.formulastudio.app.editor.PathOp
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.pow
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * TikZ / pgfplots 图形渲染（常用子集），输出到 [DrawOps]，因此可同时导出 PNG 和 SVG。
 *
 * 支持的常用构造：
 * - TikZ：`\coordinate`、`\draw`/`\path`（直线、折线、cycle、圆、矩形）、
 *   `\node`（above/below/left/right 等定位）、`\pic{right angle=..}`、`\pic{angle=..}`、
 *   `dashed`、`thick`、颜色、`scale`
 * - pgfplots：`axis` 环境、坐标轴 / 网格 / 刻度、`\addplot {表达式}`、`\addplot coordinates{..}`、
 *   图例、xlabel/ylabel
 */
object FigureRenderer {

    /** 1 个 tikz 单位（cm）对应的像素 */
    private const val UNIT = 60f

    /** 图形四周留白 */
    private const val MARGIN = 24f

    data class Size(val w: Float, val h: Float)

    private val namedColors: Map<String, Int> = mapOf(
        "red" to 0xFFE53935.toInt(), "green" to 0xFF43A047.toInt(),
        "blue" to 0xFF1E88E5.toInt(), "black" to 0xFF222222.toInt(),
        "white" to 0xFFFFFFFF.toInt(), "gray" to 0xFF9E9E9E.toInt(),
        "grey" to 0xFF9E9E9E.toInt(), "orange" to 0xFFFB8C00.toInt(),
        "purple" to 0xFF8E24AA.toInt(), "brown" to 0xFF6D4C41.toInt(),
        "pink" to 0xFFEC407A.toInt(), "cyan" to 0xFF00ACC1.toInt(),
        "yellow" to 0xFFFDD835.toInt(), "teal" to 0xFF00897B.toInt(),
        "violet" to 0xFF8E24AA.toInt(), "olive" to 0xFF9E9D24.toInt(),
        "lime" to 0xFFC0CA33.toInt(), "navy" to 0xFF1565C0.toInt()
    )

    // ---------- 中间表示 ----------

    private sealed class Op {
        data class Poly(
            val pts: List<Pair<Float, Float>>,
            val closed: Boolean,
            val color: Int,
            val width: Float,
            val dashed: Boolean
        ) : Op()

        data class Circle(
            val c: Pair<Float, Float>, val r: Float,
            val color: Int, val width: Float, val dashed: Boolean
        ) : Op()

        data class Rect(
            val x: Float, val y: Float, val w: Float, val h: Float,
            val color: Int, val width: Float, val dashed: Boolean
        ) : Op()

        data class Text(
            val x: Float, val y: Float, val text: String,
            val anchor: String, val color: Int, val size: Float
        ) : Op()

        data class Arc(
            val c: Pair<Float, Float>, val r: Float,
            val a1: Float, val a2: Float,
            val color: Int, val width: Float
        ) : Op()

        /** 直角标记 */
        data class RightAngle(
            val p1: Pair<Float, Float>, val v: Pair<Float, Float>, val p2: Pair<Float, Float>,
            val size: Float, val color: Int, val width: Float
        ) : Op()
    }

    private class Ctx {
        val coords = HashMap<String, Pair<Float, Float>>()
        val ops = ArrayList<Op>()
        var scale = 1f
        var unit = UNIT
        var stroke = 0xFF222222.toInt()
        var width = 2.2f
        var dashed = false
        var fill: Int? = null
        var fontSize = 14f
    }

    // ---------- 对外入口 ----------

    fun tikz(src: String, out: DrawOps): Size {
        val ctx = Ctx()
        parseTikz(src, ctx)
        return emit(ctx, out)
    }

    fun pgfplots(src: String, out: DrawOps): Size {
        val ctx = Ctx()
        parseAxis(src, ctx)
        return emit(ctx, out)
    }

    // ---------- TikZ 解析 ----------

    private fun parseTikz(src: String, ctx: Ctx) {
        // 全局选项 [scale=..]
        val optMatch = Regex("^\\s*\\[([^\\]]*)\\]").find(src)
        if (optMatch != null) {
            parseOptions(optMatch.groupValues[1], ctx)
        }
        for (cmd in splitCommands(src)) {
            val c = cmd.trim()
            if (c.isEmpty() || c.startsWith("%")) continue
            if (c.startsWith("\\coordinate")) {
                handleCoordinate(c, ctx)
            } else if (c.startsWith("\\draw") || c.startsWith("\\path") || c.startsWith("\\fill") || c.startsWith("\\filldraw")) {
                handleDraw(c, ctx)
            } else if (c.startsWith("\\node")) {
                handleNode(c, ctx)
            } else if (c.startsWith("\\pic")) {
                handlePic(c, ctx)
            }
        }
    }

    private fun handleCoordinate(c: String, ctx: Ctx) {
        val m = Regex("\\(([A-Za-z0-9_]+)\\)\\s*(?:at)?\\s*\\(([^)]+)\\)").find(c) ?: return
        val name = m.groupValues[1]
        val p = parsePoint(m.groupValues[2], ctx) ?: return
        ctx.coords[name] = p
    }

    private fun handleDraw(c: String, ctx: Ctx) {
        val opts = Regex("^\\\\(draw|path|fill|filldraw)\\s*(\\[([^\\]]*)\\])?").find(c)
        val local = Ctx().apply {
            coords.putAll(ctx.coords)
            scale = ctx.scale; unit = ctx.unit; stroke = ctx.stroke
            width = ctx.width; dashed = ctx.dashed; fill = ctx.fill; fontSize = ctx.fontSize
        }
        if (opts != null && opts.groupValues.size > 3) {
            parseOptions(opts.groupValues[3], local)
        }
        if (c.startsWith("\\fill") || c.startsWith("\\filldraw")) local.fill = local.fill ?: local.stroke
        val body = c.substring(opts?.range?.last?.plus(1) ?: 0).trim()
        drawPath(body, local)
        ctx.coords.putAll(local.coords)
        ctx.ops.addAll(local.ops)
    }

    private fun handleNode(c: String, ctx: Ctx) {
        val opts = Regex("^\\\\node\\s*(?:\\[([^\\]]*)\\])?").find(c)
        var rest = c.substring(opts?.range?.last?.plus(1) ?: 6).trim()
        var anchor = ""
        var pos = 0f
        if (opts != null && opts.groupValues.size > 1) {
            val o = parseNodeOptions(opts.groupValues[1])
            anchor = o.first
            pos = o.second
        }
        // at (x,y)
        val atM = Regex("at\\s*\\(([^)]+)\\)").find(rest)
        val p = if (atM != null) {
            parsePoint(atM.groupValues[1], ctx) ?: return
        } else {
            // 没有 at：取路径起点
            val pm = Regex("\\(([^)]+)\\)").find(rest) ?: return
            parsePoint(pm.groupValues[1], ctx) ?: return
        }
        val tb = rest.indexOfLast { it == '{' }
        val te = rest.lastIndexOf('}')
        if (tb < 0 || te <= tb) return
        val text = simpleText(rest.substring(tb + 1, te))
        if (text.isBlank()) return
        val (dx, dy) = anchorOffset(anchor, pos)
        ctx.ops.add(
            Op.Text(p.first + dx, p.second + dy, text, anchor, ctx.stroke, ctx.fontSize)
        )
    }

    private fun handlePic(c: String, ctx: Ctx) {
        val opts = Regex("^\\\\pic\\s*(?:\\[([^\\]]*)\\])?").find(c)
        var optStr = if (opts != null && opts.groupValues.size > 1) opts.groupValues[1] else ""
        var body = c.substring(opts?.range?.last?.plus(1) ?: 4).trim()
        body = body.trimStart('{').trimEnd('}')
        val parts = body.split("=", limit = 2)
        val kind = parts[0].trim()
        val args = if (parts.size > 1) parts[1].trim() else ""
        val pts = args.split("--").mapNotNull { seg ->
            val name = seg.trim()
            ctx.coords[name] ?: parsePoint("($name)", ctx)
        }
        val radius = Regex("angle radius\\s*=\\s*([0-9.]+)\\s*(cm|mm|pt)?").find(optStr)
        var r = (radius?.groupValues?.get(1)?.toFloatOrNull() ?: 0.45f) * ctx.unit * ctx.scale
        radius?.groupValues?.get(2)?.let { u ->
            r *= when (u) { "mm" -> 0.1f; "pt" -> 0.035f; else -> 1f }
        }
        val ecc = Regex("angle eccentricity\\s*=\\s*([0-9.]+)")
            .find(optStr)?.groupValues?.get(1)?.toFloatOrNull() ?: 1.4f
        val labelM = Regex("\"([^\"]*)\"").find(optStr)
        val label = labelM?.let { simpleText(it.groupValues[1]) } ?: ""

        if (pts.size < 3) return
        if (kind == "right angle") {
            val size = r * 0.9f
            ctx.ops.add(Op.RightAngle(pts[0], pts[1], pts[2], size, ctx.stroke, ctx.width))
        } else if (kind == "angle") {
            val v = pts[1]
            val a1 = atan2(pts[0].second - v.second, pts[0].first - v.first)
            val a2 = atan2(pts[2].second - v.second, pts[2].first - v.first)
            ctx.ops.add(Op.Arc(v, r, a1, a2, ctx.stroke, ctx.width))
            if (label.isNotEmpty()) {
                val mid = Angle.bisector(a1, a2)
                val lr = r * ecc
                ctx.ops.add(
                    Op.Text(
                        v.first + (lr * cos(mid)).toFloat(),
                        v.second + (lr * sin(mid)).toFloat(),
                        label, "", ctx.stroke, ctx.fontSize
                    )
                )
            }
        }
    }

    /** 画路径：支持直线、折线、cycle、circle、rectangle */
    private fun drawPath(body: String, ctx: Ctx) {
        val s = body.trim()
        val circleM = Regex("^circle\\s*\\(([0-9.]+)(?:cm|mm|pt)?\\)").find(s)
        if (circleM != null) {
            val c = ctx.coords.values.lastOrNull() ?: return
            var r = circleM.groupValues[1].toFloatOrNull() ?: return
            r *= ctx.unit * ctx.scale
            ctx.ops.add(Op.Circle(c, r, ctx.stroke, ctx.width, ctx.dashed))
            return
        }
        val rectM = Regex("^rectangle\\s*\\(([^)]+)\\)").find(s)
        if (rectM != null) {
            val a = Regex("\\(([^)]+)\\)").find(s)
            val p1 = parsePoint(a?.groupValues?.get(1) ?: return, ctx) ?: return
            val p2 = parsePoint(rectM.groupValues[1], ctx) ?: return
            ctx.ops.add(
                Op.Rect(
                    min(p1.first, p2.first), min(p1.second, p2.second),
                    abs(p1.first - p2.first), abs(p1.second - p2.second),
                    ctx.stroke, ctx.width, ctx.dashed
                )
            )
            return
        }
        // 折线
        val segs = Regex("--|\\|\\||-\\||\\.\\.|,").split(s)
        val pts = ArrayList<Pair<Float, Float>>()
        var closed = s.trimEnd().endsWith("cycle")
        for (seg in segs) {
            val t = seg.trim()
            if (t.isEmpty()) continue
            val nameM = Regex("^\\(([A-Za-z0-9_]+)\\)$").find(t)
            if (nameM != null) {
                val p = ctx.coords[nameM.groupValues[1]] ?: continue
                pts.add(p); continue
            }
            val p = parsePoint(t, ctx) ?: continue
            pts.add(p)
        }
        if (pts.size >= 2) {
            ctx.ops.add(Op.Poly(pts, closed, ctx.stroke, ctx.width, ctx.dashed))
        }
    }

    // ---------- pgfplots / axis 解析 ----------

    private fun parseAxis(src: String, ctx: Ctx) {
        val optM = Regex("^\\s*\\[([^\\]]*)\\]").find(src)
        var xmin = -5f; var xmax = 5f; var ymin = -5f; var ymax = 5f
        var grid = false
        var xlabel = ""; var ylabel = ""
        if (optM != null) {
            val o = optM.groupValues[1]
            fun num(key: String, def: Float): Float =
                Regex("$key\\s*=\\s*(-?[0-9.]+)").find(o)?.groupValues?.get(1)?.toFloatOrNull() ?: def
            xmin = num("xmin", xmin); xmax = num("xmax", xmax)
            ymin = num("ymin", ymin); ymax = num("ymax", ymax)
            grid = Regex("grid\\s*=\\s*(true|major|minor|all)").containsMatchIn(o)
            xlabel = Regex("xlabel\\s*=\\s*[{]?([^},]*)[}]?").find(o)?.groupValues?.get(1)?.trim() ?: ""
            ylabel = Regex("ylabel\\s*=\\s*[{]?([^},]*)[}]?").find(o)?.groupValues?.get(1)?.trim() ?: ""
        }
        if (xmax <= xmin) xmax = xmin + 1
        if (ymax <= ymin) ymax = ymin + 1

        // 数据
        val plots = ArrayList<Pair<List<Pair<Float, Float>>, Pair<Int, Boolean>>>()
        for (cmd in splitCommands(src)) {
            val c = cmd.trim()
            if (!c.startsWith("\\addplot")) continue
            val opts = Regex("^\\\\addplot\\+?\\s*(?:\\[([^\\]]*)\\])?").find(c)
            var color = 0xFF1E88E5.toInt()
            var width = 2.5f
            var dashed = false
            if (opts != null && opts.groupValues.size > 1) {
                val o = opts.groupValues[1]
                Regex("color\\s*=\\s*([A-Za-z]+)").find(o)?.let {
                    namedColors[it.groupValues[1]]?.let { c2 -> color = c2 }
                }
                if (o.contains("dashed")) dashed = true
            }
            val body = c.substring(opts?.range?.last?.plus(1) ?: 8).trim()
            val data = ArrayList<Pair<Float, Float>>()
            val exprM = Regex("^\\{([^{}]*)\\}").find(body)
            val coordM = Regex("coordinates\\s*\\{([^}]*)\\}").find(body)
            if (exprM != null) {
                val expr = exprM.groupValues[1].trim()
                val n = 100
                for (i in 0..n) {
                    val x = xmin + (xmax - xmin) * i / n
                    val y = evalExpr(expr, x)
                    if (!y.isNaN()) data.add(x to y)
                }
            } else if (coordM != null) {
                Regex("\\(\\s*(-?[0-9.]+)\\s*,\\s*(-?[0-9.]+)\\s*\\)").findAll(coordM.groupValues[1])
                    .forEach { m ->
                        data.add(m.groupValues[1].toFloat() to m.groupValues[2].toFloat())
                    }
            }
            if (data.size >= 2) plots.add(data to (color to dashed).let { it.first to it.second })
        }

        // 计算画布尺寸
        val w = 520f
        val h = 380f
        val padL = 60f; val padR = 24f; val padT = 24f; val padB = 54f
        val pw = w - padL - padR
        val ph = h - padT - padB
        val sx = pw / (xmax - xmin)
        val sy = ph / (ymax - ymin)
        fun X(x: Float) = padL + (x - xmin) * sx
        fun Y(y: Float) = padT + ph - (y - ymin) * sy

        // 网格与坐标轴
        val axisPaint = 0xFF555555.toInt()
        if (grid) {
            val steps = 10
            for (i in 0..steps) {
                val x = padL + pw * i / steps
                ctx.ops.add(Op.Poly(listOf(x to padT, x to padT + ph), false, 0xFFDDDDDD.toInt(), 1f, true))
            }
            for (i in 0..steps) {
                val y = padT + ph * i / steps
                ctx.ops.add(Op.Poly(listOf(padL to y, padL + pw to y), false, 0xFFDDDDDD.toInt(), 1f, true))
            }
        }
        ctx.ops.add(Op.Poly(listOf(padL to padT, padL to padT + ph), false, axisPaint, 1.8f, false))
        ctx.ops.add(Op.Poly(listOf(padL to padT + ph, padL + pw to padT + ph), false, axisPaint, 1.8f, false))
        // 刻度
        val steps = 5
        for (i in 0..steps) {
            val xv = xmin + (xmax - xmin) * i / steps
            val px = X(xv)
            ctx.ops.add(Op.Poly(listOf(px to padT + ph, px to padT + ph + 5f), false, axisPaint, 1.5f, false))
            ctx.ops.add(
                Op.Text(px, padT + ph + 18f, trimNum(xv), "", axisPaint.toInt(), 11f)
            )
            val yv = ymin + (ymax - ymin) * i / steps
            val py = Y(yv)
            ctx.ops.add(Op.Poly(listOf(padL - 5f to py, padL to py), false, axisPaint, 1.5f, false))
            ctx.ops.add(
                Op.Text(padL - 10f, py + 4f, trimNum(yv), "right", axisPaint.toInt(), 11f)
            )
        }
        if (xlabel.isNotEmpty()) {
            ctx.ops.add(Op.Text(padL + pw / 2f, h - 14f, xlabel, "", axisPaint.toInt(), 12f))
        }
        if (ylabel.isNotEmpty()) {
            ctx.ops.add(Op.Text(16f, padT + ph / 2f, ylabel, "", axisPaint.toInt(), 12f))
        }

        // 曲线
        for ((data, style) in plots) {
            val pts = data.map { X(it.first) to Y(it.second) }
            ctx.ops.add(Op.Poly(pts, false, style.first, 2.5f, style.second))
        }
    }

    private fun trimNum(v: Float): String =
        if (abs(v - kotlin.math.round(v)) < 1e-4f) kotlin.math.round(v).toString()
        else String.format("%.1f", v)

    // ---------- 表达式求值（pgfplots \addplot {..}） ----------

    /** 支持 + - * / ^、括号、x、数字、pi/e、sin cos tan sqrt abs exp ln log */
    fun evalExpr(src: String, x: Float): Float {
        return try {
            val p = ExprParser(src.replace(" ", ""), x)
            p.parse()
        } catch (e: Exception) {
            Float.NaN
        }
    }

    private class ExprParser(private val s: String, private val x: Float) {
        private var i = 0
        fun parse(): Float {
            val v = parseExpr()
            return v
        }
        private fun peek(): Char? = if (i < s.length) s[i] else null
        private fun parseExpr(): Float {
            var v = parseTerm()
            while (peek() == '+' || peek() == '-') {
                val op = s[i++]
                val r = parseTerm()
                v = if (op == '+') v + r else v - r
            }
            return v
        }
        private fun parseTerm(): Float {
            var v = parsePower()
            while (peek() == '*' || peek() == '/') {
                val op = s[i++]
                val r = parsePower()
                v = if (op == '*') v * r else if (r == 0f) Float.NaN else v / r
            }
            return v
        }
        private fun parsePower(): Float {
            val base = parseAtom()
            if (peek() == '^') {
                i++
                val e = parsePower()
                return base.toDouble().pow(e.toDouble()).toFloat()
            }
            return base
        }
        private fun parseAtom(): Float {
            val c = peek() ?: return 0f
            if (c == '(') {
                i++
                val v = parseExpr()
                if (peek() == ')') i++
                return v
            }
            if (c == '-') { i++; return -parseAtom() }
            if (c == '+') { i++; return parseAtom() }
            if (c.isDigit() || c == '.') {
                val st = i
                while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
                return s.substring(st, i).toFloatOrNull() ?: 0f
            }
            if (c.isLetter()) {
                val st = i
                while (i < s.length && s[i].isLetter()) i++
                val name = s.substring(st, i)
                return when (name) {
                    "x" -> x
                    "pi" -> Math.PI.toFloat()
                    "e" -> Math.E.toFloat()
                    else -> {
                        // 函数
                        var arg = 0f
                        if (peek() == '(') {
                            i++
                            arg = parseExpr()
                            if (peek() == ')') i++
                        } else {
                            arg = parseAtom()
                        }
                        when (name) {
                            "sin" -> sin(arg)
                            "cos" -> cos(arg)
                            "tan" -> kotlin.math.tan(arg)
                            "sqrt" -> kotlin.math.sqrt(arg)
                            "abs" -> abs(arg)
                            "exp" -> kotlin.math.exp(arg)
                            "ln" -> kotlin.math.ln(arg)
                            "log" -> kotlin.math.log10(arg)
                            else -> Float.NaN
                        }
                    }
                }
            }
            i++
            return Float.NaN
        }
    }

    // ---------- 输出 ----------

    private data class Box(val minX: Float, val minY: Float, val maxX: Float, val maxY: Float) {
        fun size(margin: Float) = Size(maxX - minX + margin * 2, maxY - minY + margin * 2)
    }

    private fun box(ctx: Ctx): Box {
        if (ctx.ops.isEmpty()) return Box(0f, 0f, UNIT * 2, UNIT * 2)
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        fun upd(x: Float, y: Float, pad: Float = 0f) {
            minX = min(minX, x - pad); maxX = max(maxX, x + pad)
            minY = min(minY, y - pad); maxY = max(maxY, y + pad)
        }
        for (op in ctx.ops) when (op) {
            is Op.Poly -> op.pts.forEach { upd(it.first, it.second) }
            is Op.Circle -> { upd(op.c.first - op.r, op.c.second - op.r); upd(op.c.first + op.r, op.c.second + op.r) }
            is Op.Rect -> { upd(op.x, op.y); upd(op.x + op.w, op.y + op.h) }
            is Op.Text -> upd(op.x, op.y, 10f)
            is Op.Arc -> { upd(op.c.first - op.r, op.c.second - op.r); upd(op.c.first + op.r, op.c.second + op.r) }
            is Op.RightAngle -> { upd(op.p1.first, op.p1.second); upd(op.v.first, op.v.second); upd(op.p2.first, op.p2.second) }
        }
        if (minX > maxX) { minX = 0f; maxX = UNIT * 2 }
        if (minY > maxY) { minY = 0f; maxY = UNIT * 2 }
        return Box(minX, minY, maxX, maxY)
    }

    /** 只计算图形尺寸（用于先建画布再绘制） */
    fun tikzSize(src: String): Size {
        val ctx = Ctx(); parseTikz(src, ctx)
        return box(ctx).size(MARGIN)
    }

    fun pgfplotsSize(src: String): Size = Size(520f, 380f)

    private fun emit(ctx: Ctx, out: DrawOps): Size {
        val margin = MARGIN
        val b = box(ctx)
        out.translate(-b.minX + margin, -b.minY + margin)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.SERIF
            textAlign = Paint.Align.CENTER
        }
        for (op in ctx.ops) when (op) {
            is Op.Poly -> {
                val ops = ArrayList<PathOp>()
                op.pts.forEachIndexed { idx, p ->
                    if (idx == 0) ops.add(PathOp.MoveTo(p.first, p.second))
                    else ops.add(PathOp.LineTo(p.first, p.second))
                }
                if (op.closed) ops.add(PathOp.LineTo(op.pts[0].first, op.pts[0].second))
                out.drawPath(ops, makePaint(op.color, op.width, op.dashed, false))
            }
            is Op.Circle -> {
                val ops = ArrayList<PathOp>()
                ops.add(PathOp.MoveTo(op.c.first + op.r, op.c.second))
                ops.add(PathOp.CubicTo(op.c.first + op.r, op.c.second + op.r * 0.55f, op.c.first + op.r * 0.55f, op.c.second + op.r, op.c.first, op.c.second + op.r))
                ops.add(PathOp.CubicTo(op.c.first - op.r * 0.55f, op.c.second + op.r, op.c.first - op.r, op.c.second + op.r * 0.55f, op.c.first - op.r, op.c.second))
                ops.add(PathOp.CubicTo(op.c.first - op.r, op.c.second - op.r * 0.55f, op.c.first - op.r * 0.55f, op.c.second - op.r, op.c.first, op.c.second - op.r))
                ops.add(PathOp.CubicTo(op.c.first + op.r * 0.55f, op.c.second - op.r, op.c.first + op.r, op.c.second - op.r * 0.55f, op.c.first + op.r, op.c.second))
                out.drawPath(ops, makePaint(op.color, op.width, op.dashed, false))
            }
            is Op.Rect -> {
                val ops = listOf(
                    PathOp.MoveTo(op.x, op.y), PathOp.LineTo(op.x + op.w, op.y),
                    PathOp.LineTo(op.x + op.w, op.y + op.h), PathOp.LineTo(op.x, op.y + op.h),
                    PathOp.LineTo(op.x, op.y)
                )
                out.drawPath(ops, makePaint(op.color, op.width, op.dashed, false))
            }
            is Op.Text -> {
                textPaint.color = op.color
                textPaint.textSize = op.size * ctx.scale * 1.6f
                val dx = when {
                    op.anchor.startsWith("right") -> -textPaint.measureText(op.text) - 6f
                    op.anchor.startsWith("left") -> 6f
                    else -> -textPaint.measureText(op.text) / 2f
                }
                val dy = when {
                    op.anchor.startsWith("above") -> -8f
                    op.anchor.startsWith("below") -> textPaint.textSize + 6f
                    else -> textPaint.textSize / 3f
                }
                out.drawText(op.text, op.x + dx, op.y + dy, textPaint)
            }
            is Op.Arc -> {
                val ops = ArrayList<PathOp>()
                val steps = 40
                var prev = op.a1
                ops.add(PathOp.MoveTo(op.c.first + op.r * cos(op.a1), op.c.second + op.r * sin(op.a1)))
                for (k in 1..steps) {
                    val a = op.a1 + (op.a2 - op.a1) * k / steps
                    ops.add(PathOp.LineTo(op.c.first + op.r * cos(a), op.c.second + op.r * sin(a)))
                    prev = a
                }
                out.drawPath(ops, makePaint(op.color, op.width, false, false))
            }
            is Op.RightAngle -> {
                val v1 = norm(op.p1.first - op.v.first, op.p1.second - op.v.second)
                val v2 = norm(op.p2.first - op.v.first, op.p2.second - op.v.second)
                val a = op.v.first + v1.first * op.size to op.v.second + v1.second * op.size
                val b = op.v.first + v1.first * op.size + v2.first * op.size to
                    op.v.second + v1.second * op.size + v2.second * op.size
                val c = op.v.first + v2.first * op.size to op.v.second + v2.second * op.size
                val ops = listOf(
                    PathOp.MoveTo(a.first, a.second), PathOp.LineTo(b.first, b.second),
                    PathOp.LineTo(c.first, c.second)
                )
                out.drawPath(ops, makePaint(op.color, op.width, false, false))
            }
        }
        return b.size(margin)
    }

    private fun makePaint(color: Int, width: Float, dashed: Boolean, fill: Boolean): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            this.style = if (fill) Paint.Style.FILL else Paint.Style.STROKE
            this.strokeWidth = width
            if (dashed) pathEffect = android.graphics.DashPathEffect(floatArrayOf(12f, 8f), 0f)
        }

    private fun norm(x: Float, y: Float): Pair<Float, Float> {
        val l = kotlin.math.sqrt(x * x + y * y)
        return if (l == 0f) 0f to 0f else (x / l) to (y / l)
    }

    private object Angle {
        /** 取两条射线夹角的角平分线方向（弧度） */
        fun bisector(a1: Float, a2: Float): Float {
            var d = a2 - a1
            while (d > kotlin.math.PI) d -= 2f * kotlin.math.PI.toFloat()
            while (d < -kotlin.math.PI) d += 2f * kotlin.math.PI.toFloat()
            return a1 + d / 2f
        }
    }

    // ---------- 通用解析工具 ----------

    private fun parseOptions(o: String, ctx: Ctx) {
        Regex("scale\\s*=\\s*([0-9.]+)").find(o)?.let {
            ctx.scale = it.groupValues[1].toFloatOrNull() ?: ctx.scale
        }
        Regex("x\\s*=\\s*([0-9.]+)\\s*(cm|mm|pt)?").find(o)?.let {
            ctx.unit = (it.groupValues[1].toFloatOrNull() ?: 1f) * UNIT
        }
        if (o.contains("dashed")) ctx.dashed = true
        if (o.contains("dotted")) ctx.dashed = true
        Regex("(very |ultra )?thick").find(o)?.let { ctx.width = 3f }
        Regex("color\\s*=\\s*([A-Za-z]+)").find(o)?.let {
            namedColors[it.groupValues[1]]?.let { c -> ctx.stroke = c }
        }
        Regex("draw\\s*=\\s*([A-Za-z]+)").find(o)?.let {
            namedColors[it.groupValues[1]]?.let { c -> ctx.stroke = c }
        }
    }

    private fun parseNodeOptions(o: String): Pair<String, Float> {
        val anchor = Regex("(above|below|left|right|above left|above right|below left|below right|midway)")
            .find(o)?.groupValues?.get(1) ?: ""
        val pos = Regex("pos\\s*=\\s*([0-9.]+)").find(o)?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
        return anchor to pos
    }

    private fun anchorOffset(anchor: String, pos: Float): Pair<Float, Float> {
        val d = 6f
        var dx = 0f; var dy = 0f
        if (anchor.contains("above")) dy -= d
        if (anchor.contains("below")) dy += d
        if (anchor.contains("left")) dx -= d
        if (anchor.contains("right")) dx += d
        return dx to dy
    }

    /** 解析坐标：`(x,y)`、`(name)`、`x,y` */
    private fun parsePoint(s: String, ctx: Ctx): Pair<Float, Float>? {
        val t = s.trim()
        val inner = if (t.startsWith("(") && t.endsWith(")")) t.substring(1, t.length - 1) else t
        val nameM = Regex("^([A-Za-z][A-Za-z0-9_]*)$").find(inner.trim())
        if (nameM != null) {
            val n = nameM.groupValues[1]
            ctx.coords[n]?.let { return it }
            // 可能是极坐标 (angle:radius)
            return null
        }
        val parts = inner.split(",")
        if (parts.size == 2) {
            val x = num(parts[0], ctx) ?: return null
            val y = num(parts[1], ctx) ?: return null
            return x to y
        }
        return null
    }

    private fun num(s: String, ctx: Ctx): Float? {
        val t = s.trim()
        val m = Regex("^(-?[0-9.]+)\\s*(cm|mm|pt|in)?$").find(t) ?: return null
        val v = m.groupValues[1].toFloatOrNull() ?: return null
        val unitMul = when (m.groupValues[2]) {
            "mm" -> 0.1f; "pt" -> 0.035f; "in" -> 2.54f; else -> 1f
        }
        return v * ctx.unit * ctx.scale * unitMul
    }

    /** 按顶层 `;` 切分命令（忽略花括号内部） */
    private fun splitCommands(s: String): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        var depth = 0
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                sb.append(c).append(s[i + 1]); i += 2; continue
            }
            when (c) {
                '{' -> { depth++; sb.append(c) }
                '}' -> { depth--; sb.append(c) }
                ';' -> {
                    if (depth <= 0) { out.add(sb.toString()); sb.setLength(0) }
                    else sb.append(c)
                }
                else -> sb.append(c)
            }
            i++
        }
        if (sb.isNotEmpty()) out.add(sb.toString())
        return out
    }

    /** 把节点标签里简单的 LaTeX 转成纯文本 */
    fun simpleText(latex: String): String {
        var s = latex.trim()
        s = s.removePrefix("$").removeSuffix("$")
        val sb = StringBuilder()
        var i = 0
        var script = false // 上标 / 下标里的命令（^\circ → °）
        while (i < s.length) {
            val c = s[i]
            if (c == '\\') {
                if (i + 1 >= s.length) break
                if (s[i + 1] == '\\') { sb.append('\n'); i += 2; continue }
                var j = i + 1
                while (j < s.length && s[j].isLetter()) j++
                val name = s.substring(i + 1, j)
                val disp = if (script && name == "circ") "°" else CMD_DISPLAY["\\$name"]
                script = false
                if (disp != null) {
                    sb.append(disp)
                    i = j
                    // 函数名后面跟参数时，尝试把参数也带上
                    if (name in setOf("sqrt")) {
                        if (i < s.length && s[i] == '{') {
                            val close = s.indexOf('}', i)
                            if (close > 0) { sb.append(s.substring(i + 1, close)); i = close + 1 }
                        }
                    }
                    continue
                }
                sb.append(' ')
                i = j
                continue
            }
            if (c == '{' || c == '}') { i++; continue }
            if (c == '^' || c == '_') { i++; script = true; continue }
            sb.append(c); i++
        }
        return sb.toString().replace(" ", "").trim()
    }
}
