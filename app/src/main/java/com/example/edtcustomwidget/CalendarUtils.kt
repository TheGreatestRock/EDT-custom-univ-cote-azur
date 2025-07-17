package com.example.edtcustomwidget

import biweekly.Biweekly
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
                    BiweeklyEvent(
                        title = summary,
                        start = start,
                        end = end,
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
