package io.github.andyop21.linkdownloader.download

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Runs one download in the background as a foreground `dataSync` worker (docs/architecture.md
 * § Background work). Input from [DownloadData.input]; progress and output read back with
 * [DownloadData.progressOf] and [DownloadData.resultOf].
 *
 * Progress goes to WorkManager and to the ongoing notification at most [PROGRESS_PERIOD_MS] apart.
 * A failed download returns [Result.failure], never a retry. Stopping the work cancels the
 * coroutine, which [VideoDownloader] turns into a clean cancel.
 */
class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val link = DownloadData.linkOf(inputData)
            ?: return Result.failure(DownloadData.failed(FailureReason.NotSupported))
        val selection = DownloadData.selectionOf(inputData)
        // A new download means an earlier choice was made or abandoned.
        Notifications.cancel(applicationContext, Notifications.ID_CHOOSE)

        val progress = MutableStateFlow(DownloadProgress())
        goForeground(progress.value)
        val outcome = try {
            coroutineScope {
                val publisher = launch {
                    progress.throttleLatest(PROGRESS_PERIOD_MS).collect { publish(it) }
                }
                try {
                    VideoDownloader(applicationContext).download(link, id.toString(), selection) { event ->
                        progress.update { it.after(event) }
                    }
                } finally {
                    publisher.cancel()
                }
            }
        } finally {
            Notifications.cancel(applicationContext, Notifications.ID_PROGRESS)
        }
        return finish(link, outcome)
    }

    private suspend fun finish(link: String, outcome: DownloadOutcome): Result = when (outcome) {
        is DownloadOutcome.Saved -> {
            Notifications.showSaved(applicationContext, outcome.videos, outcome.failed)
            val videos = outcome.videos.map { DownloadResult.Video(it.uri.toString(), it.fileName, it.title) }
            Result.success(DownloadData.saved(videos, outcome.failed))
        }
        is DownloadOutcome.ChooseVideos -> {
            val file = withContext(Dispatchers.IO) {
                ChoicesFile.write(applicationContext.cacheDir, id.toString(), ChoiceList(outcome.title, outcome.choices))
            }
            if (!isAppVisible()) Notifications.showChooseVideos(applicationContext, id, outcome.title)
            Result.success(DownloadData.choose(link, outcome.title, file.absolutePath))
        }
        is DownloadOutcome.Failed -> {
            Notifications.showFailed(applicationContext, outcome.reason)
            Result.failure(DownloadData.failed(outcome.reason))
        }
    }

    /**
     * Promotes the work to a foreground service. Android refuses that when the app is in the
     * background (the download was queued behind another job and started later); the download
     * then runs as ordinary work, with the notification posted directly.
     */
    private suspend fun goForeground(progress: DownloadProgress) {
        val notification = Notifications.progress(applicationContext, id, progress)
        try {
            setForeground(ForegroundInfo(Notifications.ID_PROGRESS, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC))
        } catch (e: IllegalStateException) {
            Log.w(TAG, "cannot run in the foreground; going on without it", e)
            Notifications.showProgress(applicationContext, notification)
        }
    }

    private suspend fun publish(progress: DownloadProgress) {
        setProgress(DownloadData.progress(progress))
        Notifications.showProgress(applicationContext, Notifications.progress(applicationContext, id, progress))
    }

    /** True when one of the app's activities is on screen. */
    private fun isAppVisible(): Boolean {
        val info = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(info)
        return info.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    }

    private companion object {
        const val TAG = "DownloadWorker"

        /** About four progress updates per second at most. */
        const val PROGRESS_PERIOD_MS = 250L
    }
}
