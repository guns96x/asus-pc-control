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

    private val defaultClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

    internal fun executeWithFallback(
        config: AppConfig,
        path: String,
        client: OkHttpClient = defaultClient
    ): Response {
        var failure: Exception? = null
        val hosts = listOf(config.pcHost, config.pcFallbackHost).map { it.trim() }
            .filter { it.isNotBlank() }.distinct()
        require(path.startsWith("/") && !path.startsWith("//")) { "Некоректний шлях оновлення" }

        for (host in hosts) {
            try {
                val url = "http://$host:${config.pcPort}$path"
                val request = Request.Builder().url(url).get().build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) return response

                val errBody = try {
                    response.body?.string()?.takeIf { it.isNotBlank() }
                } catch (_: Exception) {
                    null
                }
                val serverMsg = try {
                    if (errBody != null) {
                        JSONObject(errBody).optString("error").takeIf { it.isNotBlank() }
                            ?: JSONObject(errBody).optString("message").takeIf { it.isNotBlank() }
                    } else null
                } catch (_: Exception) {
                    null
                }
                failure = Exception(serverMsg ?: "[$host] HTTP ${response.code}")
                response.close()
            } catch (e: Exception) {
                failure = Exception("[$host] ${e.javaClass.simpleName}: ${e.message}")
            }
        }
        throw failure ?: Exception("Немає доступних адрес ПК для оновлення")
    }

    suspend fun checkUpdate(
        config: AppConfig,
        client: OkHttpClient = defaultClient
    ): Result<AppVersionInfo> = withContext(Dispatchers.IO) {
        try {
            executeWithFallback(config, "/api/app/version", client).use { response ->
                val str = response.body?.string().orEmpty()
                val json = if (str.isNotBlank()) JSONObject(str) else JSONObject()
                val info = AppVersionInfo(
                    versionCode = json.getInt("version_code"),
                    versionName = json.getString("version_name"),
                    downloadUrl = json.getString("download_url"),
                    releaseNotes = json.optString("release_notes", "")
                )
                require(info.versionCode > 0 && info.versionName.isNotBlank() && info.downloadUrl == "/app.apk") {
                    "ПК повернув некоректну інформацію про оновлення"
                }
                Result.success(info)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadAndInstallApk(
        context: Context,
        config: AppConfig,
        downloadUrl: String = "/app.apk",
        client: OkHttpClient = defaultClient,
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

            val apkFile = File(context.cacheDir, "update.apk")
            val tempFile = File(context.cacheDir, "update.apk.tmp")
            if (tempFile.exists()) tempFile.delete()
            if (apkFile.exists()) apkFile.delete()

            executeWithFallback(config, downloadUrl, client).use { response ->
                val body = response.body ?: return@withContext Result.failure(Exception("Порожня відповідь сервера"))
                val totalBytes = body.contentLength()
                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(32 * 1024)
                        var bytesCopied = 0L
                        var read: Int
                        var lastProgressReport = 0L

                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            bytesCopied += read
                            if (totalBytes > 0) {
                                val progress = bytesCopied.toFloat() / totalBytes.toFloat()
                                val now = System.currentTimeMillis()
                                if (now - lastProgressReport > 100 || bytesCopied == totalBytes) {
                                    lastProgressReport = now
                                    withContext(Dispatchers.Main) {
                                        onProgress(progress)
                                    }
                                }
                            }
                        }
                        require(bytesCopied > 0 && (totalBytes < 0 || bytesCopied == totalBytes)) {
                            "APK завантажено не повністю"
                        }
                        output.flush()
                    }
                }
            }

            if (!tempFile.renameTo(apkFile)) {
                tempFile.copyTo(apkFile, overwrite = true)
                tempFile.delete()
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

                val resolveList = context.packageManager.queryIntentActivities(installIntent, 0)
                for (resolveInfo in resolveList) {
                    val pkg = resolveInfo.activityInfo.packageName
                    context.grantUriPermission(pkg, apkUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
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
