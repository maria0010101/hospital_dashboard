package com.example.hospital_dashboard.data

import org.json.JSONObject

/** AI 引擎類型（全部走 OpenAI-Compatible Chat Completions 協定）。 */
enum class AiProviderType(val label: String) {
    Ollama("本地 Ollama"),
    Vllm("自建 vLLM"),
    Gemini("Google Gemini"),
    OpenAI("OpenAI"),
    Custom("Custom Gateway");

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
        val b = baseUrl.trim().trimEnd('/')
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
