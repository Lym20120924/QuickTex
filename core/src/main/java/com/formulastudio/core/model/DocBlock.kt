package com.formulastudio.core.model

/**
 * 统一的文档块模型（阶段 3+）。
 *
 * 一个文档 = 标题 + 若干 [DocBlock]。五种块覆盖了办公场景：
 * 文字 / 公式 / 图形 / 表格 / 图片。内核只描述“文档是什么”，
 * 渲染与导出由上层决定（Canvas / SVG / PDF / Word / Excel）。
 */

/** 段落文字样式：字体 / 字号 / 颜色 / 粗斜体 / 对齐 */
data class TextStyle(
    /** 字体族：serif / sans / mono / cjk 等，上层自行映射到具体 Typeface */
    val fontFamily: String = "serif",
    /** 字号（pt） */
    val fontSize: Float = 12f,
    /** 文字颜色（ARGB Int，0xFF000000 为黑） */
    val color: Int = 0xFF000000.toInt(),
    /** 是否粗体 */
    val bold: Boolean = false,
    /** 是否斜体 */
    val italic: Boolean = false,
    /** 对齐方式：left / center / right / justify */
    val alignment: String = "left",
)

sealed class DocBlock {
    /** 普通文字段落，带样式 */
    data class Text(val text: String, val style: TextStyle = TextStyle()) : DocBlock()

    /** 行内或独立公式，内容为 LaTeX 原文 */
    data class Math(val latex: String, val display: Boolean = true) : DocBlock()

    /** TikZ / pgfplots 等图形 */
    data class Figure(val kind: String, val source: String) : DocBlock()

    /** 表格（行 × 列） */
    data class Table(val rows: List<List<String>>) : DocBlock()

    /** 图片资源：src 为资源名或路径，width/height 为 pt 单位的期望尺寸 */
    data class Image(val src: String, val width: Float, val height: Float) : DocBlock()
}

/** 一份文档 */
data class Document(
    val title: String = "",
    val blocks: List<DocBlock> = emptyList()
)
