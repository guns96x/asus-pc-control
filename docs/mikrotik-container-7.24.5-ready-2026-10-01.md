# MikroTik container package ready — 2026-10-01

This is the current deployment status. The earlier 7.22.1 preflight records preparation history.

## Verified live state

- hAP ac², ARM32; RouterOS 7.24.5 stable.
- Official `container` 7.24.5 ARM package installed through one software reboot. SSH key access returned; package print reports `version="7.24.5"` without AVAILABLE/DISABLED flags, and `/container/print` is available with no entries.
- Internal free space after installation: 577,536 bytes. Free RAM at final check: 52,162,560 bytes. No existing package removed.
- `device-mode container=false` remains unchanged. Package installation does not authorize execution. Earlier `attempt-count=2`; do not spend another activation attempt before physical confirmation can be provided.
- Dedicated USB still mounted as ext4; existing 256 MiB swap verified enabled after this installation reboot. Original data was already erased at the user's explicit request in the earlier preparation.
- Current configuration exported without sensitive values before reboot to private `D:\asus-router-private\20261001-151625\staging\router-before-container-7.24.5.rsc`.

## Official package

- Source: https://download.mikrotik.com/routeros/7.24.5/all_packages-arm-7.24.5.zip
- Entry: `container-7.24.5-arm.npk`, 295,057 bytes.
- SHA256: `1914600a832bdea3863e6eac696644d9bd0eada722e28879cea8c2a34855eb06`.
- Uploaded to router root for installation and to `usb1-part1/container-7.24.5-arm.npk` for a persistent copy. Root-package SFTP readback hash matched before reboot. Root file was processed by the installer; USB copy remains and reports ARM / 7.24.5 metadata.

## User-selected Fluent Networks image

- Repository: https://github.com/Fluent-networks/tailscale-mikrotik
- Pinned Linux ARM/v7 image: `ghcr.io/fluent-networks/tailscale-mikrotik@sha256:51d0c327f5100232fd5eaec0f9a35d2169651ff912604f671e1b2011ebc46a87`.
- Archive: `usb1-part1/fluent-tailscale-armv7.tar`, 26,258,432 bytes.
- SHA256: `99b6a3b7649892535ce9b258ab590e7892750ab7bc1013e92bc79b830094847c`.
- Config digest verified against pinned registry manifest; all nine uncompressed layer hashes verified against `rootfs.diff_ids`. SFTP archive readback hash matched the source. Archive remains on USB after reboot.
- Entry command: `/usr/local/bin/tailscale.sh`, specified explicitly for RouterOS 7.22+ per the repository guidance.
- Not extracted or executed. No Tailscale authentication, route approval, or outside-home wake-up has occurred.

## Next step

Once a button press or cold power cycle can be supplied within the activation window, enable only the `container` device-mode feature. Do not replace the whole device mode. Then configure isolated networking, persistent state, measured memory/swap limits, the scoped router route `192.168.80.1/32`, and private authentication. Preserve guest isolation and the user's removal of VPS SSH access.

Physical-confirmation reference: https://manual.mikrotik.com/docs/system-information-and-utilities/device-mode/
