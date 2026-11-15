package io.github.andyop21.linkdownloader.ui

import android.content.Context
import androidx.work.WorkInfo
import io.github.andyop21.linkdownloader.download.ChoiceList
import io.github.andyop21.linkdownloader.download.ChoicesFile
import io.github.andyop21.linkdownloader.download.DownloadData
import io.github.andyop21.linkdownloader.download.DownloadProgress
import io.github.andyop21.linkdownloader.download.DownloadQueue
import io.github.andyop21.linkdownloader.download.DownloadResult
import io.github.andyop21.linkdownloader.download.EngineUpdateResult
import io.github.andyop21.linkdownloader.download.UpdateWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * What [MainViewModel] needs from the download side, kept small so unit tests use a fake.
 * [WorkDownloads] is the real one, on top of [DownloadQueue].
 */
interface Downloads {

    /** Queues a download of [link]; [selection] holds the chosen positions of a post with several videos. */
    fun start(link: String, selection: List<Int>?): UUID

    /** Cancels the download [id]. */
    fun cancel(id: UUID)

    /** Every download WorkManager knows, running or finished. */
    fun jobs(): Flow<List<DownloadJob>>

    /** The choices a post's job left at [path], or null when the file is gone. */
    suspend fun readChoices(path: String): ChoiceList?

    /** Queues a yt-dlp update now and waits for its result. */
    suspend fun updateEngine(): EngineUpdateResult

    /** yt-dlp's version, or null when it cannot be read. */
    suspend fun engineVersion(): String?
}

/** A download as the screen needs it: its state, its progress while running and its result once finished. */
data class DownloadJob(
    val id: UUID,
    val state: JobState,
    val progress: DownloadProgress? = null,
    val result: DownloadResult? = null,
)

enum class JobState {
    /** Queued, or waiting behind another job of the chain. */
    Waiting,
    Running,
    Succeeded,
    Failed,
    Cancelled,
    ;

    val isFinished: Boolean get() = this == Succeeded || this == Failed || this == Cancelled
}

/** [Downloads] on WorkManager, through [DownloadQueue]. */
class WorkDownloads(context: Context) : Downloads {

    private val queue = DownloadQueue(context)

    override fun start(link: String, selection: List<Int>?): UUID = queue.startDownload(link, selection)

    override fun cancel(id: UUID) = queue.cancel(id)

    override fun jobs(): Flow<List<DownloadJob>> = queue.observeDownloads().map { infos -> infos.map { it.toDownloadJob() } }

    override suspend fun readChoices(path: String): ChoiceList? = withContext(Dispatchers.IO) { ChoicesFile.read(path) }

    override suspend fun updateEngine(): EngineUpdateResult {
        val id = queue.updateEngineNow()
        // The work may not be in WorkManager's database yet when the flow starts, so nulls are skipped.
        val info = queue.observeWork(id).filterNotNull().first { it.state.isFinished }
        return UpdateWorker.resultOf(info.outputData)
    }

    override suspend fun engineVersion(): String? = queue.engineVersion()
}

/** This work as a [DownloadJob]. */
fun WorkInfo.toDownloadJob(): DownloadJob = DownloadJob(
    id = id,
    state = when (state) {
        WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> JobState.Waiting
        WorkInfo.State.RUNNING -> JobState.Running
        WorkInfo.State.SUCCEEDED -> JobState.Succeeded
        WorkInfo.State.FAILED -> JobState.Failed
        WorkInfo.State.CANCELLED -> JobState.Cancelled
    },
    progress = DownloadData.progressOf(progress),
    result = DownloadData.resultOf(outputData),
)
