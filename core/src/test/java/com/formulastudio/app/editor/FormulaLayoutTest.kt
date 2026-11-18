package com.formulastudio.app.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 排版引擎单元测试（纯 JVM，不需要真机）。
 *
 * 用 [FakeMetrics] 提供确定性度量，验证盒模型的几何关系。
 */
class FormulaLayoutTest {

    private val sz = 100f
    private val metrics = FakeMetrics()

    private fun layoutOf(latex: String): LayoutBox {
        val root = LatexParser().parse(latex)
        return FormulaLayout(metrics).layout(root, sz)
    }

    @Test
    fun `空公式取最小宽度`() {
        val box = layoutOf("")
        assertEquals(1.15f * sz, box.w, 0.01f)
        assertEquals(sz * 1.2f, box.h, 0.01f)   // lineHeight
        assertEquals(sz * 0.9f, box.baseline, 0.01f) // ascent
    }

    @Test
    fun `单字符宽高与基线`() {
        val box = layoutOf("a")
        assertEquals(0.5f * sz, box.w, 0.01f)   // measure("a") = 1 * 100 * 0.5
        assertEquals(sz * 1.2f, box.h, 0.01f)
        assertEquals(sz * 0.9f, box.baseline, 0.01f)
    }

    @Test
    fun `两个字符横向排列`() {
        val box = layoutOf("ab")
        // 0.5em * 2 + 0.06em 间距
        assertEquals(sz * (0.5f * 2 + 0.06f), box.w, 0.01f)
    }

    @Test
    fun `分数高度大于两个单行`() {
        val box = layoutOf("\\frac{a}{b}")
        // 分子 + 间距 + 分数线 + 间距 + 分母
        assertTrue("分数高度应大于 2 倍行高，实际 ${box.h}", box.h > 2 * sz * 1.2f)
        // 分数线信息应存在
        assertNotNull(box.parts)
    }

    @Test
    fun `根号有绘制信息`() {
        val root = LatexParser().parse("\\sqrt{a}")
        val layout = FormulaLayout(metrics)
        layout.layout(root, sz)
        val sqrtNode = root.nodes.filterIsInstance<MathNode.Sqrt>().firstOrNull()
        assertNotNull("应解析出根号节点", sqrtNode)
        assertNotNull("根号应有 SqrtInfo", layout.sqrtInfo[sqrtNode!!])
    }

    @Test
    fun `多行公式分成多行`() {
        val root = LatexParser().parse("a\\\\b")
        val layout = FormulaLayout(metrics)
        layout.layout(root, sz)
        val lines = layout.multilineLines[root]
        assertNotNull("多行应有行盒缓存", lines)
        assertEquals(2, lines!!.size)
    }

    @Test
    fun `上下标增加高度`() {
        val plain = layoutOf("x")
        val script = layoutOf("x^2")
        assertTrue("带上标后高度应增加", script.h > plain.h)
    }
}
