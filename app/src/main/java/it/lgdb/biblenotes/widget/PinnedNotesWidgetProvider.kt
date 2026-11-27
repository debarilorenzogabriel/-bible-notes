package it.lgdb.biblenotes.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import it.lgdb.biblenotes.MainActivity
import it.lgdb.biblenotes.R
import it.lgdb.biblenotes.data.NotesRepository
import it.lgdb.biblenotes.domain.JwLinkBuilder
import it.lgdb.biblenotes.domain.appStrings
import it.lgdb.biblenotes.model.AppLanguage
import it.lgdb.biblenotes.model.BibleRefDisplayStyle
import it.lgdb.biblenotes.model.NoteRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PinnedNotesWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                updateWidgetsInternal(context, appWidgetManager, appWidgetIds)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_WIDGET_NEW_NOTE = "it.lgdb.biblenotes.ACTION_WIDGET_NEW_NOTE"
        const val ACTION_WIDGET_OPEN_NOTE = "it.lgdb.biblenotes.ACTION_WIDGET_OPEN_NOTE"
        const val ACTION_WIDGET_OPEN_HOME = "it.lgdb.biblenotes.ACTION_WIDGET_OPEN_HOME"
        const val EXTRA_NOTE_ID = "extra_widget_note_id"

        private val ROW_IDS = intArrayOf(
            R.id.widget_note_row_0,
            R.id.widget_note_row_1,
            R.id.widget_note_row_2,
            R.id.widget_note_row_3
        )
        private val TITLE_IDS = intArrayOf(
            R.id.widget_note_title_0,
            R.id.widget_note_title_1,
            R.id.widget_note_title_2,
            R.id.widget_note_title_3
        )
        private val SUB_IDS = intArrayOf(
            R.id.widget_note_sub_0,
            R.id.widget_note_sub_1,
            R.id.widget_note_sub_2,
            R.id.widget_note_sub_3
        )

        fun refreshAll(context: Context) {
            runCatching {
                val appContext = context.applicationContext
                val manager = AppWidgetManager.getInstance(appContext) ?: return
                val ids = manager.getAppWidgetIds(
                    ComponentName(appContext, PinnedNotesWidgetProvider::class.java)
                )
                if (ids.isEmpty()) return
                val intent = Intent(appContext, PinnedNotesWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                appContext.sendBroadcast(intent)
            }
        }

        private fun updateWidgetsInternal(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray
        ) {
            val repository = NotesRepository(context)
            val language = repository.getLanguage()
            val refStyle = repository.getBibleRefDisplayStyle()
            val strings = appStrings(language)
            val pinnedNotes = runCatching {
                repository.getPinnedNotesSync(limit = ROW_IDS.size)
            }.getOrDefault(emptyList())

            appWidgetIds.forEach { widgetId ->
                val views = buildRemoteViews(
                    context = context,
                    widgetId = widgetId,
                    pinnedNotes = pinnedNotes,
                    language = language,
                    refStyle = refStyle,
                    headerTitle = strings.widgetPinnedNotesTitle,
                    newNoteText = "+ ${strings.widgetNewNoteAction}",
                    emptyMessage = strings.widgetEmptyPinnedMessage,
                    untitledLabel = strings.widgetUntitledNote
                )
                appWidgetManager.updateAppWidget(widgetId, views)
            }
        }

        private fun buildRemoteViews(
            context: Context,
            widgetId: Int,
            pinnedNotes: List<NoteRecord>,
            language: AppLanguage,
            refStyle: BibleRefDisplayStyle,
            headerTitle: String,
            newNoteText: String,
            emptyMessage: String,
            untitledLabel: String
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_pinned_notes)
            views.setTextViewText(R.id.widget_header_title, headerTitle)
            views.setTextViewText(R.id.widget_new_note_btn, newNoteText)
            views.setTextViewText(R.id.widget_empty_state, emptyMessage)

            // Open Home on header or empty state tap
            val homeIntent = Intent(context, MainActivity::class.java).apply {
                action = ACTION_WIDGET_OPEN_HOME
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val homePending = PendingIntent.getActivity(
                context,
                widgetId * 10 + 1,
                homeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_header_title, homePending)
            views.setOnClickPendingIntent(R.id.widget_empty_state, homePending)

            // Open New Note directly on "+ Nuova" / "+ New" button tap
            val newNoteIntent = Intent(context, MainActivity::class.java).apply {
                action = ACTION_WIDGET_NEW_NOTE
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val newNotePending = PendingIntent.getActivity(
                context,
                widgetId * 10 + 2,
                newNoteIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_new_note_btn, newNotePending)

            if (pinnedNotes.isEmpty()) {
                views.setViewVisibility(R.id.widget_empty_state, View.VISIBLE)
                views.setViewVisibility(R.id.widget_notes_container, View.GONE)
            } else {
                views.setViewVisibility(R.id.widget_empty_state, View.GONE)
                views.setViewVisibility(R.id.widget_notes_container, View.VISIBLE)

                ROW_IDS.indices.forEach { idx ->
                    val note = pinnedNotes.getOrNull(idx)
                    if (note == null) {
                        views.setViewVisibility(ROW_IDS[idx], View.GONE)
                    } else {
                        views.setViewVisibility(ROW_IDS[idx], View.VISIBLE)
                        val title = note.title.trim().ifEmpty { untitledLabel }
                        val firstRefLabel = note.content.bibleRefs.firstOrNull()?.let { ref ->
                            JwLinkBuilder.formatReference(ref, language, refStyle)
                        }
                        val snippet = note.content.text
                            .replace('\n', ' ')
                            .replace(Regex("\\s+"), " ")
                            .trim()
                        val subtitle = when {
                            firstRefLabel != null && snippet.isNotEmpty() -> "$firstRefLabel • $snippet"
                            firstRefLabel != null -> firstRefLabel
                            snippet.isNotEmpty() -> snippet
                            note.tags.isNotEmpty() -> note.tags.joinToString(" • ") { "#$it" }
                            else -> ""
                        }

                        views.setTextViewText(TITLE_IDS[idx], title)
                        if (subtitle.isNotEmpty()) {
                            views.setViewVisibility(SUB_IDS[idx], View.VISIBLE)
                            views.setTextViewText(SUB_IDS[idx], subtitle)
                        } else {
                            views.setViewVisibility(SUB_IDS[idx], View.GONE)
                        }

                        val openNoteIntent = Intent(context, MainActivity::class.java).apply {
                            action = ACTION_WIDGET_OPEN_NOTE
                            putExtra(EXTRA_NOTE_ID, note.id)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        }
                        val openNotePending = PendingIntent.getActivity(
                            context,
                            widgetId * 10 + 3 + idx,
                            openNoteIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        views.setOnClickPendingIntent(ROW_IDS[idx], openNotePending)
                    }
                }
            }
            return views
        }
    }
}
