package com.flowlauncher

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class FeedEventAdapter : RecyclerView.Adapter<FeedEventAdapter.VH>() {

    private val items = mutableListOf<EventItem>()

    fun setEvents(list: List<EventItem>) {
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = items.size
            override fun getNewListSize() = list.size
            override fun areItemsTheSame(o: Int, n: Int) = items[o].id == list[n].id
            override fun areContentsTheSame(o: Int, n: Int): Boolean {
                val a = items[o]; val b = list[n]
                return a.title == b.title && a.startMs == b.startMs &&
                       a.endMs == b.endMs && a.calendarColor == b.calendarColor
            }
        })
        items.clear()
        items.addAll(list)
        diff.dispatchUpdatesTo(this)
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_event, parent, false))

    override fun onBindViewHolder(holder: VH, pos: Int) = holder.bind(items[pos])

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        private val block      : LinearLayout = v.findViewById(R.id.eventBlock)
        private val tvTitle    : TextView     = v.findViewById(R.id.tvEventTitle)
        private val tvDate     : TextView     = v.findViewById(R.id.tvEventDate)
        private val tvCountdown: TextView     = v.findViewById(R.id.tvCountdown)

        // Pre-allocated per ViewHolder — reused on every bind, no GC pressure.
        private val blockBg     = GradientDrawable().apply { cornerRadius = 36f }
        private val countdownBg = GradientDrawable().apply { cornerRadius = 24f }

        private val dateTimeFmt = SimpleDateFormat("EEE, MMM d · HH:mm", Locale.getDefault())
        private val dateFmt     = SimpleDateFormat("EEE, MMM d", Locale.getDefault())

        init {
            // Attach reusable drawables once — setColor/setTint in bind() mutates in place.
            block.background       = blockBg
            tvCountdown.background = countdownBg
        }

        fun bind(e: EventItem) {
            tvTitle.text = e.title
            tvDate.text  = if (e.allDay) dateFmt.format(Date(e.startMs))
                           else dateTimeFmt.format(Date(e.startMs))

            // Mutate pre-allocated drawable — no allocation.
            val base = e.calendarColor or 0xFF000000.toInt()
            blockBg.setColor(base)
            blockBg.alpha = 200

            // Countdown badge
            val cd = e.countdown
            tvCountdown.text = cd
            val (bgCol, txtCol) = when (cd) {
                "Now"  -> 0xCC00C853.toInt() to Color.WHITE
                "Done" -> 0x33FFFFFF        to 0x88FFFFFF.toInt()
                else   -> 0x22FFFFFF        to 0xEEFFFFFF.toInt()
            }
            countdownBg.setColor(bgCol)
            tvCountdown.setTextColor(txtCol)
        }
    }
}
