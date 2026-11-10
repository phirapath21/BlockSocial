package com.example.btcwidget.nostr

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.example.btcwidget.nostr.core.Hex
import com.example.btcwidget.nostr.core.Schnorr
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** How the account signs. */
enum class SignerType { AMBER, LOCAL }

/** The logged-in account as stored on the device. */
data class NostrAccount(val pubkey: String, val type: SignerType, val signerPackage: String? = null)

/**
 * Stores the Nostr account in its own prefs file, "nostr_account" (excluded from backup).
 * A private key is kept only encrypted with an AES-GCM key in the Android Keystore, which
 * cannot be exported, so a copied file is useless off this device. Nothing here is logged.
 */
object NostrAccountStore {
    private const val FILE = "nostr_account"
    private const val PUBKEY = "pubkey"
    private const val TYPE = "type"
    private const val PACKAGE = "signer_package"
    private const val SECRET = "secret"   // base64(iv | ciphertext)
    private const val KEY_ALIAS = "blockface_nostr_key"
    private const val RELAYS = "relays"

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun account(context: Context): NostrAccount? {
        val p = prefs(context)
        val pubkey = p.getString(PUBKEY, null)?.takeIf { Hex.isKey(it) } ?: return null
        val type = runCatching { SignerType.valueOf(p.getString(TYPE, "") ?: "") }.getOrNull() ?: return null
        return NostrAccount(pubkey, type, p.getString(PACKAGE, null))
    }

    fun saveAmber(context: Context, pubkey: String, signerPackage: String) {
        stashIfOther(context, pubkey)
        prefs(context).edit().clear()
            .putString(PUBKEY, pubkey).putString(TYPE, SignerType.AMBER.name).putString(PACKAGE, signerPackage)
            .apply()
    }

    /** Encrypts and stores [seckey]; returns the account. */
    fun saveLocal(context: Context, seckey: String): NostrAccount {
        require(Schnorr.isValidSecretKey(seckey)) { "Not a valid private key" }
        val pubkey = Schnorr.publicKey(seckey)
        stashIfOther(context, pubkey)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, keystoreKey()) }
        val sealed = cipher.iv + cipher.doFinal(Hex.decode(seckey))
        prefs(context).edit().clear()
            .putString(PUBKEY, pubkey).putString(TYPE, SignerType.LOCAL.name)
            .putString(SECRET, Base64.encodeToString(sealed, Base64.NO_WRAP))
            .commit()
        return NostrAccount(pubkey, SignerType.LOCAL)
    }

    /** The decrypted private key of a LOCAL account; null if missing or unreadable. */
    fun secretKey(context: Context): String? = runCatching {
        val sealed = Base64.decode(prefs(context).getString(SECRET, null) ?: return null, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, keystoreKey(), GCMParameterSpec(128, sealed, 0, 12))
        Hex.encode(cipher.doFinal(sealed, 12, sealed.size - 12))
    }.getOrNull()

    // ---- Nostr Wallet Connect: the connection string can spend money, so it's sealed like a key ----

    private const val NWC = "nwc"

    fun setNwc(context: Context, uri: String?) {
        if (uri == null) { prefs(context).edit().remove(NWC).commit(); return }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, keystoreKey()) }
        val sealed = cipher.iv + cipher.doFinal(uri.toByteArray(Charsets.UTF_8))
        prefs(context).edit().putString(NWC, Base64.encodeToString(sealed, Base64.NO_WRAP)).commit()
    }

    fun nwc(context: Context): String? = runCatching {
        val sealed = Base64.decode(prefs(context).getString(NWC, null) ?: return null, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, keystoreKey(), GCMParameterSpec(128, sealed, 0, 12))
        String(cipher.doFinal(sealed, 12, sealed.size - 12), Charsets.UTF_8)
    }.getOrNull()

    /** The account's signer, or null when logged out (or the key can't be read). */
    fun signer(context: Context): NostrSigner? {
        val account = account(context) ?: return null
        return when (account.type) {
            SignerType.LOCAL -> secretKey(context)?.let { LocalSigner(it) }
            SignerType.AMBER -> account.signerPackage?.let { AmberSigner(context.applicationContext, account.pubkey, it) }
        }
    }

    /**
     * Logs out of the active account: deletes it, including its encrypted key. The Keystore
     * key is deleted too unless a saved account still needs it.
     */
    fun clear(context: Context) {
        prefs(context).edit().clear().commit()
        if (savedAccounts(context).none { it.type == SignerType.LOCAL }) {
            runCatching { KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(KEY_ALIAS) }
        }
    }

    // ---- Other accounts on this phone ----
    // Inactive accounts are kept in "nostr_accounts" (excluded from backup) as a snapshot of
    // everything in the account file: keys stay encrypted with the same Keystore key.

    private const val SAVED_FILE = "nostr_accounts"
    private fun saved(context: Context) = context.getSharedPreferences(SAVED_FILE, Context.MODE_PRIVATE)

    /** Accounts saved on this phone besides the active one. */
    fun savedAccounts(context: Context): List<NostrAccount> = saved(context).all.mapNotNull { (pubkey, json) ->
        runCatching {
            val o = org.json.JSONObject(json as String)
            NostrAccount(pubkey, SignerType.valueOf(o.getJSONObject(TYPE).getString("v")), o.optJSONObject(PACKAGE)?.getString("v"))
        }.getOrNull()
    }.sortedBy { it.pubkey }

    /** Saves the active account aside (when it isn't [pubkey]) so another can log in. */
    private fun stashIfOther(context: Context, pubkey: String) {
        val active = account(context) ?: return
        if (active.pubkey != pubkey) stashActive(context)
    }

    /** Moves the active account into the saved accounts; nothing is active afterwards. */
    fun stashActive(context: Context) {
        val active = account(context) ?: return
        val o = org.json.JSONObject()
        prefs(context).all.forEach { (k, v) ->
            val entry = org.json.JSONObject()
            when (v) {
                is String -> entry.put("t", "s").put("v", v)
                is Boolean -> entry.put("t", "b").put("v", v)
                is Int -> entry.put("t", "i").put("v", v)
                is Long -> entry.put("t", "l").put("v", v)
                is Float -> entry.put("t", "f").put("v", v.toDouble())
                is Set<*> -> entry.put("t", "set").put("v", org.json.JSONArray(v.map { it.toString() }))
                else -> return@forEach
            }
            o.put(k, entry)
        }
        saved(context).edit().putString(active.pubkey, o.toString()).commit()
        prefs(context).edit().clear().commit()
    }

    /** Makes the saved account [pubkey] active, saving the current one aside. */
    fun switchTo(context: Context, pubkey: String) {
        val json = saved(context).getString(pubkey, null) ?: error("That account isn't saved on this phone")
        stashActive(context)
        val o = org.json.JSONObject(json)
        val e = prefs(context).edit().clear()
        o.keys().forEach { k ->
            val entry = o.getJSONObject(k)
            when (entry.getString("t")) {
                "s" -> e.putString(k, entry.getString("v"))
                "b" -> e.putBoolean(k, entry.getBoolean("v"))
                "i" -> e.putInt(k, entry.getInt("v"))
                "l" -> e.putLong(k, entry.getLong("v"))
                "f" -> e.putFloat(k, entry.getDouble("v").toFloat())
                "set" -> e.putStringSet(k, entry.getJSONArray("v").let { a -> (0 until a.length()).map { a.getString(it) }.toSet() })
            }
        }
        e.commit()
        saved(context).edit().remove(pubkey).commit()
    }

    /** Removes a saved (inactive) account from this phone, including its encrypted key. */
    fun removeSaved(context: Context, pubkey: String) {
        saved(context).edit().remove(pubkey).commit()
        if (account(context)?.type != SignerType.LOCAL && savedAccounts(context).none { it.type == SignerType.LOCAL }) {
            runCatching { KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(KEY_ALIAS) }
        }
    }

    /** The user's relays, or null to use the defaults / their NIP-65 list. */
    fun relays(context: Context): List<String>? =
        prefs(context).getString(RELAYS, null)?.split('\n')?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() }

    fun setRelays(context: Context, relays: List<String>?) {
        prefs(context).edit().apply { if (relays == null) remove(RELAYS) else putString(RELAYS, relays.joinToString("\n")) }.apply()
    }

    // ---- Read state and notifications (per account: cleared on login/logout with the rest) ----

    private const val NOTIF_SEEN = "notif_seen"
    private const val NOTIF_NOTIFIED = "notif_notified"
    private const val DM_READ = "dm_read"
    private const val DM_NOTIFIED = "dm_notified"
    private const val PUSH = "push_enabled"
    private const val NOTIF_OFF = "notif_off"
    private const val DM_INBOX = "dm_inbox"

    /** Newest notification time the user has seen in the app (seconds). */
    fun notificationsSeen(context: Context): Long = prefs(context).getLong(NOTIF_SEEN, 0)
    fun setNotificationsSeen(context: Context, at: Long) = prefs(context).edit().putLong(NOTIF_SEEN, at).apply()

    /** Newest notification time already shown as a phone notification. */
    fun notificationsNotified(context: Context): Long = prefs(context).getLong(NOTIF_NOTIFIED, 0)
    fun setNotificationsNotified(context: Context, at: Long) = prefs(context).edit().putLong(NOTIF_NOTIFIED, at).apply()

    /** Newest message time read, per conversation key. */
    fun dmRead(context: Context): Map<String, Long> = runCatching {
        val o = org.json.JSONObject(prefs(context).getString(DM_READ, null) ?: return emptyMap())
        o.keys().asSequence().associateWith { o.getLong(it) }
    }.getOrDefault(emptyMap())

    fun setDmRead(context: Context, read: Map<String, Long>) =
        prefs(context).edit().putString(DM_READ, org.json.JSONObject(read).toString()).apply()

    fun dmNotified(context: Context): Long = prefs(context).getLong(DM_NOTIFIED, 0)
    fun setDmNotified(context: Context, at: Long) = prefs(context).edit().putLong(DM_NOTIFIED, at).apply()

    /** Phone notifications for replies, mentions, zaps and messages (on by default). */
    /** Phone notification types the user turned off: "reply", "mention", "repost", "like", "zap", "dm". */
    fun notificationsOff(context: Context): Set<String> = prefs(context).getStringSet(NOTIF_OFF, null)?.toSet().orEmpty()
    fun setNotificationsOff(context: Context, off: Set<String>) = prefs(context).edit().putStringSet(NOTIF_OFF, off).apply()

    fun pushEnabled(context: Context): Boolean = prefs(context).getBoolean(PUSH, true)
    fun setPushEnabled(context: Context, on: Boolean) = prefs(context).edit().putBoolean(PUSH, on).apply()

    /** The user's DM inbox relays, remembered for the background check. */
    fun dmInbox(context: Context): List<String> =
        prefs(context).getString(DM_INBOX, null)?.split('\n')?.filter { it.isNotBlank() }.orEmpty()

    fun setDmInbox(context: Context, relays: List<String>) =
        prefs(context).edit().putString(DM_INBOX, relays.joinToString("\n")).apply()

    private fun keystoreKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }
}
