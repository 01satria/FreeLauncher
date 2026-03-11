package com.flowlauncher

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.*
import com.flowlauncher.databinding.ActivitySettingsBinding

class SettingsActivity : Activity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: Prefs

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
            android.app.AlertDialog.Builder(this)
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

    private fun listen(block: (Int) -> Unit) = object : AdapterView.OnItemSelectedListener {
        override fun onNothingSelected(p: AdapterView<*>?) {}
        override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) = block(pos)
    }
}
