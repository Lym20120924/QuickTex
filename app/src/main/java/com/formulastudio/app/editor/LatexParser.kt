package com.formulastudio.app.editor

/**
 * LaTeX → 结构树 解析器（覆盖符号键盘所能表达的全部命令）。
 *
 * 原则：键盘符号库覆盖的命令 100% 还原；库外的未知命令保留为 [MathNode.Raw] 原样显示。
 */
class LatexParser {

    private var s = ""
    private var i = 0

    fun parse(src: String): MathNode.Group {
        s = src
        i = 0
        val g = MathNode.Group()
        parseSeq(g, stopAtRight = false, stopAtEnd = false)
        return g
    }

    // ---------- 词法 ----------

    private fun peek(): Char? = if (i < s.length) s[i] else null

    private fun skipWs() {
        while (i < s.length && s[i].isWhitespace()) i++
    }

    private fun readName(): String {
        val start = i
        while (i < s.length && (s[i].isLetterOrDigit())) i++
        return s.substring(start, i)
    }

    /** 读一个命令名（\ 之后） */
    private fun readCommandName(): String {
        val start = i
        if (i < s.length && s[i] == '\\') i++
        while (i < s.length && (s[i].isLetterOrDigit())) i++
        return s.substring(start, i)
    }

    // ---------- 主循环 ----------

    /**
     * 把一串 token 解析进 [g]。
     * 遇到 '}' 返回（不消费）；遇到 \\right 时若 stopAtRight 则消费并返回；
     * 遇到 \\end 时若 stopAtEnd 则返回。
     */
    private fun parseSeq(g: MathNode.Group, stopAtRight: Boolean, stopAtEnd: Boolean) {
        while (i < s.length) {
            val c = s[i]
            when {
                c == '}' -> return
                c == '{' -> {
                    // 独立花括号组：展平到当前容器
                    i++
                    parseSeq(g, stopAtRight = false, stopAtEnd = false)
                    skipWs()
                    if (peek() == '}') i++
                }
                c == '^' || c == '_' -> {
                    val kind = c
                    i++
                    val arg = parseArg()
                    attachScript(g, kind, arg)
                }
                c == '&' -> return
                c == '\\' -> {
                    i++
                    if (peek() == '\\') {
                        i++
                        if (stopAtRight || stopAtEnd) return
                        g.nodes.add(MathNode.NewLine()); continue
                    }
                    val name = readName()
                    if (name == "right") {
                        if (stopAtRight) {
                            skipWs()
                            readDelim()
                            return
                        }
                        g.nodes.add(MathNode.Raw("\\right"))
                    } else if (name == "end") {
                        if (stopAtEnd) return
                        g.nodes.add(MathNode.Raw("\\end"))
                    } else {
                        dispatchCommand(g, name)
                    }
                }
                c.isWhitespace() -> i++
                else -> {
                    appendChar(g, c)
                    i++
                }
            }
        }
    }

    private fun appendChar(g: MathNode.Group, c: Char) {
        // 每个字符独立 Text 节点（不合并，避免多字符 Text 导致光标问题）
        g.nodes.add(MathNode.Text(c.toString()))
    }

    /** 解析一个“参数”：{...} 或单个 token */
    private fun parseArg(): List<MathNode> {
        skipWs()
        if (peek() == '{') {
            i++
            val g = MathNode.Group()
            parseSeq(g, stopAtRight = false, stopAtEnd = false)
            skipWs()
            if (peek() == '}') i++
            return g.nodes
        }
        if (i >= s.length) return emptyList()
        val c = s[i]
        if (c == '\\') {
            i++
            val name = readName()
            return if (name in SYMBOL_COMMANDS) listOf(MathNode.Cmd("\\$name"))
            else listOf(MathNode.Raw("\\$name"))
        }
        if (c == '{' || c == '}' || c == '^' || c == '_') return emptyList()
        i++
        return listOf(MathNode.Text(c.toString()))
    }

    /** 把上/下标附着到最后一个节点 */
    private fun attachScript(g: MathNode.Group, kind: Char, arg: List<MathNode>) {
        val content = MathNode.Group(arg.toMutableList())
        val lastIdx = g.nodes.size - 1
        if (lastIdx < 0) {
            g.nodes.add(MathNode.Script(MathNode.Text(""), if (kind == '^') content else null, if (kind == '_') content else null))
            return
        }
        val last = g.nodes[lastIdx]
        if (last is MathNode.Script) {
            if (kind == '^') {
                if (last.sup == null) last.sup = content
                else replaceWithWrapped(g, lastIdx, last, content, kind)
            } else {
                if (last.sub == null) last.sub = content
                else replaceWithWrapped(g, lastIdx, last, content, kind)
            }
            return
        }
        g.nodes[lastIdx] = MathNode.Script(last, if (kind == '^') content else null, if (kind == '_') content else null)
    }

    private fun replaceWithWrapped(
        g: MathNode.Group,
        idx: Int,
        old: MathNode.Script,
        content: MathNode.Group,
        kind: Char
    ) {
        val w = MathNode.Script(old, null, null)
        if (kind == '^') w.sup = content else w.sub = content
        g.nodes[idx] = w
    }

    private fun readDelim(): String {
        skipWs()
        if (i >= s.length) return "."
        val c = s[i]
        if (c == '\\') {
            i++
            val n = readName()
            return when (n) {
                "langle" -> "\\langle"
                "rangle" -> "\\rangle"
                "vert" -> "|"
                "Vert" -> "\\Vert"
                "lbrace" -> "\\{"
                "rbrace" -> "\\}"
                else -> "."
            }
        }
        i++
        return when (c) {
            '(' -> "("
            '[' -> "["
            '{' -> "\\{"
            '|' -> "|"
            '.' -> "."
            else -> c.toString()
        }
    }

    // ---------- 命令分发 ----------

    private fun dispatchCommand(g: MathNode.Group, name: String) {
        when (name) {
            "frac", "dfrac", "tfrac" -> {
                val num = MathNode.Group(parseArg().toMutableList())
                val den = MathNode.Group(parseArg().toMutableList())
                g.nodes.add(MathNode.Fraction(num, den))
            }
            "sqrt" -> {
                var degree: MathNode.Group? = null
                skipWs()
                if (peek() == '[') {
                    i++
                    val d = MathNode.Group()
                    parseSeq(d, stopAtRight = false, stopAtEnd = false)
                    skipWs()
                    if (peek() == ']') i++
                    degree = d
                }
                val rad = MathNode.Group(parseArg().toMutableList())
                g.nodes.add(MathNode.Sqrt(rad, degree))
            }
            "ce" -> {
                val t = readCeBody()
                g.nodes.add(MathNode.Chem(t))
            }
            "begin" -> {
                skipWs()
                if (peek() == '{') {
                    i++
                    val env = readName()
                    skipWs()
                    if (peek() == '}') i++
                    parseEnvironment(g, env)
                }
            }
            "vec", "hat", "bar", "dot", "ddot", "overrightarrow", "overleftarrow",
            "widetilde", "widehat", "overline", "underline", "acute", "grave", "tilde" -> {
                val arg = MathNode.Group(parseArg().toMutableList())
                g.nodes.add(MathNode.Accent(arg, name))
            }
            "left" -> {
                skipWs()
                val l = readDelim()
                val inner = MathNode.Group()
                parseSeq(inner, stopAtRight = true, stopAtEnd = false)
                skipWs()
                val r = if (peek() == '\\') {
                    i++
                    readName()
                    readDelim()
                } else if (peek() == '}' || peek() == null) {
                    "."
                } else {
                    readDelim()
                }
                g.nodes.add(MathNode.Paren(inner, l, r))
            }
            "sum", "prod", "int", "iint", "iiint", "iiiint", "oint", "oiint", "oiiint",
            "bigcup", "bigcap", "bigoplus", "bigotimes", "bigodot", "coprod",
            "lim", "max", "min", "inf", "sup", "argmax", "argmin" -> {
                val isLim = name in setOf("lim", "max", "min", "inf", "sup", "argmax", "argmin")
                var under: MathNode.Group? = null
                var over: MathNode.Group? = null
                if (!isLim) {
                    // 大型运算符可带上下限
                    skipWs()
                    if (peek() == '_') { i++; under = MathNode.Group(parseArg().toMutableList()) }
                    skipWs()
                    if (peek() == '^') { i++; over = MathNode.Group(parseArg().toMutableList()) }
                } else {
                    // lim 等：_{...} 在下方，^{...} 在上方
                    skipWs()
                    if (peek() == '_') { i++; under = MathNode.Group(parseArg().toMutableList()) }
                    skipWs()
                    if (peek() == '^') { i++; over = MathNode.Group(parseArg().toMutableList()) }
                }
                g.nodes.add(MathNode.Op("\\$name", under, over))
            }
            "sin", "cos", "tan", "cot", "sec", "csc", "log", "ln", "lg", "exp",
            "det", "gcd", "arcsin", "arccos", "arctan", "sinh", "cosh", "tanh" -> {
                g.nodes.add(MathNode.Func(name))
            }
            "text", "mathrm", "mathbf", "mathit", "operatorname" -> {
                // 文本/正体/斜体：直接取参数内容，保留为文本
                val arg = parseArg()
                g.nodes.addAll(arg)
            }
            else -> {
                if (name in SYMBOL_COMMANDS) {
                    g.nodes.add(MathNode.Cmd("\\$name"))
                } else {
                    g.nodes.add(MathNode.Raw("\\$name"))
                }
            }
        }
    }

    /** 读取 \ce{...} 的原始内容（处理嵌套花括号） */
    private fun readCeBody(): String {
        skipWs()
        if (peek() != '{') return ""
        i++
        val sb = StringBuilder()
        var depth = 1
        while (i < s.length) {
            val c = s[i]
            when (c) {
                '{' -> { depth++; sb.append(c) }
                '}' -> {
                    depth--
                    if (depth == 0) { i++; return sb.toString() }
                    sb.append(c)
                }
                else -> sb.append(c)
            }
            i++
        }
        return sb.toString()
    }

    // ---------- 环境（矩阵 / cases） ----------

    private val MATRIX_ENVS = setOf("pmatrix", "bmatrix", "vmatrix", "matrix", "Bmatrix", "smallmatrix")

    private fun parseEnvironment(g: MathNode.Group, env: String) {
        if (env in MATRIX_ENVS) {
            val rows = mutableListOf<MutableList<MathNode.Group>>()
            var curRow = mutableListOf<MathNode.Group>()
            var cell = MathNode.Group()
            while (true) {
                parseSeq(cell, stopAtRight = false, stopAtEnd = true)
                when (peek()) {
                    '&' -> {
                        i++
                        curRow.add(cell)
                        cell = MathNode.Group()
                    }
                    '\\' -> {
                        // 行分隔符（已消费）
                        curRow.add(cell)
                        rows.add(curRow)
                        curRow = mutableListOf()
                        cell = MathNode.Group()
                        // 若后面紧跟 \end 由下方处理
                        skipWs()
                        if (peek() == '\\') {
                            i++
                            val n = readName()
                            if (n == "end") {
                                consumeEnd(env)
                                break
                            }
                            i += n.length // 不应发生
                        }
                    }
                    '}' -> {
                        curRow.add(cell)
                        rows.add(curRow)
                        return
                    }
                    else -> {
                        // \end 已由 parseSeq 消费
                        if (peek() == null) {
                            curRow.add(cell)
                            rows.add(curRow)
                            return
                        }
                        consumeEnd(env)
                        curRow.add(cell)
                        rows.add(curRow)
                        break
                    }
                }
            }
            g.nodes.add(MathNode.Matrix(env, rows))
        } else if (env == "cases" || env == "aligned" || env == "array") {
            val rows = mutableListOf<MathNode.Group>()
            var row = MathNode.Group()
            while (true) {
                parseSeq(row, stopAtRight = false, stopAtEnd = true)
                when (peek()) {
                    '&' -> { i++ } // 对齐符忽略
                    '\\' -> {
                        rows.add(row)
                        row = MathNode.Group()
                        skipWs()
                        if (peek() == '\\') {
                            i++
                            val n = readName()
                            if (n == "end") { consumeEnd(env); break }
                        }
                    }
                    '}' -> { rows.add(row); return }
                    else -> {
                        if (peek() == null) { rows.add(row); return }
                        consumeEnd(env)
                        rows.add(row)
                        break
                    }
                }
            }
            g.nodes.add(MathNode.Cases(rows))
        } else {
            // 未知环境：当普通组解析
            g.nodes.add(MathNode.Raw("\\begin{$env}"))
        }
    }

    private fun consumeEnd(env: String) {
        skipWs()
        if (peek() == '{') i++
        val n = readName()
        skipWs()
        if (peek() == '}') i++
    }

    companion object {
        /** 所有支持的命令符号（含希腊字母） */
        val SYMBOL_COMMANDS: Set<String> = buildSet {
            // 希腊小写
            addAll(listOf(
                "alpha", "beta", "gamma", "delta", "epsilon", "varepsilon",
                "zeta", "eta", "theta", "vartheta", "iota", "kappa",
                "lambda", "mu", "nu", "xi", "omicron", "pi", "varpi",
                "rho", "varrho", "sigma", "varsigma", "tau", "upsilon",
                "phi", "varphi", "chi", "psi", "omega"
            ))
            // 希腊大写
            addAll(listOf(
                "Gamma", "Delta", "Theta", "Lambda", "Xi", "Pi",
                "Sigma", "Upsilon", "Phi", "Psi", "Omega"
            ))
            // 基础运算与关系
            addAll(listOf(
                "times", "div", "pm", "mp", "cdot", "ast", "star", "bullet",
                "circ", "equiv", "approx", "propto", "sim", "simeq", "cong",
                "ne", "neq", "le", "ge", "ll", "gg", "geqq", "leqq",
                "in", "notin", "ni", "subset", "supset", "subseteq", "supseteq",
                "cup", "cap", "emptyset", "varnothing", "setminus", "bigcup", "bigcap"
            ))
            // 逻辑
            addAll(listOf("forall", "exists", "nexists", "neg", "wedge", "vee", "land", "lor", "lnot"))
            // 箭头
            addAll(listOf(
                "to", "rightarrow", "leftarrow", "leftrightarrow", "Rightarrow",
                "Leftarrow", "Leftrightarrow", "mapsto", "longrightarrow", "longleftarrow",
                "uparrow", "downarrow", "updownarrow", "nearrow", "searrow", "swarrow", "nwarrow"
            ))
            // 其他常用
            addAll(listOf(
                "infty", "partial", "nabla", "prime", "dots", "ldots", "cdots",
                "vdots", "ddots", "perp", "parallel", "angle", "triangle",
                "square", "blacksquare", "aleph", "hbar", "ell", "Re", "Im",
                "backslash", "not", "land", "lor"
            ))
            // 用户补充的 axMath 常用符号
            addAll(listOf(
                "doteq", "gg", "ll", "succ", "prec", "therefore", "because", "Join",
                "oplus", "ominus", "otimes", "odot", "circledast", "circledcirc",
                "uplus", "setminus", "subsetneq", "supsetneq", "Box", "bigtriangleup",
                "bot", "nless", "ngtr", "nsubseteq", "nsupseteq", "nprec", "nsucc",
                "nsubset", "ncong", "ltimes", "rtimes", "divideontimes", "mho",
                "textperthousand", "dag", "ddag", "eth", "imath", "jmath", "varkappa",
                "wp", "complement", "diamondsuit", "backepsilon", "varpropto",
                "smallsetminus", "blacktriangleright", "blacktriangleleft",
                "gets", "nleftarrow", "nrightarrow", "twoheadleftarrow",
                "twoheadrightarrow", "leftleftarrows", "rightrightarrows",
                "leftrightarrows", "rightleftarrows", "leftarrowtail", "rightarrowtail",
                "looparrowleft", "looparrowright", "curvearrowleft", "curvearrowright",
                "circlearrowleft", "circlearrowright", "leftharpoonup", "leftharpoondown",
                "rightharpoonup", "rightharpoondown", "leftrightsquigarrow",
                "nLeftarrow", "nRightarrow", "nLeftrightarrow", "longmapsto",
                "Longleftarrow", "Longrightarrow", "Longleftrightarrow",
                "implies", "impliedby", "iff"
            ))
        }
    }
}
