package com.formulastudio.app.util

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

/** 收藏夹与历史记录的本地持久化（SharedPreferences + JSON） */
object Store {

    private const val PREFS = "formula_store"
    private const val KEY_FAVORITES = "favorites"
    private const val KEY_HISTORY = "history"
    private const val HISTORY_MAX = 60

    data class Entry(val latex: String, val time: Long)

    private fun prefs(c: Context): SharedPreferences =
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun readArray(c: Context, key: String): List<Entry> {
        val raw = prefs(c).getString(key, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Entry(o.getString("latex"), o.optLong("time", 0L))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun writeArray(c: Context, key: String, entries: List<Entry>) {
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(org.json.JSONObject().put("latex", e.latex).put("time", e.time))
        }
        prefs(c).edit().putString(key, arr.toString()).apply()
    }

    // ---------- 收藏 ----------

    fun getFavorites(c: Context): List<Entry> = readArray(c, KEY_FAVORITES)

    fun isFavorite(c: Context, latex: String): Boolean =
        getFavorites(c).any { it.latex == latex }

    /** 返回 true 表示本次是新增 */
    fun addFavorite(c: Context, latex: String): Boolean {
        if (latex.isBlank()) return false
        val list = getFavorites(c).filter { it.latex != latex }
        val new = listOf(Entry(latex, System.currentTimeMillis())) + list
        writeArray(c, KEY_FAVORITES, new)
        return true
    }

    fun removeFavorite(c: Context, latex: String) {
        writeArray(c, KEY_FAVORITES, getFavorites(c).filter { it.latex != latex })
    }

    fun clearFavorites(c: Context) {
        writeArray(c, KEY_FAVORITES, emptyList())
    }

    // ---------- 历史 ----------

    fun getHistory(c: Context): List<Entry> = readArray(c, KEY_HISTORY)

    fun pushHistory(c: Context, latex: String) {
        if (latex.isBlank()) return
        val max = Prefs.historyMax(c).coerceIn(10, 500)
        val list = getHistory(c).filter { it.latex != latex }
        val new = (listOf(Entry(latex, System.currentTimeMillis())) + list).take(max)
        writeArray(c, KEY_HISTORY, new)
    }

    fun removeHistory(c: Context, latex: String) {
        writeArray(c, KEY_HISTORY, getHistory(c).filter { it.latex != latex })
    }

    fun clearHistory(c: Context) {
        writeArray(c, KEY_HISTORY, emptyList())
    }
}
