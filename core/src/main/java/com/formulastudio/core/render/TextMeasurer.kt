package com.formulastudio.core.render

/**
 * 文字度量抽象（阶段 4.4a 的第一块接缝）。
 *
 * 公式布局需要三件事：字宽、行高、升降部。
 * 以前这些直接来自 android.graphics.Paint，导致布局逻辑无法离开 Android。
 * 把度量抽象成接口后：
 * - 布局算法可以搬进 core（纯 Kotlin，可测试）
 * - Android 端用 Paint 实现；Compose 端用 TextMeasurer 实现
 *   → 这就是 4.4b（DrawScope 渲染）的前提
 */
interface TextMeasurer {
    /** 文字在给定字号/样式下的宽度（px） */
    fun measure(text: String, size: Float, style: TextStyle): Float

    /** 该字号下的行高（px） */
    fun lineHeight(size: Float, style: TextStyle): Float

    /** 基线以上的高度（px，正值） */
    fun ascent(size: Float, style: TextStyle): Float

    /** 基线以下的高度（px，正值） */
    fun descent(size: Float, style: TextStyle): Float
}
