package com.lumen.launcher.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Build

/** Android 15 supports real content in its system picker. Earlier Android
 * versions use XML fallbacks; Lumen's own picker always renders live content. */
object WidgetPreviews {
    fun publish(context: Context, data: WidgetSnapshot) {
        if (Build.VERSION.SDK_INT < 35 || data.locked) return
        val manager = AppWidgetManager.getInstance(context)
        val prefs = context.getSharedPreferences("widget_previews", Context.MODE_PRIVATE)
        val quote = context.getSharedPreferences("lumen_widget_quotes", Context.MODE_PRIVATE).getInt("last_quote", 0)
        val now = System.currentTimeMillis()
        for (kind in listOf(WidgetKind.Quote, WidgetKind.Photos, WidgetKind.Scribble)) {
            val content = when (kind) {
                WidgetKind.Quote -> data.quoteCreation ?: quote.toString()
                WidgetKind.Photos -> data.photos.firstOrNull().orEmpty()
                else -> data.scribble.orEmpty()
            } + ":${data.stored.theme}:${data.stored.glassDepth}"
            if (prefs.getString("content_${kind.name}", null) == content || now < prefs.getLong("retry_${kind.name}", 0)) continue
            val accepted = runCatching { manager.setWidgetPreview(LumenWidgetProvider.component(context, kind),
                AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN, WidgetRenderer.views(context, 0, kind, data)) }.getOrDefault(false)
            val edit = prefs.edit()
            if (accepted) edit.putString("content_${kind.name}", content)
            else edit.putLong("retry_${kind.name}", now + 30 * 60_000L) // respect the system rate limit
            edit.apply()
        }
    }
}
