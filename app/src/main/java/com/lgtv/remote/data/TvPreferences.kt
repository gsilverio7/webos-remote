package com.lgtv.remote.data

import android.content.Context
import android.content.SharedPreferences

data class TvConfig(
    val ipAddress: String,
    val macAddress: String,
    val broadcastAddress: String,
    val clientKey: String
)

class TvPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("lg_tv_remote_prefs", Context.MODE_PRIVATE)

    companion object {
        const val DEFAULT_IP = "192.168.0.4"
        const val DEFAULT_MAC = "D0:CD:BF:59:F9:2E"
        const val DEFAULT_BROADCAST = "192.168.0.255"
        const val DEFAULT_CLIENT_KEY = "46ca7bede69c3d9fc08f27fe3d93b0a9"

        private const val KEY_IP = "tv_ip_address"
        private const val KEY_MAC = "tv_mac_address"
        private const val KEY_BROADCAST = "tv_broadcast_address"
        private const val KEY_CLIENT_KEY = "tv_client_key"
    }

    fun getConfig(): TvConfig {
        return TvConfig(
            ipAddress = prefs.getString(KEY_IP, DEFAULT_IP)?.trim().orEmpty().ifEmpty { DEFAULT_IP },
            macAddress = prefs.getString(KEY_MAC, DEFAULT_MAC)?.trim().orEmpty().ifEmpty { DEFAULT_MAC },
            broadcastAddress = prefs.getString(KEY_BROADCAST, DEFAULT_BROADCAST)?.trim().orEmpty().ifEmpty { DEFAULT_BROADCAST },
            clientKey = prefs.getString(KEY_CLIENT_KEY, DEFAULT_CLIENT_KEY)?.trim().orEmpty()
        )
    }

    fun saveConfig(ipAddress: String, macAddress: String, broadcastAddress: String) {
        prefs.edit()
            .putString(KEY_IP, ipAddress.trim())
            .putString(KEY_MAC, macAddress.trim().uppercase())
            .putString(KEY_BROADCAST, broadcastAddress.trim().ifEmpty { DEFAULT_BROADCAST })
            .apply()
    }

    fun saveIpAddress(ipAddress: String) {
        if (ipAddress.isNotBlank()) {
            prefs.edit().putString(KEY_IP, ipAddress.trim()).apply()
        }
    }

    fun saveClientKey(clientKey: String) {
        if (clientKey.isNotBlank()) {
            prefs.edit().putString(KEY_CLIENT_KEY, clientKey.trim()).apply()
        }
    }
}
