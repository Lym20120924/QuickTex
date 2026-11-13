package com.formulastudio.app.keyboard

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.formulastudio.app.R
import com.formulastudio.app.editor.SymbolAction

/**
 * 数学符号键盘：顶部横向分类标签 + 符号按钮网格 + 底部工具行。
 */
class SymbolKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    /** 按钮点击回调 */
    var listener: ((SymbolAction) -> Unit)? = null

    private val chips = ArrayList<TextView>()
    private var currentTab = 0
    private val gridContainer: LinearLayout
    private val gridScroll: ScrollView

    private val primaryColor: Int = ContextCompat.getColor(context, R.color.accent_blue)
    private val onPrimaryColor: Int = android.graphics.Color.WHITE
    private val surfaceVariantColor: Int = ContextCompat.getColor(context, R.color.chip_bg)
    private val onSurfaceVariantColor: Int = ContextCompat.getColor(context, R.color.chip_text)

    init {
        orientation = VERTICAL

        // 分类标签行
        val chipScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            isFillViewport = true
        }
        val chipRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            setPadding(dp(4), dp(2), dp(4), dp(2))
        }
        Symbols.tabs.forEachIndexed { i, tab ->
            val chip = TextView(context).apply {
                text = context.getString(tab.nameRes)
                textSize = 13f
                isAllCaps = false
                isFocusable = false
                gravity = Gravity.CENTER
                setPadding(dp(14), dp(6), dp(14), dp(6))
                setOnClickListener {
                    // “系统键盘”标签同时承担调起系统输入法的功能
                    if (tab.toggleIme) {
                        listener?.invoke(SymbolAction.ToggleIme)
                    }
                    selectTab(i)
                }
            }
            chipRow.addView(chip)
            chips.add(chip)
        }
        chipScroll.addView(chipRow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(chipScroll)

        // 按钮网格（可滚动，cell 自带边框，无外层框避免双线）
        gridScroll = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
        }
        gridContainer = LinearLayout(context).apply { orientation = VERTICAL }
        gridScroll.addView(gridContainer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(gridScroll, LayoutParams(LayoutParams.MATCH_PARENT, dp(263)))

        // 工具行（左右移动 / 撤销 / 重做 / 清空）
        val toolRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(2), dp(2), dp(2), dp(4))
        }
        toolRow.addView(toolButton("←") { listener?.invoke(SymbolAction.MoveLeft) })
        toolRow.addView(toolButton("→") { listener?.invoke(SymbolAction.MoveRight) })
        toolRow.addView(toolButton(context.getString(R.string.btn_newline)) { listener?.invoke(SymbolAction.NewLine) })
        toolRow.addView(toolButton(context.getString(R.string.undo)) { listener?.invoke(SymbolAction.Undo) })
        toolRow.addView(toolButton(context.getString(R.string.redo)) { listener?.invoke(SymbolAction.Redo) })
        toolRow.addView(toolButton(context.getString(R.string.clear_all)) { listener?.invoke(SymbolAction.Clear) })
        addView(toolRow)

        selectTab(0)
    }

    private fun toolButton(label: String, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            text = label
            textSize = 14f
            gravity = Gravity.CENTER
            isFocusable = false
            setPadding(dp(12), dp(4), dp(12), dp(4))
            background = rippleBg()
            setOnClickListener { onClick() }
        }
    }

    private fun selectTab(index: Int) {
        currentTab = index
        chips.forEachIndexed { i, chip ->
            if (i == index) {
                chip.setTextColor(onPrimaryColor)
                chip.setBackgroundColor(primaryColor)
            } else {
                chip.setTextColor(onSurfaceVariantColor)
                chip.setBackgroundColor(surfaceVariantColor)
            }
        }
        rebuildGrid()
    }

    private fun rebuildGrid() {
        gridContainer.removeAllViews()
        val buttons = Symbols.tabs[currentTab].buttons
        val cellBg = ContextCompat.getDrawable(context, R.drawable.keyboard_cell_bg)
        var row: LinearLayout? = null
        buttons.forEachIndexed { i, btn ->
            if (i % 6 == 0) {
                row = LinearLayout(context).apply { orientation = HORIZONTAL }
                gridContainer.addView(
                    row!!,
                    LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
                )
            }
            if (btn.spacer) {
                // 占位：完全透明无边框（不让多余格子显示框线）
                val cell = View(context)
                row!!.addView(cell, LayoutParams(0, dp(48), 1f))
                return@forEachIndexed
            }
            val cell = TextView(context).apply {
                text = btn.labelRes?.let { context.getString(it) } ?: btn.label
                textSize = if (btn.big) 14f else 20f
                gravity = Gravity.CENTER
                isFocusable = false
                setPadding(dp(2), dp(8), dp(2), dp(8))
                background = cellBg
                foreground = rippleBg()
                setOnClickListener { listener?.invoke(btn.action) }
            }
            row!!.addView(
                cell,
                LayoutParams(0, dp(48), 1f)
            )
        }
    }

    /** 安全地取主题 ripple 背景（不能用 setBackgroundResource(attrId)，会崩溃） */
    private fun rippleBg(): android.graphics.drawable.Drawable {
        val tv = TypedValue()
        if (context.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true) &&
            tv.resourceId != 0
        ) {
            return context.getDrawable(tv.resourceId)!!
        }
        return android.graphics.drawable.ColorDrawable(0x1A000000)
    }

    private fun dp(v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
    ).toInt()
}
