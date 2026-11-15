package io.github.andyop21.linkdownloader

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import io.github.andyop21.linkdownloader.download.Notifications
import io.github.andyop21.linkdownloader.ui.MainScreen
import io.github.andyop21.linkdownloader.ui.MainViewModel
import io.github.andyop21.linkdownloader.ui.theme.LinkDownloaderTheme
import java.util.UUID

/**
 * The app's only activity. It hosts [MainScreen] inside [LinkDownloaderTheme] and passes the
 * "Choose which videos" notification's work ID to the [MainViewModel].
 */
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels { MainViewModel.factory(application) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleChoice(intent)
        val appVersion = appVersion()
        setContent {
            LinkDownloaderTheme {
                MainScreen(viewModel, appVersion)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleChoice(intent)
    }

    /** Shows the choice the notification was about. A launch from Recents repeats an old intent, so it is ignored. */
    private fun handleChoice(intent: Intent) {
        val extra = intent.getStringExtra(Notifications.EXTRA_CHOOSE_WORK_ID) ?: return
        intent.removeExtra(Notifications.EXTRA_CHOOSE_WORK_ID)
        if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
        val workId = try {
            UUID.fromString(extra)
        } catch (_: IllegalArgumentException) {
            return
        }
        viewModel.onOpenChoice(workId)
    }

    private fun appVersion(): String {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0)
        }
        return info.versionName.orEmpty()
    }
}
