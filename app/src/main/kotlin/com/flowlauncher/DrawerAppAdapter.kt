package com.flowlauncher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * Drawer adapter with alphabetical section headers.
 * Item types: TYPE_HEADER = 0, TYPE_APP = 1
 */
class DrawerAppAdapter(
    private val onAppClick: (AppInfo) -> Unit,
    private val onAppLongClick: (AppInfo, View) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_APP    = 1
    }

    // Sealed list item: either a section header or an app
    sealed class ListItem {
        data class Header(val letter: String) : ListItem()
        data class App(val info: AppInfo)     : ListItem()
    }

    private val items = mutableListOf<ListItem>()

    fun setApps(apps: List<AppInfo>) {
        items.clear()
        if (apps.isEmpty()) {
            notifyDataSetChanged()
            return
        }
        // Group by first letter, sorted
        val grouped = apps.sortedBy { it.label.lowercase() }
            .groupBy { it.label.firstOrNull()?.uppercaseChar()?.toString() ?: "#" }
            .entries.sortedBy { (k, _) ->
                if (k == "#") "\u0000" else k   // # sorts first
            }

        grouped.forEach { (letter, appsInGroup) ->
            items += ListItem.Header(letter)
            appsInGroup.forEach { items += ListItem.App(it) }
        }
        notifyDataSetChanged()
    }

    /** For search results — flat list without headers */
    fun setSearchApps(apps: List<AppInfo>) {
        items.clear()
        apps.forEach { items += ListItem.App(it) }
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size
    override fun getItemViewType(pos: Int) = when (items[pos]) {
        is ListItem.Header -> TYPE_HEADER
        is ListItem.App    -> TYPE_APP
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderVH(inflater.inflate(R.layout.item_drawer_header, parent, false))
        } else {
            AppVH(inflater.inflate(R.layout.item_drawer_app, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, pos: Int) {
        when (val item = items[pos]) {
            is ListItem.Header -> (holder as HeaderVH).bind(item)
            is ListItem.App    -> (holder as AppVH).bind(item.info)
        }
    }

    inner class HeaderVH(v: View) : RecyclerView.ViewHolder(v) {
        private val tv: TextView = v.findViewById(R.id.tvHeader)
        fun bind(h: ListItem.Header) { tv.text = h.letter }
    }

    inner class AppVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val icon: ImageView = itemView.findViewById(R.id.iv_icon)
        private val label: TextView = itemView.findViewById(R.id.tv_label)
        private val dot: View       = itemView.findViewById(R.id.dotScreenTime)
        private val stTime: TextView = itemView.findViewById(R.id.tv_screen_time)

        fun bind(app: AppInfo) {
            label.text = app.label

            if (app.icon != null) {
                icon.visibility = View.VISIBLE
                icon.setImageDrawable(app.icon)
            } else {
                icon.visibility = View.INVISIBLE
            }

            if (app.screenTimeMinutes > 0) {
                dot.visibility = View.VISIBLE
                stTime.visibility = View.VISIBLE
                stTime.text = ScreenTimeHelper.formatMinutes(app.screenTimeMinutes)
            } else {
                dot.visibility = View.GONE
                stTime.visibility = View.GONE
            }

            itemView.setOnClickListener { onAppClick(app) }
            itemView.setOnLongClickListener { onAppLongClick(app, it); true }
        }
    }
}
