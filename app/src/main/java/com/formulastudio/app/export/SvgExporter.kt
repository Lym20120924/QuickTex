package com.formulastudio.app.export

import android.content.Context
import java.io.File
import java.io.FileOutputStream

/**
 * 把公式导出为 SVG 矢量图。
 *
 * SVG 内容由 [com.formulastudio.app.editor.FormulaEditorView.renderToSvg] 生成
 * （复用与屏幕完全相同的排版结果，纯矢量、可无限放大）。
 */
object SvgExporter {

    /** 保存到应用缓存目录，返回文件；失败返回 null */
    fun save(context: Context, svg: String): File? {
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
