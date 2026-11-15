package io.github.andyop21.linkdownloader.download

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.transform

/**
 * Emits the first value at once, then at most one value per [periodMillis], always the latest one.
 * Values that arrive while waiting replace each other, and the last value of the flow is always
 * emitted, so a slow consumer sees fewer updates but never a stale final state.
 */
fun <T> Flow<T>.throttleLatest(periodMillis: Long): Flow<T> {
    require(periodMillis > 0) { "periodMillis must be positive" }
    return conflate().transform { value ->
        emit(value)
        delay(periodMillis)
    }
}
