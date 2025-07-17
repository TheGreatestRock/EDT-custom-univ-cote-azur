package com.example.edtcustomwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import androidx.core.content.edit
import java.text.SimpleDateFormat
import kotlin.math.roundToInt

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
        private val HOURS = listOf("8h", "9h", "10h", "11h", "12h", "13h", "14h", "15h", "16h", "17h", "18h")

        fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            CoroutineScope(Dispatchers.IO).launch {
                val events = fetchCalendarEvent(context)
                CoroutineScope(Dispatchers.Main).launch {
                    val views = RemoteViews(context.packageName, R.layout.widget_calendar_layout)

                    val refreshIntent = Intent(context, MyCalendarWidget::class.java).apply {
                        action = "REFRESH_WIDGET"
                    }
                    val refreshPendingIntent = PendingIntent.getBroadcast(
                        context, 0, refreshIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.refresh_button, refreshPendingIntent)

                    generateTimetableView(context, views, events)
                    manager.updateAppWidget(widgetId, views)
                }
            }
        }

        private fun generateTimetableView(context: Context, views: RemoteViews, events: List<CalendarEvent>) {
            views.removeAllViews(R.id.header_row)
            views.removeAllViews(R.id.timetable_container)

            val formatterInput = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val formatterOutput = SimpleDateFormat("EEEE dd MMMM", Locale.FRENCH)

            val result = findNextDateWithEvents(events)
            if (result == null) {
                views.setTextViewText(R.id.widget_title, "Aucun cours à venir")
                return
            }

            val (dateStr, rawEvents) = result
            val groupedEvents = groupContiguousEvents(rawEvents)
            val formattedDate = formatterOutput.format(formatterInput.parse(dateStr)!!)
            views.setTextViewText(R.id.widget_title, "Cours du $formattedDate")

            // Sort events by startHour
            val sorted = groupedEvents.sortedBy { it.startHour }

            var currentHour = 8.0f

            while (currentHour < 19.0f) {
                val matchingEvent = sorted.find { it.startHour == currentHour }

                if (matchingEvent != null) {
                    val duration = matchingEvent.endHour - matchingEvent.startHour
                    val rows = (duration / 0.5f).roundToInt()

                    for (i in 0 until rows) {
                        val row = RemoteViews(context.packageName, R.layout.hour_row)
                        row.setTextViewText(R.id.hour_label, formatHour(currentHour + i * 0.5f))

                        val cell = RemoteViews(context.packageName, R.layout.timetable_cell)

                        if (i == 0) {
                            val fullText = "${matchingEvent.title}${matchingEvent.room?.let { " - $it" } ?: ""}"
                            cell.setTextViewText(R.id.cell_text, fullText)
                            cell.setTextViewText(R.id.cell_room, "")
                        } else {
                            cell.setTextViewText(R.id.cell_text, "")
                            cell.setTextViewText(R.id.cell_room, "")
                        }

                        cell.setInt(R.id.cell_container, "setBackgroundColor", matchingEvent.color)
                        row.addView(R.id.hour_cells, cell)
                        views.addView(R.id.timetable_container, row)
                    }

                    currentHour = matchingEvent.endHour
                } else {
                    val row = RemoteViews(context.packageName, R.layout.hour_row)
                    row.setTextViewText(R.id.hour_label, formatHour(currentHour))

                    val emptyCell = RemoteViews(context.packageName, R.layout.timetable_cell)
                    emptyCell.setTextViewText(R.id.cell_text, "")
                    emptyCell.setTextViewText(R.id.cell_room, "")
                    row.addView(R.id.hour_cells, emptyCell)

                    views.addView(R.id.timetable_container, row)
                    currentHour += 0.5f
                }

            }
        }

        private fun formatHour(hour: Float): String {
            val h = hour.toInt()
            val min = if ((hour - h) >= 0.5f) "30" else "00"
            return "${h}h$min"
        }

        fun groupContiguousEvents(events: List<CalendarEvent>): List<CalendarEvent> {
            if (events.isEmpty()) return emptyList()

            val grouped = mutableListOf<CalendarEvent>()
            val sorted = events.sortedWith(compareBy({ it.date }, { it.startHour }))

            var current = sorted.first()

            for (i in 1 until sorted.size) {
                val next = sorted[i]
                val canGroup = current.title == next.title &&
                        current.room == next.room &&
                        current.date == next.date &&
                        current.endHour == next.startHour

                if (canGroup) {
                    current = current.copy(endHour = next.endHour)
                } else {
                    grouped.add(current)
                    current = next
                }
            }

            grouped.add(current)
            return grouped
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

        private suspend fun fetchCalendarEvent(context: Context): List<CalendarEvent> {
            return try {
                val biweeklyEvents = fetchCalendarEvents()
                val calendarEvents = convertToCalendarEvents(biweeklyEvents)

                val json = Gson().toJson(calendarEvents)
                context.getSharedPreferences("widget_cache", Context.MODE_PRIVATE)
                    .edit { putString("cached_events", json) }

                calendarEvents
            } catch (e: Exception) {
                e.printStackTrace()
                val prefs = context.getSharedPreferences("widget_cache", Context.MODE_PRIVATE)
                val cachedJson = prefs.getString("cached_events", null)

                if (cachedJson != null) {
                    Gson().fromJson(cachedJson, object : TypeToken<List<CalendarEvent>>() {}.type)
                } else {
                    emptyList()
                }
            }
        }

        private fun convertToCalendarEvents(biweeklyEvents: List<BiweeklyEvent>): List<CalendarEvent> {
            val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

            return biweeklyEvents.mapNotNull { event ->
                val calStart = Calendar.getInstance().apply { time = event.start }
                val calEnd = Calendar.getInstance().apply { time = event.end }

                val dayOfWeek = calStart.get(Calendar.DAY_OF_WEEK)
                if (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) return@mapNotNull null

                val date = formatter.format(calStart.time)
                val startHour = calStart.get(Calendar.HOUR_OF_DAY) + (calStart.get(Calendar.MINUTE) / 60.0f)
                val endHour = calEnd.get(Calendar.HOUR_OF_DAY) + (calEnd.get(Calendar.MINUTE) / 60.0f)

                if (startHour in 8.0..18.5 && endHour > startHour) {
                    CalendarEvent(
                        title = event.title,
                        date = date,
                        startHour = startHour,
                        endHour = endHour,
                        color = Color.GRAY,
                        room = event.location
                    )
                } else null
            }
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { updateWidget(context, manager, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == "REFRESH_WIDGET") {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, MyCalendarWidget::class.java))
            ids.forEach { updateWidget(context, manager, it) }
        }
    }

    fun cacheEvents(context: Context, events: List<CalendarEvent>) {
        val json = Gson().toJson(events)
        context.getSharedPreferences("widget_cache", Context.MODE_PRIVATE)
            .edit().putString("cached_events", json).apply()
    }
}
