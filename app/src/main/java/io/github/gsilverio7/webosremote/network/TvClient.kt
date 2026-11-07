package io.github.gsilverio7.webosremote.network

import io.github.gsilverio7.webosremote.data.TvBrand
import io.github.gsilverio7.webosremote.data.TvInstalledApp
import kotlinx.coroutines.flow.StateFlow
import java.io.File

interface TvClient {
    val brand: TvBrand
    val state: StateFlow<TvConnectionState>

    fun refreshConfigFromPrefs()
    fun setFeedback(message: String)
    suspend fun ensureConnected(timeoutMs: Long = 2800): Result<Unit>
    suspend fun togglePower(): Result<String>
    suspend fun powerOff(): Result<Unit>
    suspend fun volumeUp(): Result<Unit>
    suspend fun volumeDown(): Result<Unit>
    suspend fun channelUp(): Result<Unit>
    suspend fun channelDown(): Result<Unit>
    suspend fun setVolume(volume: Int): Result<Unit>
    suspend fun fetchAudioStatus(): Result<Pair<Int, Boolean>>
    suspend fun toggleMute(): Result<Boolean>
    suspend fun launchApp(appId: String, contentId: String = ""): Result<Unit>
    suspend fun launchYouTubeUrl(videoOrChannelUrl: String): Result<Unit>
    suspend fun searchAndLaunchYouTube(query: String): Result<Unit>
    suspend fun fetchInstalledApps(iconsDir: File): Result<List<TvInstalledApp>>
    suspend fun downloadFile(url: String, destFile: File): String?
    suspend fun sendTextInput(text: String, replace: Boolean = false, pressEnter: Boolean = false): Result<Unit>
    suspend fun sendImeEnter(): Result<Unit>
    suspend fun deleteTextCharacters(count: Int = 1): Result<Unit>
    suspend fun sendButton(buttonName: String): Result<Unit>
    fun disconnect()
}
