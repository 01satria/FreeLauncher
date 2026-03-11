package com.flowlauncher

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import java.util.Calendar

object ScreenTimeHelper {

    fun hasPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Calculates per-app foreground time strictly within [startMs, nowMs] by
     * replaying raw UsageEvents (ACTIVITY_RESUMED / ACTIVITY_PAUSED / ACTIVITY_STOPPED).
     *
     * This is the ONLY method that is reliable across all OEMs.
     * queryUsageStats / queryAndAggregateUsageStats both return totalTimeInForeground
     * scoped to internal OS bucket boundaries (often calendar day in UTC or OEM-defined),
     * so they bleed previous-day usage into the current reading.
     *
     * queryEvents gives us the raw timeline of when each app went foreground/background,
     * so we can compute exactly how many ms each app was in foreground since [startMs].
     *
     * Reset boundary: 01:00 local time (not midnight) — avoids edge cases where
     * users are still active at 00:xx and the day hasn't meaningfully "started" yet.
     */
    fun getTodayUsage(context: Context): Map<String, Long> {
        return try {
            if (!hasPermission(context)) return emptyMap()

            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val startMs = todayResetMs()
            val nowMs   = System.currentTimeMillis()

            // Query raw events in our window
            val events = usm.queryEvents(startMs, nowMs) ?: return emptyMap()

            // foregroundStart[pkg] = timestamp when app last came to foreground
            val foregroundStart = mutableMapOf<String, Long>()
            // totalMs[pkg] = accumulated foreground ms within [startMs, nowMs]
            val totalMs = mutableMapOf<String, Long>()

            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val pkg = event.packageName ?: continue
                val ts  = event.timeStamp

                when (event.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> {
                        // App came to foreground — record start time (clamped to startMs)
                        foregroundStart[pkg] = maxOf(ts, startMs)
                    }
                    UsageEvents.Event.ACTIVITY_PAUSED,
                    UsageEvents.Event.ACTIVITY_STOPPED -> {
                        val start = foregroundStart.remove(pkg) ?: continue
                        val elapsed = ts - start
                        if (elapsed > 0) {
                            totalMs[pkg] = (totalMs[pkg] ?: 0L) + elapsed
                        }
                    }
                }
            }

            // Apps still in foreground right now — add time up to now
            for ((pkg, start) in foregroundStart) {
                val elapsed = nowMs - start
                if (elapsed > 0) {
                    totalMs[pkg] = (totalMs[pkg] ?: 0L) + elapsed
                }
            }

            // Convert ms → minutes, drop zeros
            totalMs
                .mapValues { it.value / 60_000L }
                .filter { it.value > 0 }

        } catch (e: Exception) {
            emptyMap()
        }
    }

    /**
     * Returns the start-of-day boundary: 01:00 AM local time today.
     * If current time is before 01:00, returns 01:00 AM of yesterday
     * (so the "day" always covers the most recent 01:00 AM).
     */
    private fun todayResetMs(): Long {
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)

        // If it's before 1 AM, the current "day" started at 1 AM yesterday
        if (hour < 1) {
            cal.add(Calendar.DATE, -1)
        }

        cal.set(Calendar.HOUR_OF_DAY, 1)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun formatMinutes(minutes: Long): String {
        return when {
            minutes <= 0L -> ""
            minutes < 60L -> "${minutes}m"
            else          -> "${minutes / 60}h ${minutes % 60}m"
        }
    }
}
