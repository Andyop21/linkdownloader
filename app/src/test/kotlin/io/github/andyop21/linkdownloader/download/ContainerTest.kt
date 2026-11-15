package io.github.andyop21.linkdownloader.download

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class ContainerTest {

    @Test
    fun `an ftyp box is MP4`() {
        assertEquals(Container.Mp4, Container.of(bytes(0, 0, 0, 0x20) + "ftypisom".toByteArray()))
    }

    @Test
    fun `fragmented MP4 openings are MP4`() {
        assertEquals(Container.Mp4, Container.of(bytes(0, 0, 0, 0x18) + "styp".toByteArray()))
        assertEquals(Container.Mp4, Container.of(bytes(0, 0, 0, 0x18) + "sidx".toByteArray()))
    }

    @Test
    fun `two sync bytes 188 apart are MPEG-TS`() {
        val head = ByteArray(Container.HEAD_SIZE)
        head[0] = 0x47
        head[188] = 0x47
        assertEquals(Container.MpegTs, Container.of(head))
    }

    @Test
    fun `one sync byte alone is not MPEG-TS`() {
        val head = ByteArray(Container.HEAD_SIZE)
        head[0] = 0x47
        assertEquals(Container.Unknown, Container.of(head))
        head[188] = 0x47
        assertEquals(Container.Unknown, Container.of(head, length = 100))
    }

    @Test
    fun `the EBML header is WebM`() {
        assertEquals(Container.WebM, Container.of(bytes(0x1A, 0x45, 0xDF, 0xA3, 0x9F, 0x42, 0x86, 0x81)))
    }

    @Test
    fun `short or unknown heads are unknown`() {
        assertEquals(Container.Unknown, Container.of(ByteArray(0)))
        assertEquals(Container.Unknown, Container.of("ID3\u0004abcdefg".toByteArray()))
    }

    @Test
    fun `a file is read by its first bytes, not its extension`() {
        val file = File.createTempFile("hls", ".mp4")
        try {
            val content = ByteArray(1000)
            for (offset in 0 until 1000 step 188) content[offset] = 0x47
            file.writeBytes(content)
            assertEquals(Container.MpegTs, Container.of(file))
        } finally {
            file.delete()
        }
    }

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }
}
