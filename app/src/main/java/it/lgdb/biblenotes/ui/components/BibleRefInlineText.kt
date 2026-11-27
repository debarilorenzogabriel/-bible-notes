package it.lgdb.biblenotes.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.lgdb.biblenotes.domain.InNoteSearchMatch
import it.lgdb.biblenotes.domain.JwLinkBuilder
import it.lgdb.biblenotes.domain.SpanAdjuster
import it.lgdb.biblenotes.model.AppLanguage
import it.lgdb.biblenotes.model.BibleRefDisplayStyle
import it.lgdb.biblenotes.model.BibleRefSpan
import it.lgdb.biblenotes.model.NoteTextSize
import it.lgdb.biblenotes.model.RichNoteContent

@Composable
fun BibleRefInlineText(
    content: RichNoteContent,
    style: TextStyle,
    modifier: Modifier = Modifier,
    language: AppLanguage = AppLanguage.IT,
    refDisplayStyle: BibleRefDisplayStyle = BibleRefDisplayStyle.ABBREVIATED,
    textSize: NoteTextSize = NoteTextSize.NORMAL,
    placeholder: String? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    chipFontSize: TextUnit = textSize.chipFontSp.sp,
    chipHeightDp: Dp = textSize.chipHeightDp.dp,
    linkColor: Color = MaterialTheme.colorScheme.primary,
    searchMatches: List<InNoteSearchMatch> = emptyList(),
    activeSearchMatchIndex: Int = -1,
    onReferenceClick: ((BibleRefSpan) -> Unit)? = null,
    onLinkClick: ((String) -> Unit)? = null,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val chipScale = textSize.chipFontSp / 13.5f

    val annotated = remember(content, language, refDisplayStyle, linkColor, onLinkClick) {
        SpanAdjuster.buildInlineAnnotatedString(
            content = content,
            language = language,
            linkColor = linkColor,
            onLinkClick = onLinkClick,
            refDisplayStyle = refDisplayStyle
        )
    }

    val chipTextStyle = remember(style, chipFontSize) {
        style.copy(
            fontSize = chipFontSize,
            fontWeight = FontWeight.Medium,
            lineHeight = TextUnit.Unspecified
        )
    }

    val inlineContent = remember(
        content.bibleRefs,
        language,
        refDisplayStyle,
        chipTextStyle,
        chipHeightDp,
        chipScale,
        density,
        onReferenceClick
    ) {
        val iconSizeDp = (15f * chipScale).dp
        val horizontalPaddingDp = (16f * chipScale).dp
        val iconSpacingDp = (6f * chipScale).dp
        val extraSafetyDp = 1.dp

        content.bibleRefs.mapIndexed { index, ref ->
            val shortLabel = JwLinkBuilder.formatReference(ref, language, refDisplayStyle)
            val measuredPx = textMeasurer.measure(
                text = AnnotatedString(shortLabel),
                style = chipTextStyle
            ).size.width

            val totalWidthSp = with(density) {
                (measuredPx.toDp() + iconSizeDp + iconSpacingDp + horizontalPaddingDp + extraSafetyDp).toSp()
            }
            val totalHeightSp = with(density) { chipHeightDp.toSp() }

            SpanAdjuster.inlineRefId(index) to InlineTextContent(
                placeholder = Placeholder(
                    width = totalWidthSp,
                    height = totalHeightSp,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                )
            ) {
                BibleReferenceChip(
                    label = shortLabel,
                    iconSize = iconSizeDp,
                    horizontalPadDp = (8f * chipScale).dp,
                    iconSpacingDp = iconSpacingDp,
                    textStyle = chipTextStyle,
                    onClick = if (onReferenceClick != null) {
                        { onReferenceClick(ref) }
                    } else {
                        null
                    }
                )
            }
        }.toMap()
    }

    val displayText = if (annotated.text.isEmpty() && placeholder != null) {
        AnnotatedString(placeholder)
    } else {
        annotated
    }

    val effectiveStyle = if (annotated.text.isEmpty() && placeholder != null) {
        style.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        style
    }

    var localLayout by remember { mutableStateOf<TextLayoutResult?>(null) }

    val mappedMatches = remember(content, searchMatches, language, refDisplayStyle) {
        searchMatches.map { match ->
            val range = SpanAdjuster.originalRangeToInlineAnnotatedRange(
                content = content,
                origStart = match.start,
                origEnd = match.end,
                language = language,
                refDisplayStyle = refDisplayStyle
            )
            match to range
        }
    }

    val activeFillColor = Color(0xFFFFB74D).copy(alpha = 0.42f)
    val inactiveFillColor = Color(0xFF4FC3F7).copy(alpha = 0.24f)
    val activeBorderColor = Color(0xFFE65100)
    val inactiveBorderColor = Color(0xFF0288D1).copy(alpha = 0.72f)

    Text(
        text = displayText,
        style = effectiveStyle,
        inlineContent = inlineContent,
        maxLines = maxLines,
        overflow = overflow,
        onTextLayout = { result ->
            localLayout = result
            onTextLayout?.invoke(result)
        },
        modifier = modifier.drawWithContent {
            val layout = localLayout
            if (layout != null && mappedMatches.isNotEmpty() && layout.layoutInput.text.text == annotated.text) {
                // 1. Draw soft background fill behind plain-text matches (user saved highlights remain on top!)
                mappedMatches.forEachIndexed { idx, (match, range) ->
                    if (!match.isBibleRef && !range.isEmpty()) {
                        val isActive = idx == activeSearchMatchIndex
                        val rects = computeTextLayoutRangeRects(layout, range.first, range.last + 1)
                        val fill = if (isActive) activeFillColor else inactiveFillColor
                        val corner = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        rects.forEach { r ->
                            drawRoundRect(
                                color = fill,
                                topLeft = Offset(r.left, r.top),
                                size = Size(r.width, r.height),
                                cornerRadius = corner
                            )
                        }
                    }
                }
            }

            drawContent()

            // 2. Draw crisp focus borders on top so matches remain visible even inside saved highlights or chips
            if (layout != null && mappedMatches.isNotEmpty() && layout.layoutInput.text.text == annotated.text) {
                val activeStrokePx = 1.8.dp.toPx()
                val inactiveStrokePx = 1.dp.toPx()
                mappedMatches.forEachIndexed { idx, (match, range) ->
                    if (!range.isEmpty()) {
                        val isActive = idx == activeSearchMatchIndex
                        val rects = computeTextLayoutRangeRects(layout, range.first, range.last + 1)
                        val borderColor = if (isActive) activeBorderColor else inactiveBorderColor
                        val strokeWidth = if (isActive) activeStrokePx else inactiveStrokePx
                        val corner = CornerRadius(
                            if (match.isBibleRef) 8.dp.toPx() else 4.dp.toPx(),
                            if (match.isBibleRef) 8.dp.toPx() else 4.dp.toPx()
                        )
                        val pad = if (match.isBibleRef) 1.dp.toPx() else 0.dp.toPx()
                        rects.forEach { r ->
                            drawRoundRect(
                                color = borderColor,
                                topLeft = Offset(r.left - pad, r.top - pad),
                                size = Size(r.width + pad * 2, r.height + pad * 2),
                                cornerRadius = corner,
                                style = Stroke(width = strokeWidth)
                            )
                        }
                    }
                }
            }
        }
    )
}

internal fun computeTextLayoutRangeRects(
    layout: TextLayoutResult,
    startOffset: Int,
    endOffset: Int
): List<Rect> {
    val len = layout.layoutInput.text.length
    if (len == 0 || startOffset >= endOffset) return emptyList()
    val s = startOffset.coerceIn(0, len - 1)
    val e = (endOffset - 1).coerceIn(s, len - 1)
    val startLine = layout.getLineForOffset(s)
    val endLine = layout.getLineForOffset(e)
    val rects = mutableListOf<Rect>()
    for (line in startLine..endLine) {
        val lineFirstChar = if (line == startLine) s else layout.getLineStart(line)
        val lineLastChar = if (line == endLine) {
            e
        } else {
            (layout.getLineEnd(line, visibleEnd = true) - 1).coerceAtLeast(lineFirstChar)
        }
        val firstBox = layout.getBoundingBox(lineFirstChar)
        val lastBox = layout.getBoundingBox(lineLastChar)
        val left = minOf(firstBox.left, lastBox.left)
        val right = maxOf(firstBox.right, lastBox.right)
        val top = minOf(firstBox.top, lastBox.top)
        val bottom = maxOf(firstBox.bottom, lastBox.bottom)
        if (right > left && bottom > top) {
            rects.add(Rect(left, top, right, bottom))
        }
    }
    return rects
}

@Composable
private fun BibleReferenceChip(
    label: String,
    iconSize: Dp,
    horizontalPadDp: Dp,
    iconSpacingDp: Dp,
    textStyle: TextStyle,
    onClick: (() -> Unit)?
) {
    val shape = RoundedCornerShape(8.dp)
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    val containerColor = MaterialTheme.colorScheme.surface
    val iconTint = MaterialTheme.colorScheme.primary
    val textColor = MaterialTheme.colorScheme.onSurface

    val content: @Composable () -> Unit = {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(iconSpacingDp),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = horizontalPadDp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(iconSize)
            )
            Text(
                text = label,
                style = textStyle,
                color = textColor,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip
            )
        }
    }

    if (onClick != null) {
        Surface(
            onClick = onClick,
            shape = shape,
            color = containerColor,
            border = border,
            modifier = Modifier.fillMaxSize()
        ) {
            content()
        }
    } else {
        Surface(
            shape = shape,
            color = containerColor,
            border = border,
            modifier = Modifier.fillMaxSize()
        ) {
            content()
        }
    }
}
