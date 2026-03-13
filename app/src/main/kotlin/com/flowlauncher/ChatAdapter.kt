package com.flowlauncher

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ChatAdapter(private val messages: MutableList<ChatMessage>) :
    RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    class ChatViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val rootLayout: LinearLayout = view as LinearLayout
        val tvMessage: TextView = view.findViewById(R.id.tvMessage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_message, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val message = messages[position]
        holder.tvMessage.text = message.text

        val layoutParams = holder.tvMessage.layoutParams as LinearLayout.LayoutParams
        if (message.isUser) {
            holder.rootLayout.gravity = Gravity.END
            holder.tvMessage.setBackgroundResource(R.drawable.bg_chat_bubble)
            // A bit lighter for user
            holder.tvMessage.background.setTint(android.graphics.Color.parseColor("#444444"))
        } else {
            holder.rootLayout.gravity = Gravity.START
            holder.tvMessage.setBackgroundResource(R.drawable.bg_chat_bubble)
            // Accent color or darker for Gemini
            holder.tvMessage.background.setTint(android.graphics.Color.parseColor("#222222"))
        }
        holder.tvMessage.layoutParams = layoutParams
    }

    override fun getItemCount() = messages.size

    fun addMessage(message: ChatMessage) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    fun setMessages(newMessages: List<ChatMessage>) {
        messages.clear()
        messages.addAll(newMessages)
        notifyDataSetChanged()
    }
}
