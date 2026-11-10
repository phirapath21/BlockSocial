package com.example.btcwidget.nostr

import com.example.btcwidget.nostr.core.Bech32
import com.example.btcwidget.nostr.core.ClientMessage
import com.example.btcwidget.nostr.core.Content
import com.example.btcwidget.nostr.core.Event
import com.example.btcwidget.nostr.core.Events
import com.example.btcwidget.nostr.core.Filter
import com.example.btcwidget.nostr.core.Hex
import com.example.btcwidget.nostr.core.Kind
import com.example.btcwidget.nostr.core.Markdown
import com.example.btcwidget.nostr.core.Nip19
import com.example.btcwidget.nostr.core.Profile
import com.example.btcwidget.nostr.core.RelayMessage
import com.example.btcwidget.nostr.core.Schnorr
import com.example.btcwidget.nostr.core.ThreadRef
import com.example.btcwidget.nostr.core.UnsignedEvent
import com.example.btcwidget.nostr.core.Zaps
import com.example.btcwidget.nostr.core.normalizeRelayUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NostrCoreTest {
    private val seckey = Schnorr.newSecretKey()
    private val pubkey = Schnorr.publicKey(seckey)

    private fun sign(e: UnsignedEvent): Event = e.signed(Schnorr.sign(e.id, seckey))

    // ---- NIP-19 (vectors from the NIP-19 text) ----

    @Test
    fun nip19Vectors() {
        val npub = "npub10elfcs4fr0l0r8af98jlmgdh9c8tcxjvz9qkw038js35mp4dma8qzvjptg"
        val pub = "7e7e9c42a91bfef19fa929e5fda1b72e0ebc1a4c1141673e2794234d86addf4e"
        assertEquals(npub, Nip19.npub(pub))
        assertEquals(Nip19.Pubkey(pub), Nip19.decode(npub))
        val nsec = "nsec1vl029mgpspedva04g90vltkh6fvh240zqtv9k0t9af8935ke9laqsnlfe5"
        val sec = "67dea2ed018072d675f5415ecfaed7d2597555e202d85b3d65ea4e58d2d92ffa"
        assertEquals(nsec, Nip19.nsec(sec))
        assertEquals(Nip19.SecretKey(sec), Nip19.decode(nsec))
    }

    @Test
    fun nip19TlvRoundTrip() {
        val id = Hex.encode(ByteArray(32) { it.toByte() })
        val nevent = Nip19.nevent(id, listOf("wss://nos.lol"), pubkey, 1)
        assertEquals(Nip19.Note(id, listOf("wss://nos.lol"), pubkey, 1), Nip19.decode("nostr:$nevent"))
        val nprofile = Nip19.nprofile(pubkey, listOf("wss://relay.damus.io"))
        assertEquals(Nip19.Pubkey(pubkey, listOf("wss://relay.damus.io")), Nip19.decode(nprofile))
    }

    @Test
    fun nip19RejectsGarbage() {
        assertNull(Nip19.decode("npub1qqqqqq"))
        assertNull(Nip19.decode("hello"))
        // One character changed: bad checksum.
        assertNull(Nip19.decode("npub10elfcs4fr0l0r8af98jlmgdh9c8tcxjvz9qkw038js35mp4dma8qzvjptq"))
    }

    // ---- Keys and signatures (BIP-340 vector 0) ----

    @Test
    fun bip340PublicKeyAndVerify() {
        val sec = "0000000000000000000000000000000000000000000000000000000000000003"
        assertEquals("f9308a019258c31049344f85f89d5229b531c845836f99b08601f113bce036f9", Schnorr.publicKey(sec))
        val sig = "e907831f80848d1069a5371b402410364bdf1c5f8307b0084c55f1ce2dca821525f66a4a85ea8b71e482a74f382d2ce5ebeee8fdb2172f477df4900d310536c0"
        assertTrue(Schnorr.verify(sig, "00".repeat(32), "f9308a019258c31049344f85f89d5229b531c845836f99b08601f113bce036f9"))
        assertFalse(Schnorr.verify(sig, "01" + "00".repeat(31), "f9308a019258c31049344f85f89d5229b531c845836f99b08601f113bce036f9"))
    }

    @Test
    fun signedEventVerifiesAndTamperingFails() {
        val e = sign(Events.note(pubkey, "gm \"nostr\"\n\tline two \\ ok"))
        assertTrue(e.verify())
        assertFalse(e.copy(content = "gm").verify())
        assertTrue(Event.fromJson(e.toJson()).verify())
    }

    @Test
    fun idSerializationEscapesLikeNip01() {
        val e = UnsignedEvent("ab", 1, 1, listOf(listOf("t", "x")), "a\"b\\c\nd\u0001é")
        assertEquals("[0,\"ab\",1,1,[[\"t\",\"x\"]],\"a\\\"b\\\\c\\nd\\u0001é\"]", e.serializeForId())
    }

    // ---- Relay protocol ----

    @Test
    fun relayMessages() {
        val e = sign(Events.note(pubkey, "hi"))
        val msg = RelayMessage.parse("[\"EVENT\",\"s1\",${e.toJson()}]")
        assertEquals(RelayMessage.EventMsg("s1", e), msg)
        assertEquals(RelayMessage.Eose("s1"), RelayMessage.parse("[\"EOSE\",\"s1\"]"))
        assertEquals(RelayMessage.Ok("x", false, "blocked: spam"), RelayMessage.parse("[\"OK\",\"x\",false,\"blocked: spam\"]"))
        assertNull(RelayMessage.parse("not json"))
        assertTrue(ClientMessage.event(e).startsWith("[\"EVENT\",{\"id\":\"${e.id}\""))
        val req = ClientMessage.req("s", listOf(Filter(kinds = listOf(1), tags = mapOf("p" to listOf(pubkey)), limit = 5)))
        assertTrue(req.contains("\"#p\"") && req.contains("\"limit\":5"))
    }

    @Test
    fun filterMatches() {
        val e = sign(Events.note(pubkey, "#bitcoin"))
        assertTrue(Filter(kinds = listOf(1), tags = mapOf("t" to listOf("bitcoin"))).matches(e))
        assertFalse(Filter(authors = listOf("00".repeat(32))).matches(e))
    }

    @Test
    fun relayUrls() {
        assertEquals("wss://nos.lol", normalizeRelayUrl(" wss://NOS.lol/ "))
        assertNull(normalizeRelayUrl("https://nos.lol"))
    }

    // ---- Threads, quotes, reposts, likes, follows ----

    @Test
    fun replyToRootThenReplyToReply() {
        val root = sign(Events.note(pubkey, "root"))
        val other = Schnorr.newSecretKey()
        val otherPub = Schnorr.publicKey(other)
        val r1u = Events.reply(otherPub, "first", root)
        val r1 = r1u.signed(Schnorr.sign(r1u.id, other))
        assertEquals(ThreadRef(root.id, root.id, ""), ThreadRef.of(r1))
        assertTrue(pubkey in r1.tagValues("p"))

        val r2 = sign(Events.reply(pubkey, "second", r1))
        val t = ThreadRef.of(r2)
        assertEquals(root.id, t.root)
        assertEquals(r1.id, t.reply)
        // Replying to someone else's reply tags them, not yourself.
        assertEquals(listOf(otherPub), r2.tagValues("p"))
    }

    @Test
    fun positionalThreadTags() {
        val e = Event("i", "p", 0, 1, listOf(listOf("e", "A"), listOf("e", "B")), "", "")
        assertEquals(ThreadRef("A", "B"), ThreadRef.of(e))
        assertFalse(ThreadRef.of(Event("i", "p", 0, 1, emptyList(), "", "")).isReply)
    }

    @Test
    fun quoteAddsNeventAndTags() {
        val quoted = sign(Events.note(pubkey, "quoted"))
        val q = Events.quote(pubkey, "look", quoted)
        assertTrue(q.content.startsWith("look\n\nnostr:nevent1"))
        assertEquals(quoted.id, q.tags.first { it[0] == "q" }[1])
    }

    @Test
    fun repostAndReaction() {
        val n = sign(Events.note(pubkey, "n"))
        val rp = Events.repost(pubkey, n)
        assertEquals(Kind.REPOST, rp.kind)
        assertEquals(n, Event.fromJson(rp.content))
        val like = Events.reaction(pubkey, n)
        assertEquals("+", like.content)
        assertEquals(listOf(n.id), like.tags.filter { it[0] == "e" }.map { it[1] })
    }

    @Test
    fun followAndUnfollowKeepOtherContacts() {
        val a = "aa".repeat(32); val b = "bb".repeat(32)
        val current = sign(UnsignedEvent(pubkey, 1, Kind.CONTACTS, listOf(listOf("p", a)), "{\"relays\":1}"))
        val followed = Events.contacts(pubkey, current, b, follow = true)
        assertEquals(listOf(a, b), followed.tags.map { it[1] })
        assertEquals("{\"relays\":1}", followed.content)
        val unfollowed = Events.contacts(pubkey, sign(followed), a, follow = false)
        assertEquals(listOf(b), unfollowed.tags.map { it[1] })
    }

    // ---- Content ----

    @Test
    fun contentSegments() {
        val npub = Nip19.npub(pubkey)
        val segs = Content.parse("gm nostr:$npub see https://x.com/a.jpg, and https://example.com/page. #Bitcoin!")
        assertTrue(segs.any { it is Content.Segment.ProfileRef && it.pubkey == pubkey })
        assertTrue(segs.contains(Content.Segment.Image("https://x.com/a.jpg")))
        assertTrue(segs.contains(Content.Segment.Link("https://example.com/page")))
        assertTrue(segs.contains(Content.Segment.Hashtag("Bitcoin")))
        // Nothing is lost.
        assertTrue(segs.joinToString("") {
            when (it) {
                is Content.Segment.Text -> it.text
                is Content.Segment.Link -> it.url
                is Content.Segment.Image -> it.url
                is Content.Segment.Video -> it.url
                is Content.Segment.Hashtag -> "#" + it.tag
                is Content.Segment.ProfileRef -> it.raw
                is Content.Segment.NoteRef -> it.raw
                is Content.Segment.AddressRef -> it.raw
                is Content.Segment.Emoji -> ":" + it.code + ":"
            }
        }.startsWith("gm nostr:$npub see https://x.com/a.jpg, and"))
    }

    @Test
    fun noteTagsHashtagsAndMentions() {
        val other = "cc".repeat(32)
        val n = Events.note(pubkey, "hi nostr:${Nip19.npub(other)} #Bitcoin")
        assertTrue(listOf("p", other) in n.tags)
        assertTrue(listOf("t", "bitcoin") in n.tags)
    }

    @Test
    fun profileFromMetadata() {
        val e = sign(UnsignedEvent(pubkey, 5, Kind.METADATA, emptyList(), "{\"name\":\"sat\",\"display_name\":\"\",\"lud16\":\"sat@getalby.com\"}"))
        val p = Profile.fromEvent(e)!!
        assertEquals("sat", p.bestName)
        assertEquals("https://getalby.com/.well-known/lnurlp/sat", Zaps.payEndpoint(p))
    }

    // ---- Zaps ----

    @Test
    fun bolt11Amounts() {
        assertEquals(250_000_000L, Zaps.invoiceAmountMsat("lnbc2500u1pvjluezpp5qqqsyq"))
        assertEquals(2_000_000_000L, Zaps.invoiceAmountMsat("lnbc20m1pvjluezpp5qqq"))
        assertEquals(21_000L, Zaps.invoiceAmountMsat("lnbc210n1pqqq"))
        assertNull(Zaps.invoiceAmountMsat("lnbc1pvjluezpp5qqq"))
    }

    @Test
    fun lnurlAndCallback() {
        val url = "https://getalby.com/.well-known/lnurlp/sat"
        val lnurl = Zaps.encodeLnurl(url)
        assertTrue(lnurl.startsWith("lnurl1"))
        assertEquals(url, Zaps.payEndpoint(Profile(pubkey, lud06 = lnurl)))
        val info = Zaps.parsePayInfo("{\"callback\":\"https://x/cb\",\"minSendable\":1000,\"maxSendable\":100000000,\"allowsNostr\":true,\"nostrPubkey\":\"${"ab".repeat(32)}\"}")!!
        assertTrue(info.allowsNostr)
        val req = sign(Zaps.request(pubkey, "ab".repeat(32), 21_000, lnurl, listOf("wss://nos.lol"), noteId = "cd".repeat(32)))
        val cb = Zaps.callbackUrl(info, 21_000, req, lnurl)
        assertTrue(cb.startsWith("https://x/cb?amount=21000&nostr=%7B"))
        assertEquals("lnbc1", Zaps.parseInvoice("{\"pr\":\"lnbc1\"}").getOrNull())
        assertTrue(Zaps.parseInvoice("{\"status\":\"ERROR\",\"reason\":\"no\"}").isFailure)
    }

    @Test
    fun zapReceipt() {
        val req = sign(Zaps.request(pubkey, "ab".repeat(32), 21_000, "lnurl1x", emptyList(), noteId = "cd".repeat(32), comment = "great"))
        val receipt = Event(
            "ee".repeat(32), "ab".repeat(32), 9, Kind.ZAP_RECEIPT,
            listOf(listOf("p", "ab".repeat(32)), listOf("e", "cd".repeat(32)), listOf("bolt11", "lnbc210n1pqq"), listOf("description", req.toJson())),
            "", ""
        )
        val r = Zaps.parseReceipt(receipt)!!
        assertEquals(21_000L, r.amountMsat)
        assertEquals(pubkey, r.sender)
        assertEquals("great", r.comment)
        assertEquals("cd".repeat(32), r.noteId)
    }

    @Test
    fun bech32RoundTrip() {
        val (hrp, data) = Bech32.decode(Bech32.encode("test", byteArrayOf(1, 2, 3)))
        assertEquals("test", hrp)
        assertEquals(listOf<Byte>(1, 2, 3), data.toList())
    }
}

class Nip05AndSearchTest {
    @org.junit.Test
    fun nip05Parsing() {
        org.junit.Assert.assertEquals("bob" to "example.com", com.example.btcwidget.nostr.core.Nip05.parts("Bob@Example.com"))
        org.junit.Assert.assertEquals("_" to "example.com", com.example.btcwidget.nostr.core.Nip05.parts("example.com"))
        org.junit.Assert.assertNull(com.example.btcwidget.nostr.core.Nip05.parts("hello world"))
        org.junit.Assert.assertNull(com.example.btcwidget.nostr.core.Nip05.parts("bitcoin"))
        org.junit.Assert.assertEquals("https://example.com/.well-known/nostr.json?name=bob", com.example.btcwidget.nostr.core.Nip05.url("bob@example.com"))
        val pk = "ab".repeat(32)
        org.junit.Assert.assertEquals(pk, com.example.btcwidget.nostr.core.Nip05.pubkeyFrom("{\"names\":{\"bob\":\"$pk\"}}", "Bob"))
        org.junit.Assert.assertNull(com.example.btcwidget.nostr.core.Nip05.pubkeyFrom("{\"names\":{\"bob\":\"xyz\"}}", "bob"))
        org.junit.Assert.assertNull(com.example.btcwidget.nostr.core.Nip05.pubkeyFrom("not json", "bob"))
    }

    @org.junit.Test
    fun searchFilterIsSentAndNotCheckedLocally() {
        val f = com.example.btcwidget.nostr.core.Filter(kinds = listOf(0), search = "satoshi", limit = 20)
        org.junit.Assert.assertEquals("satoshi", f.toJson().getString("search"))
        val e = com.example.btcwidget.nostr.core.Event("i", "p", 0, 0, emptyList(), "{}", "")
        org.junit.Assert.assertTrue(f.matches(e))
    }
}

class BlossomTest {
    private val sec = com.example.btcwidget.nostr.core.Schnorr.newSecretKey()
    private val pub = com.example.btcwidget.nostr.core.Schnorr.publicKey(sec)

    @org.junit.Test
    fun uploadAuthAndHeader() {
        val bytes = "hello".toByteArray()
        val sha = com.example.btcwidget.nostr.core.Blossom.sha256Hex(bytes)
        org.junit.Assert.assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", sha)
        val u = com.example.btcwidget.nostr.core.Blossom.uploadAuth(pub, sha, bytes.size.toLong(), now = 1000)
        org.junit.Assert.assertEquals(24242, u.kind)
        org.junit.Assert.assertTrue(listOf("t", "upload") in u.tags && listOf("x", sha) in u.tags && listOf("expiration", "1300") in u.tags)
        val signed = u.signed(com.example.btcwidget.nostr.core.Schnorr.sign(u.id, sec))
        val header = com.example.btcwidget.nostr.core.Blossom.authHeader(signed)
        org.junit.Assert.assertTrue(header.startsWith("Nostr "))
        val decoded = String(java.util.Base64.getDecoder().decode(header.removePrefix("Nostr ")))
        org.junit.Assert.assertTrue(com.example.btcwidget.nostr.core.Event.fromJson(decoded).verify())
    }

    @org.junit.Test
    fun parseUploadChecksHash() {
        val sha = "ab".repeat(32)
        val ok = com.example.btcwidget.nostr.core.Blossom.parseUpload("{\"url\":\"https://b.example/$sha.jpg\",\"sha256\":\"$sha\",\"size\":10,\"type\":\"image/jpeg\"}", sha)
        org.junit.Assert.assertEquals("https://b.example/$sha.jpg", ok.getOrThrow().url)
        org.junit.Assert.assertTrue(com.example.btcwidget.nostr.core.Blossom.parseUpload("{\"url\":\"https://b/x\",\"sha256\":\"${"cd".repeat(32)}\"}", sha).isFailure)
        org.junit.Assert.assertTrue(com.example.btcwidget.nostr.core.Blossom.parseUpload("<html>", sha).isFailure)
    }

    @org.junit.Test
    fun serverListAndImeta() {
        val list = com.example.btcwidget.nostr.core.Event("i", pub, 0, 10063, listOf(listOf("server", "https://a.example/"), listOf("server", "http://insecure"), listOf("r", "x")), "", "")
        org.junit.Assert.assertEquals(listOf("https://a.example"), com.example.btcwidget.nostr.core.Blossom.serversFrom(list))
        val tag = com.example.btcwidget.nostr.core.Imeta.tag("https://a.example/h", "image/jpeg", "ab".repeat(32), 800, 600)
        org.junit.Assert.assertEquals(listOf("imeta", "url https://a.example/h", "m image/jpeg", "x ${"ab".repeat(32)}", "dim 800x600"), tag)
        val note = com.example.btcwidget.nostr.core.Events.note(pub, "look https://a.example/h", listOf(tag))
        val e = note.signed("")
        org.junit.Assert.assertEquals(setOf("https://a.example/h"), com.example.btcwidget.nostr.core.Imeta.imageUrls(e))
    }

    @org.junit.Test
    fun replyCarriesExtraTags() {
        val parent = com.example.btcwidget.nostr.core.Events.note(pub, "p").let { it.signed(com.example.btcwidget.nostr.core.Schnorr.sign(it.id, sec)) }
        val tag = com.example.btcwidget.nostr.core.Imeta.tag("https://a/b.jpg", "image/jpeg", "ab".repeat(32))
        org.junit.Assert.assertTrue(tag in com.example.btcwidget.nostr.core.Events.reply(pub, "r", parent, extraTags = listOf(tag)).tags)
    }
}

class ThaiTextTest {
    @org.junit.Test
    fun thaiHashtagsKeepVowelsAndToneMarks() {
        val segs = com.example.btcwidget.nostr.core.Content.parse("gm #บิตคอยน์ and #Bitcoin")
        val tags = segs.filterIsInstance<com.example.btcwidget.nostr.core.Content.Segment.Hashtag>().map { it.tag }
        org.junit.Assert.assertEquals(listOf("บิตคอยน์", "Bitcoin"), tags)
    }
}

class LinkPreviewTest {
    @org.junit.Test
    fun openGraphTagsInAnyAttributeOrder() {
        val html = """<html><head><title>Fallback</title>
            <meta content="Bitcoin &amp; you" property="og:title">
            <meta property='og:description' content='A peer-to-peer &quot;cash&quot; system'/>
            <meta property="og:image" content="/img/card.png">
            <meta name="og:site_name" content="Example">
            </head><body><meta property="og:title" content="ignored"></body></html>"""
        val p = com.example.btcwidget.nostr.core.LinkPreview.parse(html, "https://www.example.com/a/b")!!
        org.junit.Assert.assertEquals("Bitcoin & you", p.title)
        org.junit.Assert.assertEquals("A peer-to-peer \"cash\" system", p.description)
        org.junit.Assert.assertEquals("https://www.example.com/img/card.png", p.image)
        org.junit.Assert.assertEquals("Example", p.site)
    }

    @org.junit.Test
    fun titleFallbackAndRejections() {
        val p = com.example.btcwidget.nostr.core.LinkPreview.parse("<head><title>Just a page &#8212; ok</title></head>", "https://news.example.org/x")!!
        org.junit.Assert.assertEquals("Just a page — ok", p.title)
        org.junit.Assert.assertEquals("news.example.org", p.site)
        org.junit.Assert.assertNull(p.image)
        org.junit.Assert.assertNull(com.example.btcwidget.nostr.core.LinkPreview.parse("<head></head>", "https://x.example"))
        // Plain-http images aren't loaded.
        val q = com.example.btcwidget.nostr.core.LinkPreview.parse("<head><meta property=\"og:title\" content=\"T\"><meta property=\"og:image\" content=\"http://x/i.png\"></head>", "https://x.example")!!
        org.junit.Assert.assertNull(q.image)
    }
}

class VideoSegmentTest {
    @org.junit.Test
    fun videoLinksAreRecognised() {
        val segs = com.example.btcwidget.nostr.core.Content.parse("clip https://v.example/a.mp4?x=1 and https://v.example/live.m3u8 page https://v.example/watch")
        org.junit.Assert.assertEquals(
            listOf("https://v.example/a.mp4?x=1", "https://v.example/live.m3u8"),
            segs.filterIsInstance<com.example.btcwidget.nostr.core.Content.Segment.Video>().map { it.url }
        )
        org.junit.Assert.assertTrue(segs.contains(com.example.btcwidget.nostr.core.Content.Segment.Link("https://v.example/watch")))
        val e = com.example.btcwidget.nostr.core.Event("i", "p", 0, 1, listOf(listOf("imeta", "url https://b/x", "m video/mp4")), "https://b/x", "")
        org.junit.Assert.assertEquals(setOf("https://b/x"), com.example.btcwidget.nostr.core.Imeta.videoUrls(e))
    }
}

class Nip04NwcTest {
    private val n04 = com.example.btcwidget.nostr.core.Nip04
    private val nwc = com.example.btcwidget.nostr.core.Nwc
    private val schnorr = com.example.btcwidget.nostr.core.Schnorr

    @org.junit.Test
    fun nip04InteropVector() {
        // Made independently with Python's `cryptography` (secp256k1 ECDH + AES-256-CBC).
        val payload = "W7JcHJiSz8qnq1wBWZm6OTPw40gqeD6OmtWGmu0qOLw=?iv=AAECAwQFBgcICQoLDA0ODw=="
        val sk1 = "00".repeat(31) + "01"
        val pub2 = "c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5"
        org.junit.Assert.assertEquals("nostr wallet connect ⚡", n04.decrypt(payload, sk1, pub2))
        // And the other side, with sk2 and pub1.
        val sk2 = "00".repeat(31) + "02"
        org.junit.Assert.assertEquals("nostr wallet connect ⚡", n04.decrypt(payload, sk2, schnorr.publicKey(sk1)))
    }

    @org.junit.Test
    fun parseConnectionString() {
        val wallet = "ab".repeat(32); val secret = schnorr.newSecretKey()
        val c = nwc.parse("nostr+walletconnect://$wallet?relay=wss%3A%2F%2Frelay.getalby.com%2Fv1&secret=$secret&lud16=me%40getalby.com")!!
        org.junit.Assert.assertEquals(wallet, c.walletPubkey)
        org.junit.Assert.assertEquals(listOf("wss://relay.getalby.com/v1"), c.relays)
        org.junit.Assert.assertEquals("me@getalby.com", c.lud16)
        org.junit.Assert.assertNull(nwc.parse("nostr+walletconnect://$wallet?relay=wss://r.example"))   // no secret
        org.junit.Assert.assertNull(nwc.parse("https://example.com"))
    }

    @org.junit.Test
    fun payInvoiceRoundTripWithBothEncryptions() {
        val walletSec = schnorr.newSecretKey(); val walletPub = schnorr.publicKey(walletSec)
        val c = nwc.parse("nostr+walletconnect://$walletPub?relay=wss://r.example&secret=${schnorr.newSecretKey()}")!!
        for (nip44 in listOf(false, true)) {
            val req = nwc.payInvoiceRequest(c, "lnbc10n1test", nip44)
            org.junit.Assert.assertTrue(req.verify())
            org.junit.Assert.assertEquals(listOf(walletPub), req.tagValues("p"))
            // The wallet reads it…
            val body = if (nip44) com.example.btcwidget.nostr.core.Nip44.decrypt(req.content, walletSec, c.clientPubkey)
                       else n04.decrypt(req.content, walletSec, c.clientPubkey)
            org.junit.Assert.assertEquals("lnbc10n1test", org.json.JSONObject(body).getJSONObject("params").getString("invoice"))
            // …and answers.
            fun answer(json: String): com.example.btcwidget.nostr.core.Event {
                val content = if (nip44) com.example.btcwidget.nostr.core.Nip44.encrypt(json, walletSec, c.clientPubkey) else n04.encrypt(json, walletSec, c.clientPubkey)
                val u = com.example.btcwidget.nostr.core.UnsignedEvent(walletPub, 1, nwc.RESPONSE, listOf(listOf("e", req.id)), content)
                return u.signed(schnorr.sign(u.id, walletSec))
            }
            org.junit.Assert.assertEquals("pre", nwc.parseResponse(c, answer("{\"result_type\":\"pay_invoice\",\"result\":{\"preimage\":\"pre\"}}")).getOrThrow())
            val err = nwc.parseResponse(c, answer("{\"result_type\":\"pay_invoice\",\"error\":{\"code\":\"INSUFFICIENT_BALANCE\",\"message\":\"Not enough sats\"}}"))
            org.junit.Assert.assertEquals("Not enough sats", err.exceptionOrNull()?.message)
        }
    }
}

class OutboxTest {
    private val o = com.example.btcwidget.nostr.core.Outbox

    @org.junit.Test
    fun writeRelaysSkipReadOnly() {
        val e = com.example.btcwidget.nostr.core.Event("i", "p", 0, 10002, listOf(
            listOf("r", "wss://a.example"), listOf("r", "wss://b.example", "write"),
            listOf("r", "wss://c.example", "read"), listOf("r", "https://bad")
        ), "", "")
        org.junit.Assert.assertEquals(listOf("wss://a.example", "wss://b.example"), o.writeRelays(e))
    }

    @org.junit.Test
    fun planSharesRelaysAndRespectsTheCap() {
        val lists = mapOf(
            "alice" to listOf("wss://big", "wss://a1", "wss://a2"),
            "bob" to listOf("wss://big", "wss://b1"),
            "carol" to listOf("wss://c1"),
            "dave" to listOf("wss://d1", "wss://d2")
        )
        val plan = o.plan(listOf("alice", "bob", "carol", "dave", "erin"), lists, perAuthor = 2, maxRelays = 4)
        org.junit.Assert.assertTrue(plan.size <= 4)
        // The shared relay is used for both people who write there.
        org.junit.Assert.assertTrue(plan["wss://big"]!!.containsAll(listOf("alice", "bob")))
        // Carol has a single relay: it's picked.
        org.junit.Assert.assertEquals(listOf("carol"), plan["wss://c1"])
        // Erin has no list: left to the defaults.
        org.junit.Assert.assertTrue(plan.values.none { "erin" in it })
        // Avoided relays (e.g. ones we already query) aren't planned.
        org.junit.Assert.assertFalse(o.plan(listOf("bob"), lists, avoid = setOf("wss://big")).containsKey("wss://big"))
    }

    @Test
    fun replyTreeNestsRepliesInReadingOrder() {
        fun reply(id: String, parent: String, at: Long) = Event(id, "pk", at, Kind.TEXT_NOTE,
            listOf(listOf("e", "focus", "", "root"), listOf("e", parent, "", "reply")), "", "")
        val rows = com.example.btcwidget.nostr.core.ReplyTree.flatten("focus", listOf(
            reply("b", "focus", 20), reply("a", "focus", 10), reply("a1", "a", 30), reply("a1x", "a1", 40), reply("b1", "b", 25)
        ))
        assertEquals(listOf("a" to 0, "a1" to 1, "a1x" to 2, "b" to 0, "b1" to 1), rows.map { it.event.id to it.depth })
    }

    @Test
    fun naddrRoundTripsAndArticleParses() {
        val pk = "3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d"
        val a = Nip19.naddr(30023, pk, "my-post", listOf("wss://relay.example"))
        assertEquals(Nip19.Address(30023, pk, "my-post", listOf("wss://relay.example")), Nip19.decode("nostr:$a"))
        val seg = Content.parse("read nostr:$a now").filterIsInstance<Content.Segment.AddressRef>().single()
        assertEquals("my-post", seg.address.identifier)
        val e = Event("00", pk, 5, Kind.ARTICLE, listOf(listOf("d", "my-post"), listOf("title", "Hello"), listOf("published_at", "3")), "# x", "")
        val art = com.example.btcwidget.nostr.core.Article.of(e)!!
        assertEquals("Hello", art.title); assertEquals(3L, art.publishedAt)
    }

    @Test
    fun markdownBlocksAndSpans() {
        val blocks = Markdown.parse("# Title\n\nSome **bold** and *it* with [link](https://a.b).\nsame para\n\n- one\n2. two\n> quoted\n\n```\ncode\n```\n![alt](https://x/y.png)\n---")
        assertEquals(8, blocks.size)
        assertEquals(Markdown.Block.Heading(1, listOf(Markdown.Span.Plain("Title"))), blocks[0])
        val p = blocks[1] as Markdown.Block.Paragraph
        assertEquals(listOf(Markdown.Span.Plain("Some "), Markdown.Span.Bold("bold"), Markdown.Span.Plain(" and "), Markdown.Span.Italic("it"), Markdown.Span.Plain(" with "), Markdown.Span.Link("link", "https://a.b"), Markdown.Span.Plain(". same para")), p.spans)
        assertEquals("2.", (blocks[3] as Markdown.Block.ListItem).marker)
        assertEquals(Markdown.Block.Code("code"), blocks[5])
        assertEquals(Markdown.Block.Image("https://x/y.png", "alt"), blocks[6])
        assertEquals(Markdown.Block.Rule, blocks[7])
    }

    @Test
    fun pollTallyCountsLatestVotePerPerson() {
        val unsigned = com.example.btcwidget.nostr.core.Poll.create("pk", "Best?", listOf("A", " B ", ""), false, endsAt = 100, relays = listOf("wss://r"), now = 1)
        val poll = com.example.btcwidget.nostr.core.Poll.of(unsigned.signed("s"))!!
        assertEquals(listOf("A", "B"), poll.options.map { it.label })
        fun vote(who: String, at: Long, vararg ids: String) = com.example.btcwidget.nostr.core.Poll.response(who, poll, ids.toList(), now = at).signed("s")
        val t = poll.tally(listOf(
            vote("x", 10, "opt0"), vote("x", 20, "opt1"),   // changed mind: B
            vote("y", 30, "opt0"),
            vote("z", 200, "opt0"),                        // after the end
            Event("i", "w", 5, Kind.POLL_RESPONSE, listOf(listOf("e", poll.event.id), listOf("response", "opt0"), listOf("response", "opt1")), "", "s")
        ), me = "x")
        assertEquals(1 + 1, t.count("opt0"))   // y and w (single choice keeps w's first)
        assertEquals(1, t.count("opt1"))
        assertEquals(3, t.voters)
        assertEquals(setOf("opt1"), t.mine)
    }

    @Test
    fun customEmojiSplitsTextAndTagsReactions() {
        val e = Event("i", "pk", 1, Kind.TEXT_NOTE, listOf(listOf("emoji", "blob", "https://e/blob.png"), listOf("emoji", "bad", "http://x")), "hi :blob: and :bad: :blob:", "s")
        val emojis = com.example.btcwidget.nostr.core.CustomEmoji.of(e)
        assertEquals(mapOf("blob" to "https://e/blob.png"), emojis)
        val segs = Content.parse(e.content, emojis)
        assertEquals(listOf(Content.Segment.Text("hi "), Content.Segment.Emoji("blob", "https://e/blob.png"), Content.Segment.Text(" and :bad: "), Content.Segment.Emoji("blob", "https://e/blob.png")), segs)
        val r = Events.reaction("me", e, ":blob:", "https://e/blob.png")
        assertTrue(listOf("emoji", "blob", "https://e/blob.png") in r.tags)
        assertFalse(Events.reaction("me", e, "🔥", "https://x").tags.any { it[0] == "emoji" })
    }

    @Test
    fun muteRulesHideWordsTagsAndThreads() {
        val list = Event("m", "me", 1, Kind.MUTE_LIST, listOf(listOf("p", "bad"), listOf("word", "Scam"), listOf("word", "สแปม"), listOf("t", "NSFW"), listOf("e", "root1")), "", "")
        val rules = com.example.btcwidget.nostr.core.MuteRules.of(list)
        fun note(content: String, tags: List<List<String>> = emptyList(), pk: String = "ok") = Event("n" + content.hashCode(), pk, 1, Kind.TEXT_NOTE, tags, content, "")
        assertTrue(rules.hides(note("hi", pk = "bad")))
        assertTrue(rules.hides(note("free SCAM inside")))
        assertFalse(rules.hides(note("scampi for dinner")))
        assertTrue(rules.hides(note("ข้อความสแปมนะ")))
        assertTrue(rules.hides(note("pic", listOf(listOf("t", "nsfw")))))
        assertTrue(rules.hides(note("reply", listOf(listOf("e", "root1", "", "root"), listOf("e", "x", "", "reply")))))
        assertFalse(rules.hides(note("fine")))
        val net = com.example.btcwidget.nostr.core.TrustNetwork("a".repeat(64), listOf("b".repeat(64)), listOf("c".repeat(64)))
        assertTrue(net.contains("c".repeat(64)))
        assertFalse(net.contains("d".repeat(64)))
    }
}
