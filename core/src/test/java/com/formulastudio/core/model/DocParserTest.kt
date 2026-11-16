package com.formulastudio.core.model

import com.formulastudio.core.export.Omml
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DocParserTest {

    @Test
    fun `解析文本与独立公式`() {
        val doc = DocParser.parse(" hello \n\n\$\$E=mc^2\$\$ \n\n world ")
        assertTrue(doc.blocks.any { it is DocBlock.Text && it.text == "hello" })
        val m = doc.blocks.filterIsInstance<DocBlock.Math>()
        assertEquals(1, m.size)
        assertEquals("E=mc^2", m[0].latex)
        assertTrue(m[0].display)
        assertTrue(doc.blocks.any { it is DocBlock.Text && it.text == "world" })
    }

    @Test
    fun `解析 tikz 与表格`() {
        val src = "\\begin{tikzpicture}\n\\draw (0,0)--(1,1);\n\\end{tikzpicture}\n\n" +
            "| a | b |\n|---|---|\n| 1 | 2 |"
        val doc = DocParser.parse(src)
        val fig = doc.blocks.filterIsInstance<DocBlock.Figure>()
        assertEquals(1, fig.size)
        assertEquals("tikz", fig[0].kind)
        val t = doc.blocks.filterIsInstance<DocBlock.Table>()
        assertEquals(1, t.size)
        assertEquals(listOf("a", "b"), t[0].rows[0])
        assertEquals(listOf("1", "2"), t[0].rows[1])
    }

    @Test
    fun `equation 环境与 title`() {
        val doc = DocParser.parse("\\title{测试}\n\\begin{equation}\nx=1\n\\end{equation}")
        assertEquals("测试", doc.title)
        assertTrue(doc.blocks.any { it is DocBlock.Math && it.latex == "x=1" })
    }
}

class OmmlTest {
    @Test
    fun `分数转 OMML`() {
        val m = Omml.convert("\\frac{a}{b}")!!
        assertTrue(m.contains("<m:f>"))
        assertTrue(m.contains("<m:num>"))
        assertTrue(m.contains("<m:den>"))
    }

    @Test
    fun `上下标与根号`() {
        val m = Omml.convert("x^2 + y_1 + \\sqrt{z}")!!
        assertTrue(m.contains("<m:sSup>"))
        assertTrue(m.contains("<m:sSub>"))
        assertTrue(m.contains("<m:rad>"))
    }

    @Test
    fun `纯文本`() {
        assertTrue(Omml.convert("hello")!!.contains("<m:t"))
    }

    @Test
    fun `无法解析时返回 null 以便回退位图`() {
        assertTrue(Omml.convert("") == null)
    }
}
