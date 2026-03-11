package com.flowlauncher

import android.app.AppOpsManager
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
     * Returns per-app foreground minutes using INTERVAL_DAILY buckets —
     * the exact same method Android's Digital Wellbeing / system screen time uses.
     *
     * INTERVAL_DAILY buckets are aligned to local midnight by the OS itself,
     * so totalTimeInForeground in each stat already represents "today only".
     * No manual event-replay needed; the OS does the windowing correctly for this interval.
     */
    fun getTodayUsage(context: Context): Map<String, Long> {
        return try {
            if (!hasPermission(context)) return emptyMap()

            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

            // Start of today in local time (midnight)
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val startMs = cal.timeInMillis
            val nowMs   = System.currentTimeMillis()

            // INTERVAL_DAILY: OS returns one stat per app per calendar day,
            // totalTimeInForeground = usage within that calendar day only.
            val stats = usm.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startMs,
                nowMs
            ) ?: return emptyMap()

            // Accumulate (multiple entries can exist per package)
            val result = mutableMapOf<String, Long>()
            for (stat in stats) {
                val minutes = stat.totalTimeInForeground / 60_000L
                if (minutes <= 0) continue
                result[stat.packageName] = (result[stat.packageName] ?: 0L) + minutes
            }
            result
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun formatMinutes(minutes: Long): String {
        return when {
            minutes <= 0L -> ""
            minutes < 60L -> "${minutes}m"
            else          -> "${minutes / 60}h ${minutes % 60}m"
        }
    }
}
