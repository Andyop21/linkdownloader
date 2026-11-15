package io.github.andyop21.linkdownloader.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatPickerTest {

    // Sites, from the trimmed samples in src/test/resources/formats

    @Test
    fun `youtube with DASH only picks the 1080p H264 HTTPS video and the AAC audio`() {
        assertPair("299", "140", FormatPicker.pick(sample("youtube-bundled.json")))
    }

    @Test
    fun `youtube with HLS formats still prefers the HTTPS pair`() {
        assertPair("299", "140", FormatPicker.pick(sample("youtube-updated.json")))
    }

    @Test
    fun `youtube with only HLS video left pairs the best HLS H264 video`() {
        val video = sample("youtube-updated.json")
        val withoutHttpsVideo = video.copy(
            formats = video.formats.filterNot { it.protocol == "https" && it.acodec == "none" },
        )
        assertPair("312", "140", FormatPicker.pick(withoutHttpsVideo))
    }

    @Test
    fun `youtube short picks the vertical 1080x1920 video and the original-language audio`() {
        assertPair("137", "140-11", FormatPicker.pick(sample("youtube-short.json")))
    }

    @Test
    fun `instagram picks the last progressive MP4 although its codecs and size are unknown`() {
        assertSingle("3", FormatPicker.pick(sample("instagram.json")))
    }

    @Test
    fun `x picks the best progressive MP4 within the cap since the HLS audio codec is unknown`() {
        assertSingle("http-10368", FormatPicker.pick(sample("x.json")))
    }

    @Test
    fun `tiktok picks the HEVC 1080p format and the later CDN on a tie`() {
        assertSingle("bytevc1_1080p_802345-1", FormatPicker.pick(sample("tiktok.json")))
    }

    // Pair rules

    @Test
    fun `pair video ranks HTTPS above a higher HLS resolution`() {
        val plan = FormatPicker.pick(
            video(
                videoOnly("hls-1080", "avc1.640028", 1920, 1080, protocol = "m3u8_native"),
                videoOnly("https-720", "avc1.4d401f", 1280, 720),
                aac("a"),
            ),
        )
        assertPair("https-720", "a", plan)
    }

    @Test
    fun `pair video takes HEVC and skips VP9 and AV1`() {
        val plan = FormatPicker.pick(
            video(
                videoOnly("hevc", "hvc1.1.6.L120", 1920, 1080, tbr = 100.0),
                videoOnly("vp9", "vp09.00.40.08", 1920, 1080, tbr = 300.0),
                videoOnly("av1", "av01.0.08M.08", 1920, 1080, tbr = 200.0),
                aac("a"),
            ),
        )
        assertPair("hevc", "a", plan)
    }

    @Test
    fun `pair video above 1080 on the short side is never picked`() {
        val plan = FormatPicker.pick(
            video(
                videoOnly("1440", "avc1.640032", 2560, 1440, tbr = 9000.0),
                videoOnly("720", "avc1.4d401f", 1280, 720, tbr = 1000.0),
                aac("a"),
            ),
        )
        assertPair("720", "a", plan)
    }

    @Test
    fun `pair video tie goes to the later entry`() {
        val plan = FormatPicker.pick(
            video(
                videoOnly("first", "avc1.640028", 1920, 1080, tbr = 2000.0),
                videoOnly("second", "avc1.640028", 1920, 1080, tbr = 2000.0),
                aac("a"),
            ),
        )
        assertPair("second", "a", plan)
    }

    @Test
    fun `audio prefers non-DRC over DRC in the same language`() {
        val plan = FormatPicker.pick(
            video(
                videoOnly("v", "avc1.640028", 1920, 1080),
                aac("140-drc", tbr = 140.0, language = 10.0),
                aac("140", tbr = 129.0, language = 10.0),
            ),
        )
        assertPair("v", "140", plan)
    }

    @Test
    fun `audio prefers HTTPS over HLS, then the higher bitrate`() {
        val plan = FormatPicker.pick(
            video(
                videoOnly("v", "avc1.640028", 1920, 1080),
                aac("hls-high", tbr = 256.0, protocol = "m3u8_native"),
                aac("https-low", tbr = 64.0),
                aac("https-mid", tbr = 128.0),
            ),
        )
        assertPair("v", "https-mid", plan)
    }

    @Test
    fun `audio accepts TikTok's plain aac codec name`() {
        val plan = FormatPicker.pick(video(videoOnly("v", "h264", 1080, 1920), aac("a", codec = "aac")))
        assertPair("v", "a", plan)
    }

    @Test
    fun `opus audio forms no pair and the single is used`() {
        val plan = FormatPicker.pick(
            video(
                videoOnly("v", "avc1.640028", 1920, 1080),
                audioOnly("opus", "opus"),
                both("18", "mp4", "avc1.42001E", "mp4a.40.2", 640, 360),
            ),
        )
        assertSingle("18", plan)
    }

    // Single rules

    @Test
    fun `single above the cap falls back to the next size`() {
        val plan = FormatPicker.pick(
            video(
                both("4k", "mp4", null, null, 2160, 3840),
                both("1080", "mp4", null, null, 1080, 1920),
            ),
        )
        assertSingle("1080", plan)
    }

    @Test
    fun `single with an unknown size ranks below a known one`() {
        val plan = FormatPicker.pick(
            video(
                both("known", "mp4", "h264", "aac", 576, 1024),
                both("unknown", "mp4", "h264", "aac", null, null),
            ),
        )
        assertSingle("known", plan)
    }

    @Test
    fun `single MP4 is preferred over a WebM of the same size`() {
        val plan = FormatPicker.pick(
            video(
                both("mp4", "mp4", "avc1.42001E", "mp4a.40.2", 1280, 720),
                both("webm", "webm", "vp9", "opus", 1280, 720, tbr = 5000.0),
            ),
        )
        assertSingle("mp4", plan)
    }

    @Test
    fun `single WebM is kept when the site offers nothing else`() {
        val plan = FormatPicker.pick(video(both("webm", "webm", "vp9", "opus", 1280, 720)))
        assertSingle("webm", plan)
    }

    @Test
    fun `single HLS MP4 with H264 and AAC is accepted for a remux`() {
        val plan = FormatPicker.pick(
            video(both("hls-720", "mp4", "avc1.64001f", "mp4a.40.2", 1280, 720, protocol = "m3u8_native")),
        )
        assertSingle("hls-720", plan)
    }

    @Test
    fun `single MP4 with a codec MediaMuxer cannot write is rejected`() {
        assertNotSupported(video(both("vp9mp4", "mp4", "vp09.00.40.08", "mp4a.40.2", 1280, 720)))
        assertNotSupported(video(both("opusmp4", "mp4", "avc1.64001f", "opus", 1280, 720)))
    }

    @Test
    fun `single with another extension is rejected`() {
        assertNotSupported(video(both("flv", "flv", "h264", "aac", 640, 360)))
    }

    // Nothing usable

    @Test
    fun `no formats fails as not supported`() {
        assertNotSupported(VideoJson(id = "x", formats = emptyList()))
    }

    @Test
    fun `VP9 and Opus only fails as not supported`() {
        assertNotSupported(video(videoOnly("v", "vp09.00.40.08", 1920, 1080), audioOnly("a", "opus")))
    }

    @Test
    fun `everything above the cap fails as not supported`() {
        assertNotSupported(
            video(
                videoOnly("v", "avc1.640033", 3840, 2160),
                aac("a"),
                both("s", "mp4", "avc1.640033", "mp4a.40.2", 3840, 2160),
            ),
        )
    }

    @Test
    fun `formats without an ID or with a protocol that needs ffmpeg are ignored`() {
        assertNotSupported(
            video(
                both("", "mp4", "avc1.42001E", "mp4a.40.2", 640, 360),
                both("dash", "mp4", "avc1.42001E", "mp4a.40.2", 640, 360, protocol = "f4m"),
            ),
        )
    }

    @Test
    fun `audio-only and video-only formats are never picked as a single`() {
        assertNotSupported(video(videoOnly("v", "avc1.640028", 1920, 1080)))
        assertNotSupported(video(aac("a")))
    }

    // Helpers

    private fun assertPair(videoId: String, audioId: String, plan: Plan) {
        assertTrue("expected a pair, got $plan", plan is Plan.Pair)
        plan as Plan.Pair
        assertEquals(videoId, plan.video.formatId)
        assertEquals(audioId, plan.audio.formatId)
        assertEquals(listOf(videoId, audioId), plan.formats.map { it.formatId })
    }

    private fun assertSingle(id: String, plan: Plan) {
        assertTrue("expected a single format, got $plan", plan is Plan.Single)
        assertEquals(id, (plan as Plan.Single).format.formatId)
    }

    private fun assertNotSupported(video: VideoJson) {
        val error = assertThrows(DownloadException::class.java) { FormatPicker.pick(video) }
        assertEquals(FailureReason.NotSupported, error.reason)
    }

    private fun sample(name: String): VideoJson {
        val text = javaClass.getResource("/formats/$name")?.readText() ?: error("missing sample $name")
        return VideoJson.parse(text)
    }

    private fun video(vararg formats: FormatJson) = VideoJson(id = "test", title = "Test", extractor = "test", formats = formats.toList())

    private fun videoOnly(id: String, codec: String, width: Int, height: Int, tbr: Double? = null, protocol: String = "https") =
        FormatJson(
            formatId = id,
            ext = "mp4",
            protocol = protocol,
            vcodec = codec,
            acodec = "none",
            width = width.toDouble(),
            height = height.toDouble(),
            tbr = tbr,
        )

    private fun aac(
        id: String,
        tbr: Double? = 128.0,
        language: Double? = null,
        protocol: String = "https",
        codec: String = "mp4a.40.2",
    ) = FormatJson(
        formatId = id,
        ext = "m4a",
        protocol = protocol,
        vcodec = "none",
        acodec = codec,
        tbr = tbr,
        languagePreference = language,
    )

    private fun audioOnly(id: String, codec: String) =
        FormatJson(formatId = id, ext = "webm", protocol = "https", vcodec = "none", acodec = codec, tbr = 160.0)

    private fun both(
        id: String,
        ext: String,
        vcodec: String?,
        acodec: String?,
        width: Int?,
        height: Int?,
        tbr: Double? = null,
        protocol: String = "https",
    ) = FormatJson(
        formatId = id,
        ext = ext,
        protocol = protocol,
        vcodec = vcodec,
        acodec = acodec,
        width = width?.toDouble(),
        height = height?.toDouble(),
        tbr = tbr,
    )
}
