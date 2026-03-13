package com.flowlauncher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Lightweight JSON storage for Chat History to avoid Room/SQLite overhead. */
object ChatStorage {
    private const val FILE_NAME = "chat_history.json"
    private const val MAX_MESSAGES = 50

    fun getMessages(context: Context): List<ChatMessage> {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return emptyList()

        return try {
            val jsonStr = file.readText()
            val arr = JSONArray(jsonStr)
            val list = mutableListOf<ChatMessage>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    ChatMessage(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        text = obj.getString("text"),
                        isUser = obj.getBoolean("isUser"),
                        timestamp = obj.getLong("timestamp")
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveMessages(context: Context, messages: List<ChatMessage>) {
        try {
            // Keep only the last MAX_MESSAGES
            val startIdx = if (messages.size > MAX_MESSAGES) messages.size - MAX_MESSAGES else 0
            val recentMessages = messages.subList(startIdx, messages.size)

            val arr = JSONArray()
            for (msg in recentMessages) {
                val obj = JSONObject().apply {
                    put("id", msg.id)
                    put("text", msg.text)
                    put("isUser", msg.isUser)
                    put("timestamp", msg.timestamp)
                }
                arr.put(obj)
            }
            val file = File(context.filesDir, FILE_NAME)
            file.writeText(arr.toString())
        } catch (_: Exception) {}
    }

    fun clearHistory(context: Context) {
        try {
            File(context.filesDir, FILE_NAME).delete()
        } catch (_: Exception) {}
    }
}

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
