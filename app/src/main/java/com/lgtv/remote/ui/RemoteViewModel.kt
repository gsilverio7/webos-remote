package com.lgtv.remote.ui

import android.app.Application
import androidx.annotation.DrawableRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lgtv.remote.R
import com.lgtv.remote.data.TvPreferences
import com.lgtv.remote.network.LgWebOsClient
import com.lgtv.remote.network.TvConnectionState
import com.lgtv.remote.network.TvSsdpDiscovery
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class ShortcutActionType {
    APP,
    YOUTUBE_LIVE
}

data class TvAppShortcut(
    val id: String,
    val label: String,
    @DrawableRes val iconRes: Int,
    val actionType: ShortcutActionType,
    val target: String
)

class RemoteViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = TvPreferences(application.applicationContext)
    private val tvClient = LgWebOsClient(preferences)

    val tvState: StateFlow<TvConnectionState> = tvClient.state

    private val _irLedBlinking = MutableStateFlow(false)
    val irLedBlinking: StateFlow<Boolean> = _irLedBlinking.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    val appShortcuts: List<List<TvAppShortcut>> = listOf(
        listOf(
            TvAppShortcut("prime", "Prime", R.drawable.logo_primevideo, ShortcutActionType.APP, "amazon"),
            TvAppShortcut("max", "Max", R.drawable.logo_hbomax, ShortcutActionType.APP, "com.wbd.stream"),
            TvAppShortcut("disney", "Disney+", R.drawable.logo_disneyplus, ShortcutActionType.APP, "com.disney.disneyplus-prod")
        ),
        listOf(
            TvAppShortcut("globoplay", "Globoplay", R.drawable.logo_globoplay, ShortcutActionType.APP, "globoplaywebos"),
            TvAppShortcut("sbt", "SBT", R.drawable.logo_sbt, ShortcutActionType.YOUTUBE_LIVE, "https://www.youtube.com/@sbt/live"),
            TvAppShortcut("jovempan", "Jovem Pan", R.drawable.logo_jovempan, ShortcutActionType.APP, "com.webos.app.lgchannels")
        ),
        listOf(
            TvAppShortcut("ipp", "IPP TV", R.drawable.logo_ipptv, ShortcutActionType.YOUTUBE_LIVE, "https://www.youtube.com/@ippinheiros/live"),
            TvAppShortcut("youtube", "YouTube", R.drawable.logo_youtube, ShortcutActionType.APP, "youtube.leanback.v4"),
            TvAppShortcut("moonlight", "Moonlight", R.drawable.logo_moonlight, ShortcutActionType.APP, "com.limelight.webos")
        )
    )

    init {
        // Inicia monitoramento periódico de conexão com a TV
        viewModelScope.launch {
            while (isActive) {
                val state = tvState.value
                if (!state.connected) {
                    tvClient.ensureConnected(timeoutMs = 2000)
                } else if (state.paired) {
                    tvClient.fetchAudioStatus()
                }
                delay(12_000L)
            }
        }
    }

    private fun pulseIrLed() {
        viewModelScope.launch {
            _irLedBlinking.value = true
            delay(180)
            _irLedBlinking.value = false
        }
    }

    fun onPowerToggle() {
        pulseIrLed()
        viewModelScope.launch {
            _isBusy.value = true
            val current = tvState.value
            tvClient.setFeedback(
                if (current.connected && current.paired) "Desligando TV..." else "Ligando TV..."
            )
            val result = tvClient.togglePower()
            result.onSuccess { msg ->
                tvClient.setFeedback(msg)
            }.onFailure { err ->
                tvClient.setFeedback(err.message ?: "Falha ao acionar energia da TV")
            }
            _isBusy.value = false
        }
    }

    fun onVolumeUp() {
        pulseIrLed()
        viewModelScope.launch {
            tvClient.setFeedback("Volume +")
            tvClient.volumeUp().onSuccess {
                tvClient.fetchAudioStatus()
            }.onFailure { err ->
                tvClient.setFeedback(err.message ?: "Erro ao aumentar volume")
            }
        }
    }

    fun onVolumeDown() {
        pulseIrLed()
        viewModelScope.launch {
            tvClient.setFeedback("Volume −")
            tvClient.volumeDown().onSuccess {
                tvClient.fetchAudioStatus()
            }.onFailure { err ->
                tvClient.setFeedback(err.message ?: "Erro ao diminuir volume")
            }
        }
    }

    fun onMuteToggle() {
        pulseIrLed()
        viewModelScope.launch {
            val res = tvClient.toggleMute()
            res.onSuccess { muted ->
                tvClient.setFeedback(if (muted) "Mudo ativado" else "Mudo desativado")
            }.onFailure { err ->
                tvClient.setFeedback(err.message ?: "Erro ao alternar mudo")
            }
        }
    }

    fun onNavigationButton(button: String, label: String) {
        pulseIrLed()
        viewModelScope.launch {
            tvClient.setFeedback(label)
            tvClient.sendButton(button).onFailure { err ->
                tvClient.setFeedback(err.message ?: "Falha ao enviar $label")
            }
        }
    }

    fun onLaunchShortcut(shortcut: TvAppShortcut) {
        pulseIrLed()
        viewModelScope.launch {
            _isBusy.value = true
            tvClient.setFeedback("Abrindo ${shortcut.label}...")
            val result = when (shortcut.actionType) {
                ShortcutActionType.APP -> tvClient.launchApp(shortcut.target)
                ShortcutActionType.YOUTUBE_LIVE -> tvClient.launchYouTubeUrl(shortcut.target)
            }
            result.onSuccess {
                tvClient.setFeedback("${shortcut.label} aberto na TV")
            }.onFailure { err ->
                tvClient.setFeedback(err.message ?: "Falha ao abrir ${shortcut.label}")
            }
            _isBusy.value = false
        }
    }

    fun onSaveSettings(ip: String, mac: String, broadcast: String) {
        preferences.saveConfig(ip, mac, broadcast)
        tvClient.refreshConfigFromPrefs()
        tvClient.disconnect()
        viewModelScope.launch {
            tvClient.setFeedback("Reconectando em ${ip.trim()}...")
            val res = tvClient.ensureConnected(3000)
            if (res.isSuccess) {
                tvClient.setFeedback("Conectado à TV LG (${ip.trim()})")
            } else {
                tvClient.setFeedback("Configurações salvas (TV em standby)")
            }
        }
    }

    fun onDiscoverTv(onDiscovered: (String) -> Unit) {
        viewModelScope.launch {
            tvClient.setFeedback("Buscando TV LG via SSDP...")
            val found = TvSsdpDiscovery.discoverLgTv(3000)
            if (found != null) {
                preferences.saveIpAddress(found.ip)
                tvClient.refreshConfigFromPrefs()
                tvClient.setFeedback("TV encontrada: ${found.friendlyName} (${found.ip})")
                onDiscovered(found.ip)
            } else {
                tvClient.setFeedback("Nenhuma TV LG encontrada via SSDP (verifique se está ligada)")
            }
        }
    }
}
