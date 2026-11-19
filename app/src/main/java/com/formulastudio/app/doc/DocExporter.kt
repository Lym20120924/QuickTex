package com.formulastudio.app.doc

import com.formulastudio.app.R
import com.formulastudio.core.model.DocBlock
import com.formulastudio.core.model.TextStyle

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 把文档块导出成 **PDF / Word(docx)**，全部零第三方依赖：
 * - PDF 用系统 [PdfDocument]
 * - docx 手写最小 OOXML（ZIP + document.xml），公式以图片嵌入
 */
object DocExporter {

    private fun wrap(text: String, paint: Paint, maxW: Float): List<String> {
        val out = ArrayList<String>()
        for (para in text.split("\n")) {
            var line = StringBuilder()
            for (ch in para) {
                line.append(ch)
                if (paint.measureText(line.toString()) > maxW) {
                    line.setLength(line.length - 1)
                    out.add(line.toString())
                    line = StringBuilder().append(ch)
                }
            }
            out.add(line.toString())
        }
        return out
    }

    // ==================== PDF ====================

    fun toPdf(context: Context, items: List<com.formulastudio.app.DocumentActivity.Item>): File? {
        return try {
            val doc = PdfDocument()
            val pageW = 595; val pageH = 842          // A4 (pt)
            val margin = 42f
            val contentW = pageW - margin * 2
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK; textSize = 12f; typeface = Typeface.SERIF
            }
            var pageNum = 0
            var page: PdfDocument.Page? = null
            var canvas: Canvas? = null
            var y = 0f

            fun newPage() {
                page?.let { doc.finishPage(it) }
                pageNum++
                page = doc.startPage(
                    PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create()
                )
                canvas = page!!.canvas
                y = margin
            }
            newPage()

            for (it in items) {
                val c = canvas ?: break
                if (it.isText) {
                    for (line in wrap(it.text, textPaint, contentW)) {
                        if (y > pageH - margin) newPage()
                        c.drawText(line, margin, y + 12f, textPaint)
                        y += 16f
                    }
                    y += 8f
                } else {
                    val bmp = it.bitmap ?: continue
                    if (bmp.width <= 0 || bmp.height <= 0) continue
                    val sc = contentW / bmp.width
                    val h = bmp.height * sc
                    if (y + h > pageH - margin) { newPage() }
                    val cc = canvas ?: break
                    cc.save()
                    cc.translate(margin, y)
                    cc.scale(sc, sc)
                    cc.drawBitmap(bmp, 0f, 0f, null)
                    cc.restore()
                    y += h + 12f
                }
            }
            page?.let { doc.finishPage(it) }
            val f = File(File(context.cacheDir, "shared").apply { mkdirs() },
                "document_${System.currentTimeMillis()}.pdf")
            FileOutputStream(f).use { doc.writeTo(it) }
            doc.close()
            f
        } catch (e: Exception) {
            null
        }
    }

    // ==================== Word (docx) ====================

    fun toDocx(context: Context, items: List<com.formulastudio.app.DocumentActivity.Item>): File? {
        return try {
            val body = StringBuilder()
            val rels = StringBuilder()
            var imgIdx = 0
            val media = ArrayList<Pair<String, Bitmap>>()

            for (it in items) {
                if (it.isText) {
                    for (line in it.text.split("\n")) {
                        if (line.isBlank()) continue
                        body.append("<w:p><w:r><w:t xml:space=\"preserve\">")
                            .append(esc(line)).append("</w:t></w:r></w:p>\n")
                    }
                } else {
                    // 优先输出 Word 原生公式（可编辑），失败才回退到图片
                    val omml = com.formulastudio.core.export.Omml.convert(it.text)
                    if (omml != null) {
                        body.append("<w:p><m:oMath>").append(omml).append("</m:oMath></w:p>\n")
                        continue
                    }
                    val bmp = it.bitmap ?: continue
                    if (bmp.width <= 0 || bmp.height <= 0) continue
                    imgIdx++
                    val name = "image$imgIdx.png"
                    media.add(name to bmp)
                    val cx = Math.round(bmp.width / 96.0 * 914400)
                    val cy = Math.round(bmp.height / 96.0 * 914400)
                    rels.append("<Relationship Id=\"rIdImg").append(imgIdx)
                        .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"media/")
                        .append(name).append("\"/>\n")
                    body.append("<w:p><w:r><w:drawing><wp:inline distT=\"0\" distB=\"0\" distL=\"0\" distR=\"0\">")
                        .append("<wp:extent cx=\"").append(cx).append("\" cy=\"").append(cy).append("\"/>")
                        .append("<wp:docPr id=\"").append(imgIdx).append("\" name=\"Formula\"/>")
                        .append("<a:graphic xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\">")
                        .append("<a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">")
                        .append("<pic:pic xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">")
                        .append("<pic:nvPicPr><pic:cNvPr id=\"").append(imgIdx).append("\" name=\"")
                        .append(name).append("\"/><pic:cNvPicPr/></pic:nvPicPr>")
                        .append("<pic:blipFill><a:blip r:embed=\"rIdImg").append(imgIdx)
                        .append("\"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>")
                        .append("<pic:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"").append(cx)
                        .append("\" cy=\"").append(cy).append("\"/></a:xfrm>")
                        .append("<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></pic:spPr>")
                        .append("</pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>\n")
                }
            }

            val documentXml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\" " +
                "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" " +
                "xmlns:wp=\"http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing\" " +
                "xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" " +
                "xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\" " +
                "xmlns:m=\"http://schemas.openxmlformats.org/officeDocument/2006/math\">" +
                "<w:body>\n" + body + "</w:body></w:document>"

            val relsXml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">\n" +
                rels + "</Relationships>"

            val f = File(File(context.cacheDir, "shared").apply { mkdirs() },
                "document_${System.currentTimeMillis()}.docx")
            ZipOutputStream(FileOutputStream(f)).use { z ->
                fun entry(name: String, data: ByteArray) {
                    z.putNextEntry(ZipEntry(name)); z.write(data); z.closeEntry()
                }
                entry("[Content_Types].xml", ("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                    "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
                    "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
                    "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
                    "<Default Extension=\"png\" ContentType=\"image/png\"/>" +
                    "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>" +
                    "</Types>").toByteArray())
                entry("_rels/.rels", ("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                    "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                    "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>" +
                    "</Relationships>").toByteArray())
                entry("word/document.xml", documentXml.toByteArray())
                entry("word/_rels/document.xml.rels", relsXml.toByteArray())
                for ((name, bmp) in media) {
                    val bos = ByteArrayOutputStream()
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, bos)
                    entry("word/media/$name", bos.toByteArray())
                }
            }
            f
        } catch (e: Exception) {
            null
        }
    }

    /** 导出 Excel：每行一个文本行，公式作为单元格内容（LaTeX 原文） */
    fun toXlsx(context: Context, items: List<com.formulastudio.app.DocumentActivity.Item>): File? {
        return try {
            val rows = ArrayList<List<String>>()
            for (it in items) {
                if (it.isText) {
                    for (line in it.text.split("\n")) rows.add(listOf(line))
                } else {
                    rows.add(listOf("[" + context.getString(R.string.formula) + "]"))
                }
            }
            val data = com.formulastudio.core.export.XlsxExporter.build(rows, "QuickTex")
            val f = File(File(context.cacheDir, "shared").apply { mkdirs() },
                "document_${System.currentTimeMillis()}.xlsx")
            f.writeBytes(data)
            f
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 导出 Markdown：文字段落 + 公式（`$...$` / `$$...$$`）。
     * 公式保留 LaTeX 原文，可在任何 Markdown 渲染器（含 KaTeX/MathJax）中显示。
     */
    fun toMarkdown(context: Context, items: List<com.formulastudio.app.DocumentActivity.Item>): File? {
        return try {
            val sb = StringBuilder()
            for (it in items) {
                if (it.isText) {
                    if (it.text.isNotBlank()) sb.append(it.text.trim()).append("\n\n")
                } else {
                    val tex = it.text.trim()
                    if (tex.isNotEmpty()) sb.append("$$\n").append(tex).append("\n$$\n\n")
                }
            }
            val f = File(File(context.cacheDir, "shared").apply { mkdirs() },
                "document_${System.currentTimeMillis()}.md")
            f.writeText(sb.toString(), Charsets.UTF_8)
            f
        } catch (e: Exception) {
            null
        }
    }

    private fun esc(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    // ==================== 新增：DocBlock → docx（带段落样式，A5） ====================

    /** 把 [DocBlock] 列表导出为 Word 文档，保留文字样式、表格、图片（资源来自 [images]）。 */
    fun toDocxFromBlocks(
        context: Context,
        blocks: List<DocBlock>,
        images: Map<String, ByteArray> = emptyMap()
    ): File? {
        return try {
            val body = StringBuilder()
            val rels = StringBuilder()
            val media = mutableMapOf<String, ByteArray>()
            var imgIdx = 0

            for (block in blocks) {
                when (block) {
                    is DocBlock.Text -> {
                        body.append("<w:p>")
                        body.append("<w:pPr>")
                        // 对齐
                        body.append("<w:jc w:val=\"${styleAlignment(block.style)}\"/>")
                        // 段落级默认运行属性
                        body.append("<w:rPr>")
                        body.append(runPropsXml(block.style))
                        body.append("</w:rPr>")
                        body.append("</w:pPr>")
                        for (line in block.text.split("\n")) {
                            if (line.isBlank()) continue
                            body.append("<w:r><w:t xml:space=\"preserve\">")
                                .append(esc(line)).append("</w:t></w:r>")
                        }
                        body.append("</w:p>\n")
                    }
                    is DocBlock.Math -> {
                        val omml = com.formulastudio.core.export.Omml.convert(block.latex)
                        if (omml != null) {
                            body.append("<w:p><m:oMath>")
                                .append(omml).append("</m:oMath></w:p>\n")
                        } else {
                            // 回退：公式作为图片嵌入
                            val bmp = BlockRenderer.mathBitmap(context, block.latex, !block.display)
                            if (bmp != null) {
                                imgIdx++
                                val name = "image$imgIdx.png"
                                media[name] = bitmapToPngBytes(bmp)
                                val cx = Math.round(bmp.width / 96.0 * 914400)
                                val cy = Math.round(bmp.height / 96.0 * 914400)
                                rels.append(imageRel(imgIdx, name))
                                body.append(imageDrawing(imgIdx, name, cx, cy))
                            }
                        }
                    }
                    is DocBlock.Table -> {
                        body.append("<w:tbl><w:tblPr><w:tblW w:type=\"dxa\" w:val=\"9000\"/></w:tblPr>")
                        for (row in block.rows) {
                            body.append("<w:tr>")
                            for (cell in row) {
                                body.append("<w:tc><w:p><w:r><w:t xml:space=\"preserve\">")
                                    .append(esc(cell)).append("</w:t></w:r></w:p></w:tc>")
                            }
                            body.append("</w:tr>")
                        }
                        body.append("</w:tbl>\n")
                    }
                    is DocBlock.Figure -> { } // 图形暂不导出
                    is DocBlock.Image -> {
                        val data = images[block.src] ?: continue
                        val bmp = android.graphics.BitmapFactory.decodeByteArray(data, 0, data.size) ?: continue
                        imgIdx++
                        val name = "image$imgIdx.png"
                        media[name] = data
                        val cx = Math.round(block.width.coerceAtLeast(0f) / 72f * 914400).toLong()
                        val cy = Math.round(block.height.coerceAtLeast(0f) / 72f * 914400).toLong()
                        rels.append(imageRel(imgIdx, name))
                        body.append(imageDrawing(imgIdx, name, cx, cy))
                        body.append("\n")
                    }
                }
            }

            val documentXml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\" " +
                "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" " +
                "xmlns:wp=\"http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing\" " +
                "xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" " +
                "xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\" " +
                "xmlns:m=\"http://schemas.openxmlformats.org/officeDocument/2006/math\">" +
                "<w:body>\n" + body + "</w:body></w:document>"

            val relsXml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">\n" +
                "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>\n" +
                rels +
                "</Relationships>"

            val f = File(File(context.cacheDir, "shared").apply { mkdirs() },
                "document_${System.currentTimeMillis()}.docx")
            ZipOutputStream(FileOutputStream(f)).use { z ->
                fun entry(name: String, data: ByteArray) {
                    z.putNextEntry(ZipEntry(name)); z.write(data); z.closeEntry()
                }
                entry("[Content_Types].xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                    <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                    <Default Extension="xml" ContentType="application/xml"/>
                    <Default Extension="png" ContentType="image/png"/>
                    <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                    </Types>""".trim().toByteArray())
                entry("_rels/.rels", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
                    </Relationships>""".trim().toByteArray())
                entry("word/document.xml", documentXml.toByteArray())
                entry("word/_rels/document.xml.rels", relsXml.toByteArray())
                for ((name, data) in media) {
                    entry("word/media/$name", data)
                }
            }
            f
        } catch (e: Exception) {
            null
        }
    }

    private fun runPropsXml(style: TextStyle): String {
        val sb = StringBuilder()
        sb.append("<w:rFonts w:ascii=\"${fontName(style)}\" w:hAnsi=\"${fontName(style)}\" w:cs=\"${fontName(style)}\"/>")
        sb.append("<w:sz w:val=\"${(style.fontSize * 2).toInt()}\"/>")
        sb.append("<w:color w:val=\"${colorHex(style.color)}\"/>")
        if (style.bold) sb.append("<w:b/>")
        if (style.italic) sb.append("<w:i/>")
        return sb.toString()
    }

    private fun fontName(style: TextStyle): String = when (style.fontFamily) {
        "sans" -> "Arial"
        "mono" -> "Courier New"
        "cjk" -> "SimSun"
        else -> "Times New Roman"
    }

    private fun colorHex(color: Int): String = "%06X".format(color and 0xFFFFFF)

    private fun styleAlignment(style: TextStyle): String = when (style.alignment) {
        "center" -> "center"
        "right" -> "right"
        "justify" -> "both"
        else -> "left"
    }

    private fun imageRel(idx: Int, name: String): String =
        "<Relationship Id=\"rIdImg$idx\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"media/$name\"/>\n"

    private fun imageDrawing(idx: Int, name: String, cx: Long, cy: Long): String =
        "<w:p><w:r><w:drawing><wp:inline distT=\"0\" distB=\"0\" distL=\"0\" distR=\"0\">" +
            "<wp:extent cx=\"$cx\" cy=\"$cy\"/>" +
            "<wp:docPr id=\"$idx\" name=\"Formula\"/>" +
            "<a:graphic xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\">" +
            "<a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">" +
            "<pic:pic xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">" +
            "<pic:nvPicPr><pic:cNvPr id=\"$idx\" name=\"$name\"/><pic:cNvPicPr/></pic:nvPicPr>" +
            "<pic:blipFill><a:blip r:embed=\"rIdImg$idx\"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>" +
            "<pic:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"$cx\" cy=\"$cy\"/></a:xfrm>" +
            "<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></pic:spPr>" +
            "</pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>\n"

    private fun bitmapToPngBytes(bmp: Bitmap): ByteArray {
        val bos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, bos)
        return bos.toByteArray()
    }
}
