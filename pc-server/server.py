"""
Phone Mouse & Keyboard - PC server.

Receives newline-delimited JSON commands from the Android app over TCP
and turns them into mouse / keyboard actions with PyAutoGUI.

Works on Windows and Linux (X11).
Run:  python server.py            (default port 5050)
      python server.py 6000       (custom port)
"""

import json
import socket
import sys
import threading

import pyautogui

try:  # optional: lets non-ASCII text (Hindi, emoji, ...) be typed via clipboard
    import pyperclip
except ImportError:
    pyperclip = None

DEFAULT_PORT = 5050

pyautogui.FAILSAFE = False  # don't abort when the cursor hits a screen corner
pyautogui.PAUSE = 0         # no artificial delay between actions -> smooth motion


def local_ip():
    """Best guess of this PC's LAN IP (no traffic is actually sent)."""
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("8.8.8.8", 80))
        return s.getsockname()[0]
    except OSError:
        return "127.0.0.1"
    finally:
        s.close()


def type_text(text):
    if text.isascii():
        pyautogui.write(text)
    elif pyperclip is not None:
        pyperclip.copy(text)
        pyautogui.hotkey("ctrl", "v")
    else:
        # PyAutoGUI can only type ASCII; type what we can.
        ascii_part = "".join(c for c in text if c.isascii())
        if ascii_part:
            pyautogui.write(ascii_part)
        print("  (skipped non-ASCII text - `pip install pyperclip` to enable it)")


def handle(cmd):
    kind = cmd.get("type")
    if kind == "move":
        pyautogui.moveRel(int(cmd["dx"]), int(cmd["dy"]))
    elif kind == "click":
        button = cmd.get("button", "left")
        if button in ("left", "right"):
            pyautogui.click(button=button)
    elif kind == "text":
        type_text(cmd.get("text", ""))
    elif kind == "key":
        key = cmd.get("key")
        if key in ("backspace", "enter"):
            pyautogui.press(key, presses=max(1, int(cmd.get("count", 1))))


def serve_client(conn, addr):
    print(f"Connected: {addr[0]}")
    conn.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
    buffer = b""
    try:
        with conn:
            while True:
                data = conn.recv(4096)
                if not data:
                    break
                buffer += data
                while b"\n" in buffer:
                    line, buffer = buffer.split(b"\n", 1)
                    if not line.strip():
                        continue
                    try:
                        handle(json.loads(line.decode("utf-8")))
                    except (ValueError, KeyError, TypeError) as e:
                        print(f"  bad command {line!r}: {e}")
    except OSError:
        pass
    print(f"Disconnected: {addr[0]}")


def main():
    port = int(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_PORT
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server.bind(("0.0.0.0", port))
    server.listen(1)

    print("=" * 44)
    print(" Phone Mouse & Keyboard server is running")
    print(f" Enter this in the app:  {local_ip()}:{port}")
    print(" Press Ctrl+C to stop.")
    print("=" * 44)

    try:
        while True:
            conn, addr = server.accept()
            threading.Thread(target=serve_client, args=(conn, addr), daemon=True).start()
    except KeyboardInterrupt:
        print("\nStopped.")
    finally:
        server.close()


if __name__ == "__main__":
    main()
