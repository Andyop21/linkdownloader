package io.github.andyop21.linkdownloader.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.andyop21.linkdownloader.download.DownloadProgress
import io.github.andyop21.linkdownloader.download.DownloadResult
import io.github.andyop21.linkdownloader.download.DownloadStage
import io.github.andyop21.linkdownloader.download.EngineUpdateResult
import io.github.andyop21.linkdownloader.download.FailureReason
import io.github.andyop21.linkdownloader.link.LinkParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * The screen's logic (docs/product.md § Behaviour): turns taps into downloads and the downloads
 * WorkManager knows into one [UiState].
 *
 * A finished download is shown only when this ViewModel started it, saw it running, or was asked
 * to show its choice from the notification ([onOpenChoice]); so a fresh start is always idle, and
 * a download still running is shown as running (B9).
 *
 * The bar looks after itself: a saved download or a cancel empties it, so the next link starts
 * from a clean bar (B6, B5); the clear button empties it on demand (B13); and the link saved last
 * is not downloaded twice by accident (B12).
 */
class MainViewModel(private val downloads: Downloads) : ViewModel() {

    private val mutableState = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = mutableState.asStateFlow()

    /** The latest list from [Downloads.jobs]. */
    private var jobs: List<DownloadJob> = emptyList()

    /** The download the screen follows, from enqueue (or first sight) to its end. */
    private var current: UUID? = null

    /** Jobs whose end was shown or that were cancelled or abandoned here; they are never shown again. */
    private val settled = mutableSetOf<UUID>()

    /** The link of the post whose choice sheet is open. */
    private var choosingLink: String? = null

    /** The highest progress shown for [current], so the bar never goes backwards. */
    private var shownProgress: Float? = null

    /** The link [current] was started with; null for a job adopted from an earlier run. */
    private var currentLink: String? = null

    /** The link this screen saved last and what it saved, for [Status.AlreadySaved]. */
    private var lastSaved: SavedLink? = null

    init {
        viewModelScope.launch {
            downloads.jobs().collect { list ->
                jobs = list
                reconcile()
            }
        }
    }

    /** **Paste** was tapped; [clipText] is the clipboard's first item as text, or null when it is empty (B1, B2). */
    fun onPaste(clipText: String?) = startFrom(clipText, fromClipboard = true)

    /** The keyboard's action key was pressed with [barText] in the bar (B3). */
    fun onSubmit(barText: String) = startFrom(barText, fromClipboard = false)

    /** The bar was edited. A message about an earlier link no longer applies, so it gives way to idle. */
    fun onBarChanged(text: String) = mutableState.update {
        it.copy(bar = text, status = if (it.status.isSettled) Status.Idle else it.status)
    }

    /** The bar's clear button: empties the bar and dismisses the last message (B13). */
    fun onClear() = mutableState.update {
        if (it.status.isSettled) it.copy(bar = "", status = Status.Idle) else it
    }

    /** **Try again** after a failure that may pass: the bar's link is started again (B7). */
    fun onRetry() {
        val status = state.value.status
        if (status is Status.Failed && status.reason.retryable) startFrom(state.value.bar, fromClipboard = false, checkSaved = false)
    }

    /** **Download again** for a link that was already saved: a second copy is downloaded on purpose (B12). */
    fun onDownloadAgain() {
        val saved = lastSaved ?: return
        if (state.value.status !is Status.AlreadySaved) return
        lastSaved = null
        start(saved.link, null)
    }

    /** **Cancel** on the status card (B5). The job's partial files are deleted by the worker. */
    fun onCancel() {
        current?.let { id ->
            downloads.cancel(id)
            settled += id
        }
        current = null
        currentLink = null
        mutableState.update { it.copy(bar = "", status = Status.Idle) }
    }

    /** Download the chosen videos of the post on the choice sheet, [positions] as [io.github.andyop21.linkdownloader.download.VideoChoice.index] values (B11). */
    fun onChoose(positions: List<Int>) {
        val link = choosingLink ?: return
        if (positions.isEmpty() || state.value.status !is Status.Choosing) return
        choosingLink = null
        start(link, positions.distinct().sorted())
    }

    /** The choice sheet was closed without choosing: nothing is downloaded (B11). */
    fun onDismissChoice() {
        choosingLink = null
        if (state.value.status is Status.Choosing) setStatus(Status.Idle)
    }

    /**
     * The app was opened from the "Choose which videos" notification of the job [workId]. Its
     * choice is shown even though this ViewModel did not see it run (the process may have died).
     */
    fun onOpenChoice(workId: UUID) {
        if (workId in settled || state.value.status.isRunning) return
        current = workId
        currentLink = null
        shownProgress = null
        reconcile()
    }

    /** The header was long-pressed: open the About sheet and read yt-dlp's version (B10). */
    fun onOpenAbout() {
        mutableState.update { it.copy(about = it.about.copy(open = true)) }
        loadEngineVersion()
    }

    fun onCloseAbout() = mutableState.update { it.copy(about = it.about.copy(open = false)) }

    /** **Update yt-dlp** on the About sheet: "Checking…", then "Up to date" or the new version (B10). */
    fun onUpdateEngine() {
        if (state.value.about.check == EngineCheck.Checking) return
        setCheck(EngineCheck.Checking)
        viewModelScope.launch {
            val result = downloads.updateEngine()
            val check = when (result) {
                is EngineUpdateResult.Updated -> EngineCheck.Updated(result.version)
                is EngineUpdateResult.UpToDate -> EngineCheck.UpToDate
                EngineUpdateResult.NotChecked -> EngineCheck.NotChecked
            }
            setCheck(check)
            val version = when (result) {
                is EngineUpdateResult.Updated -> result.version
                is EngineUpdateResult.UpToDate -> result.version
                EngineUpdateResult.NotChecked -> null
            }
            if (version != null) {
                mutableState.update { it.copy(about = it.about.copy(engineVersion = version, versionLoading = false)) }
            } else if (check != EngineCheck.NotChecked) {
                loadEngineVersion()
            }
        }
    }

    private fun startFrom(text: String?, fromClipboard: Boolean, checkSaved: Boolean = true) {
        if (state.value.status.isRunning) return
        val link = LinkParser.firstLink(text)
        val saved = lastSaved
        when {
            link == null -> setStatus(Status.NoLink(fromClipboard))
            checkSaved && saved != null && saved.link == link ->
                mutableState.update { it.copy(bar = link, status = Status.AlreadySaved(saved.videos, saved.saved)) }
            else -> start(link, null)
        }
    }

    private fun start(link: String, selection: List<Int>?) {
        val id = downloads.start(link, selection)
        current = id
        currentLink = link
        shownProgress = null
        mutableState.update { it.copy(bar = link, status = Status.Preparing) }
        reconcile()
    }

    /** Brings the status in line with [current]'s job, adopting a running job when there is none. */
    private fun reconcile() {
        val id = current ?: adoptRunningJob() ?: return
        val job = jobs.firstOrNull { it.id == id } ?: return
        when (job.state) {
            JobState.Waiting -> setStatus(Status.Preparing)
            JobState.Running -> setStatus(runningStatus(job.progress))
            JobState.Succeeded, JobState.Failed, JobState.Cancelled -> finish(job)
        }
    }

    /** A job still running that the screen does not follow yet (B9), unless the choice sheet is open. */
    private fun adoptRunningJob(): UUID? {
        if (state.value.status is Status.Choosing) return null
        val job = jobs.firstOrNull { !it.state.isFinished && it.id !in settled } ?: return null
        current = job.id
        currentLink = null
        shownProgress = null
        return job.id
    }

    private fun runningStatus(progress: DownloadProgress?): Status {
        // A post's later videos start with a Preparing stage; once anything is known it stays a download.
        if (progress == null || (progress.stage == DownloadStage.Preparing && progress.title == null && progress.fraction == null)) {
            return Status.Preparing
        }
        val fraction = progress.fraction?.toFloat()?.coerceIn(0f, 1f)
        val shown = if (fraction == null) shownProgress else maxOf(fraction, shownProgress ?: 0f)
        shownProgress = shown
        return when (progress.stage) {
            DownloadStage.Saving -> Status.Saving(progress.title, progress.position, progress.count, shown)
            DownloadStage.Preparing, DownloadStage.Downloading ->
                Status.Downloading(progress.title, progress.position, progress.count, shown)
        }
    }

    private fun finish(job: DownloadJob) {
        settled += job.id
        current = null
        shownProgress = null
        val link = currentLink
        currentLink = null
        val result = job.result
        when {
            job.state == JobState.Cancelled -> mutableState.update { it.copy(bar = "", status = Status.Idle) }
            result is DownloadResult.Saved -> {
                if (link != null) lastSaved = SavedLink(link, result.videos, result.savedCount)
                val done = Status.Done(job.id, result.videos, result.savedCount, result.failed)
                mutableState.update { it.copy(bar = "", status = done) }
            }
            result is DownloadResult.Choose -> showChoice(result)
            result is DownloadResult.Failed -> setStatus(Status.Failed(result.reason))
            else -> setStatus(Status.Failed(FailureReason.Unknown))
        }
    }

    private fun showChoice(result: DownloadResult.Choose) {
        setStatus(Status.Preparing)
        viewModelScope.launch {
            val list = downloads.readChoices(result.choicesFile)
            // Cancel may have been tapped while the file was read.
            if (current != null || state.value.status != Status.Preparing) return@launch
            if (list == null || list.choices.isEmpty()) {
                setStatus(Status.Failed(FailureReason.Unknown))
            } else {
                choosingLink = result.link
                setStatus(Status.Choosing(result.title.ifBlank { list.title }, list.choices))
            }
        }
    }

    private fun loadEngineVersion() {
        if (state.value.about.versionLoading) return
        mutableState.update { it.copy(about = it.about.copy(versionLoading = true)) }
        viewModelScope.launch {
            val version = downloads.engineVersion()
            mutableState.update { it.copy(about = it.about.copy(engineVersion = version, versionLoading = false)) }
        }
    }

    private fun setStatus(status: Status) = mutableState.update { it.copy(status = status) }

    /** A link and what downloading it saved. */
    private class SavedLink(val link: String, val videos: List<DownloadResult.Video>, val saved: Int)

    private fun setCheck(check: EngineCheck) = mutableState.update { it.copy(about = it.about.copy(check = check)) }

    companion object {
        /** Builds the ViewModel on WorkManager for [application]. */
        fun factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer { MainViewModel(WorkDownloads(application)) }
        }
    }
}
