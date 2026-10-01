package com.hermes.pccontrol.network

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.hermes.pccontrol.data.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class AppVersionInfo(
    val versionCode: Int,
    val versionName: String,
    val downloadUrl: String,
    val releaseNotes: String
)

object OtaManager {
    private const val TAG = "OtaManager"

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun executeWithFallback(config: AppConfig, path: String): Response {
        var failure: Exception? = null
        val hosts = listOf(config.pcHost, config.pcFallbackHost).map { it.trim() }
            .filter { it.isNotBlank() }.distinct()
        for (host in hosts) {
            try {
                val request = Request.Builder().url("http://$host:${config.pcPort}$path").get().build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) return response
                failure = Exception("[$host] HTTP ${response.code}")
                response.close()
            } catch (e: Exception) {
                failure = Exception("[$host] ${e.javaClass.simpleName}")
            }
        }
        throw failure ?: Exception("Немає доступних адрес ПК для оновлення")
    }

    suspend fun checkUpdate(config: AppConfig): Result<AppVersionInfo> = withContext(Dispatchers.IO) {
        try {
            executeWithFallback(config, "/api/app/version").use { response ->
                val str = response.body?.string() ?: "{}"
                val json = JSONObject(str)
                val info = AppVersionInfo(
                    versionCode = json.optInt("version_code", 1),
                    versionName = json.optString("version_name", "1.0.0"),
                    downloadUrl = json.optString("download_url", "/app.apk"),
                    releaseNotes = json.optString("release_notes", "")
                )
                Result.success(info)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadAndInstallApk(
        context: Context,
        config: AppConfig,
        onProgress: (Float) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // minSdk is 26, so the unknown-sources permission check always applies.
            if (!context.packageManager.canRequestPackageInstalls()) {
                withContext(Dispatchers.Main) {
                    val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(settingsIntent)
                }
                return@withContext Result.failure(Exception("Будь ласка, дозвольте встановлення додатків для ASUS Control у налаштуваннях"))
            }

            val apkFile = File(context.cacheDir, "update.apk")
            if (apkFile.exists()) {
                apkFile.delete()
            }

            executeWithFallback(config, "/app.apk").use { response ->
                val body = response.body ?: return@withContext Result.failure(Exception("Порожня відповідь сервера"))
                val totalBytes = body.contentLength()
                body.byteStream().use { input ->
                    FileOutputStream(apkFile).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var bytesCopied = 0L
                        var lastPercent = -1
                        var read: Int

                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            bytesCopied += read
                            // Hop to the main thread only when the visible percentage changes, not per chunk.
                            val percent = if (totalBytes > 0) (bytesCopied * 100 / totalBytes).toInt() else -1
                            if (percent != lastPercent) {
                                lastPercent = percent
                                withContext(Dispatchers.Main) { onProgress(percent / 100f) }
                            }
                        }
                    }
                }
            }

            Log.i(TAG, "APK successfully downloaded to ${apkFile.absolutePath}, launching installer...")

            withContext(Dispatchers.Main) {
                val apkUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    apkFile
                )

                val installIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(apkUri, "application/vnd.android.package-archive")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                }
                context.startActivity(installIntent)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "OTA Download error", e)
            Result.failure(e)
        }
    }
}
