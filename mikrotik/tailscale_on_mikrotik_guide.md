# Підключення MikroTik до Tailscale & Wake-on-LAN для ASUS TUF

Цей посібник описує налаштування віддаленого пробудження вашого ПК (**MAC: `E8:9C:25:4C:4C:CA`**) з Android через Tailscale за допомогою MikroTik RouterOS v7.

---

## Варіант 1: Tailscale в контейнері RouterOS v7 (Рекомендовано для пристроїв з ARM / ARM64 / x86)

Якщо ваш роутер MikroTik (наприклад, hAP ax2, hAP ax3, RB5009 або x86/CHR) підтримує пакет `container`:

### 1. Увімкнення режиму контейнерів
У терміналі RouterOS:
```routeros
/system/device-mode/update container=yes
```
*(Роутер вимагатиме апаратного перезавантаження кнопкою або відключенням живлення для безпеки).*

### 2. Створення віртуального інтерфейсу та мережі
```routeros
/interface/veth/add name=veth-tailscale address=172.17.0.2/24 gateway=172.17.0.1
/interface/bridge/add name=dockers
/ip/address/add address=172.17.0.1/24 interface=dockers
/interface/bridge/port add bridge=dockers interface=veth-tailscale
/ip/firewall/nat add chain=srcnat action=masquerade src-address=172.17.0.0/24
```

### 3. Налаштування реєстру та контейнера
```routeros
/container/config/set registry-url=https://registry-1.docker.io tmpdir=disk1/pull
/container/envs/add name=tailscale_envs key=TS_AUTHKEY value="tskey-auth-xxxxxx"
/container/envs/add name=tailscale_envs key=TS_ROUTES value="192.168.88.0/24"
/container/envs/add name=tailscale_envs key=TS_STATE_DIR value="/var/lib/tailscale"

/container/add remote-image="tailscale/tailscale:latest" interface=veth-tailscale root-dir=disk1/tailscale envlist=tailscale_envs logging=yes
/container/start 0
```
Тепер MikroTik буде у вашому Tailnet і зможе передавати запити REST API прямо з телефону, навіть коли ви поза домом!

---

## Варіант 2: RouterOS REST API (Найпростіший спосіб)

Якщо MikroTik знаходиться в тій же домашній локальній мережі, а телефон під'єднаний через Tailscale Subnet Router (або домашній Wi-Fi):

1. Відкрийте термінал RouterOS і виконайте команди з `setup_wol_rest.rsc`:
```routeros
/user group add name=wol-only policy=read,test,rest-api
/user add name=wol-bot group=wol-only password="YourStrongPassword"
/ip service set www-ssl disabled=no port=443
```
2. Android-додаток відправляє POST-запит:
```http
POST https://192.168.88.1/rest/tool/wol
Authorization: Basic <base64(wol-bot:YourStrongPassword)>
Content-Type: application/json

{
  "mac": "E8:9C:25:4C:4C:CA",
  "interface": "bridge"
}
```
MikroTik надсилає апаратний Magic Packet рівня L2 Ethernet у свій комутатор/міст, і мережева карта Realtek ПК миттєво прокидається з гібернації!
