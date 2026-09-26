package com.hermes.pccontrol.network

import android.util.Log
import com.hermes.pccontrol.data.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class PcStatus(
    val hostname: String = "Unknown",
    val isAcPlugged: Boolean = true,
    val batteryPercent: Int = 100,
    val isCharging: Boolean = false,
    val monitorSleeping: Boolean = false,
    val keyboardLevel: Int = 3,
    val timestamp: Long = 0L
)

class PcApiClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private fun buildUrl(config: AppConfig, path: String): String {
        return "http://${config.pcHost}:${config.pcPort}$path"
    }

    private fun buildRequest(config: AppConfig, path: String, method: String = "GET", body: String? = null): Request {
        val builder = Request.Builder()
            .url(buildUrl(config, path))
            .header("Authorization", "Bearer ${config.authToken}")
            .header("Content-Type", "application/json")

        if (method == "POST") {
            val reqBody = (body ?: "{}").toRequestBody("application/json".toMediaType())
            builder.post(reqBody)
        } else {
            builder.get()
        }

        return builder.build()
    }

    suspend fun getStatus(config: AppConfig): Result<PcStatus> = withContext(Dispatchers.IO) {
        try {
            val request = buildRequest(config, "/api/status")
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val str = response.body?.string() ?: "{}"
                val json = JSONObject(str)
                val status = PcStatus(
                    hostname = json.optString("hostname", "PC"),
                    isAcPlugged = json.optBoolean("is_ac_plugged", true),
                    batteryPercent = json.optInt("battery_percent", 100),
                    isCharging = json.optBoolean("is_charging", false),
                    monitorSleeping = json.optBoolean("monitor_sleeping", false),
                    keyboardLevel = json.optInt("keyboard_level", 3),
                    timestamp = json.optLong("timestamp", System.currentTimeMillis() / 1000)
                )
                Result.success(status)
            } else {
                Result.failure(Exception("HTTP ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sleepMonitor(config: AppConfig): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val req = buildRequest(config, "/api/monitor/sleep", "POST")
            val resp = client.newCall(req).execute()
            Result.success(resp.isSuccessful)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun wakeMonitor(config: AppConfig): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val req = buildRequest(config, "/api/monitor/wake", "POST")
            val resp = client.newCall(req).execute()
            Result.success(resp.isSuccessful)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun toggleMonitor(config: AppConfig): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val req = buildRequest(config, "/api/monitor/toggle", "POST")
            val resp = client.newCall(req).execute()
            Result.success(resp.isSuccessful)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setKeyboardLevel(config: AppConfig, level: Int): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply { put("level", level) }
            val req = buildRequest(config, "/api/keyboard/level", "POST", json.toString())
            val resp = client.newCall(req).execute()
            if (resp.isSuccessful) {
                Result.success(level)
            } else {
                Result.failure(Exception("HTTP ${resp.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun toggleKeyboard(config: AppConfig): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val req = buildRequest(config, "/api/keyboard/toggle", "POST")
            val resp = client.newCall(req).execute()
            if (resp.isSuccessful) {
                val str = resp.body?.string() ?: "{}"
                val json = JSONObject(str)
                Result.success(json.optInt("level", 0))
            } else {
                Result.failure(Exception("HTTP ${resp.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun hibernate(config: AppConfig): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val req = buildRequest(config, "/api/power/hibernate", "POST")
            val resp = client.newCall(req).execute()
            Result.success(resp.isSuccessful)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
