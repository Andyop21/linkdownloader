package io.github.andyop21.linkdownloader.debug

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import io.github.andyop21.linkdownloader.download.ChoicesFile
import io.github.andyop21.linkdownloader.download.DownloadData
import io.github.andyop21.linkdownloader.download.DownloadQueue
import io.github.andyop21.linkdownloader.download.DownloadResult
import io.github.andyop21.linkdownloader.ui.theme.LinkDownloaderTheme
import kotlin.math.roundToInt

/**
 * Debug builds only. Starts downloads and yt-dlp updates from `adb`, so `DownloadWorker`, its
 * notifications and `UpdateWorker` can be checked on a phone before the screen exists, and lists
 * every download WorkManager knows. Commands in docs/development.md § Background work by hand.
 *
 * Extras: `link` (a link to download), `select` (`all` or 0-based positions such as `0,2`, for a
 * post with several videos), `update` (`now` or `due`).
 */
class DebugDownloadActivity : ComponentActivity() {

    private val queue by lazy { DownloadQueue(this) }
    private val actions = mutableStateListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handle(intent)
        setContent {
            LinkDownloaderTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val downloads by queue.observeDownloads().collectAsState(initial = emptyList())
                    DebugList(actions, downloads)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent) {
        intent.getStringExtra(EXTRA_UPDATE)?.let { mode ->
            actions += when (mode) {
                "now" -> "update queued: ${queue.updateEngineNow()}"
                else -> "update due: ${queue.updateEngineIfDue()}"
            }
        }
        val link = intent.getStringExtra(EXTRA_LINK) ?: return
        val selection = intent.getStringExtra(EXTRA_SELECT)?.let { select ->
            if (select == "all") (0 until MAX_POSITIONS).toList() else select.split(',').mapNotNull { it.trim().toIntOrNull() }
        }
        actions += "download queued: ${queue.startDownload(link, selection)}" + (selection?.let { " (selection ${it.size})" } ?: "")
        // Keeps a relaunch with the same intent from queueing the download again.
        intent.removeExtra(EXTRA_LINK)
    }

    private companion object {
        const val EXTRA_LINK = "link"
        const val EXTRA_SELECT = "select"
        const val EXTRA_UPDATE = "update"
        const val MAX_POSITIONS = 50
    }
}

@Composable
private fun DebugList(actions: List<String>, downloads: List<WorkInfo>) {
    val scroll = rememberScrollState()
    val lines = remember(actions.size, downloads) { actions + downloads.map(::describe) }
    Column(
        modifier = Modifier
            .safeDrawingPadding()
            .padding(16.dp)
            .verticalScroll(scroll),
    ) {
        Text("Background download check (debug)", style = MaterialTheme.typography.titleMedium)
        lines.forEach { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
    }
}

private fun describe(info: WorkInfo): String {
    val progress = DownloadData.progressOf(info.progress)?.let { p ->
        " · ${p.stage} ${p.position}/${p.count} ${p.fraction?.let { "${(it * 100).roundToInt()}%" } ?: "-"} ${p.title.orEmpty()}"
    }.orEmpty()
    val result = when (val r = DownloadData.resultOf(info.outputData)) {
        is DownloadResult.Saved -> " · saved ${r.savedCount} (failed ${r.failed}): ${r.videos.joinToString { it.fileName }}"
        is DownloadResult.Choose -> " · choose: ${ChoicesFile.read(r.choicesFile)?.choices?.size ?: "?"} videos in \"${r.title}\""
        is DownloadResult.Failed -> " · failed: ${r.reason}"
        null -> ""
    }
    return "${info.id.toString().take(8)} ${info.state}$progress$result"
}
