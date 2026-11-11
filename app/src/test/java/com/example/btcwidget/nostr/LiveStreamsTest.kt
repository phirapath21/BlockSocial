package com.example.btcwidget.nostr

import com.example.btcwidget.nostr.core.Event
import com.example.btcwidget.nostr.core.LiveStreams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LiveStreamsTest {
    private val service = "a".repeat(64)
    private val host = "b".repeat(64)
    private val now = 1_000_000L
    private fun ev(d: String, status: String, at: Long = now - 60, viewers: String? = null, extra: List<List<String>> = emptyList()) =
        Event("id$d$at", service, at, LiveStreams.KIND, listOfNotNull(
            listOf("d", d), listOf("title", "Stream $d"), listOf("status", status),
            listOf("streaming", "https://cdn.example/$d.m3u8"), viewers?.let { listOf("current_participants", it) },
            listOf("p", host, "", "Host"), listOf("relays", "wss://relay.one", "wss://relay.two")
        ) + extra, "", "")

    @Test
    fun parsesHostStatusAndChatAddress() {
        val live = LiveStreams.parse(ev("x", "live", viewers = "42"), now)!!
        assertEquals(host, live.host)
        assertEquals("30311:$service:x", live.address)
        assertEquals(LiveStreams.Status.LIVE, live.status)
        assertEquals(42, live.viewers)
        assertEquals(listOf("wss://relay.one", "wss://relay.two"), live.relays)
        val msg = LiveStreams.chat("c".repeat(64), live, "gm", now)
        assertEquals(LiveStreams.CHAT, msg.kind)
        assertEquals(live.address, LiveStreams.chatOf(msg.signed("s")))
    }

    @Test
    fun staleLiveCountsAsEndedAndOrderIsLiveThenPlanned() {
        assertEquals(LiveStreams.Status.ENDED, LiveStreams.parse(ev("old", "live", at = now - 7200), now)!!.status)
        val list = LiveStreams.latest(listOf(
            ev("p", "planned"), ev("small", "live", viewers = "3"), ev("big", "live", viewers = "90"),
            ev("big", "ended", at = now - 120) // older version of "big"
        ), now)
        assertEquals(listOf("Stream big", "Stream small", "Stream p"), list.map { it.title })
        assertNull(LiveStreams.parse(Event("i", service, now, 1, emptyList(), "", ""), now))
    }
}
