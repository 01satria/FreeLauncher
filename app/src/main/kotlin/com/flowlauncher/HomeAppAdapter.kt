package com.flowlauncher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView

class HomeAppAdapter(
    private val showIcons: Boolean,
    private val showScreenTime: Boolean,
    private val alignment: Int,  // Gravity constant
    private val fontSize: Float,
    private val onAppClick: (AppInfo) -> Unit,
    private val onAppLongClick: (AppInfo, View) -> Unit
) : RecyclerView.Adapter<HomeAppAdapter.VH>() {

    init { setHasStableIds(true) }

    private val apps = mutableListOf<AppInfo>()

    fun setApps(newApps: List<AppInfo>) {
        val diff = DiffUtil.calculateDiff(AppDiffCallback(apps, newApps))
        apps.clear()
        apps.addAll(newApps)
        diff.dispatchUpdatesTo(this)
    }

    override fun getItemId(pos: Int) = apps[pos].packageName.hashCode().toLong()
    override fun getItemCount() = apps.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_home_app, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(apps[position])
    }

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val icon: ImageView = itemView.findViewById(R.id.iv_icon)
        private val label: TextView = itemView.findViewById(R.id.tv_label)
        private val screenTime: TextView = itemView.findViewById(R.id.tv_screen_time)

        fun bind(app: AppInfo) {
            label.text = app.label
            label.textSize = fontSize
            label.gravity = alignment

            if (showIcons && app.icon != null) {
                icon.visibility = View.VISIBLE
                icon.setImageDrawable(app.icon)
            } else {
                icon.visibility = View.GONE
            }

            if (showScreenTime && app.screenTimeMinutes > 0) {
                screenTime.visibility = View.VISIBLE
                screenTime.text = ScreenTimeHelper.formatMinutes(app.screenTimeMinutes)
                screenTime.gravity = alignment
            } else {
                screenTime.visibility = View.GONE
            }

            itemView.setOnClickListener { onAppClick(app) }
            itemView.setOnLongClickListener {
                onAppLongClick(app, it)
                true
            }
        }
    }
}
