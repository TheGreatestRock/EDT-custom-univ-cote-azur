package com.example.edtcustomwidget

import android.content.Context
import biweekly.Biweekly
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import java.util.*

data class BiweeklyEvent(
    val title: String,
    val start: Date,
    val end: Date,
    val location: String?,
    val description: String?
)

suspend fun fetchCalendarEvents(): List<BiweeklyEvent> = withContext(Dispatchers.IO) {
    try {
        val url = URL("https://edtweb.univ-cotedazur.fr/jsp/custom/modules/plannings/anonymous_cal.jsp?resources=64194&projectId=5&calType=ical&firstDate=2025-08-01&lastDate=2026-08-01")
        url.openStream().use { stream ->
            val calendar = Biweekly.parse(stream).first()
            calendar.events.mapNotNull { event ->
                val start = event.dateStart?.value
                val end = event.dateEnd?.value
                val summary = event.summary?.value
                val location = event.location?.value
                val description = event.description?.value

                if (start != null && end != null && summary != null) {
                    // Corriger le fuseau horaire
                    val adjustedStart = Calendar.getInstance().apply {
                        time = start
                        add(Calendar.HOUR_OF_DAY, 2)
                    }.time

                    val adjustedEnd = Calendar.getInstance().apply {
                        time = end
                        add(Calendar.HOUR_OF_DAY, 2)
                    }.time

                    println("Event: $summary, Start: $adjustedStart, End: $adjustedEnd, Location: $location")

                    BiweeklyEvent(
                        title = summary,
                        start = adjustedStart,
                        end = adjustedEnd,
                        location = location,
                        description = description
                    )
                } else null
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        emptyList()
    }
}
