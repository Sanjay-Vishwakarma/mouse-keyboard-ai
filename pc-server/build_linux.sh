#!/usr/bin/env bash
# Builds release/PCRemoteServer-linux (single file, no Python needed on the target PC).
# Run this ON Linux. Needs: python3, python3-venv, python3-tk
#   sudo apt install python3-venv python3-tk
set -euo pipefail
cd "$(dirname "$0")"

VENV="${VENV:-.venv-linux}"
[ -d "$VENV" ] || python3 -m venv "$VENV"
"$VENV/bin/python" -m pip install --upgrade pip >/dev/null
"$VENV/bin/python" -m pip install pyautogui pyperclip pyinstaller pillow

[ -f assets/icon.png ] || "$VENV/bin/python" make_icon.py

# --specpath is a separate folder, so data paths must be absolute
"$VENV/bin/pyinstaller" --noconfirm --clean --onefile --windowed \
    --name PCRemoteServer-linux \
    --add-data "$PWD/assets/icon.png:assets" \
    --distpath ../release --workpath build-linux --specpath build-linux \
    server.py

chmod +x ../release/PCRemoteServer-linux
echo
echo "Done: release/PCRemoteServer-linux"
