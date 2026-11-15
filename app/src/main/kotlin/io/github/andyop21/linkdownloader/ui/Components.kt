package io.github.andyop21.linkdownloader.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.andyop21.linkdownloader.R

/**
 * The header: the mascot and the app name. A long-press opens the About sheet (B10); TalkBack
 * offers the same as a long-press action named "Show app info".
 */
@Composable
fun HeaderWithMascot(mood: Mood, onLongPress: () -> Unit, modifier: Modifier = Modifier) {
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    val actionLabel = stringResource(R.string.header_about_action)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .pointerInput(Unit) { detectTapGestures(onLongPress = { currentOnLongPress() }) }
            .semantics(mergeDescendants = true) {
                onLongClick(label = actionLabel) {
                    currentOnLongPress()
                    true
                }
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Mascot(mood, Modifier.size(56.dp))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() },
        )
    }
}

/**
 * The bar: one line that holds the link. The keyboard's action key is Go, which calls [onGo]
 * (B3). While a download runs ([enabled] false) the text stays readable but cannot change, and Go
 * does nothing (B4).
 */
@Composable
fun LinkBar(
    text: String,
    enabled: Boolean,
    onTextChange: (String) -> Unit,
    onGo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = text,
        onValueChange = onTextChange,
        modifier = modifier.fillMaxWidth(),
        readOnly = !enabled,
        singleLine = true,
        label = { Text(stringResource(R.string.bar_label)) },
        placeholder = { Text(stringResource(R.string.bar_placeholder)) },
        shape = CircleShape,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Go,
            autoCorrectEnabled = false,
        ),
        keyboardActions = KeyboardActions(onGo = { if (enabled) onGo() }),
    )
}

/** The one big button (B1, B2), disabled while a download runs (B4). */
@Composable
fun PasteButton(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp),
    ) {
        Text(stringResource(R.string.paste), style = MaterialTheme.typography.titleLarge)
    }
}
