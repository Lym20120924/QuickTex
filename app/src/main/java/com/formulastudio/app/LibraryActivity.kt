package com.formulastudio.app

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.formulastudio.app.util.Store
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 公式收藏与历史（含搜索 / 排序 / 清空） */
class LibraryActivity : AppCompatActivity() {

    private enum class Tab { FAVORITES, HISTORY }
    private enum class SortMode { TIME_DESC, TIME_ASC, NAME }

    private lateinit var content: LinearLayout
    private lateinit var emptyView: TextView
    private lateinit var searchInput: EditText
    private lateinit var tabFav: TextView
    private lateinit var tabHis: TextView

    private var tab = Tab.FAVORITES
    private var sortMode = SortMode.TIME_DESC
    private var query = ""

    private val primary: Int get() = ContextCompat.getColor(this, R.color.accent_blue)
    private val ink: Int get() = ContextCompat.getColor(this, R.color.lib_text)
    private val secondary: Int get() = ContextCompat.getColor(this, R.color.lib_text_secondary)
    private val dividerColor: Int get() = ContextCompat.getColor(this, R.color.lib_divider)
    private val chipBg: Int get() = ContextCompat.getColor(this, R.color.lib_chip_bg)
    private val chipText: Int get() = ContextCompat.getColor(this, R.color.lib_chip_text)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        buildUi()
        rebuildList()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        // 让标题栏避开状态栏
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, ins ->
            val bars = ins.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.setPadding(bars.left, bars.top, bars.right, 0)
            ins
        }

        // ---------- 顶栏 ----------
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(6), dp(4), dp(6))
        }
        val back = TextView(this).apply {
            text = "←"
            textSize = 20f
            setPadding(dp(12), dp(4), dp(16), dp(4))
            setOnClickListener { finish() }
        }
        val title = TextView(this).apply {
            text = getString(R.string.library_title)
            textSize = 17f
            setPadding(dp(2), dp(4), dp(12), dp(4))
        }
        val clearAll = TextView(this).apply {
            text = getString(R.string.clear_all)
            textSize = 14f
            setTextColor(primary)
            setPadding(dp(12), dp(4), dp(8), dp(4))
            setOnClickListener { confirmClearAll() }
        }
        bar.addView(back)
        bar.addView(title)
        val space = View(this)
        bar.addView(space, LinearLayout.LayoutParams(0, 1, 1f))
        bar.addView(clearAll)
        root.addView(bar)

        // ---------- Tab 行 ----------
        val tabRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(10), dp(4), dp(10), dp(4))
        }
        tabFav = tabChip(getString(R.string.tab_favorites)) { switchTab(Tab.FAVORITES) }
        tabHis = tabChip(getString(R.string.tab_history)) { switchTab(Tab.HISTORY) }
        tabRow.addView(tabFav, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        tabRow.addView(tabHis, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(tabRow)

        // ---------- 搜索 + 排序 ----------
        val toolRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(0), dp(10), dp(6))
        }
        searchInput = EditText(this).apply {
            hint = getString(R.string.search_hint)
            textSize = 14f
            setSingleLine(true)
            setTextColor(ink)
            setHintTextColor(secondary)
            background = null
            setPadding(dp(10), dp(6), dp(10), dp(6))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    query = s?.toString()?.trim() ?: ""
                    rebuildList()
                }
            })
        }
        val sortBtn = TextView(this).apply {
            text = getString(R.string.sort)
            textSize = 13f
            setTextColor(primary)
            setPadding(dp(10), dp(6), dp(4), dp(6))
            setOnClickListener { showSortMenu() }
        }
        toolRow.addView(searchInput, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        toolRow.addView(sortBtn)
        root.addView(toolRow)

        // ---------- 列表 ----------
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this)
        scroll.addView(
            content,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        emptyView = TextView(this).apply {
            gravity = Gravity.CENTER
            textSize = 14f
            setTextColor(secondary)
            setPadding(dp(20), dp(40), dp(20), dp(40))
        }
        root.addView(emptyView)

        setContentView(root)
    }

    private fun tabChip(label: String, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = label
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(6), dp(10), dp(6))
            setOnClickListener { onClick() }
        }
    }

    private fun switchTab(t: Tab) {
        tab = t
        updateTabStyles()
        rebuildList()
    }

    private fun updateTabStyles() {
        styleChip(tabFav, tab == Tab.FAVORITES)
        styleChip(tabHis, tab == Tab.HISTORY)
    }

    private fun styleChip(chip: TextView, selected: Boolean) {
        if (selected) {
            chip.setTextColor(android.graphics.Color.WHITE)
            chip.setBackgroundColor(primary)
        } else {
            chip.setTextColor(chipText)
            chip.setBackgroundColor(chipBg)
        }
    }

    private fun showSortMenu() {
        val labels = arrayOf(
            getString(R.string.sort_time_desc),
            getString(R.string.sort_time_asc),
            getString(R.string.sort_name)
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.sort)
            .setItems(labels) { _, which ->
                sortMode = SortMode.entries[which]
                rebuildList()
            }
            .show()
    }

    private fun confirmClearAll() {
        val msg = if (tab == Tab.FAVORITES) {
            getString(R.string.clear_favorites_confirm)
        } else {
            getString(R.string.clear_history_confirm)
        }
        AlertDialog.Builder(this)
            .setMessage(msg)
            .setPositiveButton(R.string.ok) { _, _ ->
                if (tab == Tab.FAVORITES) Store.clearFavorites(this) else Store.clearHistory(this)
                rebuildList()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ---------- 数据 ----------

    private fun rawEntries(): List<Store.Entry> =
        if (tab == Tab.FAVORITES) Store.getFavorites(this) else Store.getHistory(this)

    private fun rebuildList() {
        content.removeAllViews()

        var entries = rawEntries()
        if (query.isNotEmpty()) {
            entries = entries.filter { it.latex.contains(query, ignoreCase = true) }
        }
        entries = when (sortMode) {
            SortMode.TIME_DESC -> entries.sortedByDescending { it.time }
            SortMode.TIME_ASC -> entries.sortedBy { it.time }
            SortMode.NAME -> entries.sortedBy { it.latex.lowercase(Locale.getDefault()) }
        }

        emptyView.text = when {
            query.isNotEmpty() -> getString(R.string.no_match)
            tab == Tab.FAVORITES -> getString(R.string.empty_favorites)
            else -> getString(R.string.empty_history)
        }
        emptyView.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE

        val fmt = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
        entries.forEach { e ->
            content.addView(itemView(e.latex, fmt.format(Date(e.time))))
        }
    }

    private fun itemView(latex: String, time: String): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(8))
        }
        val text = TextView(this).apply {
            this.text = latex
            textSize = 14f
            typeface = Typeface.MONOSPACE
            setTextColor(ink)
        }
        val timeRow = TextView(this).apply {
            this.text = time
            textSize = 11f
            setTextColor(secondary)
            setPadding(0, dp(2), 0, dp(4))
        }
        val btnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val load = actionButton(getString(R.string.load)) {
            setResult(RESULT_OK, Intent().putExtra("latex", latex))
            finish()
        }
        val del = actionButton(getString(R.string.delete)) {
            if (tab == Tab.FAVORITES) {
                Store.removeFavorite(this@LibraryActivity, latex)
            } else {
                Store.removeHistory(this@LibraryActivity, latex)
            }
            rebuildList()
        }
        btnRow.addView(load)
        btnRow.addView(del)
        card.addView(text)
        card.addView(timeRow)
        card.addView(btnRow)

        val divider = View(this).apply { setBackgroundColor(dividerColor) }
        val wrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        wrap.addView(card)
        wrap.addView(divider, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))
        return wrap
    }

    private fun actionButton(label: String, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = label
            textSize = 13f
            setTextColor(primary)
            setPadding(dp(10), dp(3), dp(10), dp(3))
            setOnClickListener { onClick() }
        }
    }

    private fun dp(v: Int): Int =
        (v * resources.displayMetrics.density).toInt()
}
