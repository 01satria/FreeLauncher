package com.flowlauncher

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

class Prefs(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("flow_prefs", Context.MODE_PRIVATE)

    // ── Theme ──────────────────────────────────────────────────────────────────
    var theme: String
        get() = prefs.getString("theme", THEME_DARK)!!
        set(v) = prefs.edit().putString("theme", v).apply()

    var textColor: String
        get() = prefs.getString("text_color", "#FFFFFF")!!
        set(v) = prefs.edit().putString("text_color", v).apply()

    var accentColor: String
        get() = prefs.getString("accent_color", "#FFFFFF")!!
        set(v) = prefs.edit().putString("accent_color", v).apply()

    // ── Clock ──────────────────────────────────────────────────────────────────
    var use24Hour: Boolean
        get() = prefs.getBoolean("use_24h", false)
        set(v) = prefs.edit().putBoolean("use_24h", v).apply()

    var showDate: Boolean
        get() = prefs.getBoolean("show_date", true)
        set(v) = prefs.edit().putBoolean("show_date", v).apply()

    var showScreenTime: Boolean
        get() = prefs.getBoolean("show_screen_time", true)
        set(v) = prefs.edit().putBoolean("show_screen_time", v).apply()

    // ── Layout ─────────────────────────────────────────────────────────────────
    var alignment: String
        get() = prefs.getString("alignment", ALIGN_LEFT)!!
        set(v) = prefs.edit().putString("alignment", v).apply()

    var fontSize: Int
        get() = prefs.getInt("font_size", 18)
        set(v) = prefs.edit().putInt("font_size", v).apply()

    var homeAppCount: Int
        get() = prefs.getInt("home_app_count", 5)
        set(v) = prefs.edit().putInt("home_app_count", v).apply()

    var showIcons: Boolean
        get() = prefs.getBoolean("show_icons", false)
        set(v) = prefs.edit().putBoolean("show_icons", v).apply()

    // ── Favorites ──────────────────────────────────────────────────────────────
    var favoritePackages: List<String>
        get() {
            val json = prefs.getString("favorites", null) ?: return emptyList()
            return try {
                val arr = JSONArray(json)
                (0 until arr.length()).map { arr.getString(it) }
            } catch (_: Exception) { emptyList() }
        }
        set(list) {
            val arr = JSONArray().apply { list.forEach { put(it) } }
            prefs.edit().putString("favorites", arr.toString()).apply()
        }

    // ── Hidden apps ────────────────────────────────────────────────────────────
    var hiddenPackages: Set<String>
        get() = prefs.getStringSet("hidden_apps", emptySet())!!
        set(v) = prefs.edit().putStringSet("hidden_apps", v).apply()

    // ── To-do list ─────────────────────────────────────────────────────────────
    var todos: List<String>
        get() {
            val json = prefs.getString("todos", null) ?: return emptyList()
            return try {
                val arr = JSONArray(json)
                (0 until arr.length()).map { arr.getString(it) }
            } catch (_: Exception) { emptyList() }
        }
        set(list) {
            val arr = JSONArray().apply { list.forEach { put(it) } }
            prefs.edit().putString("todos", arr.toString()).apply()
        }

    // ── Digital Detox ─────────────────────────────────────────────────────────
    var detoxEnabled: Boolean
        get() = prefs.getBoolean("detox_enabled", false)
        set(v) = prefs.edit().putBoolean("detox_enabled", v).apply()

    var detoxDurationMinutes: Int
        get() = prefs.getInt("detox_duration", 60)
        set(v) = prefs.edit().putInt("detox_duration", v).apply()

    var detoxEndTime: Long
        get() = prefs.getLong("detox_end", 0L)
        set(v) = prefs.edit().putLong("detox_end", v).apply()

    // ── First launch ───────────────────────────────────────────────────────────
    var onboardingDone: Boolean
        get() = prefs.getBoolean("onboarding_done", false)
        set(v) = prefs.edit().putBoolean("onboarding_done", v).apply()

    companion object {
        const val THEME_DARK = "dark"
        const val THEME_LIGHT = "light"
        const val THEME_OLED = "oled"
        const val ALIGN_LEFT = "left"
        const val ALIGN_CENTER = "center"
        const val ALIGN_RIGHT = "right"
    }
}
