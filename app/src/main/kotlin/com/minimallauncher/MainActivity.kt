package com.minimallauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.minimallauncher.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var appAdapter: AppAdapter

    // BroadcastReceiver untuk update daftar app jika ada install/uninstall
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            loadApps()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Tampilkan wallpaper di balik launcher
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)

        // Pastikan tidak ada background yang menghalangi wallpaper
        window.setBackgroundDrawableResource(android.R.color.transparent)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        loadApps()
        registerPackageReceiver()
    }

    private fun setupRecyclerView() {
        appAdapter = AppAdapter { appInfo ->
            launchApp(appInfo)
        }

        binding.rvApps.apply {
            layoutManager = LinearLayoutManager(
                this@MainActivity,
                LinearLayoutManager.HORIZONTAL,
                false
            )
            adapter = appAdapter
            setHasFixedSize(true)
            // Tidak ada animasi untuk lebih ringan
            itemAnimator = null
            // Snap ke item saat scroll selesai
            addOnScrollListener(SnapScrollListener())
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

        val resolveInfoList: List<ResolveInfo> = pm.queryIntentActivities(intent, flags)

        val apps = resolveInfoList
            .filter { it.activityInfo.packageName != packageName } // Exclude launcher sendiri
            .map { resolveInfo ->
                AppInfo(
                    label = resolveInfo.loadLabel(pm).toString(),
                    packageName = resolveInfo.activityInfo.packageName,
                    activityName = resolveInfo.activityInfo.name,
                    icon = resolveInfo.loadIcon(pm)
                )
            }
            .sortedBy { it.label.lowercase() }

        appAdapter.setApps(apps)
    }

    private fun launchApp(appInfo: AppInfo) {
        try {
            val intent = packageManager.getLaunchIntentForPackage(appInfo.packageName)
            intent?.let {
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                startActivity(it)
            }
        } catch (e: Exception) {
            // App mungkin sudah di-uninstall, reload
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
        registerReceiver(packageReceiver, filter)
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(packageReceiver) } catch (_: Exception) {}
    }

    // Tidak ada service, tidak ada background task
    // Launcher tetap ada di foreground atau tidak aktif sama sekali

    override fun onBackPressed() {
        // Tidak lakukan apa-apa di home screen
    }
}

// ─── Data Model ──────────────────────────────────────────────────────────────

data class AppInfo(
    val label: String,
    val packageName: String,
    val activityName: String,
    val icon: Drawable
)

// ─── RecyclerView Adapter ─────────────────────────────────────────────────────

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

    override fun getItemCount() = apps.size

    class AppViewHolder(
        itemView: View,
        private val onAppClick: (AppInfo) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val ivIcon: ImageView = itemView.findViewById(R.id.iv_icon)
        private val tvLabel: TextView = itemView.findViewById(R.id.tv_label)

        fun bind(appInfo: AppInfo) {
            ivIcon.setImageDrawable(appInfo.icon)
            tvLabel.text = appInfo.label

            itemView.setOnClickListener {
                onAppClick(appInfo)
            }
        }
    }
}

// ─── Snap Scroll Listener ─────────────────────────────────────────────────────
// Snap sederhana tanpa dependency PagerSnapHelper agar lebih ringan

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
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val firstVisible = layoutManager.findFirstVisibleItemPosition()
        val lastVisible = layoutManager.findLastVisibleItemPosition()

        if (firstVisible == RecyclerView.NO_ID) return

        // Cari item yang paling banyak terlihat
        var bestPosition = firstVisible
        var bestVisibility = 0

        for (i in firstVisible..lastVisible) {
            val child = layoutManager.findViewByPosition(i) ?: continue
            val childRect = android.graphics.Rect()
            child.getGlobalVisibleRect(childRect)
            val visible = childRect.width()
            if (visible > bestVisibility) {
                bestVisibility = visible
                bestPosition = i
            }
        }

        recyclerView.smoothScrollToPosition(bestPosition)
    }
}
