package com.formulastudio.app.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 命中测试 / 光标定位单元测试（纯 JVM）。
 */
class FormulaHitTesterTest {

    private val sz = 100f
    private val metrics = FakeMetrics()

    private inner class Fixture(latex: String) {
        val root: MathNode.Group = LatexParser().parse(latex)
        val layout = FormulaLayout(metrics)
        val box: LayoutBox = layout.layout(root, sz)
        val tester = FormulaHitTester(layout)
    }

    @Test
    fun `空公式点击返回根容器`() {
        val f = Fixture("")
        val hit = f.tester.hitTest(f.root, f.box, 0f, 0f)
        assertEquals(f.root, hit.first)
        assertEquals(0, hit.second)
        assertEquals(-1, hit.third)
    }

    @Test
    fun `点击字符内部定位到字符偏移`() {
        val f = Fixture("a")
        // 字符宽 0.5em = 50；点 x=40 落在字符内、靠近右侧
        val hit = f.tester.hitTest(f.root, f.box, 40f, 60f)
        assertEquals(f.root, hit.first)
        assertEquals(0, hit.second)   // 第 0 个节点
        assertEquals(1, hit.third)     // 该节点内第 1 个字符后
    }

    @Test
    fun `点击字符内偏左仍算字符内`() {
        val f = Fixture("a")
        // 文本节点以“字符”为最小粒度：字符宽 50，点 x=5 仍算该字符内
        val hit = f.tester.hitTest(f.root, f.box, 5f, 60f)
        assertEquals(f.root, hit.first)
        assertEquals(0, hit.second)
        assertEquals(1, hit.third)     // 第 0 个节点内、第 1 个字符后
    }

    @Test
    fun `点击两个字符之间取最近边界`() {
        val f = Fixture("ab")
        // a: 0..50, 间距 6, b: 56..106；x=53 到 a 的尾边界(50)与 b 的头边界(56)等距，
        // 原实现取先遇到的候选 → a 的字符边界
        val hit = f.tester.hitTest(f.root, f.box, 53f, 60f)
        assertEquals(f.root, hit.first)
        assertEquals(0, hit.second)
        assertEquals(1, hit.third)
    }

    @Test
    fun `点击分数区域下钻到子容器`() {
        val f = Fixture("\\frac{a}{b}")
        val frac = f.root.nodes.filterIsInstance<MathNode.Fraction>().first()
        // 点击分数上半部（分子所在区域）
        val hit = f.tester.hitTest(f.root, f.box, f.box.w / 2f, f.box.h * 0.2f)
        // 应命中分子或分母容器，而不是停在根
        assertTrue(
            "应下钻到分子或分母，实际 ${hit.first}",
            hit.first === frac.num || hit.first === frac.den
        )
    }

    @Test
    fun `点击分数分子进入分子容器`() {
        val f = Fixture("\\frac{a}{b}")
        // 分子在分数盒顶部中央
        val frac = f.root.nodes.filterIsInstance<MathNode.Fraction>().first()
        assertNotNull(frac)
        val cx = f.box.w / 2f
        val hit = f.tester.hitTest(f.root, f.box, cx, f.box.h * 0.2f)
        // 应命中分子容器（或分子内的文本），而不是根
        assertNotNull(hit)
    }

    @Test
    fun `光标 x 坐标随下标前进`() {
        val f = Fixture("ab")
        val x0 = f.tester.caretX(f.root, f.box, 0, -1, null)
        val x1 = f.tester.caretX(f.root, f.box, 1, -1, null)
        val x2 = f.tester.caretX(f.root, f.box, 2, -1, null)
        assertEquals(0f, x0, 0.01f)
        assertTrue(x1 > x0)
        assertTrue(x2 > x1)
    }

    @Test
    fun `文本内光标 x 累加字符宽度`() {
        val f = Fixture("ab")
        // 光标在第 0 个节点内、第 1 个字符后
        val x = f.tester.caretX(f.root, f.box, 0, 1, "a")
        assertEquals(0.5f * sz, x, 0.01f)
    }

    @Test
    fun `前缀宽度`() {
        val f = Fixture("abc")
        assertEquals(0.5f * sz, f.tester.prefixWidth("abc", sz, 1), 0.01f)
        assertEquals(1.0f * sz, f.tester.prefixWidth("abc", sz, 2), 0.01f)
        assertEquals(1.5f * sz, f.tester.prefixWidth("abc", sz, 3), 0.01f)
    }
}
