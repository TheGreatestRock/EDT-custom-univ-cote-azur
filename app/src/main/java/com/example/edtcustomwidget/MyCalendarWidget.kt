package com.example.edtcustomwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import androidx.core.content.edit

data class CalendarEvent(
    val title: String,
    val dayOfWeek: Int, // 0 = Lundi, 4 = Vendredi
    val hour: Int,      // 0 = 8h, ..., 10 = 18h
    val color: Int,
    val room: String? = null
)

class MyCalendarWidget : AppWidgetProvider() {

    companion object {
        private val DAYS = arrayOf("Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi")
        private val HOURS = arrayOf("8h", "9h", "10h", "11h", "12h", "13h", "14h", "15h", "16h", "17h", "18h")

        fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            // Récupération asynchrone des événements
            CoroutineScope(Dispatchers.IO).launch {
                val events = fetchCalendarEvent(context) // Appel de votre fonction

                // Mise à jour de l'UI sur le thread principal
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
            // Nettoyer
            views.removeAllViews(R.id.header_row)
            views.removeAllViews(R.id.timetable_container)

            val DAYS = listOf("Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi")
            val HOURS = listOf("8h", "9h", "10h", "11h", "12h", "13h", "14h", "15h", "16h", "17h", "18h")

            // Trouver le prochain jour avec des événements
            val nextDayWithEvents = findNextDayWithEvents(events)

            if (nextDayWithEvents == null) {
                views.setTextViewText(R.id.widget_title, "Aucun cours à venir")
                return
            }

            val (dayIndex, dayEvents) = nextDayWithEvents
            val dayName = DAYS[dayIndex]
            views.setTextViewText(R.id.widget_title, "Cours du $dayName")

            // Ajouter l'en-tête : juste le nom du jour
            val dayView = RemoteViews(context.packageName, R.layout.day_header_item)
            dayView.setTextViewText(R.id.day_text, dayName)
            views.addView(R.id.header_row, dayView)

            // Obtenir toutes les heures avec cours (ou toutes pour un affichage complet)
            val allHourIndices = (0 until HOURS.size)

            for (hourIndex in allHourIndices) {
                val hourRow = RemoteViews(context.packageName, R.layout.hour_row)
                hourRow.setTextViewText(R.id.hour_label, HOURS[hourIndex])

                val cellView = RemoteViews(context.packageName, R.layout.timetable_cell)
                val event = dayEvents.find { it.hour == hourIndex }

                if (event != null) {
                    cellView.setTextViewText(R.id.cell_text, event.title)
                    cellView.setTextViewText(R.id.cell_room, event.room ?: "")
                    cellView.setInt(R.id.cell_container, "setBackgroundColor", event.color)
                }

                hourRow.addView(R.id.hour_cells, cellView)
                views.addView(R.id.timetable_container, hourRow)
            }
        }



        private fun findNextDayWithEvents(events: List<CalendarEvent>): Pair<Int, List<CalendarEvent>>? {
            val calendar = Calendar.getInstance()
            val today = calendar.get(Calendar.DAY_OF_WEEK)
            val todayIndex = when (today) {
                Calendar.MONDAY -> 0
                Calendar.TUESDAY -> 1
                Calendar.WEDNESDAY -> 2
                Calendar.THURSDAY -> 3
                Calendar.FRIDAY -> 4
                else -> -1 // Weekend
            }

            // Chercher à partir d'aujourd'hui
            for (dayOffset in 0..4) {
                val dayIndex = (todayIndex + dayOffset) % 5
                val dayEvents = events.filter { it.dayOfWeek == dayIndex }

                if (dayEvents.isNotEmpty()) {
                    return Pair(dayIndex, dayEvents)
                }
            }

            return null
        }

        private suspend fun fetchCalendarEvent(context: Context): List<CalendarEvent> {
            return try {
                val biweeklyEvents = fetchCalendarEvents() // récupération live (existant)
                val calendarEvents = convertToCalendarEvents(biweeklyEvents)

                // 🔁 Caching dans SharedPreferences
                val json = Gson().toJson(calendarEvents)
                context.getSharedPreferences("widget_cache", Context.MODE_PRIVATE)
                    .edit { putString("cached_events", json) }

                calendarEvents
            } catch (e: Exception) {
                e.printStackTrace()

                // 🔄 En cas d'erreur, lire depuis le cache
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
            return biweeklyEvents.mapNotNull { event ->
                val calendar = Calendar.getInstance().apply { time = event.start }
                val dayOfWeek = when (calendar.get(Calendar.DAY_OF_WEEK)) {
                    Calendar.MONDAY -> 0
                    Calendar.TUESDAY -> 1
                    Calendar.WEDNESDAY -> 2
                    Calendar.THURSDAY -> 3
                    Calendar.FRIDAY -> 4
                    else -> return@mapNotNull null // Ignorer les weekends
                }

                val hour = calendar.get(Calendar.HOUR_OF_DAY)
                val hourIndex = hour - 8 // 8h = index 0, 9h = index 1, etc.

                if (hourIndex in 0..10) { // Vérifier que l'heure est dans la plage 8h-18h
                    CalendarEvent(
                        title = event.title,
                        dayOfWeek = dayOfWeek,
                        hour = hourIndex,
                        color = android.graphics.Color.BLUE
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
