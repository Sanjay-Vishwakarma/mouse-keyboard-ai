# PC Remote

Use your Android phone as a wireless touchpad and keyboard for a Windows or Linux PC.

## Quick start (no Python, no Android Studio needed)

Ready-made files are in the [`release/`](release/) folder:

| File | For | How to run |
|---|---|---|
| `PC-Remote.apk` | Android phone | Copy to the phone, open it, allow "Install unknown apps" |
| `PCRemoteServer.exe` | Windows 10/11 | Double-click |
| `PCRemoteServer-linux` | Linux (Ubuntu 20.04+, Debian 11+, Mint, Fedora …) | Double-click, or `chmod +x PCRemoteServer-linux && ./PCRemoteServer-linux` |

1. Start the server on the PC. A small window opens and shows the IP to use, e.g. **192.168.1.5**.
   - **Windows:** the first time, Windows may show *"Windows protected your PC"*: click **More info → Run anyway**. When the Firewall asks, click **Allow** (Private networks).
   - **Linux:** if double-click doesn't start it, right-click → *Properties → Permissions → Allow executing as program*.
2. Open **PC Remote** on the phone and tap **Connect**. Type the IP in the popup and tap **Connect**. The app remembers it for next time.
3. The corner chip turns green, **● Connected**. That's it.

The server window shows **● Connected** as well. Minimize it with the window's normal **–** button while you work, and use **Stop & Exit** (or close the window) to stop it.

The phone and PC must be on the **same Wi‑Fi**, or the PC must be connected to the **phone's hotspot**.

## Using the app (one thumb is enough)

| On the touchpad | Does |
|---|---|
| Drag | Move the cursor |
| Tap | Left click (tap twice = double click) |
| Hold | Right click (phone vibrates) |
| Double-tap, then drag | Select text / drag windows |
| Two-finger drag (anywhere), or drag on the **SCROLL** strip at the right edge | Scroll up/down |

- **Left click / Right click** buttons at the bottom work like mouse buttons.
- **"Tap here to type on PC"** opens your phone's own keyboard (Gboard etc.). What you type appears on the PC. Backspace works. The keyboard's **↵ Enter** key starts a new line in the app and presses Enter on the PC. **✕** clears the app's text box (text already on the PC stays).
- Tap the status chip in the top-right corner to connect or disconnect.

---

## Project layout

```
mouse-keyboard-ai/
├── release/                 ready-to-run APK + Windows/Linux executables
├── pc-server/               Python server (PyAutoGUI + Tkinter window)
│   ├── server.py
│   ├── requirements.txt
│   ├── build_windows.bat    -> release/PCRemoteServer.exe
│   ├── build_linux.sh       -> release/PCRemoteServer-linux   (run on Linux)
│   ├── build_linux_docker.sh  same, but inside Docker (from any OS)
│   └── make_icon.py         generates assets/icon.png + icon.ico
└── android-app/             Android app (Kotlin), open in Android Studio
```

## Running the server from source

```bash
cd pc-server
pip install -r requirements.txt
python server.py              # window
python server.py --console    # terminal only
python server.py 6000         # other port (then enter 192.168.1.5:6000 in the app)
```
Linux needs: `sudo apt install python3-tk python3-dev xclip`.

## Rebuilding

- **Windows exe:** double-click `pc-server/build_windows.bat` (needs Python 3).
- **Linux binary:** on Linux run `pc-server/build_linux.sh` (needs `python3-venv python3-tk`), or from any OS with Docker run `pc-server/build_linux_docker.sh`. The Docker build uses Debian 11, so the binary runs on older distros too.
- **APK:** open `android-app` in Android Studio, then **Build → Build APK(s)**, or run `gradlew assembleDebug` in `android-app`. Output: `android-app/app/build/outputs/apk/debug/app-debug.apk`.

---

## Troubleshooting

| Problem | Fix |
|---|---|
| "Could not connect" | Check both devices are on the same network and the IP is right. Allow the server through the firewall (Windows: set the Wi‑Fi network to *Private*, or run as admin: `netsh advfirewall firewall add rule name="PC Remote" dir=in action=allow protocol=TCP localport=5050`). Linux with ufw: `sudo ufw allow 5050/tcp`. |
| "Could not start on port 5050" | The server is already running (check the taskbar), or another program uses the port. |
| Linux: "cannot control the mouse and keyboard" | You're on Wayland. Log out, click the gear icon on the login screen, choose **"Ubuntu on Xorg"**. |
| Cursor too slow or fast | Change `sensitivity` in `android-app/.../TouchpadView.kt` (default `1.6`). |
| Typing doesn't reach some apps on Windows | Apps running as Administrator don't accept input from normal programs. Right-click the server → *Run as administrator*. |
| Hindi or emoji not typed on Linux | Install `xclip`: `sudo apt install xclip`. |

The connection is plain TCP with no password, so use it only on your own trusted network.
