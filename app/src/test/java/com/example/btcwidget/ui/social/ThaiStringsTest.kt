package com.example.btcwidget.ui.social

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThaiStringsTest {
    @After fun english() = Lang.apply(Lang.Choice.ENGLISH, "en")

    @Test fun everyTranslationKeepsItsPlaceholders() {
        val placeholder = Regex("""\{\d+\}""")
        THAI.forEach { (en, th) ->
            assertEquals("placeholders in \"$en\"", placeholder.findAll(en).map { it.value }.sorted().toList(), placeholder.findAll(th).map { it.value }.sorted().toList())
            assertTrue("empty Thai for \"$en\"", th.isNotBlank())
        }
    }

    @Test fun englishByDefaultThaiWhenChosen() {
        Lang.apply(Lang.Choice.ENGLISH, "th")
        assertEquals("Mute Alice", tr("Mute {0}", "Alice"))
        Lang.apply(Lang.Choice.THAI, "en")
        assertEquals("ปิดเสียง Alice", tr("Mute {0}", "Alice"))
        assertEquals("not in the table", tr("not in the table"))
    }

    @Test fun phoneLanguageDecidesWhenNotChosen() {
        Lang.apply(Lang.Choice.SYSTEM, "th")
        assertTrue(Lang.thai)
        Lang.apply(Lang.Choice.SYSTEM, "en")
        assertTrue(!Lang.thai)
    }

    @Test fun pluralOnlyInEnglish() {
        Lang.apply(Lang.Choice.ENGLISH, "en")
        assertEquals("s", plural(3)); assertEquals("", plural(1))
        Lang.apply(Lang.Choice.THAI, "en")
        assertEquals("", plural(3))
    }
}
