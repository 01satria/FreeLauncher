package com.flowlauncher

import android.graphics.Color
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView

class TodoAdapter(
    private val onToggle: (Int) -> Unit,
    private val onDelete: (Int) -> Unit
) : RecyclerView.Adapter<TodoAdapter.VH>() {

    private val items = mutableListOf<TodoItem>()
    private var theme: FeedTheme? = null

    fun setItems(list: List<TodoItem>) {
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = items.size
            override fun getNewListSize() = list.size
            override fun areItemsTheSame(o: Int, n: Int) = items[o].text == list[n].text
            override fun areContentsTheSame(o: Int, n: Int) =
                items[o].done == list[n].done
        })
        items.clear(); items.addAll(list)
        diff.dispatchUpdatesTo(this)
    }

    fun applyTheme(t: FeedTheme) {
        theme = t
        notifyItemRangeChanged(0, items.size, PAYLOAD_THEME)
    }

    fun getItems(): List<TodoItem> = items.toList()

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_todo, parent, false))

    override fun onBindViewHolder(holder: VH, pos: Int) = holder.bind(items[pos], pos)

    override fun onBindViewHolder(holder: VH, pos: Int, payloads: List<Any>) {
        if (payloads.isEmpty()) { holder.bind(items[pos], pos); return }
        payloads.forEach { p ->
            if (p == PAYLOAD_THEME) holder.applyTheme(items[pos], theme)
        }
    }

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        private val check : ImageView   = v.findViewById(R.id.ivCheck)
        private val text  : TextView    = v.findViewById(R.id.tv_todo)
        private val delete: ImageButton = v.findViewById(R.id.btn_delete_todo)

        fun bind(todo: TodoItem, pos: Int) {
            text.text = todo.text
            applyDoneState(todo.done)
            applyTheme(todo, theme)

            check.setOnClickListener { onToggle(pos) }
            itemView.setOnClickListener { onToggle(pos) }
            delete.setOnClickListener { onDelete(pos) }
        }

        fun applyTheme(todo: TodoItem, t: FeedTheme?) {
            t ?: return
            val base = if (todo.done) t.subtle else t.onSurface
            text.setTextColor(if (todo.done) t.subtle else t.onSurface)
            delete.setColorFilter(t.faint)
        }

        private fun applyDoneState(done: Boolean) {
            if (done) {
                check.setImageResource(R.drawable.ic_check)
                check.alpha = 0.4f
                text.paintFlags = text.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                text.alpha = 0.4f
            } else {
                check.setImageResource(R.drawable.ic_circle)
                check.alpha = 0.7f
                text.paintFlags = text.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                text.alpha = 1f
            }
        }
    }

    companion object {
        private const val PAYLOAD_THEME = "theme"
    }
}
