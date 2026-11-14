package com.example.phonemouse

import android.content.Context
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.hypot
import kotlin.math.min

/**
 * One-hand touchpad (everything works with a single thumb):
 *  - drag                   -> move cursor
 *  - tap                    -> left click   (tap twice = double click)
 *  - hold                   -> right click  (also: two-finger tap)
 *  - double-tap and drag    -> drag / select (hold left button while moving)
 *  - drag on the right edge -> scroll
 */
class TouchpadView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    interface Listener {
        fun onMove(dx: Int, dy: Int)
        fun onLeftClick()
        fun onRightClick()
        fun onScroll(steps: Int)
        fun onDragStart()
        fun onDragEnd()
    }

    var listener: Listener? = null

    /** Base cursor speed. Higher = faster. */
    var sensitivity = 1.6f

    private enum class Mode { MOVE, SCROLL }

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val density = resources.displayMetrics.density
    private val scrollZoneWidth = resources.getDimension(R.dimen.scroll_zone_width)
    private val scrollStepPx = 18 * density

    private var mode = Mode.MOVE
    private var lastX = 0f
    private var lastY = 0f
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var maxPointers = 0
    private var moved = false
    private var remX = 0f
    private var remY = 0f
    private var scrollAcc = 0f

    private var lastTapUpTime = 0L
    private var dragCandidate = false
    private var dragging = false
    private var longPressed = false

    private val longPressAction = Runnable {
        if (!moved && maxPointers == 1) {
            longPressed = true
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            listener?.onRightClick()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x; lastY = event.y
                downX = event.x; downY = event.y
                downTime = event.eventTime
                maxPointers = 1
                moved = false
                longPressed = false
                remX = 0f; remY = 0f; scrollAcc = 0f
                mode = if (event.x >= width - scrollZoneWidth) Mode.SCROLL else Mode.MOVE
                dragCandidate = mode == Mode.MOVE && event.eventTime - lastTapUpTime < DOUBLE_TAP_MS
                postDelayed(longPressAction, LONG_PRESS_MS)
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                maxPointers = maxOf(maxPointers, event.pointerCount)
                removeCallbacks(longPressAction)
            }

            MotionEvent.ACTION_MOVE -> {
                if (!moved && hypot(event.x - downX, event.y - downY) > touchSlop) {
                    moved = true
                    removeCallbacks(longPressAction)
                    if (dragCandidate && maxPointers == 1 && !longPressed) {
                        dragging = true
                        listener?.onDragStart()
                    }
                }
                if (maxPointers == 1 && !longPressed) {
                    if (mode == Mode.SCROLL) scroll(event.y - lastY) else move(event.x - lastX, event.y - lastY)
                }
                lastX = event.x; lastY = event.y
            }

            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPressAction)
                val isTap = !moved && !longPressed && event.eventTime - downTime < TAP_TIMEOUT_MS
                when {
                    dragging -> {
                        dragging = false
                        listener?.onDragEnd()
                        lastTapUpTime = 0L
                    }
                    isTap && maxPointers >= 2 -> {
                        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        listener?.onRightClick()
                        lastTapUpTime = 0L
                    }
                    isTap -> {
                        performClick()
                        lastTapUpTime = event.eventTime
                    }
                    else -> lastTapUpTime = 0L
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPressAction)
                if (dragging) {
                    dragging = false
                    listener?.onDragEnd()
                }
            }
        }
        return true
    }

    private fun move(dxPx: Float, dyPx: Float) {
        val rawX = dxPx / density
        val rawY = dyPx / density
        // simple acceleration: faster swipes travel further
        val gain = sensitivity * (1f + min(hypot(rawX, rawY) / 10f, 2f))
        val fx = rawX * gain + remX
        val fy = rawY * gain + remY
        val ix = fx.toInt()
        val iy = fy.toInt()
        remX = fx - ix
        remY = fy - iy
        if (ix != 0 || iy != 0) listener?.onMove(ix, iy)
    }

    private fun scroll(dyPx: Float) {
        // natural scrolling, like on the phone: thumb down -> page moves down (scroll up)
        scrollAcc += dyPx
        val steps = (scrollAcc / scrollStepPx).toInt()
        if (steps != 0) {
            scrollAcc -= steps * scrollStepPx
            listener?.onScroll(steps)
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        listener?.onLeftClick()
        return true
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(longPressAction)
        super.onDetachedFromWindow()
    }

    private companion object {
        const val TAP_TIMEOUT_MS = 250L
        const val DOUBLE_TAP_MS = 300L
        const val LONG_PRESS_MS = 500L
    }
}
