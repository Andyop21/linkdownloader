package io.github.andyop21.linkdownloader.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileNamerTest {

    @Test
    fun `a plain title keeps its words and gets the extension`() {
        assertEquals("A calm walk by the sea.mp4", name("A calm walk by the sea"))
    }

    @Test
    fun `illegal characters are removed`() {
        assertEquals("Part 1 2 Intro what why.mp4", name("Part 1/2: Intro <what> | \"why\"?"))
        assertEquals("a b c.mp4", name("a\\b*c"))
    }

    @Test
    fun `control characters and whitespace runs collapse to one space`() {
        assertEquals("line one line two.mp4", name("line one\n\tline\u0000two"))
        assertEquals("wide gap.mp4", name("wide  　  gap"))
    }

    @Test
    fun `emoji and non-Latin letters are kept`() {
        assertEquals("Café 🎉 ねこ 🐱.mp4", name("Café 🎉 ねこ 🐱"))
    }

    @Test
    fun `leading and trailing dots and spaces are trimmed`() {
        assertEquals("hidden.mp4", name("  ..hidden..  "))
    }

    @Test
    fun `long titles are cut to 100 code points`() {
        val result = name("x".repeat(250))
        assertEquals("x".repeat(FileNamer.MAX_NAME_CODE_POINTS) + ".mp4", result)
    }

    @Test
    fun `the cut never splits an emoji and respects the byte limit`() {
        val result = name("🐱".repeat(120))
        val base = result.removeSuffix(".mp4")
        assertTrue(base.toByteArray(Charsets.UTF_8).size <= FileNamer.MAX_NAME_BYTES)
        assertEquals(FileNamer.MAX_NAME_BYTES / 4, base.codePointCount(0, base.length))
        assertTrue(base.all { it.isSurrogate() })
        assertEquals(base, String(base.toByteArray(Charsets.UTF_8), Charsets.UTF_8))
    }

    @Test
    fun `a cut does not leave a dangling joiner`() {
        val family = "👩‍👧"
        val title = "a".repeat(FileNamer.MAX_NAME_CODE_POINTS - 2) + family
        val base = name(title).removeSuffix(".mp4")
        assertTrue(!base.endsWith("‍"))
        assertEquals("a".repeat(FileNamer.MAX_NAME_CODE_POINTS - 2) + "👩", base)
    }

    @Test
    fun `an empty or useless title falls back to the site and the video id`() {
        assertEquals("youtube-abc123.mp4", FileNamer.name(null, "youtube", "abc123", "mp4"))
        assertEquals("youtube-abc123.mp4", FileNamer.name("", "youtube", "abc123", "mp4"))
        assertEquals("youtube-abc123.mp4", FileNamer.name(" /:*?<>| ", "youtube", "abc123", "mp4"))
        assertEquals("abc123.mp4", FileNamer.name("...", null, "abc123", "mp4"))
    }

    @Test
    fun `with nothing at all the name is never empty`() {
        assertEquals("${FileNamer.FALLBACK_NAME}.mp4", FileNamer.name(null, null, null, "mp4"))
        assertEquals("${FileNamer.FALLBACK_NAME}.mp4", FileNamer.name("??", " ", "", "mp4"))
    }

    @Test
    fun `the extension is used as given, with or without a dot`() {
        assertEquals("clip.webm", FileNamer.name("clip", null, null, "webm"))
        assertEquals("clip.mp4", FileNamer.name("clip", null, null, ".mp4"))
    }

    private fun name(title: String) = FileNamer.name(title, "site", "id", "mp4")
}
