package io.github.andyop21.linkdownloader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.andyop21.linkdownloader.R

/**
 * The hidden About sheet (B10): the app version, the yt-dlp version and **Update yt-dlp**, which
 * shows "Checking…", then "Up to date" or the new version.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutSheet(appVersion: String, about: AboutState, onUpdate: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            VersionRow(stringResource(R.string.about_app_version), appVersion)
            VersionRow(
                stringResource(R.string.about_engine_version),
                about.engineVersion ?: stringResource(
                    if (about.versionLoading) R.string.about_version_loading else R.string.about_version_unknown,
                ),
            )
            Button(
                onClick = onUpdate,
                enabled = about.check != EngineCheck.Checking,
                shape = CircleShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
            ) {
                Text(stringResource(R.string.about_update))
            }
            checkText(about.check)?.let { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
}

@Composable
private fun VersionRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
