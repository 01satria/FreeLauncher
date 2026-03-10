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
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.minimallauncher.databinding.ActivityMainBinding

class MainActivity : Activity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var appAdapter: AppAdapter
    private lateinit var categoryAdapter: CategoryAdapter
    private var allApps = listOf<AppInfo>()
    private var categorizedApps = listOf<CategoryInfo>()
    
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
        val child = binding.viewPager.getChildAt(0)
        if (child is RecyclerView) {
            child.overScrollMode = View.OVER_SCROLL_NEVER
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
                val appInfo = info.activityInfo.applicationInfo
                val category = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    appInfo.category
                } else {
                    -1
                }
                AppInfo(
                    label = info.loadLabel(pm).toString(),
                    packageName = info.activityInfo.packageName,
                    category = category
                )
            }
            .sortedBy { it.label.lowercase() }

        // Group into categories
        val groups = allApps.groupBy { getCategoryName(it.category) }
        categorizedApps = groups.map { (name, apps) -> CategoryInfo(name, apps) }
            .sortedBy { it.name }

        findViewById<RecyclerView>(R.id.rv_apps_vertical)?.let {
            if (it.adapter is CategoryAdapter) {
                (it.adapter as CategoryAdapter).setCategories(categorizedApps)
            } else if (it.adapter is AppAdapter) {
                (it.adapter as AppAdapter).setApps(allApps)
            }
        }
    }

    private fun getCategoryName(category: Int): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            when (category) {
                android.content.pm.ApplicationInfo.CATEGORY_GAME -> "Games"
                android.content.pm.ApplicationInfo.CATEGORY_AUDIO -> "Music"
                android.content.pm.ApplicationInfo.CATEGORY_VIDEO -> "Entertainment"
                android.content.pm.ApplicationInfo.CATEGORY_IMAGE -> "Photography"
                android.content.pm.ApplicationInfo.CATEGORY_SOCIAL -> "Social"
                android.content.pm.ApplicationInfo.CATEGORY_NEWS -> "News"
                android.content.pm.ApplicationInfo.CATEGORY_MAPS -> "Navigation"
                android.content.pm.ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Productivity"
                else -> "Others"
            }
        } else {
            "Apps"
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
        try { unregisterReceiver(packageReceiver) } catch (_: Exception) {}
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val rv = findViewById<RecyclerView>(R.id.rv_apps_vertical)
        if (binding.viewPager.currentItem != 0) {
            if (rv != null && rv.adapter is AppAdapter) {
                // Return to categories from folder or search
                rv.layoutManager = GridLayoutManager(this, 2)
                rv.adapter = categoryAdapter
            } else {
                binding.viewPager.currentItem = 0
            }
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
                categoryAdapter = CategoryAdapter { category ->
                    // Show apps in this category
                    rv.layoutManager = GridLayoutManager(holder.itemView.context, 4)
                    rv.adapter = appAdapter
                    appAdapter.setApps(category.apps)
                }

                rv.layoutManager = GridLayoutManager(holder.itemView.context, 2)
                rv.adapter = categoryAdapter
                categoryAdapter.setCategories(categorizedApps)

                etSearch.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                        val query = s?.toString() ?: ""
                        if (query.isEmpty()) {
                            // Back to categories if search is cleared
                            rv.layoutManager = GridLayoutManager(holder.itemView.context, 2)
                            rv.adapter = categoryAdapter
                            categoryAdapter.setCategories(categorizedApps)
                            return
                        }
                        
                        // Flat search mode
                        rv.layoutManager = GridLayoutManager(holder.itemView.context, 4)
                        rv.adapter = appAdapter
                        val filtered = allApps.filter { it.label.contains(query, ignoreCase = true) }
                        appAdapter.setApps(filtered)
                        
                        if (filtered.size == 1) {
                            launchApp(filtered[0])
                        }
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
    val packageName: String,
    val category: Int = -1
)

data class CategoryInfo(
    val name: String,
    val apps: List<AppInfo>
)

// ── RecyclerView Adapter for Categories ──────────────────────────────────────

class CategoryAdapter(
    private val onCategoryClick: (CategoryInfo) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder>() {

    private val categories = mutableListOf<CategoryInfo>()

    fun setCategories(newCategories: List<CategoryInfo>) {
        categories.clear()
        categories.addAll(newCategories)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_category, parent, false)
        return CategoryViewHolder(view, onCategoryClick)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        holder.bind(categories[position])
    }

    override fun getItemCount() = categories.size

    class CategoryViewHolder(
        itemView: View,
        private val onCategoryClick: (CategoryInfo) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val glPreview: android.widget.GridLayout = itemView.findViewById(R.id.gl_preview)
        private val tvName: TextView = itemView.findViewById(R.id.tv_category_name)

        fun bind(category: CategoryInfo) {
            tvName.text = category.name
            
            // Clear and populate preview with first 4 apps
            glPreview.removeAllViews()
            category.apps.take(4).forEach { app ->
                val tinyIcon = LayoutInflater.from(itemView.context).inflate(R.layout.item_app, glPreview, false)
                // Resize for preview
                tinyIcon.layoutParams.width = ViewGroup.LayoutParams.WRAP_CONTENT
                tinyIcon.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                tinyIcon.setPadding(2, 2, 2, 2)
                val innerBox = tinyIcon.findViewById<View>(R.id.tv_label).parent as View
                innerBox.layoutParams.width = (48 * itemView.context.resources.displayMetrics.density).toInt()
                innerBox.layoutParams.height = (48 * itemView.context.resources.displayMetrics.density).toInt()
                
                val tv = tinyIcon.findViewById<TextView>(R.id.tv_label)
                tv.textSize = 8f
                val shortName = if (app.label.length > 5) app.label.take(5) else app.label
                tv.text = shortName
                
                glPreview.addView(tinyIcon)
            }
            
            itemView.setOnClickListener { onCategoryClick(category) }
        }
    }
}

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
            val shortName = if (appInfo.label.length > 5) {
                appInfo.label.take(5)
            } else {
                appInfo.label
            }
            tvLabel.text = shortName
            itemView.setOnClickListener { onAppClick(appInfo) }
        }
    }
}
