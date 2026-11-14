package com.formulastudio.app.doc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FigureRendererTest {

    @Test
    fun `表达式求值`() {
        assertEquals(4.0f, FigureRenderer.evalExpr("x^2", 2f), 1e-4f)
        assertEquals(2.0f, FigureRenderer.evalExpr("2*x+1", 0.5f), 1e-4f)
        assertEquals(1.0f, FigureRenderer.evalExpr("sin(pi/2)", 0f), 1e-4f)
        assertEquals(3.0f, FigureRenderer.evalExpr("sqrt(9)", 0f), 1e-4f)
        assertEquals(7.0f, FigureRenderer.evalExpr("(2+1)*2+1", 0f), 1e-4f)
    }

    @Test
    fun `节点标签转文本`() {
        assertEquals("90°", FigureRenderer.simpleText("$90^\\circ$"))
        assertEquals("AC=1", FigureRenderer.simpleText("${'$'}AC=1${'$'}"))
        assertEquals("30°", FigureRenderer.simpleText("$30^\\circ$"))
        assertEquals("ABC", FigureRenderer.simpleText("${'$'}ABC${'$'}"))
    }

    @Test
    fun `tikz 尺寸`() {
        val sz = FigureRenderer.tikzSize(
            "\\coordinate (A) at (0,0);\n" +
                "\\draw (A)--(2,0)--(2,2)--cycle;\n" +
                "\\node[above] at (0,0) {\$A\$};"
        )
        assertTrue("宽度应大于 0：${sz.w}", sz.w > 0)
        assertTrue("高度应大于 0：${sz.h}", sz.h > 0)
    }

    @Test
    fun `pgfplots 尺寸`() {
        val sz = FigureRenderer.pgfplotsSize("\\addplot {x^2};")
        assertTrue(sz.w > 0 && sz.h > 0)
    }
}
