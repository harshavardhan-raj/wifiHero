package com.wifihero.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import androidx.core.content.FileProvider
import com.wifihero.app.model.WifiConfig
import okhttp3.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

class UpdateChecker(private val context: Context) {

    private val httpClient = OkHttpClient()
    private val handler = Handler(Looper.getMainLooper())

    interface UpdateListener {
        fun onUpdateAvailable(latestVersion: String, changelog: String, downloadUrl: String)
        fun onNoUpdateAvailable()
        fun onDownloadProgress(progress: Int)
        fun onDownloadSuccess(apkFile: File)
        fun onDownloadFailure(e: Exception)
    }

    /**
     * Checks if a new version is available by comparing local app version with config version.
     */
    fun checkForUpdates(config: WifiConfig, listener: UpdateListener) {
        val currentVersion = getCurrentVersionName()
        val latestVersion = config.appUpdate.latestVersion
        val downloadUrl = config.appUpdate.apkUrl
        val changelog = config.appUpdate.changelog

        if (isVersionOlder(currentVersion, latestVersion) && downloadUrl.isNotBlank()) {
            listener.onUpdateAvailable(latestVersion, changelog, downloadUrl)
        } else {
            listener.onNoUpdateAvailable()
        }
    }

    private fun getCurrentVersionName(): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    /**
     * Checks if current version is older than latest version (e.g. "1.0.0" vs "1.1.0")
     */
    private fun isVersionOlder(current: String, latest: String): Boolean {
        val currParts = current.split(".").mapNotNull { it.toIntOrNull() }
        val lateParts = latest.split(".").mapNotNull { it.toIntOrNull() }

        val length = maxOf(currParts.size, lateParts.size)
        for (i in 0 until length) {
            val currVal = if (i < currParts.size) currParts[i] else 0
            val lateVal = if (i < lateParts.size) lateParts[i] else 0
            
            if (currVal < lateVal) return true
            if (currVal > lateVal) return false
        }
        return false
    }

    /**
     * Downloads the APK file in the background and notifies the listener.
     */
    fun downloadApk(url: String, listener: UpdateListener) {
        val request = Request.Builder().url(url).build()
        
        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                handler.post { listener.onDownloadFailure(e) }
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    handler.post { listener.onDownloadFailure(IOException("Server error: ${response.code}")) }
                    return
                }

                val body = response.body
                if (body == null) {
                    handler.post { listener.onDownloadFailure(IOException("Empty response body")) }
                    return
                }

                try {
                    // Save APK in the app's cache directory (safe, no external permissions needed)
                    val apkDir = File(context.cacheDir, "updates")
                    if (!apkDir.exists()) apkDir.mkdirs()
                    
                    val apkFile = File(apkDir, "wifihero-update.apk")
                    if (apkFile.exists()) apkFile.delete()

                    val inputStream: InputStream = body.byteStream()
                    val outputStream = FileOutputStream(apkFile)
                    val totalBytes = body.contentLength()
                    
                    val buffer = ByteArray(4096)
                    var bytesRead: Int
                    var totalRead: Long = 0

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        
                        if (totalBytes > 0) {
                            val progress = ((totalRead * 100) / totalBytes).toInt()
                            handler.post { listener.onDownloadProgress(progress) }
                        }
                    }

                    outputStream.flush()
                    outputStream.close()
                    inputStream.close()

                    handler.post { listener.onDownloadSuccess(apkFile) }
                } catch (e: Exception) {
                    handler.post { listener.onDownloadFailure(e) }
                }
            }
        })
    }

    /**
     * Installs the downloaded APK. Opens the Android package installer.
     */
    fun installApk(apkFile: File) {
        val authority = "${context.packageName}.fileprovider"
        val apkUri = FileProvider.getUriForFile(context, authority, apkFile)

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }

        // On Android 8.0+, we check if we have permission to install unknown apps
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val settingsIntent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(settingsIntent)
                // We open settings, and once the user grants it, they can return and tap update again.
                return
            }
        }

        context.startActivity(intent)
    }
}
