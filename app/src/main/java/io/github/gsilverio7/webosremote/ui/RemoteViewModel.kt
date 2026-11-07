package io.github.gsilverio7.webosremote.ui

import android.app.Application
import androidx.annotation.DrawableRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.gsilverio7.webosremote.R
import io.github.gsilverio7.webosremote.data.ShortcutActionType
import io.github.gsilverio7.webosremote.data.ShortcutSlotConfig
import io.github.gsilverio7.webosremote.data.TvBrand
import io.github.gsilverio7.webosremote.data.TvInstalledApp
import io.github.gsilverio7.webosremote.data.TvPreferences
import io.github.gsilverio7.webosremote.network.LgWebOsClient
import io.github.gsilverio7.webosremote.network.SamsungTizenClient
import io.github.gsilverio7.webosremote.network.TvClient
import io.github.gsilverio7.webosremote.network.TvConnectionState
import io.github.gsilverio7.webosremote.network.TvSsdpDiscovery
import io.github.gsilverio7.webosremote.network.YouTubeLiveResolver
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
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

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val preferences = TvPreferences(appContext)

    private val lgClient = LgWebOsClient(preferences)
    private val samsungClient = SamsungTizenClient(preferences)

    private val lgIconsDir = File(appContext.filesDir, "tv_icons").apply { mkdirs() }
    private val samsungIconsDir = File(appContext.filesDir, "tv_icons_samsung").apply { mkdirs() }

    private val _activeBrand = MutableStateFlow(preferences.getActiveBrand())
    val activeBrand: StateFlow<TvBrand> = _activeBrand.asStateFlow()

    private val activeClient: TvClient
        get() = clientFor(_activeBrand.value)

    private fun clientFor(brand: TvBrand): TvClient = when (brand) {
        TvBrand.LG -> lgClient
        TvBrand.SAMSUNG -> samsungClient
    }

    private fun iconsDirFor(brand: TvBrand): File = when (brand) {
        TvBrand.LG -> lgIconsDir
        TvBrand.SAMSUNG -> samsungIconsDir
    }

    fun tvStateFor(brand: TvBrand): StateFlow<TvConnectionState> = clientFor(brand).state

    val tvState: StateFlow<TvConnectionState> = _activeBrand
        .flatMapLatest { brand -> clientFor(brand).state }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = clientFor(_activeBrand.value).state.value
        )

    private val _hapticEnabled = MutableStateFlow(preferences.getConfig(_activeBrand.value).hapticEnabled)
    val hapticEnabled: StateFlow<Boolean> = _hapticEnabled.asStateFlow()

    private val _hardwareVolumeKeysEnabled = MutableStateFlow(preferences.getConfig(_activeBrand.value).hardwareVolumeKeysEnabled)
    val hardwareVolumeKeysEnabled: StateFlow<Boolean> = _hardwareVolumeKeysEnabled.asStateFlow()

    private val _irLedBlinking = MutableStateFlow(false)
    val irLedBlinking: StateFlow<Boolean> = _irLedBlinking.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _installedApps = MutableStateFlow(buildInitialAppsList(_activeBrand.value))
    val installedApps: StateFlow<List<TvInstalledApp>> = _installedApps.asStateFlow()

    private val _isLoadingApps = MutableStateFlow(false)
    val isLoadingApps: StateFlow<Boolean> = _isLoadingApps.asStateFlow()

    private val _lgShortcuts = MutableStateFlow(loadUiShortcuts(TvBrand.LG))
    private val _samsungShortcuts = MutableStateFlow(loadUiShortcuts(TvBrand.SAMSUNG))

    fun shortcutsFor(brand: TvBrand): StateFlow<List<TvAppShortcut>> = when (brand) {
        TvBrand.LG -> _lgShortcuts.asStateFlow()
        TvBrand.SAMSUNG -> _samsungShortcuts.asStateFlow()
    }

    val shortcuts: StateFlow<List<TvAppShortcut>> = _activeBrand
        .flatMapLatest { brand -> shortcutsFor(brand) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = loadUiShortcuts(_activeBrand.value)
        )

    private val syncedBrandsThisSession = mutableSetOf<TvBrand>()

    companion object {
        @DrawableRes
        fun resolveBundledIcon(targetOrId: String, label: String = ""): Int {
            val key = "${targetOrId.lowercase()} ${label.lowercase()}"
            return when {
                key.contains("amazon") || key.contains("prime") || key.contains("3201910019365") -> R.drawable.logo_primevideo
                key.contains("wbd") || key.contains("hbomax") || key.contains("max") || key.contains("3202301029760") -> R.drawable.logo_hbomax
                key.contains("disney") || key.contains("3201901017640") -> R.drawable.logo_disneyplus
                key.contains("globoplay") || key.contains("3201908019041") -> R.drawable.logo_globoplay
                key.contains("sbt") -> R.drawable.logo_sbt
                key.contains("jovempan") || key.contains("lgchannels") || key.contains("tvplus") -> R.drawable.logo_jovempan
                key.contains("ippinheiros") || key.contains("ipp") -> R.drawable.logo_ipptv
                key.contains("moonlight") || key.contains("limelight") -> R.drawable.logo_moonlight
                key.contains("youtube") || key.contains("111299001912") -> R.drawable.logo_youtube
                else -> 0
            }
        }

        private fun defaultKnownTvApps(brand: TvBrand): List<TvInstalledApp> = when (brand) {
            TvBrand.LG -> listOf(
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
            TvBrand.SAMSUNG -> listOf(
                TvInstalledApp("111299001912", "YouTube"),
                TvInstalledApp("3201907018807", "Netflix"),
                TvInstalledApp("3201910019365", "Prime Video"),
                TvInstalledApp("3202301029760", "Max"),
                TvInstalledApp("3201901017640", "Disney+"),
                TvInstalledApp("3201908019041", "Globoplay"),
                TvInstalledApp("com.limelight.tizen", "Moonlight"),
                TvInstalledApp("org.tizen.tvplus", "Samsung TV Plus / Jovem Pan"),
                TvInstalledApp("3201606009684", "Spotify"),
                TvInstalledApp("3201807016597", "Apple TV"),
                TvInstalledApp("org.tizen.browser", "Samsung Internet")
            )
        }
    }

    private fun buildInitialAppsList(brand: TvBrand): List<TvInstalledApp> {
        val cached = preferences.getCachedInstalledApps(brand)
        if (cached.isNotEmpty()) return cached
        return defaultKnownTvApps(brand)
    }

    private fun loadUiShortcuts(brand: TvBrand): List<TvAppShortcut> {
        val cachedAppsById = preferences.getCachedInstalledApps(brand).associateBy { it.appId }
        return preferences.getShortcuts(brand).map { cfg ->
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

    private fun refreshShortcutsState(brand: TvBrand) {
        when (brand) {
            TvBrand.LG -> _lgShortcuts.value = loadUiShortcuts(TvBrand.LG)
            TvBrand.SAMSUNG -> _samsungShortcuts.value = loadUiShortcuts(TvBrand.SAMSUNG)
        }
    }

    fun onSelectBrand(brand: TvBrand) {
        if (_activeBrand.value == brand) return
        _activeBrand.value = brand
        preferences.setActiveBrand(brand)
        val cfg = preferences.getConfig(brand)
        _hapticEnabled.value = cfg.hapticEnabled
        _hardwareVolumeKeysEnabled.value = cfg.hardwareVolumeKeysEnabled
        _installedApps.value = buildInitialAppsList(brand)
        refreshShortcutsState(brand)

        viewModelScope.launch {
            val client = clientFor(brand)
            val state = client.state.value
            if (!state.connected) {
                client.ensureConnected(timeoutMs = 1800)
            }
        }
    }

    init {
        // Monitoramento periódico de conexão com a TV ativa
        viewModelScope.launch {
            while (isActive) {
                val brand = _activeBrand.value
                val client = clientFor(brand)
                val state = client.state.value
                if (!state.connected) {
                    client.ensureConnected(timeoutMs = 1800)
                } else if (state.paired) {
                    client.fetchAudioStatus()
                    if (syncedBrandsThisSession.add(brand)) {
                        refreshInstalledAppsSilent(brand)
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
        val client = activeClient
        viewModelScope.launch {
            _isBusy.value = true
            val current = client.state.value
            client.setFeedback(
                if (current.connected && current.paired) "Desligando TV..." else "Enviando Wake-on-LAN..."
            )
            val result = client.togglePower()
            result.onSuccess { msg ->
                client.setFeedback(msg)
            }.onFailure { err ->
                client.setFeedback(err.message ?: "Falha ao acionar energia da TV")
            }
            _isBusy.value = false
        }
    }

    fun onVolumeUp() {
        pulseIrLed()
        val client = activeClient
        viewModelScope.launch {
            client.setFeedback("Volume +")
            client.volumeUp().onSuccess {
                client.fetchAudioStatus()
            }.onFailure { err ->
                client.setFeedback(err.message ?: "Erro ao aumentar volume")
            }
        }
    }

    fun onVolumeDown() {
        pulseIrLed()
        val client = activeClient
        viewModelScope.launch {
            client.setFeedback("Volume −")
            client.volumeDown().onSuccess {
                client.fetchAudioStatus()
            }.onFailure { err ->
                client.setFeedback(err.message ?: "Erro ao diminuir volume")
            }
        }
    }

    fun onChannelUp() {
        pulseIrLed()
        val client = activeClient
        viewModelScope.launch {
            client.setFeedback("Canal +")
            client.channelUp().onFailure {
                client.sendButton("CHANNELUP").onFailure { err ->
                    client.setFeedback(err.message ?: "Erro ao mudar canal")
                }
            }
        }
    }

    fun onChannelDown() {
        pulseIrLed()
        val client = activeClient
        viewModelScope.launch {
            client.setFeedback("Canal −")
            client.channelDown().onFailure {
                client.sendButton("CHANNELDOWN").onFailure { err ->
                    client.setFeedback(err.message ?: "Erro ao mudar canal")
                }
            }
        }
    }

    fun onMuteToggle() {
        pulseIrLed()
        val client = activeClient
        viewModelScope.launch {
            val res = client.toggleMute()
            res.onSuccess { muted ->
                client.setFeedback(if (muted) "Mudo ativado" else "Mudo desativado")
            }.onFailure { err ->
                client.setFeedback(err.message ?: "Erro ao alternar mudo")
            }
        }
    }

    fun onNavigationButton(button: String, label: String) {
        pulseIrLed()
        val client = activeClient
        viewModelScope.launch {
            client.setFeedback(label)
            client.sendButton(button).onFailure { err ->
                client.setFeedback(err.message ?: "Falha ao enviar $label")
            }
        }
    }

    fun onLaunchShortcut(shortcut: TvAppShortcut) {
        pulseIrLed()
        val client = activeClient
        viewModelScope.launch {
            _isBusy.value = true
            client.setFeedback("Abrindo ${shortcut.label}...")
            val result = when (shortcut.actionType) {
                ShortcutActionType.APP -> client.launchApp(shortcut.target)
                ShortcutActionType.YOUTUBE_LIVE -> client.launchYouTubeUrl(shortcut.target)
            }
            result.onSuccess {
                client.setFeedback("${shortcut.label} aberto na TV")
            }.onFailure { err ->
                client.setFeedback(err.message ?: "Falha ao abrir ${shortcut.label}")
            }
            _isBusy.value = false
        }
    }

    /**
     * Sincroniza os aplicativos instalados e seus ícones diretamente da TV ativa (LG ou Samsung).
     */
    fun onRefreshInstalledApps() {
        val brand = _activeBrand.value
        val client = clientFor(brand)
        val iconsDir = iconsDirFor(brand)
        viewModelScope.launch {
            _isLoadingApps.value = true
            client.setFeedback("Carregando apps instalados na TV...")
            val res = client.fetchInstalledApps(iconsDir)
            res.onSuccess { apps ->
                if (_activeBrand.value == brand) {
                    _installedApps.value = apps
                }
                refreshShortcutsState(brand)
                client.setFeedback("${apps.size} apps carregados da TV!")
            }.onFailure { err ->
                client.setFeedback(err.message ?: "Ligue a TV para carregar os apps instalados")
            }
            _isLoadingApps.value = false
        }
    }

    private suspend fun refreshInstalledAppsSilent(brand: TvBrand) {
        val client = clientFor(brand)
        val iconsDir = iconsDirFor(brand)
        val res = client.fetchInstalledApps(iconsDir)
        res.onSuccess { apps ->
            if (_activeBrand.value == brand) {
                _installedApps.value = apps
            }
            refreshShortcutsState(brand)
        }
    }

    /**
     * Salva a configuração de um dos 9 botões de atalho da TV ativa (de forma independente por controle).
     * Se for uma Live do YouTube, busca automaticamente o ícone/avatar do canal!
     */
    fun onSaveShortcut(
        slotIndex: Int,
        label: String,
        actionType: ShortcutActionType,
        target: String,
        customIconPath: String?
    ) {
        val brand = _activeBrand.value
        val client = clientFor(brand)
        val iconsDir = iconsDirFor(brand)
        val currentList = preferences.getShortcuts(brand).toMutableList()
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

        preferences.saveShortcuts(brand, currentList)
        refreshShortcutsState(brand)
        client.setFeedback("Atalho \"${label.trim()}\" salvo!")

        // Se for canal de Live do YouTube e ainda não tiver ícone local, baixa o avatar do canal em background
        if (actionType == ShortcutActionType.YOUTUBE_LIVE) {
            viewModelScope.launch {
                val avatarUrl = YouTubeLiveResolver.fetchChannelAvatarUrl(cleanTarget)
                if (!avatarUrl.isNullOrBlank()) {
                    val safeChannel = cleanTarget.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                    val destFile = File(iconsDir, "yt_${safeChannel}.jpg")
                    val downloadedPath = client.downloadFile(avatarUrl, destFile)
                    if (!downloadedPath.isNullOrBlank()) {
                        val refreshed = preferences.getShortcuts(brand).map { slot ->
                            if (slot.slotIndex == slotIndex) {
                                slot.copy(customIconPath = downloadedPath)
                            } else slot
                        }
                        preferences.saveShortcuts(brand, refreshed)
                        refreshShortcutsState(brand)
                    }
                }
            }
        }
    }

    fun onResetShortcutsToDefault() {
        val brand = _activeBrand.value
        val client = clientFor(brand)
        preferences.resetShortcutsToDefault(brand)
        refreshShortcutsState(brand)
        client.setFeedback("Atalhos restaurados para o padrão")
    }

    /**
     * Teclado do Celular -> Envia texto diretamente para o campo de texto da TV ativa.
     */
    fun onSendTextToTv(text: String, replace: Boolean = false, pressEnter: Boolean = true) {
        if (text.isEmpty()) return
        pulseIrLed()
        val client = activeClient
        viewModelScope.launch {
            client.setFeedback("Enviando texto para a TV...")
            client.sendTextInput(text, replace = replace, pressEnter = pressEnter)
                .onSuccess {
                    client.setFeedback("Texto enviado: \"$text\"")
                }
                .onFailure { err ->
                    client.setFeedback(err.message ?: "Erro ao enviar texto")
                }
        }
    }

    fun onDeleteTvChar() {
        pulseIrLed()
        val client = activeClient
        viewModelScope.launch {
            client.deleteTextCharacters(1)
                .onSuccess { client.setFeedback("⌫ Apagar na TV") }
                .onFailure { client.sendButton("BACK") }
        }
    }

    fun onSendTvEnter() {
        pulseIrLed()
        val client = activeClient
        viewModelScope.launch {
            client.setFeedback("↵ Confirmar na TV")
            client.sendImeEnter()
        }
    }

    fun onSearchYouTube(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        pulseIrLed()
        val client = activeClient
        viewModelScope.launch {
            _isBusy.value = true
            client.setFeedback("Buscando \"$trimmed\" no YouTube...")
            client.searchAndLaunchYouTube(trimmed)
                .onSuccess {
                    client.setFeedback("Reproduzindo \"$trimmed\" no YouTube!")
                }
                .onFailure { err ->
                    client.setFeedback(err.message ?: "Erro ao buscar no YouTube")
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
     * Interpreta comandos naturais em português e aciona a função correspondente na TV ativa.
     */
    fun onVoiceCommand(spokenText: String) {
        val raw = spokenText.trim()
        if (raw.isEmpty()) return
        val lower = raw.lowercase()
        pulseIrLed()
        val client = activeClient

        viewModelScope.launch {
            _isBusy.value = true
            client.setFeedback("Voz: \"$raw\"")

            // 1. Ligar / Desligar TV
            if (lower == "desligar" || lower.contains("desligar tv") || lower.contains("desligar a tv") || lower.contains("desliga a tv")) {
                client.powerOff()
                _isBusy.value = false
                return@launch
            }
            if (lower == "ligar" || lower.contains("ligar tv") || lower.contains("ligar a tv") || lower.contains("liga a tv")) {
                val res = client.togglePower()
                res.onSuccess { client.setFeedback(it) }
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
                    client.setVolume(targetVol)
                        .onSuccess { client.setFeedback("Volume ajustado para $targetVol") }
                        .onFailure { client.setFeedback("Falha ao ajustar volume") }
                    _isBusy.value = false
                    return@launch
                }
            }

            // 4. Aumentar / Diminuir volume
            if (lower.contains("aumentar volume") || lower.contains("subir volume") || lower.contains("mais alto") || lower.contains("aumenta o volume")) {
                repeat(3) {
                    client.volumeUp()
                    delay(120)
                }
                client.fetchAudioStatus()
                client.setFeedback("Volume aumentado")
                _isBusy.value = false
                return@launch
            }
            if (lower.contains("diminuir volume") || lower.contains("baixar volume") || lower.contains("abaixar volume") || lower.contains("mais baixo") || lower.contains("abaixa o volume")) {
                repeat(3) {
                    client.volumeDown()
                    delay(120)
                }
                client.fetchAudioStatus()
                client.setFeedback("Volume diminuído")
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
                    client.sendButton("BACK")
                    _isBusy.value = false
                    return@launch
                }
                "home", "início", "inicio", "menu inicial" -> {
                    client.sendButton("HOME")
                    _isBusy.value = false
                    return@launch
                }
                "ok", "confirmar", "enter" -> {
                    client.sendButton("ENTER")
                    _isBusy.value = false
                    return@launch
                }
            }

            // 6. Lives no YouTube (ex: "live do sbt", "assistir cazetv ao vivo")
            if (lower.contains("live ") || lower.contains("ao vivo")) {
                val matchedShortcut = shortcuts.value.firstOrNull { sc ->
                    sc.actionType == ShortcutActionType.YOUTUBE_LIVE &&
                        lower.contains(sc.label.lowercase())
                }
                if (matchedShortcut != null) {
                    client.setFeedback("Abrindo live de ${matchedShortcut.label}...")
                    client.launchYouTubeUrl(matchedShortcut.target)
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
                    client.setFeedback("Abrindo live de \"$channelExtract\" no YouTube...")
                    client.launchYouTubeUrl(channelExtract)
                        .onSuccess { client.setFeedback("Live de \"$channelExtract\" aberta!") }
                        .onFailure { client.setFeedback("Não foi possível abrir a live") }
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
                val shortcutMatch = shortcuts.value.firstOrNull { sc ->
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
                    client.setFeedback("Abrindo ${installedMatch.title}...")
                    client.launchApp(installedMatch.appId)
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
                client.setFeedback("Buscando \"$ytCleaned\" no YouTube...")
                client.searchAndLaunchYouTube(ytCleaned)
                    .onSuccess { client.setFeedback("Reproduzindo \"$ytCleaned\" na TV!") }
                    .onFailure { err -> client.setFeedback(err.message ?: "Falha na busca do YouTube") }
                _isBusy.value = false
                return@launch
            }

            // 9. Caso contrário: tenta enviar o texto para o campo aberto na TV; se não houver, busca no YouTube
            val textSent = client.sendTextInput(raw, replace = false, pressEnter = true)
            if (textSent.isSuccess) {
                client.setFeedback("Texto falado enviado: \"$raw\"")
            } else {
                client.setFeedback("Buscando \"$raw\" no YouTube...")
                client.searchAndLaunchYouTube(raw)
            }
            _isBusy.value = false
        }
    }

    fun onSaveSettings(
        ip: String,
        mac: String,
        broadcast: String,
        hapticEnabled: Boolean,
        hardwareVolumeKeysEnabled: Boolean = true
    ) {
        val brand = _activeBrand.value
        val client = clientFor(brand)
        preferences.saveConfig(brand, ip, mac, broadcast, hapticEnabled, hardwareVolumeKeysEnabled)
        _hapticEnabled.value = hapticEnabled
        _hardwareVolumeKeysEnabled.value = hardwareVolumeKeysEnabled
        client.refreshConfigFromPrefs()
        client.disconnect()
        syncedBrandsThisSession.remove(brand)
        viewModelScope.launch {
            client.setFeedback("Reconectando em ${ip.trim()}...")
            val res = client.ensureConnected(2500)
            if (res.isSuccess) {
                client.setFeedback("Conectado à ${brand.displayName} (${ip.trim()})")
                refreshInstalledAppsSilent(brand)
            } else {
                client.setFeedback("Configurações salvas (TV em standby)")
            }
        }
    }

    fun onDiscoverTv(onDiscovered: (String) -> Unit) {
        val brand = _activeBrand.value
        val client = clientFor(brand)
        viewModelScope.launch {
            client.setFeedback("Buscando ${brand.displayName} via SSDP...")
            val found = TvSsdpDiscovery.discoverTv(brand, 3000)
            if (found != null) {
                preferences.saveIpAddress(brand, found.ip)
                client.refreshConfigFromPrefs()
                client.setFeedback("TV encontrada: ${found.friendlyName} (${found.ip})")
                onDiscovered(found.ip)
            } else {
                client.setFeedback("Nenhuma ${brand.displayName} encontrada via SSDP")
            }
        }
    }
}
