package io.github.andyop21.linkdownloader.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoJsonTest {

    @Test
    fun `parses the used fields and ignores the rest`() {
        val video = VideoJson.parse(
            """
            {"id": "abc", "title": "A title", "extractor": "youtube", "duration": 12.5,
             "thumbnails": [{"url": "x"}], "requested_formats": null,
             "formats": [{"format_id": "137", "ext": "mp4", "protocol": "https", "vcodec": "avc1.640028",
                          "acodec": "none", "width": 1920, "height": 1080, "tbr": 2610.3, "filesize": 61500000,
                          "filesize_approx": null, "language_preference": -1, "fps": 30, "url": "x"}]}
            """.trimIndent(),
        )
        assertEquals("abc", video.id)
        assertEquals("A title", video.title)
        assertEquals("youtube", video.extractor)
        val format = video.formats.single()
        assertEquals("137", format.formatId)
        assertEquals(1080.0, format.height!!, 0.0)
        assertEquals(61_500_000.0, format.knownSize!!, 0.0)
        assertEquals(-1.0, format.languagePreference!!, 0.0)
        assertNull(format.filesizeApprox)
    }

    @Test
    fun `null and missing values read as defaults`() {
        val video = VideoJson.parse("""{"title": null, "formats": null}""")
        assertNull(video.id)
        assertNull(video.title)
        assertTrue(video.formats.isEmpty())
    }

    @Test
    fun `a playlist without formats parses to an empty format list`() {
        val video = VideoJson.parse("""{"_type": "playlist", "id": "p", "entries": [{"id": "a"}]}""")
        assertTrue(video.formats.isEmpty())
    }

    @Test
    fun `known size falls back to the approximate size and ignores zero`() {
        assertEquals(5.0, FormatJson(filesize = null, filesizeApprox = 5.0).knownSize!!, 0.0)
        assertNull(FormatJson(filesize = 0.0).knownSize)
        assertNull(FormatJson().knownSize)
    }

    @Test
    fun `a single video is not a playlist`() {
        val video = sample("x.json")
        assertFalse(video.isPlaylist)
        assertTrue(video.videoEntries.isEmpty())
    }

    @Test
    fun `a post with several videos lists its video entries with their positions`() {
        val post = sample("x-post-two-videos.json")
        assertTrue(post.isPlaylist)
        assertEquals(3, post.entries.size)
        val entries = post.videoEntries
        assertEquals(listOf(0, 2), entries.map { it.index })
        assertEquals(listOf("samplexpost-1", "samplexpost-2"), entries.map { it.value.id })
        assertEquals("https://thumbs.example.invalid/clip1.jpg", entries[0].value.thumbnail)
        assertEquals(30.0, entries[1].value.duration!!, 0.0)
    }

    @Test
    fun `each entry of a post can be picked on its own`() {
        val post = sample("x-post-two-videos.json")
        post.videoEntries.forEach { (_, entry) ->
            val plan = FormatPicker.pick(entry)
            assertEquals("http-2176", (plan as Plan.Single).format.formatId)
        }
    }

    @Test
    fun `a flat playlist has no video entries`() {
        val playlist = sample("youtube-playlist-flat.json")
        assertTrue(playlist.isPlaylist)
        assertEquals(3, playlist.entries.size)
        assertTrue(playlist.videoEntries.isEmpty())
    }

    @Test
    fun `an entry is written back out as its own JSON`() {
        val text = javaClass.getResource("/formats/x-post-two-videos.json")?.readText() ?: error("missing sample")
        val (post, tree) = VideoJson.parseTree(text)
        val rawIndex = post.videoEntries[1].index
        val entry = VideoJson.parse(VideoJson.entryText(tree, rawIndex))
        assertEquals("samplexpost-2", entry.id)
        assertEquals(2, entry.formats.size)
        assertFalse(entry.isPlaylist)
    }

    @Test
    fun `multi_video results count as playlists`() {
        assertTrue(VideoJson.parse("""{"_type": "multi_video", "entries": []}""").isPlaylist)
    }

    @Test
    fun `every site sample parses`() {
        listOf("youtube-bundled", "youtube-updated", "youtube-short", "instagram", "x", "tiktok").forEach { name ->
            val text = javaClass.getResource("/formats/$name.json")?.readText() ?: error("missing $name")
            assertTrue(name, VideoJson.parse(text).formats.isNotEmpty())
        }
    }

    private fun sample(name: String): VideoJson {
        val text = javaClass.getResource("/formats/$name")?.readText() ?: error("missing sample $name")
        return VideoJson.parse(text)
    }
}
