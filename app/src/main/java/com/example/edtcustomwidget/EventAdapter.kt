package com.example.edtcustomwidget

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class EventAdapter(private val events: List<BiweeklyEvent>) :
    RecyclerView.Adapter<EventAdapter.EventViewHolder>() {

    class EventViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val time: TextView = view.findViewById(R.id.textTime)
        val title: TextView = view.findViewById(R.id.textTitle)
        val location: TextView = view.findViewById(R.id.textLocation)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_event, parent, false)
        return EventViewHolder(view)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        val event = events[position]

        val formatter = SimpleDateFormat("EEE dd MMM • HH:mm", Locale.FRANCE)
        val start = formatter.format(event.start)
        val end = SimpleDateFormat("HH:mm", Locale.FRANCE).format(event.end)

        holder.time.text = "$start ➔ $end"
        holder.title.text = event.title
        holder.location.text = event.location ?: "Lieu non spécifié"
    }

    override fun getItemCount(): Int = events.size
}
