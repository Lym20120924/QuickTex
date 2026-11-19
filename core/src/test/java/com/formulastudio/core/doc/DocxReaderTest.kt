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
    private fun docx(xml: String): ByteArray = buildDocx(mapOf("word/document.xml" to xml))

    /** 构造含多条目的 docx ZIP（entry 名 → 内容，String 会自动编码为 UTF-8 字节） */
    private fun buildDocx(entries: Map<String, Any>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            for ((name, data) in entries) {
                val bytes = when (data) {
                    is String -> data.toByteArray(Charsets.UTF_8)
                    is ByteArray -> data
                    else -> throw IllegalArgumentException("unsupported entry type: ${data::class}")
                }
                z.putNextEntry(ZipEntry(name))
                z.write(bytes)
                z.closeEntry()
            }
        }
        return bos.toByteArray()
    }

    private val NS = "xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\" " +
        "xmlns:m=\"http://schemas.openxmlformats.org/officeDocument/2006/math\" "

    /** 最小 rels：rId1 → media/image1.png */
    private val RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
        <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/image1.png"/>
        </Relationships>""".trim().toByteArray(Charsets.UTF_8)

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
        val tables = r.blocks.filter { it.isTable }
        assertEquals(1, tables.size)
        // 一个 tr 内两个 tc = 一行两列
        assertEquals(listOf(listOf("A1", "B1")), tables.first().rows)
    }

    @Test
    fun `损坏的文件返回 null`() {
        assertEquals(null, DocxReader.parse("not a zip".toByteArray()))
    }

    @Test
    fun `读取带段落样式的段落（粗体居中红色）`() {
        val src = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:document $NS><w:body>
            <w:p>
              <w:pPr>
                <w:jc w:val="center"/>
                <w:rPr>
                  <w:rFonts w:ascii="Arial" w:hAnsi="Arial"/>
                  <w:sz w:val="28"/>
                  <w:color w:val="FF0000"/>
                  <w:b/>
                </w:rPr>
              </w:pPr>
              <w:r><w:t>加粗居中红色</w:t></w:r>
            </w:p>
            </w:body></w:document>""".trim()
        val r = DocxReader.parse(docx(src))
        System.err.println("DEBUG parse result: $r")
        val txt = r?.blocks?.filter { !it.isMath && it.isTable.not() && !it.isImage }?.firstOrNull()
        assertNotNull(txt)
        assertEquals("加粗居中红色", txt!!.text)
        assertEquals("sans", txt.style.fontFamily) // Arial → sans
        assertEquals(14f, txt.style.fontSize) // 28 half-points = 14pt
        assertEquals(0xFFFF0000.toInt(), txt.style.color) // ARGB 不透明红色
        assertTrue(txt.style.bold)
        assertEquals("center", txt.style.alignment)
    }

    @Test
    fun `读取带图片的段落`() {
        val documentXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:document $NS><w:body>
            <w:p>
              <w:r><w:t>看图：</w:t></w:r>
              <w:drawing r:embed="rId1" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                <wp:graphic xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing">
                  <wp:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture">
                    <pic:pic xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture">
                      <pic:spPr>
                        <a:xfrm xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
                          <a:ext cx="914400" cy="457200"/>
                        </a:xfrm>
                      </pic:spPr>
                    </pic:pic>
                  </wp:graphicData>
                </wp:graphic>
              </w:drawing>
            </w:p>
            </w:body></w:document>""".trim()
        val pngBytes = byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte()) // PNG 签名
        val r = DocxReader.parse(buildDocx(mapOf(
            "word/document.xml" to documentXml,
            "word/_rels/document.xml.rels" to RELS,
            "word/media/image1.png" to pngBytes,
        )))!!
        val imgs = r.blocks.filter { it.isImage }
        assertEquals(1, imgs.size)
        assertEquals("image1.png", imgs.first().imageFileName)
        assertEquals("rId1", imgs.first().imageRId)
        // 914400 EMU = 1 英寸 = 72 pt
        assertEquals(72f, imgs.first().imageWidth, 0.001f)
        assertEquals(36f, imgs.first().imageHeight, 0.001f)
        // 图片字节应被提取到 Result.images
        assertEquals(4, r.images["rId1"]!!.size)
    }

    @Test
    fun `读取多行多列表格`() {
        val src = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:document $NS><w:body>
            <w:tbl>
              <w:tr>
                <w:tc><w:p><w:r><w:t>A1</w:t></w:r></w:p></w:tc>
                <w:tc><w:p><w:r><w:t>B1</w:t></w:r></w:p></w:tc>
              </w:tr>
              <w:tr>
                <w:tc><w:p><w:r><w:t>A2</w:t></w:r></w:p></w:tc>
                <w:tc><w:p><w:r><w:t>B2</w:t></w:r></w:p></w:tc>
              </w:tr>
            </w:tbl>
            </w:body></w:document>""".trim()
        val r = DocxReader.parse(docx(src))!!
        val tables = r.blocks.filter { it.isTable }
        assertEquals(1, tables.size)
        assertEquals(
            listOf(listOf("A1", "B1"), listOf("A2", "B2")),
            tables.first().rows
        )
    }
}
