package com.example.phonemouse

import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.LinkedBlockingQueue

/**
 * TCP connection to the PC server. Messages are newline-delimited JSON,
 * written from a background thread so the UI thread never blocks.
 *
 * [onStatus] is called from a background thread.
 */
class Connection(private val onStatus: (connected: Boolean, message: String) -> Unit) {

    @Volatile private var session: Session? = null

    val isConnected: Boolean get() = session?.connected == true

    fun connect(host: String, port: Int) {
        disconnect()
        session = Session(host, port).also { it.start() }
    }

    /** Closes the connection. Does not trigger [onStatus]. */
    fun disconnect() {
        session?.close()
        session = null
    }

    fun move(dx: Int, dy: Int) = send(JSONObject().put("type", "move").put("dx", dx).put("dy", dy))

    fun click(button: String) = send(JSONObject().put("type", "click").put("button", button))

    fun scroll(steps: Int) = send(JSONObject().put("type", "scroll").put("steps", steps))

    fun mouseDown() = send(JSONObject().put("type", "down").put("button", "left"))

    fun mouseUp() = send(JSONObject().put("type", "up").put("button", "left"))

    fun text(text: String) = send(JSONObject().put("type", "text").put("text", text))

    fun key(key: String, count: Int = 1) =
        send(JSONObject().put("type", "key").put("key", key).put("count", count))

    private fun send(json: JSONObject) {
        val s = session
        if (s != null && s.connected) s.queue.offer(json.toString())
    }

    private inner class Session(private val host: String, private val port: Int) :
        Thread("pc-connection") {

        val queue = LinkedBlockingQueue<String>()
        private val socket = Socket()
        @Volatile var connected = false
        @Volatile private var closed = false

        fun close() {
            closed = true
            connected = false
            interrupt()
            runCatching { socket.close() }
        }

        override fun run() {
            var error: String? = null
            var everConnected = false
            try {
                socket.connect(InetSocketAddress(host, port), 4000)
                socket.tcpNoDelay = true
                everConnected = true
                connected = true
                if (!closed) onStatus(true, "Connected to $host:$port")

                val out = socket.getOutputStream().bufferedWriter(Charsets.UTF_8)
                while (!closed) {
                    val msg = queue.take()
                    out.write(msg)
                    out.write("\n")
                    if (queue.isEmpty()) out.flush()
                }
            } catch (_: InterruptedException) {
                // close() was called
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
            } finally {
                connected = false
                runCatching { socket.close() }
            }
            if (!closed) {
                if (session === this) session = null
                val message = when {
                    error == null -> "Disconnected"
                    everConnected -> "Connection lost: $error"
                    else -> "Could not connect: $error"
                }
                onStatus(false, message)
            }
        }
    }
}
