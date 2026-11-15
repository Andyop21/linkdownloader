package io.github.andyop21.linkdownloader.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadProgressTest {

    private fun run(vararg events: DownloadEvent): DownloadProgress =
        events.fold(DownloadProgress()) { state, event -> state.after(event) }

    @Test
    fun `starts preparing with nothing known`() {
        val state = run(DownloadEvent.Preparing)
        assertEquals(DownloadStage.Preparing, state.stage)
        assertNull(state.title)
        assertNull(state.fraction)
        assertEquals(1, state.position)
        assertEquals(1, state.count)
    }

    @Test
    fun `a known title starts downloading, before any progress`() {
        val state = run(DownloadEvent.Preparing, DownloadEvent.TitleKnown("Cat", 1, 1))
        assertEquals(DownloadStage.Downloading, state.stage)
        assertEquals("Cat", state.title)
        assertNull(state.fraction)
    }

    @Test
    fun `progress sets the fraction and saving the stage`() {
        val state = run(
            DownloadEvent.Preparing,
            DownloadEvent.TitleKnown("Cat"),
            DownloadEvent.Progress(0.4),
            DownloadEvent.Saving,
            DownloadEvent.Progress(0.97),
        )
        assertEquals(DownloadStage.Saving, state.stage)
        assertEquals(0.97, state.fraction!!, 0.0)
    }

    @Test
    fun `the fraction never goes backwards and stays within 0 and 1`() {
        assertEquals(0.5, run(DownloadEvent.Progress(0.5), DownloadEvent.Progress(0.2)).fraction!!, 0.0)
        assertEquals(1.0, run(DownloadEvent.Progress(1.4)).fraction!!, 0.0)
        assertEquals(0.0, run(DownloadEvent.Progress(-0.3)).fraction!!, 0.0)
    }

    @Test
    fun `progress without a title still counts as downloading`() {
        assertEquals(DownloadStage.Downloading, run(DownloadEvent.Preparing, DownloadEvent.Progress(0.1)).stage)
    }

    @Test
    fun `the next video of a post goes back to downloading and keeps the fraction`() {
        val state = run(
            DownloadEvent.Preparing,
            DownloadEvent.TitleKnown("Clip", 1, 3),
            DownloadEvent.Progress(0.3),
            DownloadEvent.Saving,
            DownloadEvent.Progress(0.33),
            DownloadEvent.TitleKnown("Clip", 2, 3),
        )
        assertEquals(DownloadStage.Downloading, state.stage)
        assertEquals(2, state.position)
        assertEquals(3, state.count)
        assertEquals(0.33, state.fraction!!, 0.0)
    }
}
