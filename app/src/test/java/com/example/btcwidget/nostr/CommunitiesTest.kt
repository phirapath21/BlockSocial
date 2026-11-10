package com.example.btcwidget.nostr

import com.example.btcwidget.nostr.core.Communities
import com.example.btcwidget.nostr.core.Event
import com.example.btcwidget.nostr.core.PublicChat
import com.example.btcwidget.nostr.core.RelayGroups
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunitiesTest {
    private val owner = "a".repeat(64)
    private val mod = "b".repeat(64)
    private val user = "c".repeat(64)
    private fun ev(id: String, pk: String, kind: Int, tags: List<List<String>>, content: String = "", at: Long = 1) = Event(id, pk, at, kind, tags, content, "")

    @Test
    fun communityPostsShowOnceApproved() {
        val def = ev("d", owner, Communities.DEFINITION, listOf(listOf("d", "bitcoin"), listOf("name", "Bitcoin"), listOf("p", mod, "", "moderator"), listOf("relay", "wss://r.example/")))
        val c = Communities.of(def)!!
        assertEquals(setOf(mod), c.moderators)
        assertEquals(listOf("wss://r.example"), c.relays)
        val post = Communities.post(user, c, "gm")
        assertEquals(c.coordinate, Communities.communityOf(post.signed("s")))
        val p1 = ev("p1", user, 1111, post.tags); val p2 = ev("p2", user, 1111, post.tags); val p3 = ev("p3", mod, 1111, post.tags)
        val byMod = ev("x", mod, Communities.APPROVAL, listOf(listOf("a", c.coordinate), listOf("e", "p1")))
        val byStranger = ev("y", user, Communities.APPROVAL, listOf(listOf("a", c.coordinate), listOf("e", "p2")))
        assertEquals(setOf("p1", "p3"), Communities.approved(c, listOf(p1, p2, p3), listOf(byMod, byStranger)))
        val joined = Communities.listWith(user, null, c.coordinate, join = true)
        assertEquals(listOf(listOf("a", c.coordinate)), joined.tags)
        assertEquals(emptyList<List<String>>(), Communities.listWith(user, joined.signed("s"), c.coordinate, join = false).tags)
    }

    @Test
    fun channelsUseTheCreatorsNewestMetadata() {
        val create = ev("ch", owner, PublicChat.CREATE, emptyList(), "{\"name\":\"Bitcoin chat\",\"about\":\"talk\"}")
        val rename = ev("m1", owner, PublicChat.METADATA, listOf(listOf("e", "ch", "wss://r")), "{\"name\":\"BTC\"}", at = 5)
        val hijack = ev("m2", user, PublicChat.METADATA, listOf(listOf("e", "ch")), "{\"name\":\"spam\"}", at = 9)
        assertEquals("BTC", PublicChat.channel(create, listOf(rename, hijack))!!.name)
        val msg = PublicChat.message(user, "ch", "hello").signed("s")
        assertEquals("ch", PublicChat.channelOf(msg))
        assertNull(PublicChat.channel(ev("bad", owner, PublicChat.CREATE, emptyList(), "not json")))
    }

    @Test
    fun groupAddressesAndMetadata() {
        val a = RelayGroups.parse("groups.0xchat.com'abc_1")!!
        assertEquals("wss://groups.0xchat.com", a.relay)
        assertEquals("groups.0xchat.com'abc_1", a.toString())
        assertNull(RelayGroups.parse("no-quote"))
        assertNull(RelayGroups.parse("host'bad id"))
        val meta = ev("g", "d".repeat(64), RelayGroups.METADATA, listOf(listOf("d", "abc_1"), listOf("name", "Plebs"), listOf("closed")))
        val g = RelayGroups.metadata(a, listOf(meta))
        assertEquals("Plebs", g.name); assertTrue(g.closed)
        assertEquals(listOf(listOf("h", "abc_1")), RelayGroups.chat(user, a, "hi").tags)
    }
}

class DvmTest {
    @Test
    fun feedServicesAndResults() {
        val dvm = "d".repeat(64)
        val ann = Event("a", dvm, 1, com.example.btcwidget.nostr.core.Dvm.ANNOUNCEMENT, listOf(listOf("d", "trending"), listOf("k", "5300")), "{\"name\":\"Trending\",\"about\":\"hot\",\"picture\":\"http://x\"}", "")
        val s = com.example.btcwidget.nostr.core.Dvm.service(ann)!!
        assertEquals("Trending", s.name); assertNull(s.picture)
        assertNull(com.example.btcwidget.nostr.core.Dvm.service(ann.copy(tags = listOf(listOf("d", "x"), listOf("k", "5050")))))
        val n1 = "1".repeat(64); val n2 = "2".repeat(64)
        val res = Event("r", dvm, 2, com.example.btcwidget.nostr.core.Dvm.FEED_RESULT, listOf(listOf("e", "req"), listOf("p", "me")), "[[\"e\",\"$n1\"],[\"e\",\"$n2\"],[\"e\",\"bad\"]]", "")
        assertEquals(listOf(n1, n2), com.example.btcwidget.nostr.core.Dvm.noteIds(res))
        assertTrue(com.example.btcwidget.nostr.core.Dvm.answers(res, "req", dvm))
        val req = com.example.btcwidget.nostr.core.Dvm.request("me", dvm, listOf("wss://a", "wss://b"))
        assertEquals(listOf("relays", "wss://a", "wss://b"), req.tags[1])
    }
}
