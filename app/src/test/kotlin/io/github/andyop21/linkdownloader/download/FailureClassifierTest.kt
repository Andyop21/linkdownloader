package io.github.andyop21.linkdownloader.download

import io.github.andyop21.linkdownloader.download.FailureReason.NeedsLogin
import io.github.andyop21.linkdownloader.download.FailureReason.NoInternet
import io.github.andyop21.linkdownloader.download.FailureReason.NotSupported
import io.github.andyop21.linkdownloader.download.FailureReason.Unavailable
import io.github.andyop21.linkdownloader.download.FailureReason.Unknown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class FailureClassifierTest {

    // Messages seen in P02 (DEC-003)

    @Test
    fun `instagram login wall needs login`() {
        assertReason(NeedsLogin, "ERROR: [Instagram] SAMPLE: Main webpage is locked behind the login page. Use --cookies-from-browser or --cookies for the authentication.")
        assertReason(NeedsLogin, "ERROR: [Instagram] SAMPLE: Requested content is not available, rate-limit reached or login required. Use --cookies-from-browser or --cookies for the authentication.")
    }

    @Test
    fun `instagram empty media response needs login`() {
        assertReason(NeedsLogin, "ERROR: [Instagram] SAMPLE: Instagram sent an empty media response. Check if this post is accessible in your browser without being logged-in.")
    }

    @Test
    fun `youtube 403 is unknown and marked forbidden`() {
        val text = "ERROR: unable to download video data: HTTP Error 403: Forbidden"
        assertReason(Unknown, text)
        assertTrue(FailureClassifier.isForbidden(text))
    }

    @Test
    fun `the fixup crash of the bundled yt-dlp is unknown`() {
        assertReason(Unknown, "Traceback (most recent call last):\n  File \"yt_dlp/postprocessor/ffmpeg.py\"\nAttributeError: 'NoneType' object has no attribute 'lower'")
    }

    @Test
    fun `warnings do not decide the reason when there is an ERROR line`() {
        val text = """
            WARNING: ffmpeg-location /data/app/lib/libffmpeg.so does not exist! Continuing without ffmpeg
            WARNING: You are using an outdated version of yt-dlp (older than 90 days); not found newer
            ERROR: [generic] Unsupported URL: https://example.com/page
        """.trimIndent()
        assertReason(NotSupported, text)
    }

    // yt-dlp's usual messages

    @Test
    fun `unsupported links are not supported`() {
        assertReason(NotSupported, "ERROR: Unsupported URL: https://example.com/")
        assertReason(NotSupported, "ERROR: 'not a link' is not a valid URL. Set --default-search \"ytsearch\"")
        assertReason(NotSupported, "ERROR: [example] 123: No video formats found!")
        assertReason(NotSupported, "ERROR: [Reddit] abc: There's no video in this post")
    }

    @Test
    fun `private, removed and locked videos are unavailable`() {
        assertReason(Unavailable, "ERROR: [youtube] abc: Private video. Sign in if you've been granted access to this video")
        assertReason(Unavailable, "ERROR: [youtube] abc: Video unavailable")
        assertReason(Unavailable, "ERROR: [youtube] abc: Video unavailable. This video has been removed by the uploader")
        assertReason(Unavailable, "ERROR: [youtube] abc: Sign in to confirm your age. This video may be inappropriate for some users. Use --cookies-from-browser or --cookies for the authentication.")
        assertReason(Unavailable, "ERROR: [youtube] abc: The uploader has not made this video available in your country. This video is not available in your country")
        assertReason(Unavailable, "ERROR: [TikTok] 123: This post is no longer available")
        assertReason(Unavailable, "ERROR: unable to download webpage: HTTP Error 404: Not Found")
    }

    @Test
    fun `bot checks and login demands need login`() {
        assertReason(NeedsLogin, "ERROR: [youtube] abc: Sign in to confirm you’re not a bot. Use --cookies-from-browser or --cookies for the authentication.")
        assertReason(NeedsLogin, "ERROR: [twitter] 123: login required")
        assertReason(NeedsLogin, "ERROR: [vimeo] 123: This video is only available for logged in users")
    }

    @Test
    fun `name resolution and connection errors are no internet`() {
        assertReason(NoInternet, "ERROR: [youtube] abc: Unable to download API page: <urlopen error [Errno 7] No address associated with hostname> (caused by URLError)")
        assertReason(NoInternet, "ERROR: Failed to resolve 'www.youtube.com' ([Errno 7] No address associated with hostname)")
        assertReason(NoInternet, "ERROR: [Errno -3] Temporary failure in name resolution")
        assertReason(NoInternet, "ERROR: <urlopen error [Errno 101] Network is unreachable>")
        assertReason(NoInternet, "ERROR: [Errno 111] Connection refused")
        assertReason(NoInternet, "ERROR: Read timed out. (read timeout=20.0)")
        assertReason(NoInternet, "ERROR: ('Connection aborted.', RemoteDisconnected('Remote end closed connection without response'))")
    }

    @Test
    fun `text without ERROR lines is read whole`() {
        assertReason(NoInternet, "Unable to resolve host \"www.youtube.com\": No address associated with hostname")
    }

    @Test
    fun `empty or unknown text is unknown`() {
        assertReason(Unknown, null)
        assertReason(Unknown, "")
        assertReason(Unknown, "   \n ")
        assertReason(Unknown, "ERROR: something nobody expected")
    }

    @Test
    fun `403 is forbidden only in an error line`() {
        assertFalse(FailureClassifier.isForbidden("ERROR: Video unavailable"))
        assertFalse(FailureClassifier.isForbidden(null))
        assertFalse(FailureClassifier.isForbidden("WARNING: HTTP Error 403 on one fragment\nERROR: Video unavailable"))
    }

    // Exceptions

    @Test
    fun `network exceptions in the cause chain are no internet`() {
        assertEquals(NoInternet, FailureClassifier.classify(UnknownHostException("www.youtube.com")))
        assertEquals(NoInternet, FailureClassifier.classify(IOException("wrapped", ConnectException("refused"))))
        assertEquals(NoInternet, FailureClassifier.classify(IllegalStateException("x", SocketTimeoutException())))
    }

    @Test
    fun `a download exception keeps its reason`() {
        assertEquals(NotSupported, FailureClassifier.classify(DownloadException(NotSupported, "nothing to pick")))
    }

    @Test
    fun `exception messages are classified along the chain`() {
        val error = RuntimeException("yt-dlp failed", Exception("ERROR: [youtube] abc: Private video"))
        assertEquals(Unavailable, FailureClassifier.classify(error))
        assertEquals(Unknown, FailureClassifier.classify(IllegalStateException("muxer failed")))
    }

    private fun assertReason(expected: FailureReason, text: String?) {
        assertEquals(text, expected, FailureClassifier.classify(text))
    }
}
