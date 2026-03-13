package com.flowlauncher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView

/**
 * Nothing-style drawer: alphabetical headers + app rows with icon + name.
 * Icons from LruCache — O(1), no I/O.
 */
class DrawerAppAdapter(
    private val onAppClick: (AppInfo) -> Unit,
    private val onAppLongClick: (AppInfo, View) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_APP    = 1
    }

    sealed class ListItem {
        data class Header(val letter: String) : ListItem()
        data class App(val info: AppInfo)     : ListItem()
    }

    private val items = mutableListOf<ListItem>()
    private var cachedPrefs: Prefs? = null

    private fun buildItems(apps: List<AppInfo>): List<ListItem> {
        if (apps.isEmpty()) return emptyList()
        val result  = mutableListOf<ListItem>()
        val grouped = apps.sortedBy { it.label.lowercase() }
            .groupBy { it.label.firstOrNull()?.uppercaseChar()?.toString() ?: "#" }
            .entries.sortedBy { (k, _) -> if (k == "#") "\u0000" else k }
        grouped.forEach { (letter, group) ->
            result += ListItem.Header(letter)
            group.forEach { result += ListItem.App(it) }
        }
        return result
    }

    fun setApps(apps: List<AppInfo>) {
        val newItems = buildItems(apps)
        val diff = DiffUtil.calculateDiff(ItemDiffCallback(items, newItems))
        items.clear(); items.addAll(newItems)
        diff.dispatchUpdatesTo(this)
    }

    fun setSearchApps(apps: List<AppInfo>) {
        val newItems = apps.map { ListItem.App(it) }
        val diff = DiffUtil.calculateDiff(ItemDiffCallback(items, newItems))
        items.clear(); items.addAll(newItems)
        diff.dispatchUpdatesTo(this)
    }

    override fun getItemCount() = items.size
    override fun getItemViewType(pos: Int) = when (items[pos]) {
        is ListItem.Header -> TYPE_HEADER
        is ListItem.App    -> TYPE_APP
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER)
            HeaderVH(inf.inflate(R.layout.item_drawer_header, parent, false))
        else
            AppVH(inf.inflate(R.layout.item_drawer_app, parent, false))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, pos: Int) {
        when (val item = items[pos]) {
            is ListItem.Header -> (holder as HeaderVH).bind(item)
            is ListItem.App    -> (holder as AppVH).bind(item.info)
        }
    }

    inner class HeaderVH(v: View) : RecyclerView.ViewHolder(v) {
        private val tv: TextView = v.findViewById(R.id.tvHeader)
        fun bind(h: ListItem.Header) {
            tv.text = h.letter
            val prefs = cachedPrefs ?: Prefs(itemView.context).also { cachedPrefs = it }
            FontHelper.applyFont(itemView.context, prefs, tv)
        }
    }

    inner class AppVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val icon: ImageView  = itemView.findViewById(R.id.iv_icon)
        private val label: TextView  = itemView.findViewById(R.id.tv_label)
        private val dot: View        = itemView.findViewById(R.id.dotScreenTime)
        private val stTime: TextView = itemView.findViewById(R.id.tv_screen_time)

        fun bind(app: AppInfo) {
            label.text = app.label

            val prefs = cachedPrefs ?: Prefs(itemView.context).also { cachedPrefs = it }
            FontHelper.applyFont(itemView.context, prefs, label)

            icon.visibility = View.VISIBLE
            val bmp = AppRepository.getIcon(app.packageName)
            if (bmp != null) icon.setImageBitmap(bmp) else icon.setImageDrawable(null)

            if (app.screenTimeMinutes > 0) {
                dot.visibility   = View.VISIBLE
                stTime.visibility = View.VISIBLE
                stTime.text = ScreenTimeHelper.formatMinutes(app.screenTimeMinutes)
            } else {
                dot.visibility   = View.GONE
                stTime.visibility = View.GONE
            }

            itemView.setOnClickListener { onAppClick(app) }
            itemView.setOnLongClickListener { onAppLongClick(app, it); true }
        }
    }

    private class ItemDiffCallback(
        private val old: List<ListItem>,
        private val new: List<ListItem>
    ) : DiffUtil.Callback() {
        override fun getOldListSize() = old.size
        override fun getNewListSize() = new.size
        override fun areItemsTheSame(o: Int, n: Int): Boolean {
            val a = old[o]; val b = new[n]
            return when {
                a is ListItem.Header && b is ListItem.Header -> a.letter == b.letter
                a is ListItem.App    && b is ListItem.App    -> a.info.packageName == b.info.packageName
                else -> false
            }
        }
        override fun areContentsTheSame(o: Int, n: Int): Boolean {
            val a = old[o]; val b = new[n]
            return when {
                a is ListItem.Header && b is ListItem.Header -> a.letter == b.letter
                a is ListItem.App    && b is ListItem.App    ->
                    a.info.label == b.info.label &&
                    a.info.screenTimeMinutes == b.info.screenTimeMinutes &&
                    a.info.isFavorite == b.info.isFavorite
                else -> false
            }
        }
    }
}
