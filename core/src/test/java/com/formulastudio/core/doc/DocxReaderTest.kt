package com.formulastudio.core.doc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DocxReaderTest {

    /** 构造一个最小 docx（只有 document.xml） */
    private fun docx(xml: String): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            z.putNextEntry(ZipEntry("word/document.xml"))
            z.write(xml.toByteArray(Charsets.UTF_8))
            z.closeEntry()
        }
        return bos.toByteArray()
    }

    private val NS = "xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\" " +
        "xmlns:m=\"http://schemas.openxmlformats.org/officeDocument/2006/math\""

    @Test
    fun `读取纯文字段落`() {
        val src = "<?xml version=\"1.0\"?><w:document $NS><w:body>" +
            "<w:p><w:r><w:t>你好，世界</w:t></w:r></w:p>" +
            "<w:p><w:r><w:t>第二行</w:t></w:r></w:p>" +
            "</w:body></w:document>"
        val r = DocxReader.parse(docx(src))
        assertNotNull(r)
        val texts = r!!.blocks.filter { !it.isMath }.map { it.text }
        assertEquals(listOf("你好，世界", "第二行"), texts)
    }

    @Test
    fun `读取 Word 原生公式 OMML`() {
        val src = "<?xml version=\"1.0\"?><w:document $NS><w:body>" +
            "<w:p><w:r><w:t>设</w:t></w:r>" +
            "<m:oMath><m:r><m:t>x^2</m:t></m:r></m:oMath>" +
            "<w:r><w:t>为未知数</w:t></w:r></w:p>" +
            "</w:body></w:document>"
        val r = DocxReader.parse(docx(src))!!
        assertTrue(r.blocks.any { it.isMath && it.text == "x^2" })
        assertTrue(r.blocks.any { !it.isMath && it.text == "设为未知数" })
    }

    @Test
    fun `读取表格内的段落`() {
        val src = "<?xml version=\"1.0\"?><w:document $NS><w:body>" +
            "<w:tbl><w:tr><w:tc><w:p><w:r><w:t>A1</w:t></w:r></w:p></w:tc>" +
            "<w:tc><w:p><w:r><w:t>B1</w:t></w:r></w:p></w:tc></w:tr></w:tbl>" +
            "</w:body></w:document>"
        val r = DocxReader.parse(docx(src))!!
        val texts = r.blocks.filter { !it.isMath }.map { it.text }
        assertEquals(listOf("A1", "B1"), texts)
    }

    @Test
    fun `损坏的文件返回 null`() {
        assertEquals(null, DocxReader.parse("not a zip".toByteArray()))
    }
}
