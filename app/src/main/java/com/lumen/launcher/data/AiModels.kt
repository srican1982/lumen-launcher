package com.lumen.launcher.data

import android.content.Context

/** Provider models are user-configurable; no bundled credential is required. */
object AiModels {
    @Volatile var gemini: String = "gemini-2.5-flash"
        private set
    @Volatile var openRouter: String = "google/gemini-2.5-flash"
        private set
    fun load(context: Context) {
        val p = context.getSharedPreferences("ai_models", Context.MODE_PRIVATE)
        gemini = p.getString("gemini", gemini).orEmpty().ifBlank { gemini }
        openRouter = p.getString("openRouter", openRouter).orEmpty().ifBlank { openRouter }
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
