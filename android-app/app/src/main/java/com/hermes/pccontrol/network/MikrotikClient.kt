package com.hermes.pccontrol.network

import android.util.Base64
import android.util.Log
import com.hermes.pccontrol.data.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
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

            val sslContext = SSLContext.getInstance("SSL")
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

    suspend fun sendWol(config: AppConfig): Result<String> = withContext(Dispatchers.IO) {
        try {
            val scheme = if (config.mikrotikUseHttps) "https" else "http"
            val url = "$scheme://${config.mikrotikHost}:${config.mikrotikPort}/rest/tool/wol"

            val credentials = "${config.mikrotikUser}:${config.mikrotikPass}"
            val authHeader = "Basic " + Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)

            val jsonBody = JSONObject().apply {
                put("mac", config.pcMac)
                put("interface", config.mikrotikInterface)
            }

            val request = Request.Builder()
                .url(url)
                .header("Authorization", authHeader)
                .header("Content-Type", "application/json")
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            Log.i(TAG, "Calling MikroTik REST WOL at $url for MAC ${config.pcMac} on interface ${config.mikrotikInterface}...")
            val response = unsafeOkHttpClient.newCall(request).execute()

            if (response.isSuccessful) {
                Result.success("MikroTik WOL відправлено успішно!")
            } else {
                val code = response.code
                val errBody = response.body?.string() ?: ""
                Result.failure(Exception("MikroTik HTTP $code: $errBody"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "MikroTik WOL error", e)
            Result.failure(e)
        }
    }
}
