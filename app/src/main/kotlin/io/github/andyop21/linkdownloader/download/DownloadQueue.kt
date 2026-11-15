package io.github.andyop21.linkdownloader.download

import android.content.Context
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * What the screen uses to run downloads and keep yt-dlp current (docs/architecture.md
 * § Background work). Downloads and updates share one unique work chain, [ENGINE_WORK], appended
 * with [ExistingWorkPolicy.APPEND_OR_REPLACE], so an update never replaces yt-dlp under a running
 * download and downloads run one at a time.
 */
class DownloadQueue(
    context: Context,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private val appContext = context.applicationContext
    private val workManager: WorkManager get() = WorkManager.getInstance(appContext)
    private val updateStore = EngineUpdateStore(appContext)

    /** Queues a download of [link]; [selection] holds the chosen positions of a post with several videos. */
    fun startDownload(link: String, selection: List<Int>? = null): UUID {
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(DownloadData.input(link, selection))
            .addTag(DownloadData.TAG_DOWNLOAD)
            .build()
        workManager.enqueueUniqueWork(ENGINE_WORK, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        return request.id
    }

    /** Queues a yt-dlp update when the last successful check is a day old or more; true if it did. */
    fun updateEngineIfDue(): Boolean {
        if (!EngineUpdateRule.isDue(updateStore.lastCheckMillis, clock())) return false
        enqueueUpdate(force = false)
        return true
    }

    /** Queues a yt-dlp update now, however recent the last check (the About sheet). */
    fun updateEngineNow(): UUID = enqueueUpdate(force = true)

    /** Cancels the work [id]; a running download stops and deletes its partial files. */
    fun cancel(id: UUID) {
        workManager.cancelWorkById(id)
    }

    /** Every download WorkManager knows, running or finished. */
    fun observeDownloads(): Flow<List<WorkInfo>> = workManager.getWorkInfosByTagFlow(DownloadData.TAG_DOWNLOAD)

    /**
     * yt-dlp's version, such as `2026.10.26`, or null if it cannot be read. After an update it is
     * the stored version; before the first one, yt-dlp is asked (about a second).
     */
    suspend fun engineVersion(): String? = withContext(Dispatchers.IO) {
        val youtubeDl = YoutubeDL.getInstance()
        youtubeDl.version(appContext)?.takeIf { it.isNotBlank() } ?: try {
            youtubeDl.init(appContext)
            youtubeDl.execute(YoutubeDLRequest(emptyList()).addOption("--version")).out.trim().ifEmpty { null }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "cannot read the yt-dlp version", e)
            null
        }
    }

    private fun enqueueUpdate(force: Boolean): UUID {
        val request = OneTimeWorkRequestBuilder<UpdateWorker>()
            .setInputData(UpdateWorker.input(force))
            .addTag(TAG_UPDATE)
            .build()
        workManager.enqueueUniqueWork(ENGINE_WORK, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        return request.id
    }

    companion object {
        /** The unique work name downloads and yt-dlp updates share. */
        const val ENGINE_WORK = "engine"

        /** Tag on yt-dlp update requests. */
        const val TAG_UPDATE = "engine-update"

        private const val TAG = "DownloadQueue"
    }
}
