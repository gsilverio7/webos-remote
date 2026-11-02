package io.github.gsilverio7.webosremote.ui

import android.app.Application
import androidx.annotation.DrawableRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.gsilverio7.webosremote.R
import io.github.gsilverio7.webosremote.data.ShortcutActionType
import io.github.gsilverio7.webosremote.data.ShortcutSlotConfig
import io.github.gsilverio7.webosremote.data.TvInstalledApp
import io.github.gsilverio7.webosremote.data.TvPreferences
import io.github.gsilverio7.webosremote.network.LgWebOsClient
import io.github.gsilverio7.webosremote.network.TvConnectionState
import io.github.gsilverio7.webosremote.network.TvSsdpDiscovery
import io.github.gsilverio7.webosremote.network.YouTubeLiveResolver
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

data class TvAppShortcut(
    val slotIndex: Int,
    val id: String,
    val label: String,
    @DrawableRes val iconRes: Int,
    val actionType: ShortcutActionType,
    val target: String,
    val customIconPath: String? = null
)

class RemoteViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val preferences = TvPreferences(appContext)
    private val tvClient = LgWebOsClient(preferences)
    private val iconsDir = File(appContext.filesDir, "tv_icons").apply { mkdirs() }

    val tvState: StateFlow<TvConnectionState> = tvClient.state

    private val _hapticEnabled = MutableStateFlow(preferences.getConfig().hapticEnabled)
    val hapticEnabled: StateFlow<Boolean> = _hapticEnabled.asStateFlow()

    private val _irLedBlinking = MutableStateFlow(false)
    val irLedBlinking: StateFlow<Boolean> = _irLedBlinking.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _installedApps = MutableStateFlow(buildInitialAppsList())
    val installedApps: StateFlow<List<TvInstalledApp>> = _installedApps.asStateFlow()

    private val _isLoadingApps = MutableStateFlow(false)
    val isLoadingApps: StateFlow<Boolean> = _isLoadingApps.asStateFlow()

    private val _shortcuts = MutableStateFlow(loadUiShortcuts())
    val shortcuts: StateFlow<List<TvAppShortcut>> = _shortcuts.asStateFlow()

    private var hasSyncedAppsThisSession = false

    companion object {
        @DrawableRes
        fun resolveBundledIcon(targetOrId: String, label: String = ""): Int {
            val key = "${targetOrId.lowercase()} ${label.lowercase()}"
            return when {
                key.contains("amazon") || key.contains("prime") -> R.drawable.logo_primevideo
                key.contains("wbd") || key.contains("hbomax") || key.contains("max") -> R.drawable.logo_hbomax
                key.contains("disney") -> R.drawable.logo_disneyplus
                key.contains("globoplay") -> R.drawable.logo_globoplay
                key.contains("sbt") -> R.drawable.logo_sbt
                key.contains("jovempan") || key.contains("lgchannels") -> R.drawable.logo_jovempan
                key.contains("ippinheiros") || key.contains("ipp") -> R.drawable.logo_ipptv
                key.contains("moonlight") || key.contains("limelight") -> R.drawable.logo_moonlight
                key.contains("youtube") -> R.drawable.logo_youtube
                else -> 0
            }
        }

        private fun defaultKnownTvApps(): List<TvInstalledApp> = listOf(
            TvInstalledApp("youtube.leanback.v4", "YouTube"),
            TvInstalledApp("netflix", "Netflix"),
            TvInstalledApp("amazon", "Prime Video"),
            TvInstalledApp("com.wbd.stream", "Max"),
            TvInstalledApp("com.disney.disneyplus-prod", "Disney+"),
            TvInstalledApp("globoplaywebos", "Globoplay"),
            TvInstalledApp("com.limelight.webos", "Moonlight"),
            TvInstalledApp("com.webos.app.lgchannels", "LG Channels / Jovem Pan"),
            TvInstalledApp("spotify-beehive", "Spotify"),
            TvInstalledApp("com.apple.appletv", "Apple TV"),
            TvInstalledApp("com.webos.app.browser", "Navegador Web")
        )
    }

    private fun buildInitialAppsList(): List<TvInstalledApp> {
        val cached = preferences.getCachedInstalledApps()
        if (cached.isNotEmpty()) return cached
        return defaultKnownTvApps()
    }

    private fun loadUiShortcuts(): List<TvAppShortcut> {
        val cachedAppsById = preferences.getCachedInstalledApps().associateBy { it.appId }
        return preferences.getShortcuts().map { cfg ->
            val iconPath = cfg.customIconPath
                ?: cachedAppsById[cfg.target]?.localIconPath
            val fallbackRes = resolveBundledIcon(cfg.target, cfg.label).let { res ->
                if (res == 0 && cfg.actionType == ShortcutActionType.YOUTUBE_LIVE) {
                    R.drawable.logo_youtube
                } else res
            }
            TvAppShortcut(
                slotIndex = cfg.slotIndex,
                id = cfg.id,
                label = cfg.label,
                iconRes = fallbackRes,
                actionType = cfg.actionType,
                target = cfg.target,
                customIconPath = iconPath
            )
        }
    }

    init {
        // Monitoramento periódico de conexão com a TV
        viewModelScope.launch {
            while (isActive) {
                val state = tvState.value
                if (!state.connected) {
                    tvClient.ensureConnected(timeoutMs = 1800)
                } else if (state.paired) {
                    tvClient.fetchAudioStatus()
                    if (!hasSyncedAppsThisSession) {
                        hasSyncedAppsThisSession = true
                        refreshInstalledAppsSilent()
                    }
                }
                delay(10_000L)
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
                if (current.connected && current.paired) "Desligando TV..." else "Enviando Wake-on-LAN..."
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

    fun onChannelUp() {
        pulseIrLed()
        viewModelScope.launch {
            tvClient.setFeedback("Canal +")
            tvClient.channelUp().onFailure {
                tvClient.sendButton("CHANNELUP").onFailure { err ->
                    tvClient.setFeedback(err.message ?: "Erro ao mudar canal")
                }
            }
        }
    }

    fun onChannelDown() {
        pulseIrLed()
        viewModelScope.launch {
            tvClient.setFeedback("Canal −")
            tvClient.channelDown().onFailure {
                tvClient.sendButton("CHANNELDOWN").onFailure { err ->
                    tvClient.setFeedback(err.message ?: "Erro ao mudar canal")
                }
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

    /**
     * Sincroniza os aplicativos instalados e seus ícones diretamente da TV LG.
     */
    fun onRefreshInstalledApps() {
        viewModelScope.launch {
            _isLoadingApps.value = true
            tvClient.setFeedback("Carregando apps instalados na TV...")
            val res = tvClient.fetchInstalledApps(iconsDir)
            res.onSuccess { apps ->
                _installedApps.value = apps
                _shortcuts.value = loadUiShortcuts()
                tvClient.setFeedback("${apps.size} apps carregados da TV!")
            }.onFailure { err ->
                tvClient.setFeedback(err.message ?: "Ligue a TV para carregar os apps instalados")
            }
            _isLoadingApps.value = false
        }
    }

    private suspend fun refreshInstalledAppsSilent() {
        val res = tvClient.fetchInstalledApps(iconsDir)
        res.onSuccess { apps ->
            _installedApps.value = apps
            _shortcuts.value = loadUiShortcuts()
        }
    }

    /**
     * Salva a configuração de um dos 9 botões de atalho.
     * Se for uma Live do YouTube, busca automaticamente o ícone/avatar do canal!
     */
    fun onSaveShortcut(
        slotIndex: Int,
        label: String,
        actionType: ShortcutActionType,
        target: String,
        customIconPath: String?
    ) {
        val currentList = preferences.getShortcuts().toMutableList()
        val cleanTarget = if (actionType == ShortcutActionType.YOUTUBE_LIVE &&
            !target.startsWith("http") &&
            !target.startsWith("@")
        ) {
            "@${target.trim()}"
        } else {
            target.trim()
        }

        val updatedSlot = ShortcutSlotConfig(
            slotIndex = slotIndex,
            id = "slot_${slotIndex}_${cleanTarget.lowercase().replace(Regex("[^a-z0-9]"), "")}",
            label = label.trim(),
            actionType = actionType,
            target = cleanTarget,
            customIconPath = customIconPath
        )

        val idx = currentList.indexOfFirst { it.slotIndex == slotIndex }
        if (idx >= 0) {
            currentList[idx] = updatedSlot
        } else {
            currentList.add(updatedSlot)
        }

        preferences.saveShortcuts(currentList)
        _shortcuts.value = loadUiShortcuts()
        tvClient.setFeedback("Atalho \"${label.trim()}\" salvo!")

        // Se for canal de Live do YouTube e ainda não tiver ícone local, baixa o avatar do canal em background
        if (actionType == ShortcutActionType.YOUTUBE_LIVE) {
            viewModelScope.launch {
                val avatarUrl = YouTubeLiveResolver.fetchChannelAvatarUrl(cleanTarget)
                if (!avatarUrl.isNullOrBlank()) {
                    val safeChannel = cleanTarget.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                    val destFile = File(iconsDir, "yt_${safeChannel}.jpg")
                    val downloadedPath = tvClient.downloadFile(avatarUrl, destFile)
                    if (!downloadedPath.isNullOrBlank()) {
                        val refreshed = preferences.getShortcuts().map { slot ->
                            if (slot.slotIndex == slotIndex) {
                                slot.copy(customIconPath = downloadedPath)
                            } else slot
                        }
                        preferences.saveShortcuts(refreshed)
                        _shortcuts.value = loadUiShortcuts()
                    }
                }
            }
        }
    }

    fun onResetShortcutsToDefault() {
        preferences.resetShortcutsToDefault()
        _shortcuts.value = loadUiShortcuts()
        tvClient.setFeedback("Atalhos restaurados para o padrão")
    }

    /**
     * Teclado do Celular -> Envia texto diretamente para o campo de texto da TV.
     */
    fun onSendTextToTv(text: String, replace: Boolean = false, pressEnter: Boolean = true) {
        if (text.isEmpty()) return
        pulseIrLed()
        viewModelScope.launch {
            tvClient.setFeedback("Enviando texto para a TV...")
            tvClient.sendTextInput(text, replace = replace, pressEnter = pressEnter)
                .onSuccess {
                    tvClient.setFeedback("Texto enviado: \"$text\"")
                }
                .onFailure { err ->
                    tvClient.setFeedback(err.message ?: "Erro ao enviar texto")
                }
        }
    }

    fun onDeleteTvChar() {
        pulseIrLed()
        viewModelScope.launch {
            tvClient.deleteTextCharacters(1)
                .onSuccess { tvClient.setFeedback("⌫ Apagar na TV") }
                .onFailure { tvClient.sendButton("BACK") }
        }
    }

    fun onSendTvEnter() {
        pulseIrLed()
        viewModelScope.launch {
            tvClient.setFeedback("↵ Confirmar na TV")
            tvClient.sendImeEnter()
        }
    }

    fun onSearchYouTube(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        pulseIrLed()
        viewModelScope.launch {
            _isBusy.value = true
            tvClient.setFeedback("Buscando \"$trimmed\" no YouTube...")
            tvClient.searchAndLaunchYouTube(trimmed)
                .onSuccess {
                    tvClient.setFeedback("Reproduzindo \"$trimmed\" no YouTube!")
                }
                .onFailure { err ->
                    tvClient.setFeedback(err.message ?: "Erro ao buscar no YouTube")
                }
            _isBusy.value = false
        }
    }

    /**
     * Ditado por voz -> Envia o texto falado diretamente para o campo de busca/texto da TV.
     */
    fun onDictateTextToTv(spokenText: String) {
        onSendTextToTv(spokenText, replace = false, pressEnter = true)
    }

    /**
     * Controle por Voz / IA pelo microfone do celular:
     * Interpreta comandos naturais em português e aciona a função correspondente na TV.
     */
    fun onVoiceCommand(spokenText: String) {
        val raw = spokenText.trim()
        if (raw.isEmpty()) return
        val lower = raw.lowercase()
        pulseIrLed()

        viewModelScope.launch {
            _isBusy.value = true
            tvClient.setFeedback("Voz: \"$raw\"")

            // 1. Ligar / Desligar TV
            if (lower == "desligar" || lower.contains("desligar tv") || lower.contains("desligar a tv") || lower.contains("desliga a tv")) {
                tvClient.powerOff()
                _isBusy.value = false
                return@launch
            }
            if (lower == "ligar" || lower.contains("ligar tv") || lower.contains("ligar a tv") || lower.contains("liga a tv")) {
                val res = tvClient.togglePower()
                res.onSuccess { tvClient.setFeedback(it) }
                _isBusy.value = false
                return@launch
            }

            // 2. Mudo / Silenciar
            if (lower.contains("mudo") || lower.contains("silenciar") || lower.contains("silêncio")) {
                onMuteToggle()
                _isBusy.value = false
                return@launch
            }

            // 3. Volume para número exato (ex: "volume 20", "volume no 15")
            val volNumberMatch = Regex("""volume\s+(?:no\s+|para\s+|em\s+)?(\d{1,3})""").find(lower)
            if (volNumberMatch != null) {
                val targetVol = volNumberMatch.groupValues[1].toIntOrNull()
                if (targetVol != null) {
                    tvClient.setVolume(targetVol)
                        .onSuccess { tvClient.setFeedback("Volume ajustado para $targetVol") }
                        .onFailure { tvClient.setFeedback("Falha ao ajustar volume") }
                    _isBusy.value = false
                    return@launch
                }
            }

            // 4. Aumentar / Diminuir volume
            if (lower.contains("aumentar volume") || lower.contains("subir volume") || lower.contains("mais alto") || lower.contains("aumenta o volume")) {
                repeat(3) {
                    tvClient.volumeUp()
                    delay(120)
                }
                tvClient.fetchAudioStatus()
                tvClient.setFeedback("Volume aumentado")
                _isBusy.value = false
                return@launch
            }
            if (lower.contains("diminuir volume") || lower.contains("baixar volume") || lower.contains("abaixar volume") || lower.contains("mais baixo") || lower.contains("abaixa o volume")) {
                repeat(3) {
                    tvClient.volumeDown()
                    delay(120)
                }
                tvClient.fetchAudioStatus()
                tvClient.setFeedback("Volume diminuído")
                _isBusy.value = false
                return@launch
            }

            // 4.1 Mudar canal
            if (lower.contains("proximo canal") || lower.contains("próximo canal") || lower.contains("canal seguinte") || lower.contains("aumentar canal") || lower.contains("subir canal")) {
                onChannelUp()
                _isBusy.value = false
                return@launch
            }
            if (lower.contains("canal anterior") || lower.contains("voltar canal") || lower.contains("diminuir canal") || lower.contains("descer canal")) {
                onChannelDown()
                _isBusy.value = false
                return@launch
            }

            // 5. Navegação básica
            when (lower) {
                "voltar", "retornar" -> {
                    tvClient.sendButton("BACK")
                    _isBusy.value = false
                    return@launch
                }
                "home", "início", "inicio", "menu inicial" -> {
                    tvClient.sendButton("HOME")
                    _isBusy.value = false
                    return@launch
                }
                "ok", "confirmar", "enter" -> {
                    tvClient.sendButton("ENTER")
                    _isBusy.value = false
                    return@launch
                }
            }

            // 6. Lives no YouTube (ex: "live do sbt", "assistir cazetv ao vivo")
            if (lower.contains("live ") || lower.contains("ao vivo")) {
                val matchedShortcut = _shortcuts.value.firstOrNull { sc ->
                    sc.actionType == ShortcutActionType.YOUTUBE_LIVE &&
                        lower.contains(sc.label.lowercase())
                }
                if (matchedShortcut != null) {
                    tvClient.setFeedback("Abrindo live de ${matchedShortcut.label}...")
                    tvClient.launchYouTubeUrl(matchedShortcut.target)
                    _isBusy.value = false
                    return@launch
                }

                val channelExtract = lower
                    .replace("abrir", "")
                    .replace("assistir", "")
                    .replace("colocar", "")
                    .replace("live do", "")
                    .replace("live da", "")
                    .replace("live de", "")
                    .replace("live", "")
                    .replace("ao vivo", "")
                    .replace("no youtube", "")
                    .trim()

                if (channelExtract.isNotEmpty()) {
                    tvClient.setFeedback("Abrindo live de \"$channelExtract\" no YouTube...")
                    tvClient.launchYouTubeUrl(channelExtract)
                        .onSuccess { tvClient.setFeedback("Live de \"$channelExtract\" aberta!") }
                        .onFailure { tvClient.setFeedback("Não foi possível abrir a live") }
                    _isBusy.value = false
                    return@launch
                }
            }

            // 7. Abrir Aplicativo (atalhos configurados ou apps instalados na TV)
            if (lower.startsWith("abrir ") || lower.startsWith("abre ") || lower.startsWith("iniciar ")) {
                val appSpoken = lower
                    .removePrefix("abrir ")
                    .removePrefix("abre ")
                    .removePrefix("iniciar ")
                    .removePrefix("o ")
                    .removePrefix("a ")
                    .trim()

                // Verifica primeiro nos 9 atalhos
                val shortcutMatch = _shortcuts.value.firstOrNull { sc ->
                    sc.label.lowercase().contains(appSpoken) || appSpoken.contains(sc.label.lowercase())
                }
                if (shortcutMatch != null) {
                    onLaunchShortcut(shortcutMatch)
                    _isBusy.value = false
                    return@launch
                }

                // Verifica na lista de apps instalados da TV
                val installedMatch = _installedApps.value.firstOrNull { app ->
                    app.title.lowercase().contains(appSpoken) || appSpoken.contains(app.title.lowercase())
                }
                if (installedMatch != null) {
                    tvClient.setFeedback("Abrindo ${installedMatch.title}...")
                    tvClient.launchApp(installedMatch.appId)
                    _isBusy.value = false
                    return@launch
                }
            }

            // 8. Busca/Reprodução no YouTube (ex: "pesquisar X no youtube", "tocar X", ou termo geral)
            val ytCleaned = lower
                .removePrefix("pesquisar ")
                .removePrefix("buscar ")
                .removePrefix("procurar ")
                .removePrefix("tocar ")
                .removePrefix("reproduzir ")
                .removePrefix("colocar ")
                .removeSuffix(" no youtube")
                .trim()

            if (lower.contains("youtube") || lower.startsWith("pesquisar ") || lower.startsWith("buscar ") || lower.startsWith("tocar ")) {
                tvClient.setFeedback("Buscando \"$ytCleaned\" no YouTube...")
                tvClient.searchAndLaunchYouTube(ytCleaned)
                    .onSuccess { tvClient.setFeedback("Reproduzindo \"$ytCleaned\" na TV!") }
                    .onFailure { err -> tvClient.setFeedback(err.message ?: "Falha na busca do YouTube") }
                _isBusy.value = false
                return@launch
            }

            // 9. Caso contrário: tenta enviar o texto para o campo aberto na TV; se não houver, busca no YouTube
            val textSent = tvClient.sendTextInput(raw, replace = false, pressEnter = true)
            if (textSent.isSuccess) {
                tvClient.setFeedback("Texto falado enviado: \"$raw\"")
            } else {
                tvClient.setFeedback("Buscando \"$raw\" no YouTube...")
                tvClient.searchAndLaunchYouTube(raw)
            }
            _isBusy.value = false
        }
    }

    fun onSaveSettings(ip: String, mac: String, broadcast: String, hapticEnabled: Boolean) {
        preferences.saveConfig(ip, mac, broadcast, hapticEnabled)
        _hapticEnabled.value = hapticEnabled
        tvClient.refreshConfigFromPrefs()
        tvClient.disconnect()
        hasSyncedAppsThisSession = false
        viewModelScope.launch {
            tvClient.setFeedback("Reconectando em ${ip.trim()}...")
            val res = tvClient.ensureConnected(2500)
            if (res.isSuccess) {
                tvClient.setFeedback("Conectado à TV LG (${ip.trim()})")
                refreshInstalledAppsSilent()
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
