package com.flowlauncher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

object AppRepository {

    @Volatile private var cachedApps: List<AppInfo> = emptyList()
    @Volatile private var cacheValid = false
    @Volatile private var lastKnownDayOfYear: Int = -1

    private fun currentDayOfYear(): Int = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)

    private fun forceResetIfNewDay() {
        val today = currentDayOfYear()
        if (today != lastKnownDayOfYear) {
            cacheValid = false
            lastKnownDayOfYear = today
        }
    }

    suspend fun loadApps(context: Context, prefs: Prefs): List<AppInfo> =
        withContext(Dispatchers.IO) {
            forceResetIfNewDay()

            if (cacheValid && cachedApps.isNotEmpty()) {
                val screenTime = ScreenTimeHelper.getTodayUsage(context)
                val favorites  = prefs.favoritePackages.toSet()
                cachedApps = cachedApps.map { app ->
                    app.copy(
                        screenTimeMinutes = screenTime[app.packageName] ?: 0L,
                        isFavorite = app.packageName in favorites
                    )
                }
                return@withContext cachedApps
            }

            val pm     = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }

            @Suppress("DEPRECATION")
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                PackageManager.MATCH_ALL else 0

            val hidden     = prefs.hiddenPackages
            val favorites  = prefs.favoritePackages.toSet()
            val screenTime = ScreenTimeHelper.getTodayUsage(context)

            val apps = pm.queryIntentActivities(intent, flags)
                .asSequence()
                .filter {
                    it.activityInfo.packageName != context.packageName &&
                    it.activityInfo.packageName !in hidden
                }
                .map { info ->
                    AppInfo(
                        label             = try { info.loadLabel(pm).toString() }
                                            catch (_: Exception) { info.activityInfo.packageName },
                        packageName       = info.activityInfo.packageName,
                        icon              = try { info.loadIcon(pm) } catch (_: Exception) { null },
                        screenTimeMinutes = screenTime[info.activityInfo.packageName] ?: 0L,
                        isHidden          = false,
                        isFavorite        = info.activityInfo.packageName in favorites
                    )
                }
                .sortedBy { it.label.lowercase() }
                .toList()

            cachedApps           = apps
            cacheValid           = true
            lastKnownDayOfYear   = currentDayOfYear()
            apps
        }

    fun getCached(): List<AppInfo> = cachedApps

    fun getMostUsed(limit: Int = 5): List<AppInfo> =
        cachedApps.filter { it.screenTimeMinutes > 0 }
            .sortedByDescending { it.screenTimeMinutes }
            .take(limit)

    fun getFavorites(prefs: Prefs): List<AppInfo> {
        val order = prefs.favoritePackages
        val map   = cachedApps.associateBy { it.packageName }
        return order.mapNotNull { map[it] }
    }

    fun invalidate() {
        cacheValid = false
        cachedApps = emptyList()
    }
}
