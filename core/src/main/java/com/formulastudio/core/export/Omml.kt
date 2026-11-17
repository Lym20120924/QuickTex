package com.formulastudio.core.export

/**
 * LaTeX 子集 → Word 原生公式（OMML，Office Math Markup Language）。
 *
 * 让导出的 docx 里的公式是**可编辑**的，而不是图片。
 * 只覆盖常用结构：分数 / 上下标 / 根号 / 分组 / 命令；
 * 解析失败时由调用方回退到位图嵌入。
 */
object Omml {

    fun convert(latex: String): String? = try {
        // 优先走解析树（覆盖全部节点类型）
        runCatching {
            val g = com.formulastudio.app.editor.LatexParser().parse(latex)
            val out = ommlFromNodes(g.nodes)
            if (out.isNotBlank()) return out
        }
        val p = Parser(latex)
        val body = p.parseExpr(null)
        if (p.pos < p.src.length && p.src[p.pos] !in "}\\") null
        else if (body.isBlank()) null
        else body
    } catch (e: Exception) {
        null
    }

    private class Parser(val src: String) {
        var pos = 0

        fun parseExpr(stop: Char?): String {
            val sb = StringBuilder()
            var lastElem: String? = null
            while (pos < src.length) {
                val c = src[pos]
                if (c == stop) break
                if (c == '}') break
                when {
                    c == '^' || c == '_' -> {
                        val isSup = c == '^'
                        pos++
                        val arg = parseArg()
                        val prev = lastElem
                        if (prev == null) {
                            sb.append(run(if (isSup) "ⁱ" else "ₓ"))
                        } else {
                            // 把上一个元素与上下标合并
                            val merged = mergeScript(prev, arg, isSup)
                            sb.setLength(sb.length - prev.length)
                            sb.append(merged)
                            lastElem = merged
                        }
                    }
                    c == '{' -> {
                        pos++
                        val inner = parseExpr('}')
                        if (pos < src.length && src[pos] == '}') pos++
                        lastElem = inner
                        sb.append(inner)
                    }
                    c == '\\' -> {
                        val (elem, raw) = parseCommand()
                        lastElem = elem
                        sb.append(elem)
                    }
                    c.isWhitespace() -> pos++
                    else -> {
                        // 连续普通字符合并成一个 run
                        val start = pos
                        while (pos < src.length && src[pos].let {
                                it != '^' && it != '_' && it != '{' && it != '}' &&
                                    it != '\\' && !it.isWhitespace()
                            }) pos++
                        val txt = src.substring(start, pos)
                        val elem = run(txt)
                        lastElem = elem
                        sb.append(elem)
                    }
                }
            }
            return sb.toString()
        }

        /** 解析一个参数：`{...}` 或单个字符/命令 */
        fun parseArg(): String {
            if (pos < src.length && src[pos] == '{') {
                pos++
                val inner = parseExpr('}')
                if (pos < src.length && src[pos] == '}') pos++
                return inner
            }
            if (pos < src.length && src[pos] == '\\') {
                val (elem, raw) = parseCommand()
                return elem
            }
            if (pos < src.length) {
                val elem = run(src[pos].toString())
                pos++
                return elem
            }
            return ""
        }

        fun parseCommand(): Pair<String, String> {
            pos++ // 跳过反斜杠
            val start = pos
            while (pos < src.length && src[pos].isLetter()) pos++
            val name = src.substring(start, pos)
            return when (name) {
                "frac", "dfrac", "tfrac" -> {
                    val num = parseArg()
                    val den = parseArg()
                    "<m:f><m:num>$num</m:num><m:den>$den</m:den></m:f>" to "\\frac"
                }
                "sqrt" -> {
                    // 可选的 [n]
                    var deg = ""
                    if (pos < src.length && src[pos] == '[') {
                        pos++
                        val s = pos
                        while (pos < src.length && src[pos] != ']') pos++
                        deg = src.substring(s, pos)
                        if (pos < src.length) pos++
                    }
                    val body = parseArg()
                    if (deg.isNotEmpty()) {
                        "<m:rad><m:radPr><m:degHide m:val=\"0\"/></m:radPr>" +
                            "<m:deg>${run(deg)}</m:deg><m:e>$body</m:e></m:rad>" to "\\sqrt"
                    } else {
                        "<m:rad><m:radPr><m:degHide m:val=\"1\"/></m:radPr>" +
                            "<m:deg/><m:e>$body</m:e></m:rad>" to "\\sqrt"
                    }
                }
                "text", "mathrm", "operatorname", "mathbf", "mathit" -> {
                    val body = parseArg()
                    "<m:r><m:t xml:space=\"preserve\">${esc(body)}</m:t></m:r>" to "\\$name"
                }
                "left", "right" -> {
                    if (pos < src.length) {
                        val d = src[pos].toString(); pos++
                        run(d) to "\\left"
                    } else "" to ""
                }
                "cdot", "times", "div", "pm", "mp", "le", "ge", "ne", "approx", "equiv",
                "infty", "partial", "nabla", "sum", "prod", "int", "lim", "alpha", "beta",
                "gamma", "delta", "epsilon", "theta", "lambda", "mu", "pi", "sigma", "phi",
                "omega", "Gamma", "Delta", "Theta", "Lambda", "Pi", "Sigma", "Phi", "Omega" -> {
                    run(sym(name)) to "\\$name"
                }
                "" -> {
                    // 转义字符，如 \{ \} \% \$ \# \& \_ \, \; \ 等
                    if (pos < src.length) {
                        val c = src[pos].toString(); pos++
                        run(c) to "\\"
                    } else "" to ""
                }
                else -> {
                    // 未知命令：原样输出命令名
                    run("\\$name") to "\\$name"
                }
            }
        }

        fun mergeScript(base: String, arg: String, isSup: Boolean): String = when {
            base.startsWith("<m:sSup>") -> base.replace("</m:sSup>", "<m:sup>$arg</m:sup></m:sSup>")
            base.startsWith("<m:sSub>") && isSup ->
                base.replace("<m:sub>", "<m:sub>").replace("</m:sSub>", "</m:sSub>")
                    .let { it.replace("<m:sSub>", "<m:sSubSup>").replace("</m:sSub>", "</m:sSubSup>")
                        .replace("</m:sub>", "</m:sub><m:sup>$arg</m:sup>") }
            base.startsWith("<m:sSub>") -> base.replace("</m:sSub>", "<m:sub>$arg</m:sub></m:sSub>")
            base.startsWith("<m:sSubSup>") -> base.replace("</m:sSubSup>",
                (if (isSup) "<m:sup>$arg</m:sup>" else "<m:sub>$arg</m:sub>") + "</m:sSubSup>")
            isSup -> "<m:sSup><m:e>$base</m:e><m:sup>$arg</m:sup></m:sSup>"
            else -> "<m:sSub><m:e>$base</m:e><m:sub>$arg</m:sub></m:sSub>"
        }

        fun run(text: String): String =
            "<m:r><m:t xml:space=\"preserve\">${esc(text)}</m:t></m:r>"
    }

    private fun esc(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun sym(name: String): String = when (name) {
        "cdot" -> "·"; "times" -> "×"; "div" -> "÷"; "pm" -> "±"; "mp" -> "∓"
        "le" -> "≤"; "ge" -> "≥"; "ne" -> "≠"; "approx" -> "≈"; "equiv" -> "≡"
        "infty" -> "∞"; "partial" -> "∂"; "nabla" -> "∇"; "sum" -> "∑"; "prod" -> "∏"
        "int" -> "∫"; "lim" -> "lim"
        "alpha" -> "α"; "beta" -> "β"; "gamma" -> "γ"; "delta" -> "δ"; "epsilon" -> "ε"
        "theta" -> "θ"; "lambda" -> "λ"; "mu" -> "μ"; "pi" -> "π"; "sigma" -> "σ"
        "phi" -> "φ"; "omega" -> "ω"
        "Gamma" -> "Γ"; "Delta" -> "Δ"; "Theta" -> "Θ"; "Lambda" -> "Λ"; "Pi" -> "Π"
        "Sigma" -> "Σ"; "Phi" -> "Φ"; "Omega" -> "Ω"
        else -> name
    }
}

// ===================== 基于解析树的完整转换 =====================

/**
 * 把 [com.formulastudio.app.editor.MathNode] 树转成完整 OMML。
 *
 * 覆盖编辑器的全部节点类型：分组 / 文本 / 换行 / 命令 / 原始 LaTeX /
 * 分数 / 根式 / 上下标 / 大型运算符 / 函数 / 括号 / 矩阵 / cases / 重音 / 化学式。
 */
fun ommlFromNodes(nodes: List<com.formulastudio.app.editor.MathNode>): String =
    nodes.joinToString("") { ommlNode(it) }

private fun ommlGroup(g: com.formulastudio.app.editor.MathNode.Group): String =
    g.nodes.joinToString("") { ommlNode(it) }

private fun ommlNode(n: com.formulastudio.app.editor.MathNode): String = when (n) {
    is com.formulastudio.app.editor.MathNode.Group -> ommlGroup(n)
    is com.formulastudio.app.editor.MathNode.Text -> ommlRun(n.text)
    is com.formulastudio.app.editor.MathNode.NewLine -> "<m:r><m:br/></m:r>"
    is com.formulastudio.app.editor.MathNode.Raw -> ommlRun(n.latex)
    is com.formulastudio.app.editor.MathNode.Cmd -> ommlRun(cmdSymbol(n.latex))
    is com.formulastudio.app.editor.MathNode.Func -> ommlRun(n.name)
    is com.formulastudio.app.editor.MathNode.Chem -> ommlRun(n.text)
    is com.formulastudio.app.editor.MathNode.Fraction ->
        "<m:f><m:num>${ommlGroup(n.num)}</m:num><m:den>${ommlGroup(n.den)}</m:den></m:f>"
    is com.formulastudio.app.editor.MathNode.Sqrt -> {
        val deg = n.degree
        if (deg != null && deg.nodes.isNotEmpty()) {
            "<m:rad><m:radPr><m:degHide m:val=\"0\"/></m:radPr>" +
                "<m:deg>${ommlGroup(deg)}</m:deg><m:e>${ommlGroup(n.radicand)}</m:e></m:rad>"
        } else {
            "<m:rad><m:radPr><m:degHide m:val=\"1\"/></m:radPr>" +
                "<m:deg/><m:e>${ommlGroup(n.radicand)}</m:e></m:rad>"
        }
    }
    is com.formulastudio.app.editor.MathNode.Script -> when {
        n.sup != null && n.sub != null ->
            "<m:sSubSup><m:e>${ommlNode(n.base)}</m:e>" +
                "<m:sub>${ommlGroup(n.sub!!)}</m:sub><m:sup>${ommlGroup(n.sup!!)}</m:sup></m:sSubSup>"
        n.sup != null ->
            "<m:sSup><m:e>${ommlNode(n.base)}</m:e><m:sup>${ommlGroup(n.sup!!)}</m:sup></m:sSup>"
        n.sub != null ->
            "<m:sSub><m:e>${ommlNode(n.base)}</m:e><m:sub>${ommlGroup(n.sub!!)}</m:sub></m:sSub>"
        else -> ommlNode(n.base)
    }
    is com.formulastudio.app.editor.MathNode.Op -> {
        val under = n.under
        val over = n.over
        if (under == null && over == null) {
            ommlRun(cmdSymbol(n.latex))
        } else {
            val chr = naryChar(n.latex)
            val sb = StringBuilder("<m:nary><m:naryPr><m:chr m:val=\"").append(chr)
                .append("\"/><m:limLoc m:val=\"undOvr\"/>")
            if (over == null) sb.append("<m:supHide m:val=\"1\"/>")
            if (under == null) sb.append("<m:subHide m:val=\"1\"/>")
            sb.append("</m:naryPr>")
            if (under != null) sb.append("<m:sub>").append(ommlGroup(under)).append("</m:sub>")
            if (over != null) sb.append("<m:sup>").append(ommlGroup(over)).append("</m:sup>")
            sb.append("<m:e><m:r><m:t xml:space=\"preserve\"> </m:t></m:r></m:e></m:nary>")
            sb.toString()
        }
    }
    is com.formulastudio.app.editor.MathNode.Paren ->
        ommlRun(n.left) + ommlGroup(n.inner) + ommlRun(n.right)
    is com.formulastudio.app.editor.MathNode.Matrix -> {
        val sb = StringBuilder("<m:m><m:mPr><m:mcs><m:mc><m:mcJc m:val=\"center\"/></mc></m:mcs></m:mPr>")
        for (row in n.rows) {
            sb.append("<m:mr>")
            for (cell in row) sb.append("<m:e>").append(ommlGroup(cell)).append("</m:e>")
            sb.append("</m:mr>")
        }
        sb.append("</m:m>")
        sb.toString()
    }
    is com.formulastudio.app.editor.MathNode.Cases -> {
        val sb = StringBuilder("<m:d><m:dPr><m:begChr m:val=\"{\"/><m:endChr m:val=\"\"/></m:dPr><m:e>")
        sb.append("<m:eqArr><m:eqArrPr><m:maxDist m:val=\"1\"/></m:eqArrPr>")
        for (row in n.rows) sb.append("<m:e>").append(ommlGroup(row)).append("</m:e>")
        sb.append("</m:eqArr></m:e></m:d>")
        sb.toString()
    }
    is com.formulastudio.app.editor.MathNode.Accent ->
        "<m:acc><m:accPr><m:chr m:val=\"${accentChar(n.accent)}\"/></m:accPr>" +
            "<m:e>${ommlGroup(n.base)}</m:e></m:acc>"
}

private fun ommlRun(text: String): String =
    if (text.isEmpty()) "" else "<m:r><m:t xml:space=\"preserve\">${ommlEsc(text)}</m:t></m:r>"

private fun ommlEsc(s: String): String =
    s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

private fun cmdSymbol(latex: String): String {
    val name = latex.removePrefix("\\").substringBefore(' ')
    return when (name) {
        "alpha" -> "α"; "beta" -> "β"; "gamma" -> "γ"; "delta" -> "δ"; "epsilon", "varepsilon" -> "ε"
        "zeta" -> "ζ"; "eta" -> "η"; "theta" -> "θ"; "iota" -> "ι"; "kappa" -> "κ"
        "lambda" -> "λ"; "mu" -> "μ"; "nu" -> "ν"; "xi" -> "ξ"; "pi" -> "π"; "rho" -> "ρ"
        "sigma" -> "σ"; "tau" -> "τ"; "upsilon" -> "υ"; "phi", "varphi" -> "φ"; "chi" -> "χ"
        "psi" -> "ψ"; "omega" -> "ω"
        "Gamma" -> "Γ"; "Delta" -> "Δ"; "Theta" -> "Θ"; "Lambda" -> "Λ"; "Xi" -> "Ξ"
        "Pi" -> "Π"; "Sigma" -> "Σ"; "Upsilon" -> "Υ"; "Phi" -> "Φ"; "Psi" -> "Ψ"; "Omega" -> "Ω"
        "cdot", "centerdot" -> "·"; "times" -> "×"; "div" -> "÷"; "pm" -> "±"; "mp" -> "∓"
        "ast" -> "∗"; "star" -> "⋆"; "circ" -> "∘"; "bullet" -> "∙"
        "le", "leq" -> "≤"; "ge", "geq" -> "≥"; "ne", "neq" -> "≠"; "approx" -> "≈"
        "equiv" -> "≡"; "sim" -> "∼"; "simeq" -> "≃"; "cong" -> "≅"; "propto" -> "∝"
        "infty" -> "∞"; "partial" -> "∂"; "nabla" -> "∇"; "forall" -> "∀"; "exists" -> "∃"
        "in" -> "∈"; "notin" -> "∉"; "subset" -> "⊂"; "supset" -> "⊃"; "subseteq" -> "⊆"
        "supseteq" -> "⊇"; "cup" -> "∪"; "cap" -> "∩"; "emptyset", "varnothing" -> "∅"
        "to" -> "→"; "rightarrow" -> "→"; "leftarrow" -> "←"; "Rightarrow" -> "⇒"
        "Leftarrow" -> "⇐"; "leftrightarrow" -> "↔"; "mapsto" -> "↦"
        "int" -> "∫"; "oint" -> "∮"; "sum" -> "∑"; "prod" -> "∏"; "bigcup" -> "⋃"; "bigcap" -> "⋂"
        "lim" -> "lim"; "sin" -> "sin"; "cos" -> "cos"; "tan" -> "tan"; "log" -> "log"
        "ln" -> "ln"; "exp" -> "exp"; "max" -> "max"; "min" -> "min"
        "pm" -> "±"; "lt" -> "<"; "gt" -> ">"
        else -> latex
    }
}

private fun naryChar(latex: String): String = when (latex.removePrefix("\\")) {
    "sum" -> "∑"; "prod" -> "∏"; "int" -> "∫"; "oint" -> "∮"
    "bigcup" -> "⋃"; "bigcap" -> "⋂"; "coprod" -> "∐"
    else -> "∑"
}

private fun accentChar(accent: String): String = when {
    accent.contains("hat") || accent.contains("^\\^") -> "^"
    accent.contains("bar") -> "̄"
    accent.contains("vec") -> "⃗"
    accent.contains("dot") -> "̇"
    accent.contains("ddot") -> "̈"
    accent.contains("tilde") -> "̃"
    accent.contains("check") -> "̌"
    accent.contains("breve") -> "̆"
    accent.contains("acute") -> "́"
    accent.contains("grave") -> "̀"
    else -> "^"
}
