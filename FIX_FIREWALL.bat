@echo off
setlocal
cd /d "%~dp0"

:: Request Administrator Privileges
net session >nul 2>&1
if %errorlevel% neq 0 (
    echo [!] Requesting administrator privileges...
    powershell -NoProfile -Command "Start-Process cmd -ArgumentList '/c \"\"%~f0\"\"' -Verb RunAs"
    exit /b
)

echo ============================================================
echo  Unblocking ASUS PC Control in Windows Firewall
echo ============================================================

:: Delete existing rules (especially the blocking one created by Windows)
netsh advfirewall firewall delete rule name="asus-pc-agent" >nul 2>&1
netsh advfirewall firewall delete rule name="ASUS PC Control (8765)" >nul 2>&1

:: Add explicit ALLOW rules for all profiles (Private, Public, Domain)
netsh advfirewall firewall add rule name="asus-pc-agent" dir=in action=allow program="%~dp0pc-agent\asus-pc-agent.exe" enable=yes profile=any
netsh advfirewall firewall add rule name="ASUS PC Control (8765)" dir=in action=allow protocol=TCP localport=8765 enable=yes profile=any

echo [OK] Windows Firewall successfully configured!
echo.
echo Restarting ASUS PC Control Agent...
taskkill /F /IM asus-pc-agent.exe >nul 2>&1
start "" /D "%~dp0pc-agent" "%~dp0pc-agent\asus-pc-agent.exe"

echo [OK] Agent restarted and listening on port 8765.
echo Check the app on your phone now!
pause
