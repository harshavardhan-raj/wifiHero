package com.wifihero.app.model

import com.google.gson.annotations.SerializedName

data class WifiConfig(
    @SerializedName("version") val version: Int,
    @SerializedName("lastUpdated") val lastUpdated: String,
    @SerializedName("wifiNetworks") val wifiNetworks: List<WifiNetworkGroup>,
    @SerializedName("appUpdate") val appUpdate: AppUpdate
)

data class WifiNetworkGroup(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("ssidPrefix") val ssidPrefix: String,
    @SerializedName("bands") val bands: List<String>,
    @SerializedName("password") val password: String,
    @SerializedName("security") val security: String, // e.g. "WPA2"
    @SerializedName("floors") val floors: List<FloorRange>
)

data class FloorRange(
    @SerializedName("floor") val floor: Int,
    @SerializedName("startRoom") val startRoom: Int,
    @SerializedName("endRoom") val endRoom: Int
)

data class AppUpdate(
    @SerializedName("latestVersion") val latestVersion: String,
    @SerializedName("apkUrl") val apkUrl: String,
    @SerializedName("changelog") val changelog: String
)
