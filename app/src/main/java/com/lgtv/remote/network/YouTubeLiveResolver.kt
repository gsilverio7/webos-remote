package com.lgtv.remote.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
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

        // 2. ID puro de 11 caracteres
        if (raw.length == 11 && !raw.contains("/") && !raw.contains(".") && !raw.contains("?")) {
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
     * Resolve uma URL de vídeo ou canal /live do YouTube para o formato "v=VIDEO_ID" exigido pelo webOS.
     */
    suspend fun resolveContentTarget(videoOrChannelUrl: String): String = withContext(Dispatchers.IO) {
        val (staticId, staticTarget) = parseStaticTarget(videoOrChannelUrl)
        if (staticId.isNotEmpty()) {
            return@withContext staticTarget
        }

        val raw = videoOrChannelUrl.trim()
        var resolvedId = ""
        if (raw.startsWith("http") && raw.contains("/live")) {
            resolvedId = fetchLiveVideoId(raw).orEmpty()
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
