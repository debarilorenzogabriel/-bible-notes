package it.lgdb.biblenotes.domain

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

class DraftEpochCoordinator {
    private val mutex = Mutex()
    private val epochs = ConcurrentHashMap<String, Long>()

    fun currentEpoch(key: String): Long = epochs[key] ?: 0L

    fun invalidateEpoch(key: String): Long =
        epochs.compute(key) { _, prev -> (prev ?: 0L) + 1L } ?: 1L

    suspend fun writeDraftIfEpochCurrent(
        key: String,
        expectedEpoch: Long,
        performWrite: suspend () -> Unit
    ): Boolean = mutex.withLock {
        if (currentEpoch(key) != expectedEpoch) {
            return@withLock false
        }
        performWrite()
        true
    }

    suspend fun <T> invalidateAndExecuteExclusive(
        key: String,
        performOperation: suspend () -> T
    ): T {
        invalidateEpoch(key)
        return mutex.withLock {
            performOperation()
        }
    }
}
