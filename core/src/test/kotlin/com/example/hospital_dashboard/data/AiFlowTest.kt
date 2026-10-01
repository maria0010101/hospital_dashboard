package com.example.hospital_dashboard.data

import org.junit.Assert.*
import org.junit.Test

class AiFlowTest {
    @Test fun providerUsesOfficialEndpointRegardlessOfSavedCustomUrl() {
        val old = "http://127.0.0.1:11434/v1"
        val expected = mapOf(
            AiProviderType.Gemini to "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions",
            AiProviderType.OpenAI to "https://api.openai.com/v1/chat/completions",
            AiProviderType.Claude to "https://api.anthropic.com/v1/chat/completions",
            AiProviderType.DeepSeek to "https://api.deepseek.com/chat/completions"
        )
        expected.forEach { (provider, endpoint) ->
            assertEquals(endpoint, AiProviderConfig(providerType = provider, baseUrl = old).chatCompletionsUrl())
        }
        assertEquals("$old/chat/completions", AiProviderConfig(baseUrl = old).chatCompletionsUrl())
    }

    @Test fun pastedExternalReportCanBeRestoredFromPersistedMapping() {
        val anonymizer = Anonymizer()
        val code = anonymizer.codeOf("測試院區", Anonymizer.Kind.Branch)
        val restored = Anonymizer.fromJson(anonymizer.toJson())
        assertEquals("測試院區：門診 120 人次", restored.rehydrate("$code：門診 120 人次"))
    }
}
