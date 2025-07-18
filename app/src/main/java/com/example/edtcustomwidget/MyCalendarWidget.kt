package com.example.edtcustomwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.util.Log
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import androidx.core.content.edit
import java.text.SimpleDateFormat
import kotlin.div
import kotlin.math.roundToInt
import kotlin.or
import kotlin.text.compareTo
import kotlin.text.format

data class CalendarEvent(
    val title: String,
    val date: String, // Format: "yyyy-MM-dd"
    val startHour: Float, // 8.0 = 8h00, 9.5 = 9h30
    val endHour: Float,   // 10.5 = 10h30
    val color: Int,
    val room: String? = null
)

class MyCalendarWidget : AppWidgetProvider() {

    companion object {
        private const val ACTION_PREVIOUS_DAY = "PREVIOUS_DAY"
        private const val ACTION_NEXT_DAY = "NEXT_DAY"
        private const val PREF_CURRENT_DATE_OFFSET = "current_date_offset"

        private val HOURS = listOf("8h", "9h", "10h", "11h", "12h", "13h", "14h", "15h", "16h", "17h", "18h")


        fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            CoroutineScope(Dispatchers.IO).launch {
                val events = fetchCalendarEvent(context)
                val offset = getCurrentDateOffset(context)
                setCurrentDateOffset(context, offset)
                CoroutineScope(Dispatchers.Main).launch {
                    val views = RemoteViews(context.packageName, R.layout.widget_calendar_layout)

                    setupNavigationButtons(context, views)
                    generateTimetableView(context, views, events)
                    manager.updateAppWidget(widgetId, views)
                }
            }
        }

        private fun setupNavigationButtons(context: Context, views: RemoteViews) {
            // Bouton précédent
            val prevIntent = Intent(context, MyCalendarWidget::class.java).apply {
                action = ACTION_PREVIOUS_DAY
            }
            val prevPendingIntent = PendingIntent.getBroadcast(
                context, 1, prevIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_previous, prevPendingIntent)

            // Bouton suivant
            val nextIntent = Intent(context, MyCalendarWidget::class.java).apply {
                action = ACTION_NEXT_DAY
            }
            val nextPendingIntent = PendingIntent.getBroadcast(
                context, 2, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_next, nextPendingIntent)

            // Bouton rafraîchir
            val refreshIntent = Intent(context, MyCalendarWidget::class.java).apply {
                action = "REFRESH_WIDGET"
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(
                context, 3, refreshIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_refresh, refreshPendingIntent)
        }


        private fun getCurrentDateOffset(context: Context): Int {
            return context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
                .getInt(PREF_CURRENT_DATE_OFFSET, 0)
        }

        private fun setCurrentDateOffset(context: Context, offset: Int) {
            context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
                .edit { putInt(PREF_CURRENT_DATE_OFFSET, offset) }
        }

        private fun findDateWithEvents(events: List<CalendarEvent>, offset: Int): Pair<String, List<CalendarEvent>>? {
            val today = Calendar.getInstance()
            val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

            // Calculer la date cible en ajoutant l'offset jour par jour
            val targetCalendar = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, offset)
            }
            val targetDate = formatter.format(targetCalendar.time)

            // Grouper tous les cours par date

            val eventsByDate = events.groupBy { it.date }

            // Retourner la date cible avec ses cours (même si vide)
            return Pair(targetDate, eventsByDate[targetDate] ?: emptyList())
        }

        private fun generateTimetableView(context: Context, views: RemoteViews, events: List<CalendarEvent>) {
            views.removeAllViews(R.id.events_container)

            val formatterInput = SimpleDateFormat("yyyy-MM-dd", Locale.FRENCH)
            val formatterOutput = SimpleDateFormat("EEEE dd MMMM", Locale.FRENCH)

            val offset = getCurrentDateOffset(context)
            val result = findDateWithEvents(events, offset)

            if (result == null) {
                views.setTextViewText(R.id.widget_title, "Erreur de date")
                return
            }

            val (dateStr, rawEvents) = result
            val formattedDate = formatterOutput.format(formatterInput.parse(dateStr)!!)
            views.setTextViewText(R.id.widget_title, "Cours du $formattedDate")

            if (rawEvents.isEmpty()) {
                val emptyEventView = RemoteViews(context.packageName, R.layout.timetable_cell)
                emptyEventView.setTextViewText(R.id.cell_text, "🎉 Aucun cours prévu")
                emptyEventView.setInt(R.id.cell_container, "setBackgroundColor", Color.LTGRAY)
                views.addView(R.id.events_container, emptyEventView)
                return
            }

            rawEvents.sortedBy { it.startHour }.forEach { event ->
                val eventView = RemoteViews(context.packageName, R.layout.timetable_cell)
                val roomInfo = event.room?.let { "\n📍 Salle : $it" } ?: "\n📍 Salle non spécifiée"
                eventView.setTextViewText(
                    R.id.cell_text,
                    "🕒 ${event.startHour.toHourString()} - ${event.endHour.toHourString()}\n📚 ${event.title}$roomInfo"
                )
                eventView.setInt(R.id.cell_container, "setBackgroundColor", event.color)
                views.addView(R.id.events_container, eventView)
            }
        }

        private fun findNextDateWithEvents(events: List<CalendarEvent>): Pair<String, List<CalendarEvent>>? {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val sortedDates = events
                .groupBy { it.date }
                .toSortedMap()

            for ((date, list) in sortedDates) {
                if (date >= today) return Pair(date, list)
            }
            return null
        }

        private fun fetchCalendarEvent(context: Context): List<CalendarEvent> {
            return try {
                val prefs = context.getSharedPreferences("widget_cache", Context.MODE_PRIVATE)
                val cachedJson = prefs.getString("cached_events", null)
                val cacheTimestamp = prefs.getLong("cache_timestamp", 0L)

                val now = System.currentTimeMillis()
                val maxAge = 1000 * 60 * 60 * 6 // 6 heures

                if (cachedJson != null && (now - cacheTimestamp) < maxAge) {
                    Gson().fromJson(cachedJson, object : TypeToken<List<CalendarEvent>>() {}.type)
                } else {
                    Log.w("MyCalendarWidget", "Le cache est expiré ou inexistant. Envoi du broadcast REFRESH_WIDGET.")
                    context.sendBroadcast(Intent(context, MyCalendarWidget::class.java).apply {
                        action = "REFRESH_WIDGET"
                    })
                    emptyList()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { updateWidget(context, manager, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        when (intent.action) {
            "REFRESH_WIDGET" -> {
                refreshWidget(context)
            }
            ACTION_PREVIOUS_DAY -> {
                val currentOffset = getCurrentDateOffset(context)
                setCurrentDateOffset(context, currentOffset - 1)
                refreshWidget(context)
            }
            ACTION_NEXT_DAY -> {
                val currentOffset = getCurrentDateOffset(context)
                setCurrentDateOffset(context, currentOffset + 1)
                refreshWidget(context)
            }
        }
    }

    private fun refreshWidget(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, MyCalendarWidget::class.java))
        ids.forEach { updateWidget(context, manager, it) }
    }
}
