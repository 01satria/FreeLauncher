package com.flowlauncher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * Singleton app cache.
 *
 * RAM strategy:
 * - App list loaded once on IO thread; subsequent calls reuse cache.
 * - Full PM scan only happens after invalidate() (package add/remove).
 * - Icons NOT loaded — saves significant RAM.
 * - Screen-time map is a plain Map<String, Long> — only longs, very cheap.
 * - Cache auto-invalidates at midnight (local time) so screen time resets correctly.
 */
object AppRepository {

    @Volatile private var cachedApps: List<AppInfo> = emptyList()
    @Volatile private var screenTimeCache: Map<String, Long> = emptyMap()
    @Volatile private var cacheValid = false
    @Volatile private var cacheDateMs: Long = 0L   // midnight timestamp when cache was built

    /** Returns midnight (00:00) of today in local time — matches INTERVAL_DAILY boundary. */
    private fun todayMidnightMs(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    suspend fun loadApps(context: Context, prefs: Prefs): List<AppInfo> =
        withContext(Dispatchers.IO) {
            val midnightMs = todayMidnightMs()

            // Invalidate cache if date has crossed midnight
            if (cacheDateMs != midnightMs) {
                cacheValid = false
            }

            // Reuse cache on resume — only full-scan if invalidated
            if (cacheValid && cachedApps.isNotEmpty()) {
                // Refresh screen time (cheap) without re-scanning installed apps
                screenTimeCache = ScreenTimeHelper.getTodayUsage(context)
                val favorites = prefs.favoritePackages.toSet()
                cachedApps = cachedApps.map { app ->
                    app.copy(
                        screenTimeMinutes = screenTimeCache[app.packageName] ?: 0L,
                        isFavorite = app.packageName in favorites
                    )
                }
                return@withContext cachedApps
            }

            // Full scan (first load, after install/uninstall, or day change)
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }

            @Suppress("DEPRECATION")
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                PackageManager.MATCH_ALL else 0

            val hidden    = prefs.hiddenPackages
            val favorites = prefs.favoritePackages.toSet()

            screenTimeCache = ScreenTimeHelper.getTodayUsage(context)

            val apps = pm.queryIntentActivities(intent, flags)
                .asSequence()
                .filter {
                    it.activityInfo.packageName != context.packageName &&
                    it.activityInfo.packageName !in hidden
                }
                .map { info ->
                    AppInfo(
                        label        = try { info.loadLabel(pm).toString() }
                                       catch (_: Exception) { info.activityInfo.packageName },
                        packageName  = info.activityInfo.packageName,
                        icon         = try { info.loadIcon(pm) } catch (_: Exception) { null },
                        screenTimeMinutes = screenTimeCache[info.activityInfo.packageName] ?: 0L,
                        isHidden     = false,
                        isFavorite   = info.activityInfo.packageName in favorites
                    )
                }
                .sortedBy { it.label.lowercase() }
                .toList()

            cachedApps = apps
            cacheValid = true
            cacheDateMs = midnightMs
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

    /** Called on package install/uninstall — forces full PM scan next load. */
    fun invalidate() {
        cacheValid = false
        cachedApps = emptyList()
    }
}
