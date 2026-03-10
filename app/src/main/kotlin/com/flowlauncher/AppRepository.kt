package com.flowlauncher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Centralised repository so app list + screen time are loaded ONCE
 * and cached in memory. Activities just reference the cache — no repeated
 * heavy PM queries on the main thread.
 */
object AppRepository {

    @Volatile private var cachedApps: List<AppInfo> = emptyList()
    @Volatile private var screenTimeCache: Map<String, Long> = emptyMap()

    suspend fun loadApps(context: Context, prefs: Prefs): List<AppInfo> =
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                PackageManager.MATCH_ALL else 0

            val hidden = prefs.hiddenPackages
            val favorites = prefs.favoritePackages.toSet()

            // Refresh screen time cache lazily in same IO pass
            screenTimeCache = ScreenTimeHelper.getTodayUsage(context)

            val apps = pm.queryIntentActivities(intent, flags)
                .filter {
                    it.activityInfo.packageName != context.packageName &&
                    it.activityInfo.packageName !in hidden
                }
                .map { info ->
                    AppInfo(
                        label = info.loadLabel(pm).toString(),
                        packageName = info.activityInfo.packageName,
                        icon = info.loadIcon(pm),
                        screenTimeMinutes = screenTimeCache[info.activityInfo.packageName] ?: 0L,
                        isHidden = false,
                        isFavorite = info.activityInfo.packageName in favorites
                    )
                }
                .sortedBy { it.label.lowercase() }

            cachedApps = apps
            apps
        }

    fun getCached(): List<AppInfo> = cachedApps

    fun getMostUsed(limit: Int = 5): List<AppInfo> =
        cachedApps.filter { it.screenTimeMinutes > 0 }
            .sortedByDescending { it.screenTimeMinutes }
            .take(limit)

    fun getFavorites(prefs: Prefs): List<AppInfo> {
        val order = prefs.favoritePackages
        val map = cachedApps.associateBy { it.packageName }
        return order.mapNotNull { map[it] }
    }

    fun getScreenTime(packageName: String) = screenTimeCache[packageName] ?: 0L

    fun invalidate() {
        cachedApps = emptyList()
    }
}
