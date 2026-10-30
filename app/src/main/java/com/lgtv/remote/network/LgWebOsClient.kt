package com.lgtv.remote.network

import com.lgtv.remote.data.TvInstalledApp
import com.lgtv.remote.data.TvPreferences
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
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

data class TvConnectionState(
    val connected: Boolean = false,
    val paired: Boolean = false,
    val prompting: Boolean = false,
    val ipAddress: String = "",
    val macAddress: String = "",
    val broadcastAddress: String = "",
    val volume: Int = 0,
    val muted: Boolean = false,
    val feedbackMessage: String = "Pronto"
)

class LgWebOsClient(
    private val preferences: TvPreferences
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connectMutex = Mutex()
    private val reqCounter = AtomicLong(0)

    private var mainWebSocket: WebSocket? = null
    private var inputWebSocket: WebSocket? = null
    private val pendingResponses = ConcurrentHashMap<String, CompletableDeferred<JSONObject>>()

    private val _state = MutableStateFlow(
        preferences.getConfig().let { cfg ->
            TvConnectionState(
                ipAddress = cfg.ipAddress,
                macAddress = cfg.macAddress,
                broadcastAddress = cfg.broadcastAddress
            )
        }
    )
    val state: StateFlow<TvConnectionState> = _state.asStateFlow()

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
            .readTimeout(0, TimeUnit.MILLISECONDS) // WebSocket keep-alive
            .pingInterval(15, TimeUnit.SECONDS)
            .build()
    }

    private fun nextId(prefix: String): String = "${prefix}_${reqCounter.incrementAndGet()}"

    fun refreshConfigFromPrefs() {
        val cfg = preferences.getConfig()
        _state.update {
            it.copy(
                ipAddress = cfg.ipAddress,
                macAddress = cfg.macAddress,
                broadcastAddress = cfg.broadcastAddress
            )
        }
    }

    fun setFeedback(message: String) {
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

    /**
     * Garante que a conexão WebSocket SSAP esteja ativa e pareada com a TV LG.
     */
    suspend fun ensureConnected(timeoutMs: Long = 2800): Result<Unit> = withContext(Dispatchers.IO) {
        val current = _state.value
        if (current.connected && current.paired && mainWebSocket != null) {
            return@withContext Result.success(Unit)
        }

        connectMutex.withLock {
            val recheck = _state.value
            if (recheck.connected && recheck.paired && mainWebSocket != null) {
                return@withLock Result.success(Unit)
            }
            connectAndRegisterInternal(timeoutMs)
        }
    }

    private suspend fun connectAndRegisterInternal(timeoutMs: Long): Result<Unit> {
        val cfg = preferences.getConfig()
        var targetIp = cfg.ipAddress

        if (targetIp.isBlank()) {
            setFeedback("Buscando TV LG na rede via SSDP...")
            val discovered = TvSsdpDiscovery.discoverLgTv(2500)
            if (discovered != null) {
                targetIp = discovered.ip
                preferences.saveIpAddress(targetIp)
            } else {
                return Result.failure(IllegalStateException("TV não encontrada na rede Wi-Fi"))
            }
        }

        // Checagem rápida TCP para não travar quando a TV estiver em standby
        val reachable3001 = isPortReachable(targetIp, 3001, 600)
        val reachable3000 = if (!reachable3001) isPortReachable(targetIp, 3000, 600) else true
        if (!reachable3001 && !reachable3000) {
            return Result.failure(IllegalStateException("TV em standby ($targetIp)"))
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

        val endpoints = buildList {
            if (reachable3001) add("wss://$targetIp:3001")
            if (reachable3000) add("ws://$targetIp:3000")
            if (isEmpty()) {
                add("wss://$targetIp:3001")
                add("ws://$targetIp:3000")
            }
        }

        var openedWs: WebSocket? = null
        for (url in endpoints) {
            val openDeferred = CompletableDeferred<Boolean>()
            val ws = insecureOkHttpClient.newWebSocket(
                Request.Builder().url(url).build(),
                createMainSocketListener(openDeferred)
            )
            val connected = withTimeoutOrNull(timeoutMs) { openDeferred.await() } ?: false
            if (connected) {
                openedWs = ws
                break
            } else {
                runCatching { ws.cancel() }
            }
        }

        if (openedWs == null) {
            return Result.failure(IllegalStateException("TV desligada ou inacessível em $targetIp"))
        }

        mainWebSocket = openedWs
        _state.update { it.copy(connected = true) }

        // Envia payload de registro / pareamento SSAP
        val regId = nextId("register")
        val regDeferred = CompletableDeferred<JSONObject>()
        pendingResponses[regId] = regDeferred

        val permissions = listOf(
            "LAUNCH", "LAUNCH_WEBAPP", "APP_TO_APP", "CLOSE", "TEST_OPEN", "SEARCH",
            "CONTROL_AUDIO", "CONTROL_DISPLAY", "CONTROL_INPUT_JOYSTICK",
            "CONTROL_INPUT_MEDIA_PLAYBACK", "CONTROL_INPUT_TV", "CONTROL_POWER",
            "READ_APP_STATUS", "READ_CURRENT_CHANNEL", "READ_INPUT_DEVICE_LIST",
            "READ_NETWORK_STATUS", "READ_RUNNING_APPS", "READ_TV_CHANNEL_LIST",
            "WRITE_NOTIFICATION_TOAST", "READ_POWER_STATE", "READ_COUNTRY_INFO",
            "CONTROL_MOUSE_AND_KEYBOARD", "CONTROL_INPUT_TEXT", "READ_INSTALLED_APPS"
        )

        val regJson = JSONObject().apply {
            put("id", regId)
            put("type", "register")
            put("payload", JSONObject().apply {
                put("forcePairing", false)
                put("pairingType", "PROMPT")
                if (cfg.clientKey.isNotEmpty()) {
                    put("client-key", cfg.clientKey)
                }
                put("manifest", JSONObject().apply {
                    put("manifestVersion", 1)
                    put("appVersion", "1.0")
                    put("permissions", JSONArray(permissions))
                })
            })
        }

        if (!openedWs.send(regJson.toString())) {
            pendingResponses.remove(regId)
            closeSocketsInternal()
            return Result.failure(IllegalStateException("Erro ao enviar registro para a TV"))
        }

        val firstResp = withTimeoutOrNull(12_000L) { regDeferred.await() }
            ?: run {
                pendingResponses.remove(regId)
                return Result.failure(IllegalStateException("Tempo esgotado aguardando registro na TV"))
            }

        val respType = firstResp.optString("type")
        if (respType == "registered") {
            handleRegisteredPayload(firstResp.optJSONObject("payload"))
            return Result.success(Unit)
        } else if (respType == "response") {
            _state.update {
                it.copy(
                    prompting = true,
                    feedbackMessage = "Confirme o pareamento na tela da TV!"
                )
            }
            val finalDeferred = CompletableDeferred<JSONObject>()
            pendingResponses[regId] = finalDeferred
            val finalResp = withTimeoutOrNull(45_000L) { finalDeferred.await() }
            pendingResponses.remove(regId)

            if (finalResp != null && finalResp.optString("type") == "registered") {
                handleRegisteredPayload(finalResp.optJSONObject("payload"))
                return Result.success(Unit)
            }
            _state.update { it.copy(prompting = false) }
            return Result.failure(IllegalStateException("Pareamento não confirmado na TV"))
        }

        return Result.failure(IllegalStateException("Resposta inesperada da TV: $respType"))
    }

    private fun handleRegisteredPayload(payload: JSONObject?) {
        val newKey = payload?.optString("client-key").orEmpty()
        if (newKey.isNotBlank()) {
            preferences.saveClientKey(newKey)
        }
        _state.update {
            it.copy(
                connected = true,
                paired = true,
                prompting = false
            )
        }
        scope.launch {
            runCatching { fetchAudioStatus() }
            runCatching {
                // Registra teclado remoto para manter canal de IME pronto na TV
                sendCommand("ssap://com.webos.service.ime/registerRemoteKeyboard", null, 2000)
            }
        }
    }

    private fun createMainSocketListener(openDeferred: CompletableDeferred<Boolean>) =
        object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                openDeferred.complete(true)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val json = runCatching { JSONObject(text) }.getOrNull() ?: return
                val id = json.optString("id")
                val type = json.optString("type")
                val payload = json.optJSONObject("payload")

                if (payload != null) {
                    val hasVol = payload.has("volume")
                    val hasMute = payload.has("muted")
                    if (hasVol || hasMute) {
                        _state.update { current ->
                            current.copy(
                                volume = if (hasVol) payload.optInt("volume", current.volume) else current.volume,
                                muted = if (hasMute) payload.optBoolean("muted", current.muted) else current.muted
                            )
                        }
                    }
                }

                var deferred = pendingResponses[id]
                if (deferred == null && (type == "registered" || type == "response")) {
                    val regEntry = pendingResponses.entries.firstOrNull { it.key.startsWith("register") }
                    if (regEntry != null) {
                        deferred = regEntry.value
                    }
                }

                if (deferred != null) {
                    if (type == "registered" || type == "error" || !id.startsWith("register")) {
                        pendingResponses.remove(id)
                    }
                    deferred.complete(json)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!openDeferred.isCompleted) {
                    openDeferred.complete(false)
                }
                if (mainWebSocket === webSocket) {
                    _state.update {
                        it.copy(connected = false, paired = false, prompting = false)
                    }
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (mainWebSocket === webSocket) {
                    _state.update {
                        it.copy(connected = false, paired = false, prompting = false)
                    }
                }
            }
        }

    suspend fun sendCommand(
        uri: String,
        payload: JSONObject? = null,
        timeoutMs: Long = 3500
    ): Result<JSONObject> = withContext(Dispatchers.IO) {
        val connResult = ensureConnected()
        if (connResult.isFailure) {
            return@withContext Result.failure(
                connResult.exceptionOrNull() ?: IllegalStateException("Sem conexão com a TV")
            )
        }

        val ws = mainWebSocket
            ?: return@withContext Result.failure(IllegalStateException("WebSocket indisponível"))

        val reqId = nextId("req")
        val deferred = CompletableDeferred<JSONObject>()
        pendingResponses[reqId] = deferred

        val msg = JSONObject().apply {
            put("id", reqId)
            put("type", "request")
            put("uri", uri)
            if (payload != null) {
                put("payload", payload)
            }
        }

        if (!ws.send(msg.toString())) {
            pendingResponses.remove(reqId)
            closeSocketsInternal()
            return@withContext Result.failure(IllegalStateException("Falha ao enviar comando para a TV"))
        }

        val response = withTimeoutOrNull(timeoutMs) { deferred.await() }
        pendingResponses.remove(reqId)

        if (response == null) {
            return@withContext Result.failure(IllegalStateException("Tempo limite esgotado ($uri)"))
        }

        if (response.optString("type") == "error") {
            val errMsg = response.optString("error", "Erro retornado pela TV")
            return@withContext Result.failure(IllegalStateException(errMsg))
        }

        Result.success(response)
    }

    /**
     * Botão Único de Energia (Power Toggle) ultra-rápido:
     * - Se já estiver conectado -> Desliga via SSAP imediatamente.
     * - Se não estiver conectado -> Dispara o Magic Packet Wake-on-LAN IMEDIATAMENTE (sem esperar
     *   timeout de WebSocket ou mutex), ligando a TV no menor tempo possível.
     */
    suspend fun togglePower(): Result<String> = withContext(Dispatchers.IO) {
        val currentlyConnected = _state.value.connected && _state.value.paired
        if (currentlyConnected) {
            val offRes = powerOff()
            if (offRes.isSuccess) {
                return@withContext Result.success("Desligando a TV...")
            }
        }

        val cfg = preferences.getConfig()
        if (cfg.macAddress.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Configure o endereço MAC da TV para ligar via Wake-on-LAN")
            )
        }

        return@withContext runCatching {
            // Disparo imediato do pacote Wake-on-LAN (0ms de espera)
            TvWakeOnLan.sendWakeOnLan(cfg.macAddress, cfg.broadcastAddress)

            // Em paralelo, verifica se a TV liga e conecta assim que ficar pronta
            scope.launch {
                repeat(8) { attempt ->
                    delay(1800L + attempt * 800L)
                    if (ensureConnected(2000).isSuccess) {
                        setFeedback("TV LG conectada!")
                        return@launch
                    }
                }
            }
            "Wake-on-LAN enviado! Ligando TV..."
        }
    }

    suspend fun powerOff(): Result<Unit> {
        val res = sendCommand("ssap://system/turnOff", null, 2500)
        return if (res.isSuccess) {
            closeSocketsInternal()
            _state.update {
                it.copy(
                    connected = false,
                    paired = false,
                    feedbackMessage = "TV desligada"
                )
            }
            Result.success(Unit)
        } else {
            Result.failure(res.exceptionOrNull() ?: Exception("Erro ao desligar TV"))
        }
    }

    suspend fun volumeUp(): Result<Unit> =
        sendCommand("ssap://audio/volumeUp", null, 2000).map { }

    suspend fun volumeDown(): Result<Unit> =
        sendCommand("ssap://audio/volumeDown", null, 2000).map { }

    suspend fun setVolume(volume: Int): Result<Unit> {
        val target = volume.coerceIn(0, 100)
        val payload = JSONObject().put("volume", target)
        return sendCommand("ssap://audio/setVolume", payload, 2000).map {
            _state.update { s -> s.copy(volume = target) }
        }
    }

    suspend fun fetchAudioStatus(): Result<Pair<Int, Boolean>> {
        val res = sendCommand("ssap://audio/getVolume", null, 2000)
        return res.map { json ->
            val payload = json.optJSONObject("payload")
            val vol = payload?.optInt("volume", _state.value.volume) ?: _state.value.volume
            val muted = payload?.optBoolean("muted", _state.value.muted) ?: _state.value.muted
            _state.update { it.copy(volume = vol, muted = muted) }
            vol to muted
        }
    }

    suspend fun toggleMute(): Result<Boolean> {
        fetchAudioStatus()
        val newMute = !_state.value.muted
        val payload = JSONObject().put("mute", newMute)
        val res = sendCommand("ssap://audio/setMute", payload, 2000)
        return res.map {
            _state.update { s -> s.copy(muted = newMute) }
            newMute
        }
    }

    suspend fun launchApp(appId: String, contentId: String = ""): Result<Unit> {
        val payload = JSONObject().apply {
            put("id", appId)
            if (contentId.isNotEmpty()) {
                put("contentId", contentId)
            }
        }
        val first = sendCommand("ssap://system.launcher/open", payload, 3500)
        if (first.isSuccess) return Result.success(Unit)

        return sendCommand("ssap://com.webos.applicationManager/launch", payload, 3500).map { }
    }

    /**
     * Abre uma URL, canal (@handle) ou Live diretamente no aplicativo oficial do YouTube da TV.
     */
    suspend fun launchYouTubeUrl(videoOrChannelUrl: String): Result<Unit> = withContext(Dispatchers.IO) {
        val contentTarget = YouTubeLiveResolver.resolveContentTarget(videoOrChannelUrl)
        launchYouTubeWithContentTarget(contentTarget)
    }

    /**
     * Pesquisa por um termo no YouTube e abre o vídeo/live correspondente diretamente na TV.
     */
    suspend fun searchAndLaunchYouTube(query: String): Result<Unit> = withContext(Dispatchers.IO) {
        val contentTarget = YouTubeLiveResolver.resolveSearchQueryTarget(query)
            ?: return@withContext Result.failure(IllegalStateException("Nenhum vídeo encontrado para \"$query\""))
        launchYouTubeWithContentTarget(contentTarget)
    }

    private suspend fun launchYouTubeWithContentTarget(contentTarget: String): Result<Unit> {
        val closePayload = JSONObject().put("id", "youtube.leanback.v4")
        if (sendCommand("ssap://system.launcher/close", closePayload, 1800).isFailure) {
            sendCommand("ssap://com.webos.applicationManager/close", closePayload, 1800)
        }
        delay(500)

        val launchPayload = JSONObject().apply {
            put("id", "youtube.leanback.v4")
            put("contentId", contentTarget)
            put("params", JSONObject().put("contentTarget", contentTarget))
        }

        val first = sendCommand("ssap://system.launcher/launch", launchPayload, 3500)
        if (first.isSuccess) return Result.success(Unit)

        return sendCommand("ssap://com.webos.applicationManager/launch", launchPayload, 3500).map { }
    }

    /**
     * Consulta na própria TV LG os aplicativos instalados (launchPoints + listApps)
     * e faz o download dos ícones reais fornecidos pela TV para cache local.
     */
    suspend fun fetchInstalledApps(iconsDir: File): Result<List<TvInstalledApp>> = withContext(Dispatchers.IO) {
        if (!iconsDir.exists()) {
            iconsDir.mkdirs()
        }

        val appsById = linkedMapOf<String, TvInstalledApp>()

        // 1. Tenta listLaunchPoints (traz os apps visíveis na barra Home da TV e URLs de ícones HTTP/HTTPS)
        val lpRes = sendCommand("ssap://com.webos.applicationManager/listLaunchPoints", null, 4500)
        lpRes.onSuccess { json ->
            val points = json.optJSONObject("payload")?.optJSONArray("launchPoints") ?: JSONArray()
            for (i in 0 until points.length()) {
                val item = points.optJSONObject(i) ?: continue
                val id = item.optString("id").trim()
                val title = item.optString("title").trim()
                val visible = item.optBoolean("visible", true)
                if (id.isEmpty() || title.isEmpty() || !visible) continue
                val iconUrl = item.optString("icon")
                    .ifEmpty { item.optString("largeIcon") }
                    .ifEmpty { item.optString("miniicon") }
                appsById[id] = TvInstalledApp(appId = id, title = title, iconUrl = iconUrl)
            }
        }

        // 2. Complementa com listApps
        val appsRes = sendCommand("ssap://com.webos.applicationManager/listApps", null, 4500)
        appsRes.onSuccess { json ->
            val apps = json.optJSONObject("payload")?.optJSONArray("apps") ?: JSONArray()
            for (i in 0 until apps.length()) {
                val item = apps.optJSONObject(i) ?: continue
                val id = item.optString("id").trim()
                val title = item.optString("title").trim()
                val visible = item.optBoolean("visible", true)
                if (id.isEmpty() || title.isEmpty() || !visible) continue
                if (!appsById.containsKey(id)) {
                    val iconUrl = item.optString("icon")
                        .ifEmpty { item.optString("largeIcon") }
                        .ifEmpty { item.optString("miniicon") }
                    appsById[id] = TvInstalledApp(appId = id, title = title, iconUrl = iconUrl)
                }
            }
        }

        if (appsById.isEmpty()) {
            val err = lpRes.exceptionOrNull() ?: appsRes.exceptionOrNull()
                ?: IllegalStateException("Não foi possível listar os apps da TV")
            return@withContext Result.failure(err)
        }

        // Faz o download dos ícones diretamente da TV para o cache local do celular
        val resultList = appsById.values
            .sortedBy { it.title.lowercase() }
            .map { app ->
                val safeName = app.appId.replace(Regex("[^a-zA-Z0-9._-]"), "_") + ".png"
                val iconFile = File(iconsDir, safeName)
                val localPath = if (iconFile.exists() && iconFile.length() > 0) {
                    iconFile.absolutePath
                } else if (app.iconUrl.startsWith("http")) {
                    downloadFile(app.iconUrl, iconFile)
                } else {
                    null
                }
                app.copy(localIconPath = localPath)
            }

        preferences.saveCachedInstalledApps(resultList)
        Result.success(resultList)
    }

    /**
     * Baixa uma imagem/ícone (da TV ou do YouTube) e salva no arquivo de destino.
     */
    suspend fun downloadFile(url: String, destFile: File): String? = withContext(Dispatchers.IO) {
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

    /**
     * Envia texto digitado (ou ditado por voz) no celular diretamente para o campo de texto da TV.
     */
    suspend fun sendTextInput(
        text: String,
        replace: Boolean = false,
        pressEnter: Boolean = false
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("text", text)
            put("replace", if (replace) 1 else 0)
        }
        val res = sendCommand("ssap://com.webos.service.ime/insertText", payload, 3000)
        if (res.isFailure) {
            return@withContext Result.failure(
                res.exceptionOrNull() ?: IllegalStateException("Erro ao enviar texto para a TV")
            )
        }
        if (pressEnter) {
            delay(150)
            sendCommand("ssap://com.webos.service.ime/sendEnterKey", null, 2000)
        }
        Result.success(Unit)
    }

    /**
     * Pressiona Enter/Confirmar no teclado virtual da TV.
     */
    suspend fun sendImeEnter(): Result<Unit> = withContext(Dispatchers.IO) {
        val res = sendCommand("ssap://com.webos.service.ime/sendEnterKey", null, 2000)
        if (res.isSuccess) return@withContext Result.success(Unit)
        sendButton("ENTER")
    }

    /**
     * Apaga caracteres (Backspace) no campo de texto ativo da TV.
     */
    suspend fun deleteTextCharacters(count: Int = 1): Result<Unit> = withContext(Dispatchers.IO) {
        val payload = JSONObject().put("count", count.coerceAtLeast(1))
        sendCommand("ssap://com.webos.service.ime/deleteCharacters", payload, 2000).map { }
    }

    /**
     * Envia botão de navegação/direcional (UP, DOWN, LEFT, RIGHT, ENTER, BACK, HOME).
     */
    suspend fun sendButton(buttonName: String): Result<Unit> = withContext(Dispatchers.IO) {
        val btn = buttonName.trim().uppercase()

        if (btn == "HOME") {
            return@withContext launchApp("com.webos.app.home")
        }

        val message = "type:button\nname:$btn\n\n"
        val firstTry = ensureInputSocket().mapCatching { ws ->
            if (!ws.send(message)) throw IllegalStateException("Falha no envio pelo input socket")
        }
        if (firstTry.isSuccess) return@withContext Result.success(Unit)

        // Reconecta o input socket e tenta mais uma vez
        runCatching { inputWebSocket?.cancel() }
        inputWebSocket = null

        ensureInputSocket().mapCatching { ws ->
            if (!ws.send(message)) throw IllegalStateException("Falha ao enviar botão $btn")
        }
    }

    private suspend fun ensureInputSocket(): Result<WebSocket> {
        inputWebSocket?.let { return Result.success(it) }

        val resp = sendCommand(
            "ssap://com.webos.service.networkinput/getPointerInputSocket",
            null,
            3000
        ).getOrElse { return Result.failure(it) }

        val socketPath = resp.optJSONObject("payload")?.optString("socketPath").orEmpty()
        if (socketPath.isBlank()) {
            return Result.failure(IllegalStateException("TV não retornou socketPath para botões"))
        }

        val openDeferred = CompletableDeferred<Boolean>()
        val ws = insecureOkHttpClient.newWebSocket(
            Request.Builder().url(socketPath).build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    openDeferred.complete(true)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (!openDeferred.isCompleted) openDeferred.complete(false)
                    if (inputWebSocket === webSocket) inputWebSocket = null
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (inputWebSocket === webSocket) inputWebSocket = null
                }
            }
        )

        val connected = withTimeoutOrNull(3000L) { openDeferred.await() } ?: false
        return if (connected) {
            inputWebSocket = ws
            Result.success(ws)
        } else {
            runCatching { ws.cancel() }
            Result.failure(IllegalStateException("Não foi possível abrir socket de botões da TV"))
        }
    }

    fun disconnect() {
        closeSocketsInternal()
        _state.update { it.copy(connected = false, paired = false, prompting = false) }
    }

    private fun closeSocketsInternal() {
        runCatching { inputWebSocket?.close(1000, "closed") }
        runCatching { mainWebSocket?.close(1000, "closed") }
        inputWebSocket = null
        mainWebSocket = null
    }
}
