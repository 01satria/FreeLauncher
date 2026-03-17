// github.com/01satria
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
    val cardBg   : Int,   // card background (feed sections)
    val cardStroke: Int,  // card border
    val drawerBg : Int,   // app drawer sheet background
    val searchBg : Int,   // search bar background
    val isLight  : Boolean
) {
    companion object {
        fun from(prefs: Prefs): FeedTheme = when (prefs.theme) {
            Prefs.THEME_LIGHT -> FeedTheme(
                surface     = Color.parseColor("#F5F5F5"),
                surface2    = Color.parseColor("#EBEBEB"),
                onSurface   = Color.parseColor("#0A0A0A"),
                subtle      = Color.parseColor("#555555"),
                faint       = Color.parseColor("#999999"),
                divider     = Color.parseColor("#DDDDDD"),
                cardBg      = Color.parseColor("#FFFFFF"),
                cardStroke  = Color.parseColor("#E0E0E0"),
                drawerBg    = Color.parseColor("#F8F8F8"),
                searchBg    = Color.parseColor("#ECECEC"),
                isLight     = true
            )
            Prefs.THEME_OLED  -> FeedTheme(
                surface     = Color.BLACK,
                surface2    = Color.parseColor("#0D0D0D"),
                onSurface   = Color.parseColor("#F2F2F2"),
                subtle      = Color.parseColor("#666666"),
                faint       = Color.parseColor("#333333"),
                divider     = Color.parseColor("#1A1A1A"),
                cardBg      = Color.parseColor("#0A0A0A"),
                cardStroke  = 0x1AFFFFFF.toInt(),
                drawerBg    = Color.parseColor("#050505"),
                searchBg    = Color.parseColor("#111111"),
                isLight     = false
            )
            else -> FeedTheme(  // DARK
                surface     = Color.parseColor("#080808"),
                surface2    = Color.parseColor("#161616"),
                onSurface   = Color.parseColor("#F2F2F2"),
                subtle      = Color.parseColor("#777777"),
                faint       = Color.parseColor("#444444"),
                divider     = Color.parseColor("#2A2A2A"),
                cardBg      = Color.parseColor("#0D0D0D"),
                cardStroke  = 0x1AFFFFFF.toInt(),
                drawerBg    = Color.parseColor("#050505"),
                searchBg    = Color.parseColor("#111111"),
                isLight     = false
            )
        }

        // Page background (one level below surface)
        fun pageBg(prefs: Prefs): Int = when {
            prefs.transparentBg              -> Color.TRANSPARENT
            prefs.theme == Prefs.THEME_LIGHT -> Color.parseColor("#F0F0F0")
            prefs.theme == Prefs.THEME_OLED  -> Color.BLACK
            else                             -> Color.parseColor("#080808")
        }
    }
}
