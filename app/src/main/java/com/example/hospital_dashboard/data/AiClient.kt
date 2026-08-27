package com.example.hospital_dashboard.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/** 連線測試結果。 */
data class AiTestResult(val ok: Boolean, val message: String, val latencyMs: Long? = null)

/**
 * OpenAI-Compatible Chat Completions 客戶端（/v1/chat/completions）。
 * 使用 HttpURLConnection + org.json，零第三方依賴；支援 SSE 串流。
 */
object AiClient {

    private const val CONNECT_TIMEOUT = 10_000
    private const val READ_TIMEOUT = 180_000

    /** 測試連線：送 1 token 的驗證請求，回傳狀態與延遲。 */
    suspend fun testConnection(config: AiProviderConfig): AiTestResult = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("model", config.model)
            .put("max_tokens", 1)
            .put("stream", false)
            .put("messages", JSONArray().put(
                JSONObject().put("role", "user").put("content", "ping")
            ))
        var ms = 0L
        try {
            val conn = openConn(config, body)
            val started = System.currentTimeMillis()
            val code = conn.responseCode
            ms = System.currentTimeMillis() - started
            val respText = conn.inputStream?.bufferedReader()?.use { it.readText() }
                ?: conn.errorStream?.bufferedReader()?.use { it.readText() }
            if (code in 200..299) {
                AiTestResult(true, "連線成功（HTTP $code）", ms)
            } else {
                val msg = try { JSONObject(respText ?: "").optString("error", respText ?: "") } catch (e: Exception) { respText ?: "" }
                AiTestResult(false, "HTTP $code：${msg.take(160)}", ms)
            }
        } catch (e: Exception) {
            AiTestResult(false, "連線失敗：${e.message?.take(120) ?: e.javaClass.simpleName}", ms)
        }
    }

    /** 非串流單次生成（備用）。 */
    suspend fun chat(config: AiProviderConfig, messages: List<Pair<String, String>>): String =
        withContext(Dispatchers.IO) {
            val arr = JSONArray()
            messages.forEach { (role, content) ->
                arr.put(JSONObject().put("role", role).put("content", content))
            }
            val body = JSONObject()
                .put("model", config.model)
                .put("stream", false)
                .put("messages", arr)
            val conn = openConn(config, body)
            val code = conn.responseCode
            val respText = conn.inputStream?.bufferedReader()?.use { it.readText() }
                ?: conn.errorStream?.bufferedReader()?.use { it.readText() }
            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code：${respText?.take(160)}")
            }
            try {
                JSONObject(respText ?: "")
                    .optJSONArray("choices")?.optJSONObject(0)
                    ?.optJSONObject("message")?.optString("content") ?: ""
            } catch (e: Exception) {
                throw IllegalStateException("回應格式錯誤：${e.message}")
            }
        }

    /** SSE 串流生成：逐段 emit 文字增量。 */
    fun streamChat(config: AiProviderConfig, messages: List<Pair<String, String>>): Flow<String> = flow {
        val arr = JSONArray()
        messages.forEach { (role, content) ->
            arr.put(JSONObject().put("role", role).put("content", content))
        }
        val body = JSONObject()
            .put("model", config.model)
            .put("stream", true)
            .put("temperature", 0.3)
            .put("max_tokens", 2048)
            .put("messages", arr)

        val conn = openConn(config, body)
        val code = conn.responseCode
        if (code !in 200..299) {
            val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            throw IllegalStateException("HTTP $code：${err.take(160)}")
        }
        val reader = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8))
        try {
            var line: String?
            while (true) {
                line = reader.readLine() ?: break
                if (!line.startsWith("data:")) continue
                val payload = line.removePrefix("data:").trim()
                if (payload == "[DONE]") break
                if (payload.isEmpty()) continue
                try {
                    val delta = JSONObject(payload)
                        .optJSONArray("choices")?.optJSONObject(0)
                        ?.optJSONObject("delta")?.optString("content")
                    if (!delta.isNullOrEmpty()) emit(delta)
                } catch (e: Exception) { /* 忽略無法解析的區塊 */ }
            }
        } finally {
            reader.close()
            conn.disconnect()
        }
    }.flowOn(Dispatchers.IO)

    private fun openConn(config: AiProviderConfig, body: JSONObject): HttpURLConnection {
        val url = URL(config.chatCompletionsUrl())
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT
            readTimeout = READ_TIMEOUT
            setRequestProperty("Content-Type", "application/json")
            if (config.apiKey.isNotBlank()) {
                setRequestProperty("Authorization", "Bearer ${config.apiKey}")
            }
            doOutput = true
        }
        conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        return conn
    }
}
