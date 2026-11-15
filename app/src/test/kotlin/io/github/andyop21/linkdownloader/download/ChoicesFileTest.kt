package io.github.andyop21.linkdownloader.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ChoicesFileTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val list = ChoiceList(
        title = "A post",
        choices = listOf(
            VideoChoice(0, "Video 1", "https://example.com/thumb/1.jpg", 12.5),
            VideoChoice(1, "Video 2", null, null),
        ),
    )

    @Test
    fun `a written list reads back the same`() {
        val file = ChoicesFile.write(temp.root, "job-1", list)
        assertEquals(list, ChoicesFile.read(file.absolutePath))
    }

    @Test
    fun `writing a list deletes the older ones`() {
        val old = ChoicesFile.write(temp.root, "job-1", list)
        val new = ChoicesFile.write(temp.root, "job-2", list.copy(title = "Another post"))
        assertFalse(old.exists())
        assertEquals("Another post", ChoicesFile.read(new.absolutePath)?.title)
    }

    @Test
    fun `a missing or broken file reads as null`() {
        assertNull(ChoicesFile.read(File(temp.root, "gone.json").absolutePath))
        val broken = temp.newFile("broken.json").apply { writeText("{not json") }
        assertNull(ChoicesFile.read(broken.absolutePath))
    }
}
