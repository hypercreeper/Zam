package com.moqayed.zam

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.animation.AnimationUtils
import android.widget.LinearLayout

public class TrackView: LinearLayout {
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        // Initialize your view here, potentially using attributes from attrs
    }

    // You might also want to include other constructors for programmatic creation:
    constructor(context: Context) : super(context) {
        // Initialization for programmatic creation without attributes
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        // Initialization with a default style attribute
    }
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event?.action == MotionEvent.ACTION_UP) {
            val anim = AnimationUtils.loadAnimation(context, R.anim.release)
            this.startAnimation(anim)
        } else if (event?.action == MotionEvent.ACTION_DOWN) {
            val anim = AnimationUtils.loadAnimation(context, R.anim.press)
            this.startAnimation(anim)
        } else if (event?.action == MotionEvent.ACTION_CANCEL) {
            val anim = AnimationUtils.loadAnimation(context, R.anim.release)
            this.startAnimation(anim)
        }
        return super.onTouchEvent(event)
    }
}