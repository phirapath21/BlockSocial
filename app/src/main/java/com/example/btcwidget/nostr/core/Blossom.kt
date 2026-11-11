package com.example.btcwidget.nostr.core

import org.json.JSONObject
import java.security.MessageDigest
import kotlin.io.encoding.Base64

/**
 * Media uploads to Blossom servers (BUD-01/02): the file is PUT to "<server>/upload" with
 * a signed kind-24242 authorization event, and served at a URL named by its SHA-256.
 * Notes then carry the URL in their text plus a NIP-92 "imeta" tag.
 */
object Blossom {
    const val AUTH_KIND = 24242

    /** Default server when the user has no BUD-03 server list (kind 10063). */
    const val DEFAULT_SERVER = "https://blossom.primal.net"

    fun sha256Hex(bytes: ByteArray): String = Hex.encode(MessageDigest.getInstance("SHA-256").digest(bytes))

    /** Authorization event for uploading a blob with hash [sha256], valid for [validSeconds]. */
    fun uploadAuth(pubkey: String, sha256: String, size: Long, now: Long = Events.now(), validSeconds: Long = 300) =
        UnsignedEvent(
            pubkey, now, AUTH_KIND,
            listOf(
                listOf("t", "upload"),
                listOf("x", sha256),
                listOf("size", size.toString()),
                listOf("expiration", (now + validSeconds).toString())
            ),
            "Upload image from Block"
        )

    /** The HTTP Authorization header value for a signed auth event. */
    fun authHeader(auth: Event): String = "Nostr " + Base64.encode(auth.toJson().toByteArray(Charsets.UTF_8))

    fun uploadUrl(server: String): String = server.trimEnd('/') + "/upload"

    /** A server's answer to an upload. */
    data class Blob(val url: String, val sha256: String, val size: Long, val type: String?)

    /** Parses the blob descriptor; fails if the server stored something other than what was sent. */
    fun parseUpload(json: String, expectedSha256: String): Result<Blob> = runCatching {
        val o = JSONObject(json)
        val blob = Blob(
            url = o.getString("url"),
            sha256 = o.optString("sha256", expectedSha256),
            size = o.optLong("size", -1),
            type = o.optString("type", "").ifEmpty { null }
        )
        require(blob.sha256.equals(expectedSha256, ignoreCase = true)) { "The server changed the file" }
        require(blob.url.startsWith("https://")) { "The server returned no https address" }
        blob
    }

    /** Servers from a BUD-03 user server list (kind 10063), in order. */
    fun serversFrom(list: Event?): List<String> =
        list?.tags.orEmpty().filter { it.size >= 2 && it[0] == "server" }
            .map { it[1].trim().trimEnd('/') }
            .filter { it.startsWith("https://") }
}

/** NIP-92 media metadata for a URL in a note. */
object Imeta {
    fun tag(url: String, mime: String, sha256: String, width: Int? = null, height: Int? = null, blurhash: String? = null): List<String> =
        listOfNotNull(
            "imeta",
            "url $url",
            "m $mime",
            "x $sha256",
            if (width != null && height != null) "dim ${width}x$height" else null,
            blurhash?.takeIf { Blurhash.isValid(it) }?.let { "blurhash $it" }
        )

    /** The blurred preview (BlurHash) that [event]'s imeta gives for [url], if any. */
    fun blurhash(event: Event, url: String): String? =
        event.tags.filter { it.firstOrNull() == "imeta" }.firstNotNullOfOrNull { tag ->
            val fields = tag.drop(1).associate { it.substringBefore(' ') to it.substringAfter(' ', "") }
            fields["blurhash"]?.takeIf { fields["url"] == url && Blurhash.isValid(it) }
        }

    /** URLs that [event]'s imeta tags mark as videos. */
    fun videoUrls(event: Event): Set<String> =
        event.tags.filter { it.firstOrNull() == "imeta" }.mapNotNull { tag ->
            val fields = tag.drop(1).associate { it.substringBefore(' ') to it.substringAfter(' ', "") }
            fields["url"]?.takeIf { fields["m"]?.startsWith("video/") == true }
        }.toSet()

    /** Width / height of [url] from [event]'s imeta "dim" field, when given. */
    fun aspectRatio(event: Event, url: String): Float? =
        event.tags.filter { it.firstOrNull() == "imeta" }.firstNotNullOfOrNull { tag ->
            val fields = tag.drop(1).associate { it.substringBefore(' ') to it.substringAfter(' ', "") }
            if (fields["url"] != url) return@firstNotNullOfOrNull null
            val (w, h) = fields["dim"]?.split('x')?.mapNotNull { it.trim().toIntOrNull() }?.takeIf { it.size == 2 } ?: return@firstNotNullOfOrNull null
            if (w > 0 && h > 0) w.toFloat() / h else null
        }

    /** URLs that [event]'s imeta tags mark as images (so links without a file extension still show). */
    fun imageUrls(event: Event): Set<String> =
        event.tags.filter { it.firstOrNull() == "imeta" }.mapNotNull { tag ->
            val fields = tag.drop(1).associate { it.substringBefore(' ') to it.substringAfter(' ', "") }
            fields["url"]?.takeIf { fields["m"]?.startsWith("image/") == true }
        }.toSet()
}
