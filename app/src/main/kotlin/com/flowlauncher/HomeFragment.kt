package com.flowlauncher

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.flowlauncher.databinding.FragmentHomeBinding
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var prefs: Prefs
    private lateinit var homeAdapter: HomeAppAdapter

    var onOpenDrawer: (() -> Unit)? = null

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

        binding.btnDrawer.setOnClickListener {
            onOpenDrawer?.invoke()
        }

        applyTheme()
        setupClockFormat()
    }

    override fun onResume() {
        super.onResume()
        prefs = Prefs(requireContext())
        applyTheme()
        setupClockFormat()
        loadHomeApps()
        updateScreenTimeHint()
        loadNextEvent()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    // ── Theme ─────────────────────────────────────────────────────────────────

    fun applyTheme() {
        val b = _binding ?: return
        val isLight = prefs.theme == Prefs.THEME_LIGHT
        val bgColor = when (prefs.theme) {
            Prefs.THEME_LIGHT -> Color.parseColor("#F0F0F0")
            Prefs.THEME_OLED  -> Color.BLACK
            else              -> Color.parseColor("#090909")
        }
        b.homePageRoot.setBackgroundColor(bgColor)

        val textPrimary   = if (isLight) Color.parseColor("#111111") else Color.WHITE
        val textSecondary = if (isLight) Color.parseColor("#66000000") else Color.parseColor("#88FFFFFF")

        b.tvClock.setTextColor(textPrimary)
        b.tvDate.setTextColor(textSecondary)
        b.tvUsageToday.setTextColor(textSecondary)
        b.tvScreenTimeHint.setTextColor(if (isLight) Color.parseColor("#44000000") else Color.parseColor("#44FFFFFF"))
        b.btnDrawer.setColorFilter(if (isLight) Color.parseColor("#88000000") else Color.parseColor("#88FFFFFF"))
    }

    // ── Clock format ─────────────────────────────────────────────────────────

    private fun setupClockFormat() {
        val b = _binding ?: return
        if (prefs.use24Hour) {
            b.tvClock.format24Hour = "HH:mm"
            b.tvClock.format12Hour = null
        } else {
            b.tvClock.format12Hour = "hh:mm"
            b.tvClock.format24Hour = null
        }
        b.tvDate.visibility = if (prefs.showDate) View.VISIBLE else View.GONE

        b.tvClock.gravity = Gravity.START
        b.tvDate.gravity  = Gravity.START
        b.tvUsageToday.gravity = Gravity.START
    }

    // ── Home apps ─────────────────────────────────────────────────────────────

    fun loadHomeApps() {
        viewLifecycleOwner.lifecycleScope.launch {
            val apps = try { AppRepository.loadApps(requireContext(), prefs) }
                       catch (_: Exception) { emptyList() }
            val b = _binding ?: return@launch

            val homeApps = if (prefs.favoritePackages.isNotEmpty())
                AppRepository.getFavorites(prefs).take(prefs.homeAppCount)
            else
                AppRepository.getMostUsed(prefs.homeAppCount)

            homeAdapter.showScreenTime = prefs.showScreenTime
            homeAdapter.setApps(homeApps)

            val totalMin = apps.sumOf { it.screenTimeMinutes }
            if (totalMin > 0 && prefs.showScreenTime) {
                b.tvUsageToday.visibility = View.VISIBLE
                b.tvUsageToday.text = "Today: ${ScreenTimeHelper.formatMinutes(totalMin)}"
            } else {
                b.tvUsageToday.visibility = View.GONE
            }
        }
    }

    // ── Next event chip on home ───────────────────────────────────────────────

    private fun loadNextEvent() {
        val b = _binding ?: return
        if (!CalendarHelper.hasPermission(requireContext())) {
            b.tvNextEvent.visibility = View.GONE
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val events = CalendarHelper.getUpcomingEvents(requireContext(), limit = 1)
            val ev = events.firstOrNull()
            val bv = _binding ?: return@launch
            if (ev != null) {
                bv.tvNextEvent.text = "${ev.title}  ·  ${ev.countdown}"
                bv.tvNextEvent.visibility = View.VISIBLE
            } else {
                bv.tvNextEvent.visibility = View.GONE
            }
        }
    }

    // ── Screen time hint ─────────────────────────────────────────────────────

    private fun updateScreenTimeHint() {
        val b = _binding ?: return
        if (!ScreenTimeHelper.hasPermission(requireContext())) {
            b.tvScreenTimeHint.visibility = View.VISIBLE
            b.tvScreenTimeHint.setOnClickListener {
                try { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                catch (_: Exception) {}
            }
        } else {
            b.tvScreenTimeHint.visibility = View.GONE
        }
    }

    // ── Launch ────────────────────────────────────────────────────────────────

    private fun launchApp(app: AppInfo) {
        try {
            requireContext().packageManager
                .getLaunchIntentForPackage(app.packageName)
                ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED) }
                ?.let { startActivity(it) }
        } catch (_: Exception) {}
    }
}
