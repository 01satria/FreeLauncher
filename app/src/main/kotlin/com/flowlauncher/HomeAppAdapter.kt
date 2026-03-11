package com.flowlauncher

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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

    override fun onBindViewHolder(holder: VH, pos: Int) = holder.bind(apps[pos])

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val icon: android.widget.ImageView = itemView.findViewById(R.id.iv_icon)
        private val label: TextView = itemView.findViewById(R.id.tv_label)
        private val layoutST: LinearLayout = itemView.findViewById(R.id.layoutScreenTime)
        private val screenTime: TextView = itemView.findViewById(R.id.tv_screen_time)

        fun bind(app: AppInfo) {
            label.text = app.label
            label.gravity = Gravity.START

            icon.setImageDrawable(app.icon)

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
