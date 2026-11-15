package io.github.andyop21.linkdownloader.link

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinkParserTest {

    @Test
    fun `a bare link is returned as it is`() {
        assertEquals("https://example.com/watch?v=abc123", LinkParser.firstLink("https://example.com/watch?v=abc123"))
        assertEquals("http://example.com/v/1", LinkParser.firstLink("http://example.com/v/1"))
    }

    @Test
    fun `surrounding whitespace and line breaks are ignored`() {
        assertEquals("https://example.com/v/1", LinkParser.firstLink("  \n https://example.com/v/1 \n"))
    }

    @Test
    fun `share text with a title and a link gives the link`() {
        val text = "Check out this video! https://youtu.be/abc123?si=TrackingValue1 via the app"
        assertEquals("https://youtu.be/abc123?si=TrackingValue1", LinkParser.firstLink(text))
    }

    @Test
    fun `share text on several lines gives the link`() {
        val text = "A clip title with emoji 🎉\nhttps://www.instagram.com/reel/AbC-123_x/?igsh=abc123==\nShared from the app"
        assertEquals("https://www.instagram.com/reel/AbC-123_x/?igsh=abc123==", LinkParser.firstLink(text))
    }

    @Test
    fun `tracking parameters stay on the link`() {
        assertEquals(
            "https://x.com/someone/status/1234567890?s=20&t=AbCdEf",
            LinkParser.firstLink("https://x.com/someone/status/1234567890?s=20&t=AbCdEf"),
        )
        assertEquals(
            "https://www.example.com/v?utm_source=share&utm_medium=copy#t=30",
            LinkParser.firstLink("Look https://www.example.com/v?utm_source=share&utm_medium=copy#t=30"),
        )
    }

    @Test
    fun `of two links the first is used`() {
        val text = "first https://vm.tiktok.com/ZM123abc/ then https://example.com/other"
        assertEquals("https://vm.tiktok.com/ZM123abc/", LinkParser.firstLink(text))
    }

    @Test
    fun `text without a link gives null`() {
        assertNull(LinkParser.firstLink("just some words, no link here"))
        assertNull(LinkParser.firstLink(""))
        assertNull(LinkParser.firstLink("   "))
        assertNull(LinkParser.firstLink(null))
    }

    @Test
    fun `www without a scheme is not a link`() {
        assertNull(LinkParser.firstLink("www.example.com/v/1"))
        assertNull(LinkParser.firstLink("example.com/v/1"))
    }

    @Test
    fun `other schemes are not links`() {
        assertNull(LinkParser.firstLink("ftp://example.com/file.mp4"))
        assertNull(LinkParser.firstLink("mailto:someone@example.com"))
        assertNull(LinkParser.firstLink("file:///sdcard/video.mp4"))
    }

    @Test
    fun `a scheme glued to a word is not a link`() {
        assertNull(LinkParser.firstLink("xhttps://example.com/v/1"))
    }

    @Test
    fun `a scheme after punctuation is a link`() {
        assertEquals("https://example.com/v/1", LinkParser.firstLink("Video:https://example.com/v/1"))
    }

    @Test
    fun `a link without a host is rejected`() {
        assertNull(LinkParser.firstLink("https://"))
        assertNull(LinkParser.firstLink("https:///path"))
        assertNull(LinkParser.firstLink("https://./a"))
        assertNull(LinkParser.firstLink("https://?q=1"))
        assertNull(LinkParser.firstLink("https://-bad-.com/a"))
    }

    @Test
    fun `a link without a host is skipped for a later good one`() {
        assertEquals("https://example.com/v/1", LinkParser.firstLink("https:// broken, try https://example.com/v/1"))
    }

    @Test
    fun `trailing sentence punctuation is dropped`() {
        assertEquals("https://example.com/v/1", LinkParser.firstLink("Watch https://example.com/v/1."))
        assertEquals("https://example.com/v/1", LinkParser.firstLink("Watch https://example.com/v/1!!"))
        assertEquals("https://example.com/v/1", LinkParser.firstLink("Is it https://example.com/v/1?"))
        assertEquals("https://example.com/v/1", LinkParser.firstLink("https://example.com/v/1, and more"))
        assertEquals("https://example.com/v/1", LinkParser.firstLink("https://example.com/v/1…"))
        assertEquals("https://example.com/v/1", LinkParser.firstLink("链接 https://example.com/v/1。"))
    }

    @Test
    fun `closing brackets around the link are dropped`() {
        assertEquals("https://example.com/v/1", LinkParser.firstLink("(https://example.com/v/1)"))
        assertEquals("https://example.com/v/1", LinkParser.firstLink("[https://example.com/v/1]."))
        assertEquals("https://example.com/v/1", LinkParser.firstLink("see (https://example.com/v/1)."))
    }

    @Test
    fun `brackets that belong to the link are kept`() {
        assertEquals(
            "https://en.example.org/wiki/Clip_(film)",
            LinkParser.firstLink("(see https://en.example.org/wiki/Clip_(film))"),
        )
    }

    @Test
    fun `quotes end the link`() {
        assertEquals("https://example.com/v/1", LinkParser.firstLink("\"https://example.com/v/1\""))
        assertEquals("https://example.com/v/1", LinkParser.firstLink("“https://example.com/v/1”"))
        assertEquals("https://example.com/v/1", LinkParser.firstLink("'https://example.com/v/1'"))
        assertEquals("https://example.com/v/1", LinkParser.firstLink("<https://example.com/v/1>"))
    }

    @Test
    fun `an upper-case scheme is lowered and the rest kept`() {
        assertEquals("https://EXAMPLE.com/V/1", LinkParser.firstLink("HTTPS://EXAMPLE.com/V/1"))
    }

    @Test
    fun `ports, user info and IP hosts are accepted`() {
        assertEquals("https://example.com:8443/v/1", LinkParser.firstLink("https://example.com:8443/v/1"))
        assertEquals("https://user@example.com/v/1", LinkParser.firstLink("https://user@example.com/v/1"))
        assertEquals("http://192.168.0.10/v.mp4", LinkParser.firstLink("http://192.168.0.10/v.mp4"))
        assertEquals("http://[2001:db8::1]:8080/v", LinkParser.firstLink("http://[2001:db8::1]:8080/v"))
        assertNull(LinkParser.firstLink("https://example.com:port/v"))
    }

    @Test
    fun `a host with non-ASCII letters is accepted`() {
        assertEquals("https://bücher.example/v/1", LinkParser.firstLink("https://bücher.example/v/1"))
    }

    @Test
    fun `a no-break space ends the link`() {
        assertEquals("https://example.com/v/1", LinkParser.firstLink("https://example.com/v/1 more"))
    }
}
