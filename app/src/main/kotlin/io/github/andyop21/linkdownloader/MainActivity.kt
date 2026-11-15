package io.github.andyop21.linkdownloader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.andyop21.linkdownloader.ui.MainScreen
import io.github.andyop21.linkdownloader.ui.theme.LinkDownloaderTheme

/** The app's only activity. It hosts [MainScreen] inside [LinkDownloaderTheme]. */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            LinkDownloaderTheme {
                MainScreen()
            }
        }
    }
}
