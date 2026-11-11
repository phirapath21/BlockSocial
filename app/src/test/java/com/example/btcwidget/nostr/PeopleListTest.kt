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
