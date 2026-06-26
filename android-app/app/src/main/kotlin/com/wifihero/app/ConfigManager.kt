package com.wifihero.app

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.wifihero.app.model.WifiConfig
import okhttp3.*
import java.io.IOException

class ConfigManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val httpClient = OkHttpClient()

    companion object {
        private const val PREFS_NAME = "wifihero_prefs"
        private const val KEY_CONFIG_URL = "config_url"
        private const val KEY_CACHED_JSON = "cached_config_json"
        
        // Default fallback config URL pointing to a placeholder GitHub repo.
        // Users can easily update this in the app settings menu.
        const val DEFAULT_CONFIG_URL = "https://raw.githubusercontent.com/harsha-legend/wifihero/main/config/wifi-config.json"
    }

    var configUrl: String
        get() = prefs.getString(KEY_CONFIG_URL, DEFAULT_CONFIG_URL) ?: DEFAULT_CONFIG_URL
        set(value) {
            prefs.edit().putString(KEY_CONFIG_URL, value).apply()
        }

    fun getCachedConfig(): WifiConfig? {
        val json = prefs.getString(KEY_CACHED_JSON, null) ?: return null
        return try {
            gson.fromJson(json, WifiConfig::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun saveConfigToCache(jsonString: String) {
        prefs.edit().putString(KEY_CACHED_JSON, jsonString).apply()
    }

    fun fetchConfigFromServer(onResult: (WifiConfig?, Exception?) -> Unit) {
        val url = configUrl
        if (url.isBlank()) {
            onResult(null, IllegalArgumentException("Config URL is empty"))
            return
        }

        val request = Request.Builder()
            .url(url)
            .header("Cache-Control", "no-cache")
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onResult(null, e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) {
                        onResult(null, IOException("Server returned code ${response.code}"))
                        return
                    }
                    val jsonBody = response.body?.string()
                    if (jsonBody.isNullOrBlank()) {
                        onResult(null, IOException("Empty response body"))
                        return
                    }

                    try {
                        // Validate parse before caching
                        val config = gson.fromJson(jsonBody, WifiConfig::class.java)
                        saveConfigToCache(jsonBody)
                        onResult(config, null)
                    } catch (e: Exception) {
                        onResult(null, e)
                    }
                }
            }
        })
    }
}
