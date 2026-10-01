package com.hermes.pccontrol.data

import android.content.Context
import android.content.SharedPreferences

data class AppConfig(
    val pcHost: String = "192.168.80.200",
    val pcFallbackHost: String = "100.82.252.86",
    val pcPort: Int = 8765,
    val authToken: String = "3f0f6c5206aafe05231c8c97034cd2cb",
    val pcMac: String = "E8:9C:25:4C:4C:CA",
    val mikrotikHost: String = "192.168.80.1",
    val mikrotikPort: Int = 443,
    val mikrotikUseHttps: Boolean = true,
    val mikrotikUser: String = "wol-bot",
    val mikrotikPass: String = "ChangeMeSecurePassword123!",
    val mikrotikInterface: String = "bridge-LAN"
)

class AppPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("pc_control_prefs", Context.MODE_PRIVATE)

    fun loadConfig(): AppConfig {
        // Upgrade only the shipped router placeholders, preserving custom settings.
        val routerHost = prefs.getString("mikrotik_host", null)
            ?.takeUnless { it == "192.168.88.1" } ?: "192.168.80.1"
        val routerInterface = prefs.getString("mikrotik_interface", null)
            ?.takeUnless { it == "bridge" } ?: "bridge-LAN"
        return AppConfig(
            pcHost = prefs.getString("pc_host", "192.168.80.200") ?: "192.168.80.200",
            pcFallbackHost = prefs.getString("pc_fallback_host", "100.82.252.86") ?: "100.82.252.86",
            pcPort = prefs.getInt("pc_port", 8765),
            authToken = prefs.getString("auth_token", "3f0f6c5206aafe05231c8c97034cd2cb") ?: "3f0f6c5206aafe05231c8c97034cd2cb",
            pcMac = prefs.getString("pc_mac", "E8:9C:25:4C:4C:CA") ?: "E8:9C:25:4C:4C:CA",
            mikrotikHost = routerHost,
            mikrotikPort = prefs.getInt("mikrotik_port", 443),
            mikrotikUseHttps = prefs.getBoolean("mikrotik_https", true),
            mikrotikUser = prefs.getString("mikrotik_user", "wol-bot") ?: "wol-bot",
            mikrotikPass = prefs.getString("mikrotik_pass", "ChangeMeSecurePassword123!") ?: "ChangeMeSecurePassword123!",
            mikrotikInterface = routerInterface
        )
    }

    fun saveConfig(config: AppConfig) {
        prefs.edit()
            .putString("pc_host", config.pcHost.trim())
            .putString("pc_fallback_host", config.pcFallbackHost.trim())
            .putInt("pc_port", config.pcPort)
            .putString("auth_token", config.authToken.trim())
            .putString("pc_mac", config.pcMac.trim().uppercase())
            .putString("mikrotik_host", config.mikrotikHost.trim())
            .putInt("mikrotik_port", config.mikrotikPort)
            .putBoolean("mikrotik_https", config.mikrotikUseHttps)
            .putString("mikrotik_user", config.mikrotikUser.trim())
            .putString("mikrotik_pass", config.mikrotikPass)
            .putString("mikrotik_interface", config.mikrotikInterface.trim())
            .apply()
    }
}
