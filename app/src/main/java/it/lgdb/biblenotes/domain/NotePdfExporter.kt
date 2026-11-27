package it.lgdb.biblenotes.domain

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.print.pdf.PrintedPdfDocument
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import it.lgdb.biblenotes.model.AppLanguage
import it.lgdb.biblenotes.model.BibleRefDisplayStyle
import it.lgdb.biblenotes.model.NoteRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object NotePdfExporter {

    private const val A4_WIDTH_PT = 595
    private const val A4_HEIGHT_PT = 842

    private const val MARGIN_HORIZONTAL = 46f
    private const val MARGIN_TOP = 46f
    private const val MARGIN_BOTTOM = 48f
    private const val FOOTER_GAP = 14f

    private const val COLOR_TEXT_PRIMARY = 0xFF1A1C1E.toInt()
    private const val COLOR_TEXT_MUTED = 0xFF5F6368.toInt()
    private const val COLOR_ACCENT = 0xFF2B6251.toInt()
    private const val COLOR_REF_BG = 0xFFE6F0EC.toInt()
    private const val COLOR_DIVIDER = 0xFFDADCE0.toInt()

    suspend fun writePdfToUri(
        context: Context,
        uri: Uri,
        note: NoteRecord,
        language: AppLanguage,
        refDisplayStyle: BibleRefDisplayStyle,
        strings: AppStrings
    ): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val document = PdfDocument()
            try {
                renderNotePages(
                    document = document,
                    pageWidth = A4_WIDTH_PT,
                    pageHeight = A4_HEIGHT_PT,
                    note = note,
                    language = language,
                    refDisplayStyle = refDisplayStyle,
                    strings = strings
                )
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    document.writeTo(stream)
                    stream.flush()
                } ?: error("Cannot open output stream for PDF")
            } finally {
                document.close()
            }
        }.isSuccess
    }

    fun printNote(
        context: Context,
        note: NoteRecord,
        language: AppLanguage,
        refDisplayStyle: BibleRefDisplayStyle,
        strings: AppStrings
    ): Boolean {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return false
        val jobName = note.title.trim().ifEmpty { strings.widgetUntitledNote }
        val adapter = object : PrintDocumentAdapter() {
            private var currentAttributes: PrintAttributes? = null

            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback,
                extras: Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback.onLayoutCancelled()
                    return
                }
                currentAttributes = newAttributes
                val fileName = NoteExportFormatter.buildPdfSuggestedFileName(note, language)
                val info = PrintDocumentInfo.Builder(fileName)
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                    .build()
                callback.onLayoutFinished(info, newAttributes != oldAttributes)
            }

            override fun onWrite(
                pages: Array<out PageRange>?,
                destination: ParcelFileDescriptor,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback.onWriteCancelled()
                    return
                }
                val attrs = currentAttributes ?: PrintAttributes.Builder()
                    .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                    .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                    .build()

                val printedDoc = PrintedPdfDocument(context, attrs)
                try {
                    val pageWidth = ((attrs.mediaSize?.widthMils ?: 8268) * 72f / 1000f)
                        .toInt()
                        .coerceAtLeast(360)
                    val pageHeight = ((attrs.mediaSize?.heightMils ?: 11693) * 72f / 1000f)
                        .toInt()
                        .coerceAtLeast(480)

                    renderNotePages(
                        document = printedDoc,
                        pageWidth = pageWidth,
                        pageHeight = pageHeight,
                        note = note,
                        language = language,
                        refDisplayStyle = refDisplayStyle,
                        strings = strings
                    )

                    if (cancellationSignal?.isCanceled == true) {
                        callback.onWriteCancelled()
                        return
                    }

                    FileOutputStream(destination.fileDescriptor).use { out ->
                        printedDoc.writeTo(out)
                        out.flush()
                    }
                    callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (err: Throwable) {
                    callback.onWriteFailed(err.localizedMessage)
                } finally {
                    printedDoc.close()
                }
            }
        }
        printManager.print(jobName, adapter, PrintAttributes.Builder().build())
        return true
    }

    private fun renderNotePages(
        document: PdfDocument,
        pageWidth: Int,
        pageHeight: Int,
        note: NoteRecord,
        language: AppLanguage,
        refDisplayStyle: BibleRefDisplayStyle,
        strings: AppStrings
    ) {
        val contentWidth = (pageWidth - MARGIN_HORIZONTAL * 2).toInt().coerceAtLeast(120)
        val locale = if (language == AppLanguage.IT) Locale.ITALIAN else Locale.ENGLISH
        val dateText = SimpleDateFormat("d MMMM yyyy", locale).format(Date(note.updatedAt))

        val datePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT_MUTED
            textSize = 9.5f
            typeface = Typeface.DEFAULT
        }
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT_PRIMARY
            textSize = 18.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val tagsPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_ACCENT
            textSize = 9.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT_PRIMARY
            textSize = 11.5f
            typeface = Typeface.DEFAULT
        }
        val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT_MUTED
            textSize = 9f
        }
        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_DIVIDER
            strokeWidth = 0.8f
        }

        val titleLayout = note.title.trim().takeIf { it.isNotEmpty() }?.let { cleanTitle ->
            StaticLayout.Builder.obtain(cleanTitle, 0, cleanTitle.length, titlePaint, contentWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(2f, 1.15f)
                .build()
        }

        val tagsText = note.tags
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("   ") { "#$it" }
        val tagsLayout = tagsText.takeIf { it.isNotEmpty() }?.let {
            StaticLayout.Builder.obtain(it, 0, it.length, tagsPaint, contentWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(2f, 1.15f)
                .build()
        }

        val bodySpannable = buildFormattedPdfSpannable(
            note = note,
            language = language,
            refDisplayStyle = refDisplayStyle
        )
        val bodyLayout = if (bodySpannable.isNotEmpty()) {
            StaticLayout.Builder.obtain(bodySpannable, 0, bodySpannable.length, bodyPaint, contentWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(2.5f, 1.32f)
                .setIncludePad(false)
                .build()
        } else {
            null
        }

        val firstPageHeaderHeight = buildFirstPageHeaderHeight(
            datePaint = datePaint,
            titleLayout = titleLayout,
            tagsLayout = tagsLayout
        )
        val footerAreaHeight = FOOTER_GAP + 16f
        val maxContentBottomY = pageHeight - MARGIN_BOTTOM - footerAreaHeight
        val firstPageBodyTop = MARGIN_TOP + firstPageHeaderHeight
        val laterPageBodyTop = MARGIN_TOP

        val pageSlices = paginateLines(
            layout = bodyLayout,
            firstPageAvailable = (maxContentBottomY - firstPageBodyTop).coerceAtLeast(40f),
            laterPageAvailable = (maxContentBottomY - laterPageBodyTop).coerceAtLeast(40f)
        )
        val totalPages = pageSlices.size.coerceAtLeast(1)

        for (pageIndex in 0 until totalPages) {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex + 1).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas

            val bodyTopY: Float
            if (pageIndex == 0) {
                drawFirstPageHeader(
                    canvas = canvas,
                    pageWidth = pageWidth,
                    dateText = dateText,
                    datePaint = datePaint,
                    titleLayout = titleLayout,
                    tagsLayout = tagsLayout,
                    dividerPaint = dividerPaint
                )
                bodyTopY = firstPageBodyTop
            } else {
                bodyTopY = laterPageBodyTop
            }

            val lineRange = pageSlices.getOrNull(pageIndex)
            if (bodyLayout != null && lineRange != null && !lineRange.isEmpty()) {
                val sliceTop = bodyLayout.getLineTop(lineRange.first).toFloat()
                val sliceBottom = bodyLayout.getLineBottom(lineRange.last).toFloat()
                val sliceHeight = (sliceBottom - sliceTop).coerceAtLeast(0f)

                canvas.save()
                canvas.translate(MARGIN_HORIZONTAL, bodyTopY)
                canvas.clipRect(0f, 0f, contentWidth.toFloat(), sliceHeight)
                canvas.translate(0f, -sliceTop)
                bodyLayout.draw(canvas)
                canvas.restore()
            }

            drawFooter(
                canvas = canvas,
                pageWidth = pageWidth,
                pageHeight = pageHeight,
                pageNum = pageIndex + 1,
                totalPages = totalPages,
                strings = strings,
                footerPaint = footerPaint,
                dividerPaint = dividerPaint
            )

            document.finishPage(page)
        }
    }

    private fun buildFirstPageHeaderHeight(
        datePaint: TextPaint,
        titleLayout: StaticLayout?,
        tagsLayout: StaticLayout?
    ): Float {
        var h = (datePaint.fontMetrics.descent - datePaint.fontMetrics.ascent) + 8f
        if (titleLayout != null) {
            h += titleLayout.height + 8f
        }
        if (tagsLayout != null) {
            h += tagsLayout.height + 8f
        }
        h += 14f // Divider + gap before body
        return h
    }

    private fun drawFirstPageHeader(
        canvas: Canvas,
        pageWidth: Int,
        dateText: String,
        datePaint: TextPaint,
        titleLayout: StaticLayout?,
        tagsLayout: StaticLayout?,
        dividerPaint: Paint
    ) {
        var cursorY = MARGIN_TOP
        val dateBaseline = cursorY - datePaint.fontMetrics.ascent
        canvas.drawText(dateText, MARGIN_HORIZONTAL, dateBaseline, datePaint)
        cursorY += (datePaint.fontMetrics.descent - datePaint.fontMetrics.ascent) + 8f

        if (titleLayout != null) {
            canvas.save()
            canvas.translate(MARGIN_HORIZONTAL, cursorY)
            titleLayout.draw(canvas)
            canvas.restore()
            cursorY += titleLayout.height + 8f
        }

        if (tagsLayout != null) {
            canvas.save()
            canvas.translate(MARGIN_HORIZONTAL, cursorY)
            tagsLayout.draw(canvas)
            canvas.restore()
            cursorY += tagsLayout.height + 8f
        }

        canvas.drawLine(
            MARGIN_HORIZONTAL,
            cursorY,
            pageWidth - MARGIN_HORIZONTAL,
            cursorY,
            dividerPaint
        )
    }

    private fun drawFooter(
        canvas: Canvas,
        pageWidth: Int,
        pageHeight: Int,
        pageNum: Int,
        totalPages: Int,
        strings: AppStrings,
        footerPaint: TextPaint,
        dividerPaint: Paint
    ) {
        val lineY = pageHeight - MARGIN_BOTTOM - 10f
        canvas.drawLine(MARGIN_HORIZONTAL, lineY, pageWidth - MARGIN_HORIZONTAL, lineY, dividerPaint)
        val footerText = strings.pdfPageFooter
            .replace("{page}", pageNum.toString())
            .replace("{total}", totalPages.toString())
        val textWidth = footerPaint.measureText(footerText)
        val textBaseline = pageHeight - MARGIN_BOTTOM + 4f
        canvas.drawText(
            footerText,
            pageWidth - MARGIN_HORIZONTAL - textWidth,
            textBaseline,
            footerPaint
        )
    }

    private fun paginateLines(
        layout: StaticLayout?,
        firstPageAvailable: Float,
        laterPageAvailable: Float
    ): List<IntRange> {
        if (layout == null || layout.lineCount == 0) {
            return listOf(IntRange.EMPTY)
        }
        val slices = mutableListOf<IntRange>()
        var lineStart = 0
        var pageIdx = 0
        while (lineStart < layout.lineCount) {
            val available = if (pageIdx == 0) firstPageAvailable else laterPageAvailable
            val startTop = layout.getLineTop(lineStart).toFloat()
            var lineEnd = lineStart
            while (lineEnd + 1 < layout.lineCount) {
                val nextBottom = layout.getLineBottom(lineEnd + 1).toFloat()
                if (nextBottom - startTop <= available) {
                    lineEnd++
                } else {
                    break
                }
            }
            slices.add(lineStart..lineEnd)
            lineStart = lineEnd + 1
            pageIdx++
        }
        return slices
    }

    private fun buildFormattedPdfSpannable(
        note: NoteRecord,
        language: AppLanguage,
        refDisplayStyle: BibleRefDisplayStyle
    ): SpannableStringBuilder {
        if (note.isCorrupted) {
            return SpannableStringBuilder(note.rawCorruptedJson.orEmpty())
        }
        val content = note.content
        val text = content.text
        val textLen = text.length
        if (textLen == 0) return SpannableStringBuilder()

        val validRefs = content.bibleRefs
            .filter { it.start in 0 until it.end && it.end <= textLen }
            .sortedBy { it.start }
        val listLines = ListFormattingHelper.findRecognizedListLines(text)

        val builder = SpannableStringBuilder()
        var cursor = 0

        fun appendPlainSegment(segStart: Int, segEnd: Int) {
            if (segStart >= segEnd) return
            val outStart = builder.length
            builder.append(text, segStart, segEnd)

            content.highlights.forEach { span ->
                val hs = maxOf(segStart, span.start.coerceIn(0, textLen))
                val he = minOf(segEnd, span.end.coerceIn(0, textLen))
                if (hs < he) {
                    builder.setSpan(
                        BackgroundColorSpan(span.color.argb.toInt()),
                        outStart + (hs - segStart),
                        outStart + (he - segStart),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }
            content.bolds.forEach { span ->
                val bs = maxOf(segStart, span.start.coerceIn(0, textLen))
                val be = minOf(segEnd, span.end.coerceIn(0, textLen))
                if (bs < be) {
                    builder.setSpan(
                        StyleSpan(Typeface.BOLD),
                        outStart + (bs - segStart),
                        outStart + (be - segStart),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }
            content.italics.forEach { span ->
                val iss = maxOf(segStart, span.start.coerceIn(0, textLen))
                val ie = minOf(segEnd, span.end.coerceIn(0, textLen))
                if (iss < ie) {
                    builder.setSpan(
                        StyleSpan(Typeface.ITALIC),
                        outStart + (iss - segStart),
                        outStart + (ie - segStart),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }
            listLines.forEach { line ->
                val ms = maxOf(segStart, line.markerStart)
                val me = minOf(segEnd, line.markerEnd)
                if (ms < me) {
                    val s = outStart + (ms - segStart)
                    val e = outStart + (me - segStart)
                    builder.setSpan(ForegroundColorSpan(COLOR_ACCENT), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    builder.setSpan(StyleSpan(Typeface.BOLD), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
            content.links.forEach { link ->
                val ls = maxOf(segStart, link.start.coerceIn(0, textLen))
                val le = minOf(segEnd, link.end.coerceIn(0, textLen))
                if (ls < le) {
                    val s = outStart + (ls - segStart)
                    val e = outStart + (le - segStart)
                    builder.setSpan(ForegroundColorSpan(COLOR_ACCENT), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    builder.setSpan(UnderlineSpan(), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        }

        for (ref in validRefs) {
            if (ref.start < cursor) continue
            appendPlainSegment(cursor, ref.start)
            val refStart = builder.length
            val formatted = JwLinkBuilder.formatReference(ref, language, refDisplayStyle)
            builder.append(formatted)
            val refEnd = builder.length
            builder.setSpan(BackgroundColorSpan(COLOR_REF_BG), refStart, refEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            builder.setSpan(ForegroundColorSpan(COLOR_ACCENT), refStart, refEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            builder.setSpan(StyleSpan(Typeface.BOLD), refStart, refEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            cursor = ref.end
        }
        appendPlainSegment(cursor, textLen)
        return builder
    }
}
