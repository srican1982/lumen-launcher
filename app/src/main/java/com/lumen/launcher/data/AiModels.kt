package com.lumen.launcher.data

import android.content.Context

/** Provider models are user-configurable; no bundled credential is required. */
object AiModels {
    @Volatile var gemini: String = "gemini-2.5-flash-lite"
        private set
    @Volatile var openRouter: String = "google/gemini-2.5-flash-lite"
        private set
    fun load(context: Context) {
        val p = context.getSharedPreferences("ai_models", Context.MODE_PRIVATE)
        val defaultGemini = "gemini-2.5-flash-lite"
        val defaultRouter = "google/gemini-2.5-flash-lite"
        // Upgrade prior flash defaults to flash-lite without wiping custom user picks.
        val storedGemini = p.getString("gemini", null)
        val storedRouter = p.getString("openRouter", null)
        gemini = when {
            storedGemini.isNullOrBlank() || storedGemini == "gemini-2.5-flash" -> defaultGemini
            else -> storedGemini
        }
        openRouter = when {
            storedRouter.isNullOrBlank() || storedRouter == "google/gemini-2.5-flash" -> defaultRouter
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
