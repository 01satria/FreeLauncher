package com.flowlauncher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Singleton app cache.
 *
 * RAM strategy:
 * - App list loaded once on IO thread; subsequent calls reuse cache.
 * - Full PM scan only happens after invalidate() (package add/remove).
 * - Icons stored as Drawable references inside AppInfo; not duplicated.
 * - Screen-time map is a plain Map<String, Long> — only longs, very cheap.
 */
object AppRepository {

    @Volatile private var cachedApps: List<AppInfo> = emptyList()
    @Volatile private var screenTimeCache: Map<String, Long> = emptyMap()
    @Volatile private var cacheValid = false

    suspend fun loadApps(context: Context, prefs: Prefs): List<AppInfo> =
        withContext(Dispatchers.IO) {
            // Reuse cache on resume — only full-scan if invalidated
            if (cacheValid && cachedApps.isNotEmpty()) {
                // Still refresh screen time (cheap) without re-loading icons
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

            // Full scan (first load or after install/uninstall)
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
