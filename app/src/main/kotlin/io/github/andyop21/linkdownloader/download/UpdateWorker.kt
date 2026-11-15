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
 *
 * Its output says how the check went ([EngineUpdateResult], read with [resultOf]), for the About
 * sheet's **Update yt-dlp** button.
 */
class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val store = EngineUpdateStore(applicationContext)
        val forced = inputData.getBoolean(KEY_FORCE, false)
        val result = when {
            !forced && !EngineUpdateRule.isDue(store.lastCheckMillis, System.currentTimeMillis()) -> {
                Log.i(TAG, "yt-dlp was checked less than a day ago; skipping")
                EngineUpdateResult.NotChecked
            }
            !applicationContext.isOnline() -> {
                Log.i(TAG, "offline; skipping the yt-dlp update check")
                EngineUpdateResult.NotChecked
            }
            else -> update(store)
        }
        Result.success(output(result))
    }

    private fun update(store: EngineUpdateStore): EngineUpdateResult {
        val youtubeDl = YoutubeDL.getInstance()
        return try {
            youtubeDl.init(applicationContext)
            val status = youtubeDl.updateYoutubeDL(applicationContext, YoutubeDL.UpdateChannel.STABLE)
            store.lastCheckMillis = System.currentTimeMillis()
            val version = youtubeDl.version(applicationContext)?.takeIf { it.isNotBlank() }
            Log.i(TAG, "yt-dlp update check: $status; version $version")
            if (status == YoutubeDL.UpdateStatus.DONE) {
                EngineUpdateResult.Updated(version)
            } else {
                EngineUpdateResult.UpToDate(version)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "yt-dlp update check failed", e)
            EngineUpdateResult.NotChecked
        }
    }

    companion object {
        private const val TAG = "UpdateWorker"
        private const val KEY_FORCE = "force"
        private const val KEY_RESULT = "result"
        private const val KEY_VERSION = "version"

        private const val RESULT_UPDATED = "updated"
        private const val RESULT_UP_TO_DATE = "up_to_date"
        private const val RESULT_NOT_CHECKED = "not_checked"

        /** Input for an update; [force] skips the once-a-day rule (the About sheet's button). */
        fun input(force: Boolean): Data = Data.Builder().putBoolean(KEY_FORCE, force).build()

        /** The output for [result]. */
        fun output(result: EngineUpdateResult): Data = when (result) {
            is EngineUpdateResult.Updated -> Data.Builder()
                .putString(KEY_RESULT, RESULT_UPDATED)
                .putString(KEY_VERSION, result.version)
                .build()
            is EngineUpdateResult.UpToDate -> Data.Builder()
                .putString(KEY_RESULT, RESULT_UP_TO_DATE)
                .putString(KEY_VERSION, result.version)
                .build()
            EngineUpdateResult.NotChecked -> Data.Builder().putString(KEY_RESULT, RESULT_NOT_CHECKED).build()
        }

        /** How a finished update went, from its output [data]; an output without a result reads as not checked. */
        fun resultOf(data: Data): EngineUpdateResult {
            val version = data.getString(KEY_VERSION)?.takeIf { it.isNotBlank() }
            return when (data.getString(KEY_RESULT)) {
                RESULT_UPDATED -> EngineUpdateResult.Updated(version)
                RESULT_UP_TO_DATE -> EngineUpdateResult.UpToDate(version)
                else -> EngineUpdateResult.NotChecked
            }
        }
    }
}
