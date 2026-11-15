package io.github.andyop21.linkdownloader.download

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileDescriptor
import java.nio.ByteBuffer

/**
 * Writes MP4 files with Android's own `MediaExtractor` and `MediaMuxer`, without re-encoding
 * (DEC-002): it joins a video-only and an audio-only file, or remuxes one file (MPEG-TS from HLS)
 * into MP4. The output goes straight to a file descriptor, normally the pending MediaStore entry,
 * so the file is written once. The video's rotation is kept.
 *
 * Both calls check for cancellation between samples and report progress from 0 to 1 by the
 * presentation time written so far.
 */
object MediaAssembler {

    /** Joins the first video track of [video] and the first audio track of [audio] into one MP4. */
    suspend fun join(video: File, audio: File, target: FileDescriptor, onProgress: (Double) -> Unit) =
        mux(listOf(Source(video, video = true, audio = false), Source(audio, video = false, audio = true)), target, onProgress)

    /** Copies the first video and the first audio track of [input] into an MP4. */
    suspend fun remux(input: File, target: FileDescriptor, onProgress: (Double) -> Unit) =
        mux(listOf(Source(input, video = true, audio = true)), target, onProgress)

    private class Source(val file: File, val video: Boolean, val audio: Boolean)

    /** One opened input: its extractor and, per selected source track, the muxer track it goes to. */
    private class Input(val extractor: MediaExtractor, val muxerTrack: Map<Int, Int>)

    private suspend fun mux(sources: List<Source>, target: FileDescriptor, onProgress: (Double) -> Unit) =
        withContext(Dispatchers.IO) {
            val extractors = mutableListOf<MediaExtractor>()
            val muxer = MediaMuxer(target, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var started = false
            try {
                var rotation = 0
                var durationUs = 0L
                var bufferSize = MIN_BUFFER
                val inputs = sources.map { source ->
                    val extractor = MediaExtractor().also { extractors += it }
                    extractor.setDataSource(source.file.absolutePath)
                    val tracks = mutableMapOf<Int, Int>()
                    var videoTaken = !source.video
                    var audioTaken = !source.audio
                    for (index in 0 until extractor.trackCount) {
                        val format = extractor.getTrackFormat(index)
                        val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                        val take = when {
                            !videoTaken && mime.startsWith("video/") -> true.also { videoTaken = true }
                            !audioTaken && mime.startsWith("audio/") -> true.also { audioTaken = true }
                            else -> false
                        }
                        if (!take) continue
                        if (mime.startsWith("video/") && format.containsKey(MediaFormat.KEY_ROTATION)) {
                            rotation = format.getInteger(MediaFormat.KEY_ROTATION)
                        }
                        if (format.containsKey(MediaFormat.KEY_DURATION)) {
                            durationUs = maxOf(durationUs, format.getLong(MediaFormat.KEY_DURATION))
                        }
                        if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                            bufferSize = maxOf(bufferSize, format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE))
                        }
                        extractor.selectTrack(index)
                        tracks[index] = muxer.addTrack(format)
                    }
                    check(videoTaken && audioTaken) {
                        "missing ${if (!videoTaken) "video" else "audio"} track in ${source.file.name}"
                    }
                    Input(extractor, tracks)
                }
                if (rotation != 0) muxer.setOrientationHint(rotation)
                muxer.start()
                started = true

                val buffer = ByteBuffer.allocateDirect(bufferSize)
                val info = MediaCodec.BufferInfo()
                var lastReported = -1.0
                while (true) {
                    ensureActive()
                    // Interleave by time: always write the input whose next sample comes first.
                    val input = inputs
                        .filter { it.extractor.sampleTrackIndex >= 0 }
                        .minByOrNull { it.extractor.sampleTime }
                        ?: break
                    val extractor = input.extractor
                    val muxerTrack = input.muxerTrack.getValue(extractor.sampleTrackIndex)
                    val size = extractor.readSampleData(buffer, 0)
                    val timeUs = extractor.sampleTime
                    if (size >= 0 && timeUs >= 0) {
                        info.set(0, size, timeUs, keyFrameFlag(extractor.sampleFlags))
                        muxer.writeSampleData(muxerTrack, buffer, info)
                        if (durationUs > 0) {
                            val progress = (timeUs.toDouble() / durationUs).coerceIn(0.0, 1.0)
                            if (progress - lastReported >= PROGRESS_STEP) {
                                lastReported = progress
                                onProgress(progress)
                            }
                        }
                    }
                    extractor.advance()
                }
                muxer.stop()
                started = false
                onProgress(1.0)
            } finally {
                if (started) runCatching { muxer.stop() }
                muxer.release()
                extractors.forEach { it.release() }
            }
        }

    private fun keyFrameFlag(sampleFlags: Int): Int =
        if (sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0

    private const val MIN_BUFFER = 4 * 1024 * 1024
    private const val PROGRESS_STEP = 0.01
}
