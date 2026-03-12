package com.flowlauncher

import android.Manifest
import kotlinx.coroutines.*
import androidx.appcompat.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.location.Geocoder
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.flowlauncher.databinding.ActivitySettingsBinding
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
        
        applySettingsTheme()
        setupUI()
    }

    private fun applySettingsTheme() {
        val theme = prefs.theme
        val isLight = theme == Prefs.THEME_LIGHT
        
        val bgColor = when (theme) {
            Prefs.THEME_LIGHT -> Color.parseColor("#F5F5F7")
            Prefs.THEME_OLED  -> Color.BLACK
            else               -> Color.BLACK
        }
        
        val cardColor = when (theme) {
            Prefs.THEME_LIGHT -> Color.WHITE
            Prefs.THEME_OLED  -> Color.BLACK
            else               -> Color.parseColor("#161616")
        }
        
        val textColor = if (isLight) Color.BLACK else Color.WHITE
        val subTextColor = if (isLight) Color.parseColor("#8E8E93") else Color.parseColor("#55FFFFFF")
        val dividerColor = if (isLight) Color.parseColor("#E5E5EA") else Color.parseColor("#15FFFFFF")

        binding.settingsRoot.setBackgroundColor(bgColor)
        
        // Find all TextViews and apply colors
        applyColorsToViewGroup(binding.settingsContent, textColor, subTextColor, cardColor, dividerColor, isLight)
        
        // Special case for buttons
        val btnTint = if (isLight) Color.BLACK else Color.WHITE
        binding.btnBack.setColorFilter(btnTint)
    }

    private fun applyColorsToViewGroup(vg: ViewGroup, txt: Int, sub: Int, card: Int, div: Int, light: Boolean) {
        for (i in 0 until vg.childCount) {
            val v = vg.getChildAt(i)
            
            // Apply card background if it's a section container (Linear)
            if (v.id == binding.cardAppearance.id || v.id == binding.cardClock.id || 
                v.id == binding.cardHome.id || v.id == binding.cardWeather.id || v.id == binding.cardApps.id) {
                
                val gd = GradientDrawable()
                gd.setColor(card)
                gd.cornerRadius = 20 * resources.displayMetrics.density
                if (light) gd.setStroke(1, Color.parseColor("#E5E5EA"))
                else if (prefs.theme == Prefs.THEME_OLED) gd.setStroke(1, Color.parseColor("#222222"))
                v.background = gd
            }

            when (v) {
                is TextView -> {
                    // Section headers (small, letter spaced) are detected by tag or by being siblings of cards
                    if (v.letterSpacing > 0) v.setTextColor(sub)
                    else v.setTextColor(txt)
                }
                is ViewGroup -> applyColorsToViewGroup(v, txt, sub, card, div, light)
                is View -> {
                    // Dividers
                    if (v.layoutParams.height == (1 * resources.displayMetrics.density).toInt()) {
                        v.setBackgroundColor(div)
                    }
                }
            }
        }
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener { finish() }

        // Theme
        val themes    = arrayOf("Dark", "Light", "OLED Black")
        val themeVals = arrayOf(Prefs.THEME_DARK, Prefs.THEME_LIGHT, Prefs.THEME_OLED)
        binding.spinnerTheme.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, themes)
        binding.spinnerTheme.setSelection(themeVals.indexOf(prefs.theme).coerceAtLeast(0))
        binding.spinnerTheme.onItemSelectedListener = listen { 
            if (prefs.theme != themeVals[it]) {
                prefs.theme = themeVals[it]
                applySettingsTheme()
            }
        }

        // Alignment
        val alignments = arrayOf("Left", "Center", "Right")
        val alignVals  = arrayOf(Prefs.ALIGN_LEFT, Prefs.ALIGN_CENTER, Prefs.ALIGN_RIGHT)
        binding.spinnerAlignment.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, alignments)
        binding.spinnerAlignment.setSelection(alignVals.indexOf(prefs.alignment).coerceAtLeast(0))
        binding.spinnerAlignment.onItemSelectedListener = listen { prefs.alignment = alignVals[it] }

        // Switches
        binding.switchTransparentBg.isChecked = prefs.transparentBg
        binding.switchTransparentBg.setOnCheckedChangeListener { _, c -> prefs.transparentBg = c }
        binding.switch24Hour.isChecked = prefs.use24Hour
        binding.switch24Hour.setOnCheckedChangeListener { _, c -> prefs.use24Hour = c }
        binding.switchShowDate.isChecked = prefs.showDate
        binding.switchShowDate.setOnCheckedChangeListener { _, c -> prefs.showDate = c }
        binding.switchScreenTime.isChecked = prefs.showScreenTime
        binding.switchScreenTime.setOnCheckedChangeListener { _, c -> prefs.showScreenTime = c }

        // Other buttons
        binding.btnGrantUsage.setOnClickListener {
            try { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
            catch (_: Exception) {}
        }
        binding.btnSetWeatherLocation.setOnClickListener { requestLocationAndSave() }

        // Home app count
        binding.npHomeApps.minValue = 1
        binding.npHomeApps.maxValue = 10
        binding.npHomeApps.value    = prefs.homeAppCount
        binding.npHomeApps.setOnValueChangedListener { _, _, new -> prefs.homeAppCount = new }

        // Weather label
        updateWeatherLocationLabel()

        // Manage hidden apps
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

    private fun updateWeatherLocationLabel() {
        val city = prefs.weatherCity
        val isLight = prefs.theme == Prefs.THEME_LIGHT
        binding.tvWeatherLocationStatus.text = if (city.isNotEmpty()) "Location: $city" else "Location not set"
        binding.tvWeatherLocationStatus.setTextColor(if (isLight) Color.parseColor("#8E8E93") else Color.parseColor("#77FFFFFF"))
    }

    private fun requestLocationAndSave() {
        val hasFine   = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), REQ_LOCATION)
            return
        }
        fetchAndSaveLocation()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_LOCATION && grantResults.any { it == PackageManager.PERMISSION_GRANTED }) fetchAndSaveLocation()
    }

    private fun fetchAndSaveLocation() {
        binding.btnSetWeatherLocation.isEnabled = false
        binding.tvWeatherLocationStatus.text = "Getting location..."

        scope.launch {
            val loc = getCurrentLocationSuspend()
            if (loc == null) {
                binding.btnSetWeatherLocation.isEnabled = true
                binding.tvWeatherLocationStatus.text = "Could not get location. Try again."
                return@launch
            }

            prefs.weatherLat = loc.first
            prefs.weatherLon = loc.second
            val coordLabel = "${String.format("%.4f", loc.first)}, ${String.format("%.4f", loc.second)}"
            prefs.weatherCity = coordLabel
            binding.btnSetWeatherLocation.isEnabled = true
            updateWeatherLocationLabel()
            Toast.makeText(this@SettingsActivity, "Location saved!", Toast.LENGTH_SHORT).show()

            scope.launch {
                val city = withContext(Dispatchers.IO) {
                    try {
                        val geo = Geocoder(this@SettingsActivity, Locale.getDefault())
                        @Suppress("DEPRECATION")
                        val addrs = geo.getFromLocation(loc.first, loc.second, 1)
                        addrs?.firstOrNull()?.locality ?: addrs?.firstOrNull()?.subAdminArea ?: coordLabel
                    } catch (_: Exception) { coordLabel }
                }
                prefs.weatherCity = city
                updateWeatherLocationLabel()

                val result = WeatherHelper.fetch(loc.first, loc.second)
                if (result != null) {
                    prefs.weatherTempC  = result.tempC
                    prefs.weatherCode   = result.code
                    prefs.weatherLastMs = System.currentTimeMillis()
                }
            }
        }
    }

    @Suppress("MissingPermission")
    private suspend fun getCurrentLocationSuspend(): Pair<Double, Double>? {
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        val cached = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER).mapNotNull { p ->
            try { if (lm.isProviderEnabled(p)) lm.getLastKnownLocation(p) else null } catch (_: Exception) { null }
        }.maxByOrNull { it.time }

        if (cached != null && System.currentTimeMillis() - cached.time < 10 * 60 * 1000L) {
            return Pair(cached.latitude, cached.longitude)
        }

        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER).filter { try { lm.isProviderEnabled(it) } catch (_: Exception) { false } }
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
            providers.forEach { p -> lm.requestLocationUpdates(p, 0L, 0f, listener, android.os.Looper.getMainLooper()) }
            withTimeoutOrNull(15_000L) { channel.receive() } ?: cached?.let { Pair(it.latitude, it.longitude) }
        } catch (_: Exception) { cached?.let { Pair(it.latitude, it.longitude) } } finally {
            lm.removeUpdates(listener)
            channel.close()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun listen(block: (Int) -> Unit) = object : AdapterView.OnItemSelectedListener {
        override fun onNothingSelected(p: AdapterView<*>?) {}
        override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) = block(pos)
    }
}
