package com.formulastudio.app.util

import android.graphics.Bitmap
import kotlin.math.min

/**
 * 轻量高斯模糊（可分离的两次盒式模糊，近似高斯）。
 * 不依赖 RenderScript，兼容所有 API。
 */
object Glass {

    fun blur(source: Bitmap, radius: Int): Bitmap {
        if (radius <= 0) return source
        val w = source.width
        val h = source.height
        if (w == 0 || h == 0) return source

        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)
        val tmp = IntArray(w * h)

        // 先缩小再模糊，速度更快且视觉上足够
        val scale = if (w * h > 200_000) 2 else 1
        val sw = w / scale
        val sh = h / scale
        val small: IntArray =
            if (scale > 1) scaleDown(pixels, w, h, sw, sh) else pixels
        val smallTmp = IntArray(sw * sh)

        val r = (radius / scale).coerceAtLeast(1)
        pass(small, smallTmp, sw, sh, r, horizontal = true)
        pass(smallTmp, small, sw, sh, r, horizontal = false)

        val out: IntArray = if (scale > 1) scaleUp(small, sw, sh, w, h) else small
        val res = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        res.setPixels(out, 0, w, 0, 0, w, h)
        return res
    }

    private fun pass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val len = if (horizontal) w else h
        val cross = if (horizontal) h else w
        for (i in 0 until cross) {
            for (j in 0 until len) {
                var a = 0; var rr = 0; var gg = 0; var bb = 0; var cnt = 0
                val from = maxOf(0, j - r)
                val to = minOf(len - 1, j + r)
                for (k in from..to) {
                    val idx = if (horizontal) i * w + k else k * w + i
                    val c = src[idx]
                    a += (c ushr 24) and 0xFF
                    rr += (c shr 16) and 0xFF
                    gg += (c shr 8) and 0xFF
                    bb += c and 0xFF
                    cnt++
                }
                val idx = if (horizontal) i * w + j else j * w + i
                dst[idx] = ((a / cnt) shl 24) or ((rr / cnt) shl 16) or
                    ((gg / cnt) shl 8) or (bb / cnt)
            }
        }
    }

    private fun scaleDown(src: IntArray, w: Int, h: Int, sw: Int, sh: Int): IntArray {
        val out = IntArray(sw * sh)
        val xRatio = w.toFloat() / sw
        val yRatio = h.toFloat() / sh
        for (y in 0 until sh) {
            for (x in 0 until sw) {
                val sx = (x * xRatio).toInt().coerceIn(0, w - 1)
                val sy = (y * yRatio).toInt().coerceIn(0, h - 1)
                out[y * sw + x] = src[sy * w + sx]
            }
        }
        return out
    }

    private fun scaleUp(src: IntArray, sw: Int, sh: Int, w: Int, h: Int): IntArray {
        val out = IntArray(w * h)
        val xRatio = sw.toFloat() / w
        val yRatio = sh.toFloat() / h
        for (y in 0 until h) {
            for (x in 0 until w) {
                val sx = (x * xRatio).toInt().coerceIn(0, sw - 1)
                val sy = (y * yRatio).toInt().coerceIn(0, sh - 1)
                out[y * w + x] = src[sy * sw + sx]
            }
        }
        return out
    }
}
