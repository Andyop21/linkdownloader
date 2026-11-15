package io.github.andyop21.linkdownloader

import android.app.Application
import android.os.SystemClock
import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import kotlin.concurrent.thread

/**
 * Application entry point. Initialises youtubedl-android once per process, off the main thread:
 * the first start after an install or upgrade unpacks the bundled Python, which takes seconds.
 * A failure is logged and leaves the app running; downloads report it when they start.
 */
class LinkDownloaderApp : Application() {

    override fun onCreate() {
        super.onCreate()
        thread(name = "engine-init") { initEngine() }
    }

    private fun initEngine() {
        val start = SystemClock.elapsedRealtime()
        try {
            YoutubeDL.getInstance().init(this)
            Log.i(TAG, "youtubedl-android ready in ${SystemClock.elapsedRealtime() - start} ms")
        } catch (e: Exception) {
            Log.e(TAG, "youtubedl-android failed to initialise", e)
        }
    }

    private companion object {
        const val TAG = "LinkDownloaderApp"
    }
}
