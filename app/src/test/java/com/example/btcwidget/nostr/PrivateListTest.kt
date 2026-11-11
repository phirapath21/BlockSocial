package com.example.btcwidget.nostr

import com.example.btcwidget.nostr.core.Event
import com.example.btcwidget.nostr.core.Kind
import com.example.btcwidget.nostr.core.MuteRules
import com.example.btcwidget.nostr.core.PrivateTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivateListTest {
    private val bob = "b".repeat(64)
    private val carol = "c".repeat(64)

    @Test
    fun privateTagsRoundTrip() {
        val tags = listOf(listOf("p", bob), listOf("word", "scam"), listOf("e", "ab".repeat(32), "wss://r"))
        assertEquals(tags, PrivateTags.parse(PrivateTags.serialize(tags)))
        assertEquals(emptyList<List<String>>(), PrivateTags.parse("not json"))
        assertEquals(emptyList<List<String>>(), PrivateTags.parse("{\"a\":1}"))
        assertTrue(PrivateTags.isNip04("abc?iv=def"))
        assertFalse(PrivateTags.isNip04("AgAAAA"))
    }

    @Test
    fun muteRulesCombinePublicAndPrivate() {
        val list = Event("id", "a".repeat(64), 1, Kind.MUTE_LIST, listOf(listOf("p", bob)), "encrypted", "")
        val rules = MuteRules.of(list, listOf(listOf("p", carol), listOf("word", "Scam"), listOf("t", "#Spam")))
        assertEquals(setOf(bob, carol), rules.pubkeys)
        assertEquals(setOf("scam"), rules.words)
        assertEquals(setOf("spam"), rules.hashtags)
    }
}
