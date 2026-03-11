package com.flowlauncher

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class TodoAdapter(
    private val onToggle: (Int) -> Unit,
    private val onDelete: (Int) -> Unit
) : RecyclerView.Adapter<TodoAdapter.VH>() {

    private val items = mutableListOf<TodoItem>()

    fun setItems(list: List<TodoItem>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    fun getItems(): List<TodoItem> = items.toList()

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_todo, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, pos: Int) = holder.bind(items[pos], pos)

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val check: ImageView = itemView.findViewById(R.id.ivCheck)
        private val text: TextView   = itemView.findViewById(R.id.tv_todo)
        private val delete: ImageButton = itemView.findViewById(R.id.btn_delete_todo)

        fun bind(todo: TodoItem, pos: Int) {
            text.text = todo.text

            if (todo.done) {
                check.setImageResource(R.drawable.ic_check)
                check.alpha = 0.5f
                text.paintFlags = text.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                text.alpha = 0.4f
            } else {
                check.setImageResource(R.drawable.ic_circle)
                check.alpha = 1f
                text.paintFlags = text.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                text.alpha = 0.85f
            }

            check.setOnClickListener { onToggle(pos) }
            itemView.setOnClickListener { onToggle(pos) }
            delete.setOnClickListener { onDelete(pos) }
        }
    }
}
