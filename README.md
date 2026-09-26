# ASUS PC Remote Control (Android & Tailscale / LAN)

Повна система дистанційного керування ноутбуком **ASUS TUF Gaming F15 (FX507ZC4)** з Android-смартфона через локальну мережу (Wi-Fi) та **Tailscale** з підтримкою **MikroTik RouterOS**.

---

## ⚡ Можливості системи

1. **Керування екранами (Монітор On / Off)**:
   - Переведення моніторів у режим сну через Windows SysCommand (`SC_MONITORPOWER = 2`).
   - Пряма апаратна команда **DDC/CI (`VCP 0xD6 = 4`)** для зовнішнього ігрового монітора **ASUS VG259QL5A**, щоб повністю гасити підсвітку і виключати «сіре світіння».
   - М'яке та миттєве пробудження (`SC_MONITORPOWER = -1`, `ES_DISPLAY_REQUIRED`, DDC/CI `0xD6 = 1`).

2. **Підсвітка клавіатури ASUS TUF**:
   - Інтеграція з контролером ASUS ACPI WMI (`root\wmi:AsusAtkWmi_WMNB`).
   - Перемикання в один клік (On/Off) та вибір 4 рівнів яскравості: **Off (0), Low (1), Med (2), Max (3)**.

3. **Глибока гібернація (S4)**:
   - Команда `shutdown /h` (збереження пам'яті в `hiberfil.sys`, нульове споживання 0W).
   - Збереження стану мережевої карти Realtek GbE для прийому Magic Packet.
   - Захист від випадкового натискання (діалогове підтвердження в додатку).

4. **Wake-on-LAN (Вигнати з гібернації)**:
   - **Wi-Fi Broadcast (LAN):** прямий UDP Magic Packet на порт 9/7 (`255.255.255.255` та subnet broadcast) на MAC-адресу `E8:9C:25:4C:4C:CA`.
   - **MikroTik RouterOS REST API (Tailscale / Internet):** телефон надсилає захищений HTTPS REST-запит до роутера MikroTik (`POST /rest/tool/wol`), і роутер апаратно генерує L2 Ethernet Magic Packet прямо в локальний порт/міст ПК!

5. **Телеметрія в реальному часі**:
   - Статус: Online / Offline (S4 гібернація).
   - Джерело живлення: Мережа (AC) / Батарея.
   - Відсоток заряду акумулятора та статус заряджання.
   - Стан екранів та рівень підсвітки.

---

## 📁 Структура проєкту

- **`pc-agent/`**:
  - `asus-pc-agent.exe`: легковажний Go-демон (< 10 МБ RAM, 0% CPU), Win32 API interop, DDC/CI dxva2.dll, WMI, REST API з Bearer Token.
  - `config.json`: порт (8765), токен безпеки, прапор DDC/CI.
  - `start.bat`: прямий запуск.
  - `install-autostart.bat`: автозапуск демона при вході у Windows через Планувальник завдань із найвищими правами.
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
2. Запустіть `start.bat` (або `install-autostart.bat` від імені адміністратора для постійного фонового автозапуску).
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
