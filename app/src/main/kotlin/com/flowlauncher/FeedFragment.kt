package com.flowlauncher

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.flowlauncher.databinding.FragmentFeedBinding
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class FeedFragment : Fragment() {

    private var _binding: FragmentFeedBinding? = null
    private val binding get() = _binding!!

    private lateinit var prefs: Prefs
    private lateinit var todoAdapter: TodoAdapter
    private val eventAdapter = FeedEventAdapter()

    // Countdown ticker — ticks every 30 seconds while feed is visible
    private var tickJob: Job? = null

    // Modern permission request (replaces deprecated requestPermissions)
    private val calendarPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) refreshEvents()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFeedBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = Prefs(requireContext())
        setupDate()
        setupTasks()
        setupEvents()
        applyTheme()
    }

    override fun onResume() {
        super.onResume()
        applyTheme()
        refreshTasks()
        refreshEvents()
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

    // ── Theme ───────────────────────────────────────────────────────────────

    fun applyTheme() {
        val b = _binding ?: return
        val isLight = prefs.theme == Prefs.THEME_LIGHT

        val bgColor = when (prefs.theme) {
            Prefs.THEME_LIGHT -> Color.parseColor("#F2F2F2")
            Prefs.THEME_OLED  -> Color.BLACK
            else              -> Color.parseColor("#0D0D0D")
        }
        b.feedRoot.setBackgroundColor(bgColor)

        val textPrimary   = if (isLight) Color.parseColor("#111111") else Color.WHITE
        val textSecondary = if (isLight) Color.parseColor("#66000000") else Color.parseColor("#77FFFFFF")
        val labelColor    = if (isLight) Color.parseColor("#55000000") else Color.parseColor("#66FFFFFF")
        val iconTint      = if (isLight) Color.parseColor("#88000000") else Color.parseColor("#AAFFFFFF")
        val emptyColor    = if (isLight) Color.parseColor("#44000000") else Color.parseColor("#44FFFFFF")

        b.tvFeedTitle.setTextColor(textPrimary)
        b.tvFeedDate.setTextColor(textSecondary)
        b.tvSectionTasks.setTextColor(labelColor)
        b.tvSectionEvents.setTextColor(labelColor)
        b.tvTasksEmpty.setTextColor(emptyColor)
        b.tvEventsEmpty.setTextColor(emptyColor)
        b.btnAddTodo.setColorFilter(iconTint)
    }

    // ── Date header ─────────────────────────────────────────────────────────

    private fun setupDate() {
        binding.tvFeedDate.text =
            SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date())
    }

    // ── Tasks ────────────────────────────────────────────────────────────────

    private fun setupTasks() {
        todoAdapter = TodoAdapter { pos -> deleteTask(pos) }
        binding.rvTodos.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = todoAdapter
            setHasFixedSize(false)
            overScrollMode = View.OVER_SCROLL_NEVER
            itemAnimator = null
        }
        binding.btnAddTodo.setOnClickListener { showAddTaskDialog() }
        refreshTasks()
    }

    private fun refreshTasks() {
        val todos = prefs.todos
        todoAdapter.setItems(todos)
        binding.tvTasksEmpty.visibility = if (todos.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showAddTaskDialog() {
        val ctx = requireContext()
        val input = EditText(ctx).apply {
            hint = "Add a task..."
            // Dialog always dark so text is always readable
            setHintTextColor(Color.parseColor("#80FFFFFF"))
            setTextColor(Color.WHITE)
            background = null
            setPadding(32, 16, 32, 16)
        }
        AlertDialog.Builder(ctx)
            .setTitle("New Task")
            .setView(LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(48, 16, 48, 16)
                addView(input)
            })
            .setPositiveButton("Add") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isNotEmpty()) {
                    prefs.todos = prefs.todos.toMutableList().also { it.add(text) }
                    refreshTasks()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteTask(pos: Int) {
        val updated = todoAdapter.getItems().toMutableList()
        if (pos in updated.indices) {
            updated.removeAt(pos)
            prefs.todos = updated
            refreshTasks()
        }
    }

    // ── Events ───────────────────────────────────────────────────────────────

    private fun setupEvents() {
        binding.rvEvents.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = eventAdapter
            setHasFixedSize(false)
            overScrollMode = View.OVER_SCROLL_NEVER
            itemAnimator = null
            nestedScrollingEnabled = false
        }
        binding.btnGrantCalendar.setOnClickListener {
            calendarPermLauncher.launch(Manifest.permission.READ_CALENDAR)
        }
        refreshEvents()
    }

    fun refreshEvents() {
        if (!CalendarHelper.hasPermission(requireContext())) {
            _binding?.layoutCalendarPermission?.visibility = View.VISIBLE
            _binding?.rvEvents?.visibility = View.GONE
            _binding?.tvEventsEmpty?.visibility = View.GONE
            return
        }
        _binding?.layoutCalendarPermission?.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            val events = CalendarHelper.getUpcomingEvents(requireContext())
            val b = _binding ?: return@launch
            when {
                events.isEmpty() -> {
                    b.rvEvents.visibility = View.GONE
                    b.tvEventsEmpty.visibility = View.VISIBLE
                }
                else -> {
                    b.tvEventsEmpty.visibility = View.GONE
                    b.rvEvents.visibility = View.VISIBLE
                    eventAdapter.setEvents(events)
                }
            }
        }
    }

    // Countdown ticker: refresh every 30s for accurate countdowns
    private fun startCountdownTicker() {
        tickJob?.cancel()
        tickJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                delay(30_000L)
                if (_binding != null && CalendarHelper.hasPermission(requireContext())) {
                    // Only re-render existing items (no full IO query) — very cheap
                    eventAdapter.notifyDataSetChanged()
                    // Full refresh every 5 mins to catch new/updated events
                }
            }
        }
    }
}
