package io.github.andyop21.linkdownloader.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** The bar and the buttons: fully rounded ends. */
val PillShape = CircleShape

/** The status card. */
val CardShape = RoundedCornerShape(24.dp)

/** The bottom sheets: generously rounded top corners. */
val SheetShape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)

/** Small rounded pieces: thumbnails, badges. */
val TileShape = RoundedCornerShape(16.dp)

/** Material's shape scale, softened throughout. */
internal val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)
