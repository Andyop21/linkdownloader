package io.github.andyop21.linkdownloader.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.andyop21.linkdownloader.R
import io.github.andyop21.linkdownloader.ui.theme.AppTheme
import io.github.andyop21.linkdownloader.ui.theme.AppType
import io.github.andyop21.linkdownloader.ui.theme.CapsuleShape
import io.github.andyop21.linkdownloader.ui.theme.Corners
import io.github.andyop21.linkdownloader.ui.theme.FieldShape
import io.github.andyop21.linkdownloader.ui.theme.LocalReducedMotion
import io.github.andyop21.linkdownloader.ui.theme.Motion
import io.github.andyop21.linkdownloader.ui.theme.Space
import io.github.andyop21.linkdownloader.ui.theme.motion
import io.github.andyop21.linkdownloader.ui.theme.tactile

/**
 * The bar at the top: the app's name, centred. A long-press opens the About sheet (B10); TalkBack
 * offers the same as a long-press action named "Show app info".
 */
@Composable
fun Header(onLongPress: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    val actionLabel = stringResource(R.string.header_about_action)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(Space.bar)
            .pointerInput(Unit) { detectTapGestures(onLongPress = { currentOnLongPress() }) }
            .semantics(mergeDescendants = true) {
                onLongClick(label = actionLabel) {
                    currentOnLongPress()
                    true
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = AppType.barTitle,
            color = colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() },
        )
    }
}

/**
 * The link field: one line on the surface with a 3:1 edge, a link icon, and a clear button while
 * it holds text (B13). The keyboard's action key is Go, which calls [onGo] (B3). While a download
 * runs ([enabled] false) the text stays readable but cannot change, Go does nothing and the clear
 * button is hidden (B4). With focus its edge turns blue and a soft ring shows around it.
 */
@Composable
fun LinkBar(
    text: String,
    enabled: Boolean,
    onTextChange: (String) -> Unit,
    onGo: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val edge by animateColorAsState(
        targetValue = if (focused) colors.primary else colors.controlBorder,
        animationSpec = motion(Motion.BASE),
        label = "field edge",
    )
    val ring by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = motion(Motion.SLOW, Motion.EaseOut),
        label = "field ring",
    )
    val icon by animateColorAsState(
        targetValue = if (focused) colors.primary else colors.faint,
        animationSpec = motion(Motion.BASE),
        label = "field icon",
    )
    val label = stringResource(R.string.bar_label)
    val ringColor = colors.primary.copy(alpha = 0.24f)
    BasicTextField(
        value = text,
        onValueChange = onTextChange,
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = label },
        readOnly = !enabled,
        singleLine = true,
        textStyle = AppType.field.copy(color = if (enabled) colors.ink else colors.muted),
        cursorBrush = SolidColor(colors.primary),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Go,
            autoCorrectEnabled = false,
        ),
        keyboardActions = KeyboardActions(onGo = { if (enabled) onGo() }),
        interactionSource = interaction,
        decorationBox = { field ->
            Row(
                modifier = Modifier
                    .drawBehind {
                        if (ring > 0f) {
                            val spread = 3.dp.toPx()
                            drawRoundRect(
                                color = ringColor.copy(alpha = ringColor.alpha * ring),
                                topLeft = Offset(-spread / 2, -spread / 2),
                                size = Size(size.width + spread, size.height + spread),
                                cornerRadius = CornerRadius((Corners.FIELD.dp.toPx() + spread / 2)),
                                style = Stroke(width = spread),
                            )
                        }
                    }
                    .background(colors.surface, FieldShape)
                    .border(if (focused) 1.5.dp else 1.dp, edge, FieldShape)
                    .heightIn(min = 56.dp)
                    .padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(AppIcons.Link, contentDescription = null, tint = icon, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f).padding(vertical = 14.dp), contentAlignment = Alignment.CenterStart) {
                    if (text.isEmpty()) {
                        Text(
                            text = stringResource(R.string.bar_placeholder),
                            style = AppType.field,
                            color = colors.faint,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    field()
                }
                ClearButton(visible = text.isNotEmpty() && enabled, onClick = onClear)
            }
        },
    )
}

/** The field's clear button: fades and scales in while the field holds text. */
@Composable
private fun ClearButton(visible: Boolean, onClick: () -> Unit) {
    val reduced = LocalReducedMotion.current
    val colors = AppTheme.colors
    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        AnimatedVisibility(
            visible = visible,
            enter = if (reduced) {
                EnterTransition.None
            } else {
                fadeIn(tween(Motion.FAST)) + scaleIn(tween(Motion.SLOW, easing = Motion.EaseOut), initialScale = 0.6f)
            },
            exit = if (reduced) {
                ExitTransition.None
            } else {
                fadeOut(tween(Motion.FAST, easing = Motion.EaseIn)) + scaleOut(tween(Motion.FAST, easing = Motion.EaseIn), targetScale = 0.6f)
            },
        ) {
            IconButton(onClick = onClick) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .background(colors.surfaceSubtle, CapsuleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = AppIcons.Close,
                        contentDescription = stringResource(R.string.bar_clear),
                        tint = colors.ink2,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

/**
 * The main button (B1, B2): a full-width blue capsule with a clipboard icon, under the link field
 * at the bottom of the screen, where the thumb rests. It darkens and dips when pressed. Disabled while a download runs (B4).
 */
@Composable
fun PasteButton(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val container by animateColorAsState(
        targetValue = when {
            !enabled -> colors.surfaceSubtle
            pressed -> colors.primaryFillPressed
            else -> colors.primaryFill
        },
        animationSpec = motion(Motion.FAST),
        label = "paste container",
    )
    val content by animateColorAsState(
        targetValue = if (enabled) colors.onPrimary else colors.faint,
        animationSpec = motion(Motion.BASE),
        label = "paste content",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .tactile(interaction)
            .clip(CapsuleShape)
            .background(container)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 24.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(AppIcons.Paste, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(stringResource(R.string.paste), style = AppType.button, color = content)
    }
}

/**
 * A small capsule button on a card or a sheet: [primary] is filled blue, otherwise it is outlined.
 * 40 dp tall, with a 48 dp touch target.
 */
@Composable
fun ActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = AppTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val container by animateColorAsState(
        targetValue = when {
            !primary -> if (pressed) colors.press else Color.Transparent
            !enabled -> colors.surfaceSubtle
            pressed -> colors.primaryFillPressed
            else -> colors.primaryFill
        },
        animationSpec = motion(Motion.FAST),
        label = "action container",
    )
    val content = when {
        !enabled -> colors.faint
        primary -> colors.onPrimary
        else -> colors.ink
    }
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .tactile(interaction)
            .heightIn(min = 40.dp)
            .clip(CapsuleShape)
            .background(container)
            .then(if (primary) Modifier else Modifier.border(1.dp, colors.borderStrong, CapsuleShape))
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = AppType.buttonSmall, color = content, maxLines = 1)
    }
}

/**
 * A live dot: it breathes (opacity 1 → 0.35, scale 1 → 0.82) every 2.2 s while something runs.
 * Still when [pulse] is false or motion is off.
 */
@Composable
fun PulseDot(color: Color, modifier: Modifier = Modifier, pulse: Boolean = true, size: Dp = 7.dp) {
    if (!pulse || LocalReducedMotion.current) {
        Box(modifier.size(size).background(color, CapsuleShape))
        return
    }
    val transition = rememberInfiniteTransition(label = "pulse")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = Motion.Standard), RepeatMode.Reverse),
        label = "pulse phase",
    )
    Box(
        modifier
            .size(size)
            .graphicsLayer {
                alpha = 1f - 0.65f * phase
                scaleX = 1f - 0.18f * phase
                scaleY = 1f - 0.18f * phase
            }
            .background(color, CapsuleShape),
    )
}
