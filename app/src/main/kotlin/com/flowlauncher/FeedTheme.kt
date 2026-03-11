package com.flowlauncher

import android.graphics.Color

/**
 * Centralised color tokens for the Feed screen.
 * Computed once per theme switch and passed to all adapters/views.
 */
data class FeedTheme(
    val surface  : Int,   // card / section background
    val surface2 : Int,   // slightly elevated surface (search bg, chips)
    val onSurface: Int,   // primary text
    val subtle   : Int,   // secondary text / labels
    val faint    : Int,   // placeholder / empty state
    val divider  : Int,   // divider line
    val isLight  : Boolean
) {
    companion object {
        fun from(prefs: Prefs): FeedTheme = when (prefs.theme) {
            Prefs.THEME_LIGHT -> FeedTheme(
                surface   = Color.parseColor("#FFFFFF"),
                surface2  = Color.parseColor("#F0F0F0"),
                onSurface = Color.parseColor("#0D0D0D"),
                subtle    = Color.parseColor("#888888"),
                faint     = Color.parseColor("#BBBBBB"),
                divider   = Color.parseColor("#E0E0E0"),
                isLight   = true
            )
            Prefs.THEME_OLED  -> FeedTheme(
                surface   = Color.parseColor("#0A0A0A"),
                surface2  = Color.parseColor("#141414"),
                onSurface = Color.parseColor("#F2F2F2"),
                subtle    = Color.parseColor("#666666"),
                faint     = Color.parseColor("#333333"),
                divider   = Color.parseColor("#1E1E1E"),
                isLight   = false
            )
            else              -> FeedTheme(  // DARK
                surface   = Color.parseColor("#161616"),
                surface2  = Color.parseColor("#202020"),
                onSurface = Color.parseColor("#F2F2F2"),
                subtle    = Color.parseColor("#777777"),
                faint     = Color.parseColor("#444444"),
                divider   = Color.parseColor("#2A2A2A"),
                isLight   = false
            )
        }

        // Page background (one level below surface)
        fun pageBg(prefs: Prefs): Int = when {
            prefs.transparentBg -> Color.TRANSPARENT
            prefs.theme == Prefs.THEME_LIGHT -> Color.parseColor("#F0F0F0")
            prefs.theme == Prefs.THEME_OLED  -> Color.BLACK
            else                             -> Color.parseColor("#0A0A0A")
        }
    }
}
