package com.minimallauncher

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
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
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.minimallauncher.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : Activity() {

    private lateinit var binding: ActivityMainBinding
    private val appAdapter = AppAdapter { appInfo, view -> launchApp(appInfo, view) }
    private var allApps = listOf<AppInfo>()

    // Coroutine scope tied to Activity lifecycle — cancelled in onDestroy
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    // Debounce job for search input
    private var searchJob: Job? = null

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            loadAppsAsync()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        window.setBackgroundDrawableResource(android.R.color.transparent)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViewPager()
        loadAppsAsync()
        registerReceivers()
    }

    private fun setupViewPager() {
        binding.viewPager.adapter = MainPagerAdapter()
        val child = binding.viewPager.getChildAt(0)
        if (child is RecyclerView) {
            child.overScrollMode = View.OVER_SCROLL_NEVER
        }
    }

    /**
     * Load installed apps on the IO dispatcher — main thread is never blocked,
     * so the drawer scroll stays buttery smooth even on first open.
     */
    private fun loadAppsAsync() {
        scope.launch {
            val pm = packageManager
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                PackageManager.MATCH_ALL else 0

            val loaded = withContext(Dispatchers.IO) {
                pm.queryIntentActivities(intent, flags)
                    .filter { it.activityInfo.packageName != packageName }
                    .map { info ->
                        AppInfo(
                            label = info.loadLabel(pm).toString(),
                            packageName = info.activityInfo.packageName
                        )
                    }
                    .sortedBy { it.label.lowercase() }
            }

            allApps = loaded
            appAdapter.setApps(loaded)
        }
    }

    private fun launchApp(appInfo: AppInfo, view: View) {
        view.animate()
            .scaleX(1.05f).scaleY(1.05f)
            .setDuration(80)
            .withEndAction {
                view.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
                try {
                    packageManager.getLaunchIntentForPackage(appInfo.packageName)?.apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                        startActivity(this)
                        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                    }
                } catch (_: Exception) { loadAppsAsync() }
            }.start()
    }

    private fun registerReceivers() {
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
        scope.cancel()
        try { unregisterReceiver(packageReceiver) } catch (_: Exception) {}
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (binding.viewPager.currentItem != 0) binding.viewPager.currentItem = 0
    }

    // ── Pager Adapter ──────────────────────────────────────────────────────────

    inner class MainPagerAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        override fun getItemViewType(position: Int) = position

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val layoutId = if (viewType == 0) R.layout.page_home else R.layout.page_apps
            val view = LayoutInflater.from(parent.context).inflate(layoutId, parent, false)
            val holder = object : RecyclerView.ViewHolder(view) {}

            // Configure the apps page ONCE here, never again in onBindViewHolder
            if (viewType == 1) {
                val rv = view.findViewById<RecyclerView>(R.id.rv_apps_vertical)
                val etSearch = view.findViewById<EditText>(R.id.et_search)

                rv.apply {
                    layoutManager = LinearLayoutManager(view.context)
                    adapter = appAdapter
                    setHasFixedSize(true)
                    setItemViewCacheSize(4)           // sane default — saves RAM vs 20
                    overScrollMode = View.OVER_SCROLL_NEVER
                    // Default TOUCH_SLOP_DEFAULT — NOT paging slop, which kills scroll feel
                }

                etSearch.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun afterTextChanged(s: Editable?) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                        // Debounce 120 ms — avoids thrashing DiffUtil on every keystroke
                        searchJob?.cancel()
                        searchJob = scope.launch {
                            delay(120)
                            val query = s?.toString().orEmpty()
                            val filtered = if (query.isEmpty()) allApps
                                           else allApps.filter { it.label.contains(query, ignoreCase = true) }
                            appAdapter.setApps(filtered)
                            if (filtered.size == 1) launchApp(filtered[0], rv)
                        }
                    }
                })
            }
            return holder
        }

        // Nothing to bind — fully set up in onCreateViewHolder
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {}
        override fun getItemCount() = 2
    }
}

// ── Data Model ────────────────────────────────────────────────────────────────

data class AppInfo(val label: String, val packageName: String)

// ── DiffUtil callback ─────────────────────────────────────────────────────────

private class AppDiffCallback(
    private val old: List<AppInfo>,
    private val new: List<AppInfo>
) : DiffUtil.Callback() {
    override fun getOldListSize() = old.size
    override fun getNewListSize() = new.size
    override fun areItemsTheSame(o: Int, n: Int) = old[o].packageName == new[n].packageName
    override fun areContentsTheSame(o: Int, n: Int) = old[o] == new[n]
}

// ── RecyclerView Adapter ──────────────────────────────────────────────────────

class AppAdapter(
    private val onAppClick: (AppInfo, View) -> Unit
) : RecyclerView.Adapter<AppAdapter.AppViewHolder>() {

    init { setHasStableIds(true) }

    private val apps = mutableListOf<AppInfo>()

    /** Smooth incremental update — no full notifyDataSetChanged flicker. */
    fun setApps(newApps: List<AppInfo>) {
        val diff = DiffUtil.calculateDiff(AppDiffCallback(apps, newApps))
        apps.clear()
        apps.addAll(newApps)
        diff.dispatchUpdatesTo(this)
    }

    override fun getItemId(position: Int) = apps[position].packageName.hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        return AppViewHolder(view, onAppClick)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) = holder.bind(apps[position])
    override fun getItemCount() = apps.size

    class AppViewHolder(
        itemView: View,
        private val onAppClick: (AppInfo, View) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val tvLabel: TextView = itemView.findViewById(R.id.tv_label)
        fun bind(appInfo: AppInfo) {
            tvLabel.text = appInfo.label
            itemView.setOnClickListener { onAppClick(appInfo, itemView) }
        }
    }
}
