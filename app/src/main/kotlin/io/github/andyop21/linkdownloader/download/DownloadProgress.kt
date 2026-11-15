package io.github.andyop21.linkdownloader.download

/** Where a running download is, as [DownloadWorker] publishes it. */
enum class DownloadStage {
    /** Checking the link with the site. */
    Preparing,

    /** Fetching the video's files. */
    Downloading,

    /** Assembling the video and writing it to `Downloads/`. */
    Saving,
}

/**
 * A running download's state, built up from its [DownloadEvent]s with [after].
 *
 * [title] is null until the video is known and [fraction] is null until the first progress
 * arrives. [position] and [count] say which video of a post with several is running ("2 of 3");
 * [fraction] covers the whole job and never goes backwards.
 */
data class DownloadProgress(
    val stage: DownloadStage = DownloadStage.Preparing,
    val title: String? = null,
    val position: Int = 1,
    val count: Int = 1,
    val fraction: Double? = null,
) {

    /** This state after [event]. */
    fun after(event: DownloadEvent): DownloadProgress = when (event) {
        DownloadEvent.Preparing -> copy(stage = DownloadStage.Preparing)
        is DownloadEvent.TitleKnown -> copy(
            stage = DownloadStage.Downloading,
            title = event.title,
            position = event.position,
            count = event.count,
        )
        is DownloadEvent.Progress -> copy(
            stage = if (stage == DownloadStage.Preparing) DownloadStage.Downloading else stage,
            fraction = maxOf(event.fraction.coerceIn(0.0, 1.0), fraction ?: 0.0),
        )
        DownloadEvent.Saving -> copy(stage = DownloadStage.Saving)
    }
}
