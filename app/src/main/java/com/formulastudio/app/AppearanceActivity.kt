package com.formulastudio.app

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.formulastudio.app.util.Prefs
import com.google.android.material.slider.Slider

/**
 * 外观设置中心。
 *
 * 分区：界面风格 / 导航栏 / 配色 / 显示模式 / 启动画面与图标。
 */
class AppearanceActivity : AppCompatActivity() {

    private lateinit var content: LinearLayout

    private val accent: Int get() = Prefs.accentColor(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        Prefs.applyDisplayMode(this)
        setTheme(Prefs.themeRes(this))
        super.onCreate(savedInstanceState)
        Prefs.applyLanguage(this)

        val scroll = ScrollView(this)
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(16), dp(12), dp(32))
        }
        scroll.addView(content)
        setContentView(scroll)

        build()
    }

    private fun build() {
        // 1. 界面风格
        section(R.string.section_theme_style)
        radioRow(R.string.style_material, null, Prefs.themeStyle(this) == Prefs.STYLE_MATERIAL) {
            Prefs.setThemeStyle(this, Prefs.STYLE_MATERIAL); rebuild()
        }
        radioRow(R.string.style_miuix, null, Prefs.themeStyle(this) == Prefs.STYLE_MIUIX) {
            Prefs.setThemeStyle(this, Prefs.STYLE_MIUIX); rebuild()
        }

        // 2. 导航栏
        section(R.string.section_nav_bar)
        switchRow(R.string.nav_floating, R.string.nav_floating_desc, Prefs.floatingBar(this)) {
            Prefs.setFloatingBar(this, it)
        }
        switchRow(R.string.nav_liquid, R.string.nav_liquid_desc, Prefs.liquidGlass(this)) {
            Prefs.setLiquidGlass(this, it)
        }

        // 3. 配色
        section(R.string.section_color)
        iconRow(
            R.drawable.ic_wallpaper, getString(R.string.color_wallpaper),
            getString(R.string.color_wallpaper_status)
        ) {
            AlertDialog.Builder(this)
                .setTitle(R.string.color_wallpaper)
                .setItems(
                    arrayOf(
                        getString(R.string.color_style_dynamic),
                        getString(R.string.color_style_classic),
                        getString(R.string.color_style_fresh),
                        getString(R.string.color_style_deep)
                    )
                ) { _, which ->
                    Prefs.setColorStyle(this, which)
                    applyColorStyle(which)
                    rebuild()
                }
                .show()
        }
        switchRow(R.string.color_dynamic, R.string.color_dynamic_desc, Prefs.dynamicColor(this)) {
            Prefs.setDynamicColor(this, it)
            rebuild()
        }
        colorPicker()
        dropdownRow(
            R.string.color_style,
            colorStyleLabel(Prefs.colorStyle(this)),
            colorStyleOptions()
        ) {
            Prefs.setColorStyle(this, it)
            applyColorStyle(it)
            rebuild()
        }
        sliderRow(R.string.color_contrast, Prefs.contrast(this)) {
            Prefs.setContrast(this, it)
        }
        switchRow(R.string.color_content, null, Prefs.contentColor(this)) {
            Prefs.setContentColor(this, it)
        }

        // 4. 显示模式
        section(R.string.section_display)
        radioRow(R.string.display_system, null, Prefs.displayMode(this) == Prefs.DISPLAY_SYSTEM) {
            Prefs.setDisplayMode(this, Prefs.DISPLAY_SYSTEM); applyDisplay(); rebuild()
        }
        radioRow(R.string.display_light, null, Prefs.displayMode(this) == Prefs.DISPLAY_LIGHT) {
            Prefs.setDisplayMode(this, Prefs.DISPLAY_LIGHT); applyDisplay(); rebuild()
        }
        radioRow(R.string.display_dark, null, Prefs.displayMode(this) == Prefs.DISPLAY_DARK) {
            Prefs.setDisplayMode(this, Prefs.DISPLAY_DARK); applyDisplay(); rebuild()
        }
        radioRow(
            R.string.display_amoled, R.string.display_amoled_desc,
            Prefs.displayMode(this) == Prefs.DISPLAY_AMOLED
        ) {
            Prefs.setDisplayMode(this, Prefs.DISPLAY_AMOLED); applyDisplay(); rebuild()
        }

        // 5. 启动画面与图标
        section(R.string.section_splash)
        switchRow(R.string.splash_theme, null, Prefs.splashTheme(this)) {
            Prefs.setSplashTheme(this, it)
        }
        switchRow(R.string.icon_theme, null, Prefs.iconTheme(this)) {
            Prefs.setIconTheme(this, it)
        }
    }

    private fun rebuild() {
        content.removeAllViews()
        build()
    }

    private fun colorStyleOptions(): List<String> = listOf(
        getString(R.string.color_style_dynamic),
        getString(R.string.color_style_classic),
        getString(R.string.color_style_fresh),
        getString(R.string.color_style_deep)
    )

    // ---------- 配色 ----------

    private fun applyColorStyle(style: Int) {
        if (Prefs.dynamicColor(this)) return
        val base = when (style) {
            Prefs.COLOR_CLASSIC -> 0xFF1565C0.toInt()
            Prefs.COLOR_FRESH -> 0xFF00897B.toInt()
            Prefs.COLOR_DEEP -> 0xFF6A1B9A.toInt()
            else -> 0xFF3482FF.toInt()
        }
        Prefs.setAccentColor(this, adjustContrast(base, Prefs.contrast(this)))
    }

    private fun adjustContrast(color: Int, contrast: Float): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[2] = (hsv[2] * contrast).coerceIn(0f, 1f)
        return Color.HSVToColor(hsv)
    }

    private fun colorStyleLabel(style: Int): String = getString(
        when (style) {
            Prefs.COLOR_CLASSIC -> R.string.color_style_classic
            Prefs.COLOR_FRESH -> R.string.color_style_fresh
            Prefs.COLOR_DEEP -> R.string.color_style_deep
            else -> R.string.color_style_dynamic
        }
    )

    private fun colorPicker() {
        val dynamic = Prefs.dynamicColor(this)
        content.addView(TextView(this).apply {
            text = getString(R.string.color_picker)
            textSize = 15f
            setPadding(dp(10), dp(10), 0, dp(2))
        })
        if (dynamic) {
            content.addView(TextView(this).apply {
                text = getString(R.string.color_picker_disabled)
                textSize = 12f
                setTextColor(Color.GRAY)
                setPadding(0, 0, 0, dp(6))
            })
        }

        val swatches = listOf(
            0xFFF44336, 0xFFE91E63, 0xFF9C27B0, 0xFF673AB7,
            0xFF3F51B5, 0xFF1565C0, 0xFF03A9F4, 0xFF00BCD4,
            0xFF009688, 0xFF4CAF50, 0xFF8BC34A, 0xFFCDDC39,
            0xFFFFC107, 0xFFFF9800, 0xFFFF5722, 0xFF795548
        ).map { it.toInt() }

        val tile = dp(46)
        val grid = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (i in 0 until 4) {
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            for (j in 0 until 4) {
                val c = swatches[i * 4 + j]
                col.addView(swatch(c, tile, !dynamic && accent == c) {
                    if (!dynamic) {
                        Prefs.setAccentColor(this, adjustContrast(c, Prefs.contrast(this)))
                        rebuild()
                    }
                })
            }
            grid.addView(
                col,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )
        }
        content.addView(grid)

        // 默认色块 + 添加自定义颜色
        val extra = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, dp(4))
        }
        val defaultColor = 0xFF3C4043.toInt()
        extra.addView(swatch(defaultColor, tile, !dynamic && accent == defaultColor) {
            if (!dynamic) {
                Prefs.setAccentColor(this, defaultColor); rebuild()
            }
        })
        extra.addView(tileLabel(getString(R.string.color_default), tile))
        extra.addView(addButton(tile) { showCustomColorDialog() })
        extra.addView(tileLabel(getString(R.string.color_add), tile))
        content.addView(extra)
    }

    private fun swatch(color: Int, size: Int, selected: Boolean, onClick: () -> Unit): View {
        return ImageView(this).apply {
            setImageResource(R.drawable.ic_check)
            // 选中：外圈描边 + 勾选图标；未选中：半透明
            setColorFilter(
                if (selected) (if (isLight(color)) Color.DKGRAY else Color.WHITE)
                else Color.TRANSPARENT
            )
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
                if (selected) setStroke(dp(3), accent)
            }
            alpha = if (selected) 1f else 0.5f
            scaleX = if (selected) 1f else 0.9f
            scaleY = if (selected) 1f else 0.9f
            layoutParams = LinearLayout.LayoutParams(size, size).apply { marginEnd = dp(6) }
            setOnClickListener { onClick() }
        }
    }

    private fun isLight(color: Int): Boolean {
        val r = Color.red(color) / 255.0
        val g = Color.green(color) / 255.0
        val b = Color.blue(color) / 255.0
        return 0.299 * r + 0.587 * g + 0.114 * b > 0.6
    }

    private fun tileLabel(text: String, size: Int): TextView = TextView(this).apply {
        this.text = text
        textSize = 12f
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(size, size)
    }

    private fun addButton(size: Int, onClick: () -> Unit): View = TextView(this).apply {
        text = "+"
        textSize = 24f
        gravity = Gravity.CENTER
        setTextColor(Color.GRAY)
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#22000000"))
        }
        layoutParams = LinearLayout.LayoutParams(size, size).apply { marginEnd = dp(6) }
        setOnClickListener { onClick() }
    }

    private fun showCustomColorDialog() {
        val preview = View(this).apply {
            setBackgroundColor(Color.HSVToColor(floatArrayOf(210f, 0.8f, 0.9f)))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(56)
            )
        }
        val hue = Slider(this).apply {
            valueFrom = 0f
            valueTo = 360f
            value = 210f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(20), dp(8), dp(20), dp(8)) }
            addOnChangeListener { _, v, _ ->
                preview.setBackgroundColor(Color.HSVToColor(floatArrayOf(v, 0.8f, 0.9f)))
            }
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(preview)
            addView(hue)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.color_add)
            .setView(box)
            .setPositiveButton(R.string.ok) { _, _ ->
                Prefs.setAccentColor(
                    this, Color.HSVToColor(floatArrayOf(hue.value, 0.8f, 0.9f))
                )
                rebuild()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ---------- 行控件 ----------

    private fun section(res: Int) {
        content.addView(TextView(this).apply {
            text = getString(res)
            textSize = 13f
            setTextColor(accent)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(18), 0, dp(4))
        })
    }

    private fun radioRow(
        titleRes: Int,
        subtitleRes: Int?,
        selected: Boolean,
        onClick: () -> Unit
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(10), dp(6), dp(10))
            isClickable = true
            setSelectedBackground()
            setOnClickListener { onClick() }
        }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(TextView(this).apply {
            text = getString(titleRes)
            textSize = 15f
        })
        if (subtitleRes != null) {
            col.addView(TextView(this).apply {
                text = getString(subtitleRes)
                textSize = 12f
                setTextColor(Color.GRAY)
            })
        }
        row.addView(
            col,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        row.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_check)
            setColorFilter(accent)
            visibility = if (selected) View.VISIBLE else View.GONE
        })
        content.addView(row)
    }

    private fun switchRow(
        titleRes: Int,
        descRes: Int?,
        checked: Boolean,
        onChange: (Boolean) -> Unit
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(8), dp(6), dp(8))
        }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(TextView(this).apply {
            text = getString(titleRes)
            textSize = 15f
        })
        if (descRes != null) {
            col.addView(TextView(this).apply {
                text = getString(descRes)
                textSize = 12f
                setTextColor(Color.GRAY)
            })
        }
        row.addView(
            col,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        row.addView(SwitchCompat(this).apply {
            isChecked = checked
            setOnCheckedChangeListener { _, c -> onChange(c) }
        })
        content.addView(row)
    }

    private fun iconRow(
        iconRes: Int,
        title: String,
        status: String,
        onClick: () -> Unit
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(10), dp(6), dp(10))
            isClickable = true
            setSelectedBackground()
            setOnClickListener { onClick() }
        }
        row.addView(ImageView(this).apply {
            setImageResource(iconRes)
            setColorFilter(accent)
            layoutParams = LinearLayout.LayoutParams(dp(26), dp(26))
        })
        row.addView(TextView(this).apply {
            text = title
            textSize = 15f
            setPadding(dp(12), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(TextView(this).apply {
            text = status
            textSize = 13f
            setTextColor(Color.GRAY)
        })
        content.addView(row)
    }

    private fun dropdownRow(
        titleRes: Int,
        value: String,
        options: List<String>,
        onPick: (Int) -> Unit
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(10), dp(6), dp(10))
            isClickable = true
            setSelectedBackground()
            setOnClickListener {
                AlertDialog.Builder(this@AppearanceActivity)
                    .setTitle(titleRes)
                    .setItems(options.toTypedArray()) { _, which -> onPick(which) }
                    .show()
            }
        }
        row.addView(TextView(this).apply {
            text = getString(titleRes)
            textSize = 15f
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(TextView(this).apply {
            text = value
            textSize = 14f
            setTextColor(accent)
        })
        content.addView(row)
    }

    private fun sliderRow(titleRes: Int, value: Float, onChange: (Float) -> Unit) {
        content.addView(TextView(this).apply {
            text = getString(titleRes)
            textSize = 15f
            setPadding(dp(10), dp(12), dp(6), 0)
        })
        content.addView(Slider(this).apply {
            valueFrom = 0.8f
            valueTo = 1.4f
            stepSize = 0.05f
            this.value = value
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(16), 0, dp(16), 0) }
            addOnChangeListener { _, v, _ -> onChange(v) }
        })
    }

    // ---------- 工具 ----------

    private fun View.setSelectedBackground() {
        val tv = TypedValue()
        if (context.theme.resolveAttribute(
                android.R.attr.selectableItemBackground, tv, true
            )
        ) {
            setBackgroundResource(tv.resourceId)
        }
    }

    private fun applyDisplay() {
        Prefs.applyDisplayMode(this)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
