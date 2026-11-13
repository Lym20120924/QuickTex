package com.formulastudio.app.editor

/** LaTeX 命令 → 编辑器内显示的字符 */
val CMD_DISPLAY: Map<String, String> = buildMap {
    // 希腊小写
    putAll(listOf(
        "\\alpha" to "α", "\\beta" to "β", "\\gamma" to "γ", "\\delta" to "δ",
        "\\epsilon" to "ε", "\\varepsilon" to "ε", "\\zeta" to "ζ", "\\eta" to "η",
        "\\theta" to "θ", "\\vartheta" to "ϑ", "\\iota" to "ι", "\\kappa" to "κ",
        "\\lambda" to "λ", "\\mu" to "μ", "\\nu" to "ν", "\\xi" to "ξ",
        "\\omicron" to "ο", "\\pi" to "π", "\\varpi" to "ϖ", "\\rho" to "ρ",
        "\\varrho" to "ϱ", "\\sigma" to "σ", "\\varsigma" to "ς", "\\tau" to "τ",
        "\\upsilon" to "υ", "\\phi" to "φ", "\\varphi" to "ϕ", "\\chi" to "χ",
        "\\psi" to "ψ", "\\omega" to "ω"
    ))
    // 希腊大写
    putAll(listOf(
        "\\Gamma" to "Γ", "\\Delta" to "Δ", "\\Theta" to "Θ", "\\Lambda" to "Λ",
        "\\Xi" to "Ξ", "\\Pi" to "Π", "\\Sigma" to "Σ", "\\Upsilon" to "Υ",
        "\\Phi" to "Φ", "\\Psi" to "Ψ", "\\Omega" to "Ω"
    ))
    // 运算与关系
    putAll(listOf(
        "\\times" to "×", "\\div" to "÷", "\\pm" to "±", "\\mp" to "∓",
        "\\cdot" to "·", "\\ast" to "∗", "\\star" to "⋆", "\\bullet" to "•",
        "\\circ" to "∘", "\\equiv" to "≡", "\\approx" to "≈", "\\propto" to "∝",
        "\\sim" to "∼", "\\simeq" to "≃", "\\cong" to "≅", "\\ne" to "≠",
        "\\neq" to "≠", "\\le" to "≤", "\\ge" to "≥", "\\ll" to "≪", "\\gg" to "≫"
    ))
    // 集合与逻辑
    putAll(listOf(
        "\\in" to "∈", "\\notin" to "∉", "\\ni" to "∋", "\\subset" to "⊂",
        "\\supset" to "⊃", "\\subseteq" to "⊆", "\\supseteq" to "⊇",
        "\\cup" to "∪", "\\cap" to "∩", "\\emptyset" to "∅", "\\varnothing" to "∅",
        "\\setminus" to "∖", "\\forall" to "∀", "\\exists" to "∃", "\\nexists" to "∄",
        "\\neg" to "¬", "\\wedge" to "∧", "\\vee" to "∨", "\\land" to "∧", "\\lor" to "∨"
    ))
    // 箭头
    putAll(listOf(
        "\\to" to "→", "\\rightarrow" to "→", "\\leftarrow" to "←",
        "\\leftrightarrow" to "↔", "\\Rightarrow" to "⇒", "\\Leftarrow" to "⇐",
        "\\Leftrightarrow" to "⇔", "\\mapsto" to "↦", "\\longrightarrow" to "⟶",
        "\\longleftarrow" to "⟵", "\\uparrow" to "↑", "\\downarrow" to "↓",
        "\\updownarrow" to "↕", "\\nearrow" to "↗", "\\searrow" to "↘",
        "\\swarrow" to "↙", "\\nwarrow" to "↖"
    ))
    // 其他
    putAll(listOf(
        "\\infty" to "∞", "\\partial" to "∂", "\\nabla" to "∇", "\\prime" to "′",
        "\\dots" to "…", "\\ldots" to "…", "\\cdots" to "⋯", "\\vdots" to "⋮",
        "\\ddots" to "⋱", "\\perp" to "⊥", "\\parallel" to "∥", "\\angle" to "∠",
        "\\triangle" to "△", "\\square" to "□", "\\blacksquare" to "■",
        "\\hbar" to "ℏ", "\\ell" to "ℓ", "\\aleph" to "ℵ", "\\Re" to "ℜ", "\\Im" to "ℑ",
        "\\backslash" to "\\", "\\vert" to "|", "\\Vert" to "‖", "\\{" to "{", "\\}" to "}"
    ))
    // 大型运算符
    putAll(listOf(
        "\\sum" to "∑", "\\prod" to "∏", "\\coprod" to "∐", "\\int" to "∫",
        "\\iint" to "∬", "\\iiint" to "∭", "\\iiiint" to "⨌", "\\oint" to "∮",
        "\\oiint" to "∯", "\\oiiint" to "∰", "\\bigcup" to "⋃", "\\bigcap" to "⋂",
        "\\bigoplus" to "⊕", "\\bigotimes" to "⊗", "\\bigodot" to "⊙",
        "\\lim" to "lim", "\\max" to "max", "\\min" to "min", "\\inf" to "inf",
        "\\sup" to "sup", "\\argmax" to "argmax", "\\argmin" to "argmin"
    ))
    // 用户补充的 axMath 常用符号（关系/运算）
    putAll(listOf(
        "\\doteq" to "≐", "\\gg" to "≫", "\\ll" to "≪", "\\succ" to "≻", "\\prec" to "≺",
        "\\therefore" to "∴", "\\because" to "∵", "\\Join" to "⋈",
        "\\oplus" to "⊕", "\\ominus" to "⊖", "\\otimes" to "⊗", "\\odot" to "⊙",
        "\\circledast" to "⊛", "\\circledcirc" to "⊚", "\\uplus" to "⊎", "\\setminus" to "∖",
        "\\subsetneq" to "⊊", "\\supsetneq" to "⊋", "\\Box" to "□", "\\bigtriangleup" to "△",
        "\\bot" to "⊥", "\\nless" to "≮", "\\ngtr" to "≯", "\\nsubseteq" to "⊈",
        "\\nsupseteq" to "⊉", "\\nprec" to "⊀", "\\nsucc" to "⊁", "\\nsubset" to "⊄",
        "\\ncong" to "≇", "\\ltimes" to "⋉", "\\rtimes" to "⋊", "\\divideontimes" to "⋇",
        "\\mho" to "℧", "\\textperthousand" to "‰", "\\dag" to "†", "\\ddag" to "‡",
        "\\eth" to "ð", "\\imath" to "ı", "\\jmath" to "ȷ", "\\varkappa" to "ϰ",
        "\\wp" to "℘", "\\complement" to "∁", "\\diamondsuit" to "♢",
        "\\backepsilon" to "∍", "\\varpropto" to "∝", "\\smallsetminus" to "∖",
        "\\blacktriangleright" to "▸", "\\blacktriangleleft" to "◂"
    ))
    // 箭头（用户补充）
    putAll(listOf(
        "\\gets" to "←", "\\nleftarrow" to "↚", "\\nrightarrow" to "↛",
        "\\twoheadleftarrow" to "↞", "\\twoheadrightarrow" to "↠",
        "\\leftleftarrows" to "⇇", "\\rightrightarrows" to "⇉",
        "\\leftrightarrows" to "⇆", "\\rightleftarrows" to "⇄",
        "\\leftarrowtail" to "↢", "\\rightarrowtail" to "↣",
        "\\looparrowleft" to "↫", "\\looparrowright" to "↬",
        "\\curvearrowleft" to "↶", "\\curvearrowright" to "↷",
        "\\circlearrowleft" to "↺", "\\circlearrowright" to "↻",
        "\\leftharpoonup" to "↼", "\\leftharpoondown" to "↽",
        "\\rightharpoonup" to "⇀", "\\rightharpoondown" to "⇁",
        "\\leftrightsquigarrow" to "↭", "\\nLeftarrow" to "⇍",
        "\\nRightarrow" to "⇏", "\\nLeftrightarrow" to "⇎",
        "\\longmapsto" to "⟼", "\\Longleftarrow" to "⟸",
        "\\Longrightarrow" to "⟹", "\\Longleftrightarrow" to "⟺",
        "\\implies" to "⟹", "\\impliedby" to "⟸", "\\iff" to "⟺"
    ))
    // 扩展符号
    putAll(listOf(
        "\\nsupset" to "⊅", "\\notni" to "∌", "\\nparallel" to "∦", "\\nmid" to "∤",
        "\\nvdash" to "⊬", "\\vdash" to "⊢", "\\models" to "⊨",
        "\\preceq" to "≼", "\\succeq" to "≽", "\\npreceq" to "⋠", "\\nsucceq" to "⋡",
        "\\gtrapprox" to "≳", "\\lessapprox" to "≲",
        "\\triangleq" to "≜", "\\eqcirc" to "≖", "\\circeq" to "≗",
        "\\barwedge" to "⊼", "\\boxdot" to "⊡", "\\boxtimes" to "⊠",
        "\\diamond" to "⋄", "\\bigstar" to "★", "\\lozenge" to "◊", "\\blacklozenge" to "⧫",
        "\\flat" to "♭", "\\natural" to "♮", "\\sharp" to "♯",
        "\\measuredangle" to "∡", "\\sphericalangle" to "∢",
        "\\aleph" to "ℵ", "\\beth" to "ℶ",
        "\\nsim" to "≁", "\\simneq" to "≆", "\\asymp" to "≍", "\\doteqdot" to "≑",
        "\\lessgtr" to "≶", "\\gtrless" to "≷", "\\between" to "≬"
    ))
}
