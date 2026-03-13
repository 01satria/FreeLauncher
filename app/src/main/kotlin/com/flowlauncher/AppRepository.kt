package com.flowlauncher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * Singleton app cache — aggressively optimised for low RAM.
 *
 * RAM strategy:
 * ─ App metadata (labels, packages) cached permanently — tiny footprint.
 * ─ Icons stored as SCALED Bitmaps in a size-bounded LruCache (max 4 MB).
 *   AdaptiveIconDrawable layers are rasterised once to a small Bitmap and
 *   the original Drawable is immediately discarded.  This alone reduces
 *   icon memory from ~300 KB/app (full-res AdaptiveIconDrawable) to ~37 KB
 *   (96 × 96 ARGB_8888), a ~8× reduction.
 * ─ AppInfo no longer carries an icon field — adapters call getIcon().
 * ─ LruCache self-manages eviction under memory pressure; no manual clear.
 * ─ Cheap refresh only updates screenTime + favorites; touches zero bitmaps.
 * ─ Full PM rescan only on day change or package install/remove.
 */
object AppRepository {

    @Volatile private var cachedApps: List<AppInfo> = emptyList()
    @Volatile private var cacheValid = false
    @Volatile private var lastKnownDayOfYear: Int = -1

    // ── Icon LruCache — fixed 3 MB upper bound ────────────────────────────────
    // 96 × 96 × 4 bytes = ~36.9 KB per icon → fits ~80 icons in 3 MB.
    private const val MAX_ICON_CACHE_BYTES = 3 * 1024 * 1024 // 3 MB

    private val iconCache = object : LruCache<String, Bitmap>(MAX_ICON_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    /** Constant icon size in pixels — computed once on first load. */
    @Volatile private var iconSizePx: Int = 96

    /**
     * Returns the cached scaled Bitmap for [packageName], or null if not yet
     * loaded or evicted by memory pressure.
     */
    fun getIcon(packageName: String): Bitmap? = iconCache.get(packageName)

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun currentDayOfYear(): Int = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)

    private fun forceResetIfNewDay() {
        val today = currentDayOfYear()
        if (today != lastKnownDayOfYear) {
            cacheValid = false
            lastKnownDayOfYear = today
        }
    }

    /**
     * Rasterises any Drawable into a square Bitmap at [sizePx] × [sizePx].
     * This converts expensive AdaptiveIconDrawable (2 full-res layers) into a
     * single small flat Bitmap — the primary driver of RAM reduction.
     */
    private fun rasterise(drawable: android.graphics.drawable.Drawable, sizePx: Int): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, sizePx, sizePx)
        drawable.draw(canvas)
        return bmp
    }

    // ── Public API ────────────────────────────────────────────────────────────

    suspend fun loadApps(context: Context, prefs: Prefs): List<AppInfo> =
        withContext(Dispatchers.IO) {
            forceResetIfNewDay()

            // Compute icon size once: 44 dp, capped at 96 px for memory.
            if (iconSizePx == 96) {
                val density = context.resources.displayMetrics.density
                iconSizePx = (44f * density).toInt().coerceAtMost(96)
            }

            if (cacheValid && cachedApps.isNotEmpty()) {
                // ── Cheap refresh: only update screenTime + favorites ─────────
                // Icons are already in iconCache from the last full scan.
                // No bitmap work needed at all.
                val screenTime = ScreenTimeHelper.getTodayUsage(context)
                val favorites  = prefs.favoritePackages.toSet()
                cachedApps = cachedApps.map { app ->
                    app.copy(
                        screenTimeMinutes = screenTime[app.packageName] ?: 0L,
                        isFavorite        = app.packageName in favorites
                    )
                }
                return@withContext cachedApps
            }

            // ── Full scan ─────────────────────────────────────────────────────
            val pm      = context.packageManager
            val intent  = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }

            @Suppress("DEPRECATION")
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                PackageManager.GET_META_DATA else 0

            val hidden     = prefs.hiddenPackages
            val favorites  = prefs.favoritePackages.toSet()
            val screenTime = ScreenTimeHelper.getTodayUsage(context)
            val size       = iconSizePx

            val apps = pm.queryIntentActivities(intent, flags)
                .asSequence()
                .filter {
                    it.activityInfo.packageName != context.packageName &&
                    it.activityInfo.packageName !in hidden
                }
                .map { info ->
                    val pkg = info.activityInfo.packageName

                    // Load + rasterise icon only if not already cached.
                    // This avoids re-decoding bitmaps on partial invalidations.
                    if (iconCache.get(pkg) == null) {
                        try {
                            val drawable = info.loadIcon(pm)
                            iconCache.put(pkg, rasterise(drawable, size))
                            // drawable goes out of scope here — GC-eligible immediately
                        } catch (_: Exception) { /* icon stays null in cache */ }
                    }

                    AppInfo(
                        label             = try { info.loadLabel(pm).toString() }
                                            catch (_: Exception) { pkg },
                        packageName       = pkg,
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

    fun invalidate() {
        cacheValid = false
        cachedApps = emptyList()
        iconCache.evictAll()
    }
}
