package com.formulastudio.app.doc

import com.formulastudio.app.editor.CMD_DISPLAY

/**
 * LaTeX 文档解析：把整份文档拆成「文本块 / 公式块 / 图形块」。
 *
 * 这里只做**结构拆分**，不试图编译 LaTeX：
 * - 公式块 → [com.formulastudio.app.editor.LatexParser] 渲染
 * - 图形块 → [TikzRenderer] / [PgfplotsRenderer] 渲染
 * - 导言区（\documentclass / \usepackage 等）与注释直接丢弃
 */
object LatexDocument {

    enum class Type { TEXT, MATH, FIGURE }

    data class Block(
        val type: Type,
        /** TEXT：已清理的正文；MATH / FIGURE：LaTeX 源码 */
        val text: String,
        /** MATH：是否行内公式 */
        val inline: Boolean = false,
        /** FIGURE：图形类型（tikzpicture / pgfplots） */
        val figure: String = ""
    )

    /** 独占一行的数学环境 */
    private val DISPLAY_ENVS = setOf(
        "align", "align*", "equation", "equation*", "gather", "gather*",
        "multline", "multline*", "displaymath", "flalign", "flalign*",
        "alignat", "alignat*", "eqnarray", "eqnarray*"
    )

    /** 图形环境 */
    private val FIGURE_ENVS = setOf("tikzpicture")
    private val PLOT_ENVS = setOf("axis", "axis*", "pgfplots")

    fun parse(src: String): List<Block> {
        val body = stripComments(documentBody(src))
        val blocks = ArrayList<Block>()
        val text = StringBuilder()
        var i = 0

        fun flushText() {
            val t = cleanText(text.toString())
            text.setLength(0)
            if (t.isNotEmpty()) blocks.add(Block(Type.TEXT, t))
        }

        while (i < body.length) {
            val c = body[i]

            // \[ ... \]
            if (c == '\\' && i + 1 < body.length && body[i + 1] == '[') {
                flushText()
                val end = findClose(body, i + 2, "[", "]")
                blocks.add(Block(Type.MATH, body.substring(i + 2, end).trim(), inline = false))
                i = end + 2
                continue
            }
            // \( ... \)
            if (c == '\\' && i + 1 < body.length && body[i + 1] == '(') {
                flushText()
                val end = findClose(body, i + 2, "(", ")")
                blocks.add(Block(Type.MATH, body.substring(i + 2, end).trim(), inline = true))
                i = end + 2
                continue
            }
            // $$ ... $$
            if (c == '$' && i + 1 < body.length && body[i + 1] == '$') {
                flushText()
                val end = body.indexOf("$$", i + 2)
                val latex = if (end < 0) body.substring(i + 2) else body.substring(i + 2, end)
                blocks.add(Block(Type.MATH, latex.trim(), inline = false))
                i = if (end < 0) body.length else end + 2
                continue
            }
            // $ ... $
            if (c == '$') {
                flushText()
                val end = body.indexOf('$', i + 1)
                val latex = if (end < 0) body.substring(i + 1) else body.substring(i + 1, end)
                blocks.add(Block(Type.MATH, latex.trim(), inline = true))
                i = if (end < 0) body.length else end + 1
                continue
            }
            // \begin{...}
            if (c == '\\' && body.startsWith("\\begin{", i)) {
                val closeBrace = body.indexOf('}', i + 7)
                if (closeBrace > 0) {
                    val env = body.substring(i + 7, closeBrace).trim()
                    val after = closeBrace + 1
                    when {
                        env in DISPLAY_ENVS -> {
                            flushText()
                            val end = findEnvEnd(body, after, env)
                            blocks.add(Block(Type.MATH, body.substring(after, end).trim(), inline = false))
                            i = skipEnvEnd(body, end, env)
                            continue
                        }
                        env in FIGURE_ENVS -> {
                            flushText()
                            val end = findEnvEnd(body, after, env)
                            blocks.add(Block(Type.FIGURE, body.substring(after, end).trim(), figure = env))
                            i = skipEnvEnd(body, end, env)
                            continue
                        }
                        env in PLOT_ENVS -> {
                            flushText()
                            val end = findEnvEnd(body, after, env)
                            blocks.add(Block(Type.FIGURE, body.substring(after, end).trim(), figure = "pgfplots"))
                            i = skipEnvEnd(body, end, env)
                            continue
                        }
                        else -> {
                            // 透明环境（itemize / center / document …）：只产生一个换行
                            text.append('\n')
                            i = after
                            continue
                        }
                    }
                }
            }
            text.append(c)
            i++
        }
        flushText()
        return blocks
    }

    // ---------- 基础工具 ----------

    /** 取 \begin{document}...\end{document} 之间的内容；没有就返回全文 */
    fun documentBody(src: String): String {
        val b = src.indexOf("\\begin{document}")
        if (b < 0) return src
        val start = src.indexOf('}', b)
        if (start < 0) return src
        val e = src.indexOf("\\end{document}", start)
        return if (e > start) src.substring(start + 1, e) else src.substring(start + 1)
    }

    /** 去掉注释（\% 不算注释） */
    fun stripComments(src: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < src.length) {
            val c = src[i]
            if (c == '\\' && i + 1 < src.length) {
                sb.append(c).append(src[i + 1]); i += 2; continue
            }
            if (c == '%') {
                while (i < src.length && src[i] != '\n') i++
                continue
            }
            sb.append(c); i++
        }
        return sb.toString()
    }

    /** 从 from 开始找配对的 closer（处理转义） */
    private fun findClose(s: String, from: Int, open: String, close: String): Int {
        var i = from
        while (i < s.length) {
            if (s[i] == '\\' && i + 1 < s.length) {
                if (s.startsWith(close, i + 1)) return i + 1
                i += 2; continue
            }
            if (s.startsWith(close, i)) return i
            i++
        }
        return s.length
    }

    private fun findEnvEnd(s: String, from: Int, env: String): Int {
        val idx = s.indexOf("\\end{$env}", from)
        return if (idx < 0) s.length else idx
    }

    private fun skipEnvEnd(s: String, end: Int, env: String): Int {
        val token = "\\end{$env}"
        return if (end < s.length && s.startsWith(token, end)) end + token.length else end
    }

    // ---------- 正文清理 ----------

    /** 把正文里的 LaTeX 命令清理成可读文本 */
    fun cleanText(raw: String): String {
        val sb = StringBuilder()
        var i = 0
        val s = raw
        while (i < s.length) {
            val c = s[i]
            if (c == '\\') {
                if (i + 1 >= s.length) break
                val n = s[i + 1]
                when (n) {
                    '%', '$', '#', '&', '_' -> { sb.append(n); i += 2; continue }
                    '\\' -> { sb.append('\n'); i += 2; continue }
                    ' ', '~' -> { sb.append(' '); i += 2; continue }
                    '[', ']', '(', ')' -> { i += 2; continue }
                    else -> {
                        val name = readNameAt(s, i + 1)
                        when (name) {
                            "item" -> { sb.append('\n').append("• "); i += 1 + name.length; continue }
                            "noindent", "indent", "par", "vspace", "hspace",
                            "smallskip", "medskip", "bigskip", "quad", "qquad",
                            "usepackage", "documentclass", "usetikzlibrary",
                            "setmainfont", "setsansfont", "setlength" -> {
                                i = skipCommand(s, i, name); continue
                            }
                            "textbf", "textit", "emph", "textrm", "textsf", "texttt",
                            "underline", "uline", "mathbf", "text", "mathrm",
                            "left", "right", "big", "Big", "bigg", "Bigg" -> {
                                i += 1 + name.length; continue
                            }
                            else -> {
                                val disp = CMD_DISPLAY["\\$name"]
                                if (disp != null) sb.append(disp) else sb.append(' ')
                                i += 1 + name.length
                                // 常见带参数命令：丢掉参数
                                if (name in ARGS0) i = skipCommand(s, i, name)
                                continue
                            }
                        }
                    }
                }
            }
            if (c == '{' || c == '}') { i++; continue }
            if (c == '~') { sb.append(' '); i++; continue }
            sb.append(c); i++
        }
        return sb.toString().replace(Regex("[ \\t]+\\n"), "\n")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    /** 这些命令在正文里带一个花括号参数，需要一起丢掉 */
    private val ARGS0 = setOf("vspace", "hspace", "cline")

    private val TEXT_ARG_COMMANDS = setOf(
        "textbf", "textit", "emph", "textrm", "underline", "mathbf", "text"
    )

    private fun readNameAt(s: String, from: Int): String {
        var i = from
        while (i < s.length && (s[i].isLetterOrDigit() || s[i] == '*')) i++
        return s.substring(from, i)
    }

    /** 跳过命令及其后的可选/必选参数 */
    private fun skipCommand(s: String, start: Int, name: String): Int {
        var i = start + 1 + name.length
        // 可选参数 [..]
        while (i < s.length) {
            if (s[i] == '[') {
                val close = s.indexOf(']', i)
                if (close < 0) return s.length
                i = close + 1
            } else break
        }
        // 必选参数 {..}
        while (i < s.length && (s[i] == ' ' || s[i] == '\n')) i++
        if (i < s.length && s[i] == '{') {
            var depth = 0
            while (i < s.length) {
                val c = s[i]
                if (c == '{') depth++
                else if (c == '}') { depth--; i++; if (depth == 0) break; continue }
                i++
            }
        }
        return i
    }
}
