package com.formulastudio.core.doc

import com.formulastudio.core.model.Document
import com.formulastudio.core.model.DocBlock
import com.formulastudio.core.model.TextStyle
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.NodeList

/**
 * docx 读取器（阶段 v4.0：Office 双向）。
 *
 * docx 本质是个 ZIP，正文在 `word/document.xml`，关系在 `word/_rels/document.xml.rels`，
 * 图片资源在 `word/media/`。
 *
 * 本解析器把正文拆成 **文字 / 公式 / 表格 / 图片** 块，并尽量保留段落样式：
 * - `w:p` 段落 + `w:pPr` → 文字（带 TextStyle：字体/字号/颜色/粗斜体/对齐）
 * - `m:oMath` → 公式（取 `m:t` 文本）
 * - `w:tbl` → 表格（行 × 列文本）
 * - `w:drawing` → 图片（通过 rels 解析到 word/media/xxx.png）
 *
 * 纯 Kotlin，可在单元测试里验证。
 */
object DocxReader {

    enum class Kind { TEXT, MATH, TABLE, IMAGE }

    data class Block(
        val kind: Kind,
        /** TEXT：已清理的正文；MATH：LaTeX 原文；TABLE：不使用 */
        val text: String = "",
        /** 段落样式（TEXT 使用） */
        val style: TextStyle = TextStyle(),
        /** TABLE：行 × 列单元格文本 */
        val rows: List<List<String>> = emptyList(),
        /** IMAGE：关系 ID（rId），用于在 Result.images 里取字节 */
        val imageRId: String = "",
        /** IMAGE：图片文件名，如 "image1.png" */
        val imageFileName: String = "",
        /** IMAGE：期望宽度（pt） */
        val imageWidth: Float = 0f,
        /** IMAGE：期望高度（pt） */
        val imageHeight: Float = 0f,
    ) {
        val isMath: Boolean get() = kind == Kind.MATH
        val isTable: Boolean get() = kind == Kind.TABLE
        val isImage: Boolean get() = kind == Kind.IMAGE
    }

    data class Result(
        val title: String = "",
        val blocks: List<Block> = emptyList(),
        /** 图片关系 ID → 图片字节，供上层保存为资源 */
        val images: Map<String, ByteArray> = emptyMap(),
    )

    /** 把 [Result] 的 Block 列表转成内核 [DocBlock] 列表，供 app 层渲染与导出复用。 */
    fun toDocBlocks(result: Result): List<DocBlock> {
        return result.blocks.map { blk ->
            when (blk.kind) {
                Kind.TEXT -> DocBlock.Text(blk.text, blk.style)
                Kind.MATH -> DocBlock.Math(blk.text, display = true)
                Kind.TABLE -> DocBlock.Table(blk.rows)
                Kind.IMAGE -> DocBlock.Image(blk.imageFileName, blk.imageWidth, blk.imageHeight)
            }
        }
    }

    /** 解析 docx 字节流；失败返回 null */
    fun parse(bytes: ByteArray): Result? {
        val xml = readEntry(bytes, "word/document.xml") ?: return null
        val relsXml = readEntry(bytes, "word/_rels/document.xml.rels")
        val rels = relsXml?.let { parseRels(it) } ?: emptyMap() // rId → 文件名

        return try {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                isExpandEntityReferences = false
                try {
                    setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
                } catch (e: Exception) { }
            }
            val doc = factory.newDocumentBuilder().parse(ByteArrayInputStream(xml))
            val body = child(doc.documentElement, "body") ?: return null
            val blocks = ArrayList<Block>()
            val images = mutableMapOf<String, ByteArray>()

            var n = body.firstChild
            while (n != null) {
                if (n is Element) {
                    when (local(n)) {
                        "p" -> {
                            for (blk in parseParagraph(n, rels)) {
                                blocks.add(blk)
                                if (blk.kind == Kind.IMAGE && blk.imageRId.isNotEmpty()) {
                                    val fileName = rels[blk.imageRId] ?: ""
                                    if (fileName.isNotEmpty()) {
                                        readEntry(bytes, "word/media/$fileName")?.let { data ->
                                            images[blk.imageRId] = data
                                        }
                                    }
                                }
                            }
                        }
                        "tbl" -> {
                            val rows = parseTable(n)
                            if (rows.isNotEmpty()) blocks.add(Block(Kind.TABLE, rows = rows))
                        }
                    }
                }
                n = n.nextSibling
            }

            Result("", blocks, images.toMap())
        } catch (e: Exception) {
            System.err.println("DocxReader 解析失败: ${e.message}")
            null
        }
    }

    // ---------------- 段落 ----------------

    /** 解析一个段落，可能产出多个块（文本 + 公式可共存）。 */
    private fun parseParagraph(p: Element, rels: Map<String, String>): List<Block> {
        val style = parseParagraphStyle(p)

        val textSb = StringBuilder()
        val mathSb = StringBuilder()
        var imageRId = ""
        var imageFileName = ""
        var imageW = 0f
        var imageH = 0f

        var node = p.firstChild
        while (node != null) {
            if (node is Element) {
                val ln = local(node)
                when (ln) {
                    "drawing" -> {
                        // 遍历所有属性，找 embed/link（r:embed 可能带命名空间前缀）
                        val rId = (0 until node.attributes.length)
                            .map { node.attributes.item(it) }
                            .firstOrNull { it.localName == "embed" || it.localName == "link" }
                            ?.nodeValue
                        if (rId != null) {
                            imageRId = rId
                            imageFileName = rels[rId] ?: ""
                            extractImageSize(node)?.let { size ->
                                imageW = size.first
                                imageH = size.second
                            }
                        }
                    }
                    "oMath" -> {
                        // Word 原生公式：其内部的 m:t 进入 mathSb
                        collectRun(node, true, textSb, mathSb)
                    }
                    else -> {
                        // w:r 等统一走递归收集
                        collectRun(node, false, textSb, mathSb)
                    }
                }
            }
            node = node.nextSibling
        }

        val out = mutableListOf<Block>()
        val t = textSb.toString().trim()
        val m = mathSb.toString().trim()
        if (t.isNotEmpty()) out.add(Block(Kind.TEXT, text = t, style = style))
        if (m.isNotEmpty()) out.add(Block(Kind.MATH, text = m, style = style))
        if (imageRId.isNotEmpty()) {
            out.add(Block(Kind.IMAGE, imageRId = imageRId, imageFileName = imageFileName,
                imageWidth = imageW, imageHeight = imageH))
        }
        return out
    }

    /** 从 w:drawing 里提取图片尺寸（EMU → pt），失败返回 null。 */
    private fun extractImageSize(drawing: Element): Pair<Float, Float>? {
        val graphic = child(drawing, "graphic") ?: return null
        val graphicData = child(graphic, "graphicData") ?: return null
        if (graphicData.attribute("uri")?.contains("picture") != true) return null
        val pic = child(graphicData, "pic") ?: return null
        val spPr = child(pic, "spPr") ?: return null
        val xfrm = child(spPr, "xfrm") ?: return null
        val extEl = child(xfrm, "ext") ?: return null
        val cx = extEl.attributeInt("cx", 0L)
        val cy = extEl.attributeInt("cy", 0L)
        if (cx <= 0 || cy <= 0) return null
        // EMU (English Metric Units)：1 英寸 = 914400 EMU，72 pt = 1 英寸
        val w = (cx / 914400.0 * 72.0).toFloat()
        val h = (cy / 914400.0 * 72.0).toFloat()
        return Pair(w, h)
    }

    private fun collectRun(el: Element, inMath: Boolean, text: StringBuilder, math: StringBuilder) {
        var node = el.firstChild
        while (node != null) {
            if (node is Element) {
                val ln = local(node)
                val isMath = inMath || ln == "oMath"
                when {
                    ln == "t" && isMath -> math.append(node.textContent)
                    ln == "t" -> text.append(node.textContent)
                    ln == "tab" && !isMath -> text.append("\t")
                    (ln == "br" || ln == "cr") && !isMath -> text.append("\n")
                    else -> collectRun(node, isMath, text, math)
                }
            }
            node = node.nextSibling
        }
    }

    // ---------------- 段落样式 ----------------

    private fun parseParagraphStyle(p: Element): TextStyle {
        val pPr = child(p, "pPr") ?: return TextStyle()

        var alignment = "left"
        val jc = child(pPr, "jc")
        if (jc != null) {
            alignment = when (jc.attributeW("val")) {
                "center" -> "center"
                "right" -> "right"
                "both", "justify" -> "justify"
                else -> "left"
            }
        }

        // 默认样式
        var fontFamily = "serif"
        var fontSize = 12f
        var color = 0xFF000000.toInt()
        var bold = false
        var italic = false

        val rPr = child(pPr, "rPr")
        if (rPr != null) {
            val rFonts = child(rPr, "rFonts")
            if (rFonts != null) {
                fontFamily = rFonts.attributeW("ascii")
                    ?: rFonts.attributeW("hAnsi")
                    ?: "serif"
                when (fontFamily) {
                    "Calibri", "Arial", "Helvetica", "sans-serif" -> fontFamily = "sans"
                    "Courier New", "Consolas", "monospace" -> fontFamily = "mono"
                    "SimSun", "宋体", "Microsoft YaHei", "微软雅黑" -> fontFamily = "cjk"
                }
            }
            val sz = child(rPr, "sz")
            if (sz != null) {
                val v = sz.attributeIntW("val", 24L) // half-points，24 = 12pt
                fontSize = (v / 2.0).toFloat().coerceAtLeast(1f)
            }
            val colorEl = child(rPr, "color")
            if (colorEl != null) {
                val valStr = colorEl.attributeW("val")
                if (valStr != null) {
                    color = parseColor(valStr)
                }
            }
            if (child(rPr, "b") != null || child(rPr, "bCs") != null) bold = true
            if (child(rPr, "i") != null || child(rPr, "iCs") != null) italic = true
        }

        return TextStyle(fontFamily, fontSize, color, bold, italic, alignment)
    }

    // ---------------- 表格 ----------------

    private fun parseTable(tbl: Element): List<List<String>> {
        val rows = ArrayList<List<String>>()
        var node = tbl.firstChild
        while (node != null) {
            if (node is Element && local(node) == "tr") {
                val cells = ArrayList<String>()
                var c = node.firstChild
                while (c != null) {
                    if (c is Element && local(c) == "tc") {
                        val cellText = StringBuilder()
                        collectTextOnly(c, cellText)
                        cells.add(cellText.toString().trim())
                    }
                    c = c.nextSibling
                }
                rows.add(cells)
            }
            node = node.nextSibling
        }
        return rows
    }

    private fun collectTextOnly(el: Element, sb: StringBuilder) {
        var node = el.firstChild
        while (node != null) {
            if (node is Element) {
                val ln = local(node)
                if (ln == "t") sb.append(node.textContent)
                else if (ln == "tab") sb.append("\t")
                else if (ln == "br" || ln == "cr") sb.append("\n")
                else collectTextOnly(node, sb)
            } else if (node.nodeType == org.w3c.dom.Node.TEXT_NODE) {
                sb.append(node.textContent)
            }
            node = node.nextSibling
        }
    }

    // ---------------- 工具 ----------------

    private val W_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
    private val R_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

    private fun Element.attributeW(name: String): String? = attributeNS(W_NS, name)

    private fun Element.attributeNS(namespace: String, name: String): String? {
        var n = getAttributeNS(namespace, name)
        if (n.isEmpty()) n = getAttribute(name)
        return if (n.isEmpty()) null else n
    }

    private fun Element.attributeIntW(name: String, default: Long): Long {
        val v = getAttributeNS(W_NS, name)
        return if (v.isEmpty()) default else v.toLongOrNull() ?: default
    }

    private fun Element.attribute(name: String): String? {
        val v = getAttribute(name)
        return if (v.isEmpty()) null else v
    }

    private fun Element.attributeInt(name: String, default: Long): Long {
        val v = getAttribute(name)
        return if (v.isEmpty()) default else v.toLongOrNull() ?: default
    }

    private fun parseColor(hex: String): Int {
        var h = hex.trim().removePrefix("#")
        if (h.length == 6) h = "FF" + h
        return if (h.length == 8) {
            try {
                java.lang.Long.parseLong(h, 16).toInt()
            } catch (e: NumberFormatException) {
                0xFF000000.toInt()
            }
        } else 0xFF000000.toInt()
    }

    private fun local(e: Element): String =
        e.localName ?: e.nodeName.substringAfter(':')

    private fun child(parent: Element, name: String): Element? {
        var n = parent.firstChild
        while (n != null) {
            if (n is Element && local(n) == name) return n
            n = n.nextSibling
        }
        return null
    }

    /** 解析 document.xml.rels，返回 rId → Target 文件名 */
    private fun parseRels(xml: ByteArray): Map<String, String> {
        return try {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                isExpandEntityReferences = false
            }
            val doc = factory.newDocumentBuilder().parse(ByteArrayInputStream(xml))
            val out = mutableMapOf<String, String>()
            var n = doc.documentElement?.firstChild
            while (n != null) {
                if (n is Element && local(n) == "Relationship") {
                    val id = n.attribute("Id") ?: ""
                    val target = n.attribute("Target") ?: ""
                    val file = target.substringAfterLast('/')
                    if (id.isNotEmpty() && file.isNotEmpty()) out[id] = file
                }
                n = n.nextSibling
            }
            out.toMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /** 从 zip 中取出指定条目 */
    private fun readEntry(bytes: ByteArray, name: String): ByteArray? {
        ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
            var e = z.nextEntry
            while (e != null) {
                if (e.name == name) {
                    val bos = ByteArrayOutputStream()
                    val buf = ByteArray(16384)
                    while (true) {
                        val n = z.read(buf)
                        if (n < 0) break
                        bos.write(buf, 0, n)
                    }
                    return bos.toByteArray()
                }
                e = z.nextEntry
            }
        }
        return null
    }
}
