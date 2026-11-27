package it.lgdb.biblenotes

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import it.lgdb.biblenotes.ui.editor.calculateSelectionPopupPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorSelectionToolbarTest {

    private val windowSize = IntSize(width = 1080, height = 2400)
    private val containerBounds = Rect(left = 48f, top = 420f, right = 1032f, bottom = 1480f)
    private val popupSize = IntSize(width = 760, height = 144)
    private val edgeMarginPx = 36f
    private val gapAbovePx = 30f
    private val gapBelowHandlePx = 84f
    private val verticalInsetPx = 12f

    @Test
    fun `positions toolbar centered above selection when ample vertical and horizontal space exist`() {
        val selection = Rect(left = 420f, top = 800f, right = 660f, bottom = 860f)

        val offset = calculateSelectionPopupPosition(
            selectionRectInWindow = selection,
            containerBoundsInWindow = containerBounds,
            windowSize = windowSize,
            popupContentSize = popupSize,
            horizontalEdgeMarginPx = edgeMarginPx,
            verticalGapAbovePx = gapAbovePx,
            verticalGapBelowHandlePx = gapBelowHandlePx,
            verticalEdgeInsetPx = verticalInsetPx
        )

        assertEquals(160, offset.x) // center 540 - 380
        assertEquals(626, offset.y) // 800 - 144 - 30
    }

    @Test
    fun `clamps horizontally inside body left and right margins`() {
        val nearLeftSelection = Rect(left = 50f, top = 800f, right = 130f, bottom = 860f)
        val leftOffset = calculateSelectionPopupPosition(
            selectionRectInWindow = nearLeftSelection,
            containerBoundsInWindow = containerBounds,
            windowSize = windowSize,
            popupContentSize = popupSize,
            horizontalEdgeMarginPx = edgeMarginPx,
            verticalGapAbovePx = gapAbovePx,
            verticalGapBelowHandlePx = gapBelowHandlePx,
            verticalEdgeInsetPx = verticalInsetPx
        )
        assertEquals(48, leftOffset.x)

        val nearRightSelection = Rect(left = 940f, top = 800f, right = 1020f, bottom = 860f)
        val rightOffset = calculateSelectionPopupPosition(
            selectionRectInWindow = nearRightSelection,
            containerBoundsInWindow = containerBounds,
            windowSize = windowSize,
            popupContentSize = popupSize,
            horizontalEdgeMarginPx = edgeMarginPx,
            verticalGapAbovePx = gapAbovePx,
            verticalGapBelowHandlePx = gapBelowHandlePx,
            verticalEdgeInsetPx = verticalInsetPx
        )
        assertEquals(272, rightOffset.x) // 1032 - 760
    }

    @Test
    fun `flips below selection clearing handles when selection is near top edge`() {
        val topLineSelection = Rect(left = 300f, top = 430f, right = 600f, bottom = 490f)

        val offset = calculateSelectionPopupPosition(
            selectionRectInWindow = topLineSelection,
            containerBoundsInWindow = containerBounds,
            windowSize = windowSize,
            popupContentSize = popupSize,
            horizontalEdgeMarginPx = edgeMarginPx,
            verticalGapAbovePx = gapAbovePx,
            verticalGapBelowHandlePx = gapBelowHandlePx,
            verticalEdgeInsetPx = verticalInsetPx
        )

        assertEquals(574, offset.y) // 490 + 84
        assertTrue(offset.y + popupSize.height <= containerBounds.bottom - verticalInsetPx)
    }

    @Test
    fun `pins inside top container margin when multi-line selection spans entire body viewport`() {
        val fullViewportSelection = Rect(left = 48f, top = 430f, right = 1032f, bottom = 1460f)

        val offset = calculateSelectionPopupPosition(
            selectionRectInWindow = fullViewportSelection,
            containerBoundsInWindow = containerBounds,
            windowSize = windowSize,
            popupContentSize = popupSize,
            horizontalEdgeMarginPx = edgeMarginPx,
            verticalGapAbovePx = gapAbovePx,
            verticalGapBelowHandlePx = gapBelowHandlePx,
            verticalEdgeInsetPx = verticalInsetPx
        )

        val expectedMinY = (containerBounds.top + verticalInsetPx).toInt()
        assertEquals(expectedMinY, offset.y)
        assertTrue(offset.x >= containerBounds.left.toInt())
        assertTrue(offset.x + popupSize.width <= containerBounds.right.toInt())
    }

    @Test
    fun `selection toolbar controller stays hidden for collapsed cursor and opens only on text selection`() {
        val controller = it.lgdb.biblenotes.ui.editor.EditorSelectionToolbarController()
        val rect = Rect(left = 100f, top = 500f, right = 102f, bottom = 540f)

        // Collapsed cursor: only paste/selectAll offered -> must stay hidden
        controller.showMenu(
            rect = rect,
            onCopyRequested = null,
            onPasteRequested = {},
            onCutRequested = null,
            onSelectAllRequested = {}
        )
        assertEquals(
            androidx.compose.ui.platform.TextToolbarStatus.Hidden,
            controller.status
        )

        // Text selected: copy/cut offered -> shown
        controller.showMenu(
            rect = rect,
            onCopyRequested = {},
            onPasteRequested = {},
            onCutRequested = {},
            onSelectAllRequested = {}
        )
        assertEquals(
            androidx.compose.ui.platform.TextToolbarStatus.Shown,
            controller.status
        )
    }

    @Test
    fun `isSelectionRectVisibleInViewport returns false when selection is scrolled above or below viewport`() {
        val visible = Rect(left = 100f, top = 700f, right = 400f, bottom = 760f)
        val aboveViewport = Rect(left = 100f, top = 200f, right = 400f, bottom = 410f)
        val belowViewport = Rect(left = 100f, top = 1490f, right = 400f, bottom = 1540f)
        val partiallyVisible = Rect(left = 100f, top = 400f, right = 400f, bottom = 460f)

        assertTrue(it.lgdb.biblenotes.ui.editor.isSelectionRectVisibleInViewport(visible, containerBounds))
        assertTrue(it.lgdb.biblenotes.ui.editor.isSelectionRectVisibleInViewport(partiallyVisible, containerBounds))
        assertTrue(!it.lgdb.biblenotes.ui.editor.isSelectionRectVisibleInViewport(aboveViewport, containerBounds))
        assertTrue(!it.lgdb.biblenotes.ui.editor.isSelectionRectVisibleInViewport(belowViewport, containerBounds))
        assertTrue(!it.lgdb.biblenotes.ui.editor.isSelectionRectVisibleInViewport(Rect.Zero, containerBounds))
    }
}
