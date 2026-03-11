package com.flowlauncher

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

/**
 * @param showPinButton  Show pin/unpin button — true during search mode only.
 * @param pinnedIds      Set of currently pinned event IDs (used to render pin state).
 * @param onPin          Called when user taps the pin button.
 */
class FeedEventAdapter(
    private var showPinButton: Boolean = false,
    private var pinnedIds: Set<Long> = emptySet(),
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
                       a.endMs == b.endMs && a.calendarColor == b.calendarColor &&
                       // re-bind if pin state changed
                       (a.id in pinnedIds) == (b.id in pinnedIds)
            }
        })
        items.clear()
        items.addAll(list)
        diff.dispatchUpdatesTo(this)
    }

    /** Update pinned IDs and re-render pin buttons without full dataset change. */
    fun updatePinnedIds(ids: Set<Long>) {
        pinnedIds = ids
        notifyItemRangeChanged(0, items.size, PAYLOAD_PIN_STATE)
    }

    /** Switch pin button visibility (e.g. when search query changes). */
    fun setShowPinButton(show: Boolean) {
        if (showPinButton == show) return
        showPinButton = show
        notifyItemRangeChanged(0, items.size, PAYLOAD_PIN_VISIBILITY)
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_event, parent, false))

    override fun onBindViewHolder(holder: VH, pos: Int) = holder.bind(items[pos])

    override fun onBindViewHolder(holder: VH, pos: Int, payloads: List<Any>) {
        if (payloads.isEmpty()) { holder.bind(items[pos]); return }
        // Partial re-bind for pin state / visibility only
        val item = items[pos]
        for (p in payloads) {
            when (p) {
                PAYLOAD_PIN_STATE      -> holder.refreshPinState(item)
                PAYLOAD_PIN_VISIBILITY -> holder.refreshPinVisibility(item)
            }
        }
    }

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        private val block      : LinearLayout = v.findViewById(R.id.eventBlock)
        private val tvTitle    : TextView     = v.findViewById(R.id.tvEventTitle)
        private val tvDate     : TextView     = v.findViewById(R.id.tvEventDate)
        private val tvCountdown: TextView     = v.findViewById(R.id.tvCountdown)
        private val btnPin     : ImageButton  = v.findViewById(R.id.btnPinEvent)

        private val blockBg     = GradientDrawable().apply { cornerRadius = 36f }
        private val countdownBg = GradientDrawable().apply { cornerRadius = 24f }

        private val dateTimeFmt = SimpleDateFormat("EEE, MMM d · HH:mm", Locale.getDefault())
        private val dateFmt     = SimpleDateFormat("EEE, MMM d", Locale.getDefault())

        init {
            block.background       = blockBg
            tvCountdown.background = countdownBg
        }

        fun bind(e: EventItem) {
            tvTitle.text = e.title
            tvDate.text  = if (e.allDay) dateFmt.format(Date(e.startMs))
                           else dateTimeFmt.format(Date(e.startMs))

            val base = e.calendarColor or 0xFF000000.toInt()
            blockBg.setColor(base)
            blockBg.alpha = 200

            val cd = e.countdown
            tvCountdown.text = cd
            val (bgCol, txtCol) = when (cd) {
                "Now"  -> 0xCC00C853.toInt() to Color.WHITE
                "Done" -> 0x33FFFFFF        to 0x88FFFFFF.toInt()
                else   -> 0x22FFFFFF        to 0xEEFFFFFF.toInt()
            }
            countdownBg.setColor(bgCol)
            tvCountdown.setTextColor(txtCol)

            refreshPinVisibility(e)
            btnPin.setOnClickListener { onPin?.invoke(e) }
        }

        fun refreshPinState(e: EventItem) {
            val pinned = e.id in pinnedIds
            btnPin.setImageResource(if (pinned) R.drawable.ic_pin_filled else R.drawable.ic_pin_outline)
            btnPin.alpha = if (pinned) 1f else 0.5f
        }

        fun refreshPinVisibility(e: EventItem) {
            btnPin.visibility = if (showPinButton) View.VISIBLE else View.GONE
            refreshPinState(e)
        }
    }

    companion object {
        private const val PAYLOAD_PIN_STATE      = "pin_state"
        private const val PAYLOAD_PIN_VISIBILITY = "pin_visibility"
    }
}
