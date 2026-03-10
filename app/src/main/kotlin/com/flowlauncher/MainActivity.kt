package com.flowlauncher

import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.recyclerview.widget.LinearLayoutManager
import com.flowlauncher.databinding.ActivityMainBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.*

class MainActivity : Activity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: Prefs

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var loadJob: Job? = null
    private var searchJob: Job? = null

    private lateinit var homeAdapter: HomeAppAdapter
    private val drawerAdapter = DrawerAppAdapter(
        onAppClick = ::launchApp,
        onAppLongClick = ::showAppOptions
    )
    private lateinit var todoAdapter: TodoAdapter

    private lateinit var drawerSheet: BottomSheetBehavior<View>
    private var touchStartY = 0f
    private var currentCategory = AppCategory.ALL
    private var allDrawerApps: List<AppInfo> = emptyList()

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            AppRepository.invalidate()
            loadData()
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

        // Apply theme AFTER setContentView so views exist
        applyTheme()

        homeAdapter = HomeAppAdapter(
            showIcons = prefs.showIcons,
            showScreenTime = prefs.showScreenTime,
            alignment = gravityFromPref(),
            fontSize = prefs.fontSize.toFloat(),
            onAppClick = ::launchApp,
            onAppLongClick = ::showAppOptions
        )
        todoAdapter = TodoAdapter { pos -> deleteTodo(pos) }

        setupHomeScreen()
        setupDrawer()
        setupTodoWidget()
        setupSwipeGesture()
        setupClockFormat()
        registerReceivers()
        loadData()
        updateScreenTimeHint()
    }

    override fun onResume() {
        super.onResume()
        // Re-apply everything on return from Settings
        applyTheme()
        applyAlignment()
        setupClockFormat()
        updateScreenTimeHint()
        scope.launch {
            delay(200)
            loadData()
        }
    }

    // ── Theme ──────────────────────────────────────────────────────────────────
    // Applied to window background AND all themed views so switching works at runtime.

    private fun applyTheme() {
        val isLight = prefs.theme == Prefs.THEME_LIGHT
        val isOled  = prefs.theme == Prefs.THEME_OLED

        val bgColor = when {
            isLight -> Color.parseColor("#F2F2F2")
            isOled  -> Color.BLACK
            else    -> Color.parseColor("#0D0D0D")   // dark
        }
        window.decorView.setBackgroundColor(bgColor)
        binding.homeRoot.setBackgroundColor(bgColor)

        val textPrimary   = if (isLight) Color.parseColor("#111111") else Color.WHITE
        val textSecondary = if (isLight) Color.parseColor("#66000000") else Color.parseColor("#99FFFFFF")
        val iconTint      = if (isLight) Color.parseColor("#88000000") else Color.parseColor("#AAFFFFFF")
        val hintColor     = if (isLight) Color.parseColor("#55000000") else Color.parseColor("#55FFFFFF")

        binding.tvClock.setTextColor(textPrimary)
        binding.tvDate.setTextColor(textSecondary)
        binding.tvGreeting.setTextColor(textSecondary)
        binding.tvUsageToday.setTextColor(textSecondary)
        binding.tvScreenTimeHint.setTextColor(hintColor)
        binding.tvTodoHeader.setTextColor(textSecondary)
        binding.btnSettings.setColorFilter(iconTint)
        binding.btnFocus.setColorFilter(iconTint)
        binding.btnAddTodo.setColorFilter(iconTint)
        binding.ivSwipeHint.setColorFilter(
            if (isLight) Color.parseColor("#33000000") else Color.parseColor("#44FFFFFF")
        )
    }

    // ── Home Screen ───────────────────────────────────────────────────────────

    private fun setupHomeScreen() {
        binding.rvHomeApps.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = homeAdapter
            setHasFixedSize(false)
            itemAnimator = null
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.btnFocus.setOnClickListener {
            startActivity(Intent(this, FocusActivity::class.java))
        }
        applyAlignment()
    }

    private fun applyAlignment() {
        val g = gravityFromPref()
        binding.tvClock.gravity = g
        binding.tvDate.gravity  = g
        binding.tvGreeting.gravity = g
        binding.tvUsageToday.gravity = g
    }

    private fun gravityFromPref(): Int = when (prefs.alignment) {
        Prefs.ALIGN_CENTER -> Gravity.CENTER_HORIZONTAL
        Prefs.ALIGN_RIGHT  -> Gravity.END
        else               -> Gravity.START
    }

    private fun setupClockFormat() {
        if (prefs.use24Hour) {
            binding.tvClock.format24Hour = "HH:mm"
            binding.tvClock.format12Hour = null
        } else {
            binding.tvClock.format12Hour = "hh:mm"
            binding.tvClock.format24Hour = null
        }
        binding.tvDate.visibility = if (prefs.showDate) View.VISIBLE else View.GONE
    }

    private fun updateScreenTimeHint() {
        if (!ScreenTimeHelper.hasPermission(this)) {
            binding.tvScreenTimeHint.visibility = View.VISIBLE
            binding.tvScreenTimeHint.setOnClickListener {
                try { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                catch (_: Exception) {}
            }
        } else {
            binding.tvScreenTimeHint.visibility = View.GONE
        }
    }

    // ── App Drawer ────────────────────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDrawer() {
        drawerSheet = BottomSheetBehavior.from(binding.drawerSheet)
        drawerSheet.state     = BottomSheetBehavior.STATE_HIDDEN
        drawerSheet.peekHeight = 0
        drawerSheet.isHideable = true
        drawerSheet.skipCollapsed = true

        drawerSheet.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onSlide(v: View, offset: Float) {}
            override fun onStateChanged(v: View, state: Int) {
                if (state == BottomSheetBehavior.STATE_HIDDEN) {
                    hideKeyboard()
                    binding.etSearch.clearFocus()
                    binding.etSearch.setText("")
                    binding.drawerDim.visibility = View.GONE
                }
            }
        })

        binding.rvDrawerApps.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = drawerAdapter
            // RAM: small cache, no item animator
            setItemViewCacheSize(3)
            setHasFixedSize(false)
            overScrollMode = View.OVER_SCROLL_NEVER
            itemAnimator = null
        }

        setupCategoryTabs()

        // Keyboard ONLY appears when user explicitly taps the search bar
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

    // Drawer opens WITHOUT auto-showing keyboard; user taps search if needed
    private fun openDrawer() {
        binding.drawerDim.alpha = 0f
        binding.drawerDim.visibility = View.VISIBLE
        binding.drawerDim.animate().alpha(1f).setDuration(220).start()
        drawerSheet.state = BottomSheetBehavior.STATE_EXPANDED
    }

    private fun closeDrawer() {
        hideKeyboard()
        binding.etSearch.clearFocus()
        binding.drawerDim.animate().alpha(0f).setDuration(160)
            .withEndAction { binding.drawerDim.visibility = View.GONE }.start()
        drawerSheet.state = BottomSheetBehavior.STATE_HIDDEN
    }

    // ── Swipe-up gesture ─────────────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    private fun setupSwipeGesture() {
        binding.homeRoot.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { touchStartY = event.y; false }
                MotionEvent.ACTION_UP -> {
                    val dy = touchStartY - event.y
                    if (dy > 100 && drawerSheet.state == BottomSheetBehavior.STATE_HIDDEN) {
                        openDrawer(); true
                    } else false
                }
                else -> false
            }
        }
        binding.ivSwipeHint.setOnClickListener { openDrawer() }
    }

    // ── To-do widget ─────────────────────────────────────────────────────────

    private fun setupTodoWidget() {
        binding.rvTodos.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = todoAdapter
            setHasFixedSize(false)
            overScrollMode = View.OVER_SCROLL_NEVER
            itemAnimator = null
        }
        todoAdapter.setItems(prefs.todos)
        binding.btnAddTodo.setOnClickListener { showAddTodoDialog() }
        binding.tvTodoHeader.setOnClickListener {
            val expanded = binding.rvTodos.visibility == View.VISIBLE
            binding.rvTodos.visibility  = if (expanded) View.GONE else View.VISIBLE
            binding.btnAddTodo.visibility = if (expanded) View.GONE else View.VISIBLE
        }
    }

    private fun showAddTodoDialog() {
        val input = EditText(this).apply {
            hint = "Add a task..."
            setHintTextColor(Color.parseColor("#80FFFFFF"))
            setTextColor(Color.WHITE)
            background = null
            setPadding(32, 16, 32, 16)
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("New Task")
            .setView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(48, 16, 48, 16)
                addView(input)
            })
            .setPositiveButton("Add") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isNotEmpty()) {
                    val updated = prefs.todos.toMutableList().also { it.add(text) }
                    prefs.todos = updated
                    todoAdapter.setItems(updated)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteTodo(pos: Int) {
        val updated = todoAdapter.getItems().toMutableList()
        if (pos in updated.indices) {
            updated.removeAt(pos)
            prefs.todos = updated
            todoAdapter.setItems(updated)
        }
    }

    // ── Data loading (RAM-friendly) ───────────────────────────────────────────
    // Icons are loaded once in AppRepository (IO thread) and reused from cache.
    // loadData() only does a full PM scan after invalidate(); otherwise uses cache.

    private fun loadData() {
        loadJob?.cancel()
        loadJob = scope.launch {
            val apps = try {
                AppRepository.loadApps(this@MainActivity, prefs)
            } catch (_: Exception) { emptyList() }

            if (isDestroyed) return@launch

            allDrawerApps = apps

            val homeApps = if (prefs.favoritePackages.isNotEmpty())
                AppRepository.getFavorites(prefs).take(prefs.homeAppCount)
            else
                AppRepository.getMostUsed(prefs.homeAppCount)

            homeAdapter.setApps(homeApps)

            val totalMin = apps.sumOf { it.screenTimeMinutes }
            if (totalMin > 0 && prefs.showScreenTime) {
                binding.tvUsageToday.visibility = View.VISIBLE
                binding.tvUsageToday.text = "Today: ${ScreenTimeHelper.formatMinutes(totalMin)}"
            } else {
                binding.tvUsageToday.visibility = View.GONE
            }

            filterApps(binding.etSearch.text.toString())
        }
    }

    // ── App launch & options ──────────────────────────────────────────────────

    private fun launchApp(app: AppInfo) {
        closeDrawer()
        scope.launch {
            delay(120) // let drawer close
            try {
                packageManager.getLaunchIntentForPackage(app.packageName)
                    ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED) }
                    ?.let { startActivity(it) }
                    ?: Toast.makeText(this@MainActivity, "Cannot open ${app.label}", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) { loadData() }
        }
    }

    private fun showAppOptions(app: AppInfo, @Suppress("UNUSED_PARAMETER") anchor: View) {
        val items = arrayOf(
            if (app.isFavorite) "Remove from Home" else "Add to Home",
            "Hide App", "App Info", "Uninstall"
        )
        android.app.AlertDialog.Builder(this)
            .setTitle(app.label)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> toggleFavorite(app)
                    1 -> hideApp(app)
                    2 -> openAppInfo(app)
                    3 -> uninstallApp(app)
                }
            }.show()
    }

    private fun toggleFavorite(app: AppInfo) {
        val current = prefs.favoritePackages.toMutableList()
        if (app.packageName in current) current.remove(app.packageName) else current.add(app.packageName)
        prefs.favoritePackages = current
        AppRepository.invalidate()
        loadData()
    }

    private fun hideApp(app: AppInfo) {
        prefs.hiddenPackages = prefs.hiddenPackages.toMutableSet().also { it.add(app.packageName) }
        AppRepository.invalidate()
        loadData()
    }

    private fun openAppInfo(app: AppInfo) {
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", app.packageName, null)
            })
        } catch (_: Exception) {}
    }

    // FIX: ACTION_UNINSTALL_PACKAGE is the correct intent for launchers on all API levels.
    // ACTION_DELETE is deprecated on API 29+ and silently ignored on some ROMs.
    private fun uninstallApp(app: AppInfo) {
        try {
            startActivity(
                Intent(Intent.ACTION_UNINSTALL_PACKAGE,
                    Uri.fromParts("package", app.packageName, null)).apply {
                    putExtra(Intent.EXTRA_RETURN_RESULT, false)
                }
            )
        } catch (_: Exception) {
            // Ultimate fallback
            try {
                startActivity(Intent(Intent.ACTION_DELETE,
                    Uri.fromParts("package", app.packageName, null)))
            } catch (_: Exception) {}
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
        if (drawerSheet.state != BottomSheetBehavior.STATE_HIDDEN) closeDrawer()
        // No super — launcher must not exit
    }

    override fun onDestroy() {
        scope.cancel()
        try { unregisterReceiver(packageReceiver) } catch (_: Exception) {}
        super.onDestroy()
    }
}
