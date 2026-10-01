# Lighting controls — 2026-09-30 / ASUS Control 1.1.3

## Implemented and verified

- ASUS model: TUF Gaming F15 FX507ZC4_FX507ZC4.
- Replaced the failing PowerShell WMI calls with direct ATKACPI requests. The old code used Device_Arg/Control_Status; the actual WMI schema exposes Device_ID/Control_status/result. It also swallowed failures and fabricated brightness 3.
- TUF brightness writes require the 0x80 flag plus level 0–3. Direct driver reads reported 0x00050003 before testing, 0x00050000 after level 0, then 0x00050003 after restoring level 3. This confirms firmware readback; physical confirmation is separate.
- Failures now produce HTTP errors; unsupported/unknown keyboard state is -1. Invalid or missing levels cannot change hardware. Toggle reads actual state each time.
- Screen controls require the agent to run in the active physical console session. A disconnected RDP session had no physical DDC monitor handles. User subsequently logged in locally; the ASUS VG259QL5A became available.
- Replaced blocking SendMessage with SendMessageTimeout. Added DDC wake commands even when a powered-off known monitor stops answering feature reads.
- A sleep/wake command cycle was accepted. DDC readback is intermittent (invalid command-field replies and changing capability strings), so physical off/on is NOT claimed verified. The user has been asked to confirm both screens.
- Windows display state is a requested state, explicitly marked monitor_state_verified=false. User input can wake screens afterward.
- Android now shows keyboard and monitor errors, exposes a Dark mode button, and displays capabilities. Partial results from HTTP 207 do not claim all indicators were disabled. No automatic replay of timed-out POST actions.
- Ten Go tests and 29 Android tests passed, go vet passed, APK build and matching signing certificate verified. The Go race test could not run because the environment has no configured cgo compiler; ordinary Go tests passed.

## Indicators

- VG259QL5A manual includes System Setup → Power Indicator → OFF. User was asked to set it, with before/after vendor VCP reads intended to identify the command safely. No undocumented vendor VCP was written.
- Notebook ACPI StatusLed (0x000600C2), MicMuteLed (0x00040017) and SoundMuteLed (0x0004001C) reads returned unsupported/Incorrect function. No confirmed programmable control for this model's power/charge/disk indicators has been found. No EC/BIOS register writes or physical modifications were made.
- Dark mode disables supported keyboard lighting and requests screens off, but reports notebook and monitor indicator limitations explicitly.

## Deployment

- APK: ../AsusControl.apk, version 1.1.3 / code 5 / com.hermes.pccontrol.
- OTA: http://100.82.252.86:8765/app.apk over Tailscale.
- Agent binary and APK updated in place, config/token preserved. Prior binary/APK backup: C:\Users\pavlo\AppData\Local\Temp\asus-lighting-backup-20260930-230600.
- Physical settings restored after tests: keyboard level 3; wake requests sent to Windows and external monitor. Android installation is pending because no ADB device is connected.

## Primary references

- https://github.com/seerge/g-helper/blob/main/app/AsusACPI.cs (ATKACPI/TUF protocol reference; own Go implementation)
- ASUS-authored VG259Q5A/VG259QL5A manual: https://www.bhphotovideo.com/lit_files/1238250.pdf (page 22, Power Indicator)
- https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-sendmessagetimeoutw
- https://learn.microsoft.com/en-us/windows/win32/api/physicalmonitorenumerationapi/nf-physicalmonitorenumerationapi-getphysicalmonitorsfromhmonitor