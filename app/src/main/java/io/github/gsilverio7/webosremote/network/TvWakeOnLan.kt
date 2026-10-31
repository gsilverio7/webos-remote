package io.github.gsilverio7.webosremote.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

object TvWakeOnLan {

    private val wolScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Constrói o Magic Packet Wake-on-LAN de 102 bytes (6x 0xFF seguidos de 16 repetições do MAC).
     */
    fun buildMagicPacket(macAddress: String): ByteArray {
        val cleaned = macAddress.trim().replace("-", ":")
        val hexParts = cleaned.split(":").filter { it.isNotEmpty() }
        require(hexParts.size == 6) {
            "Endereço MAC inválido: $macAddress (esperado formato XX:XX:XX:XX:XX:XX)"
        }

        val macBytes = ByteArray(6)
        for (i in 0 until 6) {
            val parsed = hexParts[i].toIntOrNull(16)
                ?: throw IllegalArgumentException("Byte hexadecimal inválido no MAC: ${hexParts[i]}")
            require(parsed in 0..255) { "Valor fora do intervalo no MAC: ${hexParts[i]}" }
            macBytes[i] = parsed.toByte()
        }

        val packet = ByteArray(6 + 16 * 6)
        for (i in 0 until 6) {
            packet[i] = 0xFF.toByte()
        }
        for (rep in 0 until 16) {
            System.arraycopy(macBytes, 0, packet, 6 + rep * 6, 6)
        }
        return packet
    }

    /**
     * Envia o pacote Wake-on-LAN imediatamente via UDP broadcast nas portas 9 e 7,
     * disparando rajadas extras em background para garantir entrega imediata na Wi-Fi.
     */
    suspend fun sendWakeOnLan(macAddress: String, broadcastIp: String) = withContext(Dispatchers.IO) {
        val packetBytes = buildMagicPacket(macAddress)
        val targets = linkedSetOf(
            broadcastIp.trim().ifEmpty { "192.168.0.255" },
            "255.255.255.255"
        )
        val ports = intArrayOf(9, 7)

        val resolvedTargets = targets.mapNotNull { host ->
            runCatching { InetAddress.getByName(host) }.getOrNull()
        }

        fun sendBurst(socket: DatagramSocket) {
            for (address in resolvedTargets) {
                for (port in ports) {
                    runCatching {
                        val datagram = DatagramPacket(packetBytes, packetBytes.size, address, port)
                        socket.send(datagram)
                    }
                }
            }
        }

        // Disparo imediato (0ms de espera)
        DatagramSocket().use { socket ->
            socket.broadcast = true
            sendBurst(socket)
        }

        // Rajadas adicionais de reforço em background sem bloquear a resposta da UI
        wolScope.launch {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                repeat(3) {
                    delay(70)
                    sendBurst(socket)
                }
            }
        }
    }
}
