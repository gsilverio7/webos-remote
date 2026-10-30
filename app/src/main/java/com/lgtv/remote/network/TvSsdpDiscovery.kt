package com.lgtv.remote.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.URI
import java.util.concurrent.TimeUnit

data class DiscoveredTv(
    val ip: String,
    val friendlyName: String,
    val modelName: String
)

object TvSsdpDiscovery {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS)
        .build()

    suspend fun discoverLgTv(timeoutMs: Int = 3000): DiscoveredTv? = withContext(Dispatchers.IO) {
        val multicastAddr = InetAddress.getByName("239.255.255.250")
        val searchTargets = listOf(
            "urn:lge-com:service:webos-second-screen:1",
            "urn:schemas-upnp-org:device:MediaRenderer:1",
            "ssdp:all"
        )

        DatagramSocket().use { socket ->
            socket.soTimeout = 700
            for (st in searchTargets) {
                val query = buildString {
                    append("M-SEARCH * HTTP/1.1\r\n")
                    append("HOST: 239.255.255.250:1900\r\n")
                    append("MAN: \"ssdp:discover\"\r\n")
                    append("MX: 2\r\n")
                    append("ST: $st\r\n\r\n")
                }
                val bytes = query.toByteArray(Charsets.UTF_8)
                runCatching {
                    socket.send(DatagramPacket(bytes, bytes.size, multicastAddr, 1900))
                }
            }

            val deadline = System.currentTimeMillis() + timeoutMs
            val checkedLocations = mutableSetOf<String>()
            val buffer = ByteArray(4096)

            while (System.currentTimeMillis() < deadline) {
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(packet)
                } catch (_: SocketTimeoutException) {
                    continue
                } catch (_: Exception) {
                    break
                }

                val responseStr = String(packet.data, 0, packet.length, Charsets.UTF_8)
                val location = extractHeader(responseStr, "LOCATION") ?: continue
                if (!checkedLocations.add(location)) continue

                val hostIp = runCatching { URI(location).host }.getOrNull() ?: continue
                if (hostIp.isBlank()) continue

                val req = Request.Builder().url(location).get().build()
                val xmlBody = runCatching {
                    httpClient.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) resp.body?.string() else null
                    }
                }.getOrNull() ?: continue

                val lower = xmlBody.lowercase()
                val isLg = lower.contains("lg electronics") ||
                    lower.contains("webos") ||
                    lower.contains("[lg]") ||
                    lower.contains("au801")

                if (isLg) {
                    val friendly = extractXmlTag(xmlBody, "friendlyName") ?: "LG webOS TV"
                    val model = extractXmlTag(xmlBody, "modelName") ?: "webOS"
                    return@withContext DiscoveredTv(
                        ip = hostIp,
                        friendlyName = friendly,
                        modelName = model
                    )
                }
            }
        }
        null
    }

    private fun extractHeader(response: String, headerName: String): String? {
        val prefix = "$headerName:".lowercase()
        for (line in response.split("\r\n", "\n")) {
            val trimmed = line.trim()
            if (trimmed.lowercase().startsWith(prefix)) {
                return trimmed.substring(prefix.length).trim()
            }
        }
        return null
    }

    private fun extractXmlTag(xml: String, tag: String): String? {
        val regex = Regex("<$tag>([^<]+)</$tag>", RegexOption.IGNORE_CASE)
        return regex.find(xml)?.groupValues?.getOrNull(1)?.trim()
    }
}
