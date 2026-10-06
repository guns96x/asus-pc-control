package com.hermes.pccontrol.network

import android.util.Log
import com.hermes.pccontrol.data.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object MikrotikClient {
    private const val TAG = "MikrotikClient"

    private val unsafeOkHttpClient: OkHttpClient by lazy {
        try {
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })

            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, trustAllCerts, SecureRandom())

            OkHttpClient.Builder()
                .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                .hostnameVerifier { _, _ -> true }
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(8, TimeUnit.SECONDS)
                .build()
        } catch (e: Exception) {
            OkHttpClient()
        }
    }

    internal var defaultClientFactory: () -> OkHttpClient = { unsafeOkHttpClient }

    private fun safeLogInfo(message: String) {
        try {
            Log.i(TAG, message)
        } catch (_: Throwable) {
            // Ignored in non-Android JVM environments
        }
    }

    private fun safeLogError(message: String, throwable: Throwable? = null) {
        try {
            if (throwable != null) {
                Log.e(TAG, message, throwable)
            } else {
                Log.e(TAG, message)
            }
        } catch (_: Throwable) {
            // Ignored in non-Android JVM environments
        }
    }

    fun normalizeMac(macStr: String): String {
        val clean = macStr.replace(":", "").replace("-", "").trim()
        require(clean.length == 12 && clean.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
            "Невірний формат MAC-адреси: $macStr"
        }
        return clean.chunked(2).joinToString(":").uppercase()
    }

    fun validateConfig(config: AppConfig): Result<Unit> {
        val host = config.mikrotikHost.trim()
        if (host.isBlank() || host.any { it.isWhitespace() || it == '/' || it == '\\' || it == '@' || it == '?' || it == '#' }) {
            return Result.failure(IllegalArgumentException("Не вказано або некоректний хост MikroTik"))
        }
        try {
            HttpUrl.Builder().scheme("https").host(host).build()
        } catch (_: IllegalArgumentException) {
            return Result.failure(IllegalArgumentException("Некоректний хост MikroTik: введіть лише IP або ім'я хоста"))
        }

        if (config.mikrotikPort !in 1..65535) {
            return Result.failure(IllegalArgumentException("Некоректний порт MikroTik: ${config.mikrotikPort} (має бути 1-65535)"))
        }

        val iface = config.mikrotikInterface.trim()
        if (iface.isBlank() || iface.any { it.isWhitespace() || it == '"' || it == '\'' || it == '\\' || it == '\n' || it == '\r' }) {
            return Result.failure(IllegalArgumentException("Не вказано або некоректний інтерфейс MikroTik"))
        }

        val user = config.mikrotikUser.trim()
        if (user.isBlank()) {
            return Result.failure(IllegalArgumentException("Не вказано ім'я користувача MikroTik"))
        }

        try {
            normalizeMac(config.pcMac)
        } catch (e: IllegalArgumentException) {
            return Result.failure(e)
        }

        return Result.success(Unit)
    }

    fun buildUrl(config: AppConfig): String {
        val scheme = if (config.mikrotikUseHttps) "https" else "http"
        val host = config.mikrotikHost.trim()
        return HttpUrl.Builder().scheme(scheme).host(host).port(config.mikrotikPort)
            .addPathSegments("rest/tool/wol").build().toString()
    }

    fun buildAuthHeader(user: String, pass: String): String {
        val credentials = "$user:$pass"
        val encoded = Base64.getEncoder().encodeToString(credentials.toByteArray(Charsets.UTF_8))
        return "Basic $encoded"
    }

    fun buildWolJson(mac: String, iface: String): String {
        val normalizedMac = normalizeMac(mac)
        val cleanIface = iface.trim()
        return """{"mac":"$normalizedMac","interface":"$cleanIface"}"""
    }

    fun mapHttpError(code: Int): String {
        return when (code) {
            401 -> "Помилка автентифікації MikroTik: невірний логін або пароль (HTTP 401)"
            403 -> "Помилка автентифікації MikroTik: користувачу бракує прав read, test або rest-api (HTTP 403)"
            404 -> "REST API MikroTik недоступний або версія RouterOS не підтримує /rest/tool/wol (HTTP 404)"
            400 -> "Невірні параметри запиту WOL для MikroTik (HTTP 400)"
            in 500..599 -> "Внутрішня помилка сервера MikroTik (HTTP $code)"
            else -> "Помилка відповіді MikroTik (HTTP $code)"
        }
    }

    fun mapNetworkException(e: Throwable): String {
        return when (e) {
            is java.net.UnknownHostException -> "Неможливо знайти хост MikroTik: перевірте адресу або підключення"
            is java.net.SocketTimeoutException -> "Перевищено час очікування відповіді від MikroTik (таймаут)"
            is java.net.ConnectException -> "Не вдалося з'єднатися з MikroTik (з'єднання відхилено)"
            is SSLException -> "Помилка безпечного з'єднання (TLS/SSL) з MikroTik"
            is IOException -> "Мережева помилка підключення до MikroTik"
            else -> "Помилка виконання запиту до MikroTik"
        }
    }

    suspend fun sendWol(
        config: AppConfig,
        client: OkHttpClient = defaultClientFactory()
    ): Result<String> = withContext(Dispatchers.IO) {
        val validation = validateConfig(config)
        if (validation.isFailure) {
            return@withContext Result.failure(validation.exceptionOrNull()!!)
        }

        try {
            val url = buildUrl(config)
            val authHeader = buildAuthHeader(config.mikrotikUser.trim(), config.mikrotikPass)
            val jsonBody = buildWolJson(config.pcMac, config.mikrotikInterface)

            val request = Request.Builder()
                .url(url)
                .header("Authorization", authHeader)
                .header("Content-Type", "application/json")
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .build()

            val normalizedMac = normalizeMac(config.pcMac)
            val iface = config.mikrotikInterface.trim()
            val scheme = if (config.mikrotikUseHttps) "https" else "http"
            val host = config.mikrotikHost.trim()
            val port = config.mikrotikPort
            safeLogInfo("Calling MikroTik REST WOL at $scheme://$host:$port/rest/tool/wol for MAC $normalizedMac on interface $iface...")

            val call = client.newCall(request)
            call.execute().use { response ->
                if (response.isSuccessful) {
                    Result.success("MikroTik WOL відправлено успішно!")
                } else {
                    val message = mapHttpError(response.code)
                    Result.failure(Exception(message))
                }
            }
        } catch (e: IllegalArgumentException) {
            Result.failure(e)
        } catch (e: Exception) {
            safeLogError("MikroTik WOL error", e)
            val friendly = mapNetworkException(e)
            Result.failure(Exception(friendly, e))
        }
    }
}
