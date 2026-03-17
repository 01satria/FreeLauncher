package com.flowlauncher

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Geocoder
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.flowlauncher.databinding.ActivitySettingsBinding
import kotlinx.coroutines.*
import java.util.Locale

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: Prefs
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    companion object { private const val REQ_LOCATION = 42 }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyTheme()
        setupUI()
    }

    private fun applyTheme() {
        val isLight = prefs.theme == Prefs.THEME_LIGHT
        val bgColor = when (prefs.theme) {
            Prefs.THEME_LIGHT -> Color.parseColor("#F2F2F7")
            else              -> Color.BLACK
        }
        binding.settingsRoot.setBackgroundColor(bgColor)

        val textColor    = if (isLight) Color.parseColor("#0A0A0A") else Color.WHITE
        val subTextColor = if (isLight) Color.parseColor("#8E8E93") else Color.parseColor("#55FFFFFF")
        val btnTint      = if (isLight) Color.BLACK else Color.WHITE

        binding.btnBack.setColorFilter(btnTint)

        // Card backgrounds
        val cardBg     = if (isLight) Color.parseColor("#FFFFFF") else Color.parseColor("#0D0D0D")
        val cardStroke = if (isLight) Color.parseColor("#E0E0E0") else 0x1AFFFFFF.toInt()
        val cardRadius  = 16 * resources.displayMetrics.density

        listOf(binding.cardAppearance, binding.cardClock, binding.cardHome,
               binding.cardWeather, binding.cardApps).forEach { card ->
            card.background = android.graphics.drawable.GradientDrawable().apply {
                setColor(cardBg)
                setStroke((1 * resources.displayMetrics.density).toInt(), cardStroke)
                cornerRadius = cardRadius
            }
        }

        // Apply font and colors to all TextViews via traversal
        applyTextColors(binding.settingsContent, textColor, subTextColor)

        // Weather button text color: White in light mode, black in dark mode for contrast
        binding.btnSetWeatherLocation.setTextColor(if (isLight) Color.WHITE else Color.BLACK)
    }

    private fun applyTextColors(vg: android.view.ViewGroup, text: Int, sub: Int) {
        for (i in 0 until vg.childCount) {
            when (val v = vg.getChildAt(i)) {
                is TextView -> {
                    if (v.letterSpacing > 0) v.setTextColor(sub) else v.setTextColor(text)
                    FontHelper.applyFont(this, prefs, v)
                }
                is android.view.ViewGroup -> applyTextColors(v, text, sub)
            }
        }
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener { finish() }

        // Theme
        val themes    = arrayOf("Dark", "Light", "OLED Black")
        val themeVals = arrayOf(Prefs.THEME_DARK, Prefs.THEME_LIGHT, Prefs.THEME_OLED)
        binding.spinnerTheme.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, themes)
        binding.spinnerTheme.setSelection(themeVals.indexOf(prefs.theme).coerceAtLeast(0))
        binding.spinnerTheme.onItemSelectedListener = listen { idx ->
            if (prefs.theme != themeVals[idx]) {
                prefs.theme = themeVals[idx]
                applyTheme()
            }
        }

        // Alignment
        val alignments = arrayOf("Left", "Center", "Right")
        val alignVals  = arrayOf(Prefs.ALIGN_LEFT, Prefs.ALIGN_CENTER, Prefs.ALIGN_RIGHT)
        binding.spinnerAlignment.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, alignments)
        binding.spinnerAlignment.setSelection(alignVals.indexOf(prefs.alignment).coerceAtLeast(0))
        binding.spinnerAlignment.onItemSelectedListener = listen { prefs.alignment = alignVals[it] }

        // Font Style — applies immediately (no restart needed)
        val fonts    = arrayOf("Default", "Pixel / Dot Matrix")
        val fontVals = arrayOf(Prefs.FONT_DEFAULT, Prefs.FONT_PIXEL)
        binding.spinnerFont.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, fonts)
        binding.spinnerFont.setSelection(fontVals.indexOf(prefs.fontStyle).coerceAtLeast(0))
        binding.spinnerFont.onItemSelectedListener = listen { idx ->
            prefs.fontStyle = fontVals[idx]
            // Instant re-apply on this screen
            applyTheme()
        }

        // Switches
        binding.switchTransparentBg.isChecked = prefs.transparentBg
        binding.switchTransparentBg.setOnCheckedChangeListener { _, c -> prefs.transparentBg = c }
        binding.switch24Hour.isChecked = prefs.use24Hour
        binding.switch24Hour.setOnCheckedChangeListener { _, c -> prefs.use24Hour = c }
        binding.switchShowDate.isChecked = prefs.showDate
        binding.switchShowDate.setOnCheckedChangeListener { _, c -> prefs.showDate = c }
        binding.switchScreenTime.isChecked = prefs.showScreenTime
        binding.switchScreenTime.setOnCheckedChangeListener { _, c -> prefs.showScreenTime = c }

        binding.switchShowClock.isChecked = prefs.showClock
        binding.switchShowClock.setOnCheckedChangeListener { _, c -> prefs.showClock = c }
        binding.switchShowNextEvent.isChecked = prefs.showNextEvent
        binding.switchShowNextEvent.setOnCheckedChangeListener { _, c -> prefs.showNextEvent = c }
        binding.switchShowHomeLabels.isChecked = prefs.showHomeLabels
        binding.switchShowHomeLabels.setOnCheckedChangeListener { _, c -> prefs.showHomeLabels = c }

        binding.npClockSize.minValue = 40
        binding.npClockSize.maxValue = 120
        binding.npClockSize.value    = prefs.clockFontSize
        binding.npClockSize.setOnValueChangedListener { _, _, new -> prefs.clockFontSize = new }

        // Grant Usage Access — now a tappable row
        binding.btnGrantUsage.setOnClickListener {
            try { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
            catch (_: Exception) {}
        }

        // Weather
        binding.btnSetWeatherLocation.setOnClickListener { requestLocationAndSave() }
        updateWeatherLabel()

        // Home app count
        binding.npHomeApps.minValue = 0
        binding.npHomeApps.maxValue = 10
        binding.npHomeApps.value    = prefs.homeAppCount
        binding.npHomeApps.setOnValueChangedListener { _, _, new -> prefs.homeAppCount = new }

        // Hidden apps
        binding.btnManageHidden.setOnClickListener {
            val cached = AppRepository.getCached()
            if (cached.isEmpty()) {
                Toast.makeText(this, "App list not loaded yet", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val hidden  = prefs.hiddenPackages.toMutableSet()
            val pkgs    = cached.map { it.packageName }.toTypedArray()
            val labels  = cached.map { it.label }.toTypedArray()
            val checked = pkgs.map { it in hidden }.toBooleanArray()
            AlertDialog.Builder(this)
                .setTitle("Hidden Apps")
                .setMultiChoiceItems(labels, checked) { _, i, isChecked ->
                    if (isChecked) hidden.add(pkgs[i]) else hidden.remove(pkgs[i])
                }
                .setPositiveButton("Save") { _, _ ->
                    prefs.hiddenPackages = hidden
                    AppRepository.invalidate()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun updateWeatherLabel() {
        val city = prefs.weatherCity
        binding.tvWeatherLocationStatus.text =
            if (city.isNotEmpty()) city else "Location not set"
    }

    private fun requestLocationAndSave() {
        val ok = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                 PackageManager.PERMISSION_GRANTED ||
                 ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                 PackageManager.PERMISSION_GRANTED
        if (!ok) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                REQ_LOCATION
            )
            return
        }
        fetchAndSave()
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (code == REQ_LOCATION && results.any { it == PackageManager.PERMISSION_GRANTED }) fetchAndSave()
    }

    private fun fetchAndSave() {
        binding.btnSetWeatherLocation.isEnabled = false
        binding.tvWeatherLocationStatus.text = "Getting location…"
        scope.launch {
            val loc = getLocation()
            if (loc == null) {
                binding.btnSetWeatherLocation.isEnabled = true
                binding.tvWeatherLocationStatus.text = "Could not get location. Try again."
                return@launch
            }
            prefs.weatherLat  = loc.first
            prefs.weatherLon  = loc.second
            val coord = "${String.format("%.4f", loc.first)}, ${String.format("%.4f", loc.second)}"
            prefs.weatherCity = coord
            binding.btnSetWeatherLocation.isEnabled = true
            updateWeatherLabel()
            Toast.makeText(this@SettingsActivity, "Location saved!", Toast.LENGTH_SHORT).show()

            // Reverse geocode + weather fetch in background
            scope.launch {
                val city = withContext(Dispatchers.IO) {
                    try {
                        val geo = Geocoder(this@SettingsActivity, Locale.getDefault())
                        @Suppress("DEPRECATION")
                        geo.getFromLocation(loc.first, loc.second, 1)
                            ?.firstOrNull()
                            ?.let { it.locality ?: it.subAdminArea } ?: coord
                    } catch (_: Exception) { coord }
                }
                prefs.weatherCity = city
                updateWeatherLabel()
                WeatherHelper.fetch(loc.first, loc.second)?.let {
                    prefs.weatherTempC  = it.tempC
                    prefs.weatherCode   = it.code
                    prefs.weatherLastMs = System.currentTimeMillis()
                }
            }
        }
    }

    @Suppress("MissingPermission")
    private suspend fun getLocation(): Pair<Double, Double>? {
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        val cached = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        ).mapNotNull { p ->
            try { if (lm.isProviderEnabled(p)) lm.getLastKnownLocation(p) else null }
            catch (_: Exception) { null }
        }.maxByOrNull { it.time }

        if (cached != null && System.currentTimeMillis() - cached.time < 10 * 60 * 1000L)
            return Pair(cached.latitude, cached.longitude)

        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .filter { try { lm.isProviderEnabled(it) } catch (_: Exception) { false } }
        if (providers.isEmpty()) return cached?.let { Pair(it.latitude, it.longitude) }

        val channel = kotlinx.coroutines.channels.Channel<Pair<Double, Double>?>(1)
        val listener = object : android.location.LocationListener {
            override fun onLocationChanged(loc: android.location.Location) {
                lm.removeUpdates(this)
                channel.trySend(Pair(loc.latitude, loc.longitude))
            }
            override fun onStatusChanged(p: String?, s: Int, e: android.os.Bundle?) {}
            override fun onProviderEnabled(p: String) {}
            override fun onProviderDisabled(p: String) {}
        }
        return try {
            providers.forEach { p ->
                lm.requestLocationUpdates(p, 0L, 0f, listener, android.os.Looper.getMainLooper())
            }
            withTimeoutOrNull(15_000L) { channel.receive() }
                ?: cached?.let { Pair(it.latitude, it.longitude) }
        } catch (_: Exception) {
            cached?.let { Pair(it.latitude, it.longitude) }
        } finally {
            lm.removeUpdates(listener)
            channel.close()
        }
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    private fun listen(block: (Int) -> Unit) = object : android.widget.AdapterView.OnItemSelectedListener {
        override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
        override fun onItemSelected(p: android.widget.AdapterView<*>?, v: View?, pos: Int, id: Long) = block(pos)
    }
}
