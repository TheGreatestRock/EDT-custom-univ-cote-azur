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
import androidx.lifecycle.lifecycleScope

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView

    @OptIn(DelicateCoroutinesApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recyclerView = findViewById(R.id.eventRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        lifecycleScope.launch(Dispatchers.IO) {
            val allEvents = withContext(Dispatchers.IO) { fetchCalendarEvents() }

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

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent?.action == "REFRESH_WIDGET") {
            Log.d("MainActivity", "Intent REFRESH_WIDGET reçu, relance de la coroutine.")
            lifecycleScope.launch {
                val allEvents = withContext(Dispatchers.IO) {
                    fetchCalendarEvents()
                }
                withContext(Dispatchers.IO) {
                    cacheEventsForWidget(allEvents)
                }
            }
        }
    }



    private fun cacheEventsForWidget(events: List<BiweeklyEvent>) {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        formatter.timeZone = TimeZone.getTimeZone("Europe/Paris") // Forcer le fuseau français

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
                    color = generateColorFromTitle(event.title),
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
        val now = System.currentTimeMillis()

        getSharedPreferences("widget_cache", Context.MODE_PRIVATE)
            .edit {
                putString("cached_events", json)
                putLong("cache_timestamp", now)
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
