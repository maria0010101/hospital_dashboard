package com.example.hospital_dashboard.data

import android.content.Context

/** 設定持久化（本機 SharedPreferences，不上傳）。 */
object AiConfigStore {
    private const val PREFS = "ai_provider_config"
    private const val KEY_CONFIG = "config"

    fun load(context: Context): AiProviderConfig? =
        AiProviderConfig.fromJson(
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_CONFIG, null)
        )

    fun save(context: Context, config: AiProviderConfig) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_CONFIG, config.toJson()).apply()
    }
}
