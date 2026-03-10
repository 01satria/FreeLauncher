package com.flowlauncher

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.*
import com.flowlauncher.databinding.ActivityFocusBinding

class FocusActivity : Activity() {

    private lateinit var binding: ActivityFocusBinding
    private var timer: CountDownTimer? = null
    private var isRunning = false
    private var isWorkPhase = true

    // Pomodoro defaults (minutes)
    private var workMinutes = 25
    private var breakMinutes = 5
    private var remainingMs = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawableResource(android.R.color.black)
        binding = ActivityFocusBinding.inflate(layoutInflater)
        setContentView(binding.root)

        remainingMs = workMinutes * 60_000L

        setupUI()
    }

    private fun setupUI() {
        updateDisplay()

        binding.btnStartPause.setOnClickListener {
            if (isRunning) pauseTimer() else startTimer()
        }

        binding.btnReset.setOnClickListener { resetTimer() }

        binding.btnBack.setOnClickListener { finish() }

        // Work/break duration pickers
        binding.npWork.minValue = 1
        binding.npWork.maxValue = 90
        binding.npWork.value = workMinutes
        binding.npWork.setOnValueChangedListener { _, _, new ->
            workMinutes = new
            if (isWorkPhase && !isRunning) {
                remainingMs = workMinutes * 60_000L
                updateDisplay()
            }
        }

        binding.npBreak.minValue = 1
        binding.npBreak.maxValue = 30
        binding.npBreak.value = breakMinutes
        binding.npBreak.setOnValueChangedListener { _, _, new ->
            breakMinutes = new
            if (!isWorkPhase && !isRunning) {
                remainingMs = breakMinutes * 60_000L
                updateDisplay()
            }
        }
    }

    private fun startTimer() {
        if (remainingMs <= 0) remainingMs = (if (isWorkPhase) workMinutes else breakMinutes) * 60_000L

        timer = object : CountDownTimer(remainingMs, 1000L) {
            override fun onTick(ms: Long) {
                remainingMs = ms
                updateDisplay()
            }
            override fun onFinish() {
                isRunning = false
                remainingMs = 0
                updateDisplay()
                // Switch phase
                isWorkPhase = !isWorkPhase
                remainingMs = (if (isWorkPhase) workMinutes else breakMinutes) * 60_000L
                binding.tvPhase.text = if (isWorkPhase) "Focus" else "Break"
                binding.tvPhase.setTextColor(if (isWorkPhase) Color.WHITE else Color.parseColor("#81C784"))
                updateDisplay()
            }
        }.start()
        isRunning = true
        binding.btnStartPause.text = "Pause"
    }

    private fun pauseTimer() {
        timer?.cancel()
        isRunning = false
        binding.btnStartPause.text = "Resume"
    }

    private fun resetTimer() {
        timer?.cancel()
        isRunning = false
        isWorkPhase = true
        remainingMs = workMinutes * 60_000L
        binding.tvPhase.text = "Focus"
        binding.tvPhase.setTextColor(Color.WHITE)
        binding.btnStartPause.text = "Start"
        updateDisplay()
    }

    private fun updateDisplay() {
        val totalSec = remainingMs / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        binding.tvTimer.text = String.format("%02d:%02d", min, sec)

        // Circular progress (0–100)
        val total = (if (isWorkPhase) workMinutes else breakMinutes) * 60_000L
        val progress = if (total > 0) ((1f - remainingMs.toFloat() / total) * 100).toInt() else 0
        binding.progressTimer.progress = progress

        binding.tvPhase.text = if (isWorkPhase) "Focus" else "Break"
    }

    override fun onDestroy() {
        timer?.cancel()
        super.onDestroy()
    }
}
