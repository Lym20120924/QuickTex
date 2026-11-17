package com.formulastudio.core.doc

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/**
 * docx 读取器（阶段 v4.0：Office 双向）。
 *
 * docx 本质是个 ZIP，正文在 `word/document.xml`。
 * 本解析器把正文拆成 **文字块 / 公式块**：
 * - `w:p` 段落 → 文字
 * - `m:oMath`（Word 原生公式，正是本项目导出的 OMML）→ 公式（取 `m:t` 文本）
 *
 * 纯 Kotlin，不依赖 Android，因此可以在单元测试里验证。
 */
object DocxReader {

    enum class Kind { TEXT, MATH }

    data class Block(val kind: Kind, val text: String) {
        val isMath: Boolean get() = kind == Kind.MATH
    }

    data class Result(
        val title: String = "",
        val blocks: List<Block> = emptyList()
    )

    /** 解析 docx 字节流；失败返回 null */
    fun parse(bytes: ByteArray): Result? {
        val xml = readEntry(bytes, "word/document.xml") ?: return null
        return try {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                isExpandEntityReferences = false
                // Android 的解析器不一定支持该 feature，失败也不能影响解析
                try {
                    setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
                } catch (e: Exception) {
                }
            }
            val doc = factory.newDocumentBuilder().parse(ByteArrayInputStream(xml))
            val body = child(doc.documentElement, "body") ?: return null
            val blocks = ArrayList<Block>()
            for (p in descendants(body, "p")) {
                val text = StringBuilder()
                val math = StringBuilder()
                collect(p, false, text, math)
                val t = text.toString().trim()
                val m = math.toString().trim()
                if (t.isNotEmpty()) blocks.add(Block(Kind.TEXT, t))
                if (m.isNotEmpty()) blocks.add(Block(Kind.MATH, m))
            }
            Result("", blocks)
        } catch (e: Exception) {
            android.util.Log.e("DocxReader", "解析失败", e)
            null
        }
    }

    // ---------------- 遍历 ----------------

    private fun collect(el: Element, inMath: Boolean, text: StringBuilder, math: StringBuilder) {
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
                    else -> collect(node, isMath, text, math)
                }
            }
            node = node.nextSibling
        }
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

    /** 按文档顺序收集所有后代中指定标签的元素 */
    private fun descendants(root: Element, name: String): List<Element> {
        val out = ArrayList<Element>()
        fun walk(e: Element) {
            var n = e.firstChild
            while (n != null) {
                if (n is Element) {
                    if (local(n) == name) out.add(n)
                    walk(n)
                }
                n = n.nextSibling
            }
        }
        walk(root)
        return out
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
                z.nextEntry
                e = z.nextEntry
            }
        }
        return null
    }
}
