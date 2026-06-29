package com.zzz.webvideobrowser

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator

class MiuiVideoSeekBar @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#4DFFFFFF") }
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#007AFF") }
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }

    private var currentProgress = 0
    private var maxProgress = 1000

    private val dp = context.resources.displayMetrics.density
    private val normalTrackHeight = 3 * dp
    private val activeTrackHeight = 8 * dp
    private val normalThumbRadius = 0 * dp
    private val activeThumbRadius = 8 * dp

    private var currentTrackHeight = normalTrackHeight
    private var currentThumbRadius = normalThumbRadius

    private var animator: ValueAnimator? = null
    private var isDragging = false

    var onSeekBarChangeListener: OnSeekBarChangeListener? = null

    interface OnSeekBarChangeListener {
        fun onProgressChanged(seekBar: MiuiVideoSeekBar, progress: Int, fromUser: Boolean)
        fun onStartTrackingTouch(seekBar: MiuiVideoSeekBar)
        fun onStopTrackingTouch(seekBar: MiuiVideoSeekBar)
    }

    var progress: Int
        get() = currentProgress
        set(value) {
            if (!isDragging) {
                currentProgress = value.coerceIn(0, maxProgress)
                invalidate()
            }
        }

    var max: Int
        get() = maxProgress
        set(value) {
            maxProgress = value
            invalidate()
        }

    private fun animateState(active: Boolean) {
        animator?.cancel()
        val targetHeight = if (active) activeTrackHeight else normalTrackHeight
        val targetThumb = if (active) activeThumbRadius else normalThumbRadius

        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 200
            interpolator = DecelerateInterpolator()
            val startHeight = currentTrackHeight
            val startThumb = currentThumbRadius
            addUpdateListener { anim ->
                val fraction = anim.animatedFraction
                currentTrackHeight = startHeight + (targetHeight - startHeight) * fraction
                currentThumbRadius = startThumb + (targetThumb - startThumb) * fraction
                invalidate()
            }
            start()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isDragging = true
                animateState(true)
                updateProgressFromX(event.x)
                onSeekBarChangeListener?.onStartTrackingTouch(this)
                onSeekBarChangeListener?.onProgressChanged(this, currentProgress, true)
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                updateProgressFromX(event.x)
                onSeekBarChangeListener?.onProgressChanged(this, currentProgress, true)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                animateState(false)
                onSeekBarChangeListener?.onStopTrackingTouch(this)
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }

    private fun updateProgressFromX(x: Float) {
        val usableWidth = width - paddingLeft - paddingRight
        val percentage = ((x - paddingLeft) / usableWidth).coerceIn(0f, 1f)
        currentProgress = (percentage * maxProgress).toInt()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cy = height / 2f
        val startX = paddingLeft.toFloat()
        val endX = width - paddingRight.toFloat()
        val usableWidth = endX - startX

        val progressX = startX + if (maxProgress > 0) (currentProgress.toFloat() / maxProgress) * usableWidth else 0f

        val trackRect = RectF(startX, cy - currentTrackHeight / 2f, endX, cy + currentTrackHeight / 2f)
        canvas.drawRoundRect(trackRect, currentTrackHeight / 2f, currentTrackHeight / 2f, trackPaint)

        val progressRect = RectF(startX, cy - currentTrackHeight / 2f, progressX, cy + currentTrackHeight / 2f)
        canvas.drawRoundRect(progressRect, currentTrackHeight / 2f, currentTrackHeight / 2f, progressPaint)

        if (currentThumbRadius > 0) {
            canvas.drawCircle(progressX, cy, currentThumbRadius, thumbPaint)
        }
    }
}
