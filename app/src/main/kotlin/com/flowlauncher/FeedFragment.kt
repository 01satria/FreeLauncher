package com.flowlauncher

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.flowlauncher.databinding.FragmentFeedBinding
import kotlinx.coroutines.*
import android.Manifest

class FeedFragment : Fragment() {

    private var _b: FragmentFeedBinding? = null
    private val b get() = _b!!

    private lateinit var prefs: Prefs
    private lateinit var todoAdapter: TodoAdapter
    private var currentTheme: FeedTheme? = null

    private val eventAdapter = FeedEventAdapter(onPin = { togglePin(it) })
    private val pinnedAdapter = FeedEventAdapter()

    private var tickJob   : Job? = null
    private var searchJob : Job? = null
    private var eventsExpanded = true
    private var currentQuery   = ""

    // Pre-allocated GradientDrawable for section cards (set once, color updated per theme)
    private val sectionBg     = GradientDrawable().apply { cornerRadius = dpToPx(20f) }
    private val sectionBg2    = GradientDrawable().apply { cornerRadius = dpToPx(20f) }
    private val sectionBg3    = GradientDrawable().apply { cornerRadius = dpToPx(20f) }

    private val calendarPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) refreshEvents() }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentFeedBinding.inflate(i, c, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = Prefs(requireContext())

        // Assign pre-allocated backgrounds so we only update color, never allocate
        b.sectionEvents.background    = sectionBg
        b.sectionTasks.background     = sectionBg2
        b.cardScreenTime.background   = sectionBg3

        setupTasks()
        setupEvents()
        setupScreenTime()
        applyTheme()  // dipanggil SETELAH semua adapter diinisialisasi

        b.btnFeedSettings.setOnClickListener {
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
        if (!eventsExpanded) refreshPinnedEvents()
    }

    override fun onPause() {
        super.onPause()
        tickJob?.cancel()
        searchJob?.cancel()
    }

    override fun onDestroyView() {
        tickJob?.cancel()
        searchJob?.cancel()
        _b = null
        super.onDestroyView()
    }

    // ── Theme ─────────────────────────────────────────────────────────────────

    fun applyTheme() {
        val bv = _b ?: return
        val t  = FeedTheme.from(prefs).also { currentTheme = it }

        // Page background
        bv.feedRoot.setBackgroundColor(FeedTheme.pageBg(prefs))

        // Section card backgrounds (single GradientDrawable, just setColor)
        sectionBg.setColor(t.surface)
        sectionBg2.setColor(t.surface)
        sectionBg3.setColor(t.surface)

        // Header
        bv.tvFeedTitle.setTextColor(t.onSurface)
        bv.btnFeedSettings.setColorFilter(t.subtle)

        // Events section labels
        bv.tvEventsLabel.setTextColor(t.subtle)
        bv.tvEventsChevron.setTextColor(t.faint)
        bv.tvEventsEmpty.setTextColor(t.faint)
        bv.etEventSearch.setTextColor(t.onSurface)
        bv.etEventSearch.setHintTextColor(t.faint)
        bv.dividerEventSearch.setBackgroundColor(t.divider)

        // Tasks section
        bv.tvTasksLabel.setTextColor(t.subtle)
        bv.btnAddTodo.setColorFilter(t.subtle)
        bv.tvTasksEmpty.setTextColor(t.faint)

        // Screen time
        bv.tvScreenTimeLabel.setTextColor(t.subtle)
        bv.tvScreenTimeTotal.setTextColor(t.onSurface)

        // Push theme into adapters (partial rebind — no full layout pass)
        eventAdapter.applyTheme(t)
        pinnedAdapter.applyTheme(t)
        if (::todoAdapter.isInitialized) todoAdapter.applyTheme(t)
    }

    // ── Events ────────────────────────────────────────────────────────────────

    private fun setupEvents() {
        b.rvEvents.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = eventAdapter
            setHasFixedSize(false)
            overScrollMode = View.OVER_SCROLL_NEVER
            itemAnimator = null
            isNestedScrollingEnabled = false
        }
        b.rvPinnedEvents.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = pinnedAdapter
            setHasFixedSize(false)
            overScrollMode = View.OVER_SCROLL_NEVER
            itemAnimator = null
            isNestedScrollingEnabled = false
        }

        b.btnGrantCalendar.setOnClickListener {
            calendarPermLauncher.launch(Manifest.permission.READ_CALENDAR)
        }

        // Apply initial state (expanded by default)
        updateEventsCollapse(animate = false)

        b.layoutEventsHeader.setOnClickListener {
            eventsExpanded = !eventsExpanded
            updateEventsCollapse(animate = true)
        }

        // Search
        b.etEventSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun afterTextChanged(s: Editable?) {}
            override fun onTextChanged(s: CharSequence?, st: Int, bc: Int, c: Int) {
                val q = s?.toString().orEmpty()
                b.btnClearEventSearch.visibility = if (q.isNotBlank()) View.VISIBLE else View.GONE
                searchJob?.cancel()
                searchJob = viewLifecycleOwner.lifecycleScope.launch {
                    delay(280)
                    currentQuery = q
                    refreshEvents(query = q)
                }
            }
        })
        b.btnClearEventSearch.setOnClickListener {
            b.etEventSearch.setText("")
            currentQuery = ""
        }

        refreshEvents()
    }

    private fun updateEventsCollapse(animate: Boolean) {
        val bv = _b ?: return
        if (eventsExpanded) {
            bv.tvEventsChevron.text = "▾"
            bv.rvPinnedEvents.visibility = View.GONE
            if (animate) {
                bv.layoutEventsContent.animate().alpha(1f).setDuration(180).withStartAction {
                    bv.layoutEventsContent.alpha = 0f
                    bv.layoutEventsContent.visibility = View.VISIBLE
                }.start()
            } else {
                bv.layoutEventsContent.visibility = View.VISIBLE
                bv.layoutEventsContent.alpha = 1f
            }
        } else {
            bv.tvEventsChevron.text = "▸"
            if (animate) {
                bv.layoutEventsContent.animate().alpha(0f).setDuration(150).withEndAction {
                    bv.layoutEventsContent.visibility = View.GONE
                    refreshPinnedEvents()
                }.start()
            } else {
                bv.layoutEventsContent.visibility = View.GONE
                refreshPinnedEvents()
            }
        }
    }

    fun refreshEvents(query: String = currentQuery) {
        val bv = _b ?: return
        if (!CalendarHelper.hasPermission(requireContext())) {
            bv.layoutCalendarPermission.visibility = View.VISIBLE
            bv.rvEvents.visibility = View.GONE
            bv.tvEventsEmpty.visibility = View.GONE
            eventAdapter.setShowPinButton(false)
            return
        }
        bv.layoutCalendarPermission.visibility = View.GONE
        eventAdapter.setShowPinButton(query.isNotBlank())

        viewLifecycleOwner.lifecycleScope.launch {
            val events = CalendarHelper.getUpcomingEvents(requireContext(), query = query)
            val bv2 = _b ?: return@launch
            if (events.isEmpty()) {
                bv2.rvEvents.visibility = View.GONE
                bv2.tvEventsEmpty.visibility = View.VISIBLE
                bv2.tvEventsEmpty.text = if (query.isNotBlank())
                    "No events matching \"$query\"."
                else
                    "No upcoming events."
            } else {
                bv2.tvEventsEmpty.visibility = View.GONE
                bv2.rvEvents.visibility = View.VISIBLE
                eventAdapter.updatePinnedIds(prefs.pinnedEventIds)
                currentTheme?.let { eventAdapter.applyTheme(it) }
                eventAdapter.setEvents(events)
            }
        }
    }

    // ── Pin ───────────────────────────────────────────────────────────────────

    private fun togglePin(event: EventItem) {
        val ids = prefs.pinnedEventIds.toMutableSet()
        if (event.id in ids) ids.remove(event.id) else ids.add(event.id)
        prefs.pinnedEventIds = ids
        eventAdapter.updatePinnedIds(ids)
        if (!eventsExpanded) refreshPinnedEvents()
    }

    private fun refreshPinnedEvents() {
        val bv = _b ?: return
        if (eventsExpanded || !CalendarHelper.hasPermission(requireContext())) {
            bv.rvPinnedEvents.visibility = View.GONE
            return
        }
        val ids = prefs.pinnedEventIds
        if (ids.isEmpty()) { bv.rvPinnedEvents.visibility = View.GONE; return }

        viewLifecycleOwner.lifecycleScope.launch {
            val pinned = CalendarHelper
                .getUpcomingEvents(requireContext(), limit = Int.MAX_VALUE, rangeDays = 365)
                .filter { it.id in ids }
            val bv2 = _b ?: return@launch
            if (pinned.isEmpty()) {
                bv2.rvPinnedEvents.visibility = View.GONE
            } else {
                currentTheme?.let { pinnedAdapter.applyTheme(it) }
                pinnedAdapter.setEvents(pinned)
                bv2.rvPinnedEvents.visibility = View.VISIBLE
            }
        }
    }

    // ── Tasks ─────────────────────────────────────────────────────────────────

    private fun setupTasks() {
        todoAdapter = TodoAdapter({ pos -> toggleTask(pos) }, { pos -> deleteTask(pos) })
        b.rvTodos.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = todoAdapter
            setHasFixedSize(false)
            overScrollMode = View.OVER_SCROLL_NEVER
            itemAnimator = null
            isNestedScrollingEnabled = false
        }
        b.btnAddTodo.setOnClickListener { showAddTaskDialog() }
        refreshTasks()
    }

    private fun refreshTasks() {
        val todos = prefs.todoItems
        todoAdapter.setItems(todos)
        _b?.tvTasksEmpty?.visibility = if (todos.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showAddTaskDialog() {
        val ctx = requireContext()
        val t   = currentTheme
        val input = EditText(ctx).apply {
            hint = "New task…"
            setHintTextColor(t?.faint ?: 0x55FFFFFF.toInt())
            setTextColor(t?.onSurface ?: Color.WHITE)
            background = null
            setPadding(dpToPx(4f).toInt(), dpToPx(8f).toInt(), dpToPx(4f).toInt(), dpToPx(8f).toInt())
        }
        val wrap = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(24f).toInt(), dpToPx(8f).toInt(), dpToPx(24f).toInt(), 0)
            addView(input)
        }
        AlertDialog.Builder(ctx)
            .setTitle("Add task")
            .setView(wrap)
            .setPositiveButton("Add") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isNotEmpty()) {
                    prefs.todoItems = prefs.todoItems + TodoItem(text, false)
                    refreshTasks()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun toggleTask(pos: Int) {
        val list = todoAdapter.getItems().toMutableList()
        if (pos in list.indices) {
            list[pos] = list[pos].copy(done = !list[pos].done)
            prefs.todoItems = list; refreshTasks()
        }
    }

    private fun deleteTask(pos: Int) {
        val list = todoAdapter.getItems().toMutableList()
        if (pos in list.indices) {
            list.removeAt(pos); prefs.todoItems = list; refreshTasks()
        }
    }

    // ── Screen Time ───────────────────────────────────────────────────────────

    private fun setupScreenTime() { refreshScreenTime() }

    private fun refreshScreenTime() {
        val bv = _b ?: return
        if (!ScreenTimeHelper.hasPermission(requireContext())) {
            bv.cardScreenTime.visibility = View.GONE; return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val apps = try { AppRepository.loadApps(requireContext(), prefs) }
                       catch (_: Exception) { emptyList() }
            val bv2 = _b ?: return@launch
            val total = apps.sumOf { it.screenTimeMinutes }
            if (total <= 0) { bv2.cardScreenTime.visibility = View.GONE; return@launch }

            bv2.cardScreenTime.visibility = View.VISIBLE
            bv2.tvScreenTimeTotal.text = ScreenTimeHelper.formatMinutes(total)

            val top = apps.filter { it.screenTimeMinutes > 0 }
                .sortedByDescending { it.screenTimeMinutes }.take(5)

            val maxMin = top.firstOrNull()?.screenTimeMinutes?.toFloat() ?: 1f
            val t      = currentTheme
            bv2.layoutTopApps.removeAllViews()

            top.forEach { app ->
                val row = LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_screentime_row, bv2.layoutTopApps, false)

                row.findViewById<TextView>(R.id.tvStName).apply {
                    text = app.label
                    setTextColor(t?.onSurface ?: Color.WHITE)
                }
                row.findViewById<TextView>(R.id.tvStTime).apply {
                    text = ScreenTimeHelper.formatMinutes(app.screenTimeMinutes)
                    setTextColor(t?.subtle ?: 0x88FFFFFF.toInt())
                }

                // Animate bar width proportional to max
                val barView = row.findViewById<View>(R.id.viewStBar)
                val frac    = app.screenTimeMinutes / maxMin

                // Set bar background color = theme onSurface at reduced alpha
                val barBg = GradientDrawable().apply {
                    cornerRadius = 100f
                    setColor(if (t?.isLight == true)
                        Color.parseColor("#CCCCCC")
                    else
                        0x55FFFFFF.toInt())
                }
                barView.background = barBg

                // Width set post-layout via post{}
                barView.post {
                    val parent = barView.parent as? View ?: return@post
                    val targetW = (parent.width * frac).toInt().coerceAtLeast(4)
                    barView.layoutParams = barView.layoutParams.also { it.width = targetW }
                    barView.requestLayout()
                }

                bv2.layoutTopApps.addView(row)
            }
        }
    }

    // ── Countdown ticker ─────────────────────────────────────────────────────

    private fun startCountdownTicker() {
        tickJob?.cancel()
        tickJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                delay(30_000L)
                if (_b != null && CalendarHelper.hasPermission(requireContext())) {
                    if (eventsExpanded) eventAdapter.notifyItemRangeChanged(
                        0, eventAdapter.itemCount, "tick")
                    else pinnedAdapter.notifyItemRangeChanged(
                        0, pinnedAdapter.itemCount, "tick")
                }
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun dpToPx(dp: Float): Float =
        dp * (resources.displayMetrics.density)
}
