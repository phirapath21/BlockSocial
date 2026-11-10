package com.example.btcwidget.nostr

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.btcwidget.nostr.core.Events
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Posts written now and published later. Each one waits on the phone ("nostr_scheduled",
 * excluded from backup) until WorkManager runs it at its time; it is signed only then, so a
 * signer app may ask for approval at that moment. The phone must be on and online.
 */
object ScheduledPosts {
    private const val FILE = "nostr_scheduled"

    data class Post(val id: String, val pubkey: String, val at: Long, val content: String, val tags: List<List<String>>)

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun all(context: Context): List<Post> = prefs(context).all.values.mapNotNull { runCatching { parse(it as String) }.getOrNull() }.sortedBy { it.at }

    fun forAccount(context: Context, pubkey: String) = all(context).filter { it.pubkey == pubkey }

    fun get(context: Context, id: String): Post? = prefs(context).getString(id, null)?.let { runCatching { parse(it) }.getOrNull() }

    /** Saves [content] to publish at [at] (seconds) and schedules it. */
    fun schedule(context: Context, pubkey: String, at: Long, content: String, tags: List<List<String>>): Post {
        val post = Post(UUID.randomUUID().toString(), pubkey, at, content, tags)
        prefs(context).edit().putString(post.id, toJson(post)).commit()
        enqueue(context, post)
        return post
    }

    fun cancel(context: Context, id: String) {
        WorkManager.getInstance(context).cancelUniqueWork(work(id))
        prefs(context).edit().remove(id).commit()
    }

    /** Removes [pubkey]'s scheduled posts (on logout or removing the account). */
    fun clear(context: Context, pubkey: String) = forAccount(context, pubkey).forEach { cancel(context, it.id) }

    private fun enqueue(context: Context, post: Post) {
        val delay = (post.at * 1000 - System.currentTimeMillis()).coerceAtLeast(0)
        val request = OneTimeWorkRequestBuilder<Worker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setConstraints(androidx.work.Constraints.Builder().setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build())
            .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .setInputData(workDataOf(KEY_ID to post.id))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(work(post.id), ExistingWorkPolicy.REPLACE, request)
    }

    private fun work(id: String) = "scheduled-post-$id"
    private const val KEY_ID = "id"

    private fun toJson(p: Post) = JSONObject().put("id", p.id).put("pubkey", p.pubkey).put("at", p.at).put("content", p.content)
        .put("tags", JSONArray(p.tags.map { JSONArray(it) })).toString()

    private fun parse(json: String): Post {
        val o = JSONObject(json)
        val t = o.getJSONArray("tags")
        return Post(o.getString("id"), o.getString("pubkey"), o.getLong("at"), o.getString("content"),
            (0 until t.length()).map { i -> t.getJSONArray(i).let { a -> (0 until a.length()).map { a.getString(it) } } })
    }

    /** Publishes one scheduled post when its time comes. */
    class Worker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            val ctx = applicationContext
            val id = inputData.getString(KEY_ID) ?: return Result.failure()
            val post = get(ctx, id) ?: return Result.success()
            val repo = NostrRepository.get(ctx)
            if (repo.pubkey != post.pubkey) {
                NostrBackground.notifyAccount(ctx, id.hashCode(), "Scheduled post not sent", "Switch back to the account that wrote it, then it will be sent.")
                return Result.retry()
            }
            return try {
                repo.cacheReady.join()
                // Signed now, with the time it's published.
                repo.publish(Events.note(post.pubkey, post.content, post.tags).copy(createdAt = Events.now()))
                prefs(ctx).edit().remove(id).commit()
                NostrBackground.notifyAccount(ctx, id.hashCode(), "Scheduled post published", post.content.take(80))
                Result.success()
            } catch (e: SignerUnavailable) {
                // A signer app that needs approval can't ask while Blockface is closed.
                if (runAttemptCount == 0) NostrBackground.notifyAccount(ctx, id.hashCode(), "Scheduled post waiting", "Open Blockface to approve it in your signer app.")
                if (runAttemptCount < 5) Result.retry() else Result.failure()
            } catch (e: SignerRejected) {
                NostrBackground.notifyAccount(ctx, id.hashCode(), "Scheduled post not sent", "Your signer declined it. It's kept; open Blockface to post it.")
                Result.failure()
            } catch (e: Exception) {
                if (runAttemptCount < 5) Result.retry() else {
                    NostrBackground.notifyAccount(ctx, id.hashCode(), "Scheduled post not sent", "Couldn't reach your relays or signer. It's kept in Scheduled posts.")
                    Result.failure()
                }
            }
        }
    }
}
