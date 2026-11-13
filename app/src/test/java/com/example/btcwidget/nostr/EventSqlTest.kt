package com.example.btcwidget.nostr

import com.example.btcwidget.nostr.core.Event
import com.example.btcwidget.nostr.core.EventSql
import com.example.btcwidget.nostr.core.Events
import com.example.btcwidget.nostr.core.Filter
import com.example.btcwidget.nostr.core.Kind
import com.example.btcwidget.nostr.core.Schnorr
import com.example.btcwidget.nostr.core.UnsignedEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager

/** The local event database's SQL, run against real SQLite (the same engine as Android). */
class EventSqlTest {
    private val db: Connection = DriverManager.getConnection("jdbc:sqlite::memory:").also { c ->
        EventSql.SCHEMA.forEach { c.createStatement().execute(it) }
    }
    private val sec = Schnorr.newSecretKey()
    private val pub = Schnorr.publicKey(sec)

    private fun sign(u: UnsignedEvent) = u.signed(Schnorr.sign(u.id, sec))

    private fun run(sql: String, args: List<Any?>) = db.prepareStatement(sql).use { st ->
        args.forEachIndexed { i, a -> st.setObject(i + 1, a) }
        st.execute()
    }

    private fun ids(sql: String, args: List<Any?>): List<String> = db.prepareStatement(sql).use { st ->
        args.forEachIndexed { i, a -> st.setObject(i + 1, a) }
        st.executeQuery().use { r -> buildList { while (r.next()) add(r.getString(1)) } }
    }

    /** What EventStore does on the phone. */
    private fun insert(e: Event) {
        EventSql.newerExists(e)?.let { if (ids(it.sql, it.args).isNotEmpty()) return }
        EventSql.olderVersions(e)?.let { st -> ids(st.sql, st.args).forEach { old -> run(EventSql.DELETE_EVENT, listOf(old)); run(EventSql.DELETE_TAGS, listOf(old)) } }
        run(EventSql.INSERT_EVENT, EventSql.insertArgs(e, 0))
        EventSql.indexedTags(e).forEach { (n, v) -> run(EventSql.INSERT_TAG, listOf(e.id, n, v)) }
    }

    /** Bound as strings, as Android's rawQuery does. */
    private fun query(f: Filter): List<Event> {
        val st = EventSql.select(f)!!
        return ids(st.sql, st.args.map { it.toString() }).map { Event.fromJson(it) }
    }

    @Test
    fun answersFiltersLocally() {
        val root = sign(Events.note(pub, "root").copy(createdAt = 10))
        val reply = sign(UnsignedEvent(pub, 20, Kind.TEXT_NOTE, listOf(listOf("e", root.id, "", "root"), listOf("p", "x".repeat(64))), "reply"))
        val other = sign(UnsignedEvent(pub, 30, Kind.REACTION, listOf(listOf("e", root.id)), "+"))
        listOf(root, reply, other, root).forEach(::insert)

        assertEquals(listOf(reply.id, root.id), query(Filter(authors = listOf(pub), kinds = listOf(Kind.TEXT_NOTE))).map { it.id })
        assertEquals(listOf(other.id, reply.id), query(Filter(tags = mapOf("e" to listOf(root.id)))).map { it.id })
        assertEquals(listOf(reply.id), query(Filter(kinds = listOf(Kind.TEXT_NOTE), tags = mapOf("e" to listOf(root.id)))).map { it.id })
        assertEquals(listOf(root.id), query(Filter(ids = listOf(root.id))).map { it.id })
        assertEquals(listOf(reply.id), query(Filter(authors = listOf(pub), since = 15, until = 25)).map { it.id })
        assertEquals(1, query(Filter(authors = listOf(pub), limit = 1)).size)
        // Round trip keeps the exact signed event.
        assertEquals(true, query(Filter(ids = listOf(reply.id))).single().verify())
        assertNull(EventSql.select(Filter(search = "x")))
    }

    @Test
    fun replaceableKindsKeepOnlyTheNewest() {
        val old = sign(UnsignedEvent(pub, 100, Kind.METADATA, emptyList(), "{\"name\":\"old\"}"))
        val new = sign(UnsignedEvent(pub, 200, Kind.METADATA, emptyList(), "{\"name\":\"new\"}"))
        insert(old); insert(new); insert(old)
        assertEquals(listOf(new.id), query(Filter(authors = listOf(pub), kinds = listOf(Kind.METADATA))).map { it.id })

        val a1 = sign(UnsignedEvent(pub, 100, Kind.ARTICLE, listOf(listOf("d", "post")), "v1"))
        val a2 = sign(UnsignedEvent(pub, 200, Kind.ARTICLE, listOf(listOf("d", "post")), "v2"))
        val b = sign(UnsignedEvent(pub, 150, Kind.ARTICLE, listOf(listOf("d", "other")), "b"))
        listOf(a1, b, a2).forEach(::insert)
        assertEquals(listOf(a2.id, b.id), query(Filter(kinds = listOf(Kind.ARTICLE))).map { it.id })
        assertEquals(listOf(a2.id), query(Filter(kinds = listOf(Kind.ARTICLE), tags = mapOf("d" to listOf("post")))).map { it.id })
    }

    @Test
    fun pruningKeepsMineAndProfiles() {
        val otherSec = Schnorr.newSecretKey(); val other = Schnorr.publicKey(otherSec)
        fun theirs(t: Long, kind: Int = Kind.TEXT_NOTE) = UnsignedEvent(other, t, kind, emptyList(), "n$t").let { it.signed(Schnorr.sign(it.id, otherSec)) }
        (1L..10L).forEach { insert(theirs(it)) }
        insert(theirs(0, Kind.METADATA))
        insert(sign(UnsignedEvent(pub, 0, Kind.TEXT_NOTE, emptyList(), "mine, oldest")))
        EventSql.prune(keep = 3, me = pub).forEach { run(it.sql, it.args) }
        val left = query(Filter(limit = 100))
        assertEquals(listOf(10L, 9L, 8L), left.filter { it.pubkey == other && it.kind == Kind.TEXT_NOTE }.map { it.createdAt })
        assertEquals(1, left.count { it.pubkey == pub })
        assertEquals(1, left.count { it.kind == Kind.METADATA })
        assertEquals(0, ids("SELECT event_id FROM tags WHERE event_id NOT IN (SELECT id FROM events)", emptyList()).size)
    }

    @Test
    fun offlineFullTextSearchAndOutboxAndNwc() {
        val n1 = sign(Events.note(pub, "Learning about Bitcoin Lightning and Nostr outbox").copy(createdAt = 100))
        val n2 = sign(Events.note(pub, "Just a random post").copy(createdAt = 200))
        insert(n1); insert(n2)

        val st = EventSql.search("bitcoin lightning", kinds = listOf(Kind.TEXT_NOTE), limit = 10)!!
        val found = ids(st.sql, st.args.map { it.toString() }).map { Event.fromJson(it) }
            .filter { EventSql.matchesSearch(it, "bitcoin lightning") }
        assertEquals(listOf(n1.id), found.map { it.id })

        // Outbox read vs write relays
        val relayList = sign(UnsignedEvent(pub, 300, Kind.RELAY_LIST, listOf(
            listOf("r", "wss://write.example.com", "write"),
            listOf("r", "wss://read.example.com", "read"),
            listOf("r", "wss://both.example.com")
        ), ""))
        assertEquals(listOf("wss://write.example.com", "wss://both.example.com"), com.example.btcwidget.nostr.core.Outbox.writeRelays(relayList))
        assertEquals(listOf("wss://read.example.com", "wss://both.example.com"), com.example.btcwidget.nostr.core.Outbox.readRelays(relayList))

        // NWC request/response round-trip
        val walletSec = Schnorr.newSecretKey()
        val walletPub = Schnorr.publicKey(walletSec)
        val clientSec = Schnorr.newSecretKey()
        val conn = com.example.btcwidget.nostr.core.Nwc.Connection(walletPub, listOf("wss://relay.example.com"), clientSec, null)
        val balReq = com.example.btcwidget.nostr.core.Nwc.getBalanceRequest(conn, nip44 = true)
        assertEquals(com.example.btcwidget.nostr.core.Nwc.REQUEST, balReq.kind)
        val encReply = com.example.btcwidget.nostr.core.Nip44.encrypt(
            """{"result_type":"get_balance","result":{"balance":21000000}}""",
            walletSec, balReq.pubkey
        )
        val balResp = UnsignedEvent(walletPub, 400, com.example.btcwidget.nostr.core.Nwc.RESPONSE, listOf(listOf("e", balReq.id)), encReply)
            .let { it.signed(Schnorr.sign(it.id, walletSec)) }
        assertEquals(21_000L, com.example.btcwidget.nostr.core.Nwc.parseBalance(conn, balResp).getOrThrow())
    }
}
