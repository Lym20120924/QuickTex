package com.formulastudio.app.doc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LatexDocumentTest {

    private fun sample(): String =
        LatexDocumentTest::class.java.classLoader!!
            .getResourceAsStream("sample.tex")?.bufferedReader()?.use { it.readText() }
            ?: error("sample.tex 未找到")

    @Test
    fun `拆分文本与公式`() {
        val blocks = LatexDocument.parse(sample())
        assertTrue("应至少有 10 个块：${blocks.size}", blocks.size >= 10)
        val math = blocks.filter { it.type == LatexDocument.Type.MATH }
        assertTrue("应识别出多个公式块：${math.size}", math.size >= 5)
        // 首个块是题目文本
        assertTrue(blocks.first().type == LatexDocument.Type.TEXT)
        assertTrue("应含中文正文", blocks.first().text.contains("计算"))
    }

    @Test
    fun `导言区被丢弃`() {
        val blocks = LatexDocument.parse(sample())
        blocks.forEach { b ->
            assertTrue("不应含 documentclass：${b.text}", !b.text.contains("documentclass"))
            assertTrue("不应含 usepackage：${b.text}", !b.text.contains("usepackage"))
            assertTrue("不应含 usetikzlibrary：${b.text}", !b.text.contains("usetikzlibrary"))
            assertTrue("不应含注释：${b.text}", !b.text.contains("====="))
        }
    }

    @Test
    fun `图形块`() {
        val blocks = LatexDocument.parse(sample())
        val fig = blocks.filter { it.type == LatexDocument.Type.FIGURE }
        assertEquals("应有 1 个图形块", 1, fig.size)
        assertEquals("tikzpicture", fig.first().figure)
        assertTrue(fig.first().text.contains("\\coordinate"))
        assertTrue(fig.first().text.contains("right angle"))
    }

    @Test
    fun `行内公式`() {
        val blocks = LatexDocument.parse(sample())
        val inline = blocks.filter { it.type == LatexDocument.Type.MATH && it.inline }
        assertTrue("应识别行内公式", inline.isNotEmpty())
        assertTrue(inline.any { it.text.contains("alpha") })
    }

    @Test
    fun `无文档环境的片段也能解析`() {
        val blocks = LatexDocument.parse("已知 ${'$'}x=1${'$'}，求 \\[y=2\\]。")
        assertEquals(LatexDocument.Type.TEXT, blocks.first().type)
        assertEquals(LatexDocument.Type.MATH, blocks[1].type)
        assertEquals("x=1", blocks[1].text)
        assertTrue("应识别 display 公式", blocks.any { it.text.contains("y=2") })
    }

    @Test
    fun `正文清理`() {
        val t = LatexDocument.cleanText("\\noindent \\textbf{1.} 计算：\\vspace{4mm}")
        assertTrue("应保留编号：$t", t.contains("1."))
        assertTrue("应保留中文：$t", t.contains("计算"))
        assertTrue("不应含 vspace：$t", !t.contains("vspace"))
    }
}
