"""
PC Remote - PC server.

Receives newline-delimited JSON commands from the Android app over TCP
and turns them into mouse / keyboard actions with PyAutoGUI.
Works on Windows and Linux (X11).

    python server.py              open the window (default port 5050)
    python server.py 6000         custom port
    python server.py --console    no window, just a terminal
"""

import json
import os
import queue
import socket
import sys
import threading

try:
    import pyautogui
    STARTUP_ERROR = None
    pyautogui.FAILSAFE = False  # don't abort when the cursor hits a screen corner
    pyautogui.PAUSE = 0         # no artificial delay between actions -> smooth motion
except Exception as e:  # e.g. Linux without an X11 session
    pyautogui = None
    STARTUP_ERROR = e

try:  # optional: lets non-ASCII text (Hindi, emoji, ...) be typed via clipboard
    import pyperclip
except ImportError:
    pyperclip = None

APP_NAME = "PC Remote"
DEFAULT_PORT = 5050
WHEEL_NOTCH = 120 if sys.platform.startswith("win") else 1


def startup_error_text():
    text = f"PC Remote cannot control the mouse and keyboard.\n\n{STARTUP_ERROR}"
    if sys.platform.startswith("linux"):
        text += ("\n\nOn Linux, PC Remote needs an X11 (Xorg) session - Wayland is not supported.\n"
                 "Ubuntu: log out, click the gear icon on the login screen, choose \"Ubuntu on Xorg\".")
    return text


# ----------------------------------------------------------------------------- helpers

def resource_path(name):
    """Path to a bundled file, both when run as a script and as a PyInstaller executable."""
    base = getattr(sys, "_MEIPASS", os.path.dirname(os.path.abspath(__file__)))
    return os.path.join(base, name)


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


def dark_title_bar(window):
    """Windows 10/11: make the window's title bar dark to match the app."""
    if not sys.platform.startswith("win"):
        return
    try:
        import ctypes
        window.update_idletasks()
        hwnd = ctypes.windll.user32.GetParent(window.winfo_id())
        value = ctypes.c_int(1)
        for attribute in (20, 19):  # DWMWA_USE_IMMERSIVE_DARK_MODE (new / old builds)
            if ctypes.windll.dwmapi.DwmSetWindowAttribute(
                    hwnd, attribute, ctypes.byref(value), ctypes.sizeof(value)) == 0:
                break
    except Exception:
        pass


# ----------------------------------------------------------------------------- commands

def type_text(text, log):
    if text.isascii():
        pyautogui.write(text)
    elif pyperclip is not None:
        try:
            pyperclip.copy(text)
            pyautogui.hotkey("ctrl", "v")
        except pyperclip.PyperclipException:
            log("Install xclip to type non-English text (sudo apt install xclip)")
    else:
        ascii_part = "".join(c for c in text if c.isascii())
        if ascii_part:
            pyautogui.write(ascii_part)
        log("Skipped non-English text (pyperclip not installed)")


def button_of(cmd):
    button = cmd.get("button", "left")
    return button if button in ("left", "right") else "left"


def handle(cmd, held, log):
    """Run one command. `held` is the set of mouse buttons this client is holding down."""
    kind = cmd.get("type")
    if kind == "move":
        pyautogui.moveRel(int(cmd["dx"]), int(cmd["dy"]))
    elif kind == "click":
        pyautogui.click(button=button_of(cmd))
    elif kind == "scroll":
        # one step = one mouse-wheel notch (Windows counts a notch as 120 units)
        pyautogui.scroll(int(cmd.get("steps", 0)) * WHEEL_NOTCH)
    elif kind == "down":
        button = button_of(cmd)
        pyautogui.mouseDown(button=button)
        held.add(button)
    elif kind == "up":
        button = button_of(cmd)
        pyautogui.mouseUp(button=button)
        held.discard(button)
    elif kind == "text":
        type_text(cmd.get("text", ""), log)
    elif kind == "key":
        key = cmd.get("key")
        if key in ("backspace", "enter"):
            pyautogui.press(key, presses=max(1, int(cmd.get("count", 1))))


# ----------------------------------------------------------------------------- server

class RemoteServer:
    """TCP server. Reports events through on_event(kind, text), kind = connected/disconnected/info."""

    def __init__(self, port, on_event):
        self.port = port
        self.on_event = on_event
        self.sock = None

    def start(self):
        """Binds the port (raises OSError if it is already in use) and starts accepting."""
        self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        if not sys.platform.startswith("win"):
            self.sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self.sock.bind(("0.0.0.0", self.port))
        self.sock.listen(2)
        threading.Thread(target=self._accept_loop, daemon=True).start()

    def stop(self):
        if self.sock:
            try:
                self.sock.close()
            except OSError:
                pass

    def _accept_loop(self):
        while True:
            try:
                conn, addr = self.sock.accept()
            except OSError:
                return  # socket closed by stop()
            threading.Thread(target=self._serve_client, args=(conn, addr[0]), daemon=True).start()

    def _serve_client(self, conn, ip):
        self.on_event("connected", ip)
        log = lambda msg: self.on_event("info", msg)
        conn.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
        buffer = b""
        held = set()
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
                            handle(json.loads(line.decode("utf-8")), held, log)
                        except (ValueError, KeyError, TypeError) as e:
                            log(f"Ignored bad command: {e}")
        except OSError:
            pass
        finally:
            for button in held:  # never leave a button stuck down if the phone drops mid-drag
                pyautogui.mouseUp(button=button)
            self.on_event("disconnected", ip)


# ----------------------------------------------------------------------------- window

class App:
    BG = "#101114"
    SURFACE = "#1A1C21"
    RAISED = "#24272E"
    TEXT = "#EDEFF3"
    MUTED = "#9AA0AB"
    ACCENT = "#4F8EF7"
    OK = "#3DD68C"
    WAIT = "#F5B841"
    DANGER = "#E5534B"

    def __init__(self, port):
        import tkinter as tk
        from tkinter import messagebox
        self.tk = tk

        self.root = tk.Tk()
        self.root.title(f"{APP_NAME} Server")
        self.root.configure(bg=self.BG)
        self.root.resizable(False, False)
        self.scale = max(1.0, self.root.winfo_fpixels("1i") / 96.0)
        try:
            self.icon = tk.PhotoImage(file=resource_path("assets/icon.png"))
            self.root.iconphoto(True, self.icon)
        except tk.TclError:
            pass

        if STARTUP_ERROR is not None:
            self.root.withdraw()
            messagebox.showerror(APP_NAME, startup_error_text())
            self.root.destroy()
            sys.exit(1)

        self.events = queue.Queue()
        self.clients = set()
        self.port = port
        self.ip = local_ip()
        self.server = RemoteServer(port, lambda kind, text: self.events.put((kind, text)))

        self._build()

        try:
            self.server.start()
        except OSError as e:
            self.root.withdraw()
            messagebox.showerror(
                APP_NAME,
                f"Could not start on port {port}.\n\n"
                f"Is PC Remote already running?\n\n({e})",
            )
            self.root.destroy()
            sys.exit(1)

        self.root.protocol("WM_DELETE_WINDOW", self.quit)
        self.root.after(100, self._poll)
        dark_title_bar(self.root)
        self._center()

    # ---- layout

    def px(self, value):
        return int(value * self.scale)

    def _label(self, parent, text, size=10, color=None, bold=False, bg=None):
        return self.tk.Label(
            parent, text=text, fg=color or self.TEXT, bg=bg or parent["bg"],
            font=("Segoe UI" if sys.platform.startswith("win") else "Sans", size, "bold" if bold else "normal"),
        )

    def _button(self, parent, text, command, bg, fg=None):
        return self.tk.Button(
            parent, text=text, command=command, bg=bg, fg=fg or self.TEXT,
            activebackground=bg, activeforeground=fg or self.TEXT,
            relief="flat", bd=0, highlightthickness=0, cursor="hand2",
            padx=self.px(14), pady=self.px(8),
            font=("Segoe UI" if sys.platform.startswith("win") else "Sans", 10, "bold"),
        )

    def _build(self):
        tk, px = self.tk, self.px
        outer = tk.Frame(self.root, bg=self.BG, padx=px(22), pady=px(18))
        outer.pack(fill="both", expand=True)

        # header + status
        header = tk.Frame(outer, bg=self.BG)
        header.pack(fill="x")
        self._label(header, APP_NAME, 18, bold=True).pack(side="left")
        status = tk.Frame(header, bg=self.SURFACE, padx=px(10), pady=px(4))
        status.pack(side="right")
        self.dot = tk.Canvas(status, width=px(10), height=px(10), bg=self.SURFACE, highlightthickness=0)
        self.dot_id = self.dot.create_oval(0, 0, px(10), px(10), fill=self.WAIT, outline="")
        self.dot.pack(side="left", padx=(0, px(6)))
        self.status_label = self._label(status, "Waiting for phone…", 9)
        self.status_label.pack(side="left")

        # IP card
        card = tk.Frame(outer, bg=self.SURFACE, padx=px(20), pady=px(16))
        card.pack(fill="x", pady=(px(16), 0))
        self._label(card, "Enter this IP in the PC Remote app", 10, self.MUTED).pack(anchor="w")
        row = tk.Frame(card, bg=self.SURFACE)
        row.pack(fill="x", pady=(px(6), 0))
        self._label(row, self.ip, 26, self.ACCENT, bold=True).pack(side="left")
        self.copy_btn = self._button(row, "Copy", self.copy_ip, self.RAISED)
        self.copy_btn.pack(side="right")
        self._label(card, f"Port {self.port}", 9, self.MUTED).pack(anchor="w", pady=(px(2), 0))

        # hint + last message
        self._label(outer, "Phone and PC must be on the same Wi-Fi or hotspot.", 9, self.MUTED).pack(
            anchor="w", pady=(px(12), 0))
        self.info_label = self._label(outer, "", 9, self.MUTED)
        self.info_label.pack(anchor="w")

        # buttons
        buttons = tk.Frame(outer, bg=self.BG)
        buttons.pack(fill="x", pady=(px(14), 0))
        self._button(buttons, "Stop & Exit", self.quit, self.DANGER, "#FFFFFF").pack(side="right")

    def _center(self):
        self.root.update_idletasks()
        w, h = self.root.winfo_width(), self.root.winfo_height()
        x = (self.root.winfo_screenwidth() - w) // 2
        y = (self.root.winfo_screenheight() - h) // 3
        self.root.geometry(f"+{x}+{y}")

    # ---- behaviour

    def _poll(self):
        try:
            while True:
                kind, text = self.events.get_nowait()
                if kind == "connected":
                    self.clients.add(text)
                elif kind == "disconnected":
                    self.clients.discard(text)
                elif kind == "info":
                    self.info_label.config(text=text)
                self._render_status()
        except queue.Empty:
            pass
        self.root.after(100, self._poll)

    def _render_status(self):
        if self.clients:
            text, color = f"Connected: {', '.join(sorted(self.clients))}", self.OK
        else:
            text, color = "Waiting for phone…", self.WAIT
        self.status_label.config(text=text)
        self.dot.itemconfig(self.dot_id, fill=color)

    def copy_ip(self):
        self.root.clipboard_clear()
        self.root.clipboard_append(self.ip)
        self.copy_btn.config(text="Copied")
        self.root.after(1500, lambda: self.copy_btn.config(text="Copy"))

    def quit(self):
        self.server.stop()
        self.root.destroy()

    def run(self):
        self.root.mainloop()


# ----------------------------------------------------------------------------- entry

def run_console(port):
    if STARTUP_ERROR is not None:
        print(startup_error_text())
        sys.exit(1)

    def on_event(kind, text):
        print({"connected": "Connected: ", "disconnected": "Disconnected: "}.get(kind, "  ") + text)

    server = RemoteServer(port, on_event)
    server.start()
    print("=" * 44)
    print(f" {APP_NAME} server is running")
    print(f" Enter this in the app:  {local_ip()}")
    print(f" Port: {port}    Press Ctrl+C to stop.")
    print("=" * 44)
    try:
        threading.Event().wait()
    except KeyboardInterrupt:
        print("\nStopped.")
    finally:
        server.stop()


def main():
    args = sys.argv[1:]
    console = "--console" in args
    numbers = [a for a in args if a.isdigit()]
    port = int(numbers[0]) if numbers else DEFAULT_PORT
    if console:
        run_console(port)
    else:
        App(port).run()


if __name__ == "__main__":
    main()
