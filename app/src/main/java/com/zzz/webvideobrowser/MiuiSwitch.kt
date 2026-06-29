package com.zzz.webvideobrowser

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator

class MiuiSwitch @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var isChecked = false
    private var onCheckedChangeListener: ((Boolean) -> Unit)? = null

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val trackRect = RectF()
    private var thumbX = 0f
    private var trackColor = Color.parseColor("#E5E5EA")

    private val checkedColor = Color.parseColor("#3478F6")
    private val uncheckedColor = Color.parseColor("#E5E5EA")

    private var animator: ValueAnimator? = null

    init {
        thumbPaint.color = Color.WHITE
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        // 渲染细腻的高级弥散阴影，这是原生属性无法直接完美提供的
        val density = resources.displayMetrics.density
        thumbPaint.setShadowLayer(4f * density, 0f, 1f * density, Color.parseColor("#22000000"))
        
        isClickable = true
    }

    var checked: Boolean
        get() = isChecked
        set(value) {
            setChecked(value, false)
        }

    fun setChecked(checked: Boolean, animate: Boolean = true) {
        if (isChecked == checked) return
        isChecked = checked

        if (animate) {
            startAnimation()
        } else {
            thumbX = if (isChecked) getOnX() else getOffX()
            trackColor = if (isChecked) checkedColor else uncheckedColor
            invalidate()
        }
    }
    
    fun setCheckedNoEvent(checked: Boolean) {
        if (isChecked == checked) return
        isChecked = checked
        thumbX = if (isChecked) getOnX() else getOffX()
        trackColor = if (isChecked) checkedColor else uncheckedColor
        invalidate()
    }

    fun setOnCheckedChangeListener(listener: (Boolean) -> Unit) {
        onCheckedChangeListener = listener
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val density = resources.displayMetrics.density
        // 为阴影预留一些边缘空间，所以整体稍大一点，但轨道固定 52x32
        val w = (60 * density).toInt()
        val h = (40 * density).toInt()
        setMeasuredDimension(w, h)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val density = resources.displayMetrics.density
        // 居中放置轨道
        val trackW = 52 * density
        val trackH = 32 * density
        val cx = w / 2f
        val cy = h / 2f
        trackRect.set(cx - trackW / 2f, cy - trackH / 2f, cx + trackW / 2f, cy + trackH / 2f)
        
        if (animator?.isRunning != true) {
            thumbX = if (isChecked) getOnX() else getOffX()
        }
    }

    private fun getOffX(): Float = trackRect.left + trackRect.height() / 2f
    private fun getOnX(): Float = trackRect.right - trackRect.height() / 2f

    private fun startAnimation() {
        animator?.cancel()
        
        val startX = thumbX
        val endX = if (isChecked) getOnX() else getOffX()
        
        val startColor = trackColor
        val endColor = if (isChecked) checkedColor else uncheckedColor

        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 350
            // 贝塞尔曲线，带有极其轻微的触底回弹
            interpolator = OvershootInterpolator(0.8f)
            addUpdateListener { anim ->
                val fraction = anim.animatedFraction
                thumbX = startX + (endX - startX) * fraction
                trackColor = ArgbEvaluator().evaluate(fraction, startColor, endColor) as Int
                invalidate()
            }
            start()
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        setChecked(!isChecked, true)
        onCheckedChangeListener?.invoke(isChecked)
        return true
    }

    override fun onDraw(canvas: Canvas) {
        val r = trackRect.height() / 2f
        
        // Draw track
        trackPaint.color = trackColor
        canvas.drawRoundRect(trackRect, r, r, trackPaint)
        
        // Draw thumb (留出 2dp 边距)
        val thumbR = r - 2 * resources.displayMetrics.density
        canvas.drawCircle(thumbX, trackRect.centerY(), thumbR, thumbPaint)
    }
}
