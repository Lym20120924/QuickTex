package com.formulastudio.core.model

/**
 * 把整份 `.tex`（或含 markdown 表格的文本）解析成 [Document]。
 *
 * 纯 Kotlin，不依赖 Android，因此可以单元测试，也能在桌面端复用。
 */
object DocParser {

    fun parse(src: String): Document {
        val blocks = ArrayList<DocBlock>()
        var title = ""
        var paragraph = StringBuilder()

        fun flush() {
            val t = paragraph.toString().trim()
            paragraph = StringBuilder()
            if (t.isNotEmpty()) blocks.add(DocBlock.Text(t))
        }

        var i = 0
        val n = src.length
        while (i < n) {
            // 环境块 \begin{xxx} ... \end{xxx}
            if (src.startsWith("\\begin{", i)) {
                val close = src.indexOf('}', i)
                if (close < 0) { paragraph.append(src[i]); i++; continue }
                val env = src.substring(i + 7, close)
                val endTag = "\\end{$env}"
                val end = src.indexOf(endTag, close)
                if (end < 0) { paragraph.append(src[i]); i++; continue }
                flush()
                val body = src.substring(close + 1, end).trim()
                when (env) {
                    "equation", "equation*", "align", "align*", "gather", "displaymath" ->
                        blocks.add(DocBlock.Math(body, display = true))
                    "tikzpicture" -> blocks.add(DocBlock.Figure("tikz", body))
                    "axis", "pgfplots" -> blocks.add(DocBlock.Figure("pgfplots", body))
                    else -> blocks.add(DocBlock.Text(body))
                }
                i = end + endTag.length
                continue
            }
            // 独立公式 $$...$$
            if (src.startsWith("$$", i)) {
                val end = src.indexOf("$$", i + 2)
                if (end >= 0) {
                    flush()
                    blocks.add(DocBlock.Math(src.substring(i + 2, end).trim(), display = true))
                    i = end + 2; continue
                }
            }
            // 行内公式 $...$
            if (src[i] == '$' && (i == 0 || src[i - 1] != '\\')) {
                val end = src.indexOf('$', i + 1)
                if (end > i + 1) {
                    blocks.add(DocBlock.Math(src.substring(i + 1, end), display = false))
                    i = end + 1; continue
                }
            }
            // markdown 表格：以 | 开头且下一行是分隔行
            if (src[i] == '|' && (i == 0 || src[i - 1] == '\n')) {
                val lines = ArrayList<String>()
                var j = i
                while (j < n && src[j] != '\n') { lines.add(src.substring(j, src.indexOf('\n', j).let { if (it < 0) n else it })); j = src.indexOf('\n', j).let { if (it < 0) n else it + 1 } }
                if (lines.size >= 2 && lines[1].replace(" ", "").all { it == '|' || it == '-' || it == ':' }) {
                    flush()
                    blocks.add(DocBlock.Table(parseTable(lines)))
                    i = j; continue
                }
            }
            // \title{...}
            if (src.startsWith("\\title{", i)) {
                val close = src.indexOf('}', i)
                if (close > 0) { title = src.substring(i + 7, close); i = close + 1; continue }
            }
            // 空行分段
            if (src[i] == '\n') {
                flush()
                i++; continue
            }
            paragraph.append(src[i]); i++
        }
        flush()
        return Document(title, blocks)
    }

    private fun parseTable(lines: List<String>): List<List<String>> =
        lines.filterIndexed { idx, l -> idx != 1 || !l.replace(" ", "").all { it == '|' || it == '-' || it == ':' } }
            .map { line ->
                line.trim().trim('|').split('|').map { it.trim() }
            }
}
