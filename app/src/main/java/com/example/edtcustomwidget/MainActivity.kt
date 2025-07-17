package com.example.edtcustomwidget

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*
import androidx.core.content.edit
import android.util.Log

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView

    @OptIn(DelicateCoroutinesApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recyclerView = findViewById(R.id.eventRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        GlobalScope.launch(Dispatchers.IO) {
            val allEvents = fetchCalendarEvents()

            Log.d("MainActivity", "Nombre total d'événements récupérés: ${allEvents.size}")

            val upcomingEvents = allEvents
                .filter { it.start.after(Date()) }
                .sortedBy { it.start }

            Log.d("MainActivity", "Nombre d'événements futurs: ${upcomingEvents.size}")

            withContext(Dispatchers.Main) {
                recyclerView.adapter = EventAdapter(upcomingEvents)
            }

            cacheEventsForWidget(allEvents)
        }
    }

    private fun cacheEventsForWidget(events: List<BiweeklyEvent>) {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        val calendarEvents = events.mapNotNull { event ->
            try {
                val calStart = Calendar.getInstance().apply { time = event.start }
                val calEnd = Calendar.getInstance().apply { time = event.end }

                val date = formatter.format(calStart.time)
                val startHour = calStart.get(Calendar.HOUR_OF_DAY) + (calStart.get(Calendar.MINUTE) / 60.0f)
                val endHour = calEnd.get(Calendar.HOUR_OF_DAY) + (calEnd.get(Calendar.MINUTE) / 60.0f)

                CalendarEvent(
                    title = event.title,
                    date = date,
                    startHour = startHour,
                    endHour = endHour,
                    color = android.graphics.Color.GRAY,
                    room = event.location
                )
            } catch (e: Exception) {
                Log.e("MainActivity", "Erreur lors du mapping de l'événement : ${event.title}", e)
                null
            }
        }

        Log.d("MainActivity", "Événements convertis pour le cache: ${calendarEvents.size}")
        calendarEvents.forEach { event ->
            Log.d("MainActivity", "Cached: ${event.title} - ${event.date} - ${event.startHour}h")
        }

        // Sauvegarde dans SharedPreferences
        val json = Gson().toJson(calendarEvents)
        getSharedPreferences("widget_cache", Context.MODE_PRIVATE)
            .edit {
                putString("cached_events", json)
                Log.d("MainActivity", "Événements sauvegardés dans le cache.")
            }

        // Déclencher la mise à jour du widget
        val intent = Intent(this, MyCalendarWidget::class.java).apply {
            action = "REFRESH_WIDGET"
        }
        sendBroadcast(intent)
        Log.d("MainActivity", "Broadcast REFRESH_WIDGET envoyé.")
    }
}
