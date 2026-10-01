     -LAN/ether1.
- ASUS: 192.168.80.200, Tailscale 100.82.252.86, Ethernet MAC E8:9C:25:4C:4C:CA.
- WinBox has a saved workspace for router MAC 48:8F:5A:2C:46:A4.
- WebFig login: http://192.168.80.1/. SSH TCP opens but closes before key exchange.
- Router configuration has NOT been changed in this run. Administrator authentication is pending.

## Перед встановленням

Run `preflight_readonly.rsc` through the authenticated router connection. Check installed container package, device-mode, free RAM, attached disks, actual bridge membership and service restrictions. Back up the current configuration before changing it; keep backups private.

This model has 16 MB flash and 128 MB RAM. Container files and persistent Tailscale state must live on an existing external USB disk. Do not format an attached disk without checking its data. Do not extract the image into internal flash. ARM support alone does not prove there is enough free memory for this installation.

The container package must match the installed RouterOS version. Enabling container device-mode requires physical confirmation using the router button or cold power cycle. Plan that activation with the user; do not reboot during the current ASUS connection.

## Intended deployment

Phone → Tailscale container on MikroTik → router REST `/rest/tool/wol` → ASUS Ethernet.

Use the official `tailscale/tailscale` ARM image, pinned to an inspected version/digest. Prepare an ARM image archive on the PC if memory limits make a registry pull unsuitable. Confirm available disk space and peak RAM before extraction.

The container should use a dedicated veth/network selected after inspecting existing routes. Add only the required egress and router REST permissions; preserve existing firewall, NAT and management restrictions. No public WAN port forwarding is needed.

Use these container environment settings:

- `TS_USERSPACE=true` — userspace subnet routing is sufficient for the REST TCP request; no TUN assumption.
- `TS_ROUTES=192.168.80.1/32` — access only the router for this wake-up feature.
- `TS_ACCEPT_DNS=false`.
- `TS_AUTH_ONCE=true`.
- `TS_HOSTNAME=mikrotik-asus-wol`.
- `TS_STATE_DIR=/var/lib/tailscale`, mounted on the USB disk and preserved on restart.

Authenticate through the user's tailnet without putting auth keys into this repository or logs. Approve the advertised route in Tailscale and restrict phone access to the router REST port. Enable start-on-boot after a successful first run. Userspace subnet routing supports TCP/UDP; use a real REST request as the check rather than ICMP alone.

## App and acceptance checks

ASUS Control 1.1.2 defaults to router 192.168.80.1 and interface bridge-LAN. The old shipped 192.168.88.1/bridge placeholders are migrated; custom settings are preserved. Configure the actual REST credentials and the verified HTTPS service/certificate. The current app retains legacy self-signed certificate compatibility; certificate validation/pinning is still a follow-up, not a completed security guarantee.

1. Confirm router resources and USB storage after login.
2. Verify `/rest/tool/wol` succeeds locally for the actual Ethernet LAN interface.
3. With phone Wi-Fi disabled and Tailscale enabled, verify the router REST route.
4. Coordinate an ASUS hibernation/wake test with the user after deployment. Do not hibernate the computer while it is running this work.
5. Confirm the same Tailscale node and REST route survive a planned router restart.

## References

- https://mikrotik.com/product/hap_ac2
- https://help.mikrotik.com/docs/spaces/ROS/pages/84901929/Container
- https://tailscale.com/docs/features/containers/docker/docker-params
- https://tailscale.com/docs/reference/kernel-vs-userspace-routers