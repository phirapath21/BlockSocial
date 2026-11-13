package com.example.btcwidget.nostr

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.btcwidget.nostr.core.Event
import com.example.btcwidget.nostr.core.EventSql
import com.example.btcwidget.nostr.core.Filter

/**
 * The local event database: notes, profiles, lists and message wraps seen so far, so
 * screens open from the phone and work offline while relays refresh. SQLite in the app's
 * private storage ("nostr_events.db", excluded from backup); all of it is public Nostr data
 * or still-encrypted messages. Deleted on logout. SQL lives in [EventSql] (tested on the JVM).
 */
class EventStore(context: Context) : SQLiteOpenHelper(context.applicationContext, NAME, null, EventSql.VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        EventSql.SCHEMA.forEach { db.execSQL(it) }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // A cache: rebuilding it from relays is always safe.
        db.execSQL("DROP TABLE IF EXISTS events")
        db.execSQL("DROP TABLE IF EXISTS tags")
        onCreate(db)
    }

    override fun onConfigure(db: SQLiteDatabase) {
        db.enableWriteAheadLogging()
    }

    /** Stores verified events; older versions of replaceable lists are replaced. */
    fun insert(events: Collection<Event>) {
        if (events.isEmpty()) return
        val db = runCatching { writableDatabase }.getOrNull() ?: return
        val now = System.currentTimeMillis() / 1000
        db.beginTransaction()
        try {
            db.compileStatement(EventSql.INSERT_EVENT).use { insertEventSt ->
                db.compileStatement(EventSql.INSERT_TAG).use { insertTagSt ->
                    db.compileStatement(EventSql.DELETE_EVENT).use { delEventSt ->
                        db.compileStatement(EventSql.DELETE_TAGS).use { delTagsSt ->
                            for (e in events) {
                                val newer = EventSql.newerExists(e)
                                if (newer != null && exists(db, newer)) continue
                                EventSql.olderVersions(e)?.let { st ->
                                    ids(db, st).forEach { old ->
                                        delEventSt.clearBindings(); bind(delEventSt, listOf(old)); delEventSt.execute()
                                        delTagsSt.clearBindings(); bind(delTagsSt, listOf(old)); delTagsSt.execute()
                                    }
                                }
                                insertEventSt.clearBindings()
                                bind(insertEventSt, EventSql.insertArgs(e, now))
                                val inserted = insertEventSt.executeInsert()
                                if (inserted == -1L) continue // already stored
                                EventSql.indexedTags(e).forEach { (n, v) ->
                                    insertTagSt.clearBindings()
                                    bind(insertTagSt, listOf(e.id, n, v))
                                    insertTagSt.executeInsert()
                                }
                            }
                        }
                    }
                }
            }
            db.setTransactionSuccessful()
        } catch (e: Exception) {
            android.util.Log.w("EventStore", "insert failed", e)
        } finally {
            db.endTransaction()
        }
    }

    /** Events matching any of [filters], newest first, de-duplicated. */
    fun query(filters: List<Filter>): List<Event> {
        val db = runCatching { readableDatabase }.getOrNull() ?: return emptyList()
        return filters.flatMap { f ->
            val st = EventSql.select(f) ?: return@flatMap emptyList()
            runCatching {
                db.rawQuery(st.sql, st.args.map { it.toString() }.toTypedArray()).use { c ->
                    buildList { while (c.moveToNext()) c.getString(0)?.let { json -> runCatching { Event.fromJson(json) }.getOrNull()?.let { add(it) } } }
                }
            }.getOrDefault(emptyList())
        }.distinctBy { it.id }.sortedByDescending { it.createdAt }
    }

    /** Offline full-text search across locally cached events of [kinds], newest first. */
    fun search(query: String, kinds: List<Int> = com.example.btcwidget.nostr.core.Kind.POSTS, limit: Int = 50): List<Event> {
        val st = EventSql.search(query, kinds, limit * 2) ?: return emptyList()
        val db = runCatching { readableDatabase }.getOrNull() ?: return emptyList()
        return runCatching {
            db.rawQuery(st.sql, st.args.map { it.toString() }.toTypedArray()).use { c ->
                buildList {
                    while (c.moveToNext() && size < limit) {
                        val json = c.getString(0) ?: continue
                        val event = runCatching { Event.fromJson(json) }.getOrNull() ?: continue
                        if (EventSql.matchesSearch(event, query)) add(event)
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    fun prune(keep: Int, me: String?) {
        val db = runCatching { writableDatabase }.getOrNull() ?: return
        runCatching { EventSql.prune(keep, me).forEach { exec(db, it.sql, it.args) } }
    }

    fun clear() {
        val db = runCatching { writableDatabase }.getOrNull() ?: return
        runCatching { db.execSQL("DELETE FROM events"); db.execSQL("DELETE FROM tags") }
    }

    private fun exists(db: SQLiteDatabase, st: EventSql.Statement): Boolean =
        db.rawQuery(st.sql, st.args.map { it.toString() }.toTypedArray()).use { it.moveToFirst() }

    private fun ids(db: SQLiteDatabase, st: EventSql.Statement): List<String> =
        db.rawQuery(st.sql, st.args.map { it.toString() }.toTypedArray()).use { c -> buildList { while (c.moveToNext()) c.getString(0)?.let { add(it) } } }

    private fun exec(db: SQLiteDatabase, sql: String, args: List<Any?>) =
        db.compileStatement(sql).use { st -> bind(st, args); st.execute() }

    private fun bind(st: android.database.sqlite.SQLiteStatement, args: List<Any?>) {
        args.forEachIndexed { i, a ->
            when (a) {
                null -> st.bindNull(i + 1)
                is Int -> st.bindLong(i + 1, a.toLong())
                is Long -> st.bindLong(i + 1, a)
                else -> st.bindString(i + 1, a.toString())
            }
        }
    }

    companion object {
        const val NAME = "nostr_events.db"
    }
}
