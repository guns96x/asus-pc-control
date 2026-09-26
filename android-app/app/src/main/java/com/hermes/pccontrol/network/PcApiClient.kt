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
    val keyboardLevel: Int = 3,
    val timestamp: Long = 0L,
    val connectedHost: String = ""
)

class PcApiClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .build()

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
        parser: (Response, String) -> T
    ): Result<T> = withContext(Dispatchers.IO) {
        val candidates = getCandidates(config)
        var lastException: Exception? = null

        for (host in candidates) {
            try {
                val resp = executeRequest(host, config, path, method, body)
                if (resp.isSuccessful) {
                    activeHost = host
                    val parsed = parser(resp, host)
                    return@withContext Result.success(parsed)
                } else {
                    lastException = Exception("[$host] HTTP ${resp.code}")
                }
            } catch (e: Exception) {
                lastException = Exception("[$host] ${e.javaClass.simpleName}: ${e.message}")
            }
        }

        Result.failure(lastException ?: Exception("Немає доступних адрес ПК"))
    }

    suspend fun getStatus(config: AppConfig): Result<PcStatus> {
        return callWithFallback(config, "/api/status", "GET") { resp, host ->
            val str = resp.body?.string() ?: "{}"
            val json = JSONObject(str)
            PcStatus(
                hostname = json.optString("hostname", "PC"),
                isAcPlugged = json.optBoolean("is_ac_plugged", true),
                batteryPercent = json.optInt("battery_percent", 100),
                isCharging = json.optBoolean("is_charging", false),
                monitorSleeping = json.optBoolean("monitor_sleeping", false),
                keyboardLevel = json.optInt("keyboard_level", 3),
                timestamp = json.optLong("timestamp", System.currentTimeMillis() / 1000),
                connectedHost = host
            )
        }
    }

    suspend fun sleepMonitor(config: AppConfig): Result<Boolean> {
        return callWithFallback(config, "/api/monitor/sleep", "POST") { resp, _ ->
            resp.isSuccessful
        }
    }

    suspend fun wakeMonitor(config: AppConfig): Result<Boolean> {
        return callWithFallback(config, "/api/monitor/wake", "POST") { resp, _ ->
            resp.isSuccessful
        }
    }

    suspend fun toggleMonitor(config: AppConfig): Result<Boolean> {
        return callWithFallback(config, "/api/monitor/toggle", "POST") { resp, _ ->
            resp.isSuccessful
        }
    }

    suspend fun setKeyboardLevel(config: AppConfig, level: Int): Result<Int> {
        val json = JSONObject().apply { put("level", level) }
        return callWithFallback(config, "/api/keyboard/level", "POST", json.toString()) { resp, _ ->
            level
        }
    }

    suspend fun toggleKeyboard(config: AppConfig): Result<Int> {
        return callWithFallback(config, "/api/keyboard/toggle", "POST") { resp, _ ->
            val str = resp.body?.string() ?: "{}"
            val json = JSONObject(str)
            json.optInt("level", 0)
        }
    }

    suspend fun hibernate(config: AppConfig): Result<Boolean> {
        return callWithFallback(config, "/api/power/hibernate", "POST") { resp, _ ->
            resp.isSuccessful
        }
    }
}
