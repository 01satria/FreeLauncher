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
) : RecyclerView.Adapter<DrawerAppAdapter.AppVH>() {

    private val items = mutableListOf<AppInfo>()
    private var cachedPrefs: Prefs? = null
    private var isLightTheme: Boolean = false

    fun setApps(apps: List<AppInfo>) {
        val newItems = apps.sortedBy { it.label.lowercase() }
        val diff = DiffUtil.calculateDiff(ItemDiffCallback(items, newItems))
        items.clear(); items.addAll(newItems)
        diff.dispatchUpdatesTo(this)
    }

    fun setSearchApps(apps: List<AppInfo>) {
        val diff = DiffUtil.calculateDiff(ItemDiffCallback(items, apps))
        items.clear(); items.addAll(apps)
        diff.dispatchUpdatesTo(this)
    }

    fun setTheme(isLight: Boolean) {
        if (isLightTheme == isLight) return
        isLightTheme = isLight
        notifyItemRangeChanged(0, items.size)
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppVH {
        val inf = LayoutInflater.from(parent.context)
        return AppVH(inf.inflate(R.layout.item_drawer_app, parent, false))
    }

    override fun onBindViewHolder(holder: AppVH, pos: Int) {
        holder.bind(items[pos])
    }


    inner class AppVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val icon: ImageView  = itemView.findViewById(R.id.iv_icon)
        private val label: TextView  = itemView.findViewById(R.id.tv_label)
        private val dot: View        = itemView.findViewById(R.id.dotScreenTime)
        private val stTime: TextView = itemView.findViewById(R.id.tv_screen_time)

        fun bind(app: AppInfo) {
            label.text = app.label

            val prefs = cachedPrefs ?: Prefs(itemView.context).also { cachedPrefs = it }
            FontHelper.applyFont(itemView.context, prefs, label, stTime)

            // App label color: primary text per theme
            label.setTextColor(
                if (isLightTheme) 0xFF0A0A0A.toInt() else 0xFFFFFFFF.toInt()
            )

            icon.visibility = View.VISIBLE
            val bmp = AppRepository.getIcon(app.packageName)
            if (bmp != null) icon.setImageBitmap(bmp) else icon.setImageDrawable(null)

            if (app.screenTimeMinutes > 0) {
                dot.visibility    = View.VISIBLE
                stTime.visibility = View.VISIBLE
                stTime.text = ScreenTimeHelper.formatMinutes(app.screenTimeMinutes)
                stTime.setTextColor(
                    if (isLightTheme) 0xFF999999.toInt() else 0x44FFFFFF.toInt()
                )
            } else {
                dot.visibility    = View.GONE
                stTime.visibility = View.GONE
            }

            // Dot color per theme
            dot.background = itemView.context.getDrawable(R.drawable.bg_dot)?.apply {
                setTint(if (isLightTheme) 0xFF999999.toInt() else 0x55FFFFFF.toInt())
            }

            itemView.setOnClickListener { onAppClick(app) }
            itemView.setOnLongClickListener { onAppLongClick(app, it); true }
        }
    }

    private class ItemDiffCallback(
        private val old: List<AppInfo>,
        private val new: List<AppInfo>
    ) : DiffUtil.Callback() {
        override fun getOldListSize() = old.size
        override fun getNewListSize() = new.size
        override fun areItemsTheSame(o: Int, n: Int) = old[o].packageName == new[n].packageName
        override fun areContentsTheSame(o: Int, n: Int) = 
            old[o].label == new[n].label &&
            old[o].screenTimeMinutes == new[n].screenTimeMinutes &&
            old[o].isFavorite == new[n].isFavorite
    }
}
