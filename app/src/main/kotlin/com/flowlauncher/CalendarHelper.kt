package com.flowlauncher

import android.Manifest
import android.content.ContentUris
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
     * Fetch upcoming events sorted by start time using CalendarContract.Instances.
     *
     * Unlike Events.CONTENT_URI (which only stores the base event record),
     * Instances.CONTENT_URI expands recurring events into individual instances —
     * so a weekly meeting will show every occurrence within the queried range,
     * not just the first one.
     *
     * - No query : next 30 days, max [limit] results.
     * - With query: next 365 days, all matching results (no cap).
     */
    suspend fun getUpcomingEvents(context: Context, limit: Int = 20, query: String = ""): List<EventItem> =
        withContext(Dispatchers.IO) {
            if (!hasPermission(context)) return@withContext emptyList()

            val now      = System.currentTimeMillis()
            val rangeEnd = now + if (query.isNotBlank())
                TimeUnit.DAYS.toMillis(365)   // full-year search
            else
                TimeUnit.DAYS.toMillis(30)    // default 30-day view
            val effectiveLimit = if (query.isNotBlank()) Int.MAX_VALUE else limit

            // Build the Instances URI with the exact time window baked in.
            // This is required by the Instances content provider.
            val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().let {
                ContentUris.appendId(it, now)
                ContentUris.appendId(it, rangeEnd)
                it.build()
            }

            val projection = arrayOf(
                CalendarContract.Instances.EVENT_ID,
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.CALENDAR_COLOR
            )

            // Title filter applied in-memory after fetch (Instances URI does not
            // support LIKE selection on Android < 26 reliably).
            // Note: Instances table has no DELETED column — that belongs to Events.
            val selection     = "${CalendarContract.Instances.BEGIN} >= ?"
            val selectionArgs = arrayOf(now.toString())

            val events = mutableListOf<EventItem>()
            val seen   = mutableSetOf<String>() // deduplicate by "eventId-begin"

            try {
                context.contentResolver.query(
                    uri,
                    projection,
                    selection,
                    selectionArgs,
                    "${CalendarContract.Instances.BEGIN} ASC"
                )?.use { cursor ->
                    val iId     = cursor.getColumnIndex(CalendarContract.Instances.EVENT_ID)
                    val iTitle  = cursor.getColumnIndex(CalendarContract.Instances.TITLE)
                    val iBegin  = cursor.getColumnIndex(CalendarContract.Instances.BEGIN)
                    val iEnd    = cursor.getColumnIndex(CalendarContract.Instances.END)
                    val iAllDay = cursor.getColumnIndex(CalendarContract.Instances.ALL_DAY)
                    val iColor  = cursor.getColumnIndex(CalendarContract.Instances.CALENDAR_COLOR)

                    while (cursor.moveToNext() && events.size < effectiveLimit) {
                        val title = cursor.getString(iTitle)?.takeIf { it.isNotBlank() }
                            ?: continue

                        // Apply title filter in-memory for reliable cross-version behaviour
                        if (query.isNotBlank() &&
                            !title.contains(query, ignoreCase = true)) continue

                        val eventId = cursor.getLong(iId)
                        val begin   = cursor.getLong(iBegin)
                        val key     = "$eventId-$begin"
                        if (!seen.add(key)) continue   // skip duplicates

                        events += EventItem(
                            id            = eventId,
                            title         = title,
                            startMs       = begin,
                            endMs         = cursor.getLong(iEnd).let {
                                if (it == 0L) begin + 3_600_000L else it
                            },
                            allDay        = cursor.getInt(iAllDay) == 1,
                            calendarColor = cursor.getInt(iColor).let {
                                if (it == 0) 0xFF4285F4.toInt() else it
                            }
                        )
                    }
                }
            } catch (_: Exception) { /* SecurityException or provider unavailable */ }

            events
        }
}
