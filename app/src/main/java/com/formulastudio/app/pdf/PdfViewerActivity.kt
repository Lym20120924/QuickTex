package com.formulastudio.app.pdf

import com.formulastudio.app.R

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File

/** 用系统 PdfRenderer 离线查看 PDF（零第三方依赖） */
class PdfViewerActivity : AppCompatActivity() {

    private var renderer: PdfRenderer? = null
    private var fd: ParcelFileDescriptor? = null
    private var page = 0
    private var total = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pdf_viewer)
        val path = intent.getStringExtra(EXTRA_PATH)
        if (path == null) { finish(); return }
        try {
            val file = File(path)
            fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(fd!!)
            total = renderer!!.pageCount
        } catch (e: Exception) {
            finish(); return
        }
        findViewById<TextView>(R.id.pdf_title).text = path.substringAfterLast('/')
        findViewById<ImageButton>(R.id.pdf_prev).setOnClickListener { if (page > 0) { page--; show() } }
        findViewById<ImageButton>(R.id.pdf_next).setOnClickListener { if (page < total - 1) { page++; show() } }
        show()
    }

    private fun show() {
        val r = renderer ?: return
        try {
            r.openPage(page).use { p ->
                val scale = 2
                val bmp = Bitmap.createBitmap(p.width * scale, p.height * scale, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(android.graphics.Color.WHITE)
                p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                findViewById<ImageView>(R.id.pdf_page).setImageBitmap(bmp)
            }
        } catch (e: Exception) { }
        findViewById<TextView>(R.id.pdf_indicator).text = "第 ${page + 1} / $total 页"
    }

    override fun onDestroy() {
        try { renderer?.close() } catch (e: Exception) {}
        try { fd?.close() } catch (e: Exception) {}
        super.onDestroy()
    }

    companion object {
        const val EXTRA_PATH = "path"
    }
}
