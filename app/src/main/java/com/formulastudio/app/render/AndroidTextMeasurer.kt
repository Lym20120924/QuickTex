package com.formulastudio.app.render

import android.content.res.Resources
import android.graphics.Paint
import android.graphics.Typeface
import com.formulastudio.core.render.TextMeasurer
import com.formulastudio.core.render.TextStyle

/** 用 Android [Paint] 实现 [TextMeasurer]（供布局算法复用现有字体度量） */
class AndroidTextMeasurer(private val res: Resources) : TextMeasurer {

    private fun paint(size: Float, style: TextStyle): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        typeface = typefaceFor(res, style)
    }

    override fun measure(text: String, size: Float, style: TextStyle): Float =
        paint(size, style).measureText(text)

    override fun lineHeight(size: Float, style: TextStyle): Float {
        val p = paint(size, style)
        return p.fontMetrics.descent - p.fontMetrics.ascent
    }

    override fun ascent(size: Float, style: TextStyle): Float =
        -paint(size, style).fontMetrics.ascent

    override fun descent(size: Float, style: TextStyle): Float =
        paint(size, style).fontMetrics.descent
}
