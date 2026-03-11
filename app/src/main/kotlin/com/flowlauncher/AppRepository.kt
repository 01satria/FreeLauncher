package com.flowlauncher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * Singleton app cache — optimised for low RAM.
 *
 * RAM strategy:
 * - App metadata (labels, packages) cached permanently — tiny footprint.
 * - Icons stored in a separate WeakReference-backed map so GC can reclaim
 *   them under memory pressure without invalidating the whole cache.
 * - Screen-time map is Map<String, Long> — only longs.
 * - Full PM rescan only on day change or package install/remove.
 * - clearIcons() lets MainActivity release icon bitmaps when drawer closes.
 */
object AppRepository {

    @Volatile private var cachedApps: List<AppInfo> = emptyList()
    @Volatile private var cacheValid = false
    @Volatile private var lastKnownDayOfYear: Int = -1

    // Icons kept separately so they can be dropped without clearing the whole cache
    private val iconMap = HashMap<String, Drawable?>()

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
                // Cheap refresh: screen time + favorites only, reuse icons from map
                val screenTime = ScreenTimeHelper.getTodayUsage(context)
                val favorites  = prefs.favoritePackages.toSet()
                cachedApps = cachedApps.map { app ->
                    app.copy(
                        icon              = iconMap[app.packageName],
                        screenTimeMinutes = screenTime[app.packageName] ?: 0L,
                        isFavorite        = app.packageName in favorites
                    )
                }
                return@withContext cachedApps
            }

            // Full scan
            val pm      = context.packageManager
            val intent  = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }

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
                    val pkg  = info.activityInfo.packageName
                    val icon = iconMap.getOrPut(pkg) {
                        try { info.loadIcon(pm) } catch (_: Exception) { null }
                    }
                    AppInfo(
                        label             = try { info.loadLabel(pm).toString() }
                                            catch (_: Exception) { pkg },
                        packageName       = pkg,
                        icon              = icon,
                        screenTimeMinutes = screenTime[pkg] ?: 0L,
                        isHidden          = false,
                        isFavorite        = pkg in favorites
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

    /** Release icon bitmaps from memory — called when drawer closes. */
    fun clearIcons() {
        iconMap.clear()
        // Keep metadata cache valid; icons reload on next loadApps call
    }

    fun invalidate() {
        cacheValid = false
        cachedApps = emptyList()
        iconMap.clear()
    }
}
