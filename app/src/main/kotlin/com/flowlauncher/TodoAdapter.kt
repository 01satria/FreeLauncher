package com.flowlauncher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class TodoAdapter(
    private val onDelete: (Int) -> Unit
) : RecyclerView.Adapter<TodoAdapter.VH>() {

    private val items = mutableListOf<String>()

    fun setItems(list: List<String>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    fun getItems(): List<String> = items.toList()

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_todo, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, pos: Int) = holder.bind(items[pos], pos)

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val text: TextView = itemView.findViewById(R.id.tv_todo)
        private val delete: ImageButton = itemView.findViewById(R.id.btn_delete_todo)

        fun bind(todo: String, pos: Int) {
            text.text = todo
            delete.setOnClickListener { onDelete(pos) }
        }
    }
}
