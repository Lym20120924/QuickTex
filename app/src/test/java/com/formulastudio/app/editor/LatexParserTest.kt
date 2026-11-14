package com.formulastudio.app.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 反向同步（LaTeX → 结构树）回归测试。
 * 这些用例覆盖符号键盘能表达的全部命令，以及扩展的手写命令。
 */
class LatexParserTest {

    private val parser = LatexParser()
    private val serializer = LatexSerializer()

    private fun roundTrip(src: String): String {
        val g = parser.parse(src)
        val f = Formula()
        f.root.nodes.addAll(g.nodes)
        f.rebuildParents()
        return serializer.clean(f)
    }

    @Test
    fun `普通字符`() {
        assertEquals("x=1", roundTrip("x=1"))
    }

    @Test
    fun `分式与根号`() {
        assertEquals("\\frac{1}{2}", roundTrip("\\frac{1}{2}"))
        assertEquals("\\sqrt{x}", roundTrip("\\sqrt{x}"))
        assertEquals("\\sqrt[3]{x}", roundTrip("\\sqrt[3]{x}"))
    }

    @Test
    fun `上下标`() {
        assertEquals("x^{2}", roundTrip("x^2"))
        assertEquals("x_{1}", roundTrip("x_1"))
    }

    @Test
    fun `矩阵`() {
        val out = roundTrip("\\begin{matrix}1&2\\\\3&4\\end{matrix}")
        assertTrue("应保留 matrix 环境：$out", out.contains("\\begin{matrix}"))
        assertTrue("应含第一行：$out", out.contains("1"))
        assertTrue("应含第二行（多行矩阵不能丢行）：$out", out.contains("4"))
        assertTrue("应含换行：$out", out.contains("\\\\"))
    }

    @Test
    fun `n 次根号`() {
        assertEquals("\\sqrt[3]{x}", roundTrip("\\sqrt[3]{x}"))
    }

    @Test
    fun `left right 定界符`() {
        assertEquals("\\left( x \\right)", roundTrip("\\left( x \\right)"))
        assertEquals("\\left[ x \\right]", roundTrip("\\left[ x \\right]"))
    }

    @Test
    fun `分段函数`() {
        val out = roundTrip("\\begin{cases}x&x>0\\\\-x&x<0\\end{cases}")
        assertTrue("应为 cases：$out", out.contains("\\begin{cases}"))
    }

    @Test
    fun `括号与绝对值`() {
        assertTrue(roundTrip("\\left( x \\right)").contains("\\left( x \\right)"))
        assertTrue(roundTrip("|x|").contains("|x|"))
    }

    @Test
    fun `希腊字母`() {
        assertEquals("\\alpha", roundTrip("\\alpha"))
        assertEquals("\\Omega", roundTrip("\\Omega"))
    }

    @Test
    fun `扩展符号命令`() {
        // 这些命令必须被识别成 Cmd（显示为符号），而不是 Raw（原样显示）
        listOf(
            "\\nsupset", "\\notni", "\\nparallel", "\\models", "\\preceq",
            "\\beth", "\\measuredangle", "\\lessgtr", "\\between"
        ).forEach { cmd ->
            val out = roundTrip(cmd)
            assertEquals("命令 $cmd 应原样保留：$out", cmd, out)
        }
    }

    @Test
    fun `单字符命令与间距`() {
        val out = roundTrip("a\\,b\\quad c")
        assertTrue("应含空格：$out", out.contains("a"))
        assertFalse("不应残留 \\,", out.contains("\\,"))
    }

    @Test
    fun `binom 转成分式加括号`() {
        val out = roundTrip("\\binom{n}{k}")
        assertTrue("应为 \\left(\\frac{n}{k}\\right)：$out", out.contains("\\frac{n}{k}"))
        assertTrue(out.contains("\\left("))
    }

    @Test
    fun `透明环境不残留 begin end`() {
        val out = roundTrip("\\begin{align}x&=1\\\\y&=2\\end{align}")
        assertFalse("不应含 \\\\begin：$out", out.contains("\\begin"))
        assertFalse("不应含 \\\\end：$out", out.contains("\\end"))
        assertTrue("应含换行：$out", out.contains("\\\\"))
    }

    @Test
    fun `星号环境`() {
        val out = roundTrip("\\begin{align*}x&=1\\end{align*}")
        assertFalse("不应含 \\\\begin：$out", out.contains("\\begin"))
        assertFalse("不应含 \\\\end：$out", out.contains("\\end"))
    }

    @Test
    fun `array 列规格被跳过`() {
        val out = roundTrip("\\begin{array}{cc}1&2\\end{array}")
        assertFalse("不应残留列规格：$out", out.contains("cc"))
    }

    @Test
    fun `textcolor 取内容`() {
        val out = roundTrip("\\textcolor{red}{x}")
        assertTrue("应保留内容 x：$out", out.contains("x"))
        assertFalse("不应含 red：$out", out.contains("red"))
    }

    @Test
    fun `未知命令仍原样保留`() {
        assertEquals("\\foobar", roundTrip("\\foobar"))
    }

    @Test
    fun `手写复杂公式`() {
        val src = "E=mc^{2}"
        assertEquals(src, roundTrip(src))
    }

    @Test
    fun `新增命令原样保留`() {
        listOf(
            "\\lceil", "\\lfloor", "\\lVert", "\\lbrace", "\\rbrack",
            "\\sqcup", "\\sqcap", "\\oslash", "\\bigcirc",
            "\\curlyeqprec", "\\backsimeq", "\\vDash", "\\Vdash", "\\dashv",
            "\\triangleleft", "\\surd", "\\checkmark", "\\degree",
            "\\copyright", "\\pounds", "\\euro", "\\colon", "\\cdotp",
            "\\clubsuit", "\\heartsuit", "\\spadesuit", "\\%", "\\$", "\\#",
            "\\_", "\\&"
        ).forEach { cmd ->
            assertEquals("命令 $cmd 应原样保留", cmd, roundTrip(cmd))
        }
    }

    @Test
    fun `cfrac 与 middle`() {
        assertEquals("\\frac{1}{2}", roundTrip("\\cfrac{1}{2}"))
        assertTrue("应含定界符：${roundTrip("a\\middle|b")}", roundTrip("a\\middle|b").contains("|"))
    }

    @Test
    fun `宏定义被跳过`() {
        val out = roundTrip("\\newcommand{\\x}{y} z")
        assertFalse("不应含 newcommand：$out", out.contains("newcommand"))
        assertTrue("应保留后续内容：$out", out.contains("z"))
    }

    @Test
    fun `表格命令被忽略`() {
        val out = roundTrip("\\begin{matrix}1&2\\hline 3&4\\end{matrix}")
        assertFalse("不应含 hline：$out", out.contains("hline"))
    }

    @Test
    fun `整份文档直接解析不崩`() {
        val doc = LatexParserTest::class.java.classLoader!!
            .getResourceAsStream("sample.tex")!!.bufferedReader().readText()
        val g = parser.parse(doc)
        assertTrue("整份文档应解析出内容", g.nodes.isNotEmpty())
    }

    @Test
    fun `注释被忽略`() {
        assertEquals("x", roundTrip("x % 这是注释\n"))
    }

    @Test
    fun `多行公式`() {
        val out = roundTrip("x=1\\\\y=2")
        assertTrue("应含换行：$out", out.contains("\\\\"))
        assertTrue(out.contains("x=1"))
        assertTrue(out.contains("y=2"))
    }
}
