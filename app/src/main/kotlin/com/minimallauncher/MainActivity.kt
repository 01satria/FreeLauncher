package com.minimallauncher

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.minimallauncher.databinding.ActivityMainBinding

class MainActivity : Activity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var appAdapter: AppAdapter
    private var allApps = listOf<AppInfo>()
    
    // Battery tracking
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val batteryPct = (level * 100 / scale.toFloat()).toInt()
            updateBatteryText(batteryPct)
        }
    }

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

        setupViewPager()
        loadApps()
        registerReceivers()
    }

    private fun setupViewPager() {
        binding.viewPager.adapter = MainPagerAdapter()
        // Optional: reduce overscroll effect
        val child = binding.viewPager.getChildAt(0)
        if (child is RecyclerView) {
            child.overScrollMode = View.OVER_SCROLL_NEVER
        }
    }

    private fun updateBatteryText(pct: Int) {
        // This will be called when the home page view is bound or when battery changes
        // Since we are using a simple PagerAdapter, we might need a reference or just find by tag/id if accessible
        // For simplicity in this two-page setup, we'll manually find the view when battery updates
        // Note: ViewPager2 views are managed by RecyclerView, so it's better to update data if using a proper fragment/state setup
        // But for "extreme lightness" we'll just try to find the view in the current hierarchy
        findViewById<TextView>(R.id.tv_battery)?.text = "Battery: $pct%"
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

        // Update adapter if it's already bound (it's in the second page)
        findViewById<RecyclerView>(R.id.rv_apps_vertical)?.let {
            (it.adapter as? AppAdapter)?.setApps(allApps)
        }
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

    private fun registerReceivers() {
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        val packageFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(packageReceiver, packageFilter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(packageReceiver, packageFilter)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(batteryReceiver)
        try { unregisterReceiver(packageReceiver) } catch (_: Exception) {}
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (binding.viewPager.currentItem != 0) {
            binding.viewPager.currentItem = 0
        }
    }

    // ── Pager Adapter ──────────────────────────────────────────────────────────

    inner class MainPagerAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemViewType(position: Int): Int = position
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val layoutId = if (viewType == 0) R.layout.page_home else R.layout.page_apps
            val view = LayoutInflater.from(parent.context).inflate(layoutId, parent, false)
            return object : RecyclerView.ViewHolder(view) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (position == 1) {
                val rv = holder.itemView.findViewById<RecyclerView>(R.id.rv_apps_vertical)
                val etSearch = holder.itemView.findViewById<EditText>(R.id.et_search)
                
                appAdapter = AppAdapter { appInfo -> launchApp(appInfo) }
                rv.layoutManager = LinearLayoutManager(holder.itemView.context)
                rv.adapter = appAdapter
                appAdapter.setApps(allApps)

                etSearch.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                        val filtered = allApps.filter { it.label.contains(s ?: "", ignoreCase = true) }
                        appAdapter.setApps(filtered)
                    }
                    override fun afterTextChanged(s: Editable?) {}
                })
            }
        }

        override fun getItemCount(): Int = 2
    }
}

// ── Data Model ────────────────────────────────────────────────────────────────

data class AppInfo(
    val label: String,
    val packageName: String
)

// ── RecyclerView Adapter for Apps ─────────────────────────────────────────────

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
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
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
        private val tvLabel: TextView = itemView.findViewById(R.id.tv_label)

        fun bind(appInfo: AppInfo) {
            tvLabel.text = appInfo.label
            itemView.setOnClickListener { onAppClick(appInfo) }
        }
    }
}
