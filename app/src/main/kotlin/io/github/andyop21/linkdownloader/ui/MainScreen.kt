package io.github.andyop21.linkdownloader.ui

import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import io.github.andyop21.linkdownloader.R
import io.github.andyop21.linkdownloader.download.DownloadResult
import io.github.andyop21.linkdownloader.ui.theme.LinkDownloaderTheme
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * The app's one screen (docs/product.md § The screen), driven by [viewModel]. This function only
 * connects the platform to the ViewModel: it reads the clipboard when Paste is tapped, opens a
 * saved video, gives the haptic tick when a download is done and asks for the notification
 * permission once. [MainContent] draws the state.
 */
@Composable
fun MainScreen(viewModel: MainViewModel, appVersion: String) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val haptics = LocalHapticFeedback.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val noAppMessage = stringResource(R.string.open_no_app)

    // One tick per finished job, also across rotations (B6).
    var tickedJob by rememberSaveable { mutableStateOf<String?>(null) }
    val status = state.status
    LaunchedEffect(status) {
        if (status is Status.Done && tickedJob != status.jobId.toString()) {
            tickedJob = status.jobId.toString()
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        }
    }

    AskForNotificationsOnce(downloadRunning = status.isRunning)

    MainContent(
        state = state,
        appVersion = appVersion,
        snackbar = snackbar,
        actions = MainActions(
            onBarChanged = viewModel::onBarChanged,
            onGo = {
                focusManager.clearFocus()
                viewModel.onSubmit(viewModel.state.value.bar)
            },
            onPaste = {
                focusManager.clearFocus()
                viewModel.onPaste(readClipboard(context))
            },
            onCancel = viewModel::onCancel,
            onOpen = { uri ->
                if (!openVideo(context, uri)) scope.launch { snackbar.showSnackbar(noAppMessage) }
            },
            onOpenAbout = viewModel::onOpenAbout,
            onCloseAbout = viewModel::onCloseAbout,
            onUpdateEngine = viewModel::onUpdateEngine,
            onChoose = viewModel::onChoose,
            onDismissChoice = viewModel::onDismissChoice,
        ),
    )
}

/** What the screen's controls do. */
class MainActions(
    val onBarChanged: (String) -> Unit = {},
    val onGo: () -> Unit = {},
    val onPaste: () -> Unit = {},
    val onCancel: () -> Unit = {},
    val onOpen: (uri: String) -> Unit = {},
    val onOpenAbout: () -> Unit = {},
    val onCloseAbout: () -> Unit = {},
    val onUpdateEngine: () -> Unit = {},
    val onChoose: (positions: List<Int>) -> Unit = {},
    val onDismissChoice: () -> Unit = {},
)

/**
 * The screen for [state], top to bottom: header, bar, Paste, status card; plus the choice and
 * About sheets when open. The column is at most 560 dp wide and scrolls when it does not fit
 * (small phones, landscape, the largest font).
 */
@Composable
fun MainContent(
    state: UiState,
    appVersion: String,
    snackbar: SnackbarHostState,
    actions: MainActions,
    modifier: Modifier = Modifier,
) {
    val status = state.status
    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                HeaderWithMascot(mood = status.mood, onLongPress = actions.onOpenAbout)
                LinkBar(
                    text = state.bar,
                    enabled = !status.isRunning,
                    onTextChange = actions.onBarChanged,
                    onGo = actions.onGo,
                )
                PasteButton(enabled = !status.isRunning, onClick = actions.onPaste)
                StatusCard(
                    status = status,
                    onCancel = actions.onCancel,
                    onOpen = { (status as? Status.Done)?.videos?.firstOrNull()?.let { actions.onOpen(it.uri) } },
                )
            }
        }
    }

    if (status is Status.Choosing) {
        ChoiceSheet(
            title = status.title,
            choices = status.choices,
            onDownload = actions.onChoose,
            onDismiss = actions.onDismissChoice,
        )
    }
    if (state.about.open) {
        AboutSheet(
            appVersion = appVersion,
            about = state.about,
            onUpdate = actions.onUpdateEngine,
            onDismiss = actions.onCloseAbout,
        )
    }
}

/** The clipboard's first item as text, or null when it is empty. Read only when Paste is tapped. */
private fun readClipboard(context: Context): String? {
    val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return clip.getItemAt(0).coerceToText(context)?.toString()
}

/** Plays the saved video at [uri] in the phone's player; false when no app can open it. */
private fun openVideo(context: Context, uri: String): Boolean {
    val contentUri = uri.toUri()
    val type = context.contentResolver.getType(contentUri) ?: "video/*"
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(contentUri, type)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    return try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

@Preview(showBackground = true, name = "Idle")
@Composable
private fun IdlePreview() {
    LinkDownloaderTheme {
        MainContent(UiState(), "0.1.0", remember { SnackbarHostState() }, MainActions())
    }
}

@Preview(showBackground = true, name = "Downloading")
@Composable
private fun DownloadingPreview() {
    LinkDownloaderTheme {
        MainContent(
            UiState(bar = "https://example.com/v/1", status = Status.Downloading("A short clip", 2, 3, 0.42f)),
            "0.1.0",
            remember { SnackbarHostState() },
            MainActions(),
        )
    }
}

@Preview(showBackground = true, name = "Done")
@Composable
private fun DonePreview() {
    LinkDownloaderTheme {
        MainContent(
            UiState(
                bar = "https://example.com/v/1",
                status = Status.Done(
                    UUID(0, 1),
                    listOf(DownloadResult.Video("content://media/1", "A short clip.mp4", "A short clip")),
                    saved = 1,
                    failed = 0,
                ),
            ),
            "0.1.0",
            remember { SnackbarHostState() },
            MainActions(),
        )
    }
}
