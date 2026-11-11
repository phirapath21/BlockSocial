package com.example.btcwidget.nostr

import com.example.btcwidget.nostr.core.Event
import com.example.btcwidget.nostr.core.Kind
import com.example.btcwidget.nostr.core.RelayInfo
import com.example.btcwidget.nostr.core.RelayLists
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RelayListsTest {
    private val me = "a".repeat(64)

    @Test
    fun nip65ReadWriteRoundTrip() {
        val list = Event("i", me, 1, Kind.RELAY_LIST, listOf(
            listOf("r", "wss://both.example/"), listOf("r", "wss://read.example", "read"), listOf("r", "wss://write.example", "WRITE"),
            listOf("r", "not a url"), listOf("r", "wss://both.example")
        ), "", "")
        val entries = RelayLists.entries(list)
        assertEquals(listOf(
            RelayLists.Entry("wss://both.example", true, true),
            RelayLists.Entry("wss://read.example", true, false),
            RelayLists.Entry("wss://write.example", false, true)
        ), entries)
        val out = RelayLists.relayList(me, entries + RelayLists.Entry("wss://none.example", false, false))
        assertEquals(listOf(listOf("r", "wss://both.example"), listOf("r", "wss://read.example", "read"), listOf("r", "wss://write.example", "write")), out.tags)
    }

    @Test
    fun relaySetsKeepOtherTags() {
        val current = Event("i", me, 1, RelayLists.BLOCKED, listOf(listOf("relay", "wss://bad.example"), listOf("title", "x")), "secret", "")
        assertEquals(listOf("wss://bad.example"), RelayLists.relays(current))
        val next = RelayLists.relaySet(me, RelayLists.BLOCKED, listOf("wss://worse.example", "junk"), current)
        assertEquals(listOf(listOf("title", "x"), listOf("relay", "wss://worse.example")), next.tags)
        assertEquals("secret", next.content)
    }

    @Test
    fun nip11Info() {
        val info = RelayInfo.parse("""{"name":"Damus","description":"A relay","pubkey":"${"b".repeat(64)}","supported_nips":[1,11,"42",11],
            "software":"git+https://github.com/hoytech/strfry","version":"1.0","limitation":{"payment_required":true,"auth_required":false}}""")!!
        assertEquals("Damus", info.name)
        assertEquals(listOf(1, 11, 42), info.nips)
        assertTrue(info.paymentRequired)
        assertEquals("https://relay.example/x", RelayInfo.httpUrl("wss://relay.example/x"))
        assertNull(RelayInfo.parse("<html>"))
    }
}
