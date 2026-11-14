package com.formulastudio.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.formulastudio.app.doc.BlockRenderer
import com.formulastudio.app.doc.LatexDocument
import com.formulastudio.app.util.Prefs
import java.io.File
import java.io.FileOutputStream

/**
 * LaTeX 文档渲染：把整份 .tex 文档拆成文本 / 公式 / 图形块并逐块渲染。
 * 公式与图形都能导出 PNG / SVG，也可以拼成一张长图。
 */
class DocumentActivity : AppCompatActivity() {

    private lateinit var input: TextView
    private lateinit var list: LinearLayout
    private lateinit var status: TextView

    /** 渲染结果：文本或位图 */
    private data class Item(val isText: Boolean, val text: String, val bitmap: Bitmap?)
    private val items = ArrayList<Item>()
    private val mathLatex = ArrayList<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        com.formulastudio.app.util.Prefs.applyDisplayMode(this)
        setTheme(com.formulastudio.app.util.Prefs.themeRes(this))
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_document)
        input = findViewById(R.id.doc_input)
        list = findViewById(R.id.doc_list)
        status = findViewById(R.id.doc_status)

        findViewById<TextView>(R.id.doc_back).setOnClickListener { finish() }
        findViewById<TextView>(R.id.doc_render).setOnClickListener { render() }
        findViewById<TextView>(R.id.doc_export).setOnClickListener { exportSvg() }
        findViewById<TextView>(R.id.doc_copy).setOnClickListener { copyAll() }

        if (intent?.getStringExtra("latex") != null) {
            input.text = intent.getStringExtra("latex")
            render()
        }
    }

    // ---------- 渲染 ----------

    private fun render() {
        val src = input.text?.toString() ?: ""
        if (src.isBlank()) {
            toast(R.string.document_empty)
            return
        }
        toast(R.string.document_rendering)
        Thread {
            // 解析可以放后台；但 View / Bitmap 必须在主线程创建，否则会渲染成空白
            val blocks = LatexDocument.parse(src)
            runOnUiThread {
                val rendered = ArrayList<Item>()
                val latex = ArrayList<String>()
                for (b in blocks) {
                    when (b.type) {
                        LatexDocument.Type.TEXT -> {
                            if (b.text.isNotBlank()) rendered.add(Item(true, b.text, null))
                        }
                        LatexDocument.Type.MATH -> {
                            latex.add(b.text)
                            // 纯 Canvas 自绘引擎（无 WebView），同步渲染
                            rendered.add(
                                Item(false, b.text, BlockRenderer.mathBitmap(this, b.text, b.inline))
                            )
                        }
                        LatexDocument.Type.FIGURE -> {
                            rendered.add(
                                Item(false, b.text, BlockRenderer.figureBitmap(this, b.figure, b.text))
                            )
                        }
                    }
                }
                items.clear(); items.addAll(rendered)
                mathLatex.clear(); mathLatex.addAll(latex)
                rebuildViews()
                status.text = getString(
                    R.string.document_math_count, latex.size,
                    rendered.count { !it.isText }
                )
            }
        }.start()
    }

    private fun rebuildViews() {
        list.removeAllViews()
        items.forEachIndexed { idx, item ->
            if (item.isText) {
                list.addView(textView(item.text))
            } else {
                val box = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(0, dp(8), 0, dp(8))
                }
                if (item.bitmap != null) {
                    val iv = ImageView(this).apply {
                        setImageBitmap(item.bitmap)
                        adjustViewBounds = true
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        // 按原始尺寸居中显示，不拉伸（避免小公式被拉巨大 / 大公式被压糊）
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { gravity = Gravity.CENTER }
                    }
                    box.addView(iv)
                } else {
                    box.addView(textView(getString(R.string.document_render_failed)))
                }
                // 复制该公式
                val copyBtn = TextView(this).apply {
                    text = getString(R.string.document_copy)
                    textSize = 12f
                    gravity = Gravity.CENTER
                    setPadding(dp(8), dp(2), dp(8), dp(2))
                    setOnClickListener {
                        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("latex", item.text))
                        toast(R.string.copied)
                    }
                }
                box.addView(copyBtn)
                list.addView(box)
            }
        }
    }

    private fun textView(t: String): TextView = TextView(this).apply {
        text = t
        textSize = 15f
        typeface = Typeface.SERIF
        setPadding(0, dp(6), 0, dp(6))
    }

    // ---------- 导出 SVG（真矢量） ----------

    private fun exportSvg() {
        if (items.isEmpty()) {
            toast(R.string.document_empty)
            return
        }
        Thread {
            val margin = 24f
            val docW = 1200f
            data class Piece(val isText: Boolean, val text: String, val bmp: android.graphics.Bitmap?)
            val pieces = ArrayList<Piece>()
            var totalH = margin
            for (it in items) {
                if (it.isText) {
                    pieces.add(Piece(true, it.text, null))
                    totalH += it.text.split("\n").size * 36f + 18f
                } else {
                    pieces.add(Piece(false, it.text, it.bitmap))
                    if (it.bitmap != null) {
                        val sc = ((docW - margin * 2) / it.bitmap.width).coerceAtMost(1f)
                        totalH += it.bitmap.height * sc + 20f
                    } else {
                        totalH += 60f
                    }
                }
            }
            val docH = totalH + margin
            val sb = StringBuilder()
            sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(docW.toInt())
                .append("\" height=\"").append(docH.toInt())
                .append("\" viewBox=\"0 0 ").append(docW.toInt()).append(' ')
                .append(docH.toInt()).append("\">\n")
            sb.append("<rect width=\"100%\" height=\"100%\" fill=\"#FFFFFF\"/>\n")
            var y = margin
            for (pc in pieces) {
                if (pc.isText) {
                    for (line in pc.text.split("\n")) {
                        y += 28f
                        sb.append("<text x=\"").append(margin).append("\" y=\"").append(y)
                            .append("\" font-family=\"serif\" font-size=\"26\" fill=\"#111111\">")
                            .append(escapeXml(line)).append("</text>\n")
                        y += 8f
                    }
                    y += 10f
                } else {
                    val b = pc.bmp
                    if (b != null && b.width > 0 && b.height > 0) {
                        val sc = ((docW - margin * 2) / b.width).coerceAtMost(1f)
                        val w = b.width * sc
                        val h = b.height * sc
                        val bos = java.io.ByteArrayOutputStream()
                        b.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, bos)
                        val b64 = android.util.Base64.encodeToString(
                            bos.toByteArray(), android.util.Base64.NO_WRAP
                        )
                        sb.append("<image href=\"data:image/png;base64,").append(b64)
                            .append("\" x=\"").append(margin).append("\" y=\"").append(y)
                            .append("\" width=\"").append(w).append("\" height=\"").append(h)
                            .append("\"/>\n")
                        y += h + 20f
                    } else {
                        y += 28f
                        sb.append("<text x=\"").append(margin).append("\" y=\"").append(y)
                            .append("\" font-family=\"serif\" font-size=\"22\" fill=\"#999999\">")
                            .append(escapeXml(pc.text)).append("</text>\n")
                        y += 32f
                    }
                }
            }
            sb.append("</svg>\n")
            val dir = java.io.File(cacheDir, "shared").apply { mkdirs() }
            val f = java.io.File(dir, "document_${System.currentTimeMillis()}.svg")
            f.writeText(sb.toString(), Charsets.UTF_8)
            runOnUiThread {
                try {
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        this, "com.formulastudio.app.fileprovider", f
                    )
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "image/svg+xml"
                        putExtra(android.content.Intent.EXTRA_STREAM, uri)
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(android.content.Intent.createChooser(send, getString(R.string.document_export)))
                } catch (e: Exception) {
                    toast(R.string.document_export_failed)
                }
            }
        }.start()
    }

    /** 从 <svg ...> 标签取 width / height（注意别把 <?xml ?> 当成头部） */
    private fun parseSvgSize(svg: String): Pair<Float, Float>? {
        val m = Regex("<svg[^>]*>").find(svg) ?: return null
        val w = Regex("width=\"([0-9.]+)\"").find(m.value)?.groupValues?.get(1)?.toFloatOrNull()
        val h = Regex("height=\"([0-9.]+)\"").find(m.value)?.groupValues?.get(1)?.toFloatOrNull()
        return if (w != null && h != null && w > 0f && h > 0f) w to h else null
    }

    /** 取出 <svg> 标签之后、</svg> 之前的内部内容 */
    private fun svgInner(svg: String): String {
        val m = Regex("<svg[^>]*>").find(svg) ?: return svg
        val start = m.range.last + 1
        val end = svg.lastIndexOf("</svg>").coerceAtLeast(start)
        return svg.substring(start, end)
    }

    private fun escapeXml(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun wrapText(text: String, paint: Paint, maxW: Float): List<String> {
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

    // ---------- 复制 ----------

    private fun copyAll() {
        if (mathLatex.isEmpty()) {
            toast(R.string.document_empty)
            return
        }
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("latex", mathLatex.joinToString("\n\n")))
        Toast.makeText(
            this, getString(R.string.document_copy_done, mathLatex.size), Toast.LENGTH_SHORT
        ).show()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun toast(res: Int) = Toast.makeText(this, res, Toast.LENGTH_SHORT).show()
}
