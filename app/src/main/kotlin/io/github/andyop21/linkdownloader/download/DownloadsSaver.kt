package io.github.andyop21.linkdownloader.download

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** A file published in `Downloads/`: its content [uri] and the name MediaStore gave it. */
data class SavedFile(val uri: Uri, val fileName: String)

/**
 * Saves a file to the shared `Downloads/` folder through MediaStore. No storage permission is
 * needed from API 29 on.
 *
 * The entry is created with `IS_PENDING = 1`, so no other app sees a half-written file, and is
 * published by clearing the flag. Any failure or cancellation while writing deletes the entry.
 */
class DownloadsSaver(context: Context) {

    private val resolver = context.applicationContext.contentResolver
    private val collection: Uri = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    /**
     * Creates a pending entry named [displayName] with [mimeType], lets [write] fill it through a
     * read-write file descriptor (seekable, as `MediaMuxer` needs), and publishes it.
     */
    suspend fun save(
        displayName: String,
        mimeType: String,
        write: suspend (ParcelFileDescriptor) -> Unit,
    ): SavedFile {
        val uri = withContext(Dispatchers.IO) { create(displayName, mimeType) }
        try {
            withContext(Dispatchers.IO) {
                val descriptor = resolver.openFileDescriptor(uri, "rw")
                    ?: throw IllegalStateException("MediaStore gave no file descriptor")
                descriptor.use { write(it) }
                ensureActive()
                publish(uri)
            }
        } catch (e: Throwable) {
            withContext(NonCancellable + Dispatchers.IO) { delete(uri) }
            throw e
        }
        val name = withContext(NonCancellable + Dispatchers.IO) { runCatching { displayNameOf(uri) }.getOrNull() }
        return SavedFile(uri, name ?: displayName)
    }

    private fun create(displayName: String, mimeType: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        return resolver.insert(collection, values)
            ?: throw IllegalStateException("MediaStore refused a new Downloads entry")
    }

    private fun publish(uri: Uri) {
        val values = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
        check(resolver.update(uri, values, null, null) == 1) { "MediaStore did not publish the entry" }
    }

    private fun displayNameOf(uri: Uri): String? =
        resolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

    private fun delete(uri: Uri) {
        try {
            resolver.delete(uri, null, null)
        } catch (e: Exception) {
            Log.w(TAG, "could not delete the unfinished Downloads entry", e)
        }
    }

    private companion object {
        const val TAG = "DownloadsSaver"
    }
}
