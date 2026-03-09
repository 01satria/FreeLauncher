package com.minimallauncher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class AlphabetStripView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ#".toCharArray()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 40f
        isFakeBoldText = true
        setShadowLayer(3f, 0f, 1f, Color.parseColor("#AA000000"))
    }

    private var letterWidth = 0f
    private var selectedIndex = -1

    var onLetterSelected: ((Char) -> Unit)? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        letterWidth = w.toFloat() / letters.size
        paint.textSize = (letterWidth * 0.8f).coerceAtMost(h * 0.6f) // Auto-scale text
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (letterWidth == 0f) return

        val centerY = height / 2f - (paint.descent() + paint.ascent()) / 2f

        for (i in letters.indices) {
            val centerX = i * letterWidth + letterWidth / 2f
            
            // Highlight selected letter
            if (i == selectedIndex) {
                paint.color = Color.parseColor("#FFBB86FC") // Default highlight purple
            } else {
                paint.color = Color.WHITE
            }
            
            canvas.drawText(letters[i].toString(), centerX, centerY, paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val index = (event.x / letterWidth).toInt().coerceIn(0, letters.size - 1)
                if (index != selectedIndex) {
                    selectedIndex = index
                    onLetterSelected?.invoke(letters[index])
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // Return to unselected state
                selectedIndex = -1
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
