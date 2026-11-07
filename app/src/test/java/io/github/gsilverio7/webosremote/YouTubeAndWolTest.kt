package io.github.gsilverio7.webosremote

import io.github.gsilverio7.webosremote.data.TvBrand
import io.github.gsilverio7.webosremote.data.TvPreferences
import io.github.gsilverio7.webosremote.network.SamsungTizenClient
import io.github.gsilverio7.webosremote.network.TvWakeOnLan
import io.github.gsilverio7.webosremote.network.YouTubeLiveResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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

    @Test
    fun `mapButtonToSamsungKey translates navigation and media commands to Tizen KEY codes`() {
        assertEquals("KEY_UP", SamsungTizenClient.mapButtonToSamsungKey("UP"))
        assertEquals("KEY_DOWN", SamsungTizenClient.mapButtonToSamsungKey("DOWN"))
        assertEquals("KEY_LEFT", SamsungTizenClient.mapButtonToSamsungKey("LEFT"))
        assertEquals("KEY_RIGHT", SamsungTizenClient.mapButtonToSamsungKey("RIGHT"))
        assertEquals("KEY_ENTER", SamsungTizenClient.mapButtonToSamsungKey("ENTER"))
        assertEquals("KEY_RETURN", SamsungTizenClient.mapButtonToSamsungKey("BACK"))
        assertEquals("KEY_HOME", SamsungTizenClient.mapButtonToSamsungKey("HOME"))
        assertEquals("KEY_CHUP", SamsungTizenClient.mapButtonToSamsungKey("CHANNELUP"))
        assertEquals("KEY_CHDOWN", SamsungTizenClient.mapButtonToSamsungKey("CHANNELDOWN"))
        assertEquals("KEY_VOLUP", SamsungTizenClient.mapButtonToSamsungKey("VOLUMEUP"))
        assertEquals("KEY_VOLDOWN", SamsungTizenClient.mapButtonToSamsungKey("VOLUMEDOWN"))
        assertEquals("KEY_MUTE", SamsungTizenClient.mapButtonToSamsungKey("MUTE"))
    }

    @Test
    fun `defaultShortcuts provide independent LG webOS and Samsung Tizen app targets`() {
        val lgDefaults = TvPreferences.defaultShortcuts(TvBrand.LG)
        val samsungDefaults = TvPreferences.defaultShortcuts(TvBrand.SAMSUNG)
        assertEquals(9, lgDefaults.size)
        assertEquals(9, samsungDefaults.size)
        assertEquals(lgDefaults.map { it.label }, samsungDefaults.map { it.label })
        assertNotEquals(lgDefaults[0].target, samsungDefaults[0].target)
        assertEquals("111299001912", samsungDefaults[7].target)
    }
}
