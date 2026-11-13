package com.formulastudio.app.export

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * 用内置的 KaTeX（离线 WebView）把 LaTeX 渲染成 SVG 矢量图。
 *
 * KaTeX 的 SVG 输出是纯路径（不依赖字体），所以导出的 .svg 文件体积小、可无限放大，
 * 并且可以在任何支持 SVG 的软件里打开。
 */
object SvgExporter {

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * 渲染并保存为 .svg 文件，回调返回文件（失败返回 null）。
     * 必须在主线程调用。
     */
    fun export(activity: Activity, latex: String, callback: (File?) -> Unit) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { export(activity, latex, callback) }
            return
        }
        val appContext = activity.applicationContext
        val web = WebView(activity)
        web.settings.javaScriptEnabled = true
        web.settings.allowFileAccess = true
        // 1×1 的离屏 WebView：只用来跑 JS，不占界面
        activity.addContentView(web, ViewGroup.LayoutParams(1, 1))

        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                val js = "(function(){try{return [katex.renderToString(" +
                    "${JSONObject.quote(latex)},{output:'svg',throwOnError:false})];" +
                    "}catch(e){return null;}})();"
                view.evaluateJavascript(js) { raw ->
                    var file: File? = null
                    val svg = try {
                        if (raw != null && raw != "null") JSONArray(raw).getString(0) else null
                    } catch (e: Exception) {
                        null
                    }
                    if (svg != null && svg.trimStart().startsWith("<svg")) {
                        file = save(appContext, svg)
                    }
                    view.post {
                        (view.parent as? ViewGroup)?.removeView(view)
                        view.destroy()
                    }
                    callback(file)
                }
            }

            override fun onReceivedError(
                view: WebView,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                view.post {
                    (view.parent as? ViewGroup)?.removeView(view)
                    view.destroy()
                }
                callback(null)
            }
        }
        web.loadUrl("file:///android_asset/preview.html")
    }

    /** 保存到应用缓存目录，返回文件；失败返回 null */
    private fun save(context: Context, svg: String): File? {
        return try {
            val dir = File(context.cacheDir, "shared").apply { mkdirs() }
            val f = File(dir, "formula_${System.currentTimeMillis()}.svg")
            FileOutputStream(f).use { it.write(svg.toByteArray(Charsets.UTF_8)) }
            f
        } catch (e: Exception) {
            null
        }
    }
}
