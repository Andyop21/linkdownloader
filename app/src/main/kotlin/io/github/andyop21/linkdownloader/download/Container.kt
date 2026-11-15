package io.github.andyop21.linkdownloader.download

import java.io.File

/**
 * A media file's real container, read from its first bytes. Pure Kotlin.
 *
 * The extension is not trusted: HLS formats arrive as MPEG-TS in a file named `.mp4` (DEC-003).
 */
enum class Container {
    Mp4,
    MpegTs,
    WebM,
    Unknown,
    ;

    companion object {
        /** Bytes needed to tell the containers apart: two MPEG-TS packets of 188 bytes, plus one. */
        const val HEAD_SIZE = 2 * TS_PACKET + 1

        private val mp4Boxes = setOf("ftyp", "moov", "styp", "sidx", "free", "mdat")

        fun of(file: File): Container {
            val head = ByteArray(HEAD_SIZE)
            val read = file.inputStream().use { input ->
                var total = 0
                while (total < head.size) {
                    val count = input.read(head, total, head.size - total)
                    if (count < 0) break
                    total += count
                }
                total
            }
            return of(head, read)
        }

        /** The container of a file whose first [length] bytes are in [head]. */
        fun of(head: ByteArray, length: Int = head.size): Container {
            val size = minOf(length, head.size)
            if (size >= 8 && String(head, 4, 4, Charsets.ISO_8859_1) in mp4Boxes) return Mp4
            if (size > TS_PACKET && head[0] == TS_SYNC && head[TS_PACKET] == TS_SYNC) return MpegTs
            if (size >= 4 &&
                head[0] == 0x1A.toByte() &&
                head[1] == 0x45.toByte() &&
                head[2] == 0xDF.toByte() &&
                head[3] == 0xA3.toByte()
            ) {
                return WebM
            }
            return Unknown
        }
    }
}

private const val TS_PACKET = 188
private const val TS_SYNC = 0x47.toByte()
