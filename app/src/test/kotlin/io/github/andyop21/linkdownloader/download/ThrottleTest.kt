package io.github.andyop21.linkdownloader.download

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ThrottleTest {

    private data class Emission(val value: Int, val at: Long)

    @Test
    fun `a burst gives the first value at once and the last one after the period`() = runTest {
        val seen = mutableListOf<Emission>()
        flow {
            repeat(100) { emit(it) }
        }.throttleLatest(250).collect { seen += Emission(it, currentTime) }

        assertEquals(Emission(0, 0), seen.first())
        assertEquals(99, seen.last().value)
        assertTrue("a burst collapses to a few values, got ${seen.size}", seen.size <= 2)
    }

    @Test
    fun `never more than one value per period, always the latest`() = runTest {
        val seen = mutableListOf<Emission>()
        // One value every 10 ms for 2 s: 200 values.
        flow {
            repeat(200) {
                emit(it)
                delay(10)
            }
        }.throttleLatest(250).collect { seen += Emission(it, currentTime) }

        seen.zipWithNext().forEach { (a, b) ->
            assertTrue("$a then $b are closer than the period", b.at - a.at >= 250)
            assertTrue("values go forward: $a then $b", b.value > a.value)
        }
        // About four per second over 2 s, plus the first and the last.
        assertTrue("got ${seen.size} values", seen.size in 8..10)
        assertEquals(199, seen.last().value)
    }

    @Test
    fun `slow updates pass through unchanged`() = runTest {
        val seen = flow {
            repeat(5) {
                emit(it)
                delay(1_000)
            }
        }.throttleLatest(250).toList()

        assertEquals(listOf(0, 1, 2, 3, 4), seen)
    }

    @Test
    fun `a state flow publishes its latest state after a quiet period`() = runTest {
        val state = MutableStateFlow(0)
        val seen = mutableListOf<Int>()
        val job = launch { state.throttleLatest(250).collect { seen += it } }
        runCurrent()
        state.value = 1
        state.value = 2
        state.value = 3
        advanceTimeBy(100)
        assertEquals(listOf(0), seen)
        advanceTimeBy(200)
        assertEquals(listOf(0, 3), seen)
        job.cancel()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `the period must be positive`() {
        flow { emit(1) }.throttleLatest(0)
    }
}
