package com.example.phonemouse

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var connection: Connection
    private lateinit var addressInput: EditText
    private lateinit var connectButton: Button
    private lateinit var statusText: TextView
    private lateinit var typingInput: TypingEditText

    /** Text in the typing field that has already been sent to the PC. */
    private var sentText = ""
    private var ignoreTextChange = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        addressInput = findViewById(R.id.addressInput)
        connectButton = findViewById(R.id.connectButton)
        statusText = findViewById(R.id.statusText)
        typingInput = findViewById(R.id.typingInput)

        connection = Connection { connected, message ->
            runOnUiThread { showStatus(connected, message) }
        }

        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        addressInput.setText(prefs.getString(KEY_ADDRESS, ""))

        connectButton.setOnClickListener {
            if (connection.isConnected) {
                connection.disconnect()
                showStatus(false, "Disconnected")
            } else {
                connect()
            }
        }
        addressInput.setOnEditorActionListener { _, _, _ -> connect(); true }

        setupTouchpad()
        setupTyping()
    }

    private fun connect() {
        val raw = addressInput.text.toString().trim()
        if (raw.isEmpty()) {
            showStatus(false, "Enter your PC's IP address")
            return
        }
        val host = raw.substringBefore(':')
        val port = raw.substringAfter(':', "").toIntOrNull() ?: DEFAULT_PORT

        getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit().putString(KEY_ADDRESS, raw).apply()

        hideKeyboard()
        addressInput.clearFocus()
        statusText.text = "Connecting to $host:$port…"
        connection.connect(host, port)
    }

    private fun setupTouchpad() {
        findViewById<TouchpadView>(R.id.touchpad).listener = object : TouchpadView.Listener {
            override fun onMove(dx: Int, dy: Int) = connection.move(dx, dy)
            override fun onLeftClick() = connection.click("left")
            override fun onRightClick() = connection.click("right")
        }
        findViewById<Button>(R.id.leftButton).setOnClickListener { connection.click("left") }
        findViewById<Button>(R.id.rightButton).setOnClickListener { connection.click("right") }
    }

    private fun setupTyping() {
        typingInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (!ignoreTextChange) syncText(s?.toString() ?: "")
            }
        })

        // Backspace on an empty field still deletes on the PC.
        typingInput.onEmptyBackspace = { connection.key("backspace") }

        // Keyboard's Enter / Send key -> Enter on the PC, then clear the field.
        typingInput.setOnEditorActionListener { _, actionId, event ->
            val isEnter = actionId == EditorInfo.IME_ACTION_SEND ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER &&
                    event.action == KeyEvent.ACTION_DOWN)
            if (isEnter) {
                connection.key("enter")
                clearTypingField()
            }
            true
        }
    }

    /**
     * Compares the field with what was already sent and sends only the difference:
     * backspaces for removed characters, then the newly added text.
     * This also handles keyboard autocorrect / suggestions replacing a word.
     */
    private fun syncText(now: String) {
        val common = sentText.commonPrefixWith(now).length
        val removed = sentText.codePointCount(common, sentText.length)
        val added = now.substring(common)
        if (removed > 0) connection.key("backspace", removed)
        if (added.isNotEmpty()) connection.text(added)
        sentText = now
    }

    private fun clearTypingField() {
        ignoreTextChange = true
        typingInput.text.clear()
        ignoreTextChange = false
        sentText = ""
    }

    private fun showStatus(connected: Boolean, message: String) {
        statusText.text = message
        connectButton.setText(if (connected) R.string.disconnect else R.string.connect)
        statusText.setTextColor(getColor(if (connected) R.color.status_ok else R.color.text_secondary))
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(addressInput.windowToken, 0)
    }

    override fun onDestroy() {
        connection.disconnect()
        super.onDestroy()
    }

    private companion object {
        const val DEFAULT_PORT = 5050
        const val KEY_ADDRESS = "address"
    }
}
