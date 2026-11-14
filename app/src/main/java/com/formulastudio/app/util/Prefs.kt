package com.formulastudio.app.util

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/** 应用设置（SharedPreferences 持久化） */
object Prefs {

    private const val PREFS = "app_settings"
    private const val KEY_FONT_SCALE = "font_scale"
    private const val KEY_SHOW_CODE = "show_code"
    private const val KEY_GRID = "grid_bg"
    private const val KEY_DARK_MODE = "dark_mode"
    private const val KEY_LANGUAGE = "language"
    private const val KEY_HISTORY_MAX = "history_max"
    private const val KEY_PNG_SCALE = "png_scale"
    private const val KEY_HAPTIC = "haptic"

    // 深色模式
    const val DARK_SYSTEM = 0
    const val DARK_LIGHT = 1
    const val DARK_DARK = 2

    // 语言（空 = 跟随系统）
    const val LANG_SYSTEM = ""
    const val LANG_ZH = "zh"
    const val LANG_EN = "en"

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun fontScale(c: Context): Float = prefs(c).getFloat(KEY_FONT_SCALE, 1f)
    fun setFontScale(c: Context, v: Float) = prefs(c).edit().putFloat(KEY_FONT_SCALE, v).apply()

    fun showCode(c: Context): Boolean = prefs(c).getBoolean(KEY_SHOW_CODE, true)
    fun setShowCode(c: Context, v: Boolean) = prefs(c).edit().putBoolean(KEY_SHOW_CODE, v).apply()

    fun showGrid(c: Context): Boolean = prefs(c).getBoolean(KEY_GRID, false)
    fun setShowGrid(c: Context, v: Boolean) = prefs(c).edit().putBoolean(KEY_GRID, v).apply()

    fun darkMode(c: Context): Int = prefs(c).getInt(KEY_DARK_MODE, DARK_SYSTEM)
    fun setDarkMode(c: Context, v: Int) = prefs(c).edit().putInt(KEY_DARK_MODE, v).apply()

    fun language(c: Context): String = prefs(c).getString(KEY_LANGUAGE, LANG_SYSTEM) ?: LANG_SYSTEM
    fun setLanguage(c: Context, v: String) = prefs(c).edit().putString(KEY_LANGUAGE, v).apply()

    fun historyMax(c: Context): Int = prefs(c).getInt(KEY_HISTORY_MAX, 60)
    fun setHistoryMax(c: Context, v: Int) = prefs(c).edit().putInt(KEY_HISTORY_MAX, v).apply()

    fun pngScale(c: Context): Int = prefs(c).getInt(KEY_PNG_SCALE, 3)
    fun setPngScale(c: Context, v: Int) = prefs(c).edit().putInt(KEY_PNG_SCALE, v).apply()

    fun haptic(c: Context): Boolean = prefs(c).getBoolean(KEY_HAPTIC, true)
    fun setHaptic(c: Context, v: Boolean) = prefs(c).edit().putBoolean(KEY_HAPTIC, v).apply()

    // ---------- 外观 ----------

    private const val KEY_THEME_STYLE = "theme_style"
    private const val KEY_FLOATING_BAR = "floating_bar"
    private const val KEY_LIQUID_GLASS = "liquid_glass"
    private const val KEY_DYNAMIC_COLOR = "dynamic_color"
    private const val KEY_ACCENT = "accent_color"
    private const val KEY_COLOR_STYLE = "color_style"
    private const val KEY_CONTRAST = "contrast"
    private const val KEY_CONTENT_COLOR = "content_color"
    private const val KEY_DISPLAY_MODE = "display_mode"
    private const val KEY_SPLASH_THEME = "splash_theme"
    private const val KEY_ICON_THEME = "icon_theme"

    /** 界面风格 */
    const val STYLE_MATERIAL = 0
    const val STYLE_MIUIX = 1

    /** 显示模式 */
    const val DISPLAY_SYSTEM = 0
    const val DISPLAY_LIGHT = 1
    const val DISPLAY_DARK = 2
    const val DISPLAY_AMOLED = 3

    /** 配色风格 */
    const val COLOR_DYNAMIC = 0
    const val COLOR_CLASSIC = 1
    const val COLOR_FRESH = 2
    const val COLOR_DEEP = 3

    fun themeStyle(c: Context): Int = prefs(c).getInt(KEY_THEME_STYLE, STYLE_MATERIAL)
    fun setThemeStyle(c: Context, v: Int) = prefs(c).edit().putInt(KEY_THEME_STYLE, v).apply()

    fun floatingBar(c: Context): Boolean = prefs(c).getBoolean(KEY_FLOATING_BAR, false)
    fun setFloatingBar(c: Context, v: Boolean) = prefs(c).edit().putBoolean(KEY_FLOATING_BAR, v).apply()

    fun liquidGlass(c: Context): Boolean = prefs(c).getBoolean(KEY_LIQUID_GLASS, false)
    fun setLiquidGlass(c: Context, v: Boolean) = prefs(c).edit().putBoolean(KEY_LIQUID_GLASS, v).apply()

    fun dynamicColor(c: Context): Boolean = prefs(c).getBoolean(KEY_DYNAMIC_COLOR, false)
    fun setDynamicColor(c: Context, v: Boolean) = prefs(c).edit().putBoolean(KEY_DYNAMIC_COLOR, v).apply()

    fun accentColor(c: Context): Int = prefs(c).getInt(KEY_ACCENT, 0xFF1565C0.toInt())
    fun setAccentColor(c: Context, v: Int) = prefs(c).edit().putInt(KEY_ACCENT, v).apply()

    fun colorStyle(c: Context): Int = prefs(c).getInt(KEY_COLOR_STYLE, COLOR_DYNAMIC)
    fun setColorStyle(c: Context, v: Int) = prefs(c).edit().putInt(KEY_COLOR_STYLE, v).apply()

    fun contrast(c: Context): Float = prefs(c).getFloat(KEY_CONTRAST, 1f)
    fun setContrast(c: Context, v: Float) = prefs(c).edit().putFloat(KEY_CONTRAST, v).apply()

    fun contentColor(c: Context): Boolean = prefs(c).getBoolean(KEY_CONTENT_COLOR, false)
    fun setContentColor(c: Context, v: Boolean) = prefs(c).edit().putBoolean(KEY_CONTENT_COLOR, v).apply()

    fun displayMode(c: Context): Int = prefs(c).getInt(KEY_DISPLAY_MODE, DISPLAY_SYSTEM)
    fun setDisplayMode(c: Context, v: Int) = prefs(c).edit().putInt(KEY_DISPLAY_MODE, v).apply()

    fun splashTheme(c: Context): Boolean = prefs(c).getBoolean(KEY_SPLASH_THEME, true)
    fun setSplashTheme(c: Context, v: Boolean) = prefs(c).edit().putBoolean(KEY_SPLASH_THEME, v).apply()

    fun iconTheme(c: Context): Boolean = prefs(c).getBoolean(KEY_ICON_THEME, true)
    fun setIconTheme(c: Context, v: Boolean) = prefs(c).edit().putBoolean(KEY_ICON_THEME, v).apply()

    /** 应用深色模式设置（需在 Activity 创建前调用） */
    fun applyDarkMode(c: Context) {
        val mode = when (darkMode(c)) {
            DARK_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            DARK_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    /** 应用「显示模式」（含纯黑深色） */
    fun applyDisplayMode(c: Context) {
        val mode = when (displayMode(c)) {
            DISPLAY_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            DISPLAY_DARK, DISPLAY_AMOLED -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    /** Activity 应使用的主题（界面风格 / 显示模式） */
    fun themeRes(c: Context): Int = when {
        displayMode(c) == DISPLAY_AMOLED -> com.formulastudio.app.R.style.Theme_FormulaStudio_Amoled
        themeStyle(c) == STYLE_MIUIX -> com.formulastudio.app.R.style.Theme_FormulaStudio_Miui
        else -> com.formulastudio.app.R.style.Theme_FormulaStudio
    }

    /** 应用语言设置 */
    fun applyLanguage(c: Context) {
        val tag = language(c)
        AppCompatDelegate.setApplicationLocales(
            if (tag == LANG_SYSTEM) LocaleListCompat.getEmptyLocaleList()
            else LocaleListCompat.forLanguageTags(tag)
        )
    }
}
