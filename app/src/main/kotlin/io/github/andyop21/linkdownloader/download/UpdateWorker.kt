package io.github.andyop21.linkdownloader.download

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Updates yt-dlp to its latest stable release and records when that check succeeded.
 *
 * It always ends in [Result.success], whatever happens, and only logs: it shares the `engine`
 * chain with downloads, and a failed job would fail the download queued after it. For the same
 * reason it has no network constraint (a download queued behind it would wait for the network
 * instead of failing as *no internet*); offline, it skips the check and leaves the time unrecorded,
 * so the next app start tries again.
 */
class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val store = EngineUpdateStore(applicationContext)
        val forced = inputData.getBoolean(KEY_FORCE, false)
        when {
            !forced && !EngineUpdateRule.isDue(store.lastCheckMillis, System.currentTimeMillis()) ->
                Log.i(TAG, "yt-dlp was checked less than a day ago; skipping")
            !applicationContext.isOnline() ->
                Log.i(TAG, "offline; skipping the yt-dlp update check")
            else -> update(store)
        }
        Result.success()
    }

    private fun update(store: EngineUpdateStore) {
        val youtubeDl = YoutubeDL.getInstance()
        try {
            youtubeDl.init(applicationContext)
            val status = youtubeDl.updateYoutubeDL(applicationContext, YoutubeDL.UpdateChannel.STABLE)
            store.lastCheckMillis = System.currentTimeMillis()
            Log.i(TAG, "yt-dlp update check: $status; version ${youtubeDl.version(applicationContext)}")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "yt-dlp update check failed", e)
        }
    }

    companion object {
        private const val TAG = "UpdateWorker"
        private const val KEY_FORCE = "force"

        /** Input for an update; [force] skips the once-a-day rule (the About sheet's button). */
        fun input(force: Boolean): Data = Data.Builder().putBoolean(KEY_FORCE, force).build()
    }
}
