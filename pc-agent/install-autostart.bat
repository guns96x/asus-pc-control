@echo off
setlocal
cd /d "%~dp0"

echo ============================================================
echo  Installing ASUS PC Remote Agent to Windows Task Scheduler
echo ============================================================

set TASK_NAME=AsusPcControlAgent
set EXE_PATH=%~dp0asus-pc-agent.exe

:: Check admin rights
net session >nul 2>&1
if %errorlevel% neq 0 (
    echo [!] Requesting administrator privileges...
    powershell -Command "Start-Process cmd -ArgumentList '/c \"\"%~f0\"\"' -Verb RunAs"
    exit /b
)

schtasks /create /tn "%TASK_NAME%" /tr "\"%EXE_PATH%\"" /sc onlogon /rl highest /f

if %errorlevel% equ 0 (
    echo [OK] Task "%TASK_NAME%" successfully registered!
    echo [OK] Starting task now...
    schtasks /run /tn "%TASK_NAME%"
    echo [OK] Agent is now running in the background.
) else (
    echo [FAIL] Could not create scheduled task.
)

pause
