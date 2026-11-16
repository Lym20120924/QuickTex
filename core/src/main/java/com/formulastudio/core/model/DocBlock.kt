package com.formulastudio.core.model

/**
 * 统一的文档块模型（阶段 3）。
 *
 * 一个文档 = 标题 + 若干 [DocBlock]。四种块覆盖了办公场景：
 * 文字 / 公式 / 图形 / 表格。内核只描述“文档是什么”，
 * 渲染与导出由上层决定（Canvas / SVG / PDF / Word / Excel）。
 */
sealed class DocBlock {
    /** 普通文字段落 */
    data class Text(val text: String) : DocBlock()

    /** 行内或独立公式，内容为 LaTeX 原文 */
    data class Math(val latex: String, val display: Boolean = true) : DocBlock()

    /** TikZ / pgfplots 等图形 */
    data class Figure(val kind: String, val source: String) : DocBlock()

    /** 表格（行 × 列） */
    data class Table(val rows: List<List<String>>) : DocBlock()
}

/** 一份文档 */
data class Document(
    val title: String = "",
    val blocks: List<DocBlock> = emptyList()
)
