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

    /** 读环境名（字母 / 数字 / *，用于 \begin /\end） */
    private fun readEnvName(): String {
        val start = i
        while (i < s.length && (s[i].isLetterOrDigit() || s[i] == '*')) i++
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
    private fun parseSeq(
        g: MathNode.Group,
        stopAtRight: Boolean,
        stopAtEnd: Boolean,
        envEnd: String? = null,
        stopAtBracket: Boolean = false
    ) {
        while (i < s.length) {
            val c = s[i]
            when {
                c == '}' -> return
                c == ']' -> {
                    // \sqrt[..] 的次数括号：遇到 ] 停止（不消费）
                    if (stopAtBracket) return
                    appendChar(g, c); i++
                }
                c == '{' -> {
                    // 独立花括号组：展平到当前容器
                    i++
                    parseSeq(g, stopAtRight = false, stopAtEnd = false, envEnd = envEnd)
                    skipWs()
                    if (peek() == '}') i++
                }
                c == '^' || c == '_' -> {
                    val kind = c
                    i++
                    val arg = parseArg()
                    attachScript(g, kind, arg)
                }
                c == '&' -> {
                    // 透明环境（align 等）里 & 只是对齐符，直接忽略
                    if (envEnd != null) i++ else return
                }
                c == '\\' -> {
                    i++
                    if (peek() == '\\') {
                        i++
                        if (stopAtRight || stopAtEnd) return
                        g.nodes.add(MathNode.NewLine()); continue
                    }
                    // 单字符命令：\, \; \: \! \（空格） ~
                    val nx = peek()
                    if (nx != null && !nx.isLetter()) {
                        i++
                        when (nx) {
                            ',', ';', ':' -> g.nodes.add(MathNode.Text(" "))
                            '!', ' ' -> { /* 负空格 / 空格：不产生节点 */ }
                            '~' -> g.nodes.add(MathNode.Text(" "))
                            else -> g.nodes.add(MathNode.Cmd("\\$nx"))
                        }
                        continue
                    }
                    val name = readName()
                    if (name == "right") {
                        if (stopAtRight) {
                            // 定界符不在这里消费，交给 \left 分支读取
                            return
                        }
                        g.nodes.add(MathNode.Raw("\\right"))
                    } else if (name == "end") {
                        if (stopAtEnd) return
                        skipWs()
                        var envName = ""
                        if (peek() == '{') {
                            i++
                            envName = readEnvName()
                            skipWs()
                            if (peek() == '}') i++
                        }
                        // 透明环境结束：内容已解析完毕
                        if (envEnd != null && envName == envEnd) return
                        // 其余 \end 一律忽略（不再显示为 Raw）
                    } else {
                        dispatchCommand(g, name)
                    }
                }
                c == '%' -> {
                    // LaTeX 注释：跳过到行尾
                    while (i < s.length && s[i] != '\n') i++
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

    /** 跳过一个配平的 {...} 或 [...] 组（用于宏定义等） */
    private fun skipBalanced() {
        skipWs()
        val open = peek() ?: return
        if (open != '{' && open != '[') return
        val close = if (open == '{') '}' else ']'
        i++
        var depth = 1
        while (i < s.length && depth > 0) {
            val c = s[i]
            if (c == open) depth++
            else if (c == close) depth--
            i++
        }
    }

    /** 把 [readDelim] 的结果转成可直接显示的字符 */
    private fun normalizeDelim(d: String): String = when (d) {
        "\\{" -> "{"
        "\\}" -> "}"
        "\\|" -> "|"
        "\\Vert" -> "‖"
        "\\langle" -> "⟨"
        "\\rangle" -> "⟩"
        "." -> ""
        else -> d
    }

    // ---------- 命令分发 ----------

    private fun dispatchCommand(g: MathNode.Group, name: String) {
        when (name) {
            "frac", "dfrac", "tfrac", "cfrac" -> {
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
                    parseSeq(d, stopAtRight = false, stopAtEnd = false, stopAtBracket = true)
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
                    val env = readEnvName()
                    skipWs()
                    if (peek() == '}') i++
                    // array 的列规格 {cc} 直接跳过
                    if (env == "array") {
                        skipWs()
                        if (peek() == '{') {
                            i++
                            var depth = 1
                            while (i < s.length && depth > 0) {
                                when (s[i]) {
                                    '{' -> depth++
                                    '}' -> depth--
                                }
                                i++
                            }
                        }
                    }
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
            "text", "mathrm", "mathbf", "mathit", "operatorname",
            "mathbb", "mathcal", "mathfrak", "mathscr", "mathsf", "mathtt",
            "boldsymbol", "textbf", "textnormal", "textrm" -> {
                // 文本/字体命令：直接取参数内容，保留为文本
                val arg = parseArg()
                g.nodes.addAll(arg)
            }
            "quad", "qquad" -> g.nodes.add(MathNode.Text(" "))
            "hspace", "vspace", "hskip", "vskip", "kern", "mkern" -> {
                // 间距命令：长度参数直接丢弃
                parseArg()
            }
            "displaystyle", "textstyle", "scriptstyle", "scriptscriptstyle",
            "limits", "nolimits", "phantom", "hphantom", "vphantom",
            "color", "pagecolor" -> {
                // 样式 / 占位命令：忽略（若带参数则消费）
                if (peek() == '{') parseArg()
            }
            "textcolor", "colorbox" -> {
                parseArg() // 颜色
                val arg = parseArg()
                g.nodes.addAll(arg)
            }
            "fcolorbox" -> {
                parseArg(); parseArg() // 框色 + 底色
                val arg = parseArg()
                g.nodes.addAll(arg)
            }
            "binom", "dbinom", "tbinom" -> {
                val num = MathNode.Group(parseArg().toMutableList())
                val den = MathNode.Group(parseArg().toMutableList())
                val inner = MathNode.Group(mutableListOf<MathNode>(MathNode.Fraction(num, den)))
                g.nodes.add(MathNode.Paren(inner, "(", ")"))
            }
            "boxed", "cancel", "substack", "overbrace", "underbrace",
            "mathop", "mathrel", "mathbin", "mathord", "mathopen",
            "mathclose", "mathpunct" -> {
                // 取内容（装饰 / 盒子效果不绘制）
                val arg = parseArg()
                g.nodes.addAll(arg)
            }
            "big", "Big", "bigg", "Bigg", "bigl", "bigr", "Bigl", "Bigr",
            "biggl", "biggr", "Biggl", "Biggr" -> {
                // 放大定界符：只取符号本身
                skipWs()
                val d = readDelim()
                g.nodes.add(MathNode.Text(normalizeDelim(d)))
            }
            "middle" -> {
                // \middle| 之类的伸缩定界符
                skipWs()
                g.nodes.add(MathNode.Text(normalizeDelim(readDelim())))
            }
            "hline", "smallskip", "medskip", "bigskip", "noindent", "indent",
            "allowbreak", "nobreak", "strut", "mathstrut" -> {
                // 排版 / 撑高命令：忽略
            }
            "cline" -> { if (peek() == '{') parseArg() }
            "multicolumn" -> { repeat(3) { if (peek() == '{') parseArg() } }
            "genfrac" -> { repeat(5) { if (peek() == '{') skipBalanced() } }
            "sideset", "prescript", "tensor" -> { repeat(3) { if (peek() == '{') skipBalanced() } }
            "mathversion" -> { if (peek() == '{') skipBalanced() }
            "newcommand", "renewcommand", "newcommand*", "renewcommand*",
            "DeclareMathOperator", "DeclareMathOperator*",
            "newenvironment", "renewenvironment", "def", "let" -> {
                // 宏定义：跳过参数与定义体，不把定义体当公式内容
                var guard = 0
                while (guard++ < 8) {
                    skipWs()
                    when (peek()) {
                        '\\' -> { i++; readName() }
                        '{', '[' -> skipBalanced()
                        else -> break
                    }
                }
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

    private val MATRIX_ENVS =
        setOf("pmatrix", "bmatrix", "vmatrix", "matrix", "Bmatrix", "smallmatrix", "array")

    private fun parseEnvironment(g: MathNode.Group, env: String) {
        if (env in MATRIX_ENVS) {
            val rows = mutableListOf<MutableList<MathNode.Group>>()
            var curRow = mutableListOf<MathNode.Group>()
            var cell = MathNode.Group()
            while (true) {
                val start = i
                parseSeq(cell, stopAtRight = false, stopAtEnd = true)
                // parseSeq 会消费掉 \\ 行分隔符或 \end 的命令名，用消费过的内容判断是哪种
                val consumed = s.substring(start, i)
                when {
                    consumed.endsWith("\\end") -> {
                        consumeEnd(env) // 补上 {env}
                        curRow.add(cell)
                        rows.add(curRow)
                        break
                    }
                    consumed.endsWith("\\\\") -> {
                        // 行分隔符（已消费）
                        curRow.add(cell)
                        rows.add(curRow)
                        curRow = mutableListOf()
                        cell = MathNode.Group()
                    }
                    peek() == '&' -> {
                        i++
                        curRow.add(cell)
                        cell = MathNode.Group()
                    }
                    else -> {
                        // } 或字符串结束
                        curRow.add(cell)
                        rows.add(curRow)
                        break
                    }
                }
            }
            g.nodes.add(MathNode.Matrix(env, rows))
        } else if (env == "cases") {
            val rows = mutableListOf<MathNode.Group>()
            var row = MathNode.Group()
            while (true) {
                val start = i
                parseSeq(row, stopAtRight = false, stopAtEnd = true)
                val consumed = s.substring(start, i)
                when {
                    consumed.endsWith("\\end") -> {
                        consumeEnd(env)
                        rows.add(row)
                        break
                    }
                    consumed.endsWith("\\\\") -> {
                        // 行分隔符（已消费）
                        rows.add(row)
                        row = MathNode.Group()
                    }
                    peek() == '&' -> { i++ } // 对齐符忽略
                    else -> {
                        rows.add(row)
                        break
                    }
                }
            }
            g.nodes.add(MathNode.Cases(rows))
        } else {
            // 透明环境（equation / align / gather / aligned 及未知环境）：
            // 不显示 \begin / \end，内容（含 & 对齐符）由 parseSeq 继续解析
            parseSeq(g, stopAtRight = false, stopAtEnd = false, envEnd = env)
        }
    }

    private fun consumeEnd(env: String) {
        skipWs()
        if (peek() == '{') i++
        val n = readEnvName()
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
            // 扩展：常见关系 / 逻辑 / 几何符号（手写 LaTeX 常用）
            addAll(listOf(
                "nsupset", "notni", "nparallel", "nmid", "nvdash", "vdash", "models",
                "preceq", "succeq", "npreceq", "nsucceq", "gtrapprox", "lessapprox",
                "triangleq", "eqcirc", "circeq", "barwedge", "boxdot", "boxtimes",
                "diamond", "bigstar", "lozenge", "blacklozenge",
                "flat", "natural", "sharp", "measuredangle", "sphericalangle",
                "aleph", "beth", "nsim", "simneq", "asymp", "doteqdot",
                "lessgtr", "gtrless", "between"
            ))
            // 扩展：定界符
            addAll(listOf(
                "lceil", "rceil", "lfloor", "rfloor",
                "lVert", "rVert", "lvert", "rvert",
                "lbrace", "rbrace", "lbrack", "rbrack", "lparen", "rparen"
            ))
            // 扩展：算术 / 集合运算
            addAll(listOf(
                "sqcup", "sqcap", "dotplus", "oslash", "bigcirc",
                "smallsetminus", "leftthreetimes", "rightthreetimes"
            ))
            // 扩展：关系
            addAll(listOf(
                "curlyeqprec", "curlyeqsucc", "preccurlyeq", "succcurlyeq",
                "precsim", "succsim", "Bumpeq", "bumpeq",
                "risingdotseq", "fallingdotseq", "backsimeq", "approxeq",
                "thicksim", "thickapprox", "smallsmile", "smallfrown",
                "trianglelefteq", "trianglerighteq", "vDash", "Vdash", "dashv"
            ))
            // 扩展：几何 / 杂项
            addAll(listOf(
                "triangleleft", "triangleright", "surd", "checkmark",
                "degree", "ohm", "copyright", "pounds", "euro", "yen",
                "colon", "cdotp", "hslash", "clubsuit", "heartsuit", "spadesuit"
            ))
        }
    }
}
