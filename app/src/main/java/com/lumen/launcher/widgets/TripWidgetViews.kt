package com.lumen.launcher.widgets

import android.content.Context
import android.view.View
import android.widget.RemoteViews
import com.lumen.launcher.R
import com.lumen.launcher.data.TravelAttachment
import com.lumen.launcher.data.TravelCategory
import com.lumen.launcher.travel.model.Trip
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Three Apple-style travel widgets with distinct button destinations. */
object TripWidgetViews {
    fun views(context: Context, id: Int, kind: WidgetKind, data: WidgetSnapshot): RemoteViews {
        val layout = when (kind) {
            WidgetKind.Trip -> R.layout.widget_trip_apple
            WidgetKind.TripComplete -> R.layout.widget_trip_complete_apple
            WidgetKind.TravelDocs -> R.layout.widget_trip_docs_apple
            else -> R.layout.widget_trip_apple
        }
        val view = RemoteViews(context.packageName, layout)
        val now = System.currentTimeMillis()
        val dateFmt = SimpleDateFormat("MMM d", Locale.getDefault())
        when (kind) {
            WidgetKind.Trip -> bindTrip(context, id, view, data, data.trip ?: data.lastTrip, now, dateFmt)
            WidgetKind.TripComplete -> bindComplete(context, id, view, data, data.lastTrip ?: data.trip, now, dateFmt)
            WidgetKind.TravelDocs -> bindDocs(context, id, view, data)
            else -> Unit
        }
        return view
    }

    private fun bindTrip(
        context: Context, id: Int, view: RemoteViews, data: WidgetSnapshot, shown: Trip?,
        now: Long, dateFmt: SimpleDateFormat
    ) {
        val openAlbum = WidgetActions.pending(context, id, "TripAlbums", shown?.id?.toString().orEmpty())
        val cover = if (data.locked) null else shown?.coverUri ?: shown?.previewUris?.firstOrNull()
        setCover(context, view, cover)
        view.setTextViewText(R.id.trip_widget_label, "Trip")
        view.setTextViewText(R.id.trip_widget_title, displayTitle(shown, data))
        view.setTextViewText(
            R.id.trip_widget_dates,
            when {
                data.locked -> "Unlock to see your trip"
                shown != null -> formatRange(shown, now, dateFmt)
                else -> "Start collecting memories"
            }
        )
        val photos = shown?.photoCount ?: 0
        val places = data.stored.travelStops.size
        val docs = data.stored.travelAttachments.size
        view.setTextViewText(
            R.id.trip_widget_stats,
            if (shown == null) "Photos, places and docs live here"
            else "$photos photos · $places places · $docs docs"
        )
        val on = data.trip != null
        view.setTextViewText(R.id.trip_widget_toggle, if (on) "ON" else "OFF")
        view.setOnClickPendingIntent(R.id.trip_widget_root, openAlbum)
        view.setOnClickPendingIntent(R.id.trip_widget_primary, openAlbum)
        view.setOnClickPendingIntent(
            R.id.trip_widget_toggle,
            WidgetActions.tripToggle(context, id, data.trip?.id, WidgetKind.Trip)
        )
        view.setTextViewText(R.id.trip_widget_primary, if (shown == null) "Open Travel" else "Open Album")
    }

    private fun bindComplete(
        context: Context, id: Int, view: RemoteViews, data: WidgetSnapshot, shown: Trip?,
        now: Long, dateFmt: SimpleDateFormat
    ) {
        val tripId = shown?.id?.toString().orEmpty()
        val openAlbum = WidgetActions.pending(context, id, "TripAlbums", tripId)
        val share = WidgetActions.pending(context, id, "TripShare", tripId)
        val cover = if (data.locked) null else shown?.coverUri ?: shown?.previewUris?.firstOrNull()
        setCover(context, view, cover)
        view.setTextViewText(R.id.trip_widget_title, displayTitle(shown, data))
        view.setTextViewText(
            R.id.trip_widget_dates,
            when {
                data.locked -> "Unlock to see your trip"
                shown != null -> formatRange(shown, now, dateFmt)
                else -> "Finish a trip to see memories here"
            }
        )
        val photos = shown?.photoCount ?: 0
        val places = data.stored.travelStops.size
        val docs = data.stored.travelAttachments.size
        view.setTextViewText(
            R.id.trip_widget_caption,
            when {
                data.locked -> "Private trip"
                shown == null -> "No completed trip yet"
                photos > 0 -> "What a journey!"
                else -> "Trip wrapped up"
            }
        )
        view.setTextViewText(
            R.id.trip_widget_stats,
            if (shown == null) "Photos · places · docs"
            else "$photos photos · $places places · $docs docs"
        )
        bindPhotoStrip(context, view, data, shown)
        view.setOnClickPendingIntent(R.id.trip_widget_root, openAlbum)
        view.setOnClickPendingIntent(R.id.trip_widget_primary, openAlbum)
        view.setOnClickPendingIntent(R.id.trip_widget_share, share)
        view.setTextViewText(R.id.trip_widget_primary, if (shown == null) "Open Travel  ›" else "View Album  ›")
    }

    private fun bindPhotoStrip(context: Context, view: RemoteViews, data: WidgetSnapshot, shown: Trip?) {
        val slots = listOf(
            R.id.trip_widget_photo1, R.id.trip_widget_photo2,
            R.id.trip_widget_photo3, R.id.trip_widget_photo4
        )
        val uris = if (data.locked) emptyList() else shown?.previewUris.orEmpty().filter { it.isNotBlank() }
        slots.forEachIndexed { index, imageId ->
            val uri = uris.getOrNull(index)
            val thumb = WidgetRenderer.thumbnail(context, uri, 180)
            when {
                thumb != null -> view.setImageViewBitmap(imageId, thumb)
                uri != null -> view.setImageViewResource(imageId, R.drawable.travel_evening_valley)
                else -> view.setImageViewResource(imageId, R.drawable.widget_trip_photo_frame)
            }
        }
        val extra = ((shown?.photoCount ?: 0) - 4).coerceAtLeast(0)
        if (extra > 0) {
            view.setViewVisibility(R.id.trip_widget_more, View.VISIBLE)
            view.setTextViewText(R.id.trip_widget_more, "+$extra")
        } else {
            view.setViewVisibility(R.id.trip_widget_more, View.GONE)
        }
    }

    private fun bindDocs(context: Context, id: Int, view: RemoteViews, data: WidgetSnapshot) {
        val openDocs = WidgetActions.pending(context, id, "TravelDocs")
        val attachments = data.stored.travelAttachments
        val slots = listOf(
            TravelCategory.Flights to ("Flight" to "Boarding pass"),
            TravelCategory.Hotels to ("Hotel" to "Stay"),
            TravelCategory.Itinerary to ("Itinerary" to "Schedule")
        )
        val rows = listOf(
            DocRow(
                R.id.trip_widget_doc_row1, R.id.trip_widget_doc_icon1, R.id.trip_widget_doc_clip1,
                R.id.trip_widget_doc_title1, R.id.trip_widget_doc_sub1
            ),
            DocRow(
                R.id.trip_widget_doc_row2, R.id.trip_widget_doc_icon2, R.id.trip_widget_doc_clip2,
                R.id.trip_widget_doc_title2, R.id.trip_widget_doc_sub2
            ),
            DocRow(
                R.id.trip_widget_doc_row3, R.id.trip_widget_doc_icon3, R.id.trip_widget_doc_clip3,
                R.id.trip_widget_doc_title3, R.id.trip_widget_doc_sub3
            )
        )
        rows.forEachIndexed { index, row ->
            val (category, fallback) = slots[index]
            val doc = attachments.firstOrNull { it.travelCategory == category && it.uri.isNotBlank() }
            val attached = doc != null
            view.setTextViewText(row.icon, iconFor(category))
            view.setInt(
                row.icon, "setBackgroundResource",
                if (attached) R.drawable.widget_trip_doc_icon_blue else R.drawable.widget_trip_doc_icon_muted
            )
            view.setViewVisibility(row.clip, if (attached) View.VISIBLE else View.GONE)
            if (attached) {
                view.setTextViewText(row.title, friendlyDocTitle(doc!!, fallback.first))
                view.setTextViewText(row.sub, "Attached · ${fallback.second}")
            } else {
                view.setTextViewText(row.title, fallback.first)
                view.setTextViewText(row.sub, "Add in Travel Docs")
            }
            view.setOnClickPendingIntent(row.root, WidgetActions.pending(context, id, "TravelDocs", category.name))
        }
        view.setOnClickPendingIntent(R.id.trip_widget_root, openDocs)
        view.setOnClickPendingIntent(R.id.trip_widget_chevron, openDocs)
    }

    private data class DocRow(
        val root: Int, val icon: Int, val clip: Int, val title: Int, val sub: Int
    )

    private fun setCover(context: Context, view: RemoteViews, coverUri: String?) {
        runCatching {
            val cover = WidgetRenderer.thumbnail(context, coverUri, 512)
            if (cover != null) view.setImageViewBitmap(R.id.trip_widget_cover, cover)
            else view.setImageViewResource(R.id.trip_widget_cover, R.drawable.travel_evening_valley)
        }
    }

    private fun displayTitle(shown: Trip?, data: WidgetSnapshot): String {
        if (data.locked) return "Unlock Lumen"
        val destination = data.stored.travelDestination.trim()
        val raw = shown?.displayTitle.orEmpty()
        if (raw.isNotBlank() && !raw.equals("Current Trip", ignoreCase = true)) return raw
        if (destination.isNotBlank()) return destination
        return shown?.primaryCity ?: "Your trip"
    }

    private fun formatRange(trip: Trip, now: Long, fmt: SimpleDateFormat): String {
        val end = trip.endTime ?: now
        val days = (TimeUnit.MILLISECONDS.toDays((end - trip.startTime).coerceAtLeast(0)) + 1).toInt()
        val dayLabel = if (days == 1) "1 day" else "$days days"
        return "${fmt.format(Date(trip.startTime))} – ${fmt.format(Date(end))} · $dayLabel"
    }

    private fun friendlyDocTitle(doc: TravelAttachment, fallback: String): String {
        val title = doc.title.trim()
        if (title.isBlank() || looksLikeFilename(title)) return fallback
        return title
    }

    private fun looksLikeFilename(value: String): Boolean {
        val lower = value.lowercase(Locale.US)
        return lower.contains('.') && (
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") ||
                lower.endsWith(".webp") || lower.endsWith(".heic") || lower.endsWith(".pdf") ||
                lower.contains("gettyimages") || lower.contains("img_") || lower.contains("dsc")
            )
    }

    private fun iconFor(category: TravelCategory): String = when (category) {
        TravelCategory.Flights -> "✈"
        TravelCategory.Hotels -> "⌂"
        TravelCategory.Itinerary -> "☰"
        TravelCategory.Tickets -> "🎟"
        TravelCategory.Apps -> "▦"
        TravelCategory.Other -> "◫"
    }
}
