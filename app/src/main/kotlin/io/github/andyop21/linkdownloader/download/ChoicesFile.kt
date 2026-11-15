package io.github.andyop21.linkdownloader.download

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

/** The videos of a post with several, as [DownloadWorker] hands them to the screen. */
@Serializable
data class ChoiceList(val title: String, val choices: List<VideoChoice>)

/**
 * Stores a [ChoiceList] as JSON in the cache, because WorkManager's output `Data` is capped at
 * 10 KB and thumbnail URLs are long (DEC-005). Only the newest list is kept: writing one deletes
 * the others.
 */
object ChoicesFile {

    private const val DIR = "choices"

    private val json = Json { ignoreUnknownKeys = true }

    /** Writes [list] to `<cacheDir>/choices/<name>.json`, deletes older lists and returns the file. */
    fun write(cacheDir: File, name: String, list: ChoiceList): File {
        val dir = File(cacheDir, DIR)
        dir.mkdirs()
        val file = File(dir, "$name.json")
        dir.listFiles().orEmpty().filter { it != file }.forEach { it.delete() }
        file.writeText(json.encodeToString(ChoiceList.serializer(), list))
        return file
    }

    /** Reads the list at [path], or null when the file is gone (the cache was cleared) or unreadable. */
    fun read(path: String): ChoiceList? = try {
        json.decodeFromString(ChoiceList.serializer(), File(path).readText())
    } catch (_: IOException) {
        null
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}
