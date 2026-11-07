package io.github.gsilverio7.webosremote.network

import io.github.gsilverio7.webosremote.data.TvBrand
import io.github.gsilverio7.webosremote.data.TvInstalledApp
import io.github.gsilverio7.webosremote.data.TvPreferences
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URLEncoder
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class SamsungTizenClient(
    private val preferences: TvPreferences
) : TvClient {
    override val brand: TvBrand = TvBrand.SAMSUNG

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connectMutex = Mutex()

    private var mainWebSocket: WebSocket? = null
    private var pendingAppsDeferred: CompletableDeferred<JSONObject>? = null

    private val _state = MutableStateFlow(
        preferences.getConfig(TvBrand.SAMSUNG).let { cfg ->
            TvConnectionState(
                ipAddress = cfg.ipAddress,
                macAddress = cfg.macAddress,
                broadcastAddress = cfg.broadcastAddress,
                volume = 15
            )
        }
    )
    override val state: StateFlow<TvConnectionState> = _state.asStateFlow()

    private val insecureOkHttpClient: OkHttpClient by lazy {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        })
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, trustAllCerts, SecureRandom())
        }
        OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(2500, TimeUnit.MILLISECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(15, TimeUnit.SECONDS)
            .build()
    }

    private val restHttpClient: OkHttpClient by lazy {
        insecureOkHttpClient.newBuilder()
            .connectTimeout(2500, TimeUnit.MILLISECONDS)
            .readTimeout(3000, TimeUnit.MILLISECONDS)
            .build()
    }

    companion object {
        private val ENCODED_REMOTE_NAME: String =
            Base64.getEncoder().encodeToString("SmartRemote".toByteArray(Charsets.UTF_8))

        /**
         * Mapeia nomes genéricos de botões para códigos de tecla Samsung Tizen (KEY_*).
         */
        fun mapButtonToSamsungKey(buttonName: String): String {
            val normalized = buttonName.trim().uppercase()
            if (normalized.startsWith("KEY_")) return normalized
            return when (normalized) {
                "UP" -> "KEY_UP"
                "DOWN" -> "KEY_DOWN"
                "LEFT" -> "KEY_LEFT"
                "RIGHT" -> "KEY_RIGHT"
                "ENTER", "OK" -> "KEY_ENTER"
                "BACK", "RETURN" -> "KEY_RETURN"
                "HOME" -> "KEY_HOME"
                "MENU" -> "KEY_MENU"
                "SOURCE", "INPUT" -> "KEY_SOURCE"
                "GUIDE" -> "KEY_GUIDE"
                "INFO" -> "KEY_INFO"
                "EXIT" -> "KEY_EXIT"
                "CHANNELUP", "CHUP" -> "KEY_CHUP"
                "CHANNELDOWN", "CHDOWN" -> "KEY_CHDOWN"
                "VOLUMEUP", "VOLUP" -> "KEY_VOLUP"
                "VOLUMEDOWN", "VOLDOWN" -> "KEY_VOLDOWN"
                "MUTE" -> "KEY_MUTE"
                "POWER", "POWEROFF" -> "KEY_POWER"
                "PLAY" -> "KEY_PLAY"
                "PAUSE" -> "KEY_PAUSE"
                "STOP" -> "KEY_STOP"
                "BACKSPACE", "DELETE" -> "KEY_BACKSPACE"
                else -> "KEY_$normalized"
            }
        }

        /**
         * Mapeia IDs comuns de aplicativos para os App IDs oficiais do Samsung Tizen OS.
         */
        fun resolveSamsungAppId(appIdOrAlias: String): String {
            val trimmed = appIdOrAlias.trim()
            return when (trimmed.lowercase()) {
                "youtube", "youtube.leanback.v4" -> "111299001912"
                "netflix" -> "3201907018807"
                "amazon", "prime", "primevideo" -> "3201910019365"
                "com.wbd.stream", "max", "hbomax" -> "3202301029760"
                "com.disney.disneyplus-prod", "disney", "disneyplus" -> "3201901017640"
                "globoplaywebos", "globoplay" -> "3201908019041"
                "com.webos.app.lgchannels", "tvplus", "samsungtvplus" -> "org.tizen.tvplus"
                "spotify", "spotify-beehive" -> "3201606009684"
                "com.apple.appletv", "appletv" -> "3201807016597"
                "com.webos.app.browser", "browser" -> "org.tizen.browser"
                "com.limelight.webos", "moonlight" -> "com.limelight.tizen"
                else -> trimmed
            }
        }
    }

    override fun refreshConfigFromPrefs() {
        val cfg = preferences.getConfig(TvBrand.SAMSUNG)
        _state.update {
            it.copy(
                ipAddress = cfg.ipAddress,
                macAddress = cfg.macAddress,
                broadcastAddress = cfg.broadcastAddress
            )
        }
    }

    override fun setFeedback(message: String) {
        _state.update { it.copy(feedbackMessage = message) }
    }

    private fun isPortReachable(ip: String, port: Int, timeoutMs: Int = 650): Boolean {
        return runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), timeoutMs)
                true
            }
        }.getOrDefault(false)
    }

    override suspend fun ensureConnected(timeoutMs: Long): Result<Unit> = withContext(Dispatchers.IO) {
        val current = _state.value
        if (current.connected && current.paired && mainWebSocket != null) {
            return@withContext Result.success(Unit)
        }

        connectMutex.withLock {
            val recheck = _state.value
            if (recheck.connected && recheck.paired && mainWebSocket != null) {
                return@withLock Result.success(Unit)
            }
            connectAndPairInternal(timeoutMs)
        }
    }

    private suspend fun connectAndPairInternal(timeoutMs: Long): Result<Unit> {
        val cfg = preferences.getConfig(TvBrand.SAMSUNG)
        var targetIp = cfg.ipAddress

        if (targetIp.isBlank()) {
            setFeedback("Buscando TV Samsung na rede via SSDP...")
            val discovered = TvSsdpDiscovery.discoverSamsungTv(2500)
            if (discovered != null) {
                targetIp = discovered.ip
                preferences.saveIpAddress(TvBrand.SAMSUNG, targetIp)
            } else {
                return Result.failure(IllegalStateException("TV Samsung não encontrada na rede Wi-Fi"))
            }
        }

        val reachable8002 = isPortReachable(targetIp, 8002, 600)
        val reachable8001 = if (!reachable8002) isPortReachable(targetIp, 8001, 600) else true
        if (!reachable8002 && !reachable8001) {
            return Result.failure(IllegalStateException("TV Samsung em standby ($targetIp)"))
        }

        // Busca informações da TV (incluindo MAC Wi-Fi caso ainda não esteja salvo)
        if (cfg.macAddress.isBlank()) {
            scope.launch {
                fetchAndSaveMacIfMissing(targetIp)
            }
        }

        closeSocketsInternal()
        _state.update {
            it.copy(
                ipAddress = targetIp,
                macAddress = cfg.macAddress,
                broadcastAddress = cfg.broadcastAddress,
                connected = false,
                paired = false,
                prompting = false
            )
        }

        val tokenParam = if (cfg.clientKey.isNotEmpty()) {
            "&token=${URLEncoder.encode(cfg.clientKey, "UTF-8")}"
        } else {
            ""
        }

        val endpoints = buildList {
            if (reachable8002) {
                add("wss://$targetIp:8002/api/v2/channels/samsung.remote.control?name=$ENCODED_REMOTE_NAME$tokenParam")
            }
            if (reachable8001) {
                add("ws://$targetIp:8001/api/v2/channels/samsung.remote.control?name=$ENCODED_REMOTE_NAME")
            }
            if (isEmpty()) {
                add("wss://$targetIp:8002/api/v2/channels/samsung.remote.control?name=$ENCODED_REMOTE_NAME$tokenParam")
                add("ws://$targetIp:8001/api/v2/channels/samsung.remote.control?name=$ENCODED_REMOTE_NAME")
            }
        }

        for (url in endpoints) {
            val openDeferred = CompletableDeferred<Boolean>()
            val channelConnectDeferred = CompletableDeferred<Boolean>()
            val ws = insecureOkHttpClient.newWebSocket(
                Request.Builder().url(url).build(),
                createSocketListener(openDeferred, channelConnectDeferred)
            )

            val tcpConnected = withTimeoutOrNull(timeoutMs) { openDeferred.await() } ?: false
            if (!tcpConnected) {
                runCatching { ws.cancel() }
                continue
            }

            mainWebSocket = ws
            _state.update { it.copy(connected = true) }

            // Aguarda resposta rápida de conexão autorizada (quando já possui token válido)
            val quickPaired = withTimeoutOrNull(1200L) { channelConnectDeferred.await() }
            if (quickPaired == true) {
                return Result.success(Unit)
            } else if (quickPaired == false) {
                runCatching { ws.cancel() }
                mainWebSocket = null
                continue
            }

            // Se não respondeu em 1.2s, a TV Samsung está exibindo o pop-up de permissão na tela
            _state.update {
                it.copy(
                    prompting = true,
                    feedbackMessage = "Confirme \"Permitir\" na tela da TV Samsung!"
                )
            }

            val pairedAfterPrompt = withTimeoutOrNull(45_000L) { channelConnectDeferred.await() } ?: false
            _state.update { it.copy(prompting = false) }

            if (pairedAfterPrompt) {
                return Result.success(Unit)
            } else {
                closeSocketsInternal()
                return Result.failure(IllegalStateException("Pareamento não confirmado na TV Samsung"))
            }
        }

        return Result.failure(IllegalStateException("TV Samsung desligada ou inacessível em $targetIp"))
    }

    private suspend fun fetchAndSaveMacIfMissing(ip: String) {
        runCatching {
            val req = Request.Builder()
                .url("http://$ip:8001/api/v2/")
                .get()
                .build()
            restHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use
                val body = resp.body?.string().orEmpty()
                val device = JSONObject(body).optJSONObject("device") ?: return@use
                val wifiMac = device.optString("wifiMac").trim()
                if (wifiMac.length == 17 && !wifiMac.equals("none", ignoreCase = true)) {
                    val currentCfg = preferences.getConfig(TvBrand.SAMSUNG)
                    if (currentCfg.macAddress.isBlank()) {
                        preferences.saveConfig(
                            brand = TvBrand.SAMSUNG,
                            ipAddress = currentCfg.ipAddress.ifBlank { ip },
                            macAddress = wifiMac,
                            broadcastAddress = currentCfg.broadcastAddress,
                            hapticEnabled = currentCfg.hapticEnabled,
                            hardwareVolumeKeysEnabled = currentCfg.hardwareVolumeKeysEnabled
                        )
                        refreshConfigFromPrefs()
                    }
                }
            }
        }
    }

    private fun createSocketListener(
        openDeferred: CompletableDeferred<Boolean>,
        channelConnectDeferred: CompletableDeferred<Boolean>
    ) = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            openDeferred.complete(true)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val json = runCatching { JSONObject(text) }.getOrNull() ?: return
            val event = json.optString("event")
            val data = json.optJSONObject("data")

            when (event) {
                "ms.channel.connect" -> {
                    val token = data?.optString("token").orEmpty()
                    if (token.isNotBlank()) {
                        preferences.saveClientKey(TvBrand.SAMSUNG, token)
                    }
                    _state.update {
                        it.copy(
                            connected = true,
                            paired = true,
                            prompting = false
                        )
                    }
                    if (!channelConnectDeferred.isCompleted) {
                        channelConnectDeferred.complete(true)
                    }
                }
                "ms.channel.unauthorized", "ms.channel.timeOut" -> {
                    _state.update {
                        it.copy(connected = false, paired = false, prompting = false)
                    }
                    if (!channelConnectDeferred.isCompleted) {
                        channelConnectDeferred.complete(false)
                    }
                }
                "ed.installedApp.get" -> {
                    pendingAppsDeferred?.complete(json)
                }
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (!openDeferred.isCompleted) {
                openDeferred.complete(false)
            }
            if (!channelConnectDeferred.isCompleted) {
                channelConnectDeferred.complete(false)
            }
            if (mainWebSocket === webSocket) {
                _state.update {
                    it.copy(connected = false, paired = false, prompting = false)
                }
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (!channelConnectDeferred.isCompleted) {
                channelConnectDeferred.complete(false)
            }
            if (mainWebSocket === webSocket) {
                _state.update {
                    it.copy(connected = false, paired = false, prompting = false)
                }
            }
        }
    }

    private suspend fun sendRemoteKey(
        keyCode: String,
        cmd: String = "Click"
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val connResult = ensureConnected()
        if (connResult.isFailure) {
            return@withContext Result.failure(
                connResult.exceptionOrNull() ?: IllegalStateException("Sem conexão com a TV Samsung")
            )
        }

        val ws = mainWebSocket
            ?: return@withContext Result.failure(IllegalStateException("WebSocket Samsung indisponível"))

        val payload = JSONObject().apply {
            put("method", "ms.remote.control")
            put("params", JSONObject().apply {
                put("Cmd", cmd)
                put("DataOfCmd", keyCode)
                put("Option", "false")
                put("TypeOfRemote", "SendRemoteKey")
            })
        }

        if (!ws.send(payload.toString())) {
            closeSocketsInternal()
            val retryConn = ensureConnected()
            if (retryConn.isFailure) {
                return@withContext Result.failure(IllegalStateException("Falha ao enviar tecla $keyCode para TV Samsung"))
            }
            val retryWs = mainWebSocket
                ?: return@withContext Result.failure(IllegalStateException("Falha ao reconectar à TV Samsung"))
            if (!retryWs.send(payload.toString())) {
                return@withContext Result.failure(IllegalStateException("Falha ao enviar tecla $keyCode"))
            }
        }

        Result.success(Unit)
    }

    override suspend fun togglePower(): Result<String> = withContext(Dispatchers.IO) {
        val currentlyConnected = _state.value.connected && _state.value.paired
        if (currentlyConnected) {
            val offRes = powerOff()
            if (offRes.isSuccess) {
                return@withContext Result.success("Desligando TV Samsung...")
            }
        }

        val cfg = preferences.getConfig(TvBrand.SAMSUNG)
        if (cfg.macAddress.isBlank()) {
            // Tenta desligar/ligar via WebSocket caso a TV esteja acessível, senão pede o MAC
            if (ensureConnected(1800).isSuccess) {
                val offRes = powerOff()
                if (offRes.isSuccess) {
                    return@withContext Result.success("Desligando TV Samsung...")
                }
            }
            return@withContext Result.failure(
                IllegalStateException("Configure o endereço MAC da TV Samsung para ligar via Wake-on-LAN")
            )
        }

        return@withContext runCatching {
            TvWakeOnLan.sendWakeOnLan(cfg.macAddress, cfg.broadcastAddress)

            scope.launch {
                repeat(8) { attempt ->
                    delay(1800L + attempt * 800L)
                    if (ensureConnected(2000).isSuccess) {
                        setFeedback("TV Samsung conectada!")
                        return@launch
                    }
                }
            }
            "Wake-on-LAN enviado! Ligando TV Samsung..."
        }
    }

    override suspend fun powerOff(): Result<Unit> {
        val res = sendRemoteKey("KEY_POWER")
        return if (res.isSuccess) {
            delay(150)
            closeSocketsInternal()
            _state.update {
                it.copy(
                    connected = false,
                    paired = false,
                    feedbackMessage = "TV Samsung desligada"
                )
            }
            Result.success(Unit)
        } else {
            Result.failure(res.exceptionOrNull() ?: Exception("Erro ao desligar TV Samsung"))
        }
    }

    override suspend fun volumeUp(): Result<Unit> {
        return sendRemoteKey("KEY_VOLUP").map {
            _state.update { s ->
                s.copy(volume = (s.volume + 1).coerceAtMost(100), muted = false)
            }
        }
    }

    override suspend fun volumeDown(): Result<Unit> {
        return sendRemoteKey("KEY_VOLDOWN").map {
            _state.update { s ->
                s.copy(volume = (s.volume - 1).coerceAtLeast(0), muted = false)
            }
        }
    }

    override suspend fun channelUp(): Result<Unit> =
        sendRemoteKey("KEY_CHUP")

    override suspend fun channelDown(): Result<Unit> =
        sendRemoteKey("KEY_CHDOWN")

    override suspend fun setVolume(volume: Int): Result<Unit> = withContext(Dispatchers.IO) {
        val target = volume.coerceIn(0, 100)
        val current = _state.value.volume
        val diff = (target - current).coerceIn(-25, 25)
        if (diff > 0) {
            repeat(diff) {
                sendRemoteKey("KEY_VOLUP").getOrElse { return@withContext Result.failure(it) }
                delay(90)
            }
        } else if (diff < 0) {
            repeat(-diff) {
                sendRemoteKey("KEY_VOLDOWN").getOrElse { return@withContext Result.failure(it) }
                delay(90)
            }
        }
        _state.update { it.copy(volume = target, muted = false) }
        Result.success(Unit)
    }

    override suspend fun fetchAudioStatus(): Result<Pair<Int, Boolean>> {
        val s = _state.value
        return Result.success(s.volume to s.muted)
    }

    override suspend fun toggleMute(): Result<Boolean> {
        return sendRemoteKey("KEY_MUTE").map {
            val newMuted = !_state.value.muted
            _state.update { s -> s.copy(muted = newMuted) }
            newMuted
        }
    }

    override suspend fun launchApp(appId: String, contentId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val resolvedId = resolveSamsungAppId(appId)
        val ip = _state.value.ipAddress.ifBlank { preferences.getConfig(TvBrand.SAMSUNG).ipAddress }

        // 1. Tenta via canal WebSocket Samsung (ed.apps.launch)
        val wsResult = ensureConnected().mapCatching {
            val ws = mainWebSocket ?: throw IllegalStateException("WebSocket indisponível")
            val emitJson = JSONObject().apply {
                put("method", "ms.channel.emit")
                put("params", JSONObject().apply {
                    put("event", "ed.apps.launch")
                    put("to", "host")
                    put("data", JSONObject().apply {
                        put("appId", resolvedId)
                        put("action_type", if (contentId.isNotEmpty()) "DEEP_LINK" else "NATIVE_LAUNCH")
                        if (contentId.isNotEmpty()) {
                            put("metaTag", contentId)
                        }
                    })
                })
            }
            if (!ws.send(emitJson.toString())) {
                throw IllegalStateException("Falha ao enviar ed.apps.launch")
            }
        }

        // 2. Dispara também via REST API oficial Tizen (/api/v2/applications/<appId>) para compatibilidade com Tizen modernos
        val restLaunched = if (ip.isNotBlank()) {
            launchAppViaRest(ip, resolvedId)
        } else false

        if (wsResult.isSuccess || restLaunched) {
            Result.success(Unit)
        } else {
            Result.failure(
                wsResult.exceptionOrNull() ?: IllegalStateException("Não foi possível abrir o app na TV Samsung")
            )
        }
    }

    private fun launchAppViaRest(ip: String, appId: String): Boolean {
        val urls = listOf(
            "http://$ip:8001/api/v2/applications/$appId",
            "https://$ip:8002/api/v2/applications/$appId"
        )
        val emptyBody = "".toRequestBody("application/json".toMediaType())
        for (url in urls) {
            val ok = runCatching {
                val req = Request.Builder()
                    .url(url)
                    .post(emptyBody)
                    .build()
                restHttpClient.newCall(req).execute().use { resp ->
                    resp.isSuccessful || resp.code in 200..299
                }
            }.getOrDefault(false)
            if (ok) return true
        }
        return false
    }

    override suspend fun launchYouTubeUrl(videoOrChannelUrl: String): Result<Unit> = withContext(Dispatchers.IO) {
        val contentTarget = YouTubeLiveResolver.resolveContentTarget(videoOrChannelUrl)
        launchYouTubeWithContentTarget(contentTarget)
    }

    override suspend fun searchAndLaunchYouTube(query: String): Result<Unit> = withContext(Dispatchers.IO) {
        val contentTarget = YouTubeLiveResolver.resolveSearchQueryTarget(query)
            ?: return@withContext Result.failure(IllegalStateException("Nenhum vídeo encontrado para \"$query\""))
        launchYouTubeWithContentTarget(contentTarget)
    }

    private suspend fun launchYouTubeWithContentTarget(contentTarget: String): Result<Unit> {
        val ip = _state.value.ipAddress.ifBlank { preferences.getConfig(TvBrand.SAMSUNG).ipAddress }

        // 1. Tenta protocolo DIAL do YouTube na TV Samsung (porta 8080 / 8001) que abre o vídeo/live direto
        if (ip.isNotBlank()) {
            val dialEndpoints = listOf(
                "http://$ip:8080/ws/apps/YouTube",
                "http://$ip:8001/ws/apps/YouTube"
            )
            for (dialUrl in dialEndpoints) {
                val dialOk = runCatching {
                    val body = contentTarget.toRequestBody("text/plain; charset=utf-8".toMediaType())
                    val req = Request.Builder().url(dialUrl).post(body).build()
                    restHttpClient.newCall(req).execute().use { resp ->
                        resp.isSuccessful || resp.code == 201
                    }
                }.getOrDefault(false)
                if (dialOk) return Result.success(Unit)
            }
        }

        // 2. Aciona via WebSocket ed.apps.launch com metaTag = contentTarget + REST API
        return launchApp("111299001912", contentTarget)
    }

    override suspend fun fetchInstalledApps(iconsDir: File): Result<List<TvInstalledApp>> = withContext(Dispatchers.IO) {
        if (!iconsDir.exists()) {
            iconsDir.mkdirs()
        }

        val connResult = ensureConnected()
        if (connResult.isFailure) {
            return@withContext Result.failure(
                connResult.exceptionOrNull() ?: IllegalStateException("Ligue a TV Samsung para carregar os apps")
            )
        }

        val appsById = linkedMapOf<String, TvInstalledApp>()

        val ws = mainWebSocket
        if (ws != null) {
            val deferred = CompletableDeferred<JSONObject>()
            pendingAppsDeferred = deferred
            val emitJson = JSONObject().apply {
                put("method", "ms.channel.emit")
                put("params", JSONObject().apply {
                    put("event", "ed.installedApp.get")
                    put("to", "host")
                })
            }
            if (ws.send(emitJson.toString())) {
                val resp = withTimeoutOrNull(3500L) { deferred.await() }
                val appsArray: JSONArray? = resp
                    ?.optJSONObject("data")
                    ?.optJSONArray("data")
                if (appsArray != null) {
                    for (i in 0 until appsArray.length()) {
                        val item = appsArray.optJSONObject(i) ?: continue
                        val id = item.optString("appId").trim()
                        val name = item.optString("name").trim()
                        if (id.isNotEmpty() && name.isNotEmpty()) {
                            appsById[id] = TvInstalledApp(appId = id, title = name)
                        }
                    }
                }
            }
            pendingAppsDeferred = null
        }

        // Complementa com os principais apps conhecidos do Tizen (caso a versão do Tizen restrinja ed.installedApp.get)
        defaultSamsungApps().forEach { known ->
            if (!appsById.containsKey(known.appId)) {
                appsById[known.appId] = known
            }
        }

        val resultList = appsById.values.sortedBy { it.title.lowercase() }
        preferences.saveCachedInstalledApps(TvBrand.SAMSUNG, resultList)
        Result.success(resultList)
    }

    private fun defaultSamsungApps(): List<TvInstalledApp> = listOf(
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

    override suspend fun downloadFile(url: String, destFile: File): String? = withContext(Dispatchers.IO) {
        runCatching {
            destFile.parentFile?.mkdirs()
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .get()
                .build()
            insecureOkHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                val bytes = resp.body?.bytes() ?: return@runCatching null
                if (bytes.isEmpty()) return@runCatching null
                destFile.writeBytes(bytes)
                destFile.absolutePath
            }
        }.getOrNull()
    }

    override suspend fun sendTextInput(
        text: String,
        replace: Boolean,
        pressEnter: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val connResult = ensureConnected()
        if (connResult.isFailure) {
            return@withContext Result.failure(
                connResult.exceptionOrNull() ?: IllegalStateException("Sem conexão com a TV Samsung")
            )
        }

        val ws = mainWebSocket
            ?: return@withContext Result.failure(IllegalStateException("WebSocket Samsung indisponível"))

        val base64Text = Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8))
        val msg = JSONObject().apply {
            put("method", "ms.remote.control")
            put("params", JSONObject().apply {
                put("Cmd", base64Text)
                put("DataOfCmd", "base64")
                put("TypeOfRemote", "SendInputString")
            })
        }

        if (!ws.send(msg.toString())) {
            return@withContext Result.failure(IllegalStateException("Erro ao enviar texto para a TV Samsung"))
        }

        if (pressEnter) {
            delay(150)
            sendRemoteKey("KEY_ENTER")
        }

        Result.success(Unit)
    }

    override suspend fun sendImeEnter(): Result<Unit> =
        sendRemoteKey("KEY_ENTER")

    override suspend fun deleteTextCharacters(count: Int): Result<Unit> = withContext(Dispatchers.IO) {
        repeat(count.coerceAtLeast(1)) {
            val res = sendRemoteKey("KEY_BACKSPACE")
            if (res.isFailure) return@withContext res
            if (count > 1) delay(60)
        }
        Result.success(Unit)
    }

    override suspend fun sendButton(buttonName: String): Result<Unit> {
        val keyCode = mapButtonToSamsungKey(buttonName)
        return sendRemoteKey(keyCode)
    }

    override fun disconnect() {
        closeSocketsInternal()
        _state.update { it.copy(connected = false, paired = false, prompting = false) }
    }

    private fun closeSocketsInternal() {
        runCatching { mainWebSocket?.close(1000, "closed") }
        mainWebSocket = null
    }
}
