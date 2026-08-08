package com.formulastudio.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.formulastudio.app.util.Store
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 收藏夹 */
class LibraryActivity : AppCompatActivity() {

    private lateinit var content: LinearLayout
    private lateinit var emptyView: TextView

    private val primary = Color.rgb(21, 101, 192)

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

        // 顶栏
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
            text = getString(R.string.tab_favorites)
            textSize = 17f
            setPadding(dp(2), dp(4), dp(12), dp(4))
        }
        bar.addView(back)
        bar.addView(title)
        root.addView(bar)

        // 列表
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
            setTextColor(Color.rgb(150, 150, 150))
            setPadding(dp(20), dp(40), dp(20), dp(40))
            text = getString(R.string.empty_favorites)
        }
        root.addView(emptyView)

        setContentView(root)
    }

    private fun rebuildList() {
        content.removeAllViews()
        val entries = Store.getFavorites(this)
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
            typeface = android.graphics.Typeface.MONOSPACE
            setTextColor(Color.rgb(30, 30, 30))
        }
        val timeRow = TextView(this).apply {
            this.text = time
            textSize = 11f
            setTextColor(Color.rgb(150, 150, 150))
            setPadding(0, dp(2), 0, dp(4))
        }
        val btnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val load = actionButton(getString(R.string.load)) {
            setResult(RESULT_OK, Intent().putExtra("latex", latex))
            finish()
        }
        val del = actionButton(getString(R.string.delete)) {
            Store.removeFavorite(this@LibraryActivity, latex)
            rebuildList()
        }
        btnRow.addView(load)
        btnRow.addView(del)
        card.addView(text)
        card.addView(timeRow)
        card.addView(btnRow)

        val divider = View(this).apply { setBackgroundColor(Color.rgb(230, 230, 230)) }
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
