package com.flowlauncher

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class TodoItem(val text: String, val done: Boolean = false)

class Prefs(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("flow_prefs", Context.MODE_PRIVATE)

    // ── In-memory caches ──────────────────────────────────────────────────────
    private var _cachedFavorites: List<String>? = null
    private var _cachedTodos: List<TodoItem>? = null
    private var _cachedHidden: Set<String>? = null
    private var _cachedPinned: Set<Long>? = null

    var theme: String
        get() = prefs.getString("theme", THEME_DARK)!!
        set(v) = prefs.edit().putString("theme", v).apply()

    var use24Hour: Boolean
        get() = prefs.getBoolean("use_24h", false)
        set(v) = prefs.edit().putBoolean("use_24h", v).apply()

    var showDate: Boolean
        get() = prefs.getBoolean("show_date", true)
        set(v) = prefs.edit().putBoolean("show_date", v).apply()

    var showScreenTime: Boolean
        get() = prefs.getBoolean("show_screen_time", true)
        set(v) = prefs.edit().putBoolean("show_screen_time", v).apply()

    var alignment: String
        get() = prefs.getString("alignment", ALIGN_LEFT)!!
        set(v) = prefs.edit().putString("alignment", v).apply()

    var homeAppCount: Int
        get() = prefs.getInt("home_app_count", 5)
        set(v) = prefs.edit().putInt("home_app_count", v).apply()

    var fontStyle: String
        get() = prefs.getString("font_style", FONT_DEFAULT)!!
        set(v) = prefs.edit().putString("font_style", v).apply()

    var favoritePackages: List<String>
        get() {
            _cachedFavorites?.let { return it }
            val json = prefs.getString("favorites", null) ?: return emptyList()
            return try {
                val arr = JSONArray(json)
                (0 until arr.length()).map { arr.getString(it) }.also { _cachedFavorites = it }
            } catch (_: Exception) { emptyList() }
        }
        set(list) {
            _cachedFavorites = list
            prefs.edit().putString("favorites",
                JSONArray().also { a -> list.forEach { a.put(it) } }.toString()).apply()
        }

    var hiddenPackages: Set<String>
        get() {
            _cachedHidden?.let { return it }
            return (prefs.getStringSet("hidden_apps", emptySet()) ?: emptySet()).also { _cachedHidden = it }
        }
        set(v) {
            _cachedHidden = v
            prefs.edit().putStringSet("hidden_apps", v).apply()
        }

    var todoItems: List<TodoItem>
        get() {
            _cachedTodos?.let { return it }
            val json = prefs.getString("todos_v2", null)
            if (json != null) {
                return try {
                    val arr = JSONArray(json)
                    (0 until arr.length()).map {
                        val obj = arr.getJSONObject(it)
                        TodoItem(obj.getString("t"), obj.optBoolean("d", false))
                    }.also { _cachedTodos = it }
                } catch (_: Exception) { emptyList() }
            }
            val legacy = prefs.getString("todos", null)
            if (legacy != null) {
                return try {
                    val arr = JSONArray(legacy)
                    (0 until arr.length()).map { TodoItem(arr.getString(it), false) }.also { _cachedTodos = it }
                } catch (_: Exception) { emptyList() }
            }
            return emptyList()
        }
        set(list) {
            _cachedTodos = list
            val arr = JSONArray()
            list.forEach { item ->
                arr.put(JSONObject().apply { put("t", item.text); put("d", item.done) })
            }
            prefs.edit().putString("todos_v2", arr.toString()).apply()
        }

    // ── Pinned calendar events ────────────────────────────────────────────────
    var pinnedEventIds: Set<Long>
        get() {
            _cachedPinned?.let { return it }
            val json = prefs.getString("pinned_events", null) ?: return emptySet()
            return try {
                val arr = org.json.JSONArray(json)
                (0 until arr.length()).map { arr.getLong(it) }.toSet().also { _cachedPinned = it }
            } catch (_: Exception) { emptySet() }
        }
        set(ids) {
            _cachedPinned = ids
            val arr = org.json.JSONArray()
            ids.forEach { arr.put(it) }
            prefs.edit().putString("pinned_events", arr.toString()).apply()
        }

    // ── Transparent background ────────────────────────────────────────────────
    var transparentBg: Boolean
        get() = prefs.getBoolean("transparent_bg", false)
        set(v) = prefs.edit().putBoolean("transparent_bg", v).apply()

    // ── Weather ───────────────────────────────────────────────────────────────
    var weatherLat: Double
        get() = java.lang.Double.longBitsToDouble(prefs.getLong("w_lat", java.lang.Double.doubleToLongBits(Double.NaN)))
        set(v) = prefs.edit().putLong("w_lat", java.lang.Double.doubleToLongBits(v)).apply()

    var weatherLon: Double
        get() = java.lang.Double.longBitsToDouble(prefs.getLong("w_lon", java.lang.Double.doubleToLongBits(Double.NaN)))
        set(v) = prefs.edit().putLong("w_lon", java.lang.Double.doubleToLongBits(v)).apply()

    var weatherCity: String
        get() = prefs.getString("w_city", "") ?: ""
        set(v) = prefs.edit().putString("w_city", v).apply()

    var weatherTempC: Double
        get() = java.lang.Double.longBitsToDouble(prefs.getLong("w_temp", java.lang.Double.doubleToLongBits(Double.NaN)))
        set(v) = prefs.edit().putLong("w_temp", java.lang.Double.doubleToLongBits(v)).apply()

    var weatherCode: Int
        get() = prefs.getInt("w_code", -1)
        set(v) = prefs.edit().putInt("w_code", v).apply()

    var weatherLastMs: Long
        get() = prefs.getLong("w_last_ms", 0L)
        set(v) = prefs.edit().putLong("w_last_ms", v).apply()

    fun hasWeatherLocation() = !weatherLat.isNaN() && !weatherLon.isNaN()
    fun hasWeatherCache()    = weatherCode >= 0 && !weatherTempC.isNaN()

    companion object {
        const val THEME_DARK   = "dark"
        const val THEME_LIGHT  = "light"
        const val THEME_OLED   = "oled"
        const val ALIGN_LEFT   = "left"
        const val ALIGN_CENTER = "center"
        const val ALIGN_RIGHT  = "right"
        const val FONT_DEFAULT = "default"
        const val FONT_PIXEL   = "pixel"
    }
}
