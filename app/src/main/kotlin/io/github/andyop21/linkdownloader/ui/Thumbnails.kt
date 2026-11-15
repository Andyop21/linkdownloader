package io.github.andyop21.linkdownloader.ui

import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * The thumbnails loaded while the choice sheet is open, by URL. A failed load is kept as null, so
 * it is not tried again and the tile keeps its neutral placeholder. Used on the main thread only.
 */
class ThumbnailCache {
    private val loaded = mutableMapOf<String, ImageBitmap?>()

    fun has(url: String): Boolean = url in loaded

    fun peek(url: String): ImageBitmap? = loaded[url]

    fun put(url: String, bitmap: ImageBitmap?) {
        loaded[url] = bitmap
    }
}

/** The thumbnail at [url], scaled down, or null while it loads, when it fails, or when there is none. */
@Composable
fun rememberThumbnail(url: String?, cache: ThumbnailCache): ImageBitmap? {
    val state = produceState(initialValue = url?.let(cache::peek), url, cache) {
        if (url == null || cache.has(url)) return@produceState
        val bitmap = ThumbnailLoader.load(url)
        cache.put(url, bitmap)
        value = bitmap
    }
    return state.value
}

/**
 * Loads a thumbnail off the main thread with plain [HttpURLConnection] and [BitmapFactory],
 * decoded at a reduced size (DEC-005: no image library).
 */
object ThumbnailLoader {

    private const val TAG = "ThumbnailLoader"

    /** About the size of a tile on a large phone; images are decoded at no less than this. */
    private const val TARGET_PX = 360
    private const val MAX_BYTES = 8 * 1024 * 1024
    private const val TIMEOUT_MS = 15_000

    suspend fun load(url: String): ImageBitmap? = withContext(Dispatchers.IO) {
        try {
            val bytes = fetch(url) ?: return@withContext null
            decode(bytes)?.asImageBitmap()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The URL is not logged: it can name the account that posted the video.
            Log.w(TAG, "thumbnail not loaded: ${e.javaClass.simpleName}")
            null
        }
    }

    private fun fetch(url: String): ByteArray? {
        val connection = URL(url).openConnection() as? HttpURLConnection ?: return null
        return try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.instanceFollowRedirects = true
            if (connection.responseCode !in 200..299) null else connection.inputStream.use(::readCapped)
        } finally {
            connection.disconnect()
        }
    }

    /** The whole stream, or null when it is larger than [MAX_BYTES]. */
    private fun readCapped(input: InputStream): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) return out.toByteArray()
            if (out.size() + read > MAX_BYTES) return null
            out.write(buffer, 0, read)
        }
    }

    private fun decode(bytes: ByteArray) = BitmapFactory.Options().run {
        inJustDecodeBounds = true
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, this)
        if (outWidth <= 0 || outHeight <= 0) return@run null
        var sample = 1
        while (outWidth / (sample * 2) >= TARGET_PX && outHeight / (sample * 2) >= TARGET_PX) sample *= 2
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}
