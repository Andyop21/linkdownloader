package io.github.andyop21.linkdownloader.ui

import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import io.github.andyop21.linkdownloader.R
import io.github.andyop21.linkdownloader.download.DownloadResult
import io.github.andyop21.linkdownloader.download.FailureReason
import io.github.andyop21.linkdownloader.download.VideoChoice
import io.github.andyop21.linkdownloader.ui.theme.AppTheme
import io.github.andyop21.linkdownloader.ui.theme.CardShape
import io.github.andyop21.linkdownloader.ui.theme.LinkDownloaderTheme
import io.github.andyop21.linkdownloader.ui.theme.SheetShape
import io.github.andyop21.linkdownloader.ui.theme.Space
import io.github.andyop21.linkdownloader.ui.theme.rise
import java.util.UUID
import kotlinx.coroutines.launch

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
            onClear = viewModel::onClear,
            onCancel = viewModel::onCancel,
            onRetry = {
                focusManager.clearFocus()
                viewModel.onRetry()
            },
            onDownloadAgain = viewModel::onDownloadAgain,
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
    val onClear: () -> Unit = {},
    val onCancel: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onDownloadAgain: () -> Unit = {},
    val onOpen: (uri: String) -> Unit = {},
    val onOpenAbout: () -> Unit = {},
    val onCloseAbout: () -> Unit = {},
    val onUpdateEngine: () -> Unit = {},
    val onChoose: (positions: List<Int>) -> Unit = {},
    val onDismissChoice: () -> Unit = {},
)

/**
 * The screen for [state], centred: the app's name in a bar at the top; the orb and the status
 * under it in the middle of the free space, a little above centre; and the link field with
 * **Paste link** at the bottom, where the thumb rests. Plus the choice and About sheets when open.
 * The content is at most 560 dp wide and scrolls when it does not fit (small phones, landscape,
 * the largest font, the keyboard open). It stays clear of the system bars and the keyboard. Its
 * blocks rise in, one after another, when the screen first appears. A tap on empty space puts the
 * keyboard away.
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
    val colors = AppTheme.colors
    val focusManager = LocalFocusManager.current
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.canvas,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { AppSnackbarHost(snackbar) },
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding(),
        ) {
            val viewport = maxHeight
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 560.dp)
                        .fillMaxWidth()
                        .heightIn(min = viewport)
                        // A tap on empty space puts the keyboard away.
                        .pointerInput(Unit) { detectTapGestures { focusManager.clearFocus() } }
                        .padding(horizontal = Space.xl)
                        .padding(bottom = Space.l),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Header(onLongPress = actions.onOpenAbout, modifier = Modifier.rise(0))
                    Spacer(Modifier.weight(ABOVE).heightIn(min = Space.xl))
                    StatusHero(
                        status = status,
                        actions = StatusActions(
                            onCancel = actions.onCancel,
                            onOpen = { firstVideoUri(status)?.let(actions.onOpen) },
                            onRetry = actions.onRetry,
                            onDownloadAgain = actions.onDownloadAgain,
                            canRetry = status is Status.Failed && status.reason.retryable && state.bar.isNotBlank(),
                        ),
                        modifier = Modifier.rise(1),
                    )
                    Spacer(Modifier.weight(BELOW).heightIn(min = Space.xl))
                    LinkBar(
                        text = state.bar,
                        enabled = !status.isRunning,
                        onTextChange = actions.onBarChanged,
                        onGo = actions.onGo,
                        onClear = actions.onClear,
                        modifier = Modifier.rise(2),
                    )
                    Spacer(Modifier.height(Space.m))
                    PasteButton(
                        enabled = !status.isRunning,
                        onClick = actions.onPaste,
                        modifier = Modifier.rise(3),
                    )
                }
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

/** Free space above and below the hero: a little less above, so it sits at the optical centre. */
private const val ABOVE = 0.8f
private const val BELOW = 1f

/** The video **Open** plays: the first one saved. */
private fun firstVideoUri(status: Status): String? = when (status) {
    is Status.Done -> status.videos.firstOrNull()?.uri
    is Status.AlreadySaved -> status.videos.firstOrNull()?.uri
    else -> null
}

/** The snackbar: a dark overlay with a hairline edge, in both themes. */
@Composable
private fun AppSnackbarHost(state: SnackbarHostState) {
    val colors = AppTheme.colors
    SnackbarHost(state) { data ->
        Snackbar(
            snackbarData = data,
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .border(1.dp, Color.White.copy(alpha = 0.09f), CardShape),
            shape = CardShape,
            containerColor = colors.overlay,
            contentColor = colors.overlayInk,
            actionColor = colors.overlayAction,
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

/** Every state the screen can show, for the previews below. */
private class UiStatePreviews : PreviewParameterProvider<UiState> {
    override val values = sequenceOf(
        UiState(),
        UiState(status = Status.NoLink(fromClipboard = true)),
        UiState(bar = "example", status = Status.NoLink(fromClipboard = false)),
        UiState(bar = PREVIEW_LINK, status = Status.Preparing),
        UiState(bar = PREVIEW_LINK, status = Status.Choosing("A post with three clips", PreviewChoices)),
        UiState(bar = PREVIEW_LINK, status = Status.Downloading(null, 1, 1, null)),
        UiState(bar = PREVIEW_LINK, status = Status.Downloading("A short clip", 2, 3, 0.42f)),
        UiState(bar = PREVIEW_LINK, status = Status.Saving("A short clip", 1, 1, 0.97f)),
        UiState(status = Status.Done(UUID(0, 1), listOf(PreviewClip), saved = 1, failed = 0)),
        UiState(status = Status.Done(UUID(0, 2), listOf(PreviewClip, PreviewClip), saved = 2, failed = 1)),
        UiState(bar = PREVIEW_LINK, status = Status.AlreadySaved(listOf(PreviewClip), saved = 1)),
        UiState(bar = PREVIEW_LINK, status = Status.Failed(FailureReason.NoInternet)),
        UiState(about = AboutState(open = true, engineVersion = "2026.10.26", check = EngineCheck.UpToDate)),
    )
}

private const val PREVIEW_LINK = "https://example.com/v/1"
private val PreviewClip = DownloadResult.Video("content://media/1", "A short clip.mp4", "A short clip")
private val PreviewChoices = listOf(
    VideoChoice(1, "First clip", null, 8.0),
    VideoChoice(2, "Second clip", null, 21.0),
    VideoChoice(3, "Third clip", null, 64.0),
)

@Preview(name = "Light", showBackground = true, heightDp = 780)
@Preview(name = "Dark", showBackground = true, heightDp = 780, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MainContentPreview(@PreviewParameter(UiStatePreviews::class) state: UiState) {
    LinkDownloaderTheme(reducedMotion = true) {
        MainContent(state, "0.1.0", remember { SnackbarHostState() }, MainActions())
    }
}

@Preview(name = "Choice sheet, light", showBackground = true)
@Preview(name = "Choice sheet, dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChoiceSheetPreview() {
    LinkDownloaderTheme(reducedMotion = true) {
        Surface(color = AppTheme.colors.surface, shape = SheetShape) {
            ChoiceSheetContent(title = "A post with three clips", choices = PreviewChoices, onDownload = {})
        }
    }
}

@Preview(name = "About sheet, light", showBackground = true)
@Preview(name = "About sheet, dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AboutSheetPreview() {
    LinkDownloaderTheme(reducedMotion = true) {
        Surface(color = AppTheme.colors.surface, shape = SheetShape) {
            AboutSheetContent(
                appVersion = "0.1.0",
                about = AboutState(open = true, engineVersion = "2026.10.26", check = EngineCheck.UpToDate),
                onUpdate = {},
            )
        }
    }
}
