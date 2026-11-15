package io.github.andyop21.linkdownloader.download

/**
 * Turns yt-dlp's per-file output into one overall fraction from 0 to 1 that never goes backwards.
 * Pure Kotlin.
 *
 * - **Fetch** takes 0 to [FETCH_SHARE]. yt-dlp downloads the files of a pair one after the other,
 *   each announced by a `[download] Destination:` line and each with its own 0–100 %. A file's
 *   share is its size (`filesize`, else `filesize_approx`) when every file's size is known, else an
 *   equal part (HLS formats state no size).
 * - **Assemble and save** take the rest, up to [ASSEMBLE_END]; [onSaved] completes it.
 *
 * Percentages are read from the lines themselves. youtubedl-android's callback repeats the last
 * percentage on lines that carry none, so after a `Destination:` line it would still report the
 * previous file's 100 %. HLS's opening `100.0% of ~1.00KiB … (frag 0/N)` line is ignored too.
 *
 * Each `on…` call returns the new overall fraction when it moved forward, else `null`.
 */
class ProgressTracker(fileSizes: List<Double?>) {

    private val weights: List<Double> = weightsFor(fileSizes)
    private val filePercent = DoubleArray(weights.size)
    private var fileIndex = -1

    /** The overall fraction so far, from 0 to 1. */
    var fraction: Double = 0.0
        private set

    /** One line of yt-dlp's output during the fetch. */
    fun onFetchLine(line: String): Double? {
        if (weights.isEmpty()) return null
        val text = line.trim()
        when {
            text.startsWith(DESTINATION) -> startNextFile()
            text.startsWith(DOWNLOAD) && text.endsWith(ALREADY_DOWNLOADED) -> {
                startNextFile()
                filePercent[fileIndex] = 100.0
            }
            else -> {
                val percent = percentOf(text) ?: return null
                if (fileIndex < 0) fileIndex = 0
                filePercent[fileIndex] = maxOf(filePercent[fileIndex], percent.coerceIn(0.0, 100.0))
            }
        }
        return advanceTo(FETCH_SHARE * weights.indices.sumOf { weights[it] * filePercent[it] / 100.0 })
    }

    /** Progress of assembling and writing the file, from 0 to 1. */
    fun onAssembleProgress(progress: Double): Double? =
        advanceTo(FETCH_SHARE + (ASSEMBLE_END - FETCH_SHARE) * progress.coerceIn(0.0, 1.0))

    /** The file is saved. */
    fun onSaved(): Double? = advanceTo(1.0)

    private fun startNextFile() {
        if (fileIndex >= 0) filePercent[fileIndex] = 100.0
        fileIndex = minOf(fileIndex + 1, weights.lastIndex)
    }

    private fun advanceTo(value: Double): Double? {
        if (value <= fraction) return null
        fraction = value.coerceAtMost(1.0)
        return fraction
    }

    companion object {
        /** The fetch's share of the whole job. */
        const val FETCH_SHARE = 0.95

        /** Where assembling and writing end; the last step to 1.0 is the file being published. */
        const val ASSEMBLE_END = 0.99

        private const val DOWNLOAD = "[download]"
        private const val DESTINATION = "[download] Destination:"
        private const val ALREADY_DOWNLOADED = "has already been downloaded"
        private val percentLine = Regex("""^\[download]\s+(\d+(?:\.\d+)?)%""")

        private fun percentOf(text: String): Double? {
            if (text.contains("(frag 0/")) return null
            return percentLine.find(text)?.groupValues?.get(1)?.toDoubleOrNull()
        }

        private fun weightsFor(sizes: List<Double?>): List<Double> {
            if (sizes.isEmpty()) return emptyList()
            val known = sizes.map { it?.takeIf { size -> size > 0 } }
            if (known.any { it == null }) return List(sizes.size) { 1.0 / sizes.size }
            val total = known.sumOf { it ?: 0.0 }
            return known.map { (it ?: 0.0) / total }
        }
    }
}
