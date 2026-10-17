package com.lumen.launcher.data

import android.content.Context

/** Provider models are user-configurable; no bundled credential is required. */
object AiModels {
    private const val DefaultGemini = "gemini-2.5-flash-lite"
    private const val DefaultOpenRouter = "openai/gpt-6-luna"

    private val LegacyGemini = setOf("gemini-2.5-flash", "gemini-2.5-flash-lite")
    private val LegacyOpenRouter = setOf(
        "google/gemini-2.5-flash",
        "google/gemini-2.5-flash-lite"
    )

    @Volatile var gemini: String = DefaultGemini
        private set
    @Volatile var openRouter: String = DefaultOpenRouter
        private set

    fun load(context: Context) {
        val p = context.getSharedPreferences("ai_models", Context.MODE_PRIVATE)
        val storedGemini = p.getString("gemini", null)
        val storedRouter = p.getString("openRouter", null)
        gemini = when {
            storedGemini.isNullOrBlank() || storedGemini in LegacyGemini -> DefaultGemini
            else -> storedGemini
        }
        openRouter = when {
            storedRouter.isNullOrBlank() || storedRouter in LegacyOpenRouter -> DefaultOpenRouter
            else -> storedRouter
        }
        if (storedGemini != gemini || storedRouter != openRouter) {
            p.edit().putString("gemini", gemini).putString("openRouter", openRouter).apply()
        }
    }

    fun save(context: Context, geminiModel: String, routerModel: String) {
        require(Regex("[A-Za-z0-9._:/-]{1,160}").matches(geminiModel))
        require(Regex("[A-Za-z0-9._:/-]{1,160}").matches(routerModel))
        gemini = geminiModel
        openRouter = routerModel
        context.getSharedPreferences("ai_models", Context.MODE_PRIVATE).edit()
            .putString("gemini", gemini).putString("openRouter", openRouter).apply()
    }
}
