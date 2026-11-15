package io.github.andyop21.linkdownloader.download

import org.junit.Assert.assertEquals
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
    fun `every site sample parses`() {
        listOf("youtube-bundled", "youtube-updated", "youtube-short", "instagram", "x", "tiktok").forEach { name ->
            val text = javaClass.getResource("/formats/$name.json")?.readText() ?: error("missing $name")
            assertTrue(name, VideoJson.parse(text).formats.isNotEmpty())
        }
    }
}
