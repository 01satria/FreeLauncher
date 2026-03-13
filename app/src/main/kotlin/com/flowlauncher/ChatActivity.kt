package com.flowlauncher

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

class ChatActivity : AppCompatActivity() {

    private lateinit var rvChat: RecyclerView
    private lateinit var etMessage: EditText
    private lateinit var btnSend: ImageView
    private lateinit var btnBack: ImageView
    private lateinit var btnClear: ImageView

    private lateinit var adapter: ChatAdapter
    private val messages = mutableListOf<ChatMessage>()
    private var apiKey: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        val prefs = getSharedPreferences("flow_prefs", MODE_PRIVATE)
        apiKey = prefs.getString("gemini_api_key", "") ?: ""

        if (apiKey.isEmpty()) {
            Toast.makeText(this, "API Key missing, returning to login", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        rvChat = findViewById(R.id.rvChat)
        etMessage = findViewById(R.id.etMessage)
        btnSend = findViewById(R.id.btnSend)
        btnBack = findViewById(R.id.btnBack)
        btnClear = findViewById(R.id.btnClear)

        adapter = ChatAdapter(messages)
        val layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        rvChat.layoutManager = layoutManager
        rvChat.adapter = adapter

        // Load history
        val history = ChatStorage.getMessages(this)
        messages.addAll(history)
        adapter.notifyDataSetChanged()
        scrollToBottom()

        btnSend.setOnClickListener { sendMessage() }
        btnBack.setOnClickListener { finish() }
        btnClear.setOnClickListener {
            ChatStorage.clearHistory(this)
            messages.clear()
            adapter.notifyDataSetChanged()
        }
    }

    private fun sendMessage() {
        val text = etMessage.text.toString().trim()
        if (text.isEmpty()) return

        etMessage.setText("")
        val userMsg = ChatMessage(text = text, isUser = true)
        adapter.addMessage(userMsg)
        scrollToBottom()
        ChatStorage.saveMessages(this, messages)

        // Add thinking indicator message
        val thinkingMsg = ChatMessage(text = "Sedang berpikir...", isUser = false)
        adapter.addMessage(thinkingMsg)
        scrollToBottom()
        
        hideKeyboard()

        lifecycleScope.launch {
            // Get last N messages for context (excluding the "Sedang berpikir..." msg)
            val contextHistory = messages.dropLast(1)
            
            val response = GeminiHelper.generateResponse(apiKey, contextHistory, text)
            
            // Remove thinking message
            messages.removeLast()
            adapter.notifyItemRemoved(messages.size)

            if (response != null) {
                val geminiMsg = ChatMessage(text = response.trim(), isUser = false)
                adapter.addMessage(geminiMsg)
                ChatStorage.saveMessages(this@ChatActivity, messages)
            } else {
                Toast.makeText(this@ChatActivity, "Gagal mendapatkan respon dari Gemini.", Toast.LENGTH_SHORT).show()
            }
            scrollToBottom()
        }
    }

    private fun scrollToBottom() {
        if (messages.isNotEmpty()) {
            rvChat.smoothScrollToPosition(messages.size - 1)
        }
    }
    
    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(etMessage.windowToken, 0)
    }
}
