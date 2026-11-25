package it.lgdb.biblenotes

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import it.lgdb.biblenotes.domain.EditOperationKind
import it.lgdb.biblenotes.domain.UndoHistoryManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UndoHistoryManagerTest {

    private data class DocState(
        val title: String,
        val body: TextFieldValue
    )

    private fun applyTitle(
        history: UndoHistoryManager<DocState>,
        state: DocState,
        newTitle: String,
        nowMs: Long
    ): DocState {
        val (kind, offset, nextCursor) = UndoHistoryManager.classifyTitleEdit(state.title, newTitle)
        history.recordBeforeEdit(state, kind, nowMs, offset, nextCursor)
        return state.copy(title = newTitle)
    }

    private fun applyBody(
        history: UndoHistoryManager<DocState>,
        state: DocState,
        newBody: TextFieldValue,
        nowMs: Long
    ): DocState {
        val (kind, offset, nextCursor) = UndoHistoryManager.classifyBodyEdit(state.body, newBody)
        history.recordBeforeEdit(state, kind, nowMs, offset, nextCursor)
        return state.copy(body = newBody)
    }

    @Test
    fun `splits undo groups across pause, max group duration, title-body switch, op switch, cursor jump, and selection replace`() {
        val history = UndoHistoryManager<DocState>(
            maxSteps = 60,
            pauseCoalesceMs = 700L,
            maxGroupDurationMs = 4000L
        )
        var state = DocState(title = "", body = TextFieldValue("", TextRange(0)))
        var t = 1000L

        // 1. Type "Hi" in title continuously (<700ms) -> 1 group
        state = applyTitle(history, state, "H", t)
        t += 150L
        state = applyTitle(history, state, "Hi", t)

        // 2. Switch immediately to body without pause -> MUST start new group
        t += 100L
        state = applyBody(history, state, TextFieldValue("A", TextRange(1)), t)
        t += 100L
        state = applyBody(history, state, TextFieldValue("Ab", TextRange(2)), t)
        t += 100L
        state = applyBody(history, state, TextFieldValue("Abc", TextRange(3)), t)

        // 3. Switch operation from insert to delete immediately -> MUST start new group
        t += 100L
        state = applyBody(history, state, TextFieldValue("Ab", TextRange(2)), t)

        // 4. Move cursor to 0 and insert "X" -> MUST start new group
        t += 100L
        history.onCursorMoved()
        val moved = state.copy(body = TextFieldValue("Ab", TextRange(0)))
        state = applyBody(history, moved, TextFieldValue("XAb", TextRange(1)), t)

        // 5. Select "XA" (0..2) and replace with "Z" -> MUST start new group
        t += 100L
        val selected = state.copy(body = TextFieldValue("XAb", TextRange(0, 2)))
        state = applyBody(history, selected, TextFieldValue("Zb", TextRange(1)), t)

        // 6. Pause >= 700ms before typing "!" -> MUST start new group
        t += 750L
        state = applyBody(history, state, TextFieldValue("Zb!", TextRange(3)), t)

        // Verify undo steps unwind in exact reverse group order:
        state = history.undo(state)!!
        assertEquals("Zb", state.body.text)

        state = history.undo(state)!!
        assertEquals("XAb", state.body.text)

        state = history.undo(state)!!
        assertEquals("Ab", state.body.text)

        state = history.undo(state)!!
        assertEquals("Abc", state.body.text)

        state = history.undo(state)!!
        assertEquals("", state.body.text)
        assertEquals("Hi", state.title)

        state = history.undo(state)!!
        assertEquals("", state.title)
        assertFalse(history.canUndo)

        // Redo back one step
        assertTrue(history.canRedo)
        state = history.redo(state)!!
        assertEquals("Hi", state.title)
    }

    @Test
    fun `continuous typing exceeding max group duration starts a new undo group`() {
        val history = UndoHistoryManager<DocState>(
            maxSteps = 60,
            pauseCoalesceMs = 700L,
            maxGroupDurationMs = 4000L
        )
        var state = DocState("", TextFieldValue("", TextRange(0)))
        var t = 0L

        // Type 25 characters every 200ms (0ms..4800ms), never pausing >= 700ms
        for (i in 1..25) {
            val nextText = state.body.text + ('a' + (i - 1) % 26)
            state = applyBody(
                history = history,
                state = state,
                newBody = TextFieldValue(nextText, TextRange(nextText.length)),
                nowMs = t
            )
            t += 200L
        }

        // Group 1 started at t=0; at t=4000 (char 21) maxGroupDurationMs=4000 triggers Group 2
        val afterFirstUndo = history.undo(state)!!
        assertEquals(20, afterFirstUndo.body.text.length)

        val afterSecondUndo = history.undo(afterFirstUndo)!!
        assertEquals(0, afterSecondUndo.body.text.length)
        assertFalse(history.canUndo)
    }

    @Test
    fun `discrete operation splits surrounding typing groups`() {
        val history = UndoHistoryManager<DocState>()
        var state = DocState("", TextFieldValue("", TextRange(0)))
        var t = 500L

        state = applyBody(history, state, TextFieldValue("a", TextRange(1)), t)
        t += 100L
        state = applyBody(history, state, TextFieldValue("ab", TextRange(2)), t)
        t += 100L

        history.recordBeforeEdit(state, EditOperationKind.DISCRETE, t)
        state = state.copy(body = TextFieldValue("ab Giovanni 3:16 ", TextRange(17)))

        t += 100L
        state = applyBody(history, state, TextFieldValue("ab Giovanni 3:16 !", TextRange(18)), t)

        state = history.undo(state)!!
        assertEquals("ab Giovanni 3:16 ", state.body.text)
        state = history.undo(state)!!
        assertEquals("ab", state.body.text)
        state = history.undo(state)!!
        assertEquals("", state.body.text)
    }
}
