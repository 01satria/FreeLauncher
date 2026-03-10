package com.flowlauncher

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.flowlauncher.databinding.ActivityMainBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.*

class MainActivity : FragmentActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: Prefs

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var searchJob: Job? = null

    // Fragments
    private lateinit var homeFragment: HomeFragment
    private lateinit var feedFragment: FeedFragment

    // App drawer
    private val drawerAdapter = DrawerAppAdapter(
        onAppClick     = ::launchApp,
        onAppLongClick = ::showAppOptions
    )
    private lateinit var drawerSheet: BottomSheetBehavior<View>
    private var currentCategory = AppCategory.ALL
    private var allDrawerApps: List<AppInfo> = emptyList()

    companion object {
        const val PAGE_FEED = 0
        const val PAGE_HOME = 1
    }

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            AppRepository.invalidate()
            if (::homeFragment.isInitialized) homeFragment.loadHomeApps()
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        window.setBackgroundDrawableResource(android.R.color.transparent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(true)
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViewPager()
        setupDrawer()
        setupPageDots()
        registerReceivers()
    }

    override fun onResume() {
        super.onResume()
        updateDots(binding.viewPager.currentItem)
    }

    // ── ViewPager2: Feed (0) ← swipe left | Home (1) default ─────────────────

    private fun setupViewPager() {
        homeFragment = HomeFragment().also { it.onOpenDrawer = ::openDrawer }
        feedFragment = FeedFragment()

        binding.viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = 2
            override fun createFragment(pos: Int) = when (pos) {
                PAGE_FEED -> feedFragment
                else      -> homeFragment
            }
        }
        // Default to Home page
        binding.viewPager.setCurrentItem(PAGE_HOME, false)

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(pos: Int) {
                updateDots(pos)
                // Close drawer if open when user swipes pages
                if (drawerSheet.state != BottomSheetBehavior.STATE_HIDDEN) closeDrawer()
            }
        })
    }

    // ── Page dots indicator ───────────────────────────────────────────────────

    private fun setupPageDots() = updateDots(PAGE_HOME)

    private fun updateDots(page: Int) {
        binding.dotFeed.alpha = if (page == PAGE_FEED) 1f else 0.3f
        binding.dotHome.alpha = if (page == PAGE_HOME) 1f else 0.3f
    }

    // ── App Drawer (BottomSheet) ──────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDrawer() {
        drawerSheet = BottomSheetBehavior.from(binding.drawerSheet)
        drawerSheet.state         = BottomSheetBehavior.STATE_HIDDEN
        drawerSheet.peekHeight    = 0
        drawerSheet.isHideable    = true
        drawerSheet.skipCollapsed = true

        drawerSheet.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onSlide(v: View, offset: Float) {}
            override fun onStateChanged(v: View, state: Int) {
                if (state == BottomSheetBehavior.STATE_HIDDEN) {
                    hideKeyboard()
                    binding.etSearch.clearFocus()
                    binding.etSearch.setText("")
                    binding.drawerDim.visibility = View.GONE
                    binding.viewPager.isUserInputEnabled = true
                }
            }
        })

        binding.rvDrawerApps.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = drawerAdapter
            setItemViewCacheSize(3)
            setHasFixedSize(false)
            overScrollMode = View.OVER_SCROLL_NEVER
            itemAnimator = null
        }

        setupCategoryTabs()

        // Keyboard only on explicit tap
        binding.etSearch.showSoftInputOnFocus = false
        binding.etSearch.setOnClickListener { showKeyboard() }
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchJob?.cancel()
                searchJob = scope.launch {
                    delay(100)
                    filterApps(s?.toString().orEmpty())
                }
            }
        })

        binding.drawerDim.setOnClickListener { closeDrawer() }

        // Pre-load app list
        scope.launch {
            allDrawerApps = try { AppRepository.loadApps(this@MainActivity, prefs) }
                            catch (_: Exception) { emptyList() }
        }
    }

    private fun setupCategoryTabs() {
        val tabs = binding.tabCategories
        tabs.removeAllTabs()
        AppCategory.values().forEach { cat ->
            tabs.addTab(tabs.newTab().setText(cat.displayName))
        }
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                currentCategory = AppCategory.values()[tab.position]
                filterApps(binding.etSearch.text.toString())
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    private fun filterApps(query: String) {
        val base = when (currentCategory) {
            AppCategory.ALL       -> allDrawerApps
            AppCategory.MOST_USED -> allDrawerApps
                .filter { it.screenTimeMinutes > 0 }
                .sortedByDescending { it.screenTimeMinutes }
            else -> allDrawerApps.filter { it.getCategory() == currentCategory }
        }
        val filtered = if (query.isBlank()) base
                       else base.filter { it.label.contains(query, ignoreCase = true) }
        drawerAdapter.setApps(filtered)
        if (query.isNotBlank() && filtered.size == 1) launchApp(filtered[0])
    }

    fun openDrawer() {
        // Disable ViewPager swipe while drawer is open (prevents conflict)
        binding.viewPager.isUserInputEnabled = false
        binding.drawerDim.alpha = 0f
        binding.drawerDim.visibility = View.VISIBLE
        binding.drawerDim.animate().alpha(1f).setDuration(200).start()
        drawerSheet.state = BottomSheetBehavior.STATE_EXPANDED

        // Refresh app list
        scope.launch {
            allDrawerApps = try { AppRepository.loadApps(this@MainActivity, prefs) }
                            catch (_: Exception) { emptyList() }
            filterApps(binding.etSearch.text.toString())
        }
    }

    private fun closeDrawer() {
        hideKeyboard()
        binding.etSearch.clearFocus()
        binding.drawerDim.animate().alpha(0f).setDuration(150)
            .withEndAction { binding.drawerDim.visibility = View.GONE }.start()
        drawerSheet.state = BottomSheetBehavior.STATE_HIDDEN
        binding.viewPager.isUserInputEnabled = true
    }

    // ── App launch & options ──────────────────────────────────────────────────

    private fun launchApp(app: AppInfo) {
        closeDrawer()
        scope.launch {
            delay(120)
            try {
                packageManager.getLaunchIntentForPackage(app.packageName)
                    ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED) }
                    ?.let { startActivity(it) }
                    ?: Toast.makeText(this@MainActivity, "Cannot open ${app.label}", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) { AppRepository.invalidate() }
        }
    }

    // Public so HomeFragment can delegate long-press actions to MainActivity
    fun showAppOptionsPublic(app: AppInfo, anchor: View) = showAppOptions(app, anchor)

    private fun showAppOptions(app: AppInfo, @Suppress("UNUSED_PARAMETER") anchor: View) {
        android.app.AlertDialog.Builder(this)
            .setTitle(app.label)
            .setItems(arrayOf(
                if (app.isFavorite) "Remove from Home" else "Add to Home",
                "Hide App", "App Info", "Uninstall"
            )) { _, which ->
                when (which) {
                    0 -> toggleFavorite(app)
                    1 -> hideApp(app)
                    2 -> openAppInfo(app)
                    3 -> uninstallApp(app)
                }
            }.show()
    }

    private fun toggleFavorite(app: AppInfo) {
        val list = prefs.favoritePackages.toMutableList()
        if (app.packageName in list) list.remove(app.packageName) else list.add(app.packageName)
        prefs.favoritePackages = list
        AppRepository.invalidate()
        homeFragment.loadHomeApps()
    }

    private fun hideApp(app: AppInfo) {
        prefs.hiddenPackages = prefs.hiddenPackages.toMutableSet().also { it.add(app.packageName) }
        AppRepository.invalidate()
        homeFragment.loadHomeApps()
    }

    private fun openAppInfo(app: AppInfo) {
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", app.packageName, null)
            })
        } catch (_: Exception) {}
    }

    private fun uninstallApp(app: AppInfo) {
        try {
            startActivity(Intent(Intent.ACTION_UNINSTALL_PACKAGE,
                Uri.fromParts("package", app.packageName, null)).apply {
                putExtra(Intent.EXTRA_RETURN_RESULT, false)
            })
        } catch (_: Exception) {
            try { startActivity(Intent(Intent.ACTION_DELETE,
                Uri.fromParts("package", app.packageName, null))) }
            catch (_: Exception) {}
        }
    }

    // ── Keyboard ──────────────────────────────────────────────────────────────

    private fun showKeyboard() {
        binding.etSearch.requestFocus()
        (getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.showSoftInput(binding.etSearch, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideKeyboard() {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
    }

    // ── Receivers ─────────────────────────────────────────────────────────────

    private fun registerReceivers() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                registerReceiver(packageReceiver, filter, Context.RECEIVER_EXPORTED)
            else
                registerReceiver(packageReceiver, filter)
        } catch (_: Exception) {}
    }

    @Deprecated("Deprecated")
    override fun onBackPressed() {
        when {
            drawerSheet.state != BottomSheetBehavior.STATE_HIDDEN ->
                closeDrawer()
            binding.viewPager.currentItem != PAGE_HOME ->
                binding.viewPager.setCurrentItem(PAGE_HOME, true)
            // On home page: do nothing — launcher must not exit
        }
    }

    override fun onDestroy() {
        scope.cancel()
        try { unregisterReceiver(packageReceiver) } catch (_: Exception) {}
        super.onDestroy()
    }
}
