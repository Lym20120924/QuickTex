package com.formulastudio.app.render

import android.graphics.Typeface

/**
 * 字体注册表（阶段 4.4a）。
 *
 * 公式编辑器有多套字体（正文 / 斜体变量 / 运算符 / 中文），
 * 把 Paint 转成平台无关的 [com.formulastudio.core.render.TextStyle] 时需要识别它们，
 * 这样 [AndroidTextMeasurer] 才能选回同一字体，保证度量与原来完全一致。
 */
internal object AppFonts {
    @Volatile internal var main: Typeface = Typeface.SERIF
    @Volatile internal var varFont: Typeface = Typeface.SERIF
    @Volatile internal var op: Typeface = Typeface.SERIF
    @Volatile internal var cjk: Typeface = Typeface.SERIF

    /** 由 FormulaEditorView 在创建字体后调用 */
    fun register(main: Typeface, varFont: Typeface, op: Typeface, cjk: Typeface) {
        this.main = main
        this.varFont = varFont
        this.op = op
        this.cjk = cjk
    }
}
