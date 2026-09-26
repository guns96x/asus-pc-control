@echo off
setlocal
cd /d "%~dp0"

:: Self-elevation to Administrator
net session >nul 2>&1
if %errorlevel% neq 0 (
    echo [!] Requesting administrator permissions...
    powershell -NoProfile -Command "Start-Process cmd -ArgumentList '/c \"\"%~f0\"\"' -Verb RunAs"
    exit /b
)

echo ============================================================
echo  Removing Windows Firewall Block & Opening Port 8765
echo ============================================================

powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "Write-Host '[*] Deleting all blocking rules for asus-pc-agent...'; " ^
    "Get-NetFirewallRule -DisplayName 'asus-pc-agent*' -ErrorAction SilentlyContinue | Remove-NetFirewallRule -ErrorAction SilentlyContinue; " ^
    "Get-NetFirewallRule -DisplayName 'ASUS PC Control*' -ErrorAction SilentlyContinue | Remove-NetFirewallRule -ErrorAction SilentlyContinue; " ^
    "Write-Host '[*] Adding ALLOW rule for TCP port 8765 on all profiles...'; " ^
    "New-NetFirewallRule -DisplayName 'ASUS PC Control 8765' -Direction Inbound -Action Allow -Protocol TCP -LocalPort 8765 -Profile Any; " ^
    "Write-Host '[OK] Current firewall status:'; " ^
    "Get-NetFirewallRule -DisplayName 'ASUS PC Control*' | Select-Object DisplayName, Action, Enabled, Direction | Format-Table"

echo.
echo Restarting asus-pc-agent daemon...
taskkill /F /IM asus-pc-agent.exe >nul 2>&1
start "" /D "%~dp0pc-agent" "%~dp0pc-agent\asus-pc-agent.exe"

echo.
echo ============================================================
echo  SUCCESS! Port 8765 is now unblocked and open.
echo  Now check your phone - it will turn ONLINE!
echo ============================================================
pause
