package com.example.phonemouse

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.hypot
import kotlin.math.min

/**
 * Touchpad surface.
 *  - one finger drag  -> move cursor
 *  - one finger tap   -> left click
 *  - two finger tap   -> right click
 */
class TouchpadView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    interface Listener {
        fun onMove(dx: Int, dy: Int)
        fun onLeftClick()
        fun onRightClick()
    }

    var listener: Listener? = null

    /** Base cursor speed. Higher = faster. */
    var sensitivity = 1.6f

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val density = resources.displayMetrics.density

    private var lastX = 0f
    private var lastY = 0f
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var maxPointers = 0
    private var moved = false
    private var remX = 0f
    private var remY = 0f

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x; lastY = event.y
                downX = event.x; downY = event.y
                downTime = event.eventTime
                maxPointers = 1
                moved = false
                remX = 0f; remY = 0f
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                maxPointers = maxOf(maxPointers, event.pointerCount)
            }

            MotionEvent.ACTION_MOVE -> {
                if (!moved && hypot(event.x - downX, event.y - downY) > touchSlop) moved = true
                if (maxPointers == 1) {
                    val rawX = (event.x - lastX) / density
                    val rawY = (event.y - lastY) / density
                    // simple acceleration: faster swipes travel further
                    val speed = hypot(rawX, rawY)
                    val gain = sensitivity * (1f + min(speed / 10f, 2f))
                    val fx = rawX * gain + remX
                    val fy = rawY * gain + remY
                    val ix = fx.toInt()
                    val iy = fy.toInt()
                    remX = fx - ix
                    remY = fy - iy
                    if (ix != 0 || iy != 0) listener?.onMove(ix, iy)
                }
                lastX = event.x; lastY = event.y
            }

            MotionEvent.ACTION_UP -> {
                val quick = event.eventTime - downTime < TAP_TIMEOUT_MS
                if (!moved && quick) {
                    if (maxPointers >= 2) listener?.onRightClick() else performClick()
                }
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        listener?.onLeftClick()
        return true
    }

    private companion object {
        const val TAP_TIMEOUT_MS = 250L
    }
}
