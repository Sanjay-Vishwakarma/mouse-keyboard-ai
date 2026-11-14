@echo off
REM Builds release\PCRemoteServer.exe  (single file, no Python needed on the target PC)
cd /d "%~dp0"

if not exist .venv (
    python -m venv .venv || goto :error
)
.venv\Scripts\python -m pip install --upgrade pip >nul
.venv\Scripts\python -m pip install pyautogui pyperclip pyinstaller pillow || goto :error

if not exist assets\icon.ico .venv\Scripts\python make_icon.py

.venv\Scripts\pyinstaller --noconfirm --clean --onefile --windowed ^
    --name PCRemoteServer ^
    --icon "%~dp0assets\icon.ico" ^
    --add-data "%~dp0assets\icon.png;assets" ^
    --distpath ..\release --workpath build --specpath build ^
    server.py || goto :error

echo.
echo Done: release\PCRemoteServer.exe
exit /b 0

:error
echo Build failed.
exit /b 1
