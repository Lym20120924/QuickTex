package com.formulastudio.app.render

import android.content.Context
import android.util.Base64

/** 内嵌 KaTeX 字体（woff2 → base64）的 CSS，让 SVG 在任何设备上都长得一样 */
fun katexFontFaceCss(context: Context): String {
    val fonts = listOf(
        Triple("KaTeX_Main", "KaTeX_Main-Regular", "normal"),
        Triple("KaTeX_Main", "KaTeX_Main-Italic", "italic"),
        Triple("KaTeX_Math", "KaTeX_Math-Italic", "italic"),
        Triple("KaTeX_AMS", "KaTeX_AMS-Regular", "normal"),
        Triple("KaTeX_Size1", "KaTeX_Size1-Regular", "normal"),
        Triple("KaTeX_Size2", "KaTeX_Size2-Regular", "normal"),
        Triple("KaTeX_Size3", "KaTeX_Size3-Regular", "normal"),
        Triple("KaTeX_Size4", "KaTeX_Size4-Regular", "normal"),
        Triple("KaTeX_Caligraphic", "KaTeX_Caligraphic-Regular", "normal"),
        Triple("KaTeX_Script", "KaTeX_Script-Regular", "normal"),
        Triple("KaTeX_Fraktur", "KaTeX_Fraktur-Regular", "normal"),
        Triple("KaTeX_Typewriter", "KaTeX_Typewriter-Regular", "normal")
    )
    val sb = StringBuilder()
    for ((family, file, style) in fonts) {
        val b64 = try {
            context.assets.open("katex/fonts/$file.woff2").use { it.readBytes() }
                .let { Base64.encodeToString(it, Base64.NO_WRAP) }
        } catch (e: Exception) {
            null
        }
        if (b64 != null) {
            sb.append("@font-face{font-family:'").append(family)
                .append("';font-style:").append(style)
                .append(";src:url(data:font/woff2;base64,").append(b64)
                .append(") format('woff2');}\n")
        }
    }
    return sb.toString()
}
