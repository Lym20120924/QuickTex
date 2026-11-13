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

    /** 应用深色模式设置（需在 Activity 创建前调用） */
    fun applyDarkMode(c: Context) {
        val mode = when (darkMode(c)) {
            DARK_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            DARK_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
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
