package com.lumen.launcher.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

open class LumenWidgetProvider(private val kind: WidgetKind) : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in setOf(AppWidgetManager.ACTION_APPWIDGET_UPDATE, AppWidgetManager.ACTION_APPWIDGET_OPTIONS_CHANGED, REFRESH_QUOTE)) {
            val manager = AppWidgetManager.getInstance(context)
            val owned = manager.getAppWidgetIds(ComponentName(context, javaClass))
            val requested = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)
                ?: intArrayOf(intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID))
            val ids = requested.filter { it in owned }.ifEmpty {
                if (intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE && requested.all { it == AppWidgetManager.INVALID_APPWIDGET_ID }) owned.toList() else emptyList()
            }
            if (intent.action == REFRESH_QUOTE && kind != WidgetKind.Quote) return
            if (ids.isEmpty()) return
            val pending = goAsync()
            scope.launch {
                try {
                    withTimeout(8_000) {
                        val data = WidgetRepository.load(context.applicationContext)
                        ids.forEach { id ->
                            if (intent.action == REFRESH_QUOTE) {
                                val prefs = context.getSharedPreferences("lumen_widget_quotes", Context.MODE_PRIVATE)
                                val next = Math.floorMod(prefs.getInt("quote_$id", prefs.getInt("last_quote", 0)) + 1, WidgetQuotes.lines.size)
                                prefs.edit().putInt("quote_$id", next).putInt("last_quote", next).apply()
                            }
                            WidgetRenderer.update(context, manager, id, kind, data)
                        }
                        WidgetPreviews.publish(context, data)
                    }
                } catch (_: Exception) {
                    ids.forEach { WidgetRenderer.unavailable(context, manager, it, kind) }
                } finally { pending.finish() }
            }
        } else super.onReceive(context, intent)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val prefs = context.getSharedPreferences("lumen_widget_quotes", Context.MODE_PRIVATE).edit()
        appWidgetIds.forEach { prefs.remove("quote_$it") }
        appWidgetIds.forEach { WidgetContacts.remove(context, it) }
        WidgetWorldCities.remove(context, appWidgetIds)
        prefs.apply()
        WidgetHostStore.remove(context, appWidgetIds.toSet())
    }

    companion object {
        const val REFRESH_QUOTE = "com.lumen.launcher.widgets.REFRESH_QUOTE"
        internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        fun component(context: Context, kind: WidgetKind) = ComponentName(context, when (kind) {
            WidgetKind.ClockWeather -> ClockWeatherWidget::class.java
            WidgetKind.WorldClock -> WorldClockWidget::class.java
            WidgetKind.ClockFeatured -> ClockFeaturedWidget::class.java
            WidgetKind.CalendarDay -> CalendarDayWidget::class.java
            WidgetKind.MomentClock -> MomentClockWidget::class.java

            WidgetKind.MediaContinue -> MediaContinueWidget::class.java
            WidgetKind.MediaPlayer -> MediaPlayerWidget::class.java
            WidgetKind.MediaNowPlaying -> MediaNowPlayingWidget::class.java
            WidgetKind.MediaShortcuts -> MediaShortcutsWidget::class.java

            WidgetKind.Trip -> TripWidget::class.java
            WidgetKind.TripComplete -> TripCompleteWidget::class.java
            WidgetKind.TravelDocs -> TravelDocsWidget::class.java

            WidgetKind.Scribble -> ScribbleWidget::class.java
            WidgetKind.Quote -> QuoteWidget::class.java
            WidgetKind.Photos -> PhotosWidget::class.java
            WidgetKind.Social -> SocialWidget::class.java
            WidgetKind.Tasks -> TasksWidget::class.java
            WidgetKind.TasksOverview -> TasksOverviewWidget::class.java
            WidgetKind.TasksFilters -> TasksFiltersWidget::class.java
            WidgetKind.TasksNext -> TasksNextWidget::class.java
            WidgetKind.TasksManager -> TasksManagerWidget::class.java

            WidgetKind.Focus -> FocusWidget::class.java
            WidgetKind.Contacts -> ContactsWidget::class.java
            WidgetKind.ContactsSingle -> ContactsSingleWidget::class.java
            WidgetKind.ContactsRow -> ContactsRowWidget::class.java

            WidgetKind.Hush -> HushWidget::class.java
            WidgetKind.Battery -> BatteryWidget::class.java
        })
        fun quoteRefresh(context: Context, id: Int) = PendingIntent.getBroadcast(context, id,
            Intent(context, QuoteWidget::class.java).setAction(REFRESH_QUOTE).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        suspend fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val widgets = WidgetKind.entries.associateWith { manager.getAppWidgetIds(component(context, it)) }
            val data = WidgetRepository.load(context)
            widgets.forEach { (kind, ids) -> ids.forEach { WidgetRenderer.update(context, manager, it, kind, data) } }
            WidgetPreviews.publish(context, data)
        }
    }
}

class ClockWeatherWidget : LumenWidgetProvider(WidgetKind.ClockWeather)
class WorldClockWidget : LumenWidgetProvider(WidgetKind.WorldClock)
class ClockFeaturedWidget : LumenWidgetProvider(WidgetKind.ClockFeatured)
class CalendarDayWidget : LumenWidgetProvider(WidgetKind.CalendarDay)
class MomentClockWidget : LumenWidgetProvider(WidgetKind.MomentClock)

class MediaContinueWidget : LumenWidgetProvider(WidgetKind.MediaContinue)
class MediaPlayerWidget : LumenWidgetProvider(WidgetKind.MediaPlayer)
class MediaNowPlayingWidget : LumenWidgetProvider(WidgetKind.MediaNowPlaying)
class MediaShortcutsWidget : LumenWidgetProvider(WidgetKind.MediaShortcuts)

class TripWidget : LumenWidgetProvider(WidgetKind.Trip)
class TripCompleteWidget : LumenWidgetProvider(WidgetKind.TripComplete)
class TravelDocsWidget : LumenWidgetProvider(WidgetKind.TravelDocs)

class ScribbleWidget : LumenWidgetProvider(WidgetKind.Scribble)
class QuoteWidget : LumenWidgetProvider(WidgetKind.Quote)
class PhotosWidget : LumenWidgetProvider(WidgetKind.Photos)
class SocialWidget : LumenWidgetProvider(WidgetKind.Social)
class TasksWidget : LumenWidgetProvider(WidgetKind.Tasks)
class FocusWidget : LumenWidgetProvider(WidgetKind.Focus)
class ContactsWidget : LumenWidgetProvider(WidgetKind.Contacts)
class HushWidget : LumenWidgetProvider(WidgetKind.Hush)
class BatteryWidget : LumenWidgetProvider(WidgetKind.Battery)

class TasksOverviewWidget : LumenWidgetProvider(WidgetKind.TasksOverview)
class TasksFiltersWidget : LumenWidgetProvider(WidgetKind.TasksFilters)
class TasksNextWidget : LumenWidgetProvider(WidgetKind.TasksNext)
class TasksManagerWidget : LumenWidgetProvider(WidgetKind.TasksManager)

class ContactsSingleWidget : LumenWidgetProvider(WidgetKind.ContactsSingle)
class ContactsRowWidget : LumenWidgetProvider(WidgetKind.ContactsRow)
