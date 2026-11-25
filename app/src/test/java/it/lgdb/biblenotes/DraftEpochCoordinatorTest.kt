package it.lgdb.biblenotes

import it.lgdb.biblenotes.domain.DraftEpochCoordinator
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DraftEpochCoordinatorTest {

    @Test
    fun `stale draft write cannot recreate draft after save or discard`() = runBlocking {
        val coordinator = DraftEpochCoordinator()
        val key = "note-1"
        val dbDrafts = mutableMapOf<String, String>()

        val staleEpoch = coordinator.currentEpoch(key)
        val writeStarted = CompletableDeferred<Unit>()
        val allowWriteToFinish = CompletableDeferred<Unit>()

        // Simulate an autosave/flush SQLite write already inside the critical section
        val inFlightWrite = async {
            coordinator.writeDraftIfEpochCurrent(key, staleEpoch) {
                writeStarted.complete(Unit)
                allowWriteToFinish.await()
                dbDrafts[key] = "stale draft text"
            }
        }

        // Wait until the background draft write has entered its critical section
        writeStarted.await()

        // User taps Save or Discard while that write is already in flight
        val saveOrDiscard = async {
            coordinator.invalidateAndExecuteExclusive(key) {
                dbDrafts.remove(key)
            }
        }

        // Also queue another older autosave with the same stale epoch behind the lock
        val queuedStaleWrite = async {
            coordinator.writeDraftIfEpochCurrent(key, staleEpoch) {
                dbDrafts[key] = "even older draft text"
            }
        }

        allowWriteToFinish.complete(Unit)

        assertTrue(inFlightWrite.await())
        saveOrDiscard.await()
        assertFalse(queuedStaleWrite.await())

        // Neither the in-flight write nor the queued stale write may leave a draft behind
        assertNull(dbDrafts[key])
        assertEquals(1L, coordinator.currentEpoch(key))
    }
}
