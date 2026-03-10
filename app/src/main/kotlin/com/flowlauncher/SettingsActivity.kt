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
        val themes = arrayOf("Dark", "Light", "OLED Black")
        val themeVals = arrayOf(Prefs.THEME_DARK, Prefs.THEME_LIGHT, Prefs.THEME_OLED)
        binding.spinnerTheme.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, themes)
        binding.spinnerTheme.setSelection(themeVals.indexOf(prefs.theme).coerceAtLeast(0))
        binding.spinnerTheme.onItemSelectedListener = simpleListener { pos -> prefs.theme = themeVals[pos] }

        // Alignment
        val alignments = arrayOf("Left", "Center", "Right")
        val alignVals  = arrayOf(Prefs.ALIGN_LEFT, Prefs.ALIGN_CENTER, Prefs.ALIGN_RIGHT)
        binding.spinnerAlignment.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, alignments)
        binding.spinnerAlignment.setSelection(alignVals.indexOf(prefs.alignment).coerceAtLeast(0))
        binding.spinnerAlignment.onItemSelectedListener = simpleListener { pos -> prefs.alignment = alignVals[pos] }

        // Clock
        binding.switch24Hour.isChecked = prefs.use24Hour
        binding.switch24Hour.setOnCheckedChangeListener { _, checked -> prefs.use24Hour = checked }
        binding.switchShowDate.isChecked = prefs.showDate
        binding.switchShowDate.setOnCheckedChangeListener { _, checked -> prefs.showDate = checked }

        // Screen time
        binding.switchScreenTime.isChecked = prefs.showScreenTime
        binding.switchScreenTime.setOnCheckedChangeListener { _, checked -> prefs.showScreenTime = checked }
        binding.btnGrantUsage.setOnClickListener {
            try { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) } catch (_: Exception) { }
        }

        // Icons
        binding.switchShowIcons.isChecked = prefs.showIcons
        binding.switchShowIcons.setOnCheckedChangeListener { _, checked -> prefs.showIcons = checked }

        // Home app count
        binding.npHomeApps.minValue = 1
        binding.npHomeApps.maxValue = 10
        binding.npHomeApps.value = prefs.homeAppCount
        binding.npHomeApps.setOnValueChangedListener { _, _, new -> prefs.homeAppCount = new }

        // Font size
        binding.seekFontSize.max = 16
        binding.seekFontSize.progress = (prefs.fontSize - 14).coerceIn(0, 16)
        binding.tvFontSizeVal.text = "${prefs.fontSize}sp"
        binding.seekFontSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
            override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) {
                prefs.fontSize = 14 + (p ?: 0)
                binding.tvFontSizeVal.text = "${prefs.fontSize}sp"
            }
        })

        // Digital Detox
        binding.switchDetox.isChecked = prefs.detoxEnabled
        binding.switchDetox.setOnCheckedChangeListener { _, checked ->
            prefs.detoxEnabled = checked
            if (checked) {
                prefs.detoxEndTime = System.currentTimeMillis() + prefs.detoxDurationMinutes * 60_000L
                Toast.makeText(this, "Detox active for ${prefs.detoxDurationMinutes} min", Toast.LENGTH_SHORT).show()
            } else {
                prefs.detoxEndTime = 0L
            }
        }
        val detoxOptions = arrayOf("30 min", "1 hour", "2 hours", "4 hours", "8 hours")
        val detoxMinutes = intArrayOf(30, 60, 120, 240, 480)
        binding.spinnerDetoxDuration.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, detoxOptions)
        val detoxIdx = detoxMinutes.indexOfFirst { it == prefs.detoxDurationMinutes }.coerceAtLeast(0)
        binding.spinnerDetoxDuration.setSelection(detoxIdx)
        binding.spinnerDetoxDuration.onItemSelectedListener = simpleListener { pos -> prefs.detoxDurationMinutes = detoxMinutes[pos] }

        // Hidden apps
        binding.btnManageHidden.setOnClickListener {
            val cached = AppRepository.getCached()
            if (cached.isEmpty()) {
                Toast.makeText(this, "App list not loaded yet. Try again.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val hidden = prefs.hiddenPackages.toMutableSet()
            val all = cached.map { it.packageName }.toTypedArray()
            val labels = cached.map { it.label }.toTypedArray()
            val checked = all.map { it in hidden }.toBooleanArray()
            android.app.AlertDialog.Builder(this)
                .setTitle("Hidden Apps")
                .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                    if (isChecked) hidden.add(all[which]) else hidden.remove(all[which])
                }
                .setPositiveButton("Save") { _, _ ->
                    prefs.hiddenPackages = hidden
                    AppRepository.invalidate()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun simpleListener(onSelected: (Int) -> Unit) = object : AdapterView.OnItemSelectedListener {
        override fun onNothingSelected(p: AdapterView<*>?) {}
        override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) { onSelected(pos) }
    }
}
