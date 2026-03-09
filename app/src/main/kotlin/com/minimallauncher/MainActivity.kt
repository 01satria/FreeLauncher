package com.minimallauncher

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.minimallauncher.databinding.ActivityMainBinding

class MainActivity : Activity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var appAdapter: AppAdapter
    
    // Memory efficient cache of loaded apps (just strings)
    private var allApps = listOf<AppInfo>()

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            loadApps()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        window.setBackgroundDrawableResource(android.R.color.transparent)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupAlphabetStrip()
        loadApps()
        registerPackageReceiver()
    }

    private fun setupRecyclerView() {
        appAdapter = AppAdapter { appInfo -> launchApp(appInfo) }

        val displayMetrics = resources.displayMetrics
        val itemWidthPx = 80 * displayMetrics.density
        val spanCount = (displayMetrics.widthPixels / itemWidthPx).toInt().coerceAtLeast(1)

        binding.rvApps.apply {
            layoutManager = GridLayoutManager(
                this@MainActivity,
                spanCount,
                GridLayoutManager.VERTICAL,
                false
            )
            adapter = appAdapter

            // Extreme RAM Optimization: No view caching to prevent background Bitmap retention
            setItemViewCacheSize(0)
            recycledViewPool.setMaxRecycledViews(0, 0)
            
            setHasFixedSize(true)
            itemAnimator = null
            // No touch scrolling
            setOnTouchListener { _, _ -> true }
        }
    }

    private fun setupAlphabetStrip() {
        binding.alphabetStrip.onLetterSelected = { letter ->
            filterAppsByLetter(letter)
        }
    }

    private fun filterAppsByLetter(letter: Char) {
        val filtered = if (letter == '#') {
            // Numbers or symbols
            allApps.filter { it.label.firstOrNull()?.isLetter() == false }
        } else {
            // Matching letters (ignore case)
            allApps.filter { it.label.firstOrNull()?.equals(letter, ignoreCase = true) == true }
        }
        appAdapter.setApps(filtered)
        if (filtered.isNotEmpty()) {
            binding.rvApps.scrollToPosition(0)
        }
    }

    private fun loadApps() {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PackageManager.MATCH_ALL
        } else {
            0
        }

        allApps = pm.queryIntentActivities(intent, flags)
            .filter { it.activityInfo.packageName != packageName }
            .map { info ->
                AppInfo(
                    label = info.loadLabel(pm).toString(),
                    packageName = info.activityInfo.packageName,
                    activityName = info.activityInfo.name
                )
            }
            .sortedBy { it.label.lowercase() }
            
        val distinctLetters = allApps.mapNotNull { it.label.firstOrNull()?.uppercaseChar() }
            .map { if (it.isLetter()) it else '#' }
            .distinct()
            .sorted()

        binding.alphabetStrip.setLetters(distinctLetters)

        // Default: Show 'A' or the closest first letter items or clear if none
        val firstLetter = allApps.firstOrNull()?.label?.firstOrNull()?.uppercaseChar() ?: 'A'
        filterAppsByLetter(firstLetter)
    }

    private fun launchApp(appInfo: AppInfo) {
        try {
            val intent = packageManager.getLaunchIntentForPackage(appInfo.packageName)
            intent?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                startActivity(this)
            }
        } catch (e: Exception) {
            loadApps()
        }
    }

    private fun registerPackageReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(packageReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(packageReceiver, filter)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(packageReceiver) } catch (_: Exception) {}
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Tidak lakukan apa-apa di home screen
    }
}

// ── Data Model ────────────────────────────────────────────────────────────────

data class AppInfo(
    val label: String,
    val packageName: String,
    val activityName: String
)

// ── RecyclerView Adapter ──────────────────────────────────────────────────────

class AppAdapter(
    private val onAppClick: (AppInfo) -> Unit
) : RecyclerView.Adapter<AppAdapter.AppViewHolder>() {

    private val apps = mutableListOf<AppInfo>()

    fun setApps(newApps: List<AppInfo>) {
        apps.clear()
        apps.addAll(newApps)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false)
        return AppViewHolder(view, onAppClick)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(apps[position])
    }

    override fun onViewRecycled(holder: AppViewHolder) {
        holder.recycle()
        super.onViewRecycled(holder)
    }

    override fun getItemCount() = apps.size

    class AppViewHolder(
        itemView: View,
        private val onAppClick: (AppInfo) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val ivIcon: ImageView = itemView.findViewById(R.id.iv_icon)
        private val tvLabel: TextView = itemView.findViewById(R.id.tv_label)

        fun bind(appInfo: AppInfo) {
            try {
                val icon = itemView.context.packageManager.getApplicationIcon(appInfo.packageName)
                ivIcon.setImageDrawable(icon)
            } catch (e: Exception) {
                ivIcon.setImageDrawable(null)
            }
            tvLabel.text = appInfo.label
            itemView.setOnClickListener { onAppClick(appInfo) }
        }

        fun recycle() {
            ivIcon.setImageDrawable(null)
        }
    }
}

// ── Snap Scroll Listener ──────────────────────────────────────────────────────

class SnapScrollListener : RecyclerView.OnScrollListener() {

    private var isScrolling = false

    override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
        super.onScrollStateChanged(recyclerView, newState)
        when (newState) {
            RecyclerView.SCROLL_STATE_DRAGGING -> isScrolling = true
            RecyclerView.SCROLL_STATE_IDLE -> {
                if (isScrolling) {
                    isScrolling = false
                    snapToNearest(recyclerView)
                }
            }
        }
    }

    private fun snapToNearest(recyclerView: RecyclerView) {
        val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val first = lm.findFirstVisibleItemPosition()
        val last = lm.findLastVisibleItemPosition()
        if (first == RecyclerView.NO_POSITION) return

        var bestPos = first
        var bestVis = 0

        for (i in first..last) {
            val child = lm.findViewByPosition(i) ?: continue
            val rect = Rect()
            child.getGlobalVisibleRect(rect)
            val vis = rect.width()
            if (vis > bestVis) {
                bestVis = vis
                bestPos = i
            }
        }
        recyclerView.smoothScrollToPosition(bestPos)
    }
}
