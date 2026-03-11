package com.flowlauncher

import android.Manifest
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.flowlauncher.databinding.FragmentFeedBinding
import kotlinx.coroutines.*

class FeedFragment : Fragment() {

    private var _binding: FragmentFeedBinding? = null
    private val binding get() = _binding!!

    private lateinit var prefs: Prefs
    private lateinit var todoAdapter: TodoAdapter

    // Main events list (search results when query active, full list otherwise)
    private val eventAdapter = FeedEventAdapter(
        showPinButton = false,
        onPin = { event -> togglePin(event) }
    )

    // Pinned events list — shown only when events card is COLLAPSED
    private val pinnedAdapter = FeedEventAdapter(
        showPinButton = false,
        onPin = null
    )

    private var tickJob: Job? = null
    private var searchJob: Job? = null
    private var eventsExpanded = true
    private var currentQuery = ""

    private val calendarPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) refreshEvents() }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFeedBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = Prefs(requireContext())
        setupTasks()
        setupEvents()
        setupScreenTime()
        applyTheme()

        binding.btnFeedSettings.setOnClickListener {
            startActivity(android.content.Intent(requireContext(), SettingsActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        prefs = Prefs(requireContext())
        applyTheme()
        refreshTasks()
        refreshEvents()
        refreshScreenTime()
        startCountdownTicker()
    }

    override fun onPause() {
        super.onPause()
        tickJob?.cancel()
        searchJob?.cancel()
    }

    override fun onDestroyView() {
        tickJob?.cancel()
        searchJob?.cancel()
        _binding = null
        super.onDestroyView()
    }

    // ── Theme ─────────────────────────────────────────────────────────────────

    fun applyTheme() {
        val b = _binding ?: return
        val isLight = prefs.theme == Prefs.THEME_LIGHT

        if (prefs.transparentBg) {
            b.feedRoot.setBackgroundColor(Color.TRANSPARENT)
        } else {
            val bgColor = when (prefs.theme) {
                Prefs.THEME_LIGHT -> Color.parseColor("#F0F0F0")
                Prefs.THEME_OLED  -> Color.BLACK
                else              -> Color.parseColor("#090909")
            }
            b.feedRoot.setBackgroundColor(bgColor)
        }

        val textColor = if (isLight) Color.parseColor("#111111") else Color.WHITE
        b.tvFeedTitle.setTextColor(textColor)
    }

    // ── Events ────────────────────────────────────────────────────────────────

    private fun setupEvents() {
        // Main events RecyclerView
        binding.rvEvents.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = eventAdapter
            setHasFixedSize(false)
            overScrollMode = View.OVER_SCROLL_NEVER
            itemAnimator = null
            isNestedScrollingEnabled = false
        }

        // Pinned events RecyclerView (shown only when collapsed)
        binding.rvPinnedEvents.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = pinnedAdapter
            setHasFixedSize(false)
            overScrollMode = View.OVER_SCROLL_NEVER
            itemAnimator = null
            isNestedScrollingEnabled = false
        }

        binding.btnGrantCalendar.setOnClickListener {
            calendarPermLauncher.launch(Manifest.permission.READ_CALENDAR)
        }

        // Collapse / expand toggle
        binding.layoutEventsHeader.setOnClickListener {
            eventsExpanded = !eventsExpanded
            updateEventsCollapse()
        }

        // Search bar
        binding.etEventSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun afterTextChanged(s: Editable?) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {
                val query = s?.toString().orEmpty()
                binding.btnClearEventSearch.visibility =
                    if (query.isNotBlank()) View.VISIBLE else View.GONE
                searchJob?.cancel()
                searchJob = viewLifecycleOwner.lifecycleScope.launch {
                    delay(300)
                    currentQuery = query
                    refreshEvents(query = query)
                }
            }
        })

        binding.btnClearEventSearch.setOnClickListener {
            binding.etEventSearch.setText("")
            currentQuery = ""
        }

        refreshEvents()
    }

    private fun updateEventsCollapse() {
        val b = _binding ?: return
        if (eventsExpanded) {
            b.layoutEventsContent.visibility = View.VISIBLE
            b.tvEventsChevron.text = "▲"
            b.rvPinnedEvents.visibility = View.GONE
        } else {
            b.layoutEventsContent.visibility = View.GONE
            b.tvEventsChevron.text = "▼"
            // Show pinned events only when collapsed
            refreshPinnedEvents()
        }
    }

    fun refreshEvents(query: String = currentQuery) {
        val b = _binding ?: return
        if (!CalendarHelper.hasPermission(requireContext())) {
            b.layoutCalendarPermission.visibility = View.VISIBLE
            b.rvEvents.visibility = View.GONE
            b.tvEventsEmpty.visibility = View.GONE
            eventAdapter.setShowPinButton(false)
            return
        }
        b.layoutCalendarPermission.visibility = View.GONE

        // Show pin button only when there's an active search query
        eventAdapter.setShowPinButton(query.isNotBlank())

        viewLifecycleOwner.lifecycleScope.launch {
            val events = CalendarHelper.getUpcomingEvents(requireContext(), query = query)
            val bv = _binding ?: return@launch
            when {
                events.isEmpty() -> {
                    bv.rvEvents.visibility = View.GONE
                    bv.tvEventsEmpty.visibility = View.VISIBLE
                    bv.tvEventsEmpty.text = if (query.isNotBlank())
                        "No events matching \"$query\"."
                    else
                        "No upcoming events in next 30 days."
                }
                else -> {
                    bv.tvEventsEmpty.visibility = View.GONE
                    bv.rvEvents.visibility = View.VISIBLE
                    eventAdapter.updatePinnedIds(prefs.pinnedEventIds)
                    eventAdapter.setEvents(events)
                }
            }
        }
    }

    // ── Pin logic ─────────────────────────────────────────────────────────────

    private fun togglePin(event: EventItem) {
        val pinned = prefs.pinnedEventIds.toMutableSet()
        if (event.id in pinned) pinned.remove(event.id) else pinned.add(event.id)
        prefs.pinnedEventIds = pinned
        // Update pin button state in main list
        eventAdapter.updatePinnedIds(pinned)
        // Refresh pinned strip if currently collapsed
        if (!eventsExpanded) refreshPinnedEvents()
    }

    private fun refreshPinnedEvents() {
        val b = _binding ?: return
        if (!eventsExpanded && CalendarHelper.hasPermission(requireContext())) {
            viewLifecycleOwner.lifecycleScope.launch {
                val pinnedIds = prefs.pinnedEventIds
                if (pinnedIds.isEmpty()) {
                    b.rvPinnedEvents.visibility = View.GONE
                    return@launch
                }
                // Fetch all events then filter by pinned IDs
                val all = CalendarHelper.getUpcomingEvents(requireContext(), limit = 100)
                val pinned = all.filter { it.id in pinnedIds }
                val bv = _binding ?: return@launch
                if (pinned.isEmpty()) {
                    bv.rvPinnedEvents.visibility = View.GONE
                } else {
                    bv.rvPinnedEvents.visibility = View.VISIBLE
                    pinnedAdapter.setEvents(pinned)
                }
            }
        } else {
            b.rvPinnedEvents.visibility = View.GONE
        }
    }

    // ── Tasks ─────────────────────────────────────────────────────────────────

    private fun setupTasks() {
        todoAdapter = TodoAdapter({ pos -> toggleTask(pos) }, { pos -> deleteTask(pos) })
        binding.rvTodos.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = todoAdapter
            setHasFixedSize(false)
            overScrollMode = View.OVER_SCROLL_NEVER
            itemAnimator = null
            isNestedScrollingEnabled = false
        }
        binding.btnAddTodo.setOnClickListener { showAddTaskDialog() }
        refreshTasks()
    }

    private fun refreshTasks() {
        val todos = prefs.todoItems
        todoAdapter.setItems(todos)
        _binding?.tvTasksEmpty?.visibility = if (todos.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showAddTaskDialog() {
        val ctx = requireContext()
        val input = EditText(ctx).apply {
            hint = "Add a task..."
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
                    prefs.todoItems = prefs.todoItems.toMutableList().also { it.add(TodoItem(text, false)) }
                    refreshTasks()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun toggleTask(pos: Int) {
        val updated = todoAdapter.getItems().toMutableList()
        if (pos in updated.indices) {
            val item = updated[pos]
            updated[pos] = item.copy(done = !item.done)
            prefs.todoItems = updated
            refreshTasks()
        }
    }

    private fun deleteTask(pos: Int) {
        val updated = todoAdapter.getItems().toMutableList()
        if (pos in updated.indices) {
            updated.removeAt(pos)
            prefs.todoItems = updated
            refreshTasks()
        }
    }

    // ── Screen Time ───────────────────────────────────────────────────────────

    private fun setupScreenTime() { refreshScreenTime() }

    private fun refreshScreenTime() {
        val b = _binding ?: return
        if (!ScreenTimeHelper.hasPermission(requireContext())) {
            b.cardScreenTime.visibility = View.GONE
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val apps = try { AppRepository.loadApps(requireContext(), prefs) }
                       catch (_: Exception) { emptyList() }
            val bv = _binding ?: return@launch
            val totalMin = apps.sumOf { it.screenTimeMinutes }
            if (totalMin <= 0) { bv.cardScreenTime.visibility = View.GONE; return@launch }

            bv.cardScreenTime.visibility = View.VISIBLE
            bv.tvScreenTimeTotal.text = ScreenTimeHelper.formatMinutes(totalMin)

            val top3 = apps.filter { it.screenTimeMinutes > 0 }
                .sortedByDescending { it.screenTimeMinutes }.take(3)

            bv.layoutTopApps.removeAllViews()
            val ctx = requireContext()
            top3.forEach { app ->
                val chip = LayoutInflater.from(ctx)
                    .inflate(R.layout.item_screentime_chip, bv.layoutTopApps, false)
                chip.findViewById<TextView>(R.id.tvChipName).text = app.label
                chip.findViewById<TextView>(R.id.tvChipTime).text =
                    ScreenTimeHelper.formatMinutes(app.screenTimeMinutes)
                val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                lp.marginEnd = 8
                chip.layoutParams = lp
                bv.layoutTopApps.addView(chip)
            }
        }
    }

    // ── Countdown ticker ─────────────────────────────────────────────────────

    private fun startCountdownTicker() {
        tickJob?.cancel()
        tickJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                delay(30_000L)
                if (_binding != null && CalendarHelper.hasPermission(requireContext())) {
                    eventAdapter.notifyDataSetChanged()
                    if (!eventsExpanded) pinnedAdapter.notifyDataSetChanged()
                }
            }
        }
    }
}
