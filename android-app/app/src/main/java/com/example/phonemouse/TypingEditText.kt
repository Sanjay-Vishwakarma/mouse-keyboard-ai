package com.example.phonemouse

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.view.inputmethod.InputMethodManager
import android.widget.EditText

/**
 * A normal EditText (so the phone's own keyboard - Gboard etc. - is used) with a few tweaks:
 *  - [openKeyboard] reliably brings up the soft keyboard (called on every tap);
 *  - the cursor is always kept at the end, so text diffs map cleanly to PC keystrokes;
 *  - Backspace pressed while the field is empty is reported via [onEmptyBackspace],
 *    so you can still delete text on the PC after the field was cleared.
 */
class TypingEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : EditText(context, attrs) {

    var onEmptyBackspace: (() -> Unit)? = null

    fun openKeyboard() {
        if (!isEnabled) return
        requestFocus()
        post {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                windowInsetsController?.show(WindowInsets.Type.ime())
            }
            context.getSystemService(InputMethodManager::class.java)
                ?.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    fun closeKeyboard() {
        context.getSystemService(InputMethodManager::class.java)
            ?.hideSoftInputFromWindow(windowToken, 0)
        clearFocus()
    }

    override fun onFocusChanged(focused: Boolean, direction: Int, previouslyFocusedRect: android.graphics.Rect?) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect)
        if (focused) openKeyboard()
    }

    override fun performClick(): Boolean {
        val handled = super.performClick()
        openKeyboard()
        return handled
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val base = super.onCreateInputConnection(outAttrs) ?: return null
        return object : InputConnectionWrapper(base, true) {
            override fun sendKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_DEL && text.isNullOrEmpty()) {
                    if (event.action == KeyEvent.ACTION_DOWN) onEmptyBackspace?.invoke()
                    return true
                }
                return super.sendKeyEvent(event)
            }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                if (beforeLength > 0 && text.isNullOrEmpty()) {
                    onEmptyBackspace?.invoke()
                    return true
                }
                return super.deleteSurroundingText(beforeLength, afterLength)
            }
        }
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        val len = text?.length ?: return
        if (selStart != len || selEnd != len) setSelection(len)
    }
}
