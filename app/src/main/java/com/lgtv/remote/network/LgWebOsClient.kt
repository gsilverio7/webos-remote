package com.lgtv.remote.network

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
            .connectTimeout(3, TimeUnit.SECONDS)
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

    /**
     * Garante que a conexão WebSocket SSAP esteja ativa e pareada com a TV LG.
     */
    suspend fun ensureConnected(timeoutMs: Long = 3500): Result<Unit> = withContext(Dispatchers.IO) {
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
            val discovered = TvSsdpDiscovery.discoverLgTv(3000)
            if (discovered != null) {
                targetIp = discovered.ip
                preferences.saveIpAddress(targetIp)
            } else {
                return Result.failure(IllegalStateException("TV não encontrada na rede Wi-Fi"))
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

        val endpoints = listOf(
            "wss://$targetIp:3001",
            "ws://$targetIp:3000"
        )

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

        val firstResp = withTimeoutOrNull(15_000L) { regDeferred.await() }
            ?: run {
                pendingResponses.remove(regId)
                return Result.failure(IllegalStateException("Tempo esgotado aguardando registro na TV"))
            }

        val respType = firstResp.optString("type")
        if (respType == "registered") {
            handleRegisteredPayload(firstResp.optJSONObject("payload"))
            return Result.success(Unit)
        } else if (respType == "response") {
            // Prompt de confirmação exibido na TV
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
        timeoutMs: Long = 4000
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
     * Botão Único de Energia (Power Toggle):
     * - Se a TV estiver ligada/conectada -> Envia comando SSAP para desligar (ssap://system/turnOff).
     * - Se a TV estiver em standby/desconectada -> Envia Magic Packet Wake-on-LAN para ligar.
     */
    suspend fun togglePower(): Result<String> = withContext(Dispatchers.IO) {
        val currentlyConnected = _state.value.connected && _state.value.paired
        val canReachTv = if (currentlyConnected) {
            true
        } else {
            // Tenta uma conexão rápida de 1.2s para checar se a TV já está ligada
            ensureConnected(timeoutMs = 1200).isSuccess
        }

        if (canReachTv) {
            val offRes = powerOff()
            if (offRes.isSuccess) {
                return@withContext Result.success("Desligando a TV...")
            }
        }

        // TV está desligada / standby -> envia Wake-on-LAN
        val cfg = preferences.getConfig()
        if (cfg.macAddress.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Configure o endereço MAC da TV para ligar via Wake-on-LAN")
            )
        }

        return@withContext runCatching {
            TvWakeOnLan.sendWakeOnLan(cfg.macAddress, cfg.broadcastAddress)
            // Agenda tentativas automáticas de conexão enquanto a TV liga
            scope.launch {
                repeat(6) { attempt ->
                    delay(3000L + attempt * 1000L)
                    if (ensureConnected(2500).isSuccess) {
                        setFeedback("TV LG conectada!")
                        return@launch
                    }
                }
            }
            "Sinal para ligar (Wake-on-LAN) enviado!"
        }
    }

    suspend fun powerOff(): Result<Unit> {
        val res = sendCommand("ssap://system/turnOff", null, 3000)
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
        val first = sendCommand("ssap://system.launcher/open", payload, 4000)
        if (first.isSuccess) return Result.success(Unit)

        return sendCommand("ssap://com.webos.applicationManager/launch", payload, 4000).map { }
    }

    /**
     * Abre uma URL/Live diretamente no aplicativo oficial do YouTube da TV (youtube.leanback.v4).
     */
    suspend fun launchYouTubeUrl(videoOrChannelUrl: String): Result<Unit> = withContext(Dispatchers.IO) {
        val contentTarget = YouTubeLiveResolver.resolveContentTarget(videoOrChannelUrl)

        // Fecha eventual instância já aberta do YouTube na TV antes de lançar o novo vídeo,
        // garantindo que o webOS carregue o novo contentTarget em vez de apenas focar a Home.
        val closePayload = JSONObject().put("id", "youtube.leanback.v4")
        if (sendCommand("ssap://system.launcher/close", closePayload, 2000).isFailure) {
            sendCommand("ssap://com.webos.applicationManager/close", closePayload, 2000)
        }
        delay(600)

        val launchPayload = JSONObject().apply {
            put("id", "youtube.leanback.v4")
            put("contentId", contentTarget)
            put("params", JSONObject().put("contentTarget", contentTarget))
        }

        val first = sendCommand("ssap://system.launcher/launch", launchPayload, 4000)
        if (first.isSuccess) return@withContext Result.success(Unit)

        sendCommand("ssap://com.webos.applicationManager/launch", launchPayload, 4000).map { }
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
