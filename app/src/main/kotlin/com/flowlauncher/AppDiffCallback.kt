package com.flowlauncher

import androidx.recyclerview.widget.DiffUtil

class AppDiffCallback(
    private val old: List<AppInfo>,
    private val new: List<AppInfo>
) : DiffUtil.Callback() {
    override fun getOldListSize() = old.size
    override fun getNewListSize() = new.size
    override fun areItemsTheSame(o: Int, n: Int) = old[o].packageName == new[n].packageName
    override fun areContentsTheSame(o: Int, n: Int) =
        old[o].label             == new[n].label &&
        old[o].screenTimeMinutes == new[n].screenTimeMinutes &&
        old[o].isFavorite        == new[n].isFavorite
        // Note: icon state not compared here because icons are managed by
        // AppRepository.iconCache — not stored in AppInfo.
}
