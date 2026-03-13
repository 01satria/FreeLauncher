package com.flowlauncher

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object GeminiHelper {
    private const val API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key="
    private const val SYSTEM_INSTRUCTION = "Kamu adalah asisten tutor matematika SNBT profesional. Jawablah dengan format yang rapi dan mudah dipahami oleh siswa SMA. Selalu gunakan Bahasa Indonesia. Gunakan pendekatan problem-solving yang sistematis."

    suspend fun generateResponse(apiKey: String, history: List<ChatMessage>, newPrompt: String): String? {
        return withContext(Dispatchers.IO) {
            var conn: HttpURLConnection? = null
            try {
                val url = URL(API_URL + apiKey)
                conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true

                // Build request JSON
                val requestJson = JSONObject()
                
                // Add System Instruction
                requestJson.put("system_instruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", SYSTEM_INSTRUCTION) })
                    })
                })

                // Build Contents array (History + Request)
                val contentsArr = JSONArray()
                
                // Add recent history for context (limit to last 10 to save tokens/RAM)
                val recentHistory = history.takeLast(10)
                for (msg in recentHistory) {
                    val role = if (msg.isUser) "user" else "model"
                    contentsArr.put(JSONObject().apply {
                        put("role", role)
                        put("parts", JSONArray().apply { put(JSONObject().apply { put("text", msg.text) }) })
                    })
                }
                
                // Add current prompt
                contentsArr.put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply { put(JSONObject().apply { put("text", newPrompt) }) })
                })
                
                requestJson.put("contents", contentsArr)

                // Add safety settings/config (optional, lightweight)
                requestJson.put("generationConfig", JSONObject().apply {
                    put("temperature", 0.7)
                })

                OutputStreamWriter(conn.outputStream).use { it.write(requestJson.toString()) }

                val responseCode = conn.responseCode
                if (responseCode == 200) {
                    val responseStr = conn.inputStream.bufferedReader().use { it.readText() }
                    val responseObj = JSONObject(responseStr)
                    val candidates = responseObj.optJSONArray("candidates")
                    if (candidates != null && candidates.length() > 0) {
                        val firstCandidate = candidates.getJSONObject(0)
                        val content = firstCandidate.optJSONObject("content")
                        val parts = content?.optJSONArray("parts")
                        if (parts != null && parts.length() > 0) {
                            return@withContext parts.getJSONObject(0).optString("text")
                        }
                    }
                } else {
                    val errorStr = conn.errorStream?.bufferedReader()?.use { it.readText() }
                    android.util.Log.e("GeminiHelper", "API Error ($responseCode): $errorStr")
                }
                null
            } catch (e: Exception) {
                android.util.Log.e("GeminiHelper", "Exception", e)
                null
            } finally {
                conn?.disconnect()
            }
        }
    }
}
