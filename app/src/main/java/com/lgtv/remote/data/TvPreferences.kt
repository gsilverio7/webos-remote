package com.lgtv.remote.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

enum class ShortcutActionType {
    APP,
    YOUTUBE_LIVE
}

data class ShortcutSlotConfig(
    val slotIndex: Int,
    val id: String,
    val label: String,
    val actionType: ShortcutActionType,
    val target: String,
    val customIconPath: String? = null
)

data class TvInstalledApp(
    val appId: String,
    val title: String,
    val iconUrl: String = "",
    val localIconPath: String? = null
)

data class TvConfig(
    val ipAddress: String,
    val macAddress: String,
    val broadcastAddress: String,
    val clientKey: String,
    val hapticEnabled: Boolean
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
        private const val KEY_HAPTIC_ENABLED = "tv_haptic_enabled"
        private const val KEY_SHORTCUTS_JSON = "tv_shortcuts_v1"
        private const val KEY_INSTALLED_APPS_JSON = "tv_installed_apps_v1"

        fun defaultShortcuts(): List<ShortcutSlotConfig> = listOf(
            ShortcutSlotConfig(0, "prime", "Prime", ShortcutActionType.APP, "amazon"),
            ShortcutSlotConfig(1, "max", "Max", ShortcutActionType.APP, "com.wbd.stream"),
            ShortcutSlotConfig(2, "disney", "Disney+", ShortcutActionType.APP, "com.disney.disneyplus-prod"),
            ShortcutSlotConfig(3, "globoplay", "Globoplay", ShortcutActionType.APP, "globoplaywebos"),
            ShortcutSlotConfig(4, "sbt", "SBT", ShortcutActionType.YOUTUBE_LIVE, "@sbt"),
            ShortcutSlotConfig(5, "jovempan", "Jovem Pan", ShortcutActionType.APP, "com.webos.app.lgchannels"),
            ShortcutSlotConfig(6, "ipp", "IPP TV", ShortcutActionType.YOUTUBE_LIVE, "@ippinheiros"),
            ShortcutSlotConfig(7, "youtube", "YouTube", ShortcutActionType.APP, "youtube.leanback.v4"),
            ShortcutSlotConfig(8, "moonlight", "Moonlight", ShortcutActionType.APP, "com.limelight.webos")
        )
    }

    fun getConfig(): TvConfig {
        return TvConfig(
            ipAddress = prefs.getString(KEY_IP, DEFAULT_IP)?.trim().orEmpty().ifEmpty { DEFAULT_IP },
            macAddress = prefs.getString(KEY_MAC, DEFAULT_MAC)?.trim().orEmpty().ifEmpty { DEFAULT_MAC },
            broadcastAddress = prefs.getString(KEY_BROADCAST, DEFAULT_BROADCAST)?.trim().orEmpty().ifEmpty { DEFAULT_BROADCAST },
            clientKey = prefs.getString(KEY_CLIENT_KEY, DEFAULT_CLIENT_KEY)?.trim().orEmpty(),
            hapticEnabled = prefs.getBoolean(KEY_HAPTIC_ENABLED, true)
        )
    }

    fun saveConfig(
        ipAddress: String,
        macAddress: String,
        broadcastAddress: String,
        hapticEnabled: Boolean
    ) {
        prefs.edit()
            .putString(KEY_IP, ipAddress.trim())
            .putString(KEY_MAC, macAddress.trim().uppercase())
            .putString(KEY_BROADCAST, broadcastAddress.trim().ifEmpty { DEFAULT_BROADCAST })
            .putBoolean(KEY_HAPTIC_ENABLED, hapticEnabled)
            .apply()
    }

    fun setHapticEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HAPTIC_ENABLED, enabled).apply()
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

    fun getShortcuts(): List<ShortcutSlotConfig> {
        val raw = prefs.getString(KEY_SHORTCUTS_JSON, null)
        val defaults = defaultShortcuts()
        if (raw.isNullOrBlank()) return defaults

        return runCatching {
            val arr = JSONArray(raw)
            val byIndex = mutableMapOf<Int, ShortcutSlotConfig>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val idx = obj.optInt("slotIndex", i).coerceIn(0, 8)
                val typeStr = obj.optString("actionType", ShortcutActionType.APP.name)
                val actionType = runCatching { ShortcutActionType.valueOf(typeStr) }
                    .getOrDefault(ShortcutActionType.APP)
                val iconPath = obj.optString("customIconPath", "").takeIf { it.isNotBlank() }
                byIndex[idx] = ShortcutSlotConfig(
                    slotIndex = idx,
                    id = obj.optString("id", "slot_$idx"),
                    label = obj.optString("label", "Atalho ${idx + 1}"),
                    actionType = actionType,
                    target = obj.optString("target", ""),
                    customIconPath = iconPath
                )
            }
            defaults.map { def -> byIndex[def.slotIndex] ?: def }
        }.getOrDefault(defaults)
    }

    fun saveShortcuts(shortcuts: List<ShortcutSlotConfig>) {
        val arr = JSONArray()
        shortcuts.sortedBy { it.slotIndex }.forEach { item ->
            arr.put(
                JSONObject().apply {
                    put("slotIndex", item.slotIndex)
                    put("id", item.id)
                    put("label", item.label)
                    put("actionType", item.actionType.name)
                    put("target", item.target)
                    if (!item.customIconPath.isNullOrBlank()) {
                        put("customIconPath", item.customIconPath)
                    }
                }
            )
        }
        prefs.edit().putString(KEY_SHORTCUTS_JSON, arr.toString()).apply()
    }

    fun resetShortcutsToDefault(): List<ShortcutSlotConfig> {
        prefs.edit().remove(KEY_SHORTCUTS_JSON).apply()
        return defaultShortcuts()
    }

    fun getCachedInstalledApps(): List<TvInstalledApp> {
        val raw = prefs.getString(KEY_INSTALLED_APPS_JSON, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            val list = mutableListOf<TvInstalledApp>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val appId = obj.optString("appId").trim()
                val title = obj.optString("title").trim()
                if (appId.isNotEmpty() && title.isNotEmpty()) {
                    list.add(
                        TvInstalledApp(
                            appId = appId,
                            title = title,
                            iconUrl = obj.optString("iconUrl", ""),
                            localIconPath = obj.optString("localIconPath", "").takeIf { it.isNotBlank() }
                        )
                    )
                }
            }
            list
        }.getOrDefault(emptyList())
    }

    fun saveCachedInstalledApps(apps: List<TvInstalledApp>) {
        val arr = JSONArray()
        apps.forEach { app ->
            arr.put(
                JSONObject().apply {
                    put("appId", app.appId)
                    put("title", app.title)
                    put("iconUrl", app.iconUrl)
                    if (!app.localIconPath.isNullOrBlank()) {
                        put("localIconPath", app.localIconPath)
                    }
                }
            )
        }
        prefs.edit().putString(KEY_INSTALLED_APPS_JSON, arr.toString()).apply()
    }
}
