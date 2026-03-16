package com.flowlauncher

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import android.widget.LinearLayout
import com.flowlauncher.databinding.FragmentHomeBinding
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var prefs: Prefs
    private lateinit var homeAdapter: HomeAppAdapter

    var onOpenDrawer: (() -> Unit)? = null

    private var tickJob: kotlinx.coroutines.Job? = null
    private var currentEvent: EventItem? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = Prefs(requireContext())

        homeAdapter = HomeAppAdapter(
            showScreenTime = prefs.showScreenTime,
            onAppClick     = { app -> launchApp(app) },
            onAppLongClick = { app, anchor ->
                (activity as? MainActivity)?.showAppOptionsPublic(app, anchor)
            }
        )

        binding.rvHomeApps.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = homeAdapter
            setHasFixedSize(false)
            itemAnimator = null
            overScrollMode = View.OVER_SCROLL_NEVER
        }

        binding.llWeather.setOnClickListener { fetchWeather(showToast = true) }

        applyTheme()
        setupClockFormat()
        showCachedWeather()
        applyWindowInsets()
    }

    private fun applyWindowInsets() {
        val density = resources.displayMetrics.density
        fun Int.dp() = (this * density).toInt()

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val navBar    = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            binding.homeContentLayout.setPadding(
                28.dp(), statusBar + 16.dp(), 28.dp(), navBar + 72.dp()
            )
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
    }

    override fun onResume() {
        super.onResume()
        prefs = Prefs(requireContext())
        applyTheme()
        setupClockFormat()
        loadHomeApps()
        updateScreenTimeHint()
        loadNextEvent()
        showCachedWeather()
        val age = System.currentTimeMillis() - prefs.weatherLastMs
        if (prefs.hasWeatherLocation() && age > 30 * 60 * 1000L) fetchWeather()
        startCountdownTicker()
    }

    override fun onPause() {
        super.onPause()
        tickJob?.cancel()
    }

    override fun onDestroyView() {
        tickJob?.cancel()
        _binding = null
        super.onDestroyView()
    }

    fun applyTheme() {
        val b = _binding ?: return
        val isLight = prefs.theme == Prefs.THEME_LIGHT

        if (prefs.transparentBg) {
            b.homePageRoot.setBackgroundColor(Color.TRANSPARENT)
        } else {
            val bgColor = when (prefs.theme) {
                Prefs.THEME_LIGHT -> Color.parseColor("#F0F0F0")
                Prefs.THEME_OLED  -> Color.BLACK
                else              -> Color.parseColor("#080808")
            }
            b.homePageRoot.setBackgroundColor(bgColor)
        }

        val textPrimary   = if (isLight) Color.parseColor("#0A0A0A") else Color.WHITE
        val textSecondary = if (isLight) Color.parseColor("#555555") else Color.parseColor("#77FFFFFF")
        val textTertiary  = if (isLight) Color.parseColor("#999999") else Color.parseColor("#44FFFFFF")
        val weatherColor  = if (isLight) Color.parseColor("#555555") else Color.parseColor("#AAFFFFFF")

        b.tvClock.setTextColor(textPrimary)
        b.tvDate.setTextColor(textSecondary)
        b.tvUsageToday.setTextColor(textTertiary)
        b.tvNextEvent.setTextColor(if (isLight) Color.parseColor("#444444") else Color.parseColor("#CCFFFFFF"))
        b.ivWeatherIcon.setColorFilter(weatherColor)
        b.tvWeatherTemp.setTextColor(weatherColor)
        b.tvScreenTimeHint.setTextColor(textTertiary)

        FontHelper.applyFont(requireContext(), prefs,
            b.tvClock, b.tvDate, b.tvUsageToday, b.tvWeatherTemp,
            b.tvScreenTimeHint, b.tvNextEvent)

        // Refresh adapter so font + color changes propagate to all visible rows
        homeAdapter.setLightTheme(isLight)
    }

    private fun setupClockFormat() {
        val b = _binding ?: return
        if (prefs.use24Hour) {
            b.tvClock.format24Hour = "HH:mm"
            b.tvClock.format12Hour = null
        } else {
            b.tvClock.format12Hour = "hh:mm"
            b.tvClock.format24Hour = null
        }
        b.tvClock.textSize = prefs.clockFontSize.toFloat()
        b.tvClock.visibility = if (prefs.showClock) View.VISIBLE else View.GONE
        b.tvDate.visibility  = if (prefs.showDate) View.VISIBLE else View.GONE

        val grav = when (prefs.alignment) {
            Prefs.ALIGN_CENTER -> Gravity.CENTER_HORIZONTAL
            Prefs.ALIGN_RIGHT  -> Gravity.END
            else               -> Gravity.START
        }

        b.tvClock.gravity = grav
        b.llDateWeather.gravity = grav or Gravity.CENTER_VERTICAL
        b.tvDate.gravity  = grav
        b.tvUsageToday.gravity = grav

        // Constrain Next Event width to 70% of screen
        val screenWidth = resources.displayMetrics.widthPixels
        b.tvNextEvent.maxWidth = (screenWidth * 0.7).toInt()
        
        // Align the whole llNextEvent container
        (b.llNextEvent.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
            params.gravity = grav
            b.llNextEvent.layoutParams = params
        }
    }

    private fun showCachedWeather() {
        val b = _binding ?: return
        if (!prefs.hasWeatherLocation() || !prefs.hasWeatherCache()) {
            b.llWeather.visibility = View.GONE
            return
        }
        val iconRes = WeatherHelper.codeToIcon(prefs.weatherCode)
        val temp    = WeatherHelper.formatTemp(prefs.weatherTempC)
        b.ivWeatherIcon.setImageResource(iconRes)
        b.tvWeatherTemp.text = temp
        b.llWeather.visibility = View.VISIBLE
    }

    private fun fetchWeather(showToast: Boolean = false) {
        if (!prefs.hasWeatherLocation()) return
        viewLifecycleOwner.lifecycleScope.launch {
            val result = WeatherHelper.fetch(prefs.weatherLat, prefs.weatherLon)
            val b = _binding ?: return@launch
            if (result != null) {
                prefs.weatherTempC  = result.tempC
                prefs.weatherCode   = result.code
                prefs.weatherLastMs = System.currentTimeMillis()
                val iconRes = WeatherHelper.codeToIcon(result.code)
                val temp    = WeatherHelper.formatTemp(result.tempC)
                b.ivWeatherIcon.setImageResource(iconRes)
                b.tvWeatherTemp.text = temp
                b.llWeather.visibility = View.VISIBLE
                if (showToast) android.widget.Toast.makeText(requireContext(), "Weather updated", android.widget.Toast.LENGTH_SHORT).show()
            } else if (showToast) {
                android.widget.Toast.makeText(requireContext(), "Update failed", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun loadHomeApps() {
        viewLifecycleOwner.lifecycleScope.launch {
            val apps = try { AppRepository.loadApps(requireContext(), prefs) }
                       catch (_: Exception) { emptyList() }
            val b = _binding ?: return@launch

            val homeApps = if (apps.isNotEmpty()) {
                if (prefs.favoritePackages.isNotEmpty())
                    AppRepository.getFavorites(prefs).take(prefs.homeAppCount)
                else
                    AppRepository.getMostUsed(prefs.homeAppCount)
            } else {
                emptyList()
            }

            homeAdapter.showScreenTime = prefs.showScreenTime
            homeAdapter.setApps(homeApps)

            val totalMin = apps.sumOf { it.screenTimeMinutes }
            if (totalMin > 0 && prefs.showScreenTime) {
                b.tvUsageToday.visibility = View.VISIBLE
                b.tvUsageToday.text = "Today · ${ScreenTimeHelper.formatMinutes(totalMin)}"
            } else {
                b.tvUsageToday.visibility = View.GONE
            }
        }
    }

    private fun loadNextEvent() {
        val b = _binding ?: return
        if (!CalendarHelper.hasPermission(requireContext())) {
            b.llNextEvent.visibility = View.GONE
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val events = CalendarHelper.getUpcomingEvents(requireContext(), limit = 1)
            currentEvent = events.firstOrNull()
            updateNextEventUI()
        }
    }

    private fun updateNextEventUI() {
        val bv = _binding ?: return
        val ev = currentEvent
        if (ev != null && prefs.showNextEvent) {
            val cd = ev.countdown
            // Jika sudah "Done" dan event sudah selesai atau lewat, sembunyikan atau fetch ulang.
            // Biar gampang, fetch ulang di ticker menit berikutnya
            bv.tvNextEvent.text = "${ev.title}  ·  $cd"
            bv.llNextEvent.visibility = View.VISIBLE
        } else {
            bv.llNextEvent.visibility = View.GONE
        }
    }

    private fun startCountdownTicker() {
        tickJob?.cancel()
        tickJob = viewLifecycleOwner.lifecycleScope.launch {
            var secondsPassed = 0
            while (isActive) {
                kotlinx.coroutines.delay(30_000L) // 30s
                if (_binding == null || !CalendarHelper.hasPermission(requireContext())) continue

                // 1. Update text
                if (currentEvent != null) {
                    updateNextEventUI()
                }

                // 2. Tiap 5 menit fetch ulang untuk event baru (30s x 10)
                secondsPassed += 30
                if (secondsPassed >= 300) {
                    secondsPassed = 0
                    loadNextEvent()
                }
            }
        }
    }

    private fun updateScreenTimeHint() {
        val b = _binding ?: return
        if (prefs.showScreenTime && !ScreenTimeHelper.hasPermission(requireContext())) {
            b.tvScreenTimeHint.visibility = View.VISIBLE
            b.tvScreenTimeHint.setOnClickListener {
                try { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                catch (_: Exception) {}
            }
        } else {
            b.tvScreenTimeHint.visibility = View.GONE
        }
    }

    private fun launchApp(app: AppInfo) {
        try {
            requireContext().packageManager
                .getLaunchIntentForPackage(app.packageName)
                ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED) }
                ?.let { startActivity(it) }
        } catch (_: Exception) {}
    }
}
