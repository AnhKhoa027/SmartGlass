package com.example.smartglass.ObjectDetection

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.smartglass.R

class OverlayView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {

    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bounds = Rect()

    // Double buffer
    @Volatile
    private var lastResults: List<BoundingBox> = emptyList()

    init { initPaints() }

    private fun initPaints() {
        textBackgroundPaint.color = Color.BLACK
        textBackgroundPaint.style = Paint.Style.FILL
        textBackgroundPaint.textSize = 50f

        textPaint.color = Color.WHITE
        textPaint.style = Paint.Style.FILL
        textPaint.textSize = 50f

        boxPaint.color = ContextCompat.getColor(context!!, R.color.bounding_box_color)
        boxPaint.strokeWidth = 8f
        boxPaint.style = Paint.Style.STROKE
    }

    fun clear() {
        lastResults = emptyList()
        post { invalidate() }
    }

    fun setResults(results: List<BoundingBox>) {
        lastResults = results
        post { invalidate() } // luôn trên UI thread
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        lastResults.forEach { box ->
            val left = box.x1 * width
            val top = box.y1 * height
            val right = box.x2 * width
            val bottom = box.y2 * height

            canvas.drawRect(left, top, right, bottom, boxPaint)

            val text = box.clsName
            textBackgroundPaint.getTextBounds(text, 0, text.length, bounds)
            val textWidth = bounds.width()
            val textHeight = bounds.height()

            canvas.drawRect(
                left,
                top,
                left + textWidth + BOUNDING_RECT_TEXT_PADDING,
                top + textHeight + BOUNDING_RECT_TEXT_PADDING,
                textBackgroundPaint
            )
            canvas.drawText(text, left, top + bounds.height(), textPaint)
        }
    }

    companion object {
        private const val BOUNDING_RECT_TEXT_PADDING = 8
    }
}
