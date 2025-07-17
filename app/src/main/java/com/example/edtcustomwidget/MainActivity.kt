package com.example.edtcustomwidget

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recyclerView = findViewById(R.id.eventRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        GlobalScope.launch(Dispatchers.IO) {
            val allEvents = fetchCalendarEvents()
            val upcomingEvents = allEvents
                .filter { it.start.after(Date()) }
                .sortedBy { it.start }

            withContext(Dispatchers.Main) {
                recyclerView.adapter = EventAdapter(upcomingEvents)
            }
        }
    }
}
