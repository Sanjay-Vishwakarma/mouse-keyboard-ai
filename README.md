# Phone Mouse & Keyboard

Use your Android phone as a wireless touchpad and keyboard for a Windows or Linux PC.

```
mouse-keyboard-ai/
├── pc-server/        Python server (runs on the PC)
│   ├── server.py
│   └── requirements.txt
└── android-app/      Android app (Kotlin) — open this folder in Android Studio
```

**How to use the app**
- **Touchpad:** drag one finger to move the cursor. Tap = left click, two-finger tap = right click.
- **Left / Right** buttons: mouse clicks.
- **"Tap here to type on PC"**: opens your phone's normal keyboard (Gboard etc.). Whatever you type shows up on the PC. Backspace works. The keyboard's Enter/Send key presses Enter on the PC.

The phone and PC must be on the **same Wi‑Fi**, or the PC must be connected to the **phone's hotspot**.

---

## 1. PC setup

### Windows
1. Install Python 3 from https://python.org (tick **"Add Python to PATH"**).
2. Open a terminal in the `pc-server` folder and run:
   ```
   pip install -r requirements.txt
   python server.py
   ```
3. When Windows Firewall asks, click **Allow** (Private networks).
4. The server prints something like `Enter this in the app: 192.168.1.5:5050`.

### Linux (Ubuntu/Debian)
1. Install the packages and run the server:
   ```bash
   sudo apt install python3-pip python3-tk python3-dev xclip
   cd pc-server
   pip3 install -r requirements.txt      # add --break-system-packages on newer distros, or use a venv
   python3 server.py
   ```
2. If you use a firewall: `sudo ufw allow 5050/tcp`
3. **PyAutoGUI needs an X11 session.** On Ubuntu with Wayland, log out, click the gear icon on the login screen and pick **"Ubuntu on Xorg"**.

To use a different port: `python server.py 6000`, then enter `IP:6000` in the app.

---

## 2. Android setup

### Option A: Android Studio (recommended)
1. Install Android Studio: https://developer.android.com/studio
2. **File → Open** → select the `android-app` folder. Wait for Gradle sync to finish.
3. On the phone, turn on **Developer options → USB debugging** (Settings → About phone → tap *Build number* 7 times).
4. Connect the phone with USB and press **Run ▶**.

### Option B: Build an APK and install it
1. In Android Studio: **Build → Build App Bundle(s) / APK(s) → Build APK(s)**
   (or from a terminal in `android-app`: `gradlew assembleDebug`).
2. The APK is created at `android-app/app/build/outputs/apk/debug/app-debug.apk`.
3. Copy it to the phone, open it, and allow "Install unknown apps" when asked.

---

## 3. Connect
1. Start `server.py` on the PC.
2. Open **Phone Mouse** on the phone, type the IP the server printed (e.g. `192.168.1.5`), and tap **Connect**.
3. The status turns green: "Connected". You're done.

---

## Troubleshooting
| Problem | Fix |
|---|---|
| "Could not connect" | Check that both devices are on the same network and the IP is correct. Allow Python through the firewall (Windows: set the Wi‑Fi network to *Private*, or run `netsh advfirewall firewall add rule name="Phone Mouse" dir=in action=allow protocol=TCP localport=5050` as admin). |
| Cursor too slow or fast | Change `sensitivity` in `android-app/.../TouchpadView.kt` (default `1.6`). |
| Typing doesn't reach some apps on Windows | Apps running as Administrator won't accept input from a normal process. Run the server as Administrator too. |
| Hindi or emoji not typed | PyAutoGUI only types English (ASCII) characters. Other text is pasted via the clipboard, which needs `pyperclip` (it's in requirements.txt; on Linux it also needs `xclip`). |
| Nothing happens on Linux | Make sure you're on an X11 (Xorg) session, not Wayland. |

The connection is plain TCP with no password, so use it only on your own trusted network.
