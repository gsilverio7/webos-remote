package io.github.gsilverio7.webosremote

import io.github.gsilverio7.webosremote.network.TvWakeOnLan
import io.github.gsilverio7.webosremote.network.YouTubeLiveResolver
import org.junit.Assert.assertEquals
import org.junit.Test

class YouTubeAndWolTest {

    @Test
    fun `buildMagicPacket generates valid 102-byte Wake-on-LAN packet`() {
        val packet = TvWakeOnLan.buildMagicPacket("D0:CD:BF:59:F9:2E")
        assertEquals(102, packet.size)
        for (i in 0 until 6) {
            assertEquals(0xFF.toByte(), packet[i])
        }
        assertEquals(0xD0.toByte(), packet[6])
        assertEquals(0xCD.toByte(), packet[7])
        assertEquals(0xBF.toByte(), packet[8])
        assertEquals(0x59.toByte(), packet[9])
        assertEquals(0xF9.toByte(), packet[10])
        assertEquals(0x2E.toByte(), packet[11])
    }

    @Test
    fun `parseStaticTarget formats YouTube video IDs with v= prefix`() {
        val (id1, target1) = YouTubeLiveResolver.parseStaticTarget("E5N8086Puug")
        assertEquals("E5N8086Puug", id1)
        assertEquals("v=E5N8086Puug", target1)

        val (id2, target2) = YouTubeLiveResolver.parseStaticTarget("https://www.youtube.com/watch?v=E5N8086Puug")
        assertEquals("E5N8086Puug", id2)
        assertEquals("v=E5N8086Puug", target2)

        val (id3, target3) = YouTubeLiveResolver.parseStaticTarget("https://youtu.be/0lHP7-dsT58?t=5")
        assertEquals("0lHP7-dsT58", id3)
        assertEquals("v=0lHP7-dsT58", target3)
    }

    @Test
    fun `normalizeChannelLiveUrl converts simple channel name or handle to live URL`() {
        assertEquals("https://www.youtube.com/@sbt/live", YouTubeLiveResolver.normalizeChannelLiveUrl("sbt"))
        assertEquals("https://www.youtube.com/@sbt/live", YouTubeLiveResolver.normalizeChannelLiveUrl("@sbt"))
        assertEquals("https://www.youtube.com/@cazetv/live", YouTubeLiveResolver.normalizeChannelLiveUrl("cazetv"))
    }
}
