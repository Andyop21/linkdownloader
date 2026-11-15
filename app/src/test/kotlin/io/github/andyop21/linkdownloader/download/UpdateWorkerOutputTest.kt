package io.github.andyop21.linkdownloader.download

import androidx.work.Data
import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateWorkerOutputTest {

    @Test
    fun `every result round-trips`() {
        listOf(
            EngineUpdateResult.Updated("2026.11.01"),
            EngineUpdateResult.UpToDate("2026.10.26"),
            EngineUpdateResult.Updated(null),
            EngineUpdateResult.UpToDate(null),
            EngineUpdateResult.NotChecked,
        ).forEach { result ->
            assertEquals(result, UpdateWorker.resultOf(UpdateWorker.output(result)))
        }
    }

    @Test
    fun `an output without a result reads as not checked`() {
        assertEquals(EngineUpdateResult.NotChecked, UpdateWorker.resultOf(Data.EMPTY))
    }
}
