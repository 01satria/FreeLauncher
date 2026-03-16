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
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.flowlauncher.databinding.ActivityMainBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: Prefs

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var searchJob: Job? = null

    private lateinit var homeFragment: HomeFragment
    private lateinit var feedFragment: FeedFragment

    private val drawerAdapter = DrawerAppAdapter(
        onAppClick     = ::launchApp,
        onAppLongClick = ::showAppOptions
    )
    private lateinit var drawerSheet: BottomSheetBehavior<View>
    private var allDrawerApps: List<AppInfo> = emptyList()
    private var isSearching = false
    private var isDraggingScroller = false

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        // Window flags HARUS diset sebelum setContentView
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        window.setBackgroundDrawableResource(android.R.color.transparent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        }
        applySystemBarColors()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViewPager()
        setupDrawer()
        applyWindowInsets()
        applyDrawerTheme()
        registerReceivers()

        // Pre-load app list so drawer is instant on first open
        scope.launch {
            allDrawerApps = try { AppRepository.loadApps(this@MainActivity, prefs) }
                            catch (_: Exception) { emptyList() }
            if (::homeFragment.isInitialized) homeFragment.loadHomeApps()
        }
    }

    override fun onResume() {
        super.onResume()
        prefs = Prefs(this)
        applySystemBarColors()
        applyDrawerTheme()
    }

    // ── ViewPager ─────────────────────────────────────────────────────────────

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
        binding.viewPager.setCurrentItem(PAGE_HOME, false)

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(pos: Int) {
                if (drawerSheet.state != BottomSheetBehavior.STATE_HIDDEN) closeDrawer()
            }
        })
    }

    // ── App Drawer ────────────────────────────────────────────────────────────

    private lateinit var gestureDetector: GestureDetector

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
                    isSearching = false
                }
            }
        })

        // Swipe up gesture detector
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (e1 == null) return false
                val diffY = e2.y - e1.y
                val diffX = e2.x - e1.x
                // Detect swipe up (negative diffY)
                if (kotlin.math.abs(diffY) > kotlin.math.abs(diffX) && diffY < -100 && kotlin.math.abs(vy) > 100) {
                    if (binding.viewPager.currentItem == PAGE_HOME && drawerSheet.state == BottomSheetBehavior.STATE_HIDDEN) {
                        openDrawer()
                        return true
                    }
                }
                return false
            }
        })

        binding.rvDrawerApps.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = drawerAdapter
            setItemViewCacheSize(8)
            setHasFixedSize(false)
            overScrollMode = View.OVER_SCROLL_NEVER
            itemAnimator = null
            recycledViewPool.setMaxRecycledViews(0, 5)   // headers — few needed
            recycledViewPool.setMaxRecycledViews(1, 15)  // app rows

            addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
                    if (isDraggingScroller || isSearching || allDrawerApps.isEmpty()) return
                    
                    val lm = layoutManager as? LinearLayoutManager ?: return
                    val firstPos = lm.findFirstVisibleItemPosition()
                    if (firstPos == -1) return

                    val total = allDrawerApps.size
                    if (total <= 1) return

                    val progress = firstPos.toFloat() / (total - 1)
                    val containerHeight = binding.drawerScrollerContainer.height.toFloat()
                    val thumbHeight = binding.drawerScrollerThumb.height.toFloat()
                    
                    if (containerHeight > thumbHeight) {
                        val maxThumbY = containerHeight - thumbHeight
                        binding.drawerScrollerThumb.translationY = (progress * maxThumbY).coerceIn(0f, maxThumbY)
                    }
                }
            })
        }

        binding.etSearch.showSoftInputOnFocus = false
        binding.etSearch.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                v.requestFocus()
                showKeyboard()
            }
            false
        }
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun afterTextChanged(s: Editable?) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {
                searchJob?.cancel()
                searchJob = scope.launch {
                    delay(80)
                    filterApps(s?.toString().orEmpty())
                }
            }
        })

        binding.drawerDim.setOnClickListener { closeDrawer() }

        // Settings button in drawer header
        binding.btnDrawerSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        setupFastScroller()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupFastScroller() {
        binding.drawerScrollerContainer.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    isDraggingScroller = true
                    val height = v.height.toFloat()
                    val thumbHeight = binding.drawerScrollerThumb.height.toFloat()
                    if (height > thumbHeight) {
                        val maxThumbY = height - thumbHeight
                        
                        // Follow finger precisely: center thumb on touch Y
                        val newY = (event.y - thumbHeight / 2f).coerceIn(0f, maxThumbY)
                        binding.drawerScrollerThumb.translationY = newY

                        val progress = newY / maxThumbY
                        
                        // Find letter
                        if (allDrawerApps.isNotEmpty() && !isSearching) {
                            val distinctLetters = allDrawerApps.map { 
                                it.label.firstOrNull()?.uppercaseChar()?.toString() ?: "#" 
                            }.distinct().sortedBy { if (it == "#") "\u0000" else it }
                            
                            if (distinctLetters.isNotEmpty()) {
                                val letterIndex = (progress * (distinctLetters.size - 1)).toInt()
                                val letter = distinctLetters[letterIndex]
                                
                                // Show popup
                                binding.tvScrollerPopup.text = letter
                                binding.tvScrollerPopup.visibility = View.VISIBLE
                                // Popup follows thumb but stays inside container
                                val popupHeight = binding.tvScrollerPopup.height.toFloat()
                                val popupY = (newY + thumbHeight/2f - popupHeight/2f).coerceIn(0f, height - popupHeight)
                                binding.tvScrollerPopup.translationY = popupY

                                // Scroll RV
                                val scrollPos = allDrawerApps.indexOfFirst { 
                                    (it.label.firstOrNull()?.uppercaseChar()?.toString() ?: "#") == letter 
                                }
                                if (scrollPos != -1) {
                                    (binding.rvDrawerApps.layoutManager as? LinearLayoutManager)
                                        ?.scrollToPositionWithOffset(scrollPos, 0)
                                }
                            }
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isDraggingScroller = false
                    binding.tvScrollerPopup.animate().alpha(0f).setDuration(150).withEndAction {
                        binding.tvScrollerPopup.visibility = View.GONE
                        binding.tvScrollerPopup.alpha = 1f
                    }.start()
                    true
                }
                else -> false
            }
        }
    }

    private fun filterApps(query: String) {
        if (query.isBlank()) {
            isSearching = false
            drawerAdapter.setApps(allDrawerApps)
        } else {
            isSearching = true
            val filtered = allDrawerApps.filter {
                it.label.contains(query, ignoreCase = true)
            }
            drawerAdapter.setSearchApps(filtered)
            binding.drawerScrollerContainer.visibility = View.GONE
            // Auto-launch if exactly 1 result
            if (filtered.size == 1) launchApp(filtered[0])
        }
    }

    fun openDrawer() {
        // Show cached apps immediately — no visible delay
        val cached = AppRepository.getCached()
        if (cached.isNotEmpty() && allDrawerApps.isEmpty()) {
            allDrawerApps = cached
            drawerAdapter.setApps(allDrawerApps)
        } else if (allDrawerApps.isNotEmpty()) {
            drawerAdapter.setApps(allDrawerApps)
        }

        binding.drawerScrollerContainer.visibility = View.VISIBLE

        binding.viewPager.isUserInputEnabled = false
        binding.drawerDim.alpha = 0f
        binding.drawerDim.visibility = View.VISIBLE
        binding.drawerDim.animate().alpha(1f).setDuration(180).start()
        drawerSheet.state = BottomSheetBehavior.STATE_EXPANDED

        // Refresh in background (screen time / new installs)
        scope.launch {
            val fresh = try { AppRepository.loadApps(this@MainActivity, prefs) }
                        catch (_: Exception) { allDrawerApps }
            if (fresh != allDrawerApps) {
                allDrawerApps = fresh
                drawerAdapter.setApps(allDrawerApps)
            }
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

    fun showAppOptionsPublic(app: AppInfo, anchor: View) = showAppOptions(app, anchor)

    private fun showAppOptions(app: AppInfo, @Suppress("UNUSED_PARAMETER") anchor: View) {
        AlertDialog.Builder(this)
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
            val intent = Intent(Intent.ACTION_DELETE).apply {
                data = Uri.parse("package:${app.packageName}")
            }
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "Uninstall not supported", Toast.LENGTH_SHORT).show()
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

    private fun applyWindowInsets() {
        val density = resources.displayMetrics.density
        fun Int.dp() = (this * density).toInt()

        ViewCompat.setOnApplyWindowInsetsListener(binding.mainRoot) { _, insets ->
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val navBar    = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom

            // Drawer header: symmetric horizontal padding, top = status bar + 16dp
            binding.drawerHeader.setPadding(
                24.dp(), statusBar + 16.dp(), 24.dp(), 8.dp()
            )
            // Drawer list: bottom padding = nav bar
            binding.rvDrawerApps.setPadding(0, 0, 0, navBar + 16.dp())

            insets
        }
        ViewCompat.requestApplyInsets(binding.mainRoot)
    }

    // ── Drawer theming ────────────────────────────────────────────────────────

    private fun applyDrawerTheme() {
        val isLight = prefs.theme == Prefs.THEME_LIGHT
        val density = resources.displayMetrics.density
        fun Float.dp() = (this * density).toInt()

        val textPrimary   = if (isLight) android.graphics.Color.parseColor("#0A0A0A") else android.graphics.Color.WHITE
        val textSecondary = if (isLight) android.graphics.Color.parseColor("#666666")  else android.graphics.Color.parseColor("#55FFFFFF")
        val searchBgColor = if (isLight) android.graphics.Color.parseColor("#ECECEC")  else android.graphics.Color.parseColor("#111111")
        val searchStroke  = if (isLight) android.graphics.Color.parseColor("#DDDDDD")  else android.graphics.Color.parseColor("#22FFFFFF")
        val drawerBgColor = if (isLight) android.graphics.Color.parseColor("#F8F8F8")  else android.graphics.Color.parseColor("#050505")
        val handleColor   = if (isLight) android.graphics.Color.parseColor("#CCCCCC")  else android.graphics.Color.parseColor("#44FFFFFF")
        val settingsColor = if (isLight) android.graphics.Color.parseColor("#888888")  else android.graphics.Color.parseColor("#55FFFFFF")

        // Drawer sheet background
        val drawerBg = android.graphics.drawable.GradientDrawable().apply {
            setColor(drawerBgColor)
            cornerRadii = floatArrayOf(28.dpToPx(), 28.dpToPx(), 28.dpToPx(), 28.dpToPx(), 0f, 0f, 0f, 0f)
        }
        binding.drawerSheet.background = drawerBg

        // Handle bar color
        binding.drawerHandleBar.background = android.graphics.drawable.GradientDrawable().apply {
            setColor(handleColor)
            cornerRadius = 100.dpToPx()
        }

        // Apps title
        binding.tvAppsTitle.setTextColor(textPrimary)
        FontHelper.applyFont(this, prefs, binding.tvAppsTitle)

        // Settings button
        binding.btnDrawerSettings.setColorFilter(settingsColor)

        // Search bar background
        val searchBg = android.graphics.drawable.GradientDrawable().apply {
            setColor(searchBgColor)
            setStroke(1.dpToPxInt(), searchStroke)
            cornerRadius = 14.dpToPx()
        }
        binding.drawerSearchContainer.background = searchBg

        // Search EditText
        binding.etSearch.setTextColor(textPrimary)
        binding.etSearch.setHintTextColor(if (isLight) android.graphics.Color.parseColor("#AAAAAA") else android.graphics.Color.parseColor("#33FFFFFF"))
        FontHelper.applyFont(this, prefs, binding.etSearch)

        // Search icon tint
        binding.ivSearchIcon.setColorFilter(if (isLight) android.graphics.Color.parseColor("#AAAAAA") else android.graphics.Color.parseColor("#44FFFFFF"))

        // Scroller Theming
        binding.drawerScrollerThumb.backgroundTintList = android.content.res.ColorStateList.valueOf(handleColor)
        binding.tvScrollerPopup.backgroundTintList = android.content.res.ColorStateList.valueOf(searchBgColor)
        binding.tvScrollerPopup.setTextColor(textPrimary)
        FontHelper.applyFont(this, prefs, binding.tvScrollerPopup)

        // Hide scroller when searching
        binding.drawerScrollerContainer.visibility = if (isSearching) View.GONE else View.VISIBLE

        // Notify drawer adapter about theme change (force re-bind for font + colors)
        drawerAdapter.setTheme(isLight)
    }

    private fun Float.dpToPx() = this * resources.displayMetrics.density
    private fun Int.dpToPx()   = this.toFloat() * resources.displayMetrics.density
    private fun Int.dpToPxInt() = (this.toFloat() * resources.displayMetrics.density).toInt()

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
            drawerSheet.state != BottomSheetBehavior.STATE_HIDDEN -> closeDrawer()
            binding.viewPager.currentItem != PAGE_HOME ->
                binding.viewPager.setCurrentItem(PAGE_HOME, true)
            else -> super.onBackPressed()
        }
    }

    // ── System bar colors ─────────────────────────────────────────────────────

    private fun applySystemBarColors() {
        if (prefs.transparentBg) {
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
        } else {
            window.statusBarColor = android.graphics.Color.BLACK
            window.navigationBarColor = android.graphics.Color.BLACK
        }
    }

    private var touchStartX = 0f
    private var touchStartY = 0f
    private var verticalSwipeLocked = false

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev != null && ::gestureDetector.isInitialized) {
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    touchStartX = ev.x
                    touchStartY = ev.y
                    verticalSwipeLocked = false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!verticalSwipeLocked) {
                        val dx = kotlin.math.abs(ev.x - touchStartX)
                        val dy = kotlin.math.abs(ev.y - touchStartY)
                        // If moving vertically more than horizontally (with a small hurdle),
                        // lock the ViewPager instantly.
                        if (dy > dx && dy > 10) {
                            verticalSwipeLocked = true
                            if (binding.viewPager.currentItem == PAGE_HOME && 
                                drawerSheet.state == BottomSheetBehavior.STATE_HIDDEN) {
                                binding.viewPager.isUserInputEnabled = false
                            }
                        }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // Re-enable horizontal scroll ONLY if drawer didn't open
                    if (drawerSheet.state == BottomSheetBehavior.STATE_HIDDEN) {
                        binding.viewPager.isUserInputEnabled = true
                    }
                    verticalSwipeLocked = false
                }
            }
            gestureDetector.onTouchEvent(ev)
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onDestroy() {
        scope.cancel()
        try { unregisterReceiver(packageReceiver) } catch (_: Exception) {}
        super.onDestroy()
    }
}
