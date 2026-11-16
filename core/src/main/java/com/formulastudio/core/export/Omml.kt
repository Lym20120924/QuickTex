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
