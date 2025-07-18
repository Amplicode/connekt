@echo off
setlocal EnableDelayedExpansion

REM === Configuration ===
set INSTALL_DIR=%USERPROFILE%\.connekt
set BIN_NAME=connekt.bat
set BIN_URL=https://raw.githubusercontent.com/Amplicode/connekt/main/install/connekt.bat
set BIN_PATH=%INSTALL_DIR%\%BIN_NAME%

REM === Check if curl exists ===
where curl >nul 2>&1
if errorlevel 1 (
echo ❌ curl is not available. Please install curl or use Git Bash.
exit /b 1
)

REM === Create install directory ===
if not exist "%INSTALL_DIR%" (
mkdir "%INSTALL_DIR%"
)

REM === Download connekt.bat ===
echo 📥 Downloading connekt to %BIN_PATH% ...
curl -fsSL "%BIN_URL%" -o "%BIN_PATH%"
if errorlevel 1 (
echo ❌ Failed to download connekt script.
exit /b 1
)

REM === Add to user PATH if needed ===
echo %PATH% | find /I "%INSTALL_DIR%" >nul
if errorlevel 1 (
echo ➕ Adding %INSTALL_DIR% to user PATH ...
setx PATH "%PATH%;%INSTALL_DIR%" >nul
)

echo.
echo ✅ connekt installed to %BIN_PATH%
echo 🔁 Please restart your terminal or run refreshenv (if using Chocolatey)
echo 🧪 Then run: connekt -env my.env.json script.kts

:end
