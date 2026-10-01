package com.example.hospital_dashboard.data

import org.json.JSONObject

/** 各服務的 OpenAI-compatible Chat Completions 入口；自架服務才允許自訂網址。 */
enum class AiProviderType(val label: String, val baseUrl: String?, val defaultModel: String) {
    Ollama("本地 Ollama", null, "llama3"),
    Vllm("自建 vLLM", null, ""),
    Gemini("Google Gemini", "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-3.8-flash"),
    OpenAI("OpenAI", "https://api.openai.com/v1", "gpt-4.1-mini"),
    Claude("Claude", "https://api.anthropic.com/v1", "claude-sonnet-5-5"),
    DeepSeek("DeepSeek", "https://api.deepseek.com", "deepseek-flash"),
    Custom("自訂 AI", null, "");

    val allowsCustomBaseUrl: Boolean get() = baseUrl == null

    companion object {
        fun from(name: String?): AiProviderType =
            entries.firstOrNull { it.name == name } ?: Custom
    }
}

/** AI 引擎連線設定。 */
data class AiProviderConfig(
    val providerType: AiProviderType = AiProviderType.Ollama,
    val baseUrl: String = "http://192.168.1.50:11434/v1",
    val apiKey: String = "",
    val model: String = "llama3"
) {
    /** 正規化為完整 endpoint（/chat/completions）。 */
    fun chatCompletionsUrl(): String {
        val b = (providerType.baseUrl ?: baseUrl).trim().trimEnd('/')
        return if (b.endsWith("/chat/completions")) b else "$b/chat/completions"
    }

    fun toJson(): String = JSONObject()
        .put("providerType", providerType.name)
        .put("baseUrl", baseUrl)
        .put("apiKey", apiKey)
        .put("model", model)
        .toString()

    companion object {
        fun fromJson(s: String?): AiProviderConfig? {
            if (s.isNullOrBlank()) return null
            return try {
                val j = JSONObject(s)
                AiProviderConfig(
                    providerType = AiProviderType.from(j.optString("providerType")),
                    baseUrl = j.optString("baseUrl", ""),
                    apiKey = j.optString("apiKey", ""),
                    model = j.optString("model", "")
                )
            } catch (e: Exception) { null }
        }
    }
}
