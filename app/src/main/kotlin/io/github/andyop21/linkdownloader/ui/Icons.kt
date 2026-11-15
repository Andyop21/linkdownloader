package io.github.andyop21.linkdownloader.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** The app's two small line icons, drawn here so no icon library is needed. Tinted by `Icon`. */
object AppIcons {

    /** A clipboard with a down arrow, for **Paste**. */
    val Paste: ImageVector = lineIcon(
        name = "Paste",
        "M9,4H8A3,3 0 0,0 5,7V18A3,3 0 0,0 8,21H16A3,3 0 0,0 19,18V7A3,3 0 0,0 16,4H15",
        "M10.5,2.5H13.5A1.5,1.5 0 0,1 15,4V4.5A1.5,1.5 0 0,1 13.5,6H10.5A1.5,1.5 0 0,1 9,4.5V4" +
            "A1.5,1.5 0 0,1 10.5,2.5Z",
        "M12,10V16M9.5,13.5L12,16L14.5,13.5",
    )

    /** Two chain links, for the bar. */
    val Link: ImageVector = lineIcon(
        name = "Link",
        "M10,13.5A4,4 0 0,0 15.66,13.5L18.5,10.66A4,4 0 0,0 12.84,5L11.5,6.34",
        "M14,10.5A4,4 0 0,0 8.34,10.5L5.5,13.34A4,4 0 0,0 11.16,19L12.5,17.66",
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
