package com.moqayed.zam

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.ScrollView

class LockableScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ScrollView(context, attrs) {

    var isScrollEnabled = true

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        return if (isScrollEnabled) {
            super.onInterceptTouchEvent(ev)
        } else {
            false // Don’t intercept touch events — let child handle them
        }
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        return if (isScrollEnabled) {
            super.onTouchEvent(ev)
        } else {
            false
        }
    }
}
