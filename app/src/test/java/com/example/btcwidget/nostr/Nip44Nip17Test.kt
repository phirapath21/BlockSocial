package com.example.btcwidget.nostr

import com.example.btcwidget.nostr.core.Event
import com.example.btcwidget.nostr.core.Hex
import com.example.btcwidget.nostr.core.Nip17
import com.example.btcwidget.nostr.core.Nip44
import com.example.btcwidget.nostr.core.Schnorr
import com.example.btcwidget.nostr.core.UnsignedEvent
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * NIP-44 against the official test vectors (paulmillr/nip44, MIT; copied from Amethyst's
 * test resources into src/test/resources/nip44.vectors.json), and NIP-17 round trips.
 */
class Nip44Nip17Test {
    private val v2: JSONObject = JSONObject(
        javaClass.classLoader!!.getResourceAsStream("nip44.vectors.json")!!.readBytes().decodeToString()
    ).getJSONObject("v2")
    private val valid = v2.getJSONObject("valid")
    private val invalid = v2.getJSONObject("invalid")

    private fun each(arr: org.json.JSONArray, block: (JSONObject) -> Unit) = (0 until arr.length()).forEach { block(arr.getJSONObject(it)) }

    @Test
    fun conversationKeys() = each(valid.getJSONArray("get_conversation_key")) { v ->
        assertEquals(v.getString("conversation_key"), Hex.encode(Nip44.conversationKey(v.getString("sec1"), v.getString("pub2"))))
    }

    @Test
    fun messageKeys() {
        val mk = valid.getJSONObject("get_message_keys")
        val ck = Hex.decode(mk.getString("conversation_key"))
        each(mk.getJSONArray("keys")) { v ->
            val k = Nip44.messageKeys(ck, Hex.decode(v.getString("nonce")))
            assertEquals(v.getString("chacha_key"), Hex.encode(k.chachaKey))
            assertEquals(v.getString("chacha_nonce"), Hex.encode(k.chachaNonce))
            assertEquals(v.getString("hmac_key"), Hex.encode(k.hmacKey))
        }
    }

    @Test
    fun paddedLengths() {
        val arr = valid.getJSONArray("calc_padded_len")
        for (i in 0 until arr.length()) {
            val pair = arr.getJSONArray(i)
            assertEquals("len ${pair.getInt(0)}", pair.getInt(1), Nip44.paddedLength(pair.getInt(0)))
        }
    }

    @Test
    fun encryptDecryptVectors() = each(valid.getJSONArray("encrypt_decrypt")) { v ->
        val sec2 = v.getString("sec2")
        val ck = Nip44.conversationKey(v.getString("sec1"), Schnorr.publicKey(sec2))
        assertEquals(v.getString("conversation_key"), Hex.encode(ck))
        val payload = Nip44.encrypt(v.getString("plaintext"), ck, Hex.decode(v.getString("nonce")))
        assertEquals(v.getString("payload"), payload)
        // The other side derives the same key and decrypts.
        val ck2 = Nip44.conversationKey(sec2, Schnorr.publicKey(v.getString("sec1")))
        assertEquals(v.getString("plaintext"), Nip44.decrypt(payload, ck2))
    }

    @Test
    fun invalidPayloadsAreRejected() = each(invalid.getJSONArray("decrypt")) { v ->
        try {
            Nip44.decrypt(v.getString("payload"), Hex.decode(v.getString("conversation_key")))
            fail("accepted invalid payload: ${v.optString("note")}")
        } catch (e: Exception) {
            // expected
        }
    }

    // ---- NIP-17 ----

    private class Person {
        val sec = Schnorr.newSecretKey()
        val pub = Schnorr.publicKey(sec)
        val encrypt: suspend (String, String) -> String = { text, peer -> Nip44.encrypt(text, sec, peer) }
        val decrypt: suspend (String, String) -> String = { text, peer -> Nip44.decrypt(text, sec, peer) }
        val sign: suspend (UnsignedEvent) -> Event = { it.signed(Schnorr.sign(it.id, sec)) }
    }

    @Test
    fun directMessageRoundTrip() = runBlocking {
        val alice = Person(); val bob = Person(); val eve = Person()
        val rumor = Nip17.rumor(alice.pub, listOf(bob.pub), "gm bob 🧡")
        val toBob = Nip17.wrap(Nip17.seal(rumor, bob.pub, alice.encrypt, alice.sign), bob.pub)
        val toSelf = Nip17.wrap(Nip17.seal(rumor, alice.pub, alice.encrypt, alice.sign), alice.pub)

        // The wrap reveals only the recipient, and is signed by a throwaway key.
        assertTrue(toBob.verify())
        assertEquals(listOf(bob.pub), toBob.tagValues("p"))
        assertTrue(toBob.pubkey != alice.pub)

        val got = Nip17.unwrap(toBob, bob.decrypt)!!
        assertEquals("gm bob 🧡", got.content)
        assertEquals(alice.pub, got.sender)
        assertEquals(listOf(alice.pub), got.peers(bob.pub))
        assertEquals(rumor.id, got.id)

        val mine = Nip17.unwrap(toSelf, alice.decrypt)!!
        assertEquals(listOf(bob.pub), mine.peers(alice.pub))
        assertEquals(got.id, mine.id)

        // Someone else can't open it.
        assertNull(Nip17.unwrap(toBob, eve.decrypt))
    }

    @Test
    fun impersonationIsRejected() = runBlocking {
        val alice = Person(); val bob = Person(); val eve = Person()
        // Eve writes a rumor claiming to be Alice, but has to seal it with her own key.
        val fake = Nip17.rumor(alice.pub, listOf(bob.pub), "send me your keys")
        val wrap = Nip17.wrap(Nip17.seal(fake, bob.pub, eve.encrypt, eve.sign), bob.pub)
        assertNull(Nip17.unwrap(wrap, bob.decrypt))
    }

    @Test
    fun timestampsAreRandomizedIntoThePast() {
        val now = 1_700_000_000L
        repeat(50) { val t = Nip17.randomPast(now); assertTrue(t <= now && t > now - 2 * 24 * 3600) }
    }

    @Test
    fun dmRelayList() {
        val pub = Schnorr.publicKey(Schnorr.newSecretKey())
        val list = Nip17.dmRelayList(pub, listOf("wss://auth.nostr1.com", "wss://nos.lol"))
        assertEquals(Nip17.DM_RELAYS, list.kind)
        assertEquals(listOf("wss://auth.nostr1.com", "wss://nos.lol"), Nip17.relaysFrom(list.signed("")))
        assertArrayEquals(ByteArray(0), ByteArray(0))
        assertNotNull(list)
    }
}
