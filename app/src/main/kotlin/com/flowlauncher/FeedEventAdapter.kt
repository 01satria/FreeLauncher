package com.flowlauncher

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class FeedEventAdapter : RecyclerView.Adapter<FeedEventAdapter.VH>() {

    private val items = mutableListOf<EventItem>()

    fun setEvents(list: List<EventItem>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
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

        private val dateTimeFmt = SimpleDateFormat("EEE, MMM d · HH:mm", Locale.getDefault())
        private val dateFmt     = SimpleDateFormat("EEE, MMM d", Locale.getDefault())

        fun bind(e: EventItem) {
            tvTitle.text = e.title
            tvDate.text  = if (e.allDay) dateFmt.format(Date(e.startMs))
                           else dateTimeFmt.format(Date(e.startMs))

            // Color block background = calendar color with 80% alpha
            val base = e.calendarColor or 0xFF000000.toInt()
            val bg = GradientDrawable().apply {
                setColor(base)
                alpha = 200
                cornerRadius = 36f
            }
            block.background = bg

            // Countdown
            val cd = e.countdown
            tvCountdown.text = cd
            val (bgCol, txtCol) = when (cd) {
                "Now"  -> "#CC00C853" to "#FFFFFF"
                "Done" -> "#33FFFFFF" to "#88FFFFFF"
                else   -> "#22FFFFFF" to "#EEFFFFFF"
            }
            try {
                tvCountdown.background?.setTint(Color.parseColor(bgCol))
                tvCountdown.setTextColor(Color.parseColor(txtCol))
            } catch (_: Exception) {}
        }
    }
}
