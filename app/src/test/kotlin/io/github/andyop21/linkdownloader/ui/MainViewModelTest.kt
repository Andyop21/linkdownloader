package io.github.andyop21.linkdownloader.ui

import io.github.andyop21.linkdownloader.download.ChoiceList
import io.github.andyop21.linkdownloader.download.DownloadProgress
import io.github.andyop21.linkdownloader.download.DownloadResult
import io.github.andyop21.linkdownloader.download.DownloadStage
import io.github.andyop21.linkdownloader.download.EngineUpdateResult
import io.github.andyop21.linkdownloader.download.FailureReason
import io.github.andyop21.linkdownloader.download.VideoChoice
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val downloads = FakeDownloads()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = MainViewModel(downloads)

    private fun MainViewModel.status() = state.value.status

    // B1

    @Test
    fun `paste with a link fills the bar and starts the download at once`() {
        val vm = viewModel()
        vm.onPaste("https://example.com/v/1")

        assertEquals(listOf(Start("https://example.com/v/1", null)), downloads.started)
        assertEquals("https://example.com/v/1", vm.state.value.bar)
        assertEquals(Status.Preparing, vm.status())
    }

    @Test
    fun `paste with share text uses the first link`() {
        val vm = viewModel()
        vm.onPaste("A funny clip https://example.com/v/1?s=20 and https://example.com/v/2")

        assertEquals("https://example.com/v/1?s=20", downloads.started.single().link)
        assertEquals("https://example.com/v/1?s=20", vm.state.value.bar)
    }

    // B2

    @Test
    fun `paste without a link leaves the bar alone and says so`() {
        val vm = viewModel()
        vm.onBarChanged("typed text")
        vm.onPaste("no link in here")

        assertEquals("typed text", vm.state.value.bar)
        assertEquals(Status.NoLink(fromClipboard = true), vm.status())
        assertTrue(downloads.started.isEmpty())
    }

    @Test
    fun `paste with an empty clipboard says there is no link`() {
        val vm = viewModel()
        vm.onPaste(null)
        assertEquals(Status.NoLink(fromClipboard = true), vm.status())
        vm.onPaste("")
        assertEquals(Status.NoLink(fromClipboard = true), vm.status())
        assertTrue(downloads.started.isEmpty())
    }

    @Test
    fun `editing the bar clears the no-link message`() {
        val vm = viewModel()
        vm.onPaste(null)
        vm.onBarChanged("h")
        assertEquals(Status.Idle, vm.status())
        assertEquals("h", vm.state.value.bar)
    }

    // B3

    @Test
    fun `go with a link in the bar starts the download`() {
        val vm = viewModel()
        vm.onBarChanged("look: https://example.com/v/1")
        vm.onSubmit(vm.state.value.bar)

        assertEquals("https://example.com/v/1", downloads.started.single().link)
        assertEquals("https://example.com/v/1", vm.state.value.bar)
        assertEquals(Status.Preparing, vm.status())
    }

    @Test
    fun `go without a link says so and starts nothing`() {
        val vm = viewModel()
        vm.onBarChanged("www.example.com")
        vm.onSubmit("www.example.com")

        assertEquals(Status.NoLink(fromClipboard = false), vm.status())
        assertEquals("www.example.com", vm.state.value.bar)
        assertTrue(downloads.started.isEmpty())
    }

    // B4

    @Test
    fun `a running download shows its title, position and progress`() {
        val vm = viewModel()
        vm.onPaste("https://example.com/p/1")
        val id = downloads.lastId

        downloads.emit(DownloadJob(id, JobState.Waiting))
        assertEquals(Status.Preparing, vm.status())

        downloads.emit(DownloadJob(id, JobState.Running, DownloadProgress(DownloadStage.Preparing)))
        assertEquals(Status.Preparing, vm.status())

        downloads.emit(DownloadJob(id, JobState.Running, DownloadProgress(DownloadStage.Downloading, "Clip", 2, 3, null)))
        assertEquals(Status.Downloading("Clip", 2, 3, null), vm.status())

        downloads.emit(DownloadJob(id, JobState.Running, DownloadProgress(DownloadStage.Downloading, "Clip", 2, 3, 0.5)))
        assertEquals(Status.Downloading("Clip", 2, 3, 0.5f), vm.status())

        downloads.emit(DownloadJob(id, JobState.Running, DownloadProgress(DownloadStage.Saving, "Clip", 3, 3, 0.97)))
        assertEquals(Status.Saving("Clip", 3, 3, 0.97f), vm.status())
    }

    @Test
    fun `progress never goes backwards`() {
        val vm = viewModel()
        vm.onPaste("https://example.com/v/1")
        val id = downloads.lastId

        downloads.emit(DownloadJob(id, JobState.Running, DownloadProgress(DownloadStage.Downloading, "Clip", 1, 1, 0.6)))
        downloads.emit(DownloadJob(id, JobState.Running, DownloadProgress(DownloadStage.Downloading, "Clip", 1, 1, 0.4)))
        assertEquals(Status.Downloading("Clip", 1, 1, 0.6f), vm.status())

        downloads.emit(DownloadJob(id, JobState.Running, DownloadProgress(DownloadStage.Downloading, "Clip", 1, 1, null)))
        assertEquals(Status.Downloading("Clip", 1, 1, 0.6f), vm.status())
    }

    @Test
    fun `a later video of a post starting again keeps showing the download`() {
        val vm = viewModel()
        vm.onPaste("https://example.com/p/1")
        val id = downloads.lastId

        downloads.emit(DownloadJob(id, JobState.Running, DownloadProgress(DownloadStage.Preparing, "First", 1, 2, 0.5)))
        assertEquals(Status.Downloading("First", 1, 2, 0.5f), vm.status())
    }

    @Test
    fun `paste and go do nothing while a download runs`() {
        val vm = viewModel()
        vm.onPaste("https://example.com/v/1")
        vm.onPaste("https://example.com/v/2")
        vm.onSubmit("https://example.com/v/3")

        assertEquals(1, downloads.started.size)
        assertEquals("https://example.com/v/1", vm.state.value.bar)
    }

    // B5

    @Test
    fun `cancel stops the download and returns to idle`() {
        val vm = viewModel()
        vm.onPaste("https://example.com/v/1")
        val id = downloads.lastId
        downloads.emit(DownloadJob(id, JobState.Running, DownloadProgress(DownloadStage.Downloading, "Clip", 1, 1, 0.3)))

        vm.onCancel()
        assertEquals(listOf(id), downloads.cancelled)
        assertEquals(Status.Idle, vm.status())

        // WorkManager still reports it running for a moment, then cancelled: the screen stays idle.
        downloads.emit(DownloadJob(id, JobState.Running, DownloadProgress(DownloadStage.Downloading, "Clip", 1, 1, 0.31)))
        assertEquals(Status.Idle, vm.status())
        downloads.emit(DownloadJob(id, JobState.Cancelled))
        assertEquals(Status.Idle, vm.status())
    }

    @Test
    fun `a cancel from the notification returns to idle`() {
        val vm = viewModel()
        vm.onPaste("https://example.com/v/1")
        val id = downloads.lastId
        downloads.emit(DownloadJob(id, JobState.Running, DownloadProgress(DownloadStage.Downloading, "Clip", 1, 1, 0.3)))

        downloads.emit(DownloadJob(id, JobState.Cancelled))
        assertEquals(Status.Idle, vm.status())
        assertTrue(downloads.cancelled.isEmpty())
    }

    @Test
    fun `a new download can start after a cancel`() {
        val vm = viewModel()
        vm.onPaste("https://example.com/v/1")
        vm.onCancel()
        vm.onPaste("https://example.com/v/2")

        assertEquals(2, downloads.started.size)
        assertEquals(Status.Preparing, vm.status())
    }

    // B6

    @Test
    fun `a finished download shows what was saved`() {
        val vm = viewModel()
        vm.onPaste("https://example.com/v/1")
        val id = downloads.lastId
        val video = DownloadResult.Video("content://media/1", "Clip.mp4", "Clip")

        downloads.emit(DownloadJob(id, JobState.Succeeded, result = DownloadResult.Saved(listOf(video), 1, 0)))
        assertEquals(Status.Done(id, listOf(video), saved = 1, failed = 0), vm.status())
    }

    @Test
    fun `a post shows how many were saved and how many could not be`() {
        val vm = viewModel()
        vm.onPaste("https://example.com/p/1")
        val id = downloads.lastId
        val videos = listOf(
            DownloadResult.Video("content://media/1", "A.mp4", "A"),
            DownloadResult.Video("content://media/2", "B.mp4", "B"),
        )

        downloads.emit(DownloadJob(id, JobState.Succeeded, result = DownloadResult.Saved(videos, 2, 1)))
        assertEquals(Status.Done(id, videos, saved = 2, failed = 1), vm.status())
    }

    @Test
    fun `a new paste after done starts another download`() {
        val vm = viewModel()
        vm.onPaste("https://example.com/v/1")
        val first = downloads.lastId
        downloads.emit(DownloadJob(first, JobState.Succeeded, result = DownloadResult.Saved(emptyList(), 1, 0)))
        vm.onPaste("https://example.com/v/2")

        assertEquals(Status.Preparing, vm.status())
        // The first job is still in WorkManager's list; it is not shown again.
        downloads.emit(
            DownloadJob(first, JobState.Succeeded, result = DownloadResult.Saved(emptyList(), 1, 0)),
            DownloadJob(downloads.lastId, JobState.Waiting),
        )
        assertEquals(Status.Preparing, vm.status())
    }

    // B7

    @Test
    fun `a failed download shows its reason`() {
        FailureReason.entries.forEach { reason ->
            val vm = viewModel()
            vm.onPaste("https://example.com/v/1")
            downloads.emit(DownloadJob(downloads.lastId, JobState.Failed, result = DownloadResult.Failed(reason)))
            assertEquals(Status.Failed(reason), vm.status())
        }
    }

    @Test
    fun `a failure without a readable reason is unknown`() {
        val vm = viewModel()
        vm.onPaste("https://example.com/v/1")
        downloads.emit(DownloadJob(downloads.lastId, JobState.Failed))
        assertEquals(Status.Failed(FailureReason.Unknown), vm.status())
    }

    // B9

    @Test
    fun `a fresh start is idle even when earlier downloads finished`() {
        downloads.emit(
            DownloadJob(UUID.randomUUID(), JobState.Succeeded, result = DownloadResult.Saved(emptyList(), 1, 0)),
            DownloadJob(UUID.randomUUID(), JobState.Failed, result = DownloadResult.Failed(FailureReason.NoInternet)),
            DownloadJob(UUID.randomUUID(), JobState.Succeeded, result = DownloadResult.Choose("https://example.com/p/1", "Post", "/c.json")),
            DownloadJob(UUID.randomUUID(), JobState.Cancelled),
        )
        val vm = viewModel()
        assertEquals(UiState(), vm.state.value)
    }

    @Test
    fun `a download still running is shown as running, then its end`() {
        val id = UUID.randomUUID()
        downloads.emit(DownloadJob(id, JobState.Running, DownloadProgress(DownloadStage.Downloading, "Clip", 1, 1, 0.4)))
        val vm = viewModel()
        assertEquals(Status.Downloading("Clip", 1, 1, 0.4f), vm.status())

        vm.onPaste("https://example.com/v/2")
        assertTrue(downloads.started.isEmpty())

        downloads.emit(DownloadJob(id, JobState.Failed, result = DownloadResult.Failed(FailureReason.Unavailable)))
        assertEquals(Status.Failed(FailureReason.Unavailable), vm.status())
    }

    @Test
    fun `a running download seen at start can be cancelled`() {
        val id = UUID.randomUUID()
        downloads.emit(DownloadJob(id, JobState.Waiting))
        val vm = viewModel()
        assertEquals(Status.Preparing, vm.status())

        vm.onCancel()
        assertEquals(listOf(id), downloads.cancelled)
        assertEquals(Status.Idle, vm.status())
    }

    // B10

    @Test
    fun `opening the about sheet reads the yt-dlp version`() {
        downloads.version = CompletableDeferred()
        val vm = viewModel()
        vm.onOpenAbout()
        assertEquals(AboutState(open = true, versionLoading = true), vm.state.value.about)

        downloads.version.complete("2026.10.26")
        assertEquals(AboutState(open = true, engineVersion = "2026.10.26"), vm.state.value.about)

        vm.onCloseAbout()
        assertFalse(vm.state.value.about.open)
    }

    @Test
    fun `update yt-dlp says checking, then up to date`() {
        downloads.version = CompletableDeferred("2026.10.26")
        val vm = viewModel()
        vm.onOpenAbout()
        vm.onUpdateEngine()
        assertEquals(EngineCheck.Checking, vm.state.value.about.check)

        vm.onUpdateEngine()
        assertEquals(1, downloads.updates)

        downloads.update.complete(EngineUpdateResult.UpToDate("2026.10.26"))
        assertEquals(EngineCheck.UpToDate, vm.state.value.about.check)
        assertEquals("2026.10.26", vm.state.value.about.engineVersion)
    }

    @Test
    fun `update yt-dlp shows the new version`() {
        downloads.version = CompletableDeferred("2026.10.26")
        val vm = viewModel()
        vm.onOpenAbout()
        vm.onUpdateEngine()
        downloads.update.complete(EngineUpdateResult.Updated("2026.11.10"))

        assertEquals(EngineCheck.Updated("2026.11.10"), vm.state.value.about.check)
        assertEquals("2026.11.10", vm.state.value.about.engineVersion)
    }

    @Test
    fun `update yt-dlp offline says it could not check`() {
        downloads.version = CompletableDeferred("2026.10.26")
        val vm = viewModel()
        vm.onOpenAbout()
        vm.onUpdateEngine()
        downloads.update.complete(EngineUpdateResult.NotChecked)

        assertEquals(EngineCheck.NotChecked, vm.state.value.about.check)
        assertEquals("2026.10.26", vm.state.value.about.engineVersion)
    }

    // B11

    private val choices = listOf(
        VideoChoice(0, "First", "https://example.com/t/0.jpg", 8.0),
        VideoChoice(1, "Second", null, 12.5),
        VideoChoice(2, "Third", "https://example.com/t/2.jpg", null),
    )

    private fun chooseResult(link: String = "https://example.com/p/1") =
        DownloadResult.Choose(link, "Post title", "/cache/choices/x.json")

    @Test
    fun `a post with several videos opens the choice`() {
        downloads.choiceFiles["/cache/choices/x.json"] = ChoiceList("Post title", choices)
        val vm = viewModel()
        vm.onPaste("https://example.com/p/1")
        downloads.emit(DownloadJob(downloads.lastId, JobState.Succeeded, result = chooseResult()))

        assertEquals(Status.Choosing("Post title", choices), vm.status())
    }

    @Test
    fun `choosing queues the same link again with the chosen positions`() {
        downloads.choiceFiles["/cache/choices/x.json"] = ChoiceList("Post title", choices)
        val vm = viewModel()
        vm.onPaste("Look https://example.com/p/1")
        val first = downloads.lastId
        downloads.emit(DownloadJob(first, JobState.Succeeded, result = chooseResult()))

        vm.onChoose(listOf(2, 0))
        assertEquals(Start("https://example.com/p/1", listOf(0, 2)), downloads.started.last())
        assertEquals(Status.Preparing, vm.status())

        // The first job stays in WorkManager's list; its choice is not offered again.
        downloads.emit(
            DownloadJob(first, JobState.Succeeded, result = chooseResult()),
            DownloadJob(downloads.lastId, JobState.Running, DownloadProgress(DownloadStage.Downloading, "First", 1, 2, 0.1)),
        )
        assertEquals(Status.Downloading("First", 1, 2, 0.1f), vm.status())
    }

    @Test
    fun `choosing nothing starts nothing`() {
        downloads.choiceFiles["/cache/choices/x.json"] = ChoiceList("Post title", choices)
        val vm = viewModel()
        vm.onPaste("https://example.com/p/1")
        downloads.emit(DownloadJob(downloads.lastId, JobState.Succeeded, result = chooseResult()))

        vm.onChoose(emptyList())
        assertEquals(1, downloads.started.size)
        assertTrue(vm.status() is Status.Choosing)
    }

    @Test
    fun `dismissing the choice returns to idle and downloads nothing`() {
        downloads.choiceFiles["/cache/choices/x.json"] = ChoiceList("Post title", choices)
        val vm = viewModel()
        vm.onPaste("https://example.com/p/1")
        val id = downloads.lastId
        downloads.emit(DownloadJob(id, JobState.Succeeded, result = chooseResult()))

        vm.onDismissChoice()
        assertEquals(Status.Idle, vm.status())
        assertEquals(1, downloads.started.size)

        downloads.emit(DownloadJob(id, JobState.Succeeded, result = chooseResult()))
        assertEquals(Status.Idle, vm.status())
        vm.onChoose(listOf(0))
        assertEquals(1, downloads.started.size)
    }

    @Test
    fun `a choice whose file is gone fails kindly`() {
        val vm = viewModel()
        vm.onPaste("https://example.com/p/1")
        downloads.emit(DownloadJob(downloads.lastId, JobState.Succeeded, result = chooseResult()))

        assertEquals(Status.Failed(FailureReason.Unknown), vm.status())
    }

    @Test
    fun `the choose notification shows a choice this screen did not see run`() {
        downloads.choiceFiles["/cache/choices/x.json"] = ChoiceList("Post title", choices)
        val id = UUID.randomUUID()
        downloads.emit(DownloadJob(id, JobState.Succeeded, result = chooseResult("https://example.com/p/9")))
        val vm = viewModel()
        assertEquals(Status.Idle, vm.status())

        vm.onOpenChoice(id)
        assertEquals(Status.Choosing("Post title", choices), vm.status())

        vm.onChoose(listOf(1))
        assertEquals(Start("https://example.com/p/9", listOf(1)), downloads.started.single())
    }

    @Test
    fun `the choose notification waits for the job list`() {
        downloads.choiceFiles["/cache/choices/x.json"] = ChoiceList("Post title", choices)
        val vm = viewModel()
        val id = UUID.randomUUID()
        vm.onOpenChoice(id)
        assertEquals(Status.Idle, vm.status())

        downloads.emit(DownloadJob(id, JobState.Succeeded, result = chooseResult()))
        assertEquals(Status.Choosing("Post title", choices), vm.status())
    }

    @Test
    fun `a choice already handled is not shown again from the notification`() {
        downloads.choiceFiles["/cache/choices/x.json"] = ChoiceList("Post title", choices)
        val vm = viewModel()
        vm.onPaste("https://example.com/p/1")
        val id = downloads.lastId
        downloads.emit(DownloadJob(id, JobState.Succeeded, result = chooseResult()))
        vm.onDismissChoice()

        vm.onOpenChoice(id)
        assertEquals(Status.Idle, vm.status())
    }

    @Test
    fun `a running job is not adopted while the choice is open`() {
        downloads.choiceFiles["/cache/choices/x.json"] = ChoiceList("Post title", choices)
        val vm = viewModel()
        vm.onPaste("https://example.com/p/1")
        val id = downloads.lastId
        downloads.emit(
            DownloadJob(id, JobState.Succeeded, result = chooseResult()),
        )
        downloads.emit(
            DownloadJob(id, JobState.Succeeded, result = chooseResult()),
            DownloadJob(UUID.randomUUID(), JobState.Running),
        )
        assertTrue(vm.status() is Status.Choosing)
    }

    @Test
    fun `about starts closed with nothing loaded`() {
        val vm = viewModel()
        assertNull(vm.state.value.about.engineVersion)
        assertFalse(vm.state.value.about.open)
        assertEquals(0, downloads.versionReads)
    }

    private data class Start(val link: String, val selection: List<Int>?)

    private class FakeDownloads : Downloads {
        val jobsFlow = MutableStateFlow<List<DownloadJob>>(emptyList())
        val started = mutableListOf<Start>()
        val cancelled = mutableListOf<UUID>()
        val choiceFiles = mutableMapOf<String, ChoiceList>()
        var version: CompletableDeferred<String?> = CompletableDeferred("2026.10.26")
        var versionReads = 0
        val update = CompletableDeferred<EngineUpdateResult>()
        var updates = 0
        var lastId: UUID = UUID(0, 0)
            private set

        fun emit(vararg jobs: DownloadJob) {
            jobsFlow.value = jobs.toList()
        }

        override fun start(link: String, selection: List<Int>?): UUID {
            started += Start(link, selection)
            lastId = UUID(0, started.size.toLong())
            return lastId
        }

        override fun cancel(id: UUID) {
            cancelled += id
        }

        override fun jobs(): Flow<List<DownloadJob>> = jobsFlow

        override suspend fun readChoices(path: String): ChoiceList? = choiceFiles[path]

        override suspend fun updateEngine(): EngineUpdateResult {
            updates++
            return update.await()
        }

        override suspend fun engineVersion(): String? {
            versionReads++
            return version.await()
        }
    }
}
