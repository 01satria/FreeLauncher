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

    private var letters = charArrayOf()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 40f
        isFakeBoldText = true
        setShadowLayer(3f, 0f, 1f, Color.parseColor("#AA000000"))
    }

    private var letterWidth = 0f
    private var selectedIndex = -1

    private var numColsRow1 = 0
    private var numColsRow2 = 0
    private var row1WidthOffset = 0f
    private var row2WidthOffset = 0f

    var onLetterSelected: ((Char) -> Unit)? = null

    fun setLetters(validLetters: List<Char>) {
        this.letters = validLetters.toCharArray()
        calculateDimensions(width, height)
        requestLayout()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        calculateDimensions(w, h)
    }

    private fun calculateDimensions(w: Int, h: Int) {
        if (letters.isEmpty() || w == 0 || h == 0) return
        
        // Split into two rows, max 13 per row, dynamically balanced
        val half = (letters.size + 1) / 2
        numColsRow1 = half.coerceAtMost(13)
        numColsRow2 = letters.size - numColsRow1
        
        // Base letter width on the longest row (up to 13 items)
        val maxCols = maxOf(numColsRow1, numColsRow2)
        letterWidth = w.toFloat() / maxCols
        
        // Base text size on the new height with 2 lines (compact spacing)
        // h / 2 would be exactly half, but we want dense text, so we let the size be larger relative to height per row
        val rowHeight = h / 2f
        paint.textSize = (letterWidth * 0.8f).coerceAtMost(rowHeight * 0.8f)
        
        // To center each row independently across the width
        row1WidthOffset = (w - (numColsRow1 * letterWidth)) / 2f
        row2WidthOffset = (w - (numColsRow2 * letterWidth)) / 2f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (letterWidth == 0f || letters.isEmpty()) return

        val textHeightOffset = (paint.descent() + paint.ascent()) / 2f
        
        // Make the two rows very tight
        val topRowY = (height / 4f) * 1.5f - textHeightOffset
        val bottomRowY = (height / 4f) * 3f - textHeightOffset

        for (i in letters.indices) {
            val isRow1 = i < numColsRow1
            val colIndex = if (isRow1) i else i - numColsRow1
            val rowOffset = if (isRow1) row1WidthOffset else row2WidthOffset
            val centerY = if (isRow1) topRowY else bottomRowY
            
            val centerX = rowOffset + colIndex * letterWidth + letterWidth / 2f
            
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
                if (letters.isEmpty()) return false
                
                // Determine row based on Y coordinate
                val isRow1 = event.y < height / 2f
                val rowOffset = if (isRow1) row1WidthOffset else row2WidthOffset
                val numCols = if (isRow1) numColsRow1 else numColsRow2
                val startIndex = if (isRow1) 0 else numColsRow1
                
                // Localize X to the specific row's offset
                val localX = event.x - rowOffset
                var colIndex = (localX / letterWidth).toInt()
                
                // Coerce strictly within bounds of this row
                if (colIndex < 0) colIndex = 0
                if (colIndex >= numCols) colIndex = numCols - 1
                
                // If X is completely out of bounds left/right, find closest bound anyway
                val index = startIndex + colIndex
                
                if (index != selectedIndex && index in letters.indices) {
                    selectedIndex = index
                    onLetterSelected?.invoke(letters[index])
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                selectedIndex = -1
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
