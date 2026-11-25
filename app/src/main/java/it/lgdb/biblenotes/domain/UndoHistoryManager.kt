package it.lgdb.biblenotes.domain

import androidx.compose.ui.text.input.TextFieldValue

enum class EditOperationKind {
    TITLE_INSERT,
    TITLE_DELETE,
    BODY_INSERT,
    BODY_DELETE,
    DISCRETE
}

class UndoHistoryManager<T>(
    private val maxSteps: Int = 60,
    private val pauseCoalesceMs: Long = 700L,
    private val maxGroupDurationMs: Long = 4000L
) {
    private data class ActiveTypingGroup(
        val kind: EditOperationKind,
        val groupStartMs: Long,
        val lastEditMs: Long,
        val expectedNextCursor: Int?
    )

    private val undoStack = ArrayDeque<T>()
    private val redoStack = ArrayDeque<T>()
    private var activeGroup: ActiveTypingGroup? = null

    val canUndo: Boolean
        get() = undoStack.isNotEmpty()

    val canRedo: Boolean
        get() = redoStack.isNotEmpty()

    fun clear() {
        undoStack.clear()
        redoStack.clear()
        activeGroup = null
    }

    fun onCursorMoved() {
        activeGroup = null
    }

    fun recordBeforeEdit(
        snapshotBeforeEdit: T,
        kind: EditOperationKind,
        nowMs: Long,
        editOffset: Int? = null,
        nextExpectedCursor: Int? = null
    ) {
        redoStack.clear()

        val group = activeGroup
        val shouldCoalesce = group != null &&
            kind != EditOperationKind.DISCRETE &&
            group.kind == kind &&
            (nowMs - group.lastEditMs) < pauseCoalesceMs &&
            (nowMs - group.groupStartMs) < maxGroupDurationMs &&
            (editOffset == null || group.expectedNextCursor == null || editOffset == group.expectedNextCursor)

        if (shouldCoalesce && group != null) {
            activeGroup = group.copy(
                lastEditMs = nowMs,
                expectedNextCursor = nextExpectedCursor
            )
            return
        }

        undoStack.addLast(snapshotBeforeEdit)
        while (undoStack.size > maxSteps) {
            undoStack.removeFirst()
        }

        activeGroup = if (kind == EditOperationKind.DISCRETE) {
            null
        } else {
            ActiveTypingGroup(
                kind = kind,
                groupStartMs = nowMs,
                lastEditMs = nowMs,
                expectedNextCursor = nextExpectedCursor
            )
        }
    }

    fun undo(currentSnapshot: T): T? {
        if (undoStack.isEmpty()) return null
        redoStack.addLast(currentSnapshot)
        activeGroup = null
        return undoStack.removeLast()
    }

    fun redo(currentSnapshot: T): T? {
        if (redoStack.isEmpty()) return null
        undoStack.addLast(currentSnapshot)
        activeGroup = null
        return redoStack.removeLast()
    }

    companion object {
        fun classifyTitleEdit(oldTitle: String, newTitle: String): Triple<EditOperationKind, Int?, Int?> {
            if (newTitle.length == oldTitle.length + 1) {
                val editIdx = findSingleEditIndex(shorter = oldTitle, longer = newTitle)
                if (editIdx != null) {
                    return Triple(EditOperationKind.TITLE_INSERT, editIdx, editIdx + 1)
                }
            } else if (oldTitle.length == newTitle.length + 1) {
                val editIdx = findSingleEditIndex(shorter = newTitle, longer = oldTitle)
                if (editIdx != null) {
                    return Triple(EditOperationKind.TITLE_DELETE, editIdx + 1, editIdx)
                }
            }
            return Triple(EditOperationKind.DISCRETE, null, null)
        }

        fun classifyBodyEdit(
            oldValue: TextFieldValue,
            newValue: TextFieldValue
        ): Triple<EditOperationKind, Int?, Int?> {
            if (!oldValue.selection.collapsed) {
                return Triple(EditOperationKind.DISCRETE, null, null)
            }
            val oldCursor = oldValue.selection.start
            val oldText = oldValue.text
            val newText = newValue.text

            if (newText.length == oldText.length + 1) {
                val editIdx = findSingleEditIndex(shorter = oldText, longer = newText)
                if (editIdx != null) {
                    return Triple(
                        EditOperationKind.BODY_INSERT,
                        oldCursor,
                        newValue.selection.start
                    )
                }
            } else if (oldText.length == newText.length + 1) {
                val editIdx = findSingleEditIndex(shorter = newText, longer = oldText)
                if (editIdx != null) {
                    return Triple(
                        EditOperationKind.BODY_DELETE,
                        oldCursor,
                        newValue.selection.start
                    )
                }
            }

            return Triple(EditOperationKind.DISCRETE, null, null)
        }

        private fun findSingleEditIndex(shorter: String, longer: String): Int? {
            if (longer.length != shorter.length + 1) return null
            var prefix = 0
            while (prefix < shorter.length && shorter[prefix] == longer[prefix]) {
                prefix++
            }
            val suffixMatches = shorter.regionMatches(
                thisOffset = prefix,
                other = longer,
                otherOffset = prefix + 1,
                length = shorter.length - prefix
            )
            return if (suffixMatches) prefix else null
        }
    }
}
