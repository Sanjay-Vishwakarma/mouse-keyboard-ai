package com.example.phonemouse

import android.app.Activity
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private enum class State { DISCONNECTED, CONNECTING, CONNECTED }

    private lateinit var connection: Connection
    private lateinit var statusChip: TextView
    private lateinit var overlay: View
    private lateinit var overlayTitle: TextView
    private lateinit var overlaySubtitle: TextView
    private lateinit var connectButton: Button
    private lateinit var controls: View
    private lateinit var typingInput: TypingEditText

    private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }

    private var state = State.DISCONNECTED
    private var host = ""

    /** Text in the typing field that has already been sent to the PC. */
    private var sentText = ""
    private var ignoreTextChange = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusChip = findViewById(R.id.statusChip)
        overlay = findViewById(R.id.overlay)
        overlayTitle = findViewById(R.id.overlayTitle)
        overlaySubtitle = findViewById(R.id.overlaySubtitle)
        connectButton = findViewById(R.id.connectButton)
        controls = findViewById(R.id.controls)
        typingInput = findViewById(R.id.typingInput)

        connection = Connection { connected, message ->
            runOnUiThread { onConnectionChanged(connected, message) }
        }

        connectButton.setOnClickListener { showConnectDialog() }
        statusChip.setOnClickListener {
            if (state == State.CONNECTED) showDisconnectDialog() else showConnectDialog()
        }

        setupTouchpad()
        setupTyping()
        render()
    }

    // ---------------------------------------------------------------- connection

    private fun showConnectDialog() {
        if (state == State.CONNECTING) return
        val view = layoutInflater.inflate(R.layout.dialog_connect, null)
        val input = view.findViewById<EditText>(R.id.ipInput)
        input.setText(prefs.getString(KEY_ADDRESS, ""))
        input.setSelection(input.text.length)

        val dialog = AlertDialog.Builder(this, R.style.DialogTheme)
            .setTitle(R.string.connect_title)
            .setView(view)
            .setPositiveButton(R.string.connect, null)
            .setNegativeButton(R.string.cancel, null)
            .create()

        val tryConnect = {
            if (startConnect(input.text.toString())) dialog.dismiss()
            else input.error = getString(R.string.invalid_ip)
        }
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { tryConnect() }
        }
        input.setOnEditorActionListener { _, _, _ -> tryConnect(); true }
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        dialog.show()
    }

    private fun showDisconnectDialog() {
        AlertDialog.Builder(this, R.style.DialogTheme)
            .setTitle(R.string.connected)
            .setMessage(getString(R.string.connected_to, host))
            .setPositiveButton(R.string.disconnect) { _, _ ->
                connection.disconnect()
                state = State.DISCONNECTED
                render()
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    /** Returns false if the address is not a valid IPv4 address (optionally with :port). */
    private fun startConnect(raw: String): Boolean {
        val address = raw.trim()
        val ip = address.substringBefore(':')
        val portText = address.substringAfter(':', "")
        val port = if (portText.isEmpty()) DEFAULT_PORT else portText.toIntOrNull()
        val validIp = ip.split('.').let { parts ->
            parts.size == 4 && parts.all { it.isNotEmpty() && (it.toIntOrNull() ?: -1) in 0..255 }
        }
        if (!validIp || port == null || port !in 1..65535) return false

        prefs.edit().putString(KEY_ADDRESS, address).apply()
        host = if (port == DEFAULT_PORT) ip else "$ip:$port"
        state = State.CONNECTING
        render()
        connection.connect(ip, port)
        return true
    }

    private fun onConnectionChanged(connected: Boolean, message: String) {
        if (connected) {
            state = State.CONNECTED
            toast(getString(R.string.connected_to, host))
        } else {
            if (state != State.DISCONNECTED) toast(message)
            state = State.DISCONNECTED
        }
        render()
    }

    /** Updates every view to match the current [state]. */
    private fun render() {
        val (label, color) = when (state) {
            State.CONNECTED -> getString(R.string.connected) to R.color.status_ok
            State.CONNECTING -> getString(R.string.connecting) to R.color.status_wait
            State.DISCONNECTED -> getString(R.string.not_connected) to R.color.status_off
        }
        statusChip.text = label
        statusChip.compoundDrawableTintList = ColorStateList.valueOf(getColor(color))

        val connected = state == State.CONNECTED
        overlay.visibility = if (connected) View.GONE else View.VISIBLE
        overlayTitle.setText(if (state == State.CONNECTING) R.string.connecting else R.string.not_connected)
        overlaySubtitle.text =
            if (state == State.CONNECTING) getString(R.string.connecting_to, host)
            else getString(R.string.overlay_hint)
        connectButton.isEnabled = state == State.DISCONNECTED
        connectButton.setText(if (state == State.CONNECTING) R.string.connecting else R.string.connect)

        controls.alpha = if (connected) 1f else 0.35f
        setEnabledRecursive(controls, connected)
        typingInput.setHint(if (connected) R.string.typing_hint else R.string.typing_hint_disabled)
        if (!connected) {
            typingInput.closeKeyboard()
            clearTypingField()
        }

        if (connected) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    // ---------------------------------------------------------------- mouse

    private fun setupTouchpad() {
        findViewById<TouchpadView>(R.id.touchpad).listener = object : TouchpadView.Listener {
            override fun onMove(dx: Int, dy: Int) = connection.move(dx, dy)
            override fun onLeftClick() = connection.click("left")
            override fun onRightClick() = connection.click("right")
            override fun onScroll(steps: Int) = connection.scroll(steps)
            override fun onDragStart() = connection.mouseDown()
            override fun onDragEnd() = connection.mouseUp()
        }
        findViewById<Button>(R.id.leftButton).setOnClickListener { connection.click("left") }
        findViewById<Button>(R.id.rightButton).setOnClickListener { connection.click("right") }
    }

    // ---------------------------------------------------------------- keyboard

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
            true // keep the keyboard open
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

    // ---------------------------------------------------------------- helpers

    private fun setEnabledRecursive(view: View, enabled: Boolean) {
        view.isEnabled = enabled
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) setEnabledRecursive(view.getChildAt(i), enabled)
        }
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        connection.disconnect()
        super.onDestroy()
    }

    private companion object {
        const val DEFAULT_PORT = 5050
        const val KEY_ADDRESS = "address"
    }
}
