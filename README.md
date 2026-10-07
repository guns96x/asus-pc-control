# ASUS PC Remote Control (Android & Tailscale / LAN)

Screen-off no longer invokes Windows Modern Standby: [2026-10-06 fix and live test](docs/screen-off-no-standby-2026-10-06.md). The laptop panel is blanked with minimum brightness; Windows stays active.

Current build: **1.2.0 (code 7)**, package `com.hermes.pccontrol`. The APK keeps the existing signing certificate.

Fresh build, API and emulator verification: [completion report](docs/project-completion-2026-10-02.md).

Download from ASUS while Tailscale is connected: http://100.82.252.86:8765/app.apk

The Windows agent runs from `pc-agent/asus-pc-agent.exe` and loads config/APK beside the executable. Current-user autostart is registered as `AsusPcControlAgent` under `HKCU\Software\Microsoft\Windows\CurrentVersion\Run`.

Router deployment status: Tailscale container is deployed and running on MikroTik hAP ac^2, subnet route `192.168.80.1/32` is approved, and HTTPS REST Wake-on-LAN is verified. Details in [MikroTik deployment doc](docs/mikrotik-tailscale-deployed-2026-10-01.md).

Повна система дистанційного керування ноутбуком **ASUS TUF Gaming F15 (FX507ZC4)** з Android-смартфона через локальну мережу (Wi-Fi) та **Tailscale** з підтримкою **MikroTik RouterOS**.

---

## ⚡ Можливості системи

1. **Режими роботи ASUS (G-Helper інтеграція)**:
   - Перемикання режимів продуктивності: **Тихий (Silent / 2)**, **Баланс (Balanced / 0)**, **Турбо (Turbo / 1)**.
   - Пряма апаратна взаємодія через ASUS ATKACPI (`0x00120075`) та двостороння синхронізація конфігурації G-Helper (`%APPDATA%\GHelper\config.json`).
   - Синхронізація Windows Power Scheme (Power Saver / Balanced / High Performance).

2. **Керування екранами без переривання роботи ПК**:
   - Гасіння екранів через SysCommand (`SC_MONITORPOWER = 2`) та DDC/CI (`VCP 0xD6 = 4`).
   - **Захист від засинання ПК (Keep-Awake Controller)**: Windows Power Request API (`PowerRequestSystemRequired` + `PowerRequestExecutionRequired`) та безперервний `SetThreadExecutionState`. При вимкненому дисплеї процесор, фонові процеси та мережа (Tailscale / Wi-Fi) продовжують працювати на 100%!
   - М'яке пробудження (`SC_MONITORPOWER = -1`, DDC/CI `0xD6 = 1`) з коректним звільненням запитів живлення.

3. **Підсвітка клавіатури ASUS TUF та зовнішніх пристроїв**:
   - Інтеграція з контролером ASUS ACPI WMI (`root\wmi:AsusAtkWmi_WMNB`, Device ID `0x00050021`).
   - Перемикання в один клік (On/Off) та вибір 4 рівнів яскравості: **Off (0), Low (1), Med (2), Max (3)**.
   - Темний режим із вимкненням підсвітки та сигналом ScrollLock для зовнішніх клавіатур.

3. **Глибока гібернація (S4)**:
   - Команда `shutdown /h` (збереження пам'яті в `hiberfil.sys`; фактичне споживання залежить від апаратної конфігурації).
   - Збереження стану мережевої карти Realtek GbE для прийому Magic Packet.
   - Захист від випадкового натискання (діалогове підтвердження в додатку).

4. **Wake-on-LAN (Вигнати з гібернації)**:
   - **Wi-Fi Broadcast (LAN):** прямий UDP Magic Packet на порт 9/7 (`255.255.255.255` та subnet broadcast) на MAC-адресу `E8:9C:25:4C:4C:CA`.
   - **MikroTik RouterOS REST API (Tailscale / Internet):** телефон надсилає захищений HTTPS REST-запит до роутера MikroTik (`POST /rest/tool/wol`), і роутер апаратно генерує L2 Ethernet Magic Packet прямо в локальний порт/міст ПК!

5. **Телеметрія в реальному часі**:
   - Статус: Online / Offline. Offline означає відсутність відповіді агента; це може бути гібернація або проблема з мережею.
   - Джерело живлення: Мережа (AC) / Батарея.
   - Відсоток заряду акумулятора та статус заряджання.
   - Стан екранів та рівень підсвітки.

---

## 📁 Структура проєкту

- **`pc-agent/`**:
  - `asus-pc-agent.exe`: Go-агент, Win32 API interop, DDC/CI dxva2.dll, WMI, REST API з Bearer Token.
  - `config.json`: порт (8765), токен безпеки, прапор DDC/CI.
  - `start.bat`: прямий запуск.
  - `install-autostart.bat` / `install-autostart.ps1`: автозапуск для поточного користувача Windows без прав адміністратора, у фізичній сесії ноутбука.
- **`android-app/`**:
  - Повноцінний вихідний код Android-додатку на **Kotlin + Jetpack Compose + Material 3** у стилі Dark Neobank / Cyberpunk.
- **`AsusControl.apk`**:
  - Готовий скомпільований APK для встановлення на телефон (Samsung Galaxy S24 FE тощо).
- **`mikrotik/`**:
  - `setup_wol_rest.rsc`: скрипт RouterOS для створення ізольованого користувача `wol-bot` з мінімальними правами.
  - `tailscale_on_mikrotik_guide.md`: посібник із запуску Tailscale у контейнері RouterOS v7.

---

## 🚀 Швидкий старт

### Крок 1. Запуск агента на ПК
1. Перейдіть до папки `D:\asus-pc-control\pc-agent`.
2. Запустіть `start.bat` або `install-autostart.bat` для фонового запуску та автозапуску при наступному вході поточного користувача.
3. Агент запуститься на порту `8765` і згенерує Bearer Token (наприклад, `3f0f6c5206aafe05231c8c97034cd2cb`).

### Крок 2. Встановлення додатку на Android
* **Найпростіший спосіб:** якщо телефон у Tailscale, відкрийте в браузері телефону:
  ```text
  http://100.82.252.86:8765/app.apk
  ```
  і встановіть завантажений APK.
* Або скопіюйте файл `D:\asus-pc-control\AsusControl.apk` на телефон і відкрийте його.

### Крок 3. Налаштування додатку
При першому відкритті натисніть іконку ⚙️ (Налаштування):
- **IP адреса ПК:** `100.82.252.86` (Tailscale) або ваша домашня IP (`192.168.x.x`).
- **Порт:** `8765`.
- **Auth Token:** токен з файлу `config.json` на ПК.
- **MAC адреса:** `E8:9C:25:4C:4C:CA` (вже введена за замовчуванням).
- **MikroTik:** IP роутера, порт (443), користувач `wol-bot` та пароль.

---

## 🌐 Налаштування MikroTik для Wake-on-LAN

У терміналі MikroTik виконайте:
```routeros
/user group add name=wol-only policy=read,test,rest-api comment="WOL REST API"
/user add name=wol-bot group=wol-only password="ChangeMeSecurePassword123!"
/ip service set www-ssl disabled=no port=443
```
Детальніше про контейнер Tailscale на MikroTik читайте у `mikrotik/tailscale_on_mikrotik_guide.md`.
