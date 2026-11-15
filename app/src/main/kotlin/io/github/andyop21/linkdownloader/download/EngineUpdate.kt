package io.github.andyop21.linkdownloader.download

import android.content.Context
import androidx.core.content.edit

/** When yt-dlp is checked for an update (docs/architecture.md § Background work). */
object EngineUpdateRule {

    /** How long a successful check stays fresh. */
    const val INTERVAL_MILLIS = 24L * 60 * 60 * 1000

    /**
     * True when no check has succeeded yet, the last one is at least a day old, or the clock now
     * reads earlier than the last check (it was changed), so a wrong clock never blocks updates.
     */
    fun isDue(lastCheckMillis: Long?, nowMillis: Long): Boolean =
        lastCheckMillis == null || nowMillis < lastCheckMillis || nowMillis - lastCheckMillis >= INTERVAL_MILLIS
}

/** Keeps the time of the last successful yt-dlp update check. */
class EngineUpdateStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Milliseconds since the epoch of the last successful check, or null if there was none. */
    var lastCheckMillis: Long?
        get() = prefs.getLong(KEY_LAST_CHECK, NONE).takeIf { it != NONE }
        set(value) = prefs.edit {
            if (value == null) remove(KEY_LAST_CHECK) else putLong(KEY_LAST_CHECK, value)
        }

    private companion object {
        const val PREFS = "engine_update"
        const val KEY_LAST_CHECK = "last_check_ms"
        const val NONE = -1L
    }
}
