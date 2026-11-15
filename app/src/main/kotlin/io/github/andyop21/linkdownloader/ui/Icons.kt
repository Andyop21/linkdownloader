package io.github.andyop21.linkdownloader.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The app's line icons, drawn here so no icon library is needed: 24 dp, 2 dp round strokes,
 * tinted by `Icon`.
 */
object AppIcons {

    /** A clipboard with a down arrow, for **Paste link**. */
    val Paste: ImageVector = lineIcon(
        name = "Paste",
        "M9,4H8A3,3 0 0,0 5,7V18A3,3 0 0,0 8,21H16A3,3 0 0,0 19,18V7A3,3 0 0,0 16,4H15",
        "M10.5,2.5H13.5A1.5,1.5 0 0,1 15,4V4.5A1.5,1.5 0 0,1 13.5,6H10.5A1.5,1.5 0 0,1 9,4.5V4" +
            "A1.5,1.5 0 0,1 10.5,2.5Z",
        "M12,10V16M9.5,13.5L12,16L14.5,13.5",
    )

    /** Two chain links: the link field and the idle status. */
    val Link: ImageVector = lineIcon(
        name = "Link",
        "M10,13.5A4,4 0 0,0 15.66,13.5L18.5,10.66A4,4 0 0,0 12.84,5L11.5,6.34",
        "M14,10.5A4,4 0 0,0 8.34,10.5L5.5,13.34A4,4 0 0,0 11.16,19L12.5,17.66",
    )

    /** An arrow into a tray: a download running. */
    val Download: ImageVector = lineIcon(
        name = "Download",
        "M12,4V14.5M7.5,10L12,14.5L16.5,10",
        "M5,19.5H19",
    )

    /** A tick: saved. */
    val Check: ImageVector = lineIcon(
        name = "Check",
        "M5,12.5L9.75,17.25L19,7.5",
    )

    /** An exclamation mark in a circle: a failure. */
    val Alert: ImageVector = lineIcon(
        name = "Alert",
        "M21,12A9,9 0 1,1 3,12A9,9 0 1,1 21,12Z",
        "M12,7.5V12.5",
        "M12,16.25V16.5",
    )

    /** A question in a clipboard: nothing to paste. */
    val Empty: ImageVector = lineIcon(
        name = "Empty",
        "M9,4H8A3,3 0 0,0 5,7V18A3,3 0 0,0 8,21H16A3,3 0 0,0 19,18V7A3,3 0 0,0 16,4H15",
        "M10.5,2.5H13.5A1.5,1.5 0 0,1 15,4V4.5A1.5,1.5 0 0,1 13.5,6H10.5A1.5,1.5 0 0,1 9,4.5V4" +
            "A1.5,1.5 0 0,1 10.5,2.5Z",
        "M9,12.5H15",
    )

    /** Four tiles: a post with several videos. */
    val Grid: ImageVector = lineIcon(
        name = "Grid",
        "M5.5,4H9.5A1.5,1.5 0 0,1 11,5.5V9.5A1.5,1.5 0 0,1 9.5,11H5.5A1.5,1.5 0 0,1 4,9.5V5.5A1.5,1.5 0 0,1 5.5,4Z",
        "M14.5,4H18.5A1.5,1.5 0 0,1 20,5.5V9.5A1.5,1.5 0 0,1 18.5,11H14.5A1.5,1.5 0 0,1 13,9.5V5.5A1.5,1.5 0 0,1 14.5,4Z",
        "M5.5,13H9.5A1.5,1.5 0 0,1 11,14.5V18.5A1.5,1.5 0 0,1 9.5,20H5.5A1.5,1.5 0 0,1 4,18.5V14.5A1.5,1.5 0 0,1 5.5,13Z",
        "M14.5,13H18.5A1.5,1.5 0 0,1 20,14.5V18.5A1.5,1.5 0 0,1 18.5,20H14.5A1.5,1.5 0 0,1 13,18.5V14.5A1.5,1.5 0 0,1 14.5,13Z",
    )

    /** A cross: clear the link field. */
    val Close: ImageVector = lineIcon(
        name = "Close",
        "M7,7L17,17M17,7L7,17",
    )

    private fun lineIcon(name: String, vararg paths: String): ImageVector {
        val builder = ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
        for (path in paths) {
            builder.addPath(
                pathData = addPathNodes(path),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return builder.build()
    }
}
