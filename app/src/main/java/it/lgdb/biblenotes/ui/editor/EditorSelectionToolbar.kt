package it.lgdb.biblenotes.ui.editor

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuItem
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuKeys
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuSession
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuDataProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuProvider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatClear
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import it.lgdb.biblenotes.domain.AppStrings
import it.lgdb.biblenotes.model.AppLanguage
import it.lgdb.biblenotes.model.HighlightColor
import kotlin.math.roundToInt
import kotlinx.coroutines.channels.Channel

data class EditorSelectionMenuState(
    val isVisible: Boolean = false,
    val selectionRectInWindow: Rect = Rect.Zero,
    val onCopyRequested: (() -> Unit)? = null,
    val onPasteRequested: (() -> Unit)? = null,
    val onCutRequested: (() -> Unit)? = null,
    val onSelectAllRequested: (() -> Unit)? = null
)

@Stable
class EditorSelectionToolbarController : TextToolbar {
    var menuState by mutableStateOf(EditorSelectionMenuState())
        private set

    var showColorPalette by mutableStateOf(false)

    override val status: TextToolbarStatus
        get() = if (menuState.isVisible) TextToolbarStatus.Shown else TextToolbarStatus.Hidden

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?
    ) {
        val hasSelectionAction = onCopyRequested != null || onCutRequested != null
        if (!hasSelectionAction) {
            hide()
            return
        }
        if (!menuState.isVisible) {
            showColorPalette = false
        }
        menuState = EditorSelectionMenuState(
            isVisible = true,
            selectionRectInWindow = rect,
            onCopyRequested = onCopyRequested,
            onPasteRequested = onPasteRequested,
            onCutRequested = onCutRequested,
            onSelectAllRequested = onSelectAllRequested
        )
    }

    override fun hide() {
        if (menuState.isVisible || showColorPalette) {
            menuState = menuState.copy(isVisible = false)
            showColorPalette = false
        }
    }
}

@Stable
class EditorTextContextMenuProvider : TextContextMenuProvider {
    data class ActiveContextMenuSession(
        val dataProvider: TextContextMenuDataProvider,
        val session: TextContextMenuSession
    )

    var activeSession by mutableStateOf<ActiveContextMenuSession?>(null)
        private set

    var showColorPalette by mutableStateOf(false)

    override suspend fun showTextContextMenu(dataProvider: TextContextMenuDataProvider) {
        activeSession?.session?.close()
        showColorPalette = false
        val closeChannel = Channel<Unit>(Channel.CONFLATED)
        val session = object : TextContextMenuSession {
            override fun close() {
                closeChannel.trySend(Unit)
            }
        }
        val active = ActiveContextMenuSession(dataProvider = dataProvider, session = session)
        activeSession = active
        try {
            closeChannel.receive()
        } finally {
            if (activeSession === active) {
                activeSession = null
                showColorPalette = false
            }
        }
    }

    fun cancel() {
        activeSession?.session?.close()
        activeSession = null
        showColorPalette = false
    }
}

internal fun calculateSelectionPopupPosition(
    selectionRectInWindow: Rect,
    containerBoundsInWindow: Rect,
    windowSize: IntSize,
    popupContentSize: IntSize,
    horizontalEdgeMarginPx: Float,
    verticalGapAbovePx: Float,
    verticalGapBelowHandlePx: Float,
    verticalEdgeInsetPx: Float
): IntOffset {
    val windowWidth = windowSize.width.toFloat().coerceAtLeast(1f)
    val windowHeight = windowSize.height.toFloat().coerceAtLeast(1f)
    val hasContainer = containerBoundsInWindow.width > 0f && containerBoundsInWindow.height > 0f
    val containerFitsPopup = hasContainer &&
        (containerBoundsInWindow.right - containerBoundsInWindow.left) >= popupContentSize.width

    val leftLimit = if (containerFitsPopup) {
        maxOf(horizontalEdgeMarginPx, containerBoundsInWindow.left)
    } else {
        horizontalEdgeMarginPx
    }
    val rightLimit = if (containerFitsPopup) {
        minOf(windowWidth - horizontalEdgeMarginPx, containerBoundsInWindow.right)
    } else {
        windowWidth - horizontalEdgeMarginPx
    }
    val maxX = (rightLimit - popupContentSize.width).coerceAtLeast(leftLimit)
    val desiredX = ((selectionRectInWindow.left + selectionRectInWindow.right) / 2f) -
        (popupContentSize.width / 2f)
    val x = desiredX.coerceIn(leftLimit, maxX).roundToInt()

    val topLimit = if (hasContainer) {
        maxOf(verticalEdgeInsetPx, containerBoundsInWindow.top + verticalEdgeInsetPx)
    } else {
        verticalEdgeInsetPx
    }
    val bottomLimit = if (hasContainer) {
        minOf(windowHeight - verticalEdgeInsetPx, containerBoundsInWindow.bottom - verticalEdgeInsetPx)
    } else {
        windowHeight - verticalEdgeInsetPx
    }
    val maxY = (bottomLimit - popupContentSize.height).coerceAtLeast(topLimit)

    val aboveY = selectionRectInWindow.top - popupContentSize.height - verticalGapAbovePx
    val belowY = selectionRectInWindow.bottom + verticalGapBelowHandlePx
    val preferredY = when {
        aboveY >= topLimit -> aboveY
        belowY <= maxY -> belowY
        else -> topLimit
    }
    val y = preferredY.coerceIn(topLimit, maxY).roundToInt()
    return IntOffset(x = x, y = y)
}

internal fun isSelectionRectVisibleInViewport(
    selectionRectInWindow: Rect,
    containerBoundsInWindow: Rect
): Boolean {
    if (containerBoundsInWindow.width <= 0f || containerBoundsInWindow.height <= 0f) return false
    if (selectionRectInWindow == Rect.Zero) return false
    val horizontalOverlap = selectionRectInWindow.right > containerBoundsInWindow.left &&
        selectionRectInWindow.left < containerBoundsInWindow.right
    val verticalOverlap = selectionRectInWindow.bottom > containerBoundsInWindow.top &&
        selectionRectInWindow.top < containerBoundsInWindow.bottom
    return horizontalOverlap && verticalOverlap
}

@Composable
fun FloatingEditorContextMenu(
    provider: EditorTextContextMenuProvider,
    anchorCoordinates: LayoutCoordinates?,
    bodyBoundsInWindow: Rect,
    hasTextSelection: Boolean,
    isSelectionVisibleInViewport: Boolean = true,
    isScrolling: Boolean = false,
    isSelectionBold: Boolean = false,
    isSelectionItalic: Boolean = false,
    hasConvertibleBibleRef: Boolean = false,
    language: AppLanguage,
    strings: AppStrings,
    onApplyHighlight: (HighlightColor?) -> Unit,
    onToggleBold: () -> Unit = {},
    onToggleItalic: () -> Unit = {},
    onAddLink: () -> Unit,
    onConvertSelectionRef: () -> Unit = {}
) {
    if (isScrolling) return
    val active = provider.activeSession ?: return
    val items = active.dataProvider.data().components.filterIsInstance<TextContextMenuItem>()
    val cutItem = items.firstOrNull { it.key == TextContextMenuKeys.CutKey }
    val copyItem = items.firstOrNull { it.key == TextContextMenuKeys.CopyKey }
    val pasteItem = items.firstOrNull { it.key == TextContextMenuKeys.PasteKey }
    val selectAllItem = items.firstOrNull { it.key == TextContextMenuKeys.SelectAllKey }

    val selectionInViewport = if (isSelectionVisibleInViewport && anchorCoordinates != null && anchorCoordinates.isAttached) {
        val localBounds = active.dataProvider.contentBounds(anchorCoordinates)
        if (localBounds != Rect.Zero) {
            val windowOrigin = anchorCoordinates.localToWindow(Offset.Zero)
            isSelectionRectVisibleInViewport(localBounds.translate(windowOrigin), bodyBoundsInWindow)
        } else {
            true
        }
    } else {
        isSelectionVisibleInViewport
    }

    if (!hasTextSelection || !selectionInViewport || (cutItem == null && copyItem == null)) {
        LaunchedEffect(active, hasTextSelection, selectionInViewport) {
            active.session.close()
        }
        return
    }

    val density = LocalDensity.current
    val horizontalEdgeMarginPx = with(density) { 12.dp.toPx() }
    val verticalGapAbovePx = with(density) { 10.dp.toPx() }
    val verticalGapBelowHandlePx = with(density) { 28.dp.toPx() }
    val verticalEdgeInsetPx = with(density) { 4.dp.toPx() }

    val positionProvider = remember(
        active,
        anchorCoordinates,
        bodyBoundsInWindow,
        horizontalEdgeMarginPx,
        verticalGapAbovePx,
        verticalGapBelowHandlePx,
        verticalEdgeInsetPx
    ) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ): IntOffset {
                val selectionRectInWindow = if (anchorCoordinates != null && anchorCoordinates.isAttached) {
                    val localBounds = active.dataProvider.contentBounds(anchorCoordinates)
                    val windowOrigin = anchorCoordinates.localToWindow(Offset.Zero)
                    localBounds.translate(windowOrigin)
                } else {
                    bodyBoundsInWindow
                }
                return calculateSelectionPopupPosition(
                    selectionRectInWindow = selectionRectInWindow,
                    containerBoundsInWindow = bodyBoundsInWindow,
                    windowSize = windowSize,
                    popupContentSize = popupContentSize,
                    horizontalEdgeMarginPx = horizontalEdgeMarginPx,
                    verticalGapAbovePx = verticalGapAbovePx,
                    verticalGapBelowHandlePx = verticalGapBelowHandlePx,
                    verticalEdgeInsetPx = verticalEdgeInsetPx
                )
            }
        }
    }

    Popup(
        popupPositionProvider = positionProvider,
        properties = PopupProperties(
            focusable = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            clippingEnabled = true
        )
    ) {
        EditorSelectionPill(
            canHighlight = true,
            showColorPalette = provider.showColorPalette,
            onShowColorPaletteChange = { provider.showColorPalette = it },
            isBoldSelected = isSelectionBold,
            isItalicSelected = isSelectionItalic,
            hasConvertibleBibleRef = hasConvertibleBibleRef,
            onCut = cutItem?.let { item -> { item.onClick(active.session) } },
            onCopy = copyItem?.let { item -> { item.onClick(active.session) } },
            onPaste = pasteItem?.let { item -> { item.onClick(active.session) } },
            onSelectAll = selectAllItem?.let { item -> { item.onClick(active.session) } },
            language = language,
            strings = strings,
            onApplyHighlight = { color ->
                onApplyHighlight(color)
                active.session.close()
            },
            onToggleBold = onToggleBold,
            onToggleItalic = onToggleItalic,
            onAddLink = {
                active.session.close()
                onAddLink()
            },
            onConvertSelectionRef = {
                active.session.close()
                onConvertSelectionRef()
            }
        )
    }
}

@Composable
fun FloatingEditorSelectionMenu(
    controller: EditorSelectionToolbarController,
    bodyBoundsInWindow: Rect,
    hasTextSelection: Boolean,
    isSelectionVisibleInViewport: Boolean = true,
    isScrolling: Boolean = false,
    isSelectionBold: Boolean = false,
    isSelectionItalic: Boolean = false,
    hasConvertibleBibleRef: Boolean = false,
    language: AppLanguage,
    strings: AppStrings,
    onApplyHighlight: (HighlightColor?) -> Unit,
    onToggleBold: () -> Unit = {},
    onToggleItalic: () -> Unit = {},
    onAddLink: () -> Unit,
    onConvertSelectionRef: () -> Unit = {}
) {
    if (isScrolling) return
    val menu = controller.menuState
    if (!menu.isVisible) return

    val selectionInViewport = isSelectionVisibleInViewport &&
        isSelectionRectVisibleInViewport(menu.selectionRectInWindow, bodyBoundsInWindow)

    if (!hasTextSelection || !selectionInViewport || (menu.onCopyRequested == null && menu.onCutRequested == null)) {
        LaunchedEffect(menu, hasTextSelection, selectionInViewport) {
            controller.hide()
        }
        return
    }

    val density = LocalDensity.current
    val horizontalEdgeMarginPx = with(density) { 12.dp.toPx() }
    val verticalGapAbovePx = with(density) { 10.dp.toPx() }
    val verticalGapBelowHandlePx = with(density) { 28.dp.toPx() }
    val verticalEdgeInsetPx = with(density) { 4.dp.toPx() }

    val positionProvider = remember(
        menu.selectionRectInWindow,
        bodyBoundsInWindow,
        horizontalEdgeMarginPx,
        verticalGapAbovePx,
        verticalGapBelowHandlePx,
        verticalEdgeInsetPx
    ) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ): IntOffset = calculateSelectionPopupPosition(
                selectionRectInWindow = menu.selectionRectInWindow,
                containerBoundsInWindow = bodyBoundsInWindow,
                windowSize = windowSize,
                popupContentSize = popupContentSize,
                horizontalEdgeMarginPx = horizontalEdgeMarginPx,
                verticalGapAbovePx = verticalGapAbovePx,
                verticalGapBelowHandlePx = verticalGapBelowHandlePx,
                verticalEdgeInsetPx = verticalEdgeInsetPx
            )
        }
    }

    Popup(
        popupPositionProvider = positionProvider,
        properties = PopupProperties(
            focusable = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            clippingEnabled = true
        )
    ) {
        EditorSelectionPill(
            canHighlight = true,
            showColorPalette = controller.showColorPalette,
            onShowColorPaletteChange = { controller.showColorPalette = it },
            isBoldSelected = isSelectionBold,
            isItalicSelected = isSelectionItalic,
            hasConvertibleBibleRef = hasConvertibleBibleRef,
            onCut = menu.onCutRequested?.let { action ->
                {
                    controller.hide()
                    action()
                }
            },
            onCopy = menu.onCopyRequested?.let { action ->
                {
                    controller.hide()
                    action()
                }
            },
            onPaste = menu.onPasteRequested?.let { action ->
                {
                    controller.hide()
                    action()
                }
            },
            onSelectAll = menu.onSelectAllRequested,
            language = language,
            strings = strings,
            onApplyHighlight = { color ->
                onApplyHighlight(color)
                controller.hide()
            },
            onToggleBold = onToggleBold,
            onToggleItalic = onToggleItalic,
            onAddLink = {
                controller.hide()
                onAddLink()
            },
            onConvertSelectionRef = {
                controller.hide()
                onConvertSelectionRef()
            }
        )
    }
}

@Composable
private fun EditorSelectionPill(
    canHighlight: Boolean,
    showColorPalette: Boolean,
    onShowColorPaletteChange: (Boolean) -> Unit,
    isBoldSelected: Boolean,
    isItalicSelected: Boolean,
    hasConvertibleBibleRef: Boolean,
    onCut: (() -> Unit)?,
    onCopy: (() -> Unit)?,
    onPaste: (() -> Unit)?,
    onSelectAll: (() -> Unit)?,
    language: AppLanguage,
    strings: AppStrings,
    onApplyHighlight: (HighlightColor?) -> Unit,
    onToggleBold: () -> Unit,
    onToggleItalic: () -> Unit,
    onAddLink: () -> Unit,
    onConvertSelectionRef: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
        ),
        modifier = Modifier
            .animateContentSize(animationSpec = tween(durationMillis = 160))
            .testTag("floating_selection_toolbar")
    ) {
        AnimatedContent(
            targetState = showColorPalette && canHighlight,
            transitionSpec = {
                fadeIn(tween(130)) togetherWith fadeOut(tween(100))
            },
            label = "selection_toolbar_mode"
        ) { isPaletteMode ->
            if (isPaletteMode) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 4.dp)
                ) {
                    IconButton(
                        onClick = { onShowColorPaletteChange(false) },
                        modifier = Modifier
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .testTag("floating_palette_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = strings.backAction,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    HighlightColor.entries.forEach { color ->
                        val colorName = if (language == AppLanguage.IT) color.labelIt else color.labelEn
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                .clip(CircleShape)
                                .semantics {
                                    contentDescription = colorName
                                }
                                .clickable(role = Role.Button) {
                                    onApplyHighlight(color)
                                }
                                .testTag("floating_highlight_color_${color.name.lowercase()}")
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color(color.argb))
                                    .border(
                                        width = 1.2.dp,
                                        color = MaterialTheme.colorScheme.outline,
                                        shape = CircleShape
                                    )
                            )
                        }
                    }

                    IconButton(
                        onClick = { onApplyHighlight(null) },
                        modifier = Modifier
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .testTag("floating_clear_highlight_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FormatClear,
                            contentDescription = strings.clearHighlightDesc,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 4.dp)
                ) {
                    if (canHighlight) {
                        IconButton(
                            onClick = onToggleBold,
                            modifier = Modifier
                                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                .padding(vertical = 4.dp, horizontal = 1.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isBoldSelected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        Color.Transparent
                                    }
                                )
                                .testTag("floating_bold_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FormatBold,
                                contentDescription = strings.boldAction,
                                tint = if (isBoldSelected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = onToggleItalic,
                            modifier = Modifier
                                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                .padding(vertical = 4.dp, horizontal = 1.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isItalicSelected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        Color.Transparent
                                    }
                                )
                                .testTag("floating_italic_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FormatItalic,
                                contentDescription = strings.italicAction,
                                tint = if (isItalicSelected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .semantics {
                                    contentDescription = strings.highlightColorsDesc
                                }
                                .clickable(role = Role.Button) {
                                    onShowColorPaletteChange(true)
                                }
                                .testTag("floating_highlight_toggle")
                        ) {
                            HighlightColorStackIcon(
                                surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                outlineColor = MaterialTheme.colorScheme.outline
                            )
                        }

                        IconButton(
                            onClick = onAddLink,
                            modifier = Modifier
                                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                .testTag("floating_link_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Link,
                                contentDescription = strings.addLinkAction,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        if (hasConvertibleBibleRef) {
                            IconButton(
                                onClick = onConvertSelectionRef,
                                modifier = Modifier
                                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                    .testTag("floating_convert_ref_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                    contentDescription = strings.convertToRefDesc,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .padding(horizontal = 2.dp)
                                .width(1.dp)
                                .height(22.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant)
                        )
                    }

                    if (onCut != null) {
                        IconButton(
                            onClick = onCut,
                            modifier = Modifier
                                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                .testTag("floating_cut_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ContentCut,
                                contentDescription = strings.cutAction,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    if (onCopy != null) {
                        IconButton(
                            onClick = onCopy,
                            modifier = Modifier
                                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                .testTag("floating_copy_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ContentCopy,
                                contentDescription = strings.copyAction,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    if (onPaste != null) {
                        IconButton(
                            onClick = onPaste,
                            modifier = Modifier
                                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                .testTag("floating_paste_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ContentPaste,
                                contentDescription = strings.pasteAction,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    if (onSelectAll != null) {
                        IconButton(
                            onClick = onSelectAll,
                            modifier = Modifier
                                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                .testTag("floating_select_all_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SelectAll,
                                contentDescription = strings.selectAllAction,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HighlightColorStackIcon(
    surfaceColor: Color,
    outlineColor: Color
) {
    val stackColors = remember { HighlightColor.entries.asReversed() }
    Canvas(modifier = Modifier.size(width = 32.dp, height = 20.dp)) {
        val diameter = 16.dp.toPx()
        val radius = diameter / 2f
        val stepPx = 5.dp.toPx()
        val haloStroke = Stroke(width = 2.dp.toPx())
        val borderStroke = Stroke(width = 1.dp.toPx())
        val centerY = size.height / 2f
        val startX = (size.width - (diameter + stepPx * (stackColors.size - 1))) / 2f + radius

        stackColors.forEachIndexed { drawOrder, highlightColor ->
            val reverseIndex = stackColors.lastIndex - drawOrder
            val center = Offset(x = startX + reverseIndex * stepPx, y = centerY)
            drawCircle(
                color = surfaceColor,
                radius = radius,
                center = center,
                style = haloStroke
            )
            drawCircle(
                color = Color(highlightColor.argb),
                radius = radius,
                center = center
            )
            drawCircle(
                color = outlineColor.copy(alpha = 0.6f),
                radius = radius,
                center = center,
                style = borderStroke
            )
        }
    }
}
