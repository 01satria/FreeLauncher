package com.flowlauncher

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class FeedEventAdapter(
    private var showPinButton: Boolean = false,
    private var pinnedIds: Set<Long>   = emptySet(),
    private var theme: FeedTheme?      = null,
    private val onPin: ((EventItem) -> Unit)? = null
) : RecyclerView.Adapter<FeedEventAdapter.VH>() {

    private val items = mutableListOf<EventItem>()

    fun setEvents(list: List<EventItem>) {
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = items.size
            override fun getNewListSize() = list.size
            override fun areItemsTheSame(o: Int, n: Int) = items[o].id == list[n].id
            override fun areContentsTheSame(o: Int, n: Int): Boolean {
                val a = items[o]; val b = list[n]
                return a.title == b.title && a.startMs == b.startMs &&
                       a.calendarColor == b.calendarColor &&
                       (a.id in pinnedIds) == (b.id in pinnedIds)
            }
        })
        items.clear(); items.addAll(list)
        diff.dispatchUpdatesTo(this)
    }

    fun updatePinnedIds(ids: Set<Long>) {
        if (pinnedIds == ids) return
        pinnedIds = ids
        notifyItemRangeChanged(0, items.size, PAYLOAD_PIN)
    }

    fun setShowPinButton(show: Boolean) {
        if (showPinButton == show) return
        showPinButton = show
        notifyItemRangeChanged(0, items.size, PAYLOAD_PIN)
    }

    fun applyTheme(t: FeedTheme) {
        theme = t
        notifyItemRangeChanged(0, items.size, PAYLOAD_THEME)
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_event, parent, false))

    override fun onBindViewHolder(holder: VH, pos: Int) = holder.bind(items[pos])

    override fun onBindViewHolder(holder: VH, pos: Int, payloads: List<Any>) {
        if (payloads.isEmpty()) { holder.bind(items[pos]); return }
        payloads.forEach { p ->
            when (p) {
                PAYLOAD_PIN   -> holder.refreshPin(items[pos])
                PAYLOAD_THEME -> holder.applyTheme(items[pos], theme)
                PAYLOAD_TICK  -> holder.refreshCountdown(items[pos])
            }
        }
    }

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        private val accent    : View        = v.findViewById(R.id.viewEventAccent)
        private val tvTitle   : TextView    = v.findViewById(R.id.tvEventTitle)
        private val tvDate    : TextView    = v.findViewById(R.id.tvEventDate)
        private val tvCountdown: TextView   = v.findViewById(R.id.tvCountdown)
        private val btnPin    : ImageButton = v.findViewById(R.id.btnPinEvent)

        // Pre-allocated GradientDrawables — zero allocation in bind()
        private val accentBg    = GradientDrawable().apply { cornerRadius = 100f }
        private val cdBg        = GradientDrawable().apply { cornerRadius = 100f }

        init {
            accent.background     = accentBg
            tvCountdown.background = cdBg
        }

        fun bind(e: EventItem) {
            tvTitle.text = e.title
            tvDate.text  = formatDate(e)
            
            val prefs = Prefs(itemView.context)
            FontHelper.applyFont(itemView.context, prefs, tvTitle, tvDate, tvCountdown)

            // Accent bar color — calendar color, full opacity
            val base = e.calendarColor or 0xFF000000.toInt()
            accentBg.setColor(base)

            // Countdown pill
            val cd = e.countdown
            tvCountdown.text = cd
            val t = theme
            val (bg, txt) = cdColors(cd, t?.isLight == true)
            cdBg.setColor(bg)
            tvCountdown.setTextColor(txt)

            // Theme text colors
            t?.let {
                tvTitle.setTextColor(it.onSurface)
                tvDate.setTextColor(it.subtle)
            }

            refreshPin(e)
            btnPin.setOnClickListener { onPin?.invoke(e) }
        }

        fun refreshCountdown(e: EventItem) {
            val cd = e.countdown
            tvCountdown.text = cd
            val (bg, txt) = cdColors(cd, theme?.isLight == true)
            cdBg.setColor(bg)
            tvCountdown.setTextColor(txt)
        }

        fun refreshPin(e: EventItem) {
            val pinned = e.id in pinnedIds
            btnPin.visibility = if (showPinButton) View.VISIBLE else View.GONE
            btnPin.setImageResource(if (pinned) R.drawable.ic_pin_filled else R.drawable.ic_pin_outline)
            btnPin.alpha = if (pinned) 1f else 0.45f
        }

        fun applyTheme(e: EventItem, t: FeedTheme?) {
            t ?: return
            tvTitle.setTextColor(t.onSurface)
            tvDate.setTextColor(t.subtle)
        }

        private fun formatDate(e: EventItem): String {
            val fmt = if (e.allDay) DATE_FMT else DATETIME_FMT
            return fmt.format(Date(e.startMs))
        }
    }

    companion object {
        private const val PAYLOAD_PIN   = "pin"
        private const val PAYLOAD_THEME = "theme"
        const val  PAYLOAD_TICK  = "tick"   // countdown refresh — exposed for FeedFragment

        private val DATE_FMT     = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
        private val DATETIME_FMT = SimpleDateFormat("EEE, MMM d · HH:mm", Locale.getDefault())

        // Countdown badge colors
        private val COLOR_NOW_BG        = 0xCC00C853.toInt()
        private val COLOR_NOW_TEXT      = Color.WHITE
        private val COLOR_DONE_BG_DARK  = 0x22FFFFFF
        private val COLOR_DONE_TEXT_DARK  = 0x55FFFFFF.toInt()
        private val COLOR_FUTURE_BG_DARK  = 0x18FFFFFF
        private val COLOR_FUTURE_TEXT_DARK = 0xCCFFFFFF.toInt()
        private val COLOR_DONE_BG_LIGHT   = 0x22000000
        private val COLOR_DONE_TEXT_LIGHT  = 0x55000000.toInt()
        private val COLOR_FUTURE_BG_LIGHT  = 0x12000000
        private val COLOR_FUTURE_TEXT_LIGHT = 0xCC000000.toInt()

        fun cdColors(cd: String, isLight: Boolean = false): Pair<Int, Int> = when (cd) {
            "Now"  -> COLOR_NOW_BG to COLOR_NOW_TEXT
            "Done" -> if (isLight) COLOR_DONE_BG_LIGHT   to COLOR_DONE_TEXT_LIGHT
                      else         COLOR_DONE_BG_DARK     to COLOR_DONE_TEXT_DARK
            else   -> if (isLight) COLOR_FUTURE_BG_LIGHT to COLOR_FUTURE_TEXT_LIGHT
                      else         COLOR_FUTURE_BG_DARK   to COLOR_FUTURE_TEXT_DARK
        }
    }
}
