# MikroTik Tailscale preflight — 2026-10-01

Current status is in [mikrotik-container-7.24.5-ready-2026-10-01.md](mikrotik-container-7.24.5-ready-2026-10-01.md). The 7.22.1 package details below are historical.

## Confirmed live

- Router: hAP ac², ARM32, RouterOS 7.22.1, identity `S24 FE`.
- SSH server restricts access to `192.168.80.200/32` only. The former VPS address `10.77.0.1/32` was removed by the user and the resulting setting was read back over SSH.
- Dedicated ASUS Ed25519 public key installed for the existing `admin` account with comment `asus-control-mikrotik`. A second, noninteractive connection using this key succeeded. Existing account/password settings were not changed.
- Local SSH alias: `mikrotik-asus`. Key material stays in the user's `.ssh` directory and is not stored in this project.
- RAM: 128 MiB total, approximately 39 MiB free before USB transfer; approximately 33 MiB during SFTP backup.
- Internal flash: 16 MiB total, approximately 884–888 KiB free.
- Installed packages: `routeros` and `wireless`, both 7.22.1. Container package absent.
- Device mode: advanced, not flagged, `container=no`.
- LAN router address `192.168.80.1/24`, interface `bridge-LAN`. Separate guest LAN `192.168.90.1/24` exists and must retain its isolation.
- `www` enabled on TCP/80; `www-ssl` enabled on TCP/443 with no certificate configured. A valid HTTPS service remains necessary for the app's intended HTTPS REST connection.

## USB and preservation

USB hardware `usb1`, mounted partition `usb1-part1`, FAT32:

- Partition size: 31,040,978,432 bytes.
- Filesystem free space: 23,776,198,656 bytes.
- Source inventory: 42 files, 49 directories, 7,254,950,414 file bytes. Occupied filesystem space also includes allocation overhead.
- Private backup directory on ASUS: `D:\asus-router-private\20261001-151625`.
- Router export without sensitive values and the preflight inventory are saved there. An initial export command with an unsupported argument was replaced with `/export`; the captured export was checked for syntax errors and has a RouterOS header.
- Recursive SFTP copy was stopped after the user explicitly said the USB files were unnecessary and requested formatting. The partial PC copy is not a verified full backup; it remains private and was not deleted.
- User selected permanent MikroTik use. The existing `usb1-part1` partition was formatted as ext4 with label `tailscale`, then verified mounted with formatting finished. Original USB data was erased as authorized. The other router settings were preserved.
- A 256 MiB swap file `usb1-part1/tailscale.swap` was created. The resulting disk entry `file-usb1-part1-tailscale-swap` reports `swap-enabled=true`.
- Container device-mode activation and router restart are still pending physical coordination.

## Prepared package

Downloaded from the official MikroTik archive:

`https://download.mikrotik.com/routeros/7.22.1/all_packages-arm-7.22.1.zip`

- Exact package: `container-7.22.1-arm.npk`.
- Archive entry size: **188,561 bytes (184.1 KiB)**. It fits the measured free internal space; an estimate based on another RouterOS release is not applicable.
- SHA256: `76193d06edd6087584c8e373b5df04eb6e6e74d96c7428096bf8f50f946bd987`.
- Package uploaded to the router root for installation on the next coordinated reboot; not installed yet.

## Prepared Tailscale image

- Official `tailscale/tailscale` Linux ARM/v7 image pinned to `sha256:45c71532645408f01315be875bb2f48d9e52dc71dc5d1e8f82e55e3a4407ec4a`.
- Downloaded on the ASUS with official Google `crane` v0.22.1. The Windows release archive SHA256 was checked against the GitHub release digest before execution.
- Docker-save archive: `tailscale-armv7.tar`, 54,872,576 bytes.
- Archive SHA256: `ff7e857388748873bf348f399cfdcb36d6a63c24989325e40646fa213d8a4e99`.
- Config architecture/OS and all five layer `rootfs.diff_ids` were verified locally without executing the ARM image. Config command is `/usr/local/bin/containerboot`.
- Archive uploaded to `usb1-part1/tailscale-armv7.tar`. Router file size matches the local archive. Container extraction and execution have not occurred yet.

## Remaining gates

1. Coordinate container package installation/reboot and physical confirmation for `device-mode`. Do not initiate the physical-confirmation countdown before the user is ready.
2. Import the verified local ARM archive onto ext4 and mount persistent Tailscale state there.
3. With only about 39 MiB RAM free, configure and verify appropriate container memory limits before a supervised run. USB swap is enabled, but no Tailscale runtime memory measurements exist yet.
4. Advertise only `192.168.80.1/32`, preserve the guest network, configure the HTTPS REST service, and verify authenticated Wake-on-LAN from the phone outside home Wi-Fi.
5. Reboot persistence and actual ASUS wake-up remain unverified.

## Alternatives when only remote WinBox access is available

The user subsequently explicitly required **Tailscale** and rejected switching to Back to Home or ZeroTier. The alternatives below are research history, not the selected deployment.

Read-only SSH verification on 2026-10-01 confirms `zerotier=true`, `container=false`, `back-to-home-vpn=revoked-and-disabled`, RouterOS 7.22.1, and 921,600 bytes of free internal storage.

- **Back to Home (rejected by user):** built into the `routeros` package on ARM hardware. MikroTik documents manual activation through `/ip/cloud/set ddns-enabled=yes` and `/ip/cloud/set back-to-home-vpn=enabled`. This avoids container activation, USB requirements, and extra-package installation. It can work behind NAT through MikroTik's encrypted relay service; relay performance can be lower than a direct connection. No configuration has been changed or phone connection tested for this alternative.
- **Native ZeroTier:** ARM extra package; its device-mode permission is already enabled on this router. Package installation requires a software reboot and enough installation space, but does not require changing device-mode. LAN access must use explicit firewall rules and routes preserving guest isolation. Installation and runtime resource use have not been verified.
- On Android, Back to Home/WireGuard or ZeroTier would replace the active Tailscale VPN in the same profile. ASUS Control would need to access the PC over its LAN address through the chosen home VPN. One active VPN per profile is an Android platform restriction.
- Before any alternative package-installation reboot, account for the task-generated `container-7.22.1-arm.npk` already staged in the router root. Do not inadvertently install it when selecting a different approach.

Alternative references:

- https://help.mikrotik.com/docs/spaces/ROS/pages/197984280/Back%20To%20Home
- https://help.mikrotik.com/docs/spaces/ROS/pages/83755083/ZeroTier
- https://developer.android.com/develop/connectivity/vpn

## Container preparation references

- https://manual.mikrotik.com/docs/management-tools/ssh/
- https://manual.mikrotik.com/docs/authentication-authorization-accounting/user/
- https://help.mikrotik.com/docs/spaces/ROS/pages/84901929/Container
- https://help.mikrotik.com/docs/spaces/ROS/pages/91193346/Disks
- https://tailscale.com/docs/features/containers/docker/docker-params

## User-supplied Fluent Networks Tailscale project

Reviewed https://github.com/Fluent-networks/tailscale-mikrotik at commit `00b287d9ee1ec7b3cbf39e555d1ff29a5ece0bdd`, including README, Dockerfile, build.sh, and tailscale.sh.

- This is genuine Tailscale in a RouterOS container, not a native NPK or a different VPN protocol. README configuration step 1 explicitly requires `/system/device-mode/update container=yes`.
- Queried the public registry manifest of `ghcr.io/fluent-networks/tailscale-mikrotik:latest` with crane. An ARM/v7 image exists with digest `sha256:51d0c327f5100232fd5eaec0f9a35d2169651ff912604f671e1b2011ebc46a87`, alongside ARM64 and AMD64 variants. ARM/v7 matches this router architecture; runtime memory/performance acceptance remains untested.
- The entry command is `/usr/local/bin/tailscale.sh`; README explicitly requires `cmd` for RouterOS 7.22+. Its image includes SSH and routing utilities. Do not copy example root-password/auth-key values or enable the example exit node unnecessarily.
- Reverified live `container=false` on the hAP ac². This project does not remove the hardware-confirmation requirement. Official device-mode documentation allows a button press or power removal/restoration. A remotely controlled power source could provide the latter if it exists; its presence and control have not been confirmed. A WinBox software reboot is not a substitute.
- Tailscale also officially supports Android/Android TV subnet routing. Live router leases show a Tab S9 FE on the LAN; no software was installed or changed on that tablet. Whether an independent LAN device can remain available while ASUS sleeps is a user/environment dependency.

References:

- https://github.com/Fluent-networks/tailscale-mikrotik
- https://manual.mikrotik.com/docs/system-information-and-utilities/device-mode/
- https://tailscale.com/docs/features/subnet-routers/how-to/setup?tab=android
