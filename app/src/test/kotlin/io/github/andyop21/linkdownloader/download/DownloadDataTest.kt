package io.github.andyop21.linkdownloader.download

import androidx.work.Data
import androidx.work.OverwritingInputMerger
import io.github.andyop21.linkdownloader.download.DownloadData.clipped
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadDataTest {

    @Test
    fun `input carries the link and an optional selection`() {
        val plain = DownloadData.input("https://example.com/v/1")
        assertEquals("https://example.com/v/1", DownloadData.linkOf(plain))
        assertNull(DownloadData.selectionOf(plain))

        val chosen = DownloadData.input("https://example.com/p/2", listOf(0, 2))
        assertEquals(listOf(0, 2), DownloadData.selectionOf(chosen))
        assertNull(DownloadData.selectionOf(DownloadData.input("https://example.com/p/2", emptyList())))
    }

    /**
     * A job appended to the `engine` chain gets its input merged with the output of the job before
     * it, that output winning on equal keys (WorkManager's default [OverwritingInputMerger]). The
     * link and selection must survive every output a download or an update can leave behind.
     */
    @Test
    fun `input survives being merged with the previous job's output`() {
        val input = DownloadData.input("https://example.com/v/new", listOf(1))
        val previousOutputs = listOf(
            DownloadData.choose("https://example.com/p/old", "Old post", "/cache/choices/old.json"),
            DownloadData.saved(listOf(DownloadResult.Video("content://media/1", "Old.mp4", "Old")), failed = 0),
            DownloadData.failed(FailureReason.NotSupported),
            DownloadData.progress(DownloadProgress(DownloadStage.Downloading, "Old", 2, 2, 0.5)),
            UpdateWorker.output(EngineUpdateResult.Updated("2026.11.01")),
            UpdateWorker.output(EngineUpdateResult.NotChecked),
        )
        previousOutputs.forEach { previous ->
            val merged = OverwritingInputMerger().merge(listOf(input, previous))
            assertEquals("after $previous", "https://example.com/v/new", DownloadData.linkOf(merged))
            assertEquals("after $previous", listOf(1), DownloadData.selectionOf(merged))
        }
        val plain = OverwritingInputMerger().merge(listOf(DownloadData.input("https://example.com/v/new"), previousOutputs.first()))
        assertNull(DownloadData.selectionOf(plain))
    }

    @Test
    fun `a blank or missing link reads as none`() {
        assertNull(DownloadData.linkOf(Data.EMPTY))
        assertNull(DownloadData.linkOf(DownloadData.input("  ")))
    }

    @Test
    fun `progress round-trips, with an unknown fraction kept unknown`() {
        val early = DownloadProgress(DownloadStage.Preparing)
        assertEquals(early, DownloadData.progressOf(DownloadData.progress(early)))

        val late = DownloadProgress(DownloadStage.Saving, "Clip", 2, 3, 0.66)
        assertEquals(late, DownloadData.progressOf(DownloadData.progress(late)))
    }

    @Test
    fun `no progress published reads as null`() {
        assertNull(DownloadData.progressOf(Data.EMPTY))
    }

    @Test
    fun `long titles are clipped in progress`() {
        val progress = DownloadProgress(DownloadStage.Downloading, "a".repeat(5_000))
        val title = DownloadData.progressOf(DownloadData.progress(progress))!!.title!!
        assertEquals(DownloadData.MAX_TITLE_CHARS, title.length)
        assertTrue(title.endsWith("…"))
    }

    @Test
    fun `saved round-trips every video and the failed count`() {
        val videos = listOf(
            DownloadResult.Video("content://media/external/downloads/1", "Clip.mp4", "Clip"),
            DownloadResult.Video("content://media/external/downloads/2", "Clip (1).mp4", "Clip"),
        )
        val result = DownloadData.resultOf(DownloadData.saved(videos, failed = 1))
        assertEquals(DownloadResult.Saved(videos, savedCount = 2, failed = 1), result)
    }

    @Test
    fun `saved leaves out the last videos rather than exceed the 10 KB cap`() {
        val videos = (1..40).map { n ->
            DownloadResult.Video(
                "content://media/external_primary/downloads/10000000$n",
                "${"名".repeat(60)} $n.mp4",
                "${"題".repeat(190)} $n",
            )
        }
        val result = DownloadData.resultOf(DownloadData.saved(videos, failed = 0)) as DownloadResult.Saved
        assertEquals(40, result.savedCount)
        assertTrue("some videos kept: ${result.videos.size}", result.videos.isNotEmpty())
        assertTrue("some videos left out: ${result.videos.size}", result.videos.size < 40)
        assertEquals(videos.take(result.videos.size).map { it.uri }, result.videos.map { it.uri })
    }

    @Test
    fun `choose and failed round-trip`() {
        assertEquals(
            DownloadResult.Choose("https://example.com/p/1", "Post", "/cache/choices/x.json"),
            DownloadData.resultOf(DownloadData.choose("https://example.com/p/1", "Post", "/cache/choices/x.json")),
        )
        FailureReason.entries.forEach { reason ->
            assertEquals(DownloadResult.Failed(reason), DownloadData.resultOf(DownloadData.failed(reason)))
        }
    }

    @Test
    fun `an output without an outcome reads as null`() {
        assertNull(DownloadData.resultOf(Data.EMPTY))
    }

    @Test
    fun `clipping never splits a surrogate pair`() {
        val text = "ab" + "😀".repeat(10)
        val clipped = text.clipped(6)
        assertTrue(clipped.length <= 6)
        assertTrue(clipped.endsWith("…"))
        val body = clipped.removeSuffix("…")
        assertTrue("dangling surrogate in $body", !Character.isHighSurrogate(body.last()))
        assertEquals("short", "short".clipped(6))
    }
}
