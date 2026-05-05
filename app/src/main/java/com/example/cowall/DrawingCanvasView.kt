package com.example.cowall

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class DrawingCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class StrokePath(val path: Path, val color: Int, val widthPx: Float)

    private val completedStrokes = mutableListOf<StrokePath>()
    private var currentPath: Path? = null
    private var lastX = 0f
    private var lastY = 0f

    private var currentColor: Int = android.graphics.Color.RED
    private var currentWidthPx: Float = 16f

    var isDrawingEnabled: Boolean = false

    private val drawPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        for (stroke in completedStrokes) {
            drawPaint.color = stroke.color
            drawPaint.strokeWidth = stroke.widthPx
            canvas.drawPath(stroke.path, drawPaint)
        }
        currentPath?.let { path ->
            drawPaint.color = currentColor
            drawPaint.strokeWidth = currentWidthPx
            canvas.drawPath(path, drawPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isDrawingEnabled) return false
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                currentPath = Path().apply { moveTo(lastX, lastY) }
            }
            MotionEvent.ACTION_MOVE -> {
                val midX = (lastX + event.x) / 2f
                val midY = (lastY + event.y) / 2f
                currentPath?.quadTo(lastX, lastY, midX, midY)
                lastX = event.x
                lastY = event.y
            }
            MotionEvent.ACTION_UP -> {
                currentPath?.lineTo(event.x, event.y)
                currentPath?.let { completedStrokes.add(StrokePath(it, currentColor, currentWidthPx)) }
                currentPath = null
            }
        }
        invalidate()
        return true
    }

    fun setStrokeColor(color: Int) { currentColor = color }
    fun setStrokeWidthPx(widthPx: Float) { currentWidthPx = widthPx }

    fun undo() {
        if (completedStrokes.isNotEmpty()) {
            completedStrokes.removeAt(completedStrokes.lastIndex)
            invalidate()
        }
    }

    fun clearAll() {
        completedStrokes.clear()
        currentPath = null
        invalidate()
    }

    fun getStrokesSnapshot(): List<StrokePath> = completedStrokes.toList()
}
