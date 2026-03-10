package com.minimallauncher

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.minimallauncher.databinding.ActivityMainBinding
import java.util.concurrent.Executors

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
        loadApps()
        registerPackageReceiver()
    }

    private fun setupRecyclerView() {
        appAdapter = AppAdapter { appInfo -> launchApp(appInfo) }

        binding.rvApps.apply {
            layoutManager = LinearLayoutManager(
                this@MainActivity,
                LinearLayoutManager.HORIZONTAL,
                false
            )
            adapter = appAdapter

            // Extreme RAM Optimization: No view caching to prevent background Bitmap retention
            setItemViewCacheSize(0)
            recycledViewPool.setMaxRecycledViews(0, 0)
            
            setHasFixedSize(true)
            itemAnimator = null
            
            // Apply snap helper for smooth snapping
            LinearSnapHelper().attachToRecyclerView(this)
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
                    packageName = info.activityInfo.packageName
                )
            }
            .sortedBy { it.label.lowercase() }

        appAdapter.setApps(allApps)
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

    override fun onStart() {
        super.onStart()
        // Extreme RAM Restoring
        if (allApps.isEmpty()) {
            loadApps()
        }
        if (binding.rvApps.adapter == null) {
            setupRecyclerView()
        }
    }

    override fun onStop() {
        super.onStop()
        // Extreme RAM Optimization: Detach all views, drop references, and purge state
        binding.rvApps.adapter = null
        binding.rvApps.removeAllViews()
        allApps = emptyList() // Brutal data discard
        appAdapter.setApps(emptyList())

        // Force GC to drop ~30-50MB of application instances/views into oblivion
        Runtime.getRuntime().gc()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_UI_HIDDEN) {
            binding.rvApps.adapter = null
            binding.rvApps.removeAllViews()
            allApps = emptyList()
            Runtime.getRuntime().gc()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        appAdapter.shutdown() // Shutdown executors
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
    val packageName: String
)

// ── RecyclerView Adapter ──────────────────────────────────────────────────────

class AppAdapter(
    private val onAppClick: (AppInfo) -> Unit
) : RecyclerView.Adapter<AppAdapter.AppViewHolder>() {

    private val apps = mutableListOf<AppInfo>()
    // Fixed thread pool for smooth asynchronous loading
    private val executor = Executors.newFixedThreadPool(4)
    private val mainHandler = Handler(Looper.getMainLooper())

    fun setApps(newApps: List<AppInfo>) {
        apps.clear()
        apps.addAll(newApps)
        notifyDataSetChanged()
    }

    fun shutdown() {
        executor.shutdownNow()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false)
        return AppViewHolder(view, onAppClick)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val appInfo = apps[position]
        holder.bind(appInfo, executor, mainHandler)
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
        
        // Track the current package to ensure async loads match the current view state
        private var currentPackageName: String? = null

        fun bind(appInfo: AppInfo, executor: java.util.concurrent.ExecutorService, mainHandler: Handler) {
            currentPackageName = appInfo.packageName
            tvLabel.text = appInfo.label
            
            // Clear previous icon to immediately appear clean while loading asynchronously
            ivIcon.setImageDrawable(null)

            executor.execute {
                try {
                    val pm = itemView.context.packageManager
                    val icon = pm.getApplicationIcon(appInfo.packageName)
                    
                    mainHandler.post {
                        // Only set if this ViewHolder hasn't been recycled for another app
                        if (currentPackageName == appInfo.packageName) {
                            ivIcon.setImageDrawable(icon)
                        }
                    }
                } catch (e: Exception) {
                    mainHandler.post {
                        if (currentPackageName == appInfo.packageName) {
                            ivIcon.setImageDrawable(null)
                        }
                    }
                }
            }

            itemView.setOnClickListener { onAppClick(appInfo) }
        }

        fun recycle() {
            currentPackageName = null
            ivIcon.setImageDrawable(null)
        }
    }
}
