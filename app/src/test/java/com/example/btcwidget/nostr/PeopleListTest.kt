package com.example.btcwidget.nostr

import com.example.btcwidget.nostr.core.Event
import com.example.btcwidget.nostr.core.HomeFeed
import com.example.btcwidget.nostr.core.Kind
import com.example.btcwidget.nostr.core.PeopleList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PeopleListTest {
    private val me = "a".repeat(64)
    private val bob = "b".repeat(64)
    private val carol = "c".repeat(64)
    private fun ev(tags: List<List<String>>, at: Long = 10, content: String = "") = Event("id$at", me, at, PeopleList.KIND, tags, content, "")

    @Test
    fun editsKeepPrivateMembersAndOtherTags() {
        val src = ev(listOf(listOf("d", "devs"), listOf("title", "Devs"), listOf("image", "https://x/i.png"), listOf("p", bob)), content = "encrypted-private-members")
        val list = PeopleList.parse(src)!!
        assertEquals("Devs", list.title)
        assertEquals(listOf(bob), list.members)
        val out = list.with(carol).without(bob).toEvent(now = 5)
        assertEquals(PeopleList.KIND, out.kind)
        assertEquals(11L, out.createdAt) // newer than the version it replaces, even with a slow clock
        assertEquals("encrypted-private-members", out.content)
        assertTrue(listOf("image", "https://x/i.png") in out.tags)
        assertEquals(listOf(carol), out.tags.filter { it[0] == "p" }.map { it[1] })
        assertEquals(listOf("d", "devs"), out.tags.first())
    }

    @Test
    fun newestVersionWinsAndDeletedListsDisappear() {
        val old = ev(listOf(listOf("d", "x"), listOf("title", "Old"), listOf("p", bob)), at = 1)
        val renamed = ev(listOf(listOf("d", "x"), listOf("title", "New"), listOf("p", bob)), at = 2)
        val other = ev(listOf(listOf("d", "y"), listOf("title", "another")), at = 3)
        assertEquals(listOf("another", "New"), PeopleList.latest(listOf(old, renamed, other)).map { it.title })
        val emptied = ev(listOf(listOf("d", "x")), at = 4)
        assertEquals(listOf("another"), PeopleList.latest(listOf(old, renamed, other, emptied)).map { it.title })
        // Other apps' own lists aren't people's lists.
        assertNull(PeopleList.parse(ev(listOf(listOf("d", "mute"), listOf("p", bob)))))
    }

    @Test
    fun deletionAndFeedId() {
        val list = PeopleList.create(me, "  Friends ")
        assertEquals("Friends", list.title)
        assertEquals(16, list.d.length)
        val del = PeopleList.deletion(list, 99)
        assertEquals(Kind.DELETION, del.kind)
        assertEquals(listOf("a", "30000:$me:${list.d}"), del.tags[0])
        val feed = HomeFeed.People(me, list.d, "Friends: close")
        assertEquals(feed, HomeFeed.parse(feed.id))
    }
}

class MediaPostsTest {
    @org.junit.Test
    fun mediaFeedsAndImetaUrls() {
        val pk = "a".repeat(64)
        val pic = com.example.btcwidget.nostr.core.Event("id", pk, 1, com.example.btcwidget.nostr.core.Kind.PICTURE, listOf(
            listOf("imeta", "url https://x/a.jpg", "m image/jpeg", "blurhash LEHV6nWB2yk8pyo0adR*.7kCMdnj"),
            listOf("imeta", "url https://x/b.jpg", "m image/jpeg"),
            listOf("imeta", "m image/jpeg") // no url
        ), "caption", "")
        org.junit.Assert.assertEquals(listOf("https://x/a.jpg", "https://x/b.jpg"), com.example.btcwidget.nostr.core.Imeta.urls(pic))
        org.junit.Assert.assertEquals("LEHV6nWB2yk8pyo0adR*.7kCMdnj", com.example.btcwidget.nostr.core.Imeta.blurhash(pic, "https://x/a.jpg"))
        org.junit.Assert.assertNull(com.example.btcwidget.nostr.core.Imeta.blurhash(pic, "https://x/b.jpg"))
        listOf("pictures", "pictures:all", "videos", "videos:all").forEach { id ->
            org.junit.Assert.assertEquals(id, com.example.btcwidget.nostr.core.HomeFeed.parse(id)!!.id)
        }
    }
}

class HashtagFollowTest {
    @org.junit.Test
    fun followingATopicKeepsEverythingElse() {
        val me = "a".repeat(64); val bob = "b".repeat(64)
        val current = com.example.btcwidget.nostr.core.Event("id", me, 1, com.example.btcwidget.nostr.core.Kind.CONTACTS,
            listOf(listOf("p", bob), listOf("t", "Nostr")), "{\"wss://r\":{}}", "")
        val on = com.example.btcwidget.nostr.core.Events.contactsHashtag(me, current, "#Bitcoin", true)
        org.junit.Assert.assertEquals(listOf(listOf("p", bob), listOf("t", "Nostr"), listOf("t", "bitcoin")), on.tags)
        org.junit.Assert.assertEquals(current.content, on.content) // relay map in content kept
        val off = com.example.btcwidget.nostr.core.Events.contactsHashtag(me, on.signed("s"), "nostr", false)
        org.junit.Assert.assertEquals(listOf(listOf("p", bob), listOf("t", "bitcoin")), off.tags)
    }
}

class FollowPackTest {
    @org.junit.Test
    fun packsParseAndFollowAllKeepsTheList() {
        val me = "a".repeat(64); val bob = "b".repeat(64); val carol = "c".repeat(64); val dave = "d".repeat(64)
        val e = com.example.btcwidget.nostr.core.Event("p", dave, 5, com.example.btcwidget.nostr.core.FollowPack.KIND,
            listOf(listOf("d", "btc"), listOf("title", "Bitcoin devs"), listOf("p", bob), listOf("p", carol), listOf("p", "bad")), "", "")
        val pack = com.example.btcwidget.nostr.core.FollowPack.parse(e)!!
        org.junit.Assert.assertEquals(listOf(bob, carol), pack.members)
        org.junit.Assert.assertEquals("39089:$dave:btc", pack.address)
        val current = com.example.btcwidget.nostr.core.Event("c", me, 1, com.example.btcwidget.nostr.core.Kind.CONTACTS, listOf(listOf("p", bob), listOf("t", "nostr")), "relays", "")
        val next = com.example.btcwidget.nostr.core.Events.contactsAdding(me, current, pack.members + me)
        org.junit.Assert.assertEquals(listOf(listOf("p", bob), listOf("t", "nostr"), listOf("p", carol)), next.tags) // no duplicates, not yourself
        org.junit.Assert.assertEquals("relays", next.content)
    }
}
