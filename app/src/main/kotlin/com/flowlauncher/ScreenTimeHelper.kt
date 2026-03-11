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
     * Returns a map of packageName -> usage minutes for TODAY only (since 00:00 local time).
     *
     * Why NOT queryAndAggregateUsageStats:
     * Many OEMs (Samsung, Xiaomi, etc.) ignore the startTime in that API and return
     * cumulative data from their internal bucket boundary, causing yesterday's usage
     * to bleed into today's count.
     *
     * Fix: use queryUsageStats(INTERVAL_BEST, midnight, now).
     * Each UsageStat object from this call has totalTimeInForeground scoped to the
     * queried window [midnight, now], so it's strictly today's data.
     * We also guard with lastTimeUsed >= midnight to skip stale entries the OS may return.
     */
    fun getTodayUsage(context: Context): Map<String, Long> {
        return try {
            if (!hasPermission(context)) return emptyMap()

            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

            val midnightMs = todayMidnightMs()
            val nowMs = System.currentTimeMillis()

            val stats = usm.queryUsageStats(
                UsageStatsManager.INTERVAL_BEST,
                midnightMs,
                nowMs
            ) ?: return emptyMap()

            // Accumulate per package (there can be multiple entries per package)
            // Only include entries where the app was actually used today
            val result = mutableMapOf<String, Long>()
            for (stat in stats) {
                if (stat.lastTimeUsed < midnightMs) continue   // not used today — skip
                val minutes = stat.totalTimeInForeground / 60_000L
                if (minutes <= 0) continue
                result[stat.packageName] = (result[stat.packageName] ?: 0L) + minutes
            }
            result
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun todayMidnightMs(): Long {
        val cal = Calendar.getInstance()  // uses device local timezone
        cal.set(Calendar.HOUR_OF_DAY, 0)
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
