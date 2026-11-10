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
        prefs(context).edit().clear()
            .putString(PUBKEY, pubkey).putString(TYPE, SignerType.AMBER.name).putString(PACKAGE, signerPackage)
            .apply()
    }

    /** Encrypts and stores [seckey]; returns the account. */
    fun saveLocal(context: Context, seckey: String): NostrAccount {
        require(Schnorr.isValidSecretKey(seckey)) { "Not a valid private key" }
        val pubkey = Schnorr.publicKey(seckey)
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

    /** The account's signer, or null when logged out (or the key can't be read). */
    fun signer(context: Context): NostrSigner? {
        val account = account(context) ?: return null
        return when (account.type) {
            SignerType.LOCAL -> secretKey(context)?.let { LocalSigner(it) }
            SignerType.AMBER -> account.signerPackage?.let { AmberSigner(context.applicationContext, account.pubkey, it) }
        }
    }

    /** Logs out: deletes the account, including the encrypted key and the Keystore key. */
    fun clear(context: Context) {
        prefs(context).edit().clear().commit()
        runCatching { KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(KEY_ALIAS) }
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
