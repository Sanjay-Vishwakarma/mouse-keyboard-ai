package com.example.phonemouse

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.widget.EditText

/**
 * A normal EditText (so the phone's own keyboard - Gboard etc. - is used) with two tweaks:
 *  - the cursor is always kept at the end, so text diffs map cleanly to PC keystrokes;
 *  - Backspace pressed while the field is empty is reported via [onEmptyBackspace],
 *    so you can still delete text on the PC after the field was cleared.
 */
class TypingEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : EditText(context, attrs) {

    var onEmptyBackspace: (() -> Unit)? = null

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
        val len = text?.length ?: 0
        if (selStart != len || selEnd != len) setSelection(len)
    }
}
