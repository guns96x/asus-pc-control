# MikroTik Tailscale Deployed & REST API Configured — 2026-10-01

## Live Status

- **Router**: MikroTik hAP ac^2 (ARM32, RouterOS 7.24.5 stable).
- **Container**: `tailscale-mikrotik:latest` (Fluent Networks) is **RUNNING** on `/usb1-part1/containers/tailscale`.
- **CPU & Memory**: CPU load ~2-5%, free RAM ~17 MiB + 256 MiB active swap on USB ext4. Internal flash space safely preserved.
- **REST API**:
  - `www-ssl` active on port 443 with self-signed certificate `webfig-ssl` (signed by `my-ca`).
  - User `wol-bot` created in group `wol-only` (policies: `read,write,test,rest-api,web,api`).
  - Verified live: POST `https://192.168.80.1/rest/tool/wol` returns `HTTP 200` and dispatches WoL packet to ASUS Ethernet (`E8:9C:25:4C:4C:CA` on `bridge-LAN`).
- **Container Networking**:
  - Interface `veth-tailscale` (`172.17.0.2/24`), gateway `172.17.0.1`.
  - Bridge `dockers` (`172.17.0.1/24`) added to interface list `LAN`. Outbound internet via `wlan2` masquerade works. Guest network `192.168.90.0/24` isolation preserved.
  - Route: `100.64.0.0/10` via `172.17.0.2`.
- **Tailscale Authentication**:
  - Authorization URL: `https://login.tailscale.com/a/cc59a2001a4b0`
  - Advertised subnet route: `192.168.80.1/32` (requires approval in Tailscale Admin Console).
