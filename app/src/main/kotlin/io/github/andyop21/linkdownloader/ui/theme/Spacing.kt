package io.github.andyop21.linkdownloader.ui.theme

import androidx.compose.ui.unit.dp

/**
 * The spacing scale, on a 4 dp grid: every gap and padding on the screen is one of these, so the
 * rhythm stays even.
 */
object Space {
    /** Between a title and its detail line. */
    val xs = 4.dp

    /** Between buttons in a row. */
    val s = 8.dp

    /** Between the link field and the Paste button. */
    val m = 12.dp

    /** Inside rows and lists. */
    val l = 16.dp

    /** The page's side margins; between the caption and its buttons. */
    val xl = 24.dp

    /** Between the orb and its caption. */
    val xxl = 32.dp

    /** The least free space kept above and below the hero. */
    val xxxl = 48.dp

    /** The height of the title bar at the top. */
    val bar = 56.dp
}
