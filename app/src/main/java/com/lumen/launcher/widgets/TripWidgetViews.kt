package com.lumen.launcher.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import com.lumen.launcher.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Reference-inspired glass travel cards, rendered entirely from the user's saved trip data. */
object TripWidgetViews {
    fun views(context: Context, id: Int, kind: WidgetKind, data: WidgetSnapshot): RemoteViews {
        val shown = when (kind) { WidgetKind.TripCompleted -> data.lastTrip; WidgetKind.TripUpcoming -> null; else -> data.trip ?: data.lastTrip }
        val tripId = shown?.id?.toString().orEmpty()
        val options = if (id > 0) AppWidgetManager.getInstance(context).getAppWidgetOptions(id) else android.os.Bundle()
        val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, if (kind.wide) 300 else 150)
        val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, kind.heightDp)
        val short = height < 180
        val small = short || kind in setOf(WidgetKind.TripCompact, WidgetKind.TripSmall) || width < 240
        val view = RemoteViews(context.packageName, if (kind == WidgetKind.TripCollage) R.layout.widget_trip_collage else R.layout.widget_trip_card)
        val openAlbum = WidgetActions.pending(context, id, "TripAlbums", tripId)
        view.setOnClickPendingIntent(R.id.trip_widget_root, openAlbum)
        val title = if (data.locked) "Unlock Lumen" else shown?.displayTitle ?: if (kind == WidgetKind.TripUpcoming)
            data.stored.travelDestination.ifBlank { "Plan your next trip" } else "Your next adventure"
        val preview = if (data.locked) emptyList() else shown?.previewUris.orEmpty().filter(String::isNotBlank).take(4)
        view.setTextViewText(R.id.trip_widget_title, title)
        if (kind == WidgetKind.TripCollage) {
            val photos = listOf(R.id.trip_widget_photo1, R.id.trip_widget_photo2, R.id.trip_widget_photo3, R.id.trip_widget_photo4)
            photos.forEachIndexed { index, image ->
                val bmp = WidgetRenderer.thumbnail(context, preview.getOrNull(index), 256)
                if (bmp != null) view.setImageViewBitmap(image, bmp) else view.setImageViewResource(image, R.drawable.travel_evening_valley)
                view.setOnClickPendingIntent(image, openAlbum)
            }
            view.setTextViewText(R.id.trip_widget_stats, if (data.locked) "Unlock to see memories" else if (shown == null) "Your memories start here" else "${shown.photoCount} photos")
            return view
        }
        view.setTextViewTextSize(R.id.trip_widget_title, android.util.TypedValue.COMPLEX_UNIT_SP, if (small) 17f else 24f)
        val label = when (kind) {
            WidgetKind.TripUpcoming -> "NEXT TRIP"
            WidgetKind.TripCompleted -> "TRIP COMPLETE"
            WidgetKind.TripActions -> "QUICK ACTIONS"
            else -> if (data.trip != null) "TRIP MODE" else "TRIP ALBUM"
        }
        view.setTextViewText(R.id.trip_widget_label, label)
        view.setTextViewText(R.id.trip_widget_toggle, if (data.trip != null) "ON" else "OFF")
        view.setContentDescription(R.id.trip_widget_toggle, if (data.trip != null) "End current trip" else "Start Trip Mode")
        view.setOnClickPendingIntent(R.id.trip_widget_toggle, WidgetActions.tripToggle(context, id, data.trip?.id, kind))
        val cover = if (!data.locked && kind != WidgetKind.TripUpcoming) WidgetRenderer.thumbnail(context, shown?.coverUri, 512) else null
        if (cover != null) view.setImageViewBitmap(R.id.trip_widget_cover, cover)
        else view.setImageViewResource(R.id.trip_widget_cover, R.drawable.travel_evening_valley)
        val format = SimpleDateFormat("MMM d", Locale.getDefault())
        val now = System.currentTimeMillis()
        val planned = data.stored.travelStops.mapNotNull { it.at }.filter { it >= now }.sorted()
        val dates = if (shown != null) format.format(Date(shown.startTime)) + " – " + (shown.endTime?.let { format.format(Date(it)) } ?: "Now")
            else if (planned.isNotEmpty()) format.format(Date(planned.first())) + " – " + format.format(Date(planned.last()))
            else "Add dates and destinations"
        view.setTextViewText(R.id.trip_widget_dates, if (data.locked) "Unlock to see trip details" else dates)
        val day = shown?.let { ((minOf(it.endTime ?: now, now) - it.startTime).coerceAtLeast(0) / 86400000 + 1).toInt() }
        val next = data.stored.travelStops.firstOrNull { it.markedNext } ?: data.stored.travelStops.filter { it.at == null || it.at >= now }.minByOrNull { it.at ?: Long.MAX_VALUE }
        view.setTextViewText(R.id.trip_widget_route, if (kind == WidgetKind.TripCompact && day != null) "Day $day"
            else shown?.citiesLabel ?: next?.place ?: "Making room for memories")
        view.setTextViewText(R.id.trip_widget_stats, if (data.locked) "" else
            "${shown?.photoCount ?: 0} photos  ·  ${data.stored.travelStops.size} saved places  ·  ${data.stored.travelAttachments.size} docs")
        val photoIds = listOf(R.id.trip_widget_photo1, R.id.trip_widget_photo2, R.id.trip_widget_photo3, R.id.trip_widget_photo4)
        photoIds.forEachIndexed { index, image ->
            val bitmap = WidgetRenderer.thumbnail(context, preview.getOrNull(index), 192)
            view.setViewVisibility(image, if (bitmap != null) View.VISIBLE else View.GONE)
            if (bitmap != null) view.setImageViewBitmap(image, framedPhoto(bitmap))
            view.setOnClickPendingIntent(image, openAlbum)
        }
        view.setTextViewText(R.id.trip_widget_primary, if (kind == WidgetKind.TripUpcoming) "Get ready  ›" else "Open Trip Album  ›")
        view.setOnClickPendingIntent(R.id.trip_widget_primary, if (kind == WidgetKind.TripUpcoming) WidgetActions.pending(context, id, "TripPlan") else openAlbum)
        view.setTextViewText(R.id.trip_widget_next, if (kind == WidgetKind.TripCompleted) "Share  ↗" else next?.let { "Next: ${it.place}" } ?: "Plan your journey  ›")
        view.setOnClickPendingIntent(R.id.trip_widget_next, if (kind == WidgetKind.TripCompleted) WidgetActions.pending(context, id, "TripShare", tripId) else WidgetActions.pending(context, id, "TripPlan"))
        view.setViewVisibility(R.id.trip_widget_previews, if (!small && preview.isNotEmpty()) View.VISIBLE else View.GONE)
        view.setViewVisibility(R.id.trip_widget_stats, if (short || kind in setOf(WidgetKind.TripCompact, WidgetKind.TripSmall, WidgetKind.TripActions)) View.GONE else View.VISIBLE)
        view.setViewVisibility(R.id.trip_widget_footer, if (short || kind in setOf(WidgetKind.TripCompact, WidgetKind.TripSmall, WidgetKind.TripActions)) View.GONE else View.VISIBLE)
        if (small && kind != WidgetKind.TripCompleted) view.setViewVisibility(R.id.trip_widget_next, View.GONE)
        if (small && kind == WidgetKind.TripCompleted) view.setTextViewText(R.id.trip_widget_primary, "Album  ›")
        if (kind in setOf(WidgetKind.TripUpcoming, WidgetKind.TripCompleted)) {
            view.setTextViewText(R.id.trip_widget_toggle, "›")
            view.setOnClickPendingIntent(R.id.trip_widget_toggle, if (kind == WidgetKind.TripUpcoming) WidgetActions.pending(context, id, "TripPlan") else openAlbum)
            view.setContentDescription(R.id.trip_widget_toggle, "Open ${kind.title}")
        }
        if (kind == WidgetKind.TripActions) {
            listOf(R.id.trip_widget_title, R.id.trip_widget_dates, R.id.trip_widget_route).forEach { view.setViewVisibility(it, View.GONE) }
            view.setViewVisibility(R.id.trip_widget_actions, View.VISIBLE)
            val actions = listOf(Triple(R.id.trip_widget_photo, "Photo", "TripCamera"), Triple(R.id.trip_widget_place, "Place", "TripPlan"),
                Triple(R.id.trip_widget_note, "Note", "TripNote"), Triple(R.id.trip_widget_docs, "Docs", "TravelDocs"))
            val icons = listOf(R.drawable.widget_trip_gold_camera, R.drawable.widget_trip_gold_place,
                R.drawable.widget_trip_gold_note, R.drawable.widget_trip_gold_docs)
            actions.forEachIndexed { index, (button, text, destination) ->
                view.setTextViewText(button, text)
                view.setTextColor(button, 0xFFE4CB91.toInt())
                view.setTextViewCompoundDrawables(button, 0, icons[index], 0, 0)
                view.setOnClickPendingIntent(button, WidgetActions.pending(context, id, destination))
            }
        }
        if (data.locked) {
            listOf(R.id.trip_widget_route, R.id.trip_widget_stats, R.id.trip_widget_previews, R.id.trip_widget_actions).forEach { view.setViewVisibility(it, View.GONE) }
        }
        return view
    }

    private fun framedPhoto(source: android.graphics.Bitmap): android.graphics.Bitmap {
        val width = 160; val height = 190
        val output = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(output)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        paint.color = 0xFFE5E9EF.toInt()
        canvas.drawRoundRect(android.graphics.RectF(0f, 0f, width.toFloat(), height.toFloat()), 12f, 12f, paint)
        val rect = android.graphics.RectF(7f, 7f, 153f, 174f)
        val shader = android.graphics.BitmapShader(source, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP)
        val scale = maxOf(rect.width() / source.width, rect.height() / source.height)
        shader.setLocalMatrix(android.graphics.Matrix().apply {
            setScale(scale, scale)
            postTranslate(rect.left + (rect.width() - source.width * scale) / 2f, rect.top + (rect.height() - source.height * scale) / 2f)
        })
        paint.shader = shader
        canvas.drawRoundRect(rect, 7f, 7f, paint)
        return output
    }
}
