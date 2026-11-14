package com.formulastudio.app.doc

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONArray
import org.json.JSONObject

/**
 * 用 KaTeX（真 TeX 渲染）把 LaTeX 渲染成位图。
 *
 * 复用一个隐藏 WebView：注入 LaTeX -> 量出内容尺寸 -> 调整 WebView 尺寸 -> draw 到位图。
 * 相比自研排版引擎，KaTeX 覆盖的 LaTeX 命令多得多（amsmath / amssymb 等）。
 */
object KaTeXRenderer {

    private class Job(
        val latex: String,
        val display: Boolean,
        val fontPx: Int,
        val callback: (Bitmap?) -> Unit
    )

    private var web: WebView? = null
    private var pageReady = false
    private var working = false
    private val queue = ArrayDeque<Job>()

    fun init(activity: Activity) {
        if (web != null) return
        val w = WebView(activity)
        w.settings.javaScriptEnabled = true
        w.settings.allowFileAccess = true
        w.visibility = View.INVISIBLE
        activity.addContentView(w, ViewGroup.LayoutParams(1, 1))
        w.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                pageReady = true
                drain()
            }
        }
        w.loadUrl("file:///android_asset/katex/render.html")
        web = w
    }

    fun render(
        activity: Activity,
        latex: String,
        display: Boolean,
        fontPx: Int,
        callback: (Bitmap?) -> Unit
    ) {
        init(activity)
        queue.addLast(Job(latex, display, fontPx, callback))
        drain()
    }

    private fun drain() {
        val w = web ?: return
        if (working || !pageReady) return
        val job = queue.removeFirstOrNull() ?: return
        working = true

        val js = "(function(){var o=document.getElementById('out');" +
            "o.style.fontSize='" + job.fontPx + "px';o.innerHTML='';" +
            "try{katex.render(" + JSONObject.quote(job.latex) + ",o," +
            "{displayMode:" + job.display + ",throwOnError:false,output:'html'});}" +
            "catch(e){o.textContent=" + JSONObject.quote(job.latex) + ";}" +
            "var r=o.getBoundingClientRect();" +
            "return [Math.ceil(r.width),Math.ceil(r.height)];})();"

        w.evaluateJavascript(js) { raw ->
            var cw = 0
            var ch = 0
            try {
                val arr = JSONArray(raw)
                cw = arr.getInt(0)
                ch = arr.getInt(1)
            } catch (e: Exception) {
                // 量取失败，交回回调走回退
            }
            if (cw <= 0 || ch <= 0) {
                done(job, null)
                return@evaluateJavascript
            }
            try {
                val targetW = cw.coerceAtMost(2400)
                val targetH = ch.coerceAtMost(4000)
                val lp = w.layoutParams ?: ViewGroup.LayoutParams(targetW, targetH)
                lp.width = targetW
                lp.height = targetH
                w.layoutParams = lp
                w.measure(
                    View.MeasureSpec.makeMeasureSpec(targetW, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(targetH, View.MeasureSpec.EXACTLY)
                )
                w.layout(0, 0, targetW, targetH)
                val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                val c = Canvas(bmp)
                c.drawColor(Color.WHITE)
                w.draw(c)
                done(job, bmp)
            } catch (e: Exception) {
                done(job, null)
            }
        }
    }

    private fun done(job: Job, bmp: Bitmap?) {
        working = false
        job.callback(bmp)
        drain()
    }
}
