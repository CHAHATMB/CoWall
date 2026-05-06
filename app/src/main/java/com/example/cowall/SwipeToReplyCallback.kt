package com.example.cowall

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.HapticFeedbackConstants
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.cowall.data.MessageModel
import kotlin.math.abs
import kotlin.math.min

class SwipeToReplyCallback(
    private val getMessageAt: (Int) -> MessageModel?,
    private val onSwipeReply: (MessageModel, Int) -> Unit
) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.RIGHT) {

    private val maxSwipeDistance = 250f
    private var hasTriggeredHaptic = false

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean = false

    override fun getSwipeDirs(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
        // Date headers are not swipeable
        return if (getMessageAt(viewHolder.bindingAdapterPosition) == null) 0
        else super.getSwipeDirs(recyclerView, viewHolder)
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        val position = viewHolder.bindingAdapterPosition
        val message = getMessageAt(position) ?: return
        onSwipeReply(message, position)
    }

    override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder): Float = 0.3f

    override fun getSwipeEscapeVelocity(defaultValue: Float): Float = defaultValue * 2

    override fun onChildDraw(
        canvas: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        val clampedDx = min(dX, maxSwipeDistance)

        if (isCurrentlyActive && clampedDx > maxSwipeDistance * 0.3f && !hasTriggeredHaptic) {
            viewHolder.itemView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            hasTriggeredHaptic = true
        }

        if (!isCurrentlyActive) {
            hasTriggeredHaptic = false
        }

        val alpha = (abs(clampedDx) / maxSwipeDistance).coerceIn(0f, 1f)
        val arrowCenterX = viewHolder.itemView.left + 60f
        val arrowCenterY = (viewHolder.itemView.top + viewHolder.itemView.bottom) / 2f

        val paint = Paint().apply {
            color = 0xFFE94560.toInt()
            this.alpha = (alpha * 255).toInt()
            style = Paint.Style.STROKE
            strokeWidth = 6f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
        }

        val arrowSize = 16f
        val path = Path().apply {
            moveTo(arrowCenterX - arrowSize, arrowCenterY - arrowSize)
            lineTo(arrowCenterX, arrowCenterY)
            lineTo(arrowCenterX - arrowSize, arrowCenterY + arrowSize)
        }
        canvas.drawPath(path, paint)

        super.onChildDraw(canvas, recyclerView, viewHolder, clampedDx, dY, actionState, isCurrentlyActive)
    }
}
