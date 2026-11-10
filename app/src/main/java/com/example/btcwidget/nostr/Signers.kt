package com.example.btcwidget.nostr

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResult
import com.example.btcwidget.nostr.core.Event
import com.example.btcwidget.nostr.core.Nip19
import com.example.btcwidget.nostr.core.Nip44
import com.example.btcwidget.nostr.core.Schnorr
import com.example.btcwidget.nostr.core.UnsignedEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Signs events for the logged-in account. */
interface NostrSigner {
    /** Hex public key. */
    val pubkey: String
    suspend fun sign(event: UnsignedEvent): Event

    /** NIP-44 encryption to [peer] with the user's key (for private messages). */
    suspend fun nip44Encrypt(plaintext: String, peer: String): String

    /**
     * NIP-44 decryption from [peer]. With [interactive] false (loading many messages) a
     * signer app that would have to ask the user returns null instead of prompting.
     */
    suspend fun nip44Decrypt(ciphertext: String, peer: String, interactive: Boolean = false): String?

    /** Legacy NIP-04 decryption (for reading old direct messages); same [interactive] rule. */
    suspend fun nip04Decrypt(ciphertext: String, peer: String, interactive: Boolean = false): String?
}

/** The user declined in the signer app. */
class SignerRejected : Exception("Signing was declined")

/** The signer app is missing or did not answer. */
class SignerUnavailable(message: String) : Exception(message)

/** Signs with a private key held in memory (decrypted from [NostrAccountStore]). */
class LocalSigner(private val seckey: String) : NostrSigner {
    override val pubkey: String = Schnorr.publicKey(seckey)

    override suspend fun sign(event: UnsignedEvent): Event = withContext(Dispatchers.Default) {
        require(event.pubkey == pubkey) { "event is not from this account" }
        event.signed(Schnorr.sign(event.id, seckey))
    }

    /** Conversation keys by peer: ECDH is the slow part, and a chat reuses the same peer. */
    private val keys = java.util.concurrent.ConcurrentHashMap<String, ByteArray>()
    private fun key(peer: String) = keys.getOrPut(peer) { Nip44.conversationKey(seckey, peer) }

    override suspend fun nip44Encrypt(plaintext: String, peer: String): String =
        withContext(Dispatchers.Default) { Nip44.encrypt(plaintext, key(peer)) }

    override suspend fun nip44Decrypt(ciphertext: String, peer: String, interactive: Boolean): String? =
        withContext(Dispatchers.Default) { runCatching { Nip44.decrypt(ciphertext, key(peer)) }.getOrNull() }

    override suspend fun nip04Decrypt(ciphertext: String, peer: String, interactive: Boolean): String? =
        withContext(Dispatchers.Default) { runCatching { com.example.btcwidget.nostr.core.Nip04.decrypt(ciphertext, seckey, peer) }.getOrNull() }
}

/**
 * Starts signer-app intents and returns their results. The Activity registers
 * [launch] in onCreate; signers suspend on [request] until the result arrives.
 */
object SignerBridge {
    var launch: ((Intent) -> Unit)? = null
    private var pending: CompletableDeferred<ActivityResult>? = null
    private val mutex = Mutex()

    /** One request at a time: signer apps answer them in order anyway. */
    suspend fun request(intent: Intent): ActivityResult = mutex.withLock {
        val launcher = launch ?: throw SignerUnavailable("Open Blockface to sign")
        val deferred = CompletableDeferred<ActivityResult>()
        pending = deferred
        withContext(Dispatchers.Main) {
            try {
                launcher(intent)
            } catch (e: ActivityNotFoundException) {
                pending = null
                throw SignerUnavailable("Signer app not found")
            }
        }
        deferred.await()
    }

    fun onResult(result: ActivityResult) {
        pending?.complete(result)
        pending = null
    }
}

/**
 * NIP-55 signer app such as Amber. Signs in the background through the signer's
 * ContentResolver when the user has allowed it, otherwise through an intent the user
 * confirms. The private key never reaches this app.
 */
class AmberSigner(
    private val context: Context,
    override val pubkey: String,
    private val signerPackage: String
) : NostrSigner {

    override suspend fun sign(event: UnsignedEvent): Event {
        require(event.pubkey == pubkey) { "event is not from this account" }
        val json = event.toJson()
        val npub = Nip19.npub(pubkey)
        signInBackground(json, npub)?.let { return finish(event, it) }

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("nostrsigner:" + Uri.encode(json))).apply {
            `package` = signerPackage
            putExtra("type", "sign_event")
            putExtra("id", event.id)
            putExtra("current_user", npub)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val result = SignerBridge.request(intent)
        val data = result.data
        if (result.resultCode != android.app.Activity.RESULT_OK || data == null) throw SignerRejected()
        val answer = SignerAnswer(
            event = data.getStringExtra("event"),
            signature = data.getStringExtra("signature") ?: data.getStringExtra("result")
        )
        return finish(event, answer)
    }

    private data class SignerAnswer(val event: String?, val signature: String?)

    /** Uses the signed event if the signer returned one, else attaches the signature; verifies either way. */
    private fun finish(unsigned: UnsignedEvent, answer: SignerAnswer): Event {
        val fromEvent = answer.event?.let { runCatching { Event.fromJson(it) }.getOrNull() }
        val signed = when {
            fromEvent != null && fromEvent.id == unsigned.id -> fromEvent
            answer.signature?.length == 128 -> unsigned.signed(answer.signature)
            else -> throw SignerUnavailable("The signer returned no signature")
        }
        if (!signed.verify()) throw SignerUnavailable("The signer returned an invalid signature")
        return signed
    }

    override suspend fun nip44Encrypt(plaintext: String, peer: String): String =
        crypt("nip44_encrypt", "NIP44_ENCRYPT", plaintext, peer, interactive = true)
            ?: throw SignerUnavailable("The signer didn't encrypt the message")

    override suspend fun nip44Decrypt(ciphertext: String, peer: String, interactive: Boolean): String? =
        crypt("nip44_decrypt", "NIP44_DECRYPT", ciphertext, peer, interactive)

    override suspend fun nip04Decrypt(ciphertext: String, peer: String, interactive: Boolean): String? =
        crypt("nip04_decrypt", "NIP04_DECRYPT", ciphertext, peer, interactive)

    /**
     * Encrypts or decrypts through the signer: in the background when the user allowed it,
     * else (if [interactive]) with an intent the user confirms.
     */
    private suspend fun crypt(type: String, provider: String, data: String, peer: String, interactive: Boolean): String? {
        val npub = Nip19.npub(pubkey)
        val background = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.query(Uri.parse("content://$signerPackage.$provider"), arrayOf(data, peer, npub), null, null, null)?.use { c ->
                    if (c.getColumnIndex("rejected") >= 0) return@use null
                    if (!c.moveToFirst()) return@use null
                    (c.getColumnIndex("result").takeIf { it >= 0 } ?: c.getColumnIndex("signature").takeIf { it >= 0 })?.let { c.getString(it) }
                }
            }.getOrNull()
        }
        if (background != null || !interactive) return background
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("nostrsigner:" + Uri.encode(data))).apply {
            `package` = signerPackage
            putExtra("type", type)
            putExtra("pubkey", peer)
            putExtra("current_user", npub)
            putExtra("id", type + data.hashCode())
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val result = SignerBridge.request(intent)
        if (result.resultCode != android.app.Activity.RESULT_OK) throw SignerRejected()
        return result.data?.let { it.getStringExtra("result") ?: it.getStringExtra("signature") }
    }

    /** Null when the signer needs to ask the user (or has no content provider). */
    private suspend fun signInBackground(json: String, npub: String): SignerAnswer? = withContext(Dispatchers.IO) {
        runCatching {
            val uri = Uri.parse("content://$signerPackage.SIGN_EVENT")
            context.contentResolver.query(uri, arrayOf(json, "", npub), null, null, null)?.use { c ->
                if (c.getColumnIndex("rejected") >= 0) throw SignerRejected()
                if (!c.moveToFirst()) return@use null
                val event = c.getColumnIndex("event").takeIf { it >= 0 }?.let { c.getString(it) }
                val sig = c.getColumnIndex("signature").takeIf { it >= 0 }?.let { c.getString(it) }
                    ?: c.getColumnIndex("result").takeIf { it >= 0 }?.let { c.getString(it) }
                SignerAnswer(event, sig)
            }
        }.getOrElse { if (it is SignerRejected) throw it else null }
    }

    companion object {
        /** Kinds this app signs; asked for up front so the signer can remember the choice. */
        private val KINDS = listOf(0, 1, 3, 5, 6, 7, 13, 1018, 1068, 1984, 9734, 10000, 10001, 10002, 10003, 10030, 10050, 10063, 24242)

        fun isInstalled(context: Context): Boolean =
            Intent(Intent.ACTION_VIEW, Uri.parse("nostrsigner:")).resolveActivity(context.packageManager) != null

        /** Asks the signer for the user's public key. Returns (hex pubkey, signer package). */
        suspend fun connect(): Pair<String, String> {
            val permissions = JSONArray().apply {
                KINDS.forEach { put(JSONObject().put("type", "sign_event").put("kind", it)) }
                // Private messages (NIP-17) need the user's key to seal and open them.
                put(JSONObject().put("type", "nip44_encrypt"))
                put(JSONObject().put("type", "nip44_decrypt"))
                // Reading older direct messages (NIP-04).
                put(JSONObject().put("type", "nip04_decrypt"))
            }
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("nostrsigner:")).apply {
                putExtra("type", "get_public_key")
                putExtra("permissions", permissions.toString())
            }
            val result = SignerBridge.request(intent)
            val data = result.data
            if (result.resultCode != android.app.Activity.RESULT_OK || data == null) throw SignerRejected()
            val raw = data.getStringExtra("result") ?: data.getStringExtra("signature")
                ?: throw SignerUnavailable("The signer returned no public key")
            // Older signers return an npub, newer ones hex.
            val pubkey = (Nip19.decode(raw) as? Nip19.Pubkey)?.hex ?: raw.lowercase()
            if (!com.example.btcwidget.nostr.core.Hex.isKey(pubkey)) throw SignerUnavailable("Unrecognised public key")
            val pkg = data.getStringExtra("package") ?: throw SignerUnavailable("The signer did not say which app it is")
            return pubkey to pkg
        }
    }
}
