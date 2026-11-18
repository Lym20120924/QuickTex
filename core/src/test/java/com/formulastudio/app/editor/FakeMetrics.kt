package com.formulastudio.app.editor

import com.formulastudio.core.render.TextStyle

/**
 * 测试用度量：确定性、不依赖 Android。
 *
 * 每个字符宽 0.5em，行高 1.2em，ascent 0.9em，descent 0.3em，最小描边 1px。
 */
class FakeMetrics : LayoutMetrics {

    override val minStrokeWidth: Float = 1f

    override fun measure(text: String, size: Float, style: TextStyle): Float =
        text.length * size * 0.5f

    override fun lineHeight(size: Float, style: TextStyle): Float = size * 1.2f

    override fun ascent(size: Float, style: TextStyle): Float = size * 0.9f

    override fun descent(size: Float, style: TextStyle): Float = size * 0.3f
}
