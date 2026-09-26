package com.hermes.pccontrol.network

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.hermes.pccontrol.data.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
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

    suspend fun checkUpdate(config: AppConfig): Result<AppVersionInfo> = withContext(Dispatchers.IO) {
        try {
            val url = "http://${config.pcHost}:${config.pcPort}/api/app/version"
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val str = response.body?.string() ?: "{}"
                val json = JSONObject(str)
                val info = AppVersionInfo(
                    versionCode = json.optInt("version_code", 1),
                    versionName = json.optString("version_name", "1.0.0"),
                    downloadUrl = json.optString("download_url", "/app.apk"),
                    releaseNotes = json.optString("release_notes", "")
                )
                Result.success(info)
            } else {
                Result.failure(Exception("HTTP ${response.code}"))
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
            // Check unknown sources permission on Android 8.0+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
            }

            val apkUrl = "http://${config.pcHost}:${config.pcPort}/app.apk"
            val request = Request.Builder().url(apkUrl).get().build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Не вдалося завантажити APK: HTTP ${response.code}"))
            }

            val body = response.body ?: return@withContext Result.failure(Exception("Порожня відповідь сервера"))
            val totalBytes = body.contentLength()

            val apkFile = File(context.cacheDir, "update.apk")
            if (apkFile.exists()) {
                apkFile.delete()
            }

            body.byteStream().use { input ->
                FileOutputStream(apkFile).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var bytesCopied = 0L
                    var read: Int

                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesCopied += read
                        if (totalBytes > 0) {
                            val progress = bytesCopied.toFloat() / totalBytes.toFloat()
                            withContext(Dispatchers.Main) {
                                onProgress(progress)
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
