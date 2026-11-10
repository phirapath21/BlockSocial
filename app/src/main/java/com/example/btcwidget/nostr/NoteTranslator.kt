package com.example.btcwidget.nostr

import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Translates note text on the device with ML Kit. The text never leaves the phone; the
 * language model (about 30 MB per language) downloads once from Google the first time.
 */
object NoteTranslator {
    /** Thrown when the note is already in the reader's language, or the language isn't supported. */
    class Unsupported(message: String) : Exception(message)

    data class Result(val text: String, val fromLanguage: String)

    suspend fun translate(text: String, target: Locale = Locale.getDefault()): Result {
        val targetCode = TranslateLanguage.fromLanguageTag(target.language) ?: TranslateLanguage.ENGLISH
        val identifier = LanguageIdentification.getClient()
        val detected = try { identifier.identifyLanguage(text).await() } finally { identifier.close() }
        if (detected == "und") throw Unsupported("Couldn't tell which language this is")
        val source = TranslateLanguage.fromLanguageTag(detected) ?: throw Unsupported("Can't translate from this language yet")
        val fromName = Locale.forLanguageTag(detected).getDisplayLanguage(Locale.ENGLISH).ifBlank { detected }
        if (source == targetCode) throw Unsupported("This post is already in $fromName")
        val translator = Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(targetCode).build())
        try {
            translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
            return Result(translator.translate(text).await(), fromName)
        } finally {
            translator.close()
        }
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resumeWithException(it) }
        addOnCanceledListener { cont.cancel() }
    }
}
