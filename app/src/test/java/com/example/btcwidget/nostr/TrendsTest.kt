package com.example.btcwidget.nostr

import com.example.btcwidget.nostr.core.Event
import org.junit.Assert.assertEquals
import org.junit.Test

/** Trending hashtags worked out from notes: ranked by different people, then notes. */
class TrendsTest {
    private fun note(id: String, author: String, vararg tags: String) =
        Event(id, author, 1, 1, tags.map { listOf("t", it) }, "", "")

    @Test fun ranksByPeopleNotByOneBusyAccount() {
        val events = listOf(
            // One account posting #spam a lot.
            note("1", "a", "spam"), note("2", "a", "spam"), note("3", "a", "spam"), note("4", "a", "spam"),
            // Three people on #bitcoin, two on #siamstr.
            note("5", "a", "Bitcoin"), note("6", "b", "bitcoin"), note("7", "c", "#bitcoin"),
            note("8", "b", "siamstr"), note("9", "c", "siamstr"), note("10", "c", "siamstr"),
        )
        val trends = NostrRepository.trendsOf(events)
        assertEquals(listOf("bitcoin", "siamstr"), trends.map { it.tag })
        assertEquals(NostrRepository.Trend("bitcoin", 3, 3), trends[0])
        assertEquals(NostrRepository.Trend("siamstr", 2, 3), trends[1])
    }

    @Test fun countsANoteOnceAndIgnoresDuplicates() {
        val events = listOf(note("1", "a", "nostr", "Nostr"), note("1", "a", "nostr"), note("2", "b", "nostr"))
        assertEquals(listOf(NostrRepository.Trend("nostr", 2, 2)), NostrRepository.trendsOf(events))
    }
}
