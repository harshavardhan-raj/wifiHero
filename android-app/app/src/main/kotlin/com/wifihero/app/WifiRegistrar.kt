package com.wifihero.app

import android.content.Context
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.wifihero.app.model.WifiConfig
import com.wifihero.app.model.WifiNetworkGroup

class WifiRegistrar(private val context: Context) {

    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val prefs = context.getSharedPreferences("wifi_registrar_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "WifiRegistrar"
        private const val KEY_REGISTERED_COUNT = "registered_count"
        private const val KEY_REGISTERED_VERSION = "registered_config_version"
        private const val KEY_REGISTERED_SSIDS = "registered_ssids"
    }

    var registeredCount: Int
        get() = prefs.getInt(KEY_REGISTERED_COUNT, 0)
        private set(value) = prefs.edit().putInt(KEY_REGISTERED_COUNT, value).apply()

    var registeredConfigVersion: Int
        get() = prefs.getInt(KEY_REGISTERED_VERSION, -1)
        private set(value) = prefs.edit().putInt(KEY_REGISTERED_VERSION, value).apply()

    private var registeredSsids: Set<String>
        get() = prefs.getStringSet(KEY_REGISTERED_SSIDS, emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_REGISTERED_SSIDS, value).apply()

    /**
     * Registers all WiFi networks defined in the config.
     * Removes any previously registered suggestions first to avoid duplicates or outdated passwords.
     */
    fun registerWifiNetworks(config: WifiConfig): RegistrationResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return RegistrationResult.Failure("Android 10 (API 29) or higher is required for network suggestions.")
        }

        try {
            // 1. Remove previous registrations
            removePreviousSuggestions()

            // 2. Generate and build suggestions
            val suggestions = mutableListOf<WifiNetworkSuggestion>()
            val ssidsToRegister = mutableSetOf<String>()

            for (group in config.wifiNetworks) {
                val groupSuggestions = buildSuggestionsForGroup(group)
                suggestions.addAll(groupSuggestions.first)
                ssidsToRegister.addAll(groupSuggestions.second)
            }

            if (suggestions.isEmpty()) {
                return RegistrationResult.Failure("No valid rooms or bands configured in the profile.")
            }

            Log.d(TAG, "Adding ${suggestions.size} network suggestions to the system")

            // 3. Register with OS (in batches of 100 to ensure reliability and avoid OS IPC limits)
            val batchSize = 100
            var successCount = 0
            for (i in 0 until suggestions.size step batchSize) {
                val batch = suggestions.subList(i, minOf(i + batchSize, suggestions.size))
                val status = wifiManager.addNetworkSuggestions(batch)
                
                if (status == WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS) {
                    successCount += batch.size
                } else {
                    Log.e(TAG, "Failed to register batch starting at $i, status code: $status")
                    return RegistrationResult.Failure("Failed to register WiFi suggestions. OS error code: $status")
                }
            }

            // 4. Save state
            registeredCount = successCount
            registeredConfigVersion = config.version
            registeredSsids = ssidsToRegister

            return RegistrationResult.Success(successCount)
        } catch (e: Exception) {
            Log.e(TAG, "Exception during WiFi registration", e)
            return RegistrationResult.Failure(e.localizedMessage ?: "Unknown error occurred")
        }
    }

    /**
     * Removes all registered WiFi suggestions.
     */
    fun unregisterAllNetworks(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        
        val removed = removePreviousSuggestions()
        if (removed) {
            registeredCount = 0
            registeredConfigVersion = -1
            registeredSsids = emptySet()
        }
        return removed
    }

    private fun removePreviousSuggestions(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        
        val ssids = registeredSsids
        if (ssids.isEmpty()) return true

        Log.d(TAG, "Removing ${ssids.size} previous suggestions")
        
        // Reconstruct basic suggestions with empty/dummy configs just to remove them by SSID
        val suggestionsToRemove = ssids.map { ssid ->
            WifiNetworkSuggestion.Builder()
                .setSsid(ssid)
                .build()
        }

        val batchSize = 100
        var allSuccess = true
        for (i in 0 until suggestionsToRemove.size step batchSize) {
            val batch = suggestionsToRemove.subList(i, minOf(i + batchSize, suggestionsToRemove.size))
            val status = wifiManager.removeNetworkSuggestions(batch)
            if (status != WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS) {
                Log.w(TAG, "Failed to remove suggestion batch, status code: $status")
                allSuccess = false
            }
        }
        
        return allSuccess
    }

    private fun buildSuggestionsForGroup(group: WifiNetworkGroup): Pair<List<WifiNetworkSuggestion>, List<String>> {
        val suggestions = mutableListOf<WifiNetworkSuggestion>()
        val ssids = mutableListOf<String>()

        for (floorObj in group.floors) {
            val start = minOf(floorObj.startRoom, floorObj.endRoom)
            val end = maxOf(floorObj.startRoom, floorObj.endRoom)
            
            for (roomNum in start..end) {
                for (band in group.bands) {
                    val ssid = "${group.ssidPrefix}-$band-$roomNum"
                    
                    val builder = WifiNetworkSuggestion.Builder()
                        .setSsid(ssid)
                        
                    if (group.security == "WPA2" || group.security == "WPA3") {
                        if (group.password.isNotBlank()) {
                            builder.setWpa2Passphrase(group.password)
                        }
                    }
                    
                    // Prioritize this network if the user is near it
                    builder.setIsAppInteractionRequired(false) // Auto-connect silently

                    suggestions.add(builder.build())
                    ssids.add(ssid)
                }
            }
        }
        return Pair(suggestions, ssids)
    }

    sealed class RegistrationResult {
        data class Success(val count: Int) : RegistrationResult()
        data class Failure(val errorMessage: String) : RegistrationResult()
    }
}
