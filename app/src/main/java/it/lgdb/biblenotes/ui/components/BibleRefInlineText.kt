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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.lgdb.biblenotes.domain.SpanAdjuster
import it.lgdb.biblenotes.model.BibleRefSpan
import it.lgdb.biblenotes.model.RichNoteContent

@Composable
fun BibleRefInlineText(
    content: RichNoteContent,
    style: TextStyle,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    chipFontSize: TextUnit = 13.5.sp,
    chipHeightDp: Dp = 28.dp,
    onReferenceClick: ((BibleRefSpan) -> Unit)? = null
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    val annotated = remember(content) {
        SpanAdjuster.buildInlineAnnotatedString(content = content)
    }

    val chipTextStyle = remember(chipFontSize) {
        TextStyle(
            fontSize = chipFontSize,
            fontWeight = FontWeight.Medium
        )
    }

    val inlineContent = remember(
        content.bibleRefs,
        chipFontSize,
        chipHeightDp,
        density,
        onReferenceClick
    ) {
        val iconSizeDp = if (chipHeightDp <= 24.dp) 13.dp else 15.dp
        val horizontalPaddingDp = 16.dp
        val iconSpacingDp = 6.dp
        val extraSafetyDp = 6.dp

        content.bibleRefs.mapIndexed { index, ref ->
            val measuredPx = textMeasurer.measure(
                text = AnnotatedString(ref.displayText),
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
                    label = ref.displayText,
                    iconSize = iconSizeDp,
                    fontSize = chipFontSize,
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

    Text(
        text = displayText,
        style = effectiveStyle,
        inlineContent = inlineContent,
        maxLines = maxLines,
        overflow = overflow,
        modifier = modifier
    )
}

@Composable
private fun BibleReferenceChip(
    label: String,
    iconSize: Dp,
    fontSize: TextUnit,
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
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(iconSize)
            )
            Text(
                text = label,
                color = textColor,
                fontSize = fontSize,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
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
