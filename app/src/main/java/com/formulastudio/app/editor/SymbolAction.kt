package com.formulastudio.app.editor

/** 符号键盘按钮对应的编辑动作 */
sealed class SymbolAction {
    object Fraction : SymbolAction()
    object Sqrt : SymbolAction()
    object NRoot : SymbolAction()
    object Sup : SymbolAction()
    object Sub : SymbolAction()
    object BothScripts : SymbolAction()
    object Paren : SymbolAction()
    object Bracket : SymbolAction()
    object Curly : SymbolAction()
    object Abs : SymbolAction()
    object SumLimits : SymbolAction()
    object ProdLimits : SymbolAction()
    object IntLimits : SymbolAction()
    object Matrix2 : SymbolAction()
    object Matrix3 : SymbolAction()
    object Det2 : SymbolAction()
    object Cases2 : SymbolAction()
    object Cases3 : SymbolAction()
    object Vec : SymbolAction()
    object Hat : SymbolAction()
    object Bar : SymbolAction()
    object Dot : SymbolAction()
    object Tilde : SymbolAction()
    object WideHat : SymbolAction()
    object OverArrow : SymbolAction()
    object Lim : SymbolAction()
    object Ce : SymbolAction()
    object Prime : SymbolAction()

    /** 插入命令符号（\times 等） */
    data class Command(val latex: String) : SymbolAction()

    /** 插入普通文本 */
    data class Text(val s: String) : SymbolAction()

    /** 插入化学式（\ce{...} 内容） */
    data class ChemText(val s: String) : SymbolAction()

    /** 插入换行（分行公式） */
    object NewLine : SymbolAction()

    object MoveLeft : SymbolAction()
    object MoveRight : SymbolAction()
    object MoveUp : SymbolAction()
    object MoveDown : SymbolAction()
    object Undo : SymbolAction()
    object Redo : SymbolAction()
    object Backspace : SymbolAction()
    object Clear : SymbolAction()

    /** 唤出/收起系统输入法 */
    object ToggleIme : SymbolAction()
}
