package com.flowlauncher

import android.content.Context
import android.graphics.Typeface
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat

object FontHelper {
    private var pixelTypeface: Typeface? = null

    fun getTypeface(context: Context, prefs: Prefs): Typeface {
        return if (prefs.fontStyle == Prefs.FONT_PIXEL) {
            if (pixelTypeface == null) {
                try {
                    pixelTypeface = ResourcesCompat.getFont(context, R.font.dotgothic)
                } catch (e: Exception) {
                    return Typeface.DEFAULT
                }
            }
            pixelTypeface ?: Typeface.DEFAULT

// github.com/01satria
        } else {
            Typeface.DEFAULT
        }
    }

    fun applyFont(context: Context, prefs: Prefs, vararg views: TextView) {
        val tf = getTypeface(context, prefs)
        for (v in views) {
            v.typeface = tf
        }
    }
}
