package com.flowlauncher

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

data class EventItem(
    val id: Long,
    val title: String,
    val startMs: Long,
    val endMs: Long,
    val allDay: Boolean,
    val calendarColor: Int
) {
    /** Milliseconds until event starts (negative = already started) */
    val msUntilStart: Long get() = startMs - System.currentTimeMillis()

    /** Formatted countdown string, e.g. "2d 4h" / "3h 20m" / "45m" / "Now" */
    val countdown: String get() {
        val ms = msUntilStart
        return when {
            ms < 0 && (System.currentTimeMillis() < endMs) -> "Now"
            ms < 0 -> "Done"
            ms < TimeUnit.MINUTES.toMillis(1) -> "< 1m"
            ms < TimeUnit.HOURS.toMillis(1) -> {
                val m = TimeUnit.MILLISECONDS.toMinutes(ms)
                "${m}m"
            }
            ms < TimeUnit.DAYS.toMillis(1) -> {
                val h = TimeUnit.MILLISECONDS.toHours(ms)
                val m = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
                if (m == 0L) "${h}h" else "${h}h ${m}m"
            }
            ms < TimeUnit.DAYS.toMillis(7) -> {
                val d = TimeUnit.MILLISECONDS.toDays(ms)
                val h = TimeUnit.MILLISECONDS.toHours(ms) % 24
                if (h == 0L) "${d}d" else "${d}d ${h}h"
            }
            else -> {
                val d = TimeUnit.MILLISECONDS.toDays(ms)
                "${d}d"
            }
        }
    }
}

object CalendarHelper {

    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CALENDAR) ==
                PackageManager.PERMISSION_GRANTED

    /**
     * Fetch upcoming events within the next 30 days, sorted by start time.
     * If [query] is non-blank, filters by title (case-insensitive LIKE).
     * Returns empty list if permission not granted.
     */
    suspend fun getUpcomingEvents(context: Context, limit: Int = 20, query: String = ""): List<EventItem> =
        withContext(Dispatchers.IO) {
            if (!hasPermission(context)) return@withContext emptyList()
            val events = mutableListOf<EventItem>()
            val now = System.currentTimeMillis()
            val rangeEnd = now + TimeUnit.DAYS.toMillis(30)

            val projection = arrayOf(
                CalendarContract.Events._ID,
                CalendarContract.Events.TITLE,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.DTEND,
                CalendarContract.Events.ALL_DAY,
                CalendarContract.Events.CALENDAR_COLOR
            )

            val selection = buildString {
                append("${CalendarContract.Events.DTSTART} >= ? AND ")
                append("${CalendarContract.Events.DTSTART} <= ? AND ")
                append("${CalendarContract.Events.DELETED} = 0")
                if (query.isNotBlank()) {
                    append(" AND ${CalendarContract.Events.TITLE} LIKE ?")
                }
            }
            val selectionArgs = if (query.isNotBlank())
                arrayOf(now.toString(), rangeEnd.toString(), "%$query%")
            else
                arrayOf(now.toString(), rangeEnd.toString())

            try {
                context.contentResolver.query(
                    CalendarContract.Events.CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    "${CalendarContract.Events.DTSTART} ASC"
                )?.use { cursor ->
                    val iId     = cursor.getColumnIndex(CalendarContract.Events._ID)
                    val iTitle  = cursor.getColumnIndex(CalendarContract.Events.TITLE)
                    val iStart  = cursor.getColumnIndex(CalendarContract.Events.DTSTART)
                    val iEnd    = cursor.getColumnIndex(CalendarContract.Events.DTEND)
                    val iAllDay = cursor.getColumnIndex(CalendarContract.Events.ALL_DAY)
                    val iColor  = cursor.getColumnIndex(CalendarContract.Events.CALENDAR_COLOR)
                    while (cursor.moveToNext() && events.size < limit) {
                        val title = cursor.getString(iTitle)?.takeIf { it.isNotBlank() }
                            ?: continue
                        events += EventItem(
                            id            = cursor.getLong(iId),
                            title         = title,
                            startMs       = cursor.getLong(iStart),
                            endMs         = cursor.getLong(iEnd).let { if (it == 0L) cursor.getLong(iStart) + 3_600_000L else it },
                            allDay        = cursor.getInt(iAllDay) == 1,
                            calendarColor = cursor.getInt(iColor).let { if (it == 0) 0xFF4285F4.toInt() else it }
                        )
                    }
                }
            } catch (_: Exception) { /* SecurityException or provider unavailable */ }

            events
        }
}
