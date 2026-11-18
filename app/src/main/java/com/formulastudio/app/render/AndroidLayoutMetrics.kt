package com.formulastudio.app.render

import android.content.res.Resources
import com.formulastudio.app.editor.LayoutMetrics
import com.formulastudio.core.render.TextStyle

/**
 * Android 端的布局度量实现（阶段 4.4a）。
 *
 * 文字度量交给 [AndroidTextMeasurer]（内部用 Paint），
 * 最小描边宽度取屏幕 density —— 布局几何因此可以留在 core。
 */
class AndroidLayoutMetrics(private val res: Resources) : LayoutMetrics {

    private val measurer = AndroidTextMeasurer(res)

    override val minStrokeWidth: Float = res.displayMetrics.density

    override fun measure(text: String, size: Float, style: TextStyle): Float =
        measurer.measure(text, size, style)

    override fun lineHeight(size: Float, style: TextStyle): Float =
        measurer.lineHeight(size, style)

    override fun ascent(size: Float, style: TextStyle): Float =
        measurer.ascent(size, style)

    override fun descent(size: Float, style: TextStyle): Float =
        measurer.descent(size, style)
}
