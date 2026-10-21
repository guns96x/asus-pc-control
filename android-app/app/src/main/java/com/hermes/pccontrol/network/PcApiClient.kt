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
    val performanceMode: Int = 0,
    val performanceModeName: String = "",
    val keepAwakeActive: Boolean = false,
    val timestamp: Long = 0L,
    val connectedHost: String = ""
)

data class LightingCapabilities(
    val keyboardSupported: Boolean = false,
    val laptopIndicatorsSupported: Boolean = false,
    val monitorIndicatorSupported: Boolean = false,
    val message: String = ""
)

data class TaskMetrics(
    val cpuTotal: Float = 0f,
    val cpuTempC: Int = 0,
    val cpuFanRpm: Int = 0,
    val cpuFreqGhz: Float = 0f,
    val cpuModel: String = "Intel Core i5-12500H",
    val coresLogical: Int = 16,
    val coresPhysical: Int = 12,
    val uptimeSec: Long = 0L,
    val processesCount: Int = 0,
    val threadsCount: Int = 0,
    val handlesCount: Int = 0,
    val ramPercent: Float = 0f,
    val ramUsedGb: Float = 0f,
    val ramTotalGb: Float = 0f,
    val ramAvailGb: Float = 0f,
    val diskReadMb: Float = 0f,
    val diskWriteMb: Float = 0f,
    val netRecvKbps: Float = 0f,
    val netSentKbps: Float = 0f,
    val netRecvMbps: Float = 0f,
    val netSentMbps: Float = 0f,
    val gpuName: String = "NVIDIA GeForce RTX 3050 Laptop GPU",
    val gpuLoad: Float = 0f,
    val gpuTempC: Int = 0,
    val gpuFanRpm: Int = 0,
    val gpuVramUsedMb: Float = 0f,
    val gpuVramTotalMb: Float = 4096f,
    val cpuHistory: List<Float> = emptyList(),
    val ramHistory: List<Float> = emptyList(),
    val gpuHistory: List<Float> = emptyList()
)

data class ProcessItem(
    val pid: Int,
    val name: String,
    val cpu: Float,
    val memoryMb: Float,
    val memoryPercent: Float,
    val diskMbps: Float,
    val category: String,
    val isCritical: Boolean,
    val exe: String,
    val username: String
)

class PcApiClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()
) {
    var activeHost: String? = null
        internal set

    internal fun isConnectFailure(e: Exception): Boolean {
        if (e is java.net.ConnectException ||
            e is java.net.UnknownHostException ||
            e is java.net.NoRouteToHostException ||
            e is java.net.PortUnreachableException) {
            return true
        }
        if (e is java.net.SocketTimeoutException) {
            val msg = e.message.orEmpty().lowercase()
            return msg.contains("connect timed out") || msg.contains("failed to connect")
        }
        val cause = e.cause
        if (cause is Exception && cause !== e) {
            return isConnectFailure(cause)
        }
        return false
    }

    internal fun getCandidates(config: AppConfig): List<String> {
        val list = mutableListOf<String>()
        val primary = config.pcHost.trim()
        val fallback = config.pcFallbackHost.trim()
        val validConfigHosts = listOf(primary, fallback).filter { it.isNotBlank() }

        val cached = activeHost?.trim()
        if (!cached.isNullOrBlank() && validConfigHosts.contains(cached)) {
            list.add(cached)
        }
        for (h in validConfigHosts) {
            if (!list.contains(h)) list.add(h)
        }
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

    internal suspend fun <T> callWithFallback(
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
                        lastException = Exception(msg)
                        if (method == "POST" || resp.code == 401 || resp.code == 403) {
                            return@withContext Result.failure(lastException!!)
                        }
                        if (activeHost == host) {
                            activeHost = null
                        }
                    }
                }
            } catch (e: Exception) {
                lastException = Exception("[$host] ${e.javaClass.simpleName}: ${e.message}")
                if (activeHost == host) {
                    activeHost = null
                }
                // A POST may already have changed hardware; only connection failures permit fallback.
                if (method == "POST" && !isConnectFailure(e)) {
                    return@withContext Result.failure(lastException!!)
                }
            }
        }

        Result.failure(lastException ?: Exception("Немає доступних адрес ПК"))
    }

    suspend fun getStatus(config: AppConfig): Result<PcStatus> {
        return callWithFallback(config, "/api/status", "GET") { _, bodyStr, host ->
            val json = JSONObject(bodyStr)
            require(json.has("hostname") && json.has("timestamp")) { "ПК повернув неповний статус" }
            PcStatus(
                hostname = json.optString("hostname", "PC"),
                isAcPlugged = json.optBoolean("is_ac_plugged", true),
                batteryPercent = json.optInt("battery_percent", 100),
                isCharging = json.optBoolean("is_charging", false),
                monitorSleeping = json.optBoolean("monitor_sleeping", false),
                monitorStateVerified = json.optBoolean("monitor_state_verified", false),
                keyboardLevel = json.optInt("keyboard_level", -1),
                performanceMode = json.optInt("performance_mode", 0),
                performanceModeName = json.optString("performance_mode_name", "balanced"),
                keepAwakeActive = json.optBoolean("keep_awake_active", false),
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
        if (level !in 0..3) return Result.failure(IllegalArgumentException("Яскравість має бути від 0 до 3"))
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

    suspend fun getPerformanceMode(config: AppConfig): Result<Int> {
        return callWithFallback(config, "/api/performance", "GET") { _, bodyStr, _ ->
            val json = if (bodyStr.isNotBlank()) JSONObject(bodyStr) else JSONObject()
            json.optInt("mode", 0)
        }
    }

    suspend fun setPerformanceMode(config: AppConfig, mode: Int): Result<Int> {
        if (mode !in 0..2) return Result.failure(IllegalArgumentException("Режим має бути від 0 до 2"))
        val json = JSONObject().apply { put("mode", mode) }
        return callWithFallback(config, "/api/performance", "POST", json.toString()) { _, bodyStr, _ ->
            val resJson = if (bodyStr.isNotBlank()) JSONObject(bodyStr) else JSONObject()
            resJson.getInt("mode").also { require(it in 0..2) { "Невідомий режим продуктивності" } }
        }
    }

    suspend fun hibernate(config: AppConfig): Result<Boolean> {
        return callWithFallback(config, "/api/power/hibernate", "POST") { _, _, _ ->
            true
        }
    }

    private fun executeCustomPort(host: String, port: Int, path: String, method: String = "GET", body: String? = null): Response {
        val url = "http://$host:$port$path"
        val builder = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
        if (method == "POST") {
            val reqBody = (body ?: "{}").toRequestBody("application/json".toMediaType())
            builder.post(reqBody)
        } else {
            builder.get()
        }
        return client.newCall(builder.build()).execute()
    }

    suspend fun getTaskMetrics(config: AppConfig): Result<TaskMetrics> = withContext(Dispatchers.IO) {
        val candidates = getCandidates(config)
        var lastErr: Exception? = null
        for (host in candidates) {
            try {
                executeCustomPort(host, config.taskManagerPort, "/api/system").use { resp ->
                    if (resp.isSuccessful) {
                        val json = JSONObject(resp.body?.string().orEmpty())
                        val cpu = json.optJSONObject("cpu") ?: JSONObject()
                        val mem = json.optJSONObject("memory") ?: JSONObject()
                        val disk = json.optJSONObject("disk") ?: JSONObject()
                        val net = json.optJSONObject("network") ?: JSONObject()
                        val gpu = json.optJSONObject("gpu") ?: JSONObject()

                        val metrics = TaskMetrics(
                            cpuTotal = cpu.optDouble("total", 0.0).toFloat(),
                            cpuTempC = cpu.optInt("temp_c", 0),
                            cpuFanRpm = cpu.optInt("fan_rpm", 0),
                            cpuFreqGhz = cpu.optDouble("freq_ghz", 0.0).toFloat(),
                            cpuModel = cpu.optString("model", "Intel Core i5-12500H"),
                            coresLogical = cpu.optInt("cores_logical", 16),
                            coresPhysical = cpu.optInt("cores_physical", 12),
                            uptimeSec = cpu.optLong("uptime_sec", 0L),
                            processesCount = cpu.optInt("processes_count", 0),
                            threadsCount = cpu.optInt("threads_count", 0),
                            handlesCount = cpu.optInt("handles_count", 0),
                            ramPercent = mem.optDouble("percent", 0.0).toFloat(),
                            ramUsedGb = mem.optDouble("used_gb", 0.0).toFloat(),
                            ramTotalGb = mem.optDouble("total_gb", 0.0).toFloat(),
                            ramAvailGb = mem.optDouble("available_gb", 0.0).toFloat(),
                            diskReadMb = disk.optDouble("read_speed_mb", 0.0).toFloat(),
                            diskWriteMb = disk.optDouble("write_speed_mb", 0.0).toFloat(),
                            netRecvKbps = net.optDouble("kbps_recv", 0.0).toFloat(),
                            netSentKbps = net.optDouble("kbps_sent", 0.0).toFloat(),
                            netRecvMbps = net.optDouble("mbps_recv", 0.0).toFloat(),
                            netSentMbps = net.optDouble("mbps_sent", 0.0).toFloat(),
                            gpuName = gpu.optString("name", "NVIDIA GeForce RTX 3050 Laptop GPU"),
                            gpuLoad = gpu.optDouble("load", 0.0).toFloat(),
                            gpuTempC = gpu.optInt("temp_c", 0),
                            gpuFanRpm = gpu.optInt("fan_rpm", 0),
                            gpuVramUsedMb = gpu.optDouble("memory_used_mb", 0.0).toFloat(),
                            gpuVramTotalMb = gpu.optDouble("memory_total_mb", 4096.0).toFloat()
                        )
                        return@withContext Result.success(metrics)
                    }
                }
            } catch (e: Exception) {
                lastErr = e
            }
        }
        Result.failure(lastErr ?: Exception("Не вдалося отримати метрики диспетчера"))
    }

    suspend fun getProcesses(
        config: AppConfig,
        search: String = "",
        category: String = "all",
        sortBy: String = "cpu"
    ): Result<List<ProcessItem>> = withContext(Dispatchers.IO) {
        val candidates = getCandidates(config)
        var lastErr: Exception? = null
        val encodedSearch = java.net.URLEncoder.encode(search, "UTF-8")
        val path = "/api/processes?search=$encodedSearch&category=$category&sort_by=$sortBy&limit=250"
        for (host in candidates) {
            try {
                executeCustomPort(host, config.taskManagerPort, path).use { resp ->
                    if (resp.isSuccessful) {
                        val json = JSONObject(resp.body?.string().orEmpty())
                        val arr = json.optJSONArray("processes") ?: org.json.JSONArray()
                        val list = mutableListOf<ProcessItem>()
                        for (i in 0 until arr.length()) {
                            val o = arr.getJSONObject(i)
                            list.add(
                                ProcessItem(
                                    pid = o.getInt("pid"),
                                    name = o.optString("name", ""),
                                    cpu = o.optDouble("cpu", 0.0).toFloat(),
                                    memoryMb = o.optDouble("memory_mb", 0.0).toFloat(),
                                    memoryPercent = o.optDouble("memory_percent", 0.0).toFloat(),
                                    diskMbps = o.optDouble("disk_mbps", 0.0).toFloat(),
                                    category = o.optString("category", "background"),
                                    isCritical = o.optBoolean("is_critical", false),
                                    exe = o.optString("exe", ""),
                                    username = o.optString("username", "")
                                )
                            )
                        }
                        return@withContext Result.success(list)
                    }
                }
            } catch (e: Exception) {
                lastErr = e
            }
        }
        Result.failure(lastErr ?: Exception("Не вдалося отримати процеси"))
    }

    suspend fun killProcess(config: AppConfig, pid: Int): Result<String> = withContext(Dispatchers.IO) {
        val candidates = getCandidates(config)
        for (host in candidates) {
            try {
                executeCustomPort(host, config.taskManagerPort, "/api/process/$pid/kill", "POST").use { resp ->
                    val bodyStr = resp.body?.string().orEmpty()
                    val json = if (bodyStr.isNotBlank()) JSONObject(bodyStr) else JSONObject()
                    if (resp.isSuccessful && json.optBoolean("success", true)) {
                        return@withContext Result.success(json.optString("message", "Процес $pid завершено"))
                    } else {
                        val err = json.optString("detail", json.optString("error", "Помилка"))
                        return@withContext Result.failure(Exception(err))
                    }
                }
            } catch (e: Exception) {
                return@withContext Result.failure(e)
            }
        }
        Result.failure(Exception("Не вдалося завершити процес"))
    }
}
