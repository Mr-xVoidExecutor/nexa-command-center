package com.nexa.app

import org.json.JSONArray
import org.json.JSONObject

/** Provider-agnostic chat client: Claude (Anthropic API) or any OpenAI-compatible API. */
object Ai {
    class Preset(val id: String, val label: String, val type: String, val url: String, val model: String)

    val presets = listOf(
        Preset("claude", "Claude (Anthropic)", "claude", "https://api.anthropic.com/v1/messages", "claude-sonnet-5-5"),
        Preset("openai", "OpenAI (ChatGPT)", "openai", "https://api.openai.com/v1/chat/completions", "gpt-4o-mini"),
        Preset("gemini", "Google Gemini", "openai", "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions", "gemini-2.0-flash"),
        Preset("groq", "Groq (free tier)", "openai", "https://api.groq.com/openai/v1/chat/completions", "llama-3.3-70b-versatile"),
        Preset("openrouter", "OpenRouter (any model)", "openai", "https://openrouter.ai/api/v1/chat/completions", "openai/gpt-4o-mini"),
        Preset("deepseek", "DeepSeek", "openai", "https://api.deepseek.com/chat/completions", "deepseek-chat"),
        Preset("custom", "Custom (OpenAI-compatible)", "openai", "", "")
    )

    fun preset(id: String): Preset = presets.firstOrNull { it.id == id } ?: presets[0]

    fun key(): String = Store.get("aiKey").ifBlank { Store.get("claudeKey") }

    /** Returns (httpCode, text). Code 200 = success. */
    suspend fun chat(system: String, msgs: List<Pair<String, String>>, maxTokens: Int): Pair<Int, String> {
        val key = key()
        if (key.isBlank()) return -2 to ""
        val p = preset(Store.get("aiPreset").ifBlank { "claude" })
        val type = Store.get("aiType").ifBlank { p.type }
        val url = Store.get("aiUrl").ifBlank { p.url }
        val model = Store.get("aiModel").ifBlank { Store.get("claudeModel").ifBlank { p.model } }
        if (url.isBlank() || model.isBlank()) return -3 to ""

        val arr = JSONArray()
        val body = JSONObject().put("model", model).put("max_tokens", maxTokens)
        val headers: Map<String, String>
        if (type == "claude") {
            msgs.forEach { arr.put(JSONObject().put("role", it.first).put("content", it.second)) }
            body.put("system", system).put("messages", arr)
            headers = mapOf("x-api-key" to key, "anthropic-version" to "2023-06-01")
        } else {
            arr.put(JSONObject().put("role", "system").put("content", system))
            msgs.forEach { arr.put(JSONObject().put("role", it.first).put("content", it.second)) }
            body.put("messages", arr)
            headers = mapOf("Authorization" to "Bearer $key")
        }
        val (c, b) = Net.call(url, "POST", body.toString(), headers)
        if (c != 200) return c to b.take(200)
        return try {
            val t = if (type == "claude")
                JSONObject(b).getJSONArray("content").getJSONObject(0).getString("text")
            else
                JSONObject(b).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
            200 to t
        } catch (e: Exception) { -4 to "" }
    }
}
