package com.formulastudio.app.editor

/**
 * 结构树 → LaTeX 代码。
 *
 * [clean]：给公式框展示的“干净”代码；
 * [instrumented]：给预览渲染用的代码，在每个容器外包一层 \cssId{g{n}}{...}，
 * 同时返回 “id → 容器” 映射，用于预览点击反查编辑器光标位置。
 */
class LatexSerializer {

    /** 干净版 LaTeX */
    fun clean(f: Formula): String {
        val sb = StringBuilder()
        cleanGroup(f.root, sb)
        return sb.toString()
    }

    /** 带 cssId 的预览版 LaTeX + id 映射 */
    fun instrumented(f: Formula): Pair<String, Map<String, MathNode.Group>> {
        val sb = StringBuilder()
        val map = HashMap<String, MathNode.Group>()
        idCounter = 0  // 每次渲染都从 0 开始，避免 id 越积越大
        instGroup(f.root, sb, map)
        return sb.toString() to map
    }

    private fun cleanGroup(g: MathNode.Group, sb: StringBuilder) {
        for (n in g.nodes) cleanNode(n, sb)
    }

    private fun instGroup(
        g: MathNode.Group,
        sb: StringBuilder,
        map: MutableMap<String, MathNode.Group>
    ) {
        if (g.nodes.isEmpty()) {
            // 空容器 = 占位框：预览里画一个灰色空心方块，且可点击定位
            val id = newId(map)
            map[id.toString()] = g
            sb.append("\\cssId{g").append(id).append("}{\\textcolor{gray}{\\square}}")
            return
        }
        val id = newId(map)
        map[id.toString()] = g
        sb.append("\\cssId{g").append(id).append("}{")
        for (n in g.nodes) instNode(n, sb, map)
        sb.append("}")
    }

    private var idCounter = 0
    private fun newId(map: MutableMap<String, MathNode.Group>): Int {
        return idCounter++
    }

    private fun cleanNode(n: MathNode, sb: StringBuilder) {
        when (n) {
            is MathNode.Group -> cleanGroup(n, sb)
            is MathNode.NewLine -> {
                // 先输出 \\ 再换行，代码框里看起来是「上一行末尾 \\」
                sb.append("\\\\")
                sb.append("\n")
            }
            is MathNode.Text -> sb.append(n.text)
            is MathNode.Cmd -> sb.append(n.latex)
            is MathNode.Raw -> sb.append(n.latex)
            is MathNode.Fraction -> {
                sb.append("\\frac{")
                cleanGroup(n.num, sb)
                sb.append("}{")
                cleanGroup(n.den, sb)
                sb.append("}")
            }
            is MathNode.Sqrt -> {
                if (n.degree != null) {
                    sb.append("\\sqrt[")
                    cleanGroup(n.degree!!, sb)
                    sb.append("]")
                }
                sb.append("\\sqrt{")
                cleanGroup(n.radicand, sb)
                sb.append("}")
            }
            is MathNode.Script -> {
                cleanNode(n.base, sb)
                n.sub?.let {
                    sb.append("_{")
                    cleanGroup(it, sb)
                    sb.append("}")
                }
                n.sup?.let {
                    sb.append("^{")
                    cleanGroup(it, sb)
                    sb.append("}")
                }
            }
            is MathNode.Op -> {
                sb.append(n.latex)
                n.under?.let {
                    sb.append("_{")
                    cleanGroup(it, sb)
                    sb.append("}")
                }
                n.over?.let {
                    sb.append("^{")
                    cleanGroup(it, sb)
                    sb.append("}")
                }
            }
            is MathNode.Func -> sb.append("\\").append(n.name)
            is MathNode.Paren -> {
                sb.append("\\left").append(n.left).append(" ")
                cleanGroup(n.inner, sb)
                sb.append(" \\right").append(n.right)
            }
            is MathNode.Matrix -> {
                sb.append("\\begin{").append(n.delimiter).append("}")
                for (r in n.rows.indices) {
                    if (r > 0) sb.append(" \\\\ ")
                    val row = n.rows[r]
                    for (c in row.indices) {
                        if (c > 0) sb.append(" & ")
                        cleanGroup(row[c], sb)
                    }
                }
                sb.append(" \\end{").append(n.delimiter).append("}")
            }
            is MathNode.Cases -> {
                sb.append("\\begin{cases}")
                for (r in n.rows.indices) {
                    if (r > 0) sb.append(" \\\\ ")
                    cleanGroup(n.rows[r], sb)
                }
                sb.append(" \\end{cases}")
            }
            is MathNode.Accent -> {
                sb.append("\\").append(n.accent).append("{")
                cleanGroup(n.base, sb)
                sb.append("}")
            }
            is MathNode.Chem -> sb.append("\\ce{").append(n.text).append("}")
        }
    }

    private fun instNode(n: MathNode, sb: StringBuilder, map: MutableMap<String, MathNode.Group>) {
        when (n) {
            is MathNode.Group -> instGroup(n, sb, map)
            is MathNode.NewLine -> sb.append("\\\\")
            is MathNode.Text -> sb.append(n.text)
            is MathNode.Cmd -> sb.append(n.latex)
            is MathNode.Raw -> sb.append(n.latex)
            is MathNode.Fraction -> {
                sb.append("\\frac{")
                instGroup(n.num, sb, map)
                sb.append("}{")
                instGroup(n.den, sb, map)
                sb.append("}")
            }
            is MathNode.Sqrt -> {
                if (n.degree != null) {
                    sb.append("\\sqrt[")
                    instGroup(n.degree!!, sb, map)
                    sb.append("]")
                }
                sb.append("\\sqrt{")
                instGroup(n.radicand, sb, map)
                sb.append("}")
            }
            is MathNode.Script -> {
                instNode(n.base, sb, map)
                n.sub?.let {
                    sb.append("_{")
                    instGroup(it, sb, map)
                    sb.append("}")
                }
                n.sup?.let {
                    sb.append("^{")
                    instGroup(it, sb, map)
                    sb.append("}")
                }
            }
            is MathNode.Op -> {
                sb.append(n.latex)
                n.under?.let {
                    sb.append("_{")
                    instGroup(it, sb, map)
                    sb.append("}")
                }
                n.over?.let {
                    sb.append("^{")
                    instGroup(it, sb, map)
                    sb.append("}")
                }
            }
            is MathNode.Func -> sb.append("\\").append(n.name)
            is MathNode.Paren -> {
                sb.append("\\left").append(n.left).append(" ")
                instGroup(n.inner, sb, map)
                sb.append(" \\right").append(n.right)
            }
            is MathNode.Matrix -> {
                sb.append("\\begin{").append(n.delimiter).append("}")
                for (r in n.rows.indices) {
                    if (r > 0) sb.append(" \\\\ ")
                    val row = n.rows[r]
                    for (c in row.indices) {
                        if (c > 0) sb.append(" & ")
                        instGroup(row[c], sb, map)
                    }
                }
                sb.append(" \\end{").append(n.delimiter).append("}")
            }
            is MathNode.Cases -> {
                sb.append("\\begin{cases}")
                for (r in n.rows.indices) {
                    if (r > 0) sb.append(" \\\\ ")
                    instGroup(n.rows[r], sb, map)
                }
                sb.append(" \\end{cases}")
            }
            is MathNode.Accent -> {
                sb.append("\\").append(n.accent).append("{")
                instGroup(n.base, sb, map)
                sb.append("}")
            }
            is MathNode.Chem -> sb.append("\\ce{").append(n.text).append("}")
        }
    }
}
