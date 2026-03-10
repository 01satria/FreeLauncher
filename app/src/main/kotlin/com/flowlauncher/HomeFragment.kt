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

    // Callback → MainActivity opens the app drawer
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
            showIcons      = prefs.showIcons,
            showScreenTime = prefs.showScreenTime,
            alignment      = gravityFromPref(),
            fontSize       = prefs.fontSize.toFloat(),
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

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(requireContext(), SettingsActivity::class.java))
        }
        binding.btnFocus.setOnClickListener {
            startActivity(Intent(requireContext(), FocusActivity::class.java))
        }
        binding.ivSwipeHint.setOnClickListener { onOpenDrawer?.invoke() }

        setupClockFormat()
        applyTheme()
    }

    override fun onResume() {
        super.onResume()
        applyTheme()
        setupClockFormat()
        loadHomeApps()
        updateScreenTimeHint()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    // ── Theme ────────────────────────────────────────────────────────────────

    fun applyTheme() {
        val b = _binding ?: return
        val isLight = prefs.theme == Prefs.THEME_LIGHT

        val bgColor = when (prefs.theme) {
            Prefs.THEME_LIGHT -> Color.parseColor("#F2F2F2")
            Prefs.THEME_OLED  -> Color.BLACK
            else              -> Color.parseColor("#0D0D0D")
        }
        b.homePageRoot.setBackgroundColor(bgColor)

        val textPrimary   = if (isLight) Color.parseColor("#111111") else Color.WHITE
        val textSecondary = if (isLight) Color.parseColor("#66000000") else Color.parseColor("#99FFFFFF")
        val iconTint      = if (isLight) Color.parseColor("#88000000") else Color.parseColor("#AAFFFFFF")
        val hintTint      = if (isLight) Color.parseColor("#55000000") else Color.parseColor("#55FFFFFF")

        b.tvClock.setTextColor(textPrimary)
        b.tvDate.setTextColor(textSecondary)
        b.tvUsageToday.setTextColor(textSecondary)
        b.tvScreenTimeHint.setTextColor(hintTint)
        b.btnSettings.setColorFilter(iconTint)
        b.btnFocus.setColorFilter(iconTint)
        b.ivSwipeHint.setColorFilter(
            if (isLight) Color.parseColor("#33000000") else Color.parseColor("#44FFFFFF")
        )
    }

    // ── Clock ────────────────────────────────────────────────────────────────

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

        val gravity = gravityFromPref()
        b.tvClock.gravity   = gravity
        b.tvDate.gravity    = gravity
        b.tvUsageToday.gravity = gravity
    }

    private fun gravityFromPref(): Int = when (prefs.alignment) {
        Prefs.ALIGN_CENTER -> Gravity.CENTER_HORIZONTAL
        Prefs.ALIGN_RIGHT  -> Gravity.END
        else               -> Gravity.START
    }

    // ── Home apps ────────────────────────────────────────────────────────────

    fun loadHomeApps() {
        viewLifecycleOwner.lifecycleScope.launch {
            val apps = try { AppRepository.loadApps(requireContext(), prefs) }
                       catch (_: Exception) { emptyList() }
            val b = _binding ?: return@launch

            val homeApps = if (prefs.favoritePackages.isNotEmpty())
                AppRepository.getFavorites(prefs).take(prefs.homeAppCount)
            else
                AppRepository.getMostUsed(prefs.homeAppCount)

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

    // ── App launch ───────────────────────────────────────────────────────────

    private fun launchApp(app: AppInfo) {
        try {
            requireContext().packageManager
                .getLaunchIntentForPackage(app.packageName)
                ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED) }
                ?.let { startActivity(it) }
        } catch (_: Exception) {}
    }
}
