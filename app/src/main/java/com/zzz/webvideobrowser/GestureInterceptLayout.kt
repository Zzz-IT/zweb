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
    var passThroughTap: Boolean = false

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        return interceptCallback?.invoke(ev) ?: false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val handled = touchCallback?.invoke(event) ?: false
        return if (handled) true else !passThroughTap
    }
}
