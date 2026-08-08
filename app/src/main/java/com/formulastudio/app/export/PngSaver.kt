package com.formulastudio.app.export

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream

/** 把画布渲染出的位图保存为 PNG 文件 */
object PngSaver {

    /** 保存到应用缓存目录，返回文件；失败返回 null */
    fun save(context: Context, bitmap: Bitmap): File? {
        return try {
            val dir = File(context.cacheDir, "shared").apply { mkdirs() }
            val f = File(dir, "formula_${System.currentTimeMillis()}.png")
            FileOutputStream(f).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            if (!bitmap.isRecycled) bitmap.recycle()
            f
        } catch (e: Exception) {
            null
        }
    }
}
