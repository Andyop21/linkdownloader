package io.github.andyop21.linkdownloader.download

import androidx.work.Data

/** How a finished [DownloadWorker] ended, read back from its output [Data]. */
sealed interface DownloadResult {

    /** One saved video: its content uri (as a string), its file name in `Downloads/` and its title. */
    data class Video(val uri: String, val fileName: String, val title: String)

    /**
     * [videos] were saved, in download order. [savedCount] is how many were saved in all; it is
     * larger than `videos.size` only when the list had to be shortened to fit WorkManager's 10 KB
     * cap. [failed] counts the chosen videos that could not be saved.
     */
    data class Saved(val videos: List<Video>, val savedCount: Int, val failed: Int) : DownloadResult

    /**
     * [link] is a post with several videos; the choices are in the JSON file at [choicesFile]
     * ([ChoicesFile.read]). The link travels with the result so the chosen videos can be queued
     * even when the screen did not start the first job (it was opened from the notification).
     */
    data class Choose(val link: String, val title: String, val choicesFile: String) : DownloadResult

    data class Failed(val reason: FailureReason) : DownloadResult
}

/**
 * The [Data] a download carries in and out of WorkManager: its input, its progress and its
 * result. The screen reads [WorkInfo][androidx.work.WorkInfo] with [progressOf] and [resultOf].
 */
object DownloadData {

    /** Tag on every download's work request; [DownloadQueue.observeDownloads] filters by it. */
    const val TAG_DOWNLOAD = "download"

    /** Titles are cut to this many characters in progress and output data, to stay far below 10 KB. */
    const val MAX_TITLE_CHARS = 200

    /*
     * Input keys must differ from every output key, of a download or an update. A job appended to
     * the `engine` chain gets its input merged with the output of the job before it, and WorkManager's
     * OverwritingInputMerger lets that output win: with a shared key, a post's "choose" output
     * replaced the next job's link with the post's own (BUG-002).
     */
    private const val KEY_INPUT_LINK = "input_link"
    private const val KEY_INPUT_SELECTION = "input_selection"

    private const val KEY_LINK = "link"

    private const val KEY_STAGE = "stage"
    private const val KEY_TITLE = "title"
    private const val KEY_POSITION = "position"
    private const val KEY_COUNT = "count"
    private const val KEY_FRACTION = "fraction"

    private const val KEY_OUTCOME = "outcome"
    private const val KEY_URIS = "uris"
    private const val KEY_FILE_NAMES = "file_names"
    private const val KEY_TITLES = "titles"
    private const val KEY_SAVED = "saved"
    private const val KEY_FAILED = "failed"
    private const val KEY_CHOICES_FILE = "choices_file"
    private const val KEY_REASON = "reason"

    private const val OUTCOME_SAVED = "saved"
    private const val OUTCOME_CHOOSE = "choose"
    private const val OUTCOME_FAILED = "failed"

    /** The input of a download of [link]; [selection] holds the chosen positions of a post with several videos. */
    fun input(link: String, selection: List<Int>? = null): Data = Data.Builder()
        .putString(KEY_INPUT_LINK, link)
        .apply { if (!selection.isNullOrEmpty()) putIntArray(KEY_INPUT_SELECTION, selection.toIntArray()) }
        .build()

    fun linkOf(input: Data): String? = input.getString(KEY_INPUT_LINK)?.takeIf { it.isNotBlank() }

    fun selectionOf(input: Data): List<Int>? = input.getIntArray(KEY_INPUT_SELECTION)?.toList()?.takeIf { it.isNotEmpty() }

    fun progress(progress: DownloadProgress): Data = Data.Builder()
        .putString(KEY_STAGE, progress.stage.name)
        .putString(KEY_TITLE, progress.title?.clipped(MAX_TITLE_CHARS))
        .putInt(KEY_POSITION, progress.position)
        .putInt(KEY_COUNT, progress.count)
        .apply { progress.fraction?.let { putDouble(KEY_FRACTION, it) } }
        .build()

    /** The progress in [data], or null when the work has published none yet. */
    fun progressOf(data: Data): DownloadProgress? {
        val stage = data.getString(KEY_STAGE)?.let { name -> DownloadStage.entries.firstOrNull { it.name == name } }
            ?: return null
        return DownloadProgress(
            stage = stage,
            title = data.getString(KEY_TITLE),
            position = data.getInt(KEY_POSITION, 1),
            count = data.getInt(KEY_COUNT, 1),
            fraction = if (data.hasKeyWithValueOfType(KEY_FRACTION, Double::class.javaObjectType)) {
                data.getDouble(KEY_FRACTION, 0.0)
            } else {
                null
            },
        )
    }

    /**
     * The output for [videos] saved and [failed] not saved. Should the list not fit in 10 KB (a post
     * with many videos and long names), the last videos are left out of it; [DownloadResult.Saved.savedCount]
     * still counts them.
     */
    fun saved(videos: List<DownloadResult.Video>, failed: Int): Data {
        for (kept in videos.size downTo 0) {
            val list = videos.take(kept)
            try {
                return Data.Builder()
                    .putString(KEY_OUTCOME, OUTCOME_SAVED)
                    .putStringArray(KEY_URIS, list.map { it.uri }.toTypedArray())
                    .putStringArray(KEY_FILE_NAMES, list.map { it.fileName }.toTypedArray())
                    .putStringArray(KEY_TITLES, list.map { it.title.clipped(MAX_TITLE_CHARS) }.toTypedArray())
                    .putInt(KEY_SAVED, videos.size)
                    .putInt(KEY_FAILED, failed)
                    .build()
            } catch (_: IllegalStateException) {
                // Over WorkManager's size cap: try again with one video fewer.
            }
        }
        error("an empty saved result cannot exceed the size cap")
    }

    fun choose(link: String, title: String, choicesFile: String): Data = Data.Builder()
        .putString(KEY_OUTCOME, OUTCOME_CHOOSE)
        .putString(KEY_LINK, link)
        .putString(KEY_TITLE, title.clipped(MAX_TITLE_CHARS))
        .putString(KEY_CHOICES_FILE, choicesFile)
        .build()

    fun failed(reason: FailureReason): Data = Data.Builder()
        .putString(KEY_OUTCOME, OUTCOME_FAILED)
        .putString(KEY_REASON, reason.name)
        .build()

    /** The result in a finished download's output [data], or null when it holds none. */
    fun resultOf(data: Data): DownloadResult? = when (data.getString(KEY_OUTCOME)) {
        OUTCOME_SAVED -> {
            val uris = data.getNullableStringArray(KEY_URIS).orEmpty()
            val names = data.getNullableStringArray(KEY_FILE_NAMES).orEmpty()
            val titles = data.getNullableStringArray(KEY_TITLES).orEmpty()
            val videos = uris.indices.mapNotNull { i ->
                val uri = uris[i] ?: return@mapNotNull null
                DownloadResult.Video(uri, names.getOrNull(i) ?: "", titles.getOrNull(i) ?: "")
            }
            DownloadResult.Saved(videos, data.getInt(KEY_SAVED, videos.size), data.getInt(KEY_FAILED, 0))
        }
        OUTCOME_CHOOSE -> {
            val link = data.getString(KEY_LINK)
            val file = data.getString(KEY_CHOICES_FILE)
            if (link.isNullOrBlank() || file == null) null else DownloadResult.Choose(link, data.getString(KEY_TITLE).orEmpty(), file)
        }
        OUTCOME_FAILED -> DownloadResult.Failed(
            FailureReason.entries.firstOrNull { it.name == data.getString(KEY_REASON) } ?: FailureReason.Unknown,
        )
        else -> null
    }

    /** At most [max] characters, never splitting a surrogate pair, with an ellipsis when cut. */
    internal fun String.clipped(max: Int): String {
        if (length <= max) return this
        var end = max - 1
        if (end > 0 && Character.isHighSurrogate(this[end - 1])) end--
        return substring(0, end).trimEnd() + "…"
    }
}
