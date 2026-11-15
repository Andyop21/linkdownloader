package io.github.andyop21.linkdownloader.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * One corner scale (6, 8, 10, 12, 14, 18 dp), every corner a circular arc, and a capsule for
 * buttons and badges. A shape nested inside another at an equal inset takes the outer radius less
 * the inset, so the two curves run parallel.
 */
object Corners {
    const val BADGE = 6
    const val TILE = 10
    const val FIELD = 12
    const val CARD = 14
    const val SHEET = 18

    /** The radius of a shape nested at [inset] inside one of radius [outer], never below zero. */
    fun inner(outer: Int, inset: Int): Int = (outer - inset).coerceAtLeast(0)
}

/** Buttons and small marks: fully rounded ends. */
val CapsuleShape = CircleShape

/** The link field. */
val FieldShape = RoundedCornerShape(Corners.FIELD.dp)

/** Grouped lists and the snackbar. */
val CardShape = RoundedCornerShape(Corners.CARD.dp)

/** The bottom sheets' top corners. */
val SheetShape = RoundedCornerShape(topStart = Corners.SHEET.dp, topEnd = Corners.SHEET.dp)

/** Icon tiles and thumbnails. */
val TileShape = RoundedCornerShape(Corners.TILE.dp)

/** A thumbnail's length badge, nested 4 dp inside its tile. */
val BadgeShape = RoundedCornerShape(Corners.inner(Corners.TILE, 4).dp)

/** Material's shape scale on the same corners. */
internal val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(Corners.BADGE.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(Corners.TILE.dp),
    large = RoundedCornerShape(Corners.CARD.dp),
    extraLarge = RoundedCornerShape(Corners.SHEET.dp),
)
