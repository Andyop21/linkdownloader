package io.github.andyop21.linkdownloader.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DurationTextTest {

    @Test
    fun `short videos read as minutes and seconds`() {
        assertEquals("0:00", durationText(0.0))
        assertEquals("0:08", durationText(8.0))
        assertEquals("0:08", durationText(7.6))
        assertEquals("1:05", durationText(65.0))
        assertEquals("59:59", durationText(3599.0))
    }

    @Test
    fun `long videos add the hours`() {
        assertEquals("1:00:00", durationText(3600.0))
        assertEquals("1:02:03", durationText(3723.0))
    }

    @Test
    fun `an unknown or impossible length gives null`() {
        assertNull(durationText(null))
        assertNull(durationText(-1.0))
        assertNull(durationText(Double.NaN))
        assertNull(durationText(Double.POSITIVE_INFINITY))
    }
}
