package io.github.andyop21.linkdownloader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.andyop21.linkdownloader.R
import io.github.andyop21.linkdownloader.ui.theme.AppTheme
import io.github.andyop21.linkdownloader.ui.theme.AppType
import io.github.andyop21.linkdownloader.ui.theme.CardShape
import io.github.andyop21.linkdownloader.ui.theme.SheetShape

/**
 * The hidden About sheet (B10): the app version, the yt-dlp version and **Update yt-dlp**, which
 * shows "Checking…", then "Up to date" or the new version.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutSheet(appVersion: String, about: AboutState, onUpdate: () -> Unit, onDismiss: () -> Unit) {
    val colors = AppTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = SheetShape,
        containerColor = colors.surface,
        scrimColor = colors.scrim,
        dragHandle = { BottomSheetDefaults.DragHandle(color = colors.borderStrong) },
    ) {
        AboutSheetContent(appVersion, about, onUpdate)
    }
}

/** The About sheet's content: the app name, both versions in one grouped list, **Update yt-dlp** and its result. */
@Composable
fun AboutSheetContent(appVersion: String, about: AboutState, onUpdate: () -> Unit) {
    val colors = AppTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = AppType.heading,
            color = colors.ink,
            modifier = Modifier.semantics { heading() },
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.canvas, CardShape)
                .border(1.dp, colors.border, CardShape),
        ) {
            VersionRow(stringResource(R.string.about_app_version), appVersion)
            Box(
                Modifier
                    .padding(start = 16.dp)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.border),
            )
            VersionRow(
                stringResource(R.string.about_engine_version),
                about.engineVersion ?: stringResource(
                    if (about.versionLoading) R.string.about_version_loading else R.string.about_version_unknown,
                ),
            )
        }
        ActionButton(
            text = stringResource(R.string.about_update),
            onClick = onUpdate,
            enabled = about.check != EngineCheck.Checking,
            modifier = Modifier.fillMaxWidth(),
        )
        checkText(about.check)?.let { text ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            ) {
                PulseDot(
                    color = when (about.check) {
                        EngineCheck.NotChecked -> colors.warning
                        EngineCheck.Checking -> colors.primary
                        else -> colors.success
                    },
                    pulse = about.check == EngineCheck.Checking,
                )
                Spacer(Modifier.width(10.dp))
                Text(text = text, style = AppType.body, color = colors.ink2)
            }
        }
    }
}

@Composable
private fun VersionRow(label: String, value: String) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = AppType.body, color = colors.ink2, modifier = Modifier.weight(1f))
        Text(value, style = AppType.value, color = colors.ink)
    }
}

@Composable
private fun checkText(check: EngineCheck): String? = when (check) {
    EngineCheck.None -> null
    EngineCheck.Checking -> stringResource(R.string.about_checking)
    EngineCheck.UpToDate -> stringResource(R.string.about_up_to_date)
    is EngineCheck.Updated -> check.version?.let { stringResource(R.string.about_updated, it) }
        ?: stringResource(R.string.about_updated_no_version)
    EngineCheck.NotChecked -> stringResource(R.string.about_not_checked)
}
