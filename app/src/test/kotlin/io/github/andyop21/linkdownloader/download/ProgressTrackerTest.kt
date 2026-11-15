package io.github.andyop21.linkdownloader.download

import io.github.andyop21.linkdownloader.download.ProgressTracker.Companion.ASSEMBLE_END
import io.github.andyop21.linkdownloader.download.ProgressTracker.Companion.FETCH_SHARE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressTrackerTest {

    @Test
    fun `a single file maps 0 to 100 percent onto the fetch share`() {
        val tracker = ProgressTracker(listOf(1000.0))
        tracker.onFetchLine("[download] Destination: /job/18.mp4")
        assertFraction(0.5 * FETCH_SHARE, tracker.onFetchLine("[download]  50.0% of   10.00MiB at    2.00MiB/s ETA 00:03"))
        assertFraction(FETCH_SHARE, tracker.onFetchLine("[download] 100% of   10.00MiB in 00:00:05 at 2.00MiB/s"))
    }

    @Test
    fun `a pair is weighted by file size and does not go back when the second file starts`() {
        val tracker = ProgressTracker(listOf(900.0, 100.0))
        tracker.onFetchLine("[download] Destination: /job/299.mp4")
        assertFraction(0.9 * FETCH_SHARE, tracker.onFetchLine("[download] 100.0% of 9.00MiB at 5MiB/s ETA 00:00"))
        // The second file restarts at 0 %; the overall fraction stays put.
        assertNull(tracker.onFetchLine("[download] Destination: /job/140.m4a"))
        assertNull(tracker.onFetchLine("[download]   0.0% of 1.00MiB at Unknown B/s ETA Unknown"))
        assertFraction((0.9 + 0.1 * 0.5) * FETCH_SHARE, tracker.onFetchLine("[download]  50.0% of 1.00MiB at 1MiB/s ETA 00:01"))
        assertFraction(FETCH_SHARE, tracker.onFetchLine("[download] 100% of 1.00MiB in 00:00:01"))
    }

    @Test
    fun `a pair with an unknown size is weighted evenly`() {
        val tracker = ProgressTracker(listOf(null, 3_000_000.0))
        tracker.onFetchLine("[download] Destination: /job/312.mp4")
        assertFraction(0.5 * FETCH_SHARE, tracker.onFetchLine("[download] 100.0% of ~ 20.00MiB at 3MiB/s ETA 00:00 (frag 40/40)"))
        tracker.onFetchLine("[download] Destination: /job/140.m4a")
        assertFraction(0.75 * FETCH_SHARE, tracker.onFetchLine("[download]  50.0% of 3.00MiB at 1MiB/s ETA 00:01"))
    }

    @Test
    fun `a destination line completes the previous file`() {
        val tracker = ProgressTracker(listOf(1.0, 1.0))
        tracker.onFetchLine("[download] Destination: /job/a.mp4")
        tracker.onFetchLine("[download]  80.0% of 1.00MiB at 1MiB/s ETA 00:01")
        assertFraction(0.5 * FETCH_SHARE, tracker.onFetchLine("[download] Destination: /job/b.m4a"))
    }

    @Test
    fun `the HLS opening fragment line is ignored`() {
        val tracker = ProgressTracker(listOf(null))
        tracker.onFetchLine("[download] Destination: /job/312.mp4")
        assertNull(tracker.onFetchLine("[download] 100.0% of ~   1.00KiB at  1.00KiB/s ETA Unknown (frag 0/38)"))
        assertEquals(0.0, tracker.fraction, 0.0)
        assertFraction(0.1 * FETCH_SHARE, tracker.onFetchLine("[download]  10.0% of ~ 15.00MiB at 2MiB/s ETA 00:10 (frag 4/38)"))
    }

    @Test
    fun `a falling estimate never moves the fraction back`() {
        val tracker = ProgressTracker(listOf(null))
        tracker.onFetchLine("[download] Destination: /job/312.mp4")
        tracker.onFetchLine("[download]  40.0% of ~ 10.00MiB at 2MiB/s ETA 00:03 (frag 10/38)")
        assertNull(tracker.onFetchLine("[download]  35.0% of ~ 12.00MiB at 2MiB/s ETA 00:04 (frag 11/38)"))
        assertEquals(0.4 * FETCH_SHARE, tracker.fraction, 1e-9)
    }

    @Test
    fun `lines that are not progress are ignored`() {
        val tracker = ProgressTracker(listOf(1.0))
        assertNull(tracker.onFetchLine("[info] 18: Downloading 1 format(s): 18"))
        assertNull(tracker.onFetchLine("WARNING: ffmpeg not found"))
        assertNull(tracker.onFetchLine(""))
        assertEquals(0.0, tracker.fraction, 0.0)
    }

    @Test
    fun `progress before any destination line counts for the first file`() {
        val tracker = ProgressTracker(listOf(1.0, 1.0))
        assertFraction(0.25 * FETCH_SHARE, tracker.onFetchLine("[download]  50.0% of 1.00MiB at 1MiB/s ETA 00:01"))
    }

    @Test
    fun `an already downloaded file counts as complete`() {
        val tracker = ProgressTracker(listOf(1.0, 1.0))
        assertFraction(0.5 * FETCH_SHARE, tracker.onFetchLine("[download] /job/a.mp4 has already been downloaded"))
    }

    @Test
    fun `more destination lines than files stay on the last file`() {
        val tracker = ProgressTracker(listOf(1.0))
        tracker.onFetchLine("[download] Destination: /job/a.mp4")
        assertFraction(FETCH_SHARE, tracker.onFetchLine("[download] Destination: /job/b.mp4"))
        assertNull(tracker.onFetchLine("[download] 100% of 1.00MiB"))
    }

    @Test
    fun `assemble takes the rest up to the assemble end and saving completes it`() {
        val tracker = ProgressTracker(listOf(1.0))
        tracker.onFetchLine("[download] 100% of 1.00MiB")
        assertFraction(FETCH_SHARE + (ASSEMBLE_END - FETCH_SHARE) / 2, tracker.onAssembleProgress(0.5))
        assertFraction(ASSEMBLE_END, tracker.onAssembleProgress(2.0))
        assertNull(tracker.onAssembleProgress(0.1))
        assertFraction(1.0, tracker.onSaved())
        assertNull(tracker.onSaved())
    }

    @Test
    fun `the fraction is never above one and never decreases across steps`() {
        val tracker = ProgressTracker(listOf(10.0, 20.0))
        val lines = listOf(
            "[download] Destination: /job/v.mp4",
            "[download]  30.0% of 1MiB at 1MiB/s ETA 00:01",
            "[download]  20.0% of 1MiB at 1MiB/s ETA 00:01",
            "[download] 100.0% of 1MiB at 1MiB/s ETA 00:00",
            "[download] Destination: /job/a.m4a",
            "[download]   5.0% of 2MiB at 1MiB/s ETA 00:02",
            "[download] 150.0% of 2MiB at 1MiB/s ETA 00:00",
        )
        var last = 0.0
        lines.forEach { line ->
            tracker.onFetchLine(line)
            assertTrue(tracker.fraction >= last)
            last = tracker.fraction
        }
        assertEquals(FETCH_SHARE, tracker.fraction, 1e-9)
        tracker.onAssembleProgress(1.0)
        tracker.onSaved()
        assertEquals(1.0, tracker.fraction, 0.0)
    }

    @Test
    fun `no files means no fetch progress`() {
        val tracker = ProgressTracker(emptyList())
        assertNull(tracker.onFetchLine("[download] 50.0% of 1MiB"))
    }

    private fun assertFraction(expected: Double, actual: Double?) {
        assertTrue("expected $expected, got null", actual != null)
        assertEquals(expected, actual!!, 1e-9)
    }
}
