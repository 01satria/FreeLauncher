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

    fun getTodayUsage(context: Context): Map<String, Long> {
        return try {
            if (!hasPermission(context)) return emptyMap()

            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val startOfDay = getStartOfToday()
            val now = System.currentTimeMillis()

            val usageMap = usm.queryAndAggregateUsageStats(startOfDay, now)

            usageMap.mapValues { it.value.totalTimeInForeground / 60_000L }
                .filter { it.value > 0 }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun getTodayTotalMinutes(context: Context): Long {
        return try {
            if (!hasPermission(context)) return 0L
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val usageMap = usm.queryAndAggregateUsageStats(getStartOfToday(), System.currentTimeMillis())
            usageMap.values.sumOf { it.totalTimeInForeground } / 60_000L
        } catch (e: Exception) { 0L }
    }

    private fun getStartOfToday(): Long {
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
