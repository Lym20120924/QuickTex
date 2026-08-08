package com.formulastudio.app.editor

/**
 * 公式结构树节点。
 *
 * 设计要点：
 *  - 一切“结构”（分数、根号、上下标、矩阵……）都是节点；
 *  - 节点只能存放在 [Group]（容器）里，光标也只能存在于容器中；
 *  - 空容器就是“占位框”（编辑器画成虚线框）。
 */
sealed class MathNode {

    /** 水平排列的一组子节点（公式根、分数分子分母、根号内部等） */
    class Group(var nodes: MutableList<MathNode> = mutableListOf()) : MathNode()

    /** 普通文本（字母、数字、标点） */
    class Text(var text: String) : MathNode()

    /** 换行符（对应 LaTeX 的 \\） */
    class NewLine : MathNode()

    /** LaTeX 命令符号（如 \times、\alpha、\sum），序列化时原样输出命令 */
    class Cmd(var latex: String) : MathNode()

    /** 无法解析的原始内容，原样保留 */
    class Raw(var latex: String) : MathNode()

    /** 分数 a/b */
    class Fraction(var num: Group, var den: Group) : MathNode()

    /** 根号；degree 为 null 时是平方根 */
    class Sqrt(var radicand: Group, var degree: Group? = null) : MathNode()

    /** 上下标 */
    class Script(var base: MathNode, var sup: Group? = null, var sub: Group? = null) : MathNode()

    /** 大型运算符（\sum \prod \int …），可带上下限 */
    class Op(var latex: String, var under: Group? = null, var over: Group? = null) : MathNode()

    /** 函数名（\sin \cos \log …，lim 用 Op 表示） */
    class Func(var name: String) : MathNode()

    /** 自动大小括号 */
    class Paren(var inner: Group, var left: String = "(", var right: String = ")") : MathNode()

    /** 矩阵/行列式；delimiter: pmatrix / bmatrix / vmatrix / matrix */
    class Matrix(var delimiter: String, var rows: MutableList<MutableList<Group>>) : MathNode()

    /** 分段函数 cases */
    class Cases(var rows: MutableList<Group>) : MathNode()

    /** 重音（\vec \hat \bar \dot \overrightarrow …） */
    class Accent(var base: Group, var accent: String) : MathNode()

    /** 化学式 \ce{...}（内部是 mhchem 语法文本） */
    class Chem(var text: String) : MathNode()
}

/** 公式文档：结构树 + 光标 */
class Formula {

    val root = MathNode.Group()

    /** 光标所在容器 */
    var cursorContainer: MathNode.Group = root

    /** 光标下标（0..容器大小） */
    var cursorIndex: Int = 0

    /**
     * 光标在"前一个节点"内部的字符偏移：
     *  -1 = 光标在节点边界（cursorIndex-1 与 cursorIndex 之间）
     *  0..len = 光标在 nodes[cursorIndex-1]（Text/Chem）内部，前面已过 cursorChar 个字符；
     *           cursorChar==len 视为与 -1 等同（节点尾边界）
     */
    var cursorChar: Int = -1

    /** 节点 -> 所在容器（根节点无） */
    val nodeParent: HashMap<MathNode, MathNode.Group> = HashMap()

    /** 子容器 -> 所属结构节点 */
    val groupOwner: HashMap<MathNode.Group, MathNode> = HashMap()

    /** 结构变更后重建父链 */
    fun rebuildParents() {
        nodeParent.clear()
        groupOwner.clear()
        walkGroup(root)
    }

    private fun walkGroup(g: MathNode.Group) {
        for (n in g.nodes) {
            nodeParent[n] = g
            when (n) {
                is MathNode.Fraction -> {
                    groupOwner[n.num] = n; groupOwner[n.den] = n
                    walkGroup(n.num); walkGroup(n.den)
                }
                is MathNode.Sqrt -> {
                    groupOwner[n.radicand] = n; walkGroup(n.radicand)
                    n.degree?.let { groupOwner[it] = n; walkGroup(it) }
                }
                is MathNode.Script -> {
                    n.sup?.let { groupOwner[it] = n; walkGroup(it) }
                    n.sub?.let { groupOwner[it] = n; walkGroup(it) }
                }
                is MathNode.Op -> {
                    n.under?.let { groupOwner[it] = n; walkGroup(it) }
                    n.over?.let { groupOwner[it] = n; walkGroup(it) }
                }
                is MathNode.Paren -> { groupOwner[n.inner] = n; walkGroup(n.inner) }
                is MathNode.Matrix -> n.rows.forEach { row ->
                    row.forEach { c -> groupOwner[c] = n; walkGroup(c) }
                }
                is MathNode.Cases -> n.rows.forEach { r -> groupOwner[r] = n; walkGroup(r) }
                is MathNode.Accent -> { groupOwner[n.base] = n; walkGroup(n.base) }
                else -> {}
            }
        }
    }

    /** 前序遍历所有容器（用于方向键跳转） */
    fun groupsInOrder(): List<MathNode.Group> {
        val out = ArrayList<MathNode.Group>()
        fun walk(g: MathNode.Group) {
            out.add(g)
            for (n in g.nodes) {
                when (n) {
                    is MathNode.Fraction -> { walk(n.num); walk(n.den) }
                    is MathNode.Sqrt -> { walk(n.radicand); n.degree?.let { walk(it) } }
                    is MathNode.Script -> { n.sup?.let { walk(it) }; n.sub?.let { walk(it) } }
                    is MathNode.Op -> { n.under?.let { walk(it) }; n.over?.let { walk(it) } }
                    is MathNode.Paren -> walk(n.inner)
                    is MathNode.Matrix -> n.rows.forEach { row -> row.forEach { walk(it) } }
                    is MathNode.Cases -> n.rows.forEach { walk(it) }
                    is MathNode.Accent -> walk(n.base)
                    else -> {}
                }
            }
        }
        walk(root)
        return out
    }

    fun clampCursor() {
        if (cursorIndex < 0) cursorIndex = 0
        if (cursorIndex > cursorContainer.nodes.size) cursorIndex = cursorContainer.nodes.size
        // 校验 cursorChar（0..len；len = 与 -1 同位置，直接转边界）
        val prev = if (cursorIndex > 0) cursorContainer.nodes[cursorIndex - 1] else null
        val maxChar = when (prev) {
            is MathNode.Text -> prev.text.length
            is MathNode.Chem -> prev.text.length
            else -> -1
        }
        if (maxChar < 0) { cursorChar = -1; return }
        if (cursorChar >= maxChar) cursorChar = -1 // cursorChar==len 转为边界
        else if (cursorChar < 0) cursorChar = -1
    }

    /** 光标是否位于某个文本节点内部（而不是节点边界） */
    fun cursorInText(): Boolean = cursorChar >= 0

    /** 光标前的节点（可能为 null） */
    fun prevNode(): MathNode? =
        if (cursorIndex > 0) cursorContainer.nodes[cursorIndex - 1] else null

    /** 光标后的节点（可能为 null） */
    fun nextNode(): MathNode? =
        if (cursorIndex < cursorContainer.nodes.size) cursorContainer.nodes[cursorIndex] else null

    /** 光标前插入节点（退出节点内状态） */
    fun insert(node: MathNode) {
        cursorChar = -1
        cursorContainer.nodes.add(cursorIndex, node)
        cursorIndex++
        rebuildParents()
    }

    /** 输入一段文本（每个字符独立节点，不合并；避免多字符 Text 节点导致光标问题） */
    fun insertText(s: String) {
        if (s.isEmpty()) return
        // 光标在文本内部：插入到内部
        if (cursorChar >= 0) {
            val prev = prevNode()
            val t = when (prev) {
                is MathNode.Text -> prev.text
                is MathNode.Chem -> prev.text
                else -> null
            }
            if (t != null) {
                val updated = t.substring(0, cursorChar) + s + t.substring(cursorChar)
                when (prev) {
                    is MathNode.Text -> prev.text = updated
                    is MathNode.Chem -> prev.text = updated
                    else -> {}
                }
                cursorChar += s.length
                rebuildParents()
                return
            }
        }
        // 每个字符独立 Text 节点
        for (c in s) {
            cursorContainer.nodes.add(cursorIndex, MathNode.Text(c.toString()))
            cursorIndex++
        }
        cursorChar = -1
        rebuildParents()
    }

    /** 退格：删除光标前一个字符/节点；若光标在子容器开头则删除整个所属结构 */
    fun backspace(): Boolean {
        // 光标在文本节点内部 → 删除内部字符
        if (cursorChar > 0) {
            val prev = prevNode()
            val t = when (prev) {
                is MathNode.Text -> prev.text
                is MathNode.Chem -> prev.text
                else -> return false
            }
            when (prev) {
                is MathNode.Text -> prev.text = t.removeRange(cursorChar - 1, cursorChar)
                is MathNode.Chem -> prev.text = t.removeRange(cursorChar - 1, cursorChar)
                else -> {}
            }
            cursorChar--
            rebuildParents()
            return true
        }
        if (cursorChar == 0) {
            // 光标在节点开头：删第一个字符（若只剩单字符则直接删节点）
            val prev = prevNode()
            val len = when (prev) {
                is MathNode.Text -> prev.text.length
                is MathNode.Chem -> prev.text.length
                else -> 0
            }
            if (len == 1) {
                // 单字符节点：直接删掉
                cursorContainer.nodes.removeAt(cursorIndex - 1)
                cursorIndex--
                cursorChar = -1
            } else if (len > 1) {
                when (prev) {
                    is MathNode.Text -> prev.text = prev.text.removeRange(0, 1)
                    is MathNode.Chem -> prev.text = prev.text.removeRange(0, 1)
                    else -> {}
                }
                // cursorChar 保持 0（仍在节点开头）
            }
            rebuildParents()
            return true
        }
        if (cursorIndex > 0) {
            val idx = cursorIndex - 1
            val node = cursorContainer.nodes[idx]
            // 文本/化学式节点：按"字符"删除，而不是一次删掉整个节点
            if (node is MathNode.Text && node.text.length > 1) {
                node.text = node.text.dropLast(1)
            } else if (node is MathNode.Chem && node.text.length > 1) {
                node.text = node.text.dropLast(1)
            } else {
                cursorContainer.nodes.removeAt(idx)
                cursorIndex = idx
            }
            rebuildParents()
            return true
        }
        val owner = groupOwner[cursorContainer]
        if (owner != null) {
            val parentGroup = nodeParent[owner] ?: return false
            val idx = parentGroup.nodes.indexOf(owner)
            if (idx < 0) return false
            parentGroup.nodes.removeAt(idx)
            cursorContainer = parentGroup
            cursorIndex = idx
            rebuildParents()
            return true
        }
        return false
    }

    /** 删除光标后的一个字符/节点 */
    fun deleteForward(): Boolean {
        // 光标在文本节点内部 → 删内部字符（光标后）
        if (cursorChar >= 0) {
            val prev = prevNode()
            val t = when (prev) {
                is MathNode.Text -> prev.text
                is MathNode.Chem -> prev.text
                else -> null
            }
            if (t != null) {
                if (t.length <= 1) {
                    // 不剩字符：直接删节点
                    cursorContainer.nodes.removeAt(cursorIndex - 1)
                    cursorIndex--
                    cursorChar = -1
                    rebuildParents()
                    return true
                }
                if (cursorChar < t.length) {
                    val updated = t.removeRange(cursorChar, cursorChar + 1)
                    when (prev) {
                        is MathNode.Text -> prev.text = updated
                        is MathNode.Chem -> prev.text = updated
                        else -> {}
                    }
                    rebuildParents()
                    return true
                }
                // 已到节点末尾 → 光标回到节点尾边界
                cursorChar = -1
            }
        }
        if (cursorIndex < cursorContainer.nodes.size) {
            val node = cursorContainer.nodes[cursorIndex]
            if (node is MathNode.Text && node.text.length > 1) {
                node.text = node.text.drop(1)
            } else if (node is MathNode.Chem && node.text.length > 1) {
                node.text = node.text.drop(1)
            } else {
                cursorContainer.nodes.removeAt(cursorIndex)
            }
            rebuildParents()
            return true
        }
        return false
    }

    fun moveLeft() {
        if (cursorChar > 0) { cursorChar--; return }
        if (cursorChar == 0) { cursorChar = -1; cursorIndex--; return }
        moveLeftFromBoundary()
    }

    fun moveRight() {
        if (cursorChar >= 0) {
            val prev = prevNode()
            val len = when (prev) {
                is MathNode.Text -> prev.text.length
                is MathNode.Chem -> prev.text.length
                else -> 0
            }
            if (len > 0 && cursorChar < len) {
                cursorChar++
                if (cursorChar == len) { cursorChar = -1; moveRightStep() }
                return
            }
            cursorChar = -1
        }
        moveRightStep()
    }

    /** 光标在节点边界时向右移动一步（结构节点会进入内部子容器） */
    private fun moveRightStep() {
        if (cursorIndex < cursorContainer.nodes.size) {
            val next = cursorContainer.nodes[cursorIndex]
            when (next) {
                is MathNode.Paren -> { cursorContainer = next.inner; cursorIndex = 0; cursorChar = -1 }
                is MathNode.Fraction -> { cursorContainer = next.num; cursorIndex = 0; cursorChar = -1 }
                is MathNode.Sqrt -> { cursorContainer = next.radicand; cursorIndex = 0; cursorChar = -1 }
                is MathNode.Script -> {
                    val g: MathNode.Group? = (next.sup as? MathNode.Group) ?: (next.sub as? MathNode.Group) ?: (next.base as? MathNode.Group)
                    if (g != null) { cursorContainer = g; cursorIndex = 0; cursorChar = -1 }
                    else cursorIndex++
                }
                is MathNode.Op -> {
                    val g: MathNode.Group? = (next.under as? MathNode.Group) ?: (next.over as? MathNode.Group)
                    if (g != null) { cursorContainer = g; cursorIndex = 0; cursorChar = -1 }
                    else cursorIndex++
                }
                is MathNode.Accent -> { cursorContainer = next.base; cursorIndex = 0; cursorChar = -1 }
                is MathNode.Matrix -> {
                    val g = next.rows.firstOrNull()?.firstOrNull() ?: return
                    cursorContainer = g; cursorIndex = 0; cursorChar = -1
                }
                is MathNode.Cases -> {
                    val g = next.rows.firstOrNull() ?: return
                    cursorContainer = g; cursorIndex = 0; cursorChar = -1
                }
                else -> cursorIndex++
            }
            return
        }
        // 容器切换
        val order = groupsInOrder()
        val cur = order.indexOf(cursorContainer)
        if (cur >= 0 && cur < order.size - 1) { cursorContainer = order[cur + 1]; cursorIndex = 0; cursorChar = -1 }
    }

    /** 光标在节点边界时向左移动一步（结构节点会进入内部子容器） */
    private fun moveLeftFromBoundary() {
        if (cursorIndex > 0) {
            val prev = cursorContainer.nodes[cursorIndex - 1]
            when (prev) {
                is MathNode.Paren -> { cursorContainer = prev.inner; cursorIndex = prev.inner.nodes.size; cursorChar = -1 }
                is MathNode.Fraction -> { cursorContainer = prev.den; cursorIndex = prev.den.nodes.size; cursorChar = -1 }
                is MathNode.Sqrt -> {
                    val g = prev.degree ?: prev.radicand
                    cursorContainer = g; cursorIndex = g.nodes.size; cursorChar = -1
                }
                is MathNode.Script -> {
                    val g: MathNode.Group? = (prev.sub as? MathNode.Group) ?: (prev.sup as? MathNode.Group) ?: (prev.base as? MathNode.Group)
                    if (g != null) { cursorContainer = g; cursorIndex = g.nodes.size; cursorChar = -1 }
                    else cursorIndex--
                }
                is MathNode.Op -> {
                    val g: MathNode.Group? = (prev.over as? MathNode.Group) ?: (prev.under as? MathNode.Group)
                    if (g != null) { cursorContainer = g; cursorIndex = g.nodes.size; cursorChar = -1 }
                    else cursorIndex--
                }
                is MathNode.Accent -> { cursorContainer = prev.base; cursorIndex = prev.base.nodes.size; cursorChar = -1 }
                is MathNode.Matrix -> {
                    val lastRow = prev.rows.lastOrNull()
                    val g = lastRow?.lastOrNull() ?: return
                    cursorContainer = g; cursorIndex = g.nodes.size; cursorChar = -1
                }
                is MathNode.Cases -> {
                    val g = prev.rows.lastOrNull() ?: return
                    cursorContainer = g; cursorIndex = g.nodes.size; cursorChar = -1
                }
                else -> cursorIndex--
            }
            return
        }
        // 容器切换
        val order = groupsInOrder()
        val cur = order.indexOf(cursorContainer)
        if (cur > 0) {
            cursorContainer = order[cur - 1]
            cursorIndex = cursorContainer.nodes.size
            cursorChar = -1
        }
    }

    /** 上/下方向：在分数的分子分母、上下标、矩阵行之间移动 */
    fun moveUp() {
        val owner = groupOwner[cursorContainer] ?: return
        when (owner) {
            is MathNode.Fraction -> {
                if (cursorContainer === owner.den) {
                    cursorContainer = owner.num
                    cursorIndex = cursorContainer.nodes.size.coerceAtMost(cursorIndex)
                    cursorChar = -1
                }
            }
            is MathNode.Script -> {
                if (owner.sub != null && cursorContainer === owner.sub && owner.sup != null) {
                    cursorContainer = owner.sup!!
                    cursorIndex = cursorContainer.nodes.size.coerceAtMost(cursorIndex)
                    cursorChar = -1
                }
            }
            is MathNode.Matrix -> {
                val (r, c) = cellIndex(owner) ?: return
                if (r > 0) {
                    cursorContainer = owner.rows[r - 1][c]
                    cursorIndex = cursorContainer.nodes.size.coerceAtMost(cursorIndex)
                    cursorChar = -1
                }
            }
            else -> {}
        }
    }

    fun moveDown() {
        val owner = groupOwner[cursorContainer] ?: return
        when (owner) {
            is MathNode.Fraction -> {
                if (cursorContainer === owner.num) {
                    cursorContainer = owner.den
                    cursorIndex = cursorContainer.nodes.size.coerceAtMost(cursorIndex)
                    cursorChar = -1
                }
            }
            is MathNode.Script -> {
                if (owner.sup != null && cursorContainer === owner.sup && owner.sub != null) {
                    cursorContainer = owner.sub!!
                    cursorIndex = cursorContainer.nodes.size.coerceAtMost(cursorIndex)
                    cursorChar = -1
                }
            }
            is MathNode.Matrix -> {
                val (r, c) = cellIndex(owner) ?: return
                if (r < owner.rows.size - 1) {
                    cursorContainer = owner.rows[r + 1][c]
                    cursorIndex = cursorContainer.nodes.size.coerceAtMost(cursorIndex)
                    cursorChar = -1
                }
            }
            else -> {}
        }
    }

    private fun cellIndex(m: MathNode.Matrix): Pair<Int, Int>? {
        for (r in m.rows.indices) {
            val c = m.rows[r].indexOf(cursorContainer)
            if (c >= 0) return r to c
        }
        return null
    }
}
