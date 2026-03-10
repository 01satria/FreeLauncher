package com.flowlauncher

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_event, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, pos: Int) = holder.bind(items[pos])

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        private val colorBar   : View     = v.findViewById(R.id.vEventColor)
        private val tvTitle    : TextView = v.findViewById(R.id.tvEventTitle)
        private val tvDate     : TextView = v.findViewById(R.id.tvEventDate)
        private val tvCountdown: TextView = v.findViewById(R.id.tvCountdown)

        private val dateTimeFmt = SimpleDateFormat("EEE, MMM d · HH:mm", Locale.getDefault())
        private val dateFmt     = SimpleDateFormat("EEE, MMM d", Locale.getDefault())

        fun bind(e: EventItem) {
            tvTitle.text = e.title
            tvDate.text  = if (e.allDay) dateFmt.format(Date(e.startMs))
                           else dateTimeFmt.format(Date(e.startMs))

            // countdown is a computed property — always fresh
            val cd = e.countdown
            tvCountdown.text = cd

            // Countdown badge styling
            val (bgHex, textHex) = when (cd) {
                "Now"  -> "#3300C853" to "#00C853"   // green
                "Done" -> "#22FFFFFF" to "#66FFFFFF"  // dim
                else   -> "#22FFFFFF" to "#EEFFFFFF"  // white
            }
            tvCountdown.setTextColor(Color.parseColor(textHex))
            try {
                tvCountdown.background?.setTint(Color.parseColor(bgHex))
            } catch (_: Exception) {}

            // Calendar color bar (ensure full opacity)
            colorBar.setBackgroundColor(e.calendarColor or 0xFF000000.toInt())
        }
    }
}
