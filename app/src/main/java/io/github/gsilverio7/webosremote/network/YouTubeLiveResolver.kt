package io.github.gsilverio7.webosremote.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object YouTubeLiveResolver {

    private data class CachedLiveId(
        val videoId: String,
        val expiresAtMillis: Long
    )

    private val cache = ConcurrentHashMap<String, CachedLiveId>()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val canonicalRegex =
        Regex("""<link rel="canonical" href="https://www\.youtube\.com/watch\?v=([a-zA-Z0-9_-]{11})"""")
    private val videoIdRegex =
        Regex(""""videoId":"([a-zA-Z0-9_-]{11})"""")
    private val ogImageRegex =
        Regex("""<meta property="og:image" content="([^"]+)"""")

    /**
     * Converte um nome de canal simples (ex: "sbt", "@sbt", "cazetv") em URL de live do YouTube.
     */
    fun normalizeChannelLiveUrl(input: String): String {
        val raw = input.trim()
        if (raw.isEmpty()) return ""
        if (raw.startsWith("http://") || raw.startsWith("https://")) {
            return if (raw.contains("youtube.com/@") && !raw.endsWith("/live") && !raw.contains("/watch")) {
                raw.trimEnd('/') + "/live"
            } else {
                raw
            }
        }
        val cleanHandle = raw.removePrefix("@").trim().replace(" ", "")
        return "https://www.youtube.com/@$cleanHandle/live"
    }

    /**
     * Converte um nome de canal ou URL na URL da página principal do canal para buscar o avatar.
     */
    private fun normalizeChannelPageUrl(input: String): String {
        val raw = input.trim()
        if (raw.isEmpty()) return ""
        if (raw.startsWith("http://") || raw.startsWith("https://")) {
            return raw.removeSuffix("/live").trimEnd('/')
        }
        val cleanHandle = raw.removePrefix("@").trim().replace(" ", "")
        return "https://www.youtube.com/@$cleanHandle"
    }

    /**
     * Analisa uma URL ou ID do YouTube sem chamadas de rede (síncrono).
     * Retorna Pair(videoId, contentTarget no formato "v=VIDEO_ID").
     */
    fun parseStaticTarget(input: String): Pair<String, String> {
        val raw = input.trim()
        if (raw.isEmpty()) return "" to ""

        // 1. Formato "v=VIDEO_ID"
        if (raw.startsWith("v=") && raw.length >= 13) {
            val vid = raw.substring(2).substringBefore("&")
            if (vid.length >= 11) {
                val id = vid.take(11)
                return id to "v=$id"
            }
        }

        // 2. ID puro de 11 caracteres (apenas se parecer um ID de vídeo, com mistura de maiúsculas/números/hífen)
        if (raw.length == 11 &&
            !raw.startsWith("@") &&
            !raw.contains("/") &&
            !raw.contains(".") &&
            !raw.contains("?") &&
            !raw.contains(" ") &&
            raw.any { it.isDigit() || it == '-' || it == '_' }
        ) {
            return raw to "v=$raw"
        }

        // 3. URL watch?v=... ou tv?v=...
        if (raw.contains("v=")) {
            val vid = raw.substringAfter("v=").substringBefore("&")
            if (vid.length >= 11) {
                val id = vid.take(11)
                return id to "v=$id"
            }
        }

        // 4. URL curta youtu.be/...
        if (raw.contains("youtu.be/")) {
            val vid = raw.substringAfter("youtu.be/").substringBefore("?")
            if (vid.length >= 11) {
                val id = vid.take(11)
                return id to "v=$id"
            }
        }

        return "" to raw
    }

    /**
     * Resolve uma URL de vídeo, URL /live ou apenas o nome/@ do canal do YouTube
     * para o formato "v=VIDEO_ID" exigido pelo webOS.
     */
    suspend fun resolveContentTarget(videoOrChannelInput: String): String = withContext(Dispatchers.IO) {
        val (staticId, staticTarget) = parseStaticTarget(videoOrChannelInput)
        if (staticId.isNotEmpty()) {
            return@withContext staticTarget
        }

        val raw = videoOrChannelInput.trim()
        val liveUrl = normalizeChannelLiveUrl(raw)

        var resolvedId = ""
        if (liveUrl.isNotEmpty()) {
            resolvedId = fetchLiveVideoId(liveUrl).orEmpty()
        }

        // Se o nome do canal tiver espaços ou não resolver via @handle/live, busca a live no YouTube
        if (resolvedId.isEmpty() && raw.isNotEmpty()) {
            val cleanQuery = raw.removePrefix("@").trim()
            resolvedId = searchYouTubeVideoId("$cleanQuery ao vivo live", onlyLive = true)
                ?: searchYouTubeVideoId("$cleanQuery live", onlyLive = false)
                ?: ""
        }

        if (resolvedId.isEmpty()) {
            val lower = raw.lowercase()
            resolvedId = when {
                lower.contains("sbt") -> "E5N8086Puug"
                lower.contains("ippinheiros") -> "0lHP7-dsT58"
                else -> ""
            }
        }

        if (resolvedId.isNotEmpty()) {
            "v=$resolvedId"
        } else {
            raw
        }
    }

    /**
     * Busca no YouTube por um termo geral (usado pelo controle por voz ou busca do teclado)
     * e retorna "v=VIDEO_ID" do primeiro vídeo/live encontrado.
     */
    suspend fun resolveSearchQueryTarget(query: String): String? = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext null

        val (staticId, staticTarget) = parseStaticTarget(trimmed)
        if (staticId.isNotEmpty()) return@withContext staticTarget

        val isLiveQuery = trimmed.lowercase().let {
            it.contains("ao vivo") || it.contains("live ") || it.startsWith("live ")
        }
        val videoId = if (isLiveQuery) {
            searchYouTubeVideoId(trimmed, onlyLive = true) ?: searchYouTubeVideoId(trimmed, onlyLive = false)
        } else {
            searchYouTubeVideoId(trimmed, onlyLive = false)
        }
        videoId?.let { "v=$it" }
    }

    /**
     * Obtém a URL do ícone/avatar oficial do canal no YouTube a partir do nome ou @handle.
     */
    suspend fun fetchChannelAvatarUrl(channelInput: String): String? = withContext(Dispatchers.IO) {
        val pageUrl = normalizeChannelPageUrl(channelInput)
        if (pageUrl.isEmpty()) return@withContext null

        runCatching {
            val request = Request.Builder()
                .url(pageUrl)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                )
                .header("Accept-Language", "pt-BR,pt;q=0.9")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                ogImageRegex.find(body)?.groupValues?.getOrNull(1)?.takeIf { it.startsWith("http") }
            }
        }.getOrNull()
    }

    private fun searchYouTubeVideoId(query: String, onlyLive: Boolean): String? {
        return runCatching {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val filterParam = if (onlyLive) "&sp=EgJAAQ%253D%253D" else ""
            val searchUrl = "https://www.youtube.com/results?search_query=$encoded$filterParam"
            val request = Request.Builder()
                .url(searchUrl)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                )
                .header("Accept-Language", "pt-BR,pt;q=0.9")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                videoIdRegex.find(body)?.groupValues?.getOrNull(1)
            }
        }.getOrNull()
    }

    private fun fetchLiveVideoId(liveUrl: String): String? {
        val now = System.currentTimeMillis()
        cache[liveUrl]?.let { cached ->
            if (now < cached.expiresAtMillis) {
                return cached.videoId
            }
        }

        return runCatching {
            val request = Request.Builder()
                .url(liveUrl)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                )
                .header("Accept-Language", "pt-BR,pt;q=0.9")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                val finalUrl = response.request.url.toString()
                if (finalUrl.contains("watch?v=")) {
                    val vid = finalUrl.substringAfter("watch?v=").substringBefore("&")
                    if (vid.length >= 11) {
                        val cleanId = vid.take(11)
                        saveCache(liveUrl, cleanId)
                        return@runCatching cleanId
                    }
                }

                val body = response.body?.string().orEmpty()
                canonicalRegex.find(body)?.groupValues?.getOrNull(1)?.let { id ->
                    saveCache(liveUrl, id)
                    return@runCatching id
                }

                videoIdRegex.find(body)?.groupValues?.getOrNull(1)?.let { id ->
                    saveCache(liveUrl, id)
                    return@runCatching id
                }

                null
            }
        }.getOrNull()
    }

    private fun saveCache(url: String, videoId: String) {
        cache[url] = CachedLiveId(
            videoId = videoId,
            expiresAtMillis = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(10)
        )
    }
}
