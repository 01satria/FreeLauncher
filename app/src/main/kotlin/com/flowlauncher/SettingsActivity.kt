package com.flowlauncher

import android.Manifest
import kotlinx.coroutines.*
import androidx.appcompat.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
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
        setupUI()
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener { finish() }

        // Theme
        val themes    = arrayOf("Dark", "Light", "OLED Black")
        val themeVals = arrayOf(Prefs.THEME_DARK, Prefs.THEME_LIGHT, Prefs.THEME_OLED)
        binding.spinnerTheme.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, themes)
        binding.spinnerTheme.setSelection(themeVals.indexOf(prefs.theme).coerceAtLeast(0))
        binding.spinnerTheme.onItemSelectedListener = listen { prefs.theme = themeVals[it] }

        // Alignment
        val alignments = arrayOf("Left", "Center", "Right")
        val alignVals  = arrayOf(Prefs.ALIGN_LEFT, Prefs.ALIGN_CENTER, Prefs.ALIGN_RIGHT)
        binding.spinnerAlignment.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, alignments)
        binding.spinnerAlignment.setSelection(alignVals.indexOf(prefs.alignment).coerceAtLeast(0))
        binding.spinnerAlignment.onItemSelectedListener = listen { prefs.alignment = alignVals[it] }

        // Transparent background
        binding.switchTransparentBg.isChecked = prefs.transparentBg
        binding.switchTransparentBg.setOnCheckedChangeListener { _, c -> prefs.transparentBg = c }

        // Clock
        binding.switch24Hour.isChecked = prefs.use24Hour
        binding.switch24Hour.setOnCheckedChangeListener { _, c -> prefs.use24Hour = c }
        binding.switchShowDate.isChecked = prefs.showDate
        binding.switchShowDate.setOnCheckedChangeListener { _, c -> prefs.showDate = c }

        // Screen time
        binding.switchScreenTime.isChecked = prefs.showScreenTime
        binding.switchScreenTime.setOnCheckedChangeListener { _, c -> prefs.showScreenTime = c }
        binding.btnGrantUsage.setOnClickListener {
            try { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
            catch (_: Exception) {}
        }

        // Home app count
        binding.npHomeApps.minValue = 1
        binding.npHomeApps.maxValue = 10
        binding.npHomeApps.value    = prefs.homeAppCount
        binding.npHomeApps.setOnValueChangedListener { _, _, new -> prefs.homeAppCount = new }

        // Weather location
        updateWeatherLocationLabel()
        binding.btnSetWeatherLocation.setOnClickListener { requestLocationAndSave() }

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

    // ── Weather location ──────────────────────────────────────────────────────

    private fun updateWeatherLocationLabel() {
        val city = prefs.weatherCity
        binding.tvWeatherLocationStatus.text = if (city.isNotEmpty())
            "Location: $city" else "Location not set"
    }

    private fun requestLocationAndSave() {
        val hasFine   = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                REQ_LOCATION
            )
            return
        }
        fetchAndSaveLocation()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_LOCATION && grantResults.any { it == PackageManager.PERMISSION_GRANTED }) {
            fetchAndSaveLocation()
        } else {
            Toast.makeText(this, "Location permission needed for weather", Toast.LENGTH_SHORT).show()
        }
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

            // ✅ Save location coords immediately — no waiting for geocode/weather
            prefs.weatherLat = loc.first
            prefs.weatherLon = loc.second
            // Show coords right away as placeholder
            val coordLabel = "${String.format("%.4f", loc.first)}, ${String.format("%.4f", loc.second)}"
            prefs.weatherCity = coordLabel
            binding.btnSetWeatherLocation.isEnabled = true
            binding.tvWeatherLocationStatus.text = "Location: $coordLabel"
            Toast.makeText(this@SettingsActivity, "Location saved!", Toast.LENGTH_SHORT).show()

            // Geocode + weather fetch in background — non-blocking
            scope.launch {
                val city = withContext(Dispatchers.IO) {
                    try {
                        val geo = Geocoder(this@SettingsActivity, Locale.getDefault())
                        @Suppress("DEPRECATION")
                        val addrs = geo.getFromLocation(loc.first, loc.second, 1)
                        addrs?.firstOrNull()?.locality
                            ?: addrs?.firstOrNull()?.subAdminArea
                            ?: coordLabel
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

        // Use cached if recent (< 10 min)
        val cached = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        ).mapNotNull { p ->
            try { if (lm.isProviderEnabled(p)) lm.getLastKnownLocation(p) else null }
            catch (_: Exception) { null }
        }.maxByOrNull { it.time }

        if (cached != null && System.currentTimeMillis() - cached.time < 10 * 60 * 1000L) {
            return Pair(cached.latitude, cached.longitude)
        }

        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .filter { try { lm.isProviderEnabled(it) } catch (_: Exception) { false } }

        if (providers.isEmpty()) {
            return cached?.let { Pair(it.latitude, it.longitude) }
        }

        // Channel-based — avoids suspendCancellableCoroutine resume signature issues
        val channel = kotlinx.coroutines.channels.Channel<Pair<Double, Double>?>(1)

        val listener = object : android.location.LocationListener {
            override fun onLocationChanged(loc: android.location.Location) {
                lm.removeUpdates(this)
                channel.trySend(Pair(loc.latitude, loc.longitude))
            }
            @Deprecated("Deprecated")
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

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun listen(block: (Int) -> Unit) = object : AdapterView.OnItemSelectedListener {
        override fun onNothingSelected(p: AdapterView<*>?) {}
        override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) = block(pos)
    }
}
