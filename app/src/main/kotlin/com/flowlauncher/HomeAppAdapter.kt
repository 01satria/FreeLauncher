package com.flowlauncher

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView

/**
 * Nothing-style home adapter — text-only, no icons, bold large app names.
 * RAM-light: no bitmap fetching, no ImageView, no Drawable.
 */
class HomeAppAdapter(
    var showScreenTime: Boolean,
    private val onAppClick: (AppInfo) -> Unit,
    private val onAppLongClick: (AppInfo, View) -> Unit
) : RecyclerView.Adapter<HomeAppAdapter.VH>() {

    companion object {
        const val MODE_LIST = 0
        const val MODE_GRID = 1
    }

    init { setHasStableIds(true) }

    private val apps = mutableListOf<AppInfo>()
    private var cachedPrefs: Prefs? = null
    private var isLightTheme: Boolean = false
    private var mode = MODE_LIST
    var showLabels: Boolean = true

    fun setMode(newMode: Int) {
        if (mode == newMode) return
        mode = newMode
        notifyDataSetChanged()
    }

    fun setApps(newApps: List<AppInfo>) {
        val old = apps.toList()
        val diff = DiffUtil.calculateDiff(AppDiffCallback(old, newApps))
        apps.clear()
        apps.addAll(newApps)
        diff.dispatchUpdatesTo(this)
    }

    fun setLightTheme(isLight: Boolean) {
        if (isLightTheme == isLight) return
        isLightTheme = isLight
        notifyItemRangeChanged(0, apps.size)
    }

    override fun getItemViewType(pos: Int) = mode

    override fun getItemId(pos: Int): Long {
        val pkg = apps[pos].packageName
        var h = 0L
        for (ch in pkg) h = h * 31 + ch.code.toLong()
        return h
    }

    override fun getItemCount() = apps.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val layoutRes = if (viewType == MODE_GRID) R.layout.item_home_app_grid else R.layout.item_home_app
        val v = LayoutInflater.from(parent.context).inflate(layoutRes, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, pos: Int) = holder.bind(apps[pos])

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Common
        private val label: TextView = itemView.findViewById(R.id.tv_label)
        
        // List mode only
        private val layoutST: LinearLayout? = itemView.findViewById(R.id.layoutScreenTime)
        private val screenTime: TextView?   = itemView.findViewById(R.id.tv_screen_time)
        private val arrow: TextView?        = itemView.findViewById(R.id.tvArrow)

        // Grid mode only
        private val icon: android.widget.ImageView? = itemView.findViewById(R.id.iv_icon)

        fun bind(app: AppInfo) {
            val prefs = cachedPrefs ?: Prefs(itemView.context).also { cachedPrefs = it }
            
            label.text = app.label
            val textPrimary = if (isLightTheme) 0xFF0A0A0A.toInt() else 0xFFFFFFFF.toInt()
            label.setTextColor(textPrimary)

            if (mode == MODE_LIST) {
                label.visibility = View.VISIBLE
                label.gravity = Gravity.START
                FontHelper.applyFont(itemView.context, prefs, label)
                
                screenTime?.let { st ->
                    st.setTextColor(if (isLightTheme) 0xFF999999.toInt() else 0x44FFFFFF.toInt())
                    FontHelper.applyFont(itemView.context, prefs, st)
                }
                arrow?.let { a ->
                    a.setTextColor(if (isLightTheme) 0x33000000.toInt() else 0x22FFFFFF.toInt())
                    FontHelper.applyFont(itemView.context, prefs, a)
                }

                if (showScreenTime && app.screenTimeMinutes > 0) {
                    layoutST?.visibility = View.VISIBLE
                    screenTime?.text = ScreenTimeHelper.formatMinutes(app.screenTimeMinutes)
                } else {
                    layoutST?.visibility = View.GONE
                }
            } else {
                label.visibility = if (showLabels) View.VISIBLE else View.INVISIBLE
                label.gravity = Gravity.CENTER
                FontHelper.applyFont(itemView.context, prefs, label)
                
                icon?.let { iv ->
                    val bmp = AppRepository.getIcon(app.packageName)
                    iv.setImageBitmap(bmp)
                    if (isLightTheme) iv.alpha = 0.8f
                }
            }

            itemView.setOnClickListener { onAppClick(app) }
            itemView.setOnLongClickListener { onAppLongClick(app, it); true }
        }
    }
}
