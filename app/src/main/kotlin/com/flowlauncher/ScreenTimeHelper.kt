package com.flowlauncher

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
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
     * Hitung screen time per app dari raw events sejak 00:00 lokal hari ini.
     * Pakai MOVE_TO_FOREGROUND / MOVE_TO_BACKGROUND sehingga kita sendiri
     * yang kontrol batas midnight — tidak bergantung bucket internal OEM.
     */
    fun getTodayUsage(context: Context): Map<String, Long> {
        return try {
            if (!hasPermission(context)) return emptyMap()

            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val startOfDay = getStartOfToday()
            val now = System.currentTimeMillis()

            val events = usm.queryEvents(startOfDay, now) ?: return emptyMap()

            val lastForeground = mutableMapOf<String, Long>() // pkg -> timestamp mulai foreground
            val totalMs = mutableMapOf<String, Long>()

            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val pkg = event.packageName ?: continue
                val ts  = event.timeStamp

                when (event.eventType) {
                    UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                        lastForeground[pkg] = ts
                    }
                    UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                        val start = lastForeground.remove(pkg) ?: continue
                        val elapsed = ts - start
                        if (elapsed > 0) totalMs[pkg] = (totalMs[pkg] ?: 0L) + elapsed
                    }
                }
            }

            // App yang masih foreground sampai sekarang
            for ((pkg, start) in lastForeground) {
                val elapsed = now - start
                if (elapsed > 0) totalMs[pkg] = (totalMs[pkg] ?: 0L) + elapsed
            }

            totalMs.mapValues { it.value / 60_000L }.filter { it.value > 0 }

        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun getStartOfToday(): Long {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                java.time.LocalDate.now(java.time.ZoneId.systemDefault())
                    .atStartOfDay(java.time.ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
            } catch (e: Exception) {
                midnightCalendar()
            }
        } else {
            midnightCalendar()
        }
    }

    private fun midnightCalendar(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    fun formatMinutes(minutes: Long): String {
        return when {
            minutes <= 0L -> ""
            minutes < 60L -> "${minutes}m"
            else          -> "${minutes / 60}h ${minutes % 60}m"
        }
    }
}
