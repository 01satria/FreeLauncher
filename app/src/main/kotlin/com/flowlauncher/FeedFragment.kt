package com.flowlauncher

import androidx.appcompat.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.flowlauncher.databinding.FragmentFeedBinding
import kotlinx.coroutines.*
import android.Manifest
import android.provider.Settings

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
    // cornerRadius diset di onViewCreated setelah context tersedia — BUKAN di property init
    private val sectionBg     = GradientDrawable()
    private val sectionBg2    = GradientDrawable()
    private val sectionBg3    = GradientDrawable()

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

        // Set cornerRadius setelah context tersedia (resources.displayMetrics sudah siap)
        val r = dpToPx(20f)
        sectionBg.cornerRadius  = r
        sectionBg2.cornerRadius = r
        sectionBg3.cornerRadius = r

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
        applyFeedWindowInsets()
    }

    private fun applyFeedWindowInsets() {
        val density = resources.displayMetrics.density
        fun Int.dp() = (this * density).toInt()

        ViewCompat.setOnApplyWindowInsetsListener(b.root) { _, insets ->
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val navBar    = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom

            b.feedContentLayout.setPadding(
                20.dp(), statusBar + 16.dp(), 20.dp(), navBar + 32.dp()
            )
            insets
        }
        ViewCompat.requestApplyInsets(b.root)
    }

    override fun onResume() {
        super.onResume()
        prefs = Prefs(requireContext())
        applyTheme()
        if (::todoAdapter.isInitialized) refreshTasks()
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
        
        FontHelper.applyFont(requireContext(), prefs, bv.tvFeedTitle, bv.tvEventsLabel, bv.tvEventsChevron, bv.tvEventsEmpty, bv.etEventSearch)

        // Tasks section
        bv.tvTasksLabel.setTextColor(t.subtle)
        bv.btnAddTodo.setColorFilter(t.subtle)
        bv.tvTasksEmpty.setTextColor(t.faint)

        // Screen time
        bv.tvScreenTimeLabel.setTextColor(t.subtle)
        bv.tvScreenTimeTotal.setTextColor(t.onSurface)
        
        FontHelper.applyFont(requireContext(), prefs, bv.tvTasksLabel, bv.tvTasksEmpty, bv.etAddTodo, bv.tvScreenTimeLabel, bv.tvScreenTimeTotal, bv.tvScreenTimePermHint)
        // Re-check permission state on theme change (onResume path)
        if (prefs.showScreenTime && !ScreenTimeHelper.hasPermission(requireContext())) {
            bv.cardScreenTime.visibility = View.VISIBLE
            bv.layoutScreenTimePermission.visibility = View.VISIBLE
            bv.layoutTopApps.visibility = View.GONE
        }

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
            val allEvents = CalendarHelper
                .getUpcomingEvents(requireContext(), limit = Int.MAX_VALUE, rangeDays = 365)
            
            // Deduplicate: If multiple instances for the same event ID exist (recurring), 
            // only take the first one (closest to now).
            val pinned = allEvents
                .filter { it.id in ids }
                .groupBy { it.id }
                .map { it.value.first() }
                .sortedBy { it.startMs }

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

    // Pre-allocated GradientDrawable untuk 3 chip — tidak dialokasikan ulang saat bind
    // Warna berbeda untuk tiap chip (indeks 0,1,2)
    private val chipBg = Array(3) { GradientDrawable() }

    // Palet warna chip: soft, kontras baik di dark & light
    private val CHIP_COLORS = intArrayOf(
        0xFF1A237E.toInt(),   // indigo deep — chip 1
        0xFF1B5E20.toInt(),   // green deep  — chip 2
        0xFF4A148C.toInt()    // purple deep  — chip 3
    )
    private val CHIP_COLORS_LIGHT = intArrayOf(
        0xFFBBDEFB.toInt(),   // blue light   — chip 1
        0xFFC8E6C9.toInt(),   // green light  — chip 2
        0xFFE1BEE7.toInt()    // purple light — chip 3
    )

    private fun setupScreenTime() {
        // Set cornerRadius sekali saja ke semua chip bg
        val r = dpToPx(14f)
        chipBg.forEach { it.cornerRadius = r }

        _b?.btnGrantUsageAccess?.setOnClickListener {
            try {
                startActivity(android.content.Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            } catch (_: Exception) {}
        }
        refreshScreenTime()
    }

    private fun refreshScreenTime() {
        val bv = _b ?: return

        // Fitur dimatikan dari Settings
        if (!prefs.showScreenTime) {
            bv.cardScreenTime.visibility = View.GONE
            return
        }

        // Kartu selalu tampil jika fitur aktif
        bv.cardScreenTime.visibility = View.VISIBLE

        if (!ScreenTimeHelper.hasPermission(requireContext())) {
            bv.layoutScreenTimePermission.visibility = View.VISIBLE
            bv.layoutTopApps.visibility = View.GONE
            bv.tvScreenTimeTotal.text = ""
            return
        }

        bv.layoutScreenTimePermission.visibility = View.GONE
        bv.layoutTopApps.visibility = View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch {
            val apps = try { AppRepository.loadApps(requireContext(), prefs) }
                       catch (_: Exception) { emptyList() }
            val bv2 = _b ?: return@launch

            val total = apps.sumOf { it.screenTimeMinutes }
            bv2.tvScreenTimeTotal.text = if (total > 0)
                ScreenTimeHelper.formatMinutes(total) else ""

            // Hanya top 3
            val top3 = apps.filter { it.screenTimeMinutes > 0 }
                .sortedByDescending { it.screenTimeMinutes }
                .take(3)

            // ID 3 chip yang sudah di-include di XML
            val chipIds = intArrayOf(R.id.chipApp1, R.id.chipApp2, R.id.chipApp3)
            val isLight = currentTheme?.isLight == true
            val palette = if (isLight) CHIP_COLORS_LIGHT else CHIP_COLORS

            chipIds.forEachIndexed { idx, chipId ->
                val chip = bv2.layoutTopApps.findViewById<View>(chipId) ?: return@forEachIndexed
                val app  = top3.getOrNull(idx)

                if (app == null) {
                    // Tidak ada data cukup → sembunyikan chip ini
                    chip.visibility = View.INVISIBLE
                    return@forEachIndexed
                }

                chip.visibility = View.VISIBLE

                // Terapkan warna background chip — reuse GradientDrawable yang sudah ada
                chipBg[idx].setColor(palette[idx])
                chip.background = chipBg[idx]

                chip.findViewById<TextView>(R.id.tvChipName).apply {
                    text = app.label
                    setTextColor(if (isLight) 0xCC000000.toInt() else 0xAAFFFFFF.toInt())
                }
                chip.findViewById<TextView>(R.id.tvChipTime).apply {
                    text = ScreenTimeHelper.formatMinutes(app.screenTimeMinutes)
                    setTextColor(if (isLight) 0xFF000000.toInt() else Color.WHITE)
                }
            }

            if (top3.isEmpty()) {
                bv2.tvScreenTimeTotal.text = ""
                bv2.layoutTopApps.visibility = View.GONE
            }
        }
    }

    // ── Countdown ticker ─────────────────────────────────────────────────────

    private fun startCountdownTicker() {
        tickJob?.cancel()
        tickJob = viewLifecycleOwner.lifecycleScope.launch {
            var counter = 0
            while (isActive) {
                delay(30_000L)
                if (_b == null || !CalendarHelper.hasPermission(requireContext())) continue
                
                // 1. Refresh UI countdown components (smooth 30s update)
                if (eventAdapter.itemCount > 0)
                    eventAdapter.notifyItemRangeChanged(0, eventAdapter.itemCount, FeedEventAdapter.PAYLOAD_TICK)
                if (pinnedAdapter.itemCount > 0)
                    pinnedAdapter.notifyItemRangeChanged(0, pinnedAdapter.itemCount, FeedEventAdapter.PAYLOAD_TICK)

                // 2. Periodically refresh data from Calendar provider (every 5 mins)
                // This ensures we pick up external changes and update instances for recurring events.
                counter++
                if (counter >= 10) { // 10 * 30s = 5m
                    counter = 0
                    if (currentQuery.isBlank()) refreshEvents()
                    if (!eventsExpanded) refreshPinnedEvents()
                }
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun dpToPx(dp: Float): Float =
        dp * (resources.displayMetrics.density)
}
