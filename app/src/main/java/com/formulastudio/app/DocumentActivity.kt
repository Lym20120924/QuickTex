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
        findViewById<TextView>(R.id.doc_export).setOnClickListener { exportLongImage() }
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
                            val bmp = BlockRenderer.mathBitmap(this, b.text, b.inline)
                            latex.add(b.text)
                            rendered.add(Item(false, b.text, bmp))
                        }
                        LatexDocument.Type.FIGURE -> {
                            val bmp = BlockRenderer.figureBitmap(this, b.figure, b.text)
                            rendered.add(Item(false, b.text, bmp))
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

    // ---------- 导出长图 ----------

    private fun exportLongImage() {
        if (items.isEmpty()) {
            toast(R.string.document_empty)
            return
        }
        toast(R.string.document_rendering)
        Thread {
            val contentWidth = 900
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 26f
                typeface = Typeface.SERIF
            }
            // 先算总高
            var totalH = 0f
            val scaled = ArrayList<Triple<Boolean, String, Bitmap?>>()
            for (it in items) {
                if (it.isText) {
                    val lines = wrapText(it.text, paint, contentWidth - 40f)
                    val h = lines.size * (paint.textSize + 10f) + 18f
                    scaled.add(Triple(true, it.text, null))
                    totalH += h
                } else {
                    val bmp = it.bitmap ?: continue
                    val s = contentWidth.toFloat() / bmp.width
                    scaled.add(Triple(false, it.text, bmp))
                    totalH += bmp.height * s + 18f
                }
            }
            val bmp = Bitmap.createBitmap(
                contentWidth, totalH.toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888
            )
            val c = Canvas(bmp)
            c.drawColor(Color.WHITE)
            var y = 12f
            for (t in scaled) {
                if (t.first) {
                    for (line in wrapText(t.second, paint, contentWidth - 40f)) {
                        c.drawText(line, 20f, y + paint.textSize, paint)
                        y += paint.textSize + 10f
                    }
                    y += 6f
                } else {
                    val b = t.third ?: continue
                    val s = contentWidth.toFloat() / b.width
                    val h = b.height * s
                    c.save()
                    c.translate(0f, y)
                    c.scale(s, s)
                    c.drawBitmap(b, 0f, 0f, null)
                    c.restore()
                    y += h + 12f
                }
            }
            val f = File(cacheDir, "shared").apply { mkdirs() }
                .let { File(it, "document_${System.currentTimeMillis()}.png") }
            FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            runOnUiThread {
                try {
                    val uri = FileProvider.getUriForFile(
                        this, "com.formulastudio.app.fileprovider", f
                    )
                    val i = Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(i, getString(R.string.document_export)))
                } catch (e: Exception) {
                    toast(R.string.document_export_failed)
                }
            }
        }.start()
    }

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
