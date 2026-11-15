package io.github.andyop21.linkdownloader.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.andyop21.linkdownloader.R
import io.github.andyop21.linkdownloader.ui.theme.PillShape
import io.github.andyop21.linkdownloader.ui.theme.springSpec
import io.github.andyop21.linkdownloader.ui.theme.tweenSpec

/**
 * The header: the mascot above the app name. A long-press opens the About sheet (B10); TalkBack
 * offers the same as a long-press action named "Show app info".
 */
@Composable
fun HeaderWithMascot(
    mood: Mood,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Float? = null,
) {
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    val actionLabel = stringResource(R.string.header_about_action)
    Column(
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
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Mascot(mood, Modifier.size(104.dp), progress = progress)
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() },
        )
    }
}

/**
 * The bar: a soft filled pill that holds the link on one line. The keyboard's action key is Go,
 * which calls [onGo] (B3). While a download runs ([enabled] false) the text stays readable but
 * cannot change, and Go does nothing (B4). A lavender ring shows when it has focus.
 */
@Composable
fun LinkBar(
    text: String,
    enabled: Boolean,
    onTextChange: (String) -> Unit,
    onGo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val ring by animateColorAsState(
        targetValue = if (focused) colors.primary else Color.Transparent,
        animationSpec = tweenSpec(),
        label = "bar ring",
    )
    val container = colors.surfaceContainerHighest
    TextField(
        value = text,
        onValueChange = onTextChange,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .border(2.dp, ring, PillShape),
        readOnly = !enabled,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge,
        label = { Text(stringResource(R.string.bar_label)) },
        placeholder = { Text(stringResource(R.string.bar_placeholder)) },
        leadingIcon = { Icon(AppIcons.Link, contentDescription = null) },
        shape = PillShape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = container,
            unfocusedContainerColor = container,
            disabledContainerColor = container,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            focusedLabelColor = colors.primary,
            unfocusedLabelColor = colors.onSurfaceVariant,
            focusedLeadingIconColor = colors.primary,
            unfocusedLeadingIconColor = colors.onSurfaceVariant,
        ),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Go,
            autoCorrectEnabled = false,
        ),
        keyboardActions = KeyboardActions(onGo = { if (enabled) onGo() }),
        interactionSource = interaction,
    )
}

/**
 * The one big button (B1, B2): lavender, with a clipboard icon, and it gives a little when
 * pressed. Disabled while a download runs (B4).
 */
@Composable
fun PasteButton(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) PRESSED_SCALE else 1f,
        animationSpec = springSpec(),
        label = "paste press",
    )
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = PillShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.primary,
            contentColor = colors.onPrimary,
            disabledContainerColor = colors.primaryContainer,
            disabledContentColor = colors.onPrimaryContainer.copy(alpha = 0.55f),
        ),
        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp),
        interactionSource = interaction,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
    ) {
        Icon(AppIcons.Paste, contentDescription = null, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(12.dp))
        Text(stringResource(R.string.paste), style = MaterialTheme.typography.headlineSmall)
    }
}

private const val PRESSED_SCALE = 0.95f
