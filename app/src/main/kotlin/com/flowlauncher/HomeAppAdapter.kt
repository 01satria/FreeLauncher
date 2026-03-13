package com.flowlauncher

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView

class HomeAppAdapter(
    var showScreenTime: Boolean,
    private val onAppClick: (AppInfo) -> Unit,
    private val onAppLongClick: (AppInfo, View) -> Unit
) : RecyclerView.Adapter<HomeAppAdapter.VH>() {

    init { setHasStableIds(true) }

    private val apps = mutableListOf<AppInfo>()

    fun setApps(newApps: List<AppInfo>) {
        val oldSnapshot = apps.toList()          // snapshot sebelum mutasi
        val diff = DiffUtil.calculateDiff(AppDiffCallback(oldSnapshot, newApps))
        apps.clear()
        apps.addAll(newApps)
        diff.dispatchUpdatesTo(this)
    }

    override fun getItemId(pos: Int): Long {
        // Gunakan kombinasi hash yang lebih robust untuk menghindari collision
        val pkg = apps[pos].packageName
        var h = 0L
        for (ch in pkg) h = h * 31 + ch.code.toLong()
        return h
    }
    override fun getItemCount() = apps.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_home_app, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, pos: Int) = holder.bind(apps[pos])

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val icon: ImageView      = itemView.findViewById(R.id.iv_icon)
        private val label: TextView      = itemView.findViewById(R.id.tv_label)
        private val layoutST: LinearLayout = itemView.findViewById(R.id.layoutScreenTime)
        private val screenTime: TextView = itemView.findViewById(R.id.tv_screen_time)

        fun bind(app: AppInfo) {
            label.text    = app.label
            label.gravity = Gravity.START
            
            val prefs = Prefs(itemView.context)
            FontHelper.applyFont(itemView.context, prefs, label, screenTime)

            // Fetch from LruCache — O(1), no I/O, no Drawable allocation.
            // Reset dulu sebelum set — cegah icon lama flash saat view di-recycle
            icon.setImageBitmap(null)
            val bmp = AppRepository.getIcon(app.packageName)
            if (bmp != null) icon.setImageBitmap(bmp)

            if (showScreenTime && app.screenTimeMinutes > 0) {
                layoutST.visibility = View.VISIBLE
                screenTime.text = ScreenTimeHelper.formatMinutes(app.screenTimeMinutes)
            } else {
                layoutST.visibility = View.GONE
            }

            itemView.setOnClickListener { onAppClick(app) }
            itemView.setOnLongClickListener { onAppLongClick(app, it); true }
        }
    }
}
