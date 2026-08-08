package com.formulastudio.app.keyboard

import com.formulastudio.app.editor.SymbolAction

/** 键盘按钮 */
data class SymbolButton(
    val label: String,
    val action: SymbolAction,
    val big: Boolean = false,
    /** 占位按钮：不显示文字、不画边框，用来把每栏补满 6 列 */
    val spacer: Boolean = false
)

/** 键盘分类页 */
data class SymbolTab(val name: String, val buttons: List<SymbolButton>)

/** 把按钮列表补齐到 6 的倍数（末尾加占位），保证每行 6 个、网格线整齐 */
fun pad6(buttons: List<SymbolButton>): List<SymbolButton> {
    val rem = buttons.size % 6
    if (rem == 0) return buttons
    return buttons + List(6 - rem) { SymbolButton("", SymbolAction.Text(""), spacer = true) }
}

object Symbols {

    val tabs: List<SymbolTab> = listOf(
        // ---------- 基础（运算 + 括号模板，共 24 = 4×6） ----------
        SymbolTab("基础", listOf(
            SymbolButton("+", SymbolAction.Text("+")),
            SymbolButton("−", SymbolAction.Text("-")),
            SymbolButton("×", SymbolAction.Command("\\times")),
            SymbolButton("÷", SymbolAction.Command("\\div")),
            SymbolButton("±", SymbolAction.Command("\\pm")),
            SymbolButton("∓", SymbolAction.Command("\\mp")),
            SymbolButton("·", SymbolAction.Command("\\cdot")),
            SymbolButton("=", SymbolAction.Text("=")),
            SymbolButton("≈", SymbolAction.Command("\\approx")),
            SymbolButton("≡", SymbolAction.Command("\\equiv")),
            SymbolButton("≠", SymbolAction.Command("\\ne")),
            SymbolButton("<", SymbolAction.Text("<")),
            SymbolButton(">", SymbolAction.Text(">")),
            SymbolButton("≤", SymbolAction.Command("\\le")),
            SymbolButton("≥", SymbolAction.Command("\\ge")),
            SymbolButton("≪", SymbolAction.Command("\\ll")),
            SymbolButton("≫", SymbolAction.Command("\\gg")),
            SymbolButton("⊥", SymbolAction.Command("\\perp")),
            SymbolButton("分式", SymbolAction.Fraction, big = true),
            SymbolButton("根号", SymbolAction.Sqrt, big = true),
            SymbolButton("n次根", SymbolAction.NRoot, big = true),
            SymbolButton("| |", SymbolAction.Abs, big = true),
            SymbolButton("∠", SymbolAction.Command("\\angle")),
            SymbolButton("△", SymbolAction.Command("\\triangle")),
        )),

        // ---------- 集合（共 18 = 3×6） ----------
        SymbolTab("集合", listOf(
            SymbolButton("∈", SymbolAction.Command("\\in")),
            SymbolButton("∋", SymbolAction.Command("\\ni")),
            SymbolButton("∉", SymbolAction.Command("\\notin")),
            SymbolButton("⊂", SymbolAction.Command("\\subset")),
            SymbolButton("⊃", SymbolAction.Command("\\supset")),
            SymbolButton("⊄", SymbolAction.Command("\\nsubset")),
            SymbolButton("⊅", SymbolAction.Command("\\nsupset")),
            SymbolButton("⊆", SymbolAction.Command("\\subseteq")),
            SymbolButton("⊇", SymbolAction.Command("\\supseteq")),
            SymbolButton("⊊", SymbolAction.Command("\\subsetneq")),
            SymbolButton("⊋", SymbolAction.Command("\\supsetneq")),
            SymbolButton("∪", SymbolAction.Command("\\cup")),
            SymbolButton("∩", SymbolAction.Command("\\cap")),
            SymbolButton("∅", SymbolAction.Command("\\emptyset")),
            SymbolButton("∁", SymbolAction.Command("\\complement")),
            SymbolButton("∖", SymbolAction.Command("\\setminus")),
            SymbolButton("∌", SymbolAction.Command("\\notni")),
            SymbolButton("⊎", SymbolAction.Command("\\uplus")),
        )),

        // ---------- 关系/逻辑（共 18 = 3×6） ----------
        SymbolTab("关系", listOf(
            SymbolButton("∧", SymbolAction.Command("\\wedge")),
            SymbolButton("∨", SymbolAction.Command("\\vee")),
            SymbolButton("¬", SymbolAction.Command("\\neg")),
            SymbolButton("∀", SymbolAction.Command("\\forall")),
            SymbolButton("∃", SymbolAction.Command("\\exists")),
            SymbolButton("∄", SymbolAction.Command("\\nexists")),
            SymbolButton("∽", SymbolAction.Command("\\sim")),
            SymbolButton("≌", SymbolAction.Command("\\cong")),
            SymbolButton("≃", SymbolAction.Command("\\simeq")),
            SymbolButton("≅", SymbolAction.Command("\\cong")),
            SymbolButton("∴", SymbolAction.Command("\\therefore")),
            SymbolButton("∵", SymbolAction.Command("\\because")),
            SymbolButton("≮", SymbolAction.Command("\\nless")),
            SymbolButton("≯", SymbolAction.Command("\\ngtr")),
            SymbolButton("≇", SymbolAction.Command("\\ncong")),
            SymbolButton("↮", SymbolAction.Text("↮")),
            SymbolButton("⇌", SymbolAction.Text("⇌")),
        )),

        // ---------- 大型运算符（共 18 = 3×6） ----------
        SymbolTab("大运算", listOf(
            SymbolButton("∑", SymbolAction.Command("\\sum")),
            SymbolButton("∏", SymbolAction.Command("\\prod")),
            SymbolButton("∐", SymbolAction.Command("\\coprod")),
            SymbolButton("∫", SymbolAction.Command("\\int")),
            SymbolButton("∬", SymbolAction.Command("\\iint")),
            SymbolButton("∭", SymbolAction.Command("\\iiint")),
            SymbolButton("∮", SymbolAction.Command("\\oint")),
            SymbolButton("∯", SymbolAction.Command("\\oiint")),
            SymbolButton("∰", SymbolAction.Command("\\oiiint")),
            SymbolButton("⋃", SymbolAction.Command("\\bigcup")),
            SymbolButton("⋂", SymbolAction.Command("\\bigcap")),
            SymbolButton("⊕", SymbolAction.Command("\\oplus")),
            SymbolButton("⊗", SymbolAction.Command("\\otimes")),
            SymbolButton("⨀", SymbolAction.Command("\\bigodot")),
            SymbolButton("⨂", SymbolAction.Command("\\bigotimes")),
            SymbolButton("∑上下限", SymbolAction.SumLimits, big = true),
            SymbolButton("∏上下限", SymbolAction.ProdLimits, big = true),
            SymbolButton("∫上下限", SymbolAction.IntLimits, big = true),
        )),

        // ---------- 希腊字母 ----------
        SymbolTab("希腊", greekLetters()),

        // ---------- 分析（极限/微分/常数/集合补集，共 18 = 3×6） ----------
        SymbolTab("分析", listOf(
            SymbolButton("lim", SymbolAction.Lim, big = true),
            SymbolButton("log", SymbolAction.Command("\\log")),
            SymbolButton("ln", SymbolAction.Command("\\ln")),
            SymbolButton("lg", SymbolAction.Command("\\lg")),
            SymbolButton("∂", SymbolAction.Command("\\partial")),
            SymbolButton("∇", SymbolAction.Command("\\nabla")),
            SymbolButton("Δ", SymbolAction.Command("\\Delta")),
            SymbolButton("∞", SymbolAction.Command("\\infty")),
            SymbolButton("∝", SymbolAction.Command("\\propto")),
            SymbolButton("°", SymbolAction.Command("\\circ")),
            SymbolButton("ℚ", SymbolAction.Text("ℚ")),
            SymbolButton("ℝ", SymbolAction.Text("ℝ")),
            SymbolButton("ℕ", SymbolAction.Text("ℕ")),
            SymbolButton("ℤ", SymbolAction.Text("ℤ")),
            SymbolButton("ℂ", SymbolAction.Text("ℂ")),
            SymbolButton("∁", SymbolAction.Command("\\complement")),
            SymbolButton("δ", SymbolAction.Command("\\delta")),
        )),

        // ---------- 矩阵（共 5） ----------
        SymbolTab("矩阵", listOf(
            SymbolButton("矩阵2×2", SymbolAction.Matrix2, big = true),
            SymbolButton("矩阵3×3", SymbolAction.Matrix3, big = true),
            SymbolButton("行列式", SymbolAction.Det2, big = true),
            SymbolButton("分段(2行)", SymbolAction.Cases2, big = true),
            SymbolButton("分段(3行)", SymbolAction.Cases3, big = true),
        )),

        // ---------- 上下标·装饰（共 12 = 2×6，无重复） ----------
        SymbolTab("上下标", listOf(
            SymbolButton("上标", SymbolAction.Sup, big = true),
            SymbolButton("下标", SymbolAction.Sub, big = true),
            SymbolButton("上下标", SymbolAction.BothScripts, big = true),
            SymbolButton("′", SymbolAction.Prime),
            SymbolButton("″", SymbolAction.Text("″")),
            SymbolButton("‴", SymbolAction.Text("‴")),
            SymbolButton("向量", SymbolAction.Vec, big = true),
            SymbolButton("帽子", SymbolAction.Hat, big = true),
            SymbolButton("横线", SymbolAction.Bar, big = true),
            SymbolButton("点", SymbolAction.Dot, big = true),
            SymbolButton("波浪", SymbolAction.Tilde, big = true),
            SymbolButton("宽帽子", SymbolAction.WideHat, big = true),
        )),

        // ---------- 系统键盘 ----------
        SymbolTab("系统键盘", listOf(
            SymbolButton("打开系统键盘", SymbolAction.ToggleIme, big = true),
        )),
    ).map { it.copy(buttons = pad6(it.buttons)) }

    private fun greekLetters(): List<SymbolButton> {
        val lower = listOf(
            "alpha" to "α", "beta" to "β", "gamma" to "γ", "delta" to "δ",
            "epsilon" to "ε", "zeta" to "ζ", "eta" to "η", "theta" to "θ",
            "iota" to "ι", "kappa" to "κ", "lambda" to "λ", "mu" to "μ",
            "nu" to "ν", "xi" to "ξ", "pi" to "π", "rho" to "ρ",
            "sigma" to "σ", "tau" to "τ", "upsilon" to "υ", "phi" to "φ",
            "chi" to "χ", "psi" to "ψ", "omega" to "ω"
        )
        val upper = listOf(
            "Α", "Β", "Γ", "Δ", "Ε", "Ζ", "Η", "Θ",
            "Ι", "Κ", "Λ", "Μ", "Ν", "Ξ", "Ο", "Π",
            "Ρ", "Σ", "Τ", "Υ", "Φ", "Χ", "Ψ", "Ω"
        )
        // 小写用 \alpha 等标准命令；大写直接插 Unicode（\Alpha 等不是标准 LaTeX 命令）
        return lower.map { (cmd, disp) ->
            SymbolButton(disp, SymbolAction.Command("\\$cmd"))
        } + upper.map { disp ->
            SymbolButton(disp, SymbolAction.Text(disp))
        }
    }
}