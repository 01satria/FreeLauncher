package com.flowlauncher

import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.flowlauncher.databinding.ActivityFocusBinding

class FocusActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFocusBinding
    private var timer: CountDownTimer? = null
    private var isRunning = false
    private var isWorkPhase = true

    private var workMinutes = 25
    private var breakMinutes = 5
    private var remainingMs = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFocusBinding.inflate(layoutInflater)
        setContentView(binding.root)
        remainingMs = workMinutes * 60_000L
        setupUI()
    }

    private fun setupUI() {
        updateDisplay()

        binding.btnBack.setOnClickListener { finish() }

        // Duration presets
        binding.btnDur25.setOnClickListener  { setDuration(25) }
        binding.btnDur50.setOnClickListener  { setDuration(50) }
        binding.btnDurCustom.setOnClickListener { showCustomDurationDialog() }

        // Start/Pause/Stop button
        binding.btnStartPause.setOnClickListener {
            when {
                isRunning -> pauseTimer()
                remainingMs > 0 && remainingMs < workMinutes * 60_000L -> startTimer() // resume
                else -> startTimer()
            }
        }

        // Goal field
        binding.etGoal.setOnEditorActionListener { _, _, _ ->
            updateGoalDisplay()
            true
        }
        binding.etGoal.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) updateGoalDisplay()
        }
    }


// github.com/01satria
    private fun setDuration(mins: Int) {
        if (isRunning) return
        workMinutes = mins
        remainingMs = workMinutes * 60_000L
        updateDisplay()
        // Update button states
        val active  = Color.WHITE
        val inactive = Color.parseColor("#AAFFFFFF")
        binding.btnDur25.setTextColor(if (mins == 25) Color.BLACK else inactive)
        binding.btnDur50.setTextColor(if (mins == 50) Color.BLACK else inactive)
    }

    private fun showCustomDurationDialog() {
        if (isRunning) return
        val input = EditText(this).apply {
            hint = "Minutes (1–120)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#66FFFFFF"))
            background = null
            setPadding(32, 16, 32, 16)
        }
        AlertDialog.Builder(this)
            .setTitle("Custom duration")
            .setView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(48, 16, 48, 16)
                addView(input)
            })
            .setPositiveButton("Set") { _, _ ->
                val m = input.text.toString().toIntOrNull()?.coerceIn(1, 120)
                if (m != null) setDuration(m)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateGoalDisplay() {
        val goal = binding.etGoal.text.toString().trim()
        if (goal.isNotEmpty()) {
            binding.ivGoalFlag.visibility  = View.VISIBLE
            binding.tvGoalDisplay.visibility = View.VISIBLE
            binding.tvGoalDisplay.text = goal
        } else {
            binding.ivGoalFlag.visibility  = View.GONE
            binding.tvGoalDisplay.visibility = View.GONE
        }
    }

    private fun startTimer() {
        if (remainingMs <= 0) remainingMs = workMinutes * 60_000L
        timer = object : CountDownTimer(remainingMs, 1000L) {
            override fun onTick(ms: Long) { remainingMs = ms; updateDisplay() }
            override fun onFinish() {
                isRunning = false
                isWorkPhase = !isWorkPhase
                remainingMs = (if (isWorkPhase) workMinutes else breakMinutes) * 60_000L
                updateDisplay()
            }
        }.start()
        isRunning = true
        binding.btnStartPause.text = "STOP"
        updateGoalDisplay()
    }

    private fun pauseTimer() {
        timer?.cancel()
        isRunning = false
        binding.btnStartPause.text = "RESUME"
    }

    private fun updateDisplay() {
        val totalSec = remainingMs / 1000
        binding.tvTimer.text = String.format("%02d:%02d", totalSec / 60, totalSec % 60)
        binding.tvPhase.text = if (isWorkPhase) "FOCUS" else "BREAK"
        binding.tvPhase.setTextColor(
            if (isWorkPhase) Color.WHITE else Color.parseColor("#81C784")
        )
        val total = (if (isWorkPhase) workMinutes else breakMinutes) * 60_000L
        val progress = if (total > 0) ((1f - remainingMs.toFloat() / total) * 100).toInt() else 0
        binding.progressTimer.progress = progress.coerceIn(0, 100)

        if (!isRunning) binding.btnStartPause.text = "START"
    }

    override fun onDestroy() {
        timer?.cancel()
        super.onDestroy()
    }
}
