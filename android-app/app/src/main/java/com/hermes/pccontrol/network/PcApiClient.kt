package com.hermes.pccontrol.network

import android.util.Log
import com.hermes.pccontrol.data.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class PcStatus(
    val hostname: String = "Unknown",
    val isAcPlugged: Boolean = true,
    val batteryPercent: Int = 100,
    val isCharging: Boolean = false,
    val monitorSleeping: Boolean = false,
    val monitorStateVerified: Boolean = false,
    val keyboardLevel: Int = -1,
    val timestamp: Long = 0L,
    val connectedHost: String = ""
)

data class LightingCapabilities(
    val keyboardSupported: Boolean = false,
    val laptopIndicatorsSupported: Boolean = false,
    val monitorIndicatorSupported: Boolean = false,
    val message: String = ""
)

class PcApiClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .build()
) {
    var activeHost: String? = null
        private set

    private fun getCandidates(config: AppConfig): List<String> {
        val list = mutableListOf<String>()
        val cached = activeHost
        if (!cached.isNullOrBlank()) list.add(cached)
        if (config.pcHost.isNotBlank() && !list.contains(config.pcHost)) list.add(config.pcHost)
        if (config.pcFallbackHost.isNotBlank() && !list.contains(config.pcFallbackHost)) list.add(config.pcFallbackHost)
        return list
    }

    private fun executeRequest(host: String, config: AppConfig, path: String, method: String, body: String?): Response {
        val url = "http://$host:${config.pcPort}$path"
        val builder = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${config.authToken}")
            .header("Content-Type", "application/json")

        if (method == "POST") {
            val reqBody = (body ?: "{}").toRequestBody("application/json".toMediaType())
            builder.post(reqBody)
        } else {
            builder.get()
        }

        return client.newCall(builder.build()).execute()
    }

    private suspend fun <T> callWithFallback(
        config: AppConfig,
        path: String,
        method: String = "GET",
        body: String? = null,
        parser: (Response, String, String) -> T
    ): Result<T> = withContext(Dispatchers.IO) {
        val candidates = getCandidates(config)
        var lastException: Exception? = null

        for (host in candidates) {
            try {
                executeRequest(host, config, path, method, body).use { resp ->
                    val bodyStr = resp.body?.string().orEmpty()
                    val json = try {
                        if (bodyStr.isNotBlank()) JSONObject(bodyStr) else null
                    } catch (_: Exception) {
                        null
                    }

                    val isSuccessFlag = json?.optBoolean("success", true) ?: true
                    val errorMsg = json?.optString("error")?.takeIf { it.isNotBlank() }
                        ?: json?.optString("message")?.takeIf { it.isNotBlank() }

                    if ((resp.isSuccessful && isSuccessFlag) || resp.code == 207) {
                        val parsed = parser(resp, bodyStr, host)
                        activeHost = host
                        return@withContext Result.success(parsed)
                    } else {
                        val msg = errorMsg ?: "[$host] HTTP ${resp.code}"
                        return@withContext Result.failure(Exception(msg))
                    }
                }
            } catch (e: Exception) {
                lastException = Exception("[$host] ${e.javaClass.simpleName}: ${e.message}")
                // A timeout after a POST may mean the action already ran; do not repeat toggles.
                if (method == "POST" && e !is java.net.ConnectException && e !is java.net.UnknownHostException) {
                    return@withContext Result.failure(lastException!!)
                }
            }
        }

        Result.failure(lastException ?: Exception("Немає доступних адрес ПК"))
    }

    suspend fun getStatus(config: AppConfig): Result<PcStatus> {
        return callWithFallback(config, "/api/status", "GET") { _, bodyStr, host ->
            val json = if (bodyStr.isNotBlank()) JSONObject(bodyStr) else JSONObject()
            PcStatus(
                hostname = json.optString("hostname", "PC"),
                isAcPlugged = json.optBoolean("is_ac_plugged", true),
                batteryPercent = json.optInt("battery_percent", 100),
                isCharging = json.optBoolean("is_charging", false),
                monitorSleeping = json.optBoolean("monitor_sleeping", false),
                monitorStateVerified = json.optBoolean("monitor_state_verified", false),
                keyboardLevel = json.optInt("keyboard_level", -1),
                timestamp = json.optLong("timestamp", System.currentTimeMillis() / 1000),
                connectedHost = host
            )
        }
    }

    suspend fun getLightingCapabilities(config: AppConfig): Result<LightingCapabilities> {
        return callWithFallback(config, "/api/lighting", "GET") { _, bodyStr, _ ->
            val json = if (bodyStr.isNotBlank()) JSONObject(bodyStr) else JSONObject()
            LightingCapabilities(
                keyboardSupported = json.optBoolean("keyboard_supported", false),
                laptopIndicatorsSupported = json.optBoolean("laptop_indicators_supported", false),
                monitorIndicatorSupported = json.optBoolean("monitor_indicator_supported", false),
                message = json.optString("message", "")
            )
        }
    }

    suspend fun sleepMonitor(config: AppConfig): Result<Boolean> {
        return callWithFallback(config, "/api/monitor/sleep", "POST") { _, _, _ ->
            true
        }
    }

    suspend fun wakeMonitor(config: AppConfig): Result<Boolean> {
        return callWithFallback(config, "/api/monitor/wake", "POST") { _, _, _ ->
            true
        }
    }

    suspend fun toggleMonitor(config: AppConfig): Result<Boolean> {
        return callWithFallback(config, "/api/monitor/toggle", "POST") { _, bodyStr, _ ->
            val json = if (bodyStr.isNotBlank()) JSONObject(bodyStr) else JSONObject()
            json.optBoolean("monitor_sleeping", false)
        }
    }

    suspend fun setKeyboardLevel(config: AppConfig, level: Int): Result<Int> {
        val json = JSONObject().apply { put("level", level) }
        return callWithFallback(config, "/api/keyboard/level", "POST", json.toString()) { _, bodyStr, _ ->
            val resJson = if (bodyStr.isNotBlank()) JSONObject(bodyStr) else JSONObject()
            resJson.getInt("level").also { require(it in 0..3) { "Невідома яскравість клавіатури" } }
        }
    }

    suspend fun toggleKeyboard(config: AppConfig): Result<Int> {
        return callWithFallback(config, "/api/keyboard/toggle", "POST") { _, bodyStr, _ ->
            val resJson = if (bodyStr.isNotBlank()) JSONObject(bodyStr) else JSONObject()
            resJson.getInt("level").also { require(it in 0..3) { "Невідома яскравість клавіатури" } }
        }
    }

    suspend fun darkMode(config: AppConfig): Result<String> {
        return callWithFallback(config, "/api/lighting/dark", "POST") { _, bodyStr, _ ->
            val json = if (bodyStr.isNotBlank()) JSONObject(bodyStr) else JSONObject()
            json.getString("message").also { require(it.isNotBlank()) { "ПК не повернув результат темного режиму" } }
        }
    }

    suspend fun hibernate(config: AppConfig): Result<Boolean> {
        return callWithFallback(config, "/api/power/hibernate", "POST") { _, _, _ ->
            true
        }
    }
}
