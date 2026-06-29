package com.zzz.webvideobrowser

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.FrameLayout

class GestureInterceptLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    var interceptCallback: ((MotionEvent) -> Boolean)? = null
    var touchCallback: ((MotionEvent) -> Boolean)? = null

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        val intercepted = interceptCallback?.invoke(ev) ?: false
        if (intercepted) return true
        return super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val handled = touchCallback?.invoke(event) ?: false
        if (handled) return true
        return super.onTouchEvent(event)
    }
}
