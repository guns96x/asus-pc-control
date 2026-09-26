# ============================================================
# MikroTik RouterOS v7 Script: Dedicated Wake-on-LAN User
# ============================================================
# Run this script in RouterOS Terminal to create a secure,
# restricted user for the Android Remote Control app.

/user group
add name=wol-only policy=read,test,rest-api comment="Restricted group for Wake-on-LAN via REST API"

/user
add name=wol-bot group=wol-only password="ChangeMeSecurePassword123!" comment="Android PC Control WOL Service"

# Enable REST API service with SSL (or plain HTTP if internal only)
/ip service
set www-ssl disabled=no port=443
# Optional: if you prefer plain HTTP over Tailscale/LAN:
# set www disabled=no port=80

# Verify test command:
# /tool/wol mac=E8:9C:25:4C:4C:CA interface=bridge
