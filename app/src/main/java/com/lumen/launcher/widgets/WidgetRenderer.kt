package com.lumen.launcher.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import androidx.core.graphics.drawable.toBitmap
import com.lumen.launcher.R
import com.lumen.launcher.data.GlassDepth
import com.lumen.launcher.data.LumenThemeMode

object WidgetRenderer {
    fun icon(kind: WidgetKind): Int = when (kind) {
        WidgetKind.Trip -> R.drawable.widget_ic_trip
        WidgetKind.Scribble -> R.drawable.widget_ic_scribble
        WidgetKind.Quote -> R.drawable.widget_ic_quote
        WidgetKind.Photos -> R.drawable.widget_ic_photos
        WidgetKind.Social -> R.drawable.widget_ic_social
        WidgetKind.Tasks -> R.drawable.widget_ic_tasks
        WidgetKind.Focus -> R.drawable.widget_ic_focus
        WidgetKind.Contacts -> R.drawable.widget_ic_contacts
        WidgetKind.Hush -> R.drawable.widget_ic_hush
        WidgetKind.TravelDocs -> R.drawable.widget_ic_traveldocs
        WidgetKind.Battery -> R.drawable.widget_ic_battery
    }
    private fun background(data: WidgetSnapshot): Int {
        val white = LumenThemeMode.parse(data.stored.theme).isWhiteGlass
        return when (GlassDepth.parse(data.stored.glassDepth)) {
            GlassDepth.Soft -> if (white) R.drawable.widget_glass_white_soft else R.drawable.widget_glass_dark_soft
            GlassDepth.Balanced -> if (white) R.drawable.widget_glass_white_balanced else R.drawable.widget_glass_dark_balanced
            GlassDepth.Solid -> if (white) R.drawable.widget_glass_white_solid else R.drawable.widget_glass_dark_solid
        }
    }
    fun update(context: Context, manager: AppWidgetManager, id: Int, kind: WidgetKind, data: WidgetSnapshot) {
        manager.updateAppWidget(id, views(context, id, kind, data))
    }
    fun unavailable(context: Context, manager: AppWidgetManager, id: Int, kind: WidgetKind) {
        manager.updateAppWidget(id, views(context, id, kind, WidgetSnapshot(locked = true)))
    }
    fun views(context: Context, id: Int, kind: WidgetKind, data: WidgetSnapshot): RemoteViews {
        if (kind in setOf(WidgetKind.Quote, WidgetKind.Photos, WidgetKind.Scribble)) return contentViews(context, id, kind, data)
        val options = if (id > 0) AppWidgetManager.getInstance(context).getAppWidgetOptions(id) else android.os.Bundle()
        val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, if (kind.wide) 280 else 140)
        val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, kind.heightDp)
        val layout = when (kind) {
            WidgetKind.Trip -> if (width < 240) R.layout.lumen_widget_trip_compact else R.layout.lumen_widget_trip
            WidgetKind.Social -> R.layout.lumen_widget_social
            WidgetKind.Contacts -> R.layout.lumen_widget_contacts
            else -> R.layout.lumen_widget
        }
        val view = RemoteViews(context.packageName, layout)
        view.setTextViewTextSize(R.id.widget_title, android.util.TypedValue.COMPLEX_UNIT_SP, if (width >= 260) 14f else 12f)
        // Hosts may reapply onto the same View tree. Explicitly clear the previous
        // state so completed rows, old photos and running timers cannot linger.
        listOf(R.id.widget_detail, R.id.widget_status, R.id.widget_timer, R.id.widget_photos,
            R.id.widget_photo_names, R.id.widget_photo1, R.id.widget_photo2, R.id.widget_photo3, R.id.widget_photo4,
            R.id.widget_row1, R.id.widget_row2, R.id.widget_button, R.id.widget_meter).forEach {
            view.setViewVisibility(it, View.GONE)
        }
        view.setChronometer(R.id.widget_timer, 0, "%s", false)
        view.setInt(R.id.widget_root, "setBackgroundResource", background(data))
        view.setImageViewResource(R.id.widget_icon, icon(kind))
        view.setTextViewText(R.id.widget_title, kind.title)
        view.setTextViewText(R.id.widget_action, when (kind) { WidgetKind.Quote -> "\u21bb"; WidgetKind.Tasks, WidgetKind.Scribble, WidgetKind.Photos -> "+"; else -> "\u22ef" })
        view.setOnClickPendingIntent(R.id.widget_root, if (kind == WidgetKind.Quote) LumenWidgetProvider.quoteRefresh(context, id) else WidgetActions.pending(context, id, kind.name))
        view.setContentDescription(R.id.widget_root, "Lumen ${kind.title}")
        view.setOnClickPendingIntent(R.id.widget_action, when (kind) {
            WidgetKind.Quote -> LumenWidgetProvider.quoteRefresh(context, id)
            WidgetKind.Tasks -> WidgetActions.pending(context, id, "AddTask")
            else -> WidgetActions.pending(context, id, kind.name)
        })
        view.setContentDescription(R.id.widget_action, when (kind) { WidgetKind.Quote -> "Refresh quote"; WidgetKind.Tasks -> "Add task"; else -> "Open ${kind.title}" })
        fun text(main: String, detail: String = "") {
            view.setTextViewText(R.id.widget_detail, main)
            view.setViewVisibility(R.id.widget_detail, if (main.isEmpty()) View.GONE else View.VISIBLE)
            view.setTextViewText(R.id.widget_status, detail)
            view.setViewVisibility(R.id.widget_status, if (detail.isEmpty()) View.GONE else View.VISIBLE)
        }
        fun photos(uris: List<String>, people: List<WidgetPerson> = emptyList()) {
            val ids = listOf(R.id.widget_photo1, R.id.widget_photo2, R.id.widget_photo3, R.id.widget_photo4)
            val count = if (people.isNotEmpty()) people.size else uris.size
            view.setViewVisibility(R.id.widget_photos, if (count == 0) View.GONE else View.VISIBLE)
            ids.forEachIndexed { index, image ->
                if (index < count) {
                    view.setViewVisibility(image, View.VISIBLE)
                    val bitmap = thumbnail(context, if (people.isNotEmpty()) people[index].photo else uris[index])
                    val person = people.getOrNull(index)
                    if (person != null) view.setImageViewBitmap(image, contactAvatar(bitmap, person.name))
                    else if (bitmap != null) view.setImageViewBitmap(image, roundedThumbnail(bitmap, if (kind == WidgetKind.Trip) 100f / 64 else 1f))
                    else view.setImageViewResource(image, icon(WidgetKind.Photos))
                    view.setContentDescription(image, person?.name ?: "Open photo")
                    val target = if (person != null) "Person" else if (kind == WidgetKind.Trip) "TripAlbums" else "Photo"
                    val item = person?.id?.toString() ?: if (kind == WidgetKind.Trip) data.trip?.id?.toString().orEmpty() else uris[index]
                    view.setOnClickPendingIntent(image, WidgetActions.pending(context, id, target, item))
                } else if (people.isNotEmpty()) view.setViewVisibility(image, View.INVISIBLE)
            }
        }
        if (data.locked && kind !in setOf(WidgetKind.Battery, WidgetKind.Quote, WidgetKind.Scribble)) {
            text("Open Lumen", "Unlock to see your latest details")
            return view
        }
        when (kind) {
            WidgetKind.Trip -> {
                val shown = data.trip ?: data.lastTrip
                text(shown?.displayTitle ?: "Your next adventure", if (shown == null) "Start collecting memories" else "${shown.photoCount} ${if (shown.photoCount == 1) "photo" else "photos"} \u00b7 ${if (data.trip != null) "Collecting" else "Saved album"}")
                if (width < 240) view.setViewVisibility(R.id.widget_status, View.GONE)
                val preview = shown?.previewUris?.firstOrNull() ?: shown?.coverUri
                if (preview != null) photos(listOf(preview)) else {
                    view.setViewVisibility(R.id.widget_photos, View.VISIBLE)
                    view.setViewVisibility(R.id.widget_photo1, View.VISIBLE)
                    val scene = BitmapFactory.decodeResource(context.resources, R.drawable.travel_evening_valley,
                        BitmapFactory.Options().apply { inSampleSize = 4 })
                    if (scene != null) view.setImageViewBitmap(R.id.widget_photo1, roundedThumbnail(scene, 100f / 64))
                    view.setContentDescription(R.id.widget_photo1, "Lumen landscape")
                }
                if (shown != null) view.setOnClickPendingIntent(R.id.widget_photo1,
                    WidgetActions.pending(context, id, "TripAlbums", shown.id.toString()))
                view.setTextViewText(R.id.widget_action, if (data.trip != null) "ON" else "OFF")
                view.setTextViewTextSize(R.id.widget_action, android.util.TypedValue.COMPLEX_UNIT_SP, 10f)
                view.setOnClickPendingIntent(R.id.widget_action, WidgetActions.tripToggle(context, id, data.trip?.id))
                view.setContentDescription(R.id.widget_action, if (data.trip != null) "End trip" else "Start trip")
            }
            WidgetKind.Scribble -> {
                text("Your next idea", "Sketch it. Make it a sticker.")
                view.setViewVisibility(R.id.widget_button, View.VISIBLE)
                view.setTextViewText(R.id.widget_button, "Start drawing")
                view.setOnClickPendingIntent(R.id.widget_button, WidgetActions.pending(context, id, "Scribble"))
            }
            WidgetKind.Quote -> text(WidgetQuotes.at(context.getSharedPreferences("lumen_widget_quotes", Context.MODE_PRIVATE).getInt("quote_$id", 0)))
            WidgetKind.Photos -> {
                text(if (data.photos.isEmpty()) "Photo studio" else "", if (data.photos.isEmpty()) "Cut out, create and share" else "")
                photos(data.photos)
                if (data.photos.isEmpty()) {
                    view.setViewVisibility(R.id.widget_button, View.VISIBLE)
                    view.setTextViewText(R.id.widget_button, "Choose photo")
                    view.setOnClickPendingIntent(R.id.widget_button, WidgetActions.pending(context, id, "Photos"))
                }
            }
            WidgetKind.Social -> {
                val minutes = data.usage?.values?.sum()?.div(60_000)
                text(if (minutes == null) "Your time, at a glance" else "${minutes / 60}h ${minutes % 60}m", if (minutes == null) "Tap to enable Usage Access" else "Your social time today")
                if (minutes == null) {
                    view.setOnClickPendingIntent(R.id.widget_root, WidgetActions.pending(context, id, "UsageAccess"))
                    view.setOnClickPendingIntent(R.id.widget_action, WidgetActions.pending(context, id, "UsageAccess"))
                }
                if (data.usage?.values?.any { it > 0 } == true) {
                    view.setViewVisibility(R.id.widget_photos, View.VISIBLE)
                    view.setViewVisibility(R.id.widget_photo1, View.VISIBLE)
                    view.setImageViewBitmap(R.id.widget_photo1, usageBars(data.usage.values.sortedDescending().take(6)))
                    view.setContentDescription(R.id.widget_photo1, "Relative time across your most used social apps")
                }
            }
            WidgetKind.Tasks -> {
                text("", if (data.tasks.isEmpty()) "All clear for today" else "${data.tasks.size} left today")
                data.tasks.take(2).forEachIndexed { index, task ->
                    val row = if (index == 0) R.id.widget_row1 else R.id.widget_row2
                    val label = if (index == 0) R.id.widget_task1 else R.id.widget_task2
                    val check = if (index == 0) R.id.widget_check1 else R.id.widget_check2
                    view.setViewVisibility(row, View.VISIBLE)
                    view.setTextViewText(label, task.text)
                    view.setOnClickPendingIntent(label, WidgetActions.pending(context, id, "Tasks"))
                    view.setOnClickPendingIntent(check, WidgetCommandReceiver.pending(context, id, kind, "CompleteTask", WidgetTaskTarget.encode(task)))
                    view.setContentDescription(check, "Mark ${task.text} as done")
                }
            }
            WidgetKind.Focus -> {
                val active = data.stored.focusUntil > System.currentTimeMillis()
                val remaining = (data.focusRemainingMs + 59_999) / 60_000
                text(if (active || data.focusPaused) "${remaining} min left" else "A little quiet",
                    data.focusError.takeIf { it.isNotBlank() } ?: if (data.focusPaused) "Paused \u00b7 Your time is saved" else if (active) "Quiet mode is on" else "Make room for what matters")
                view.setViewVisibility(R.id.widget_button, View.VISIBLE)
                view.setInt(R.id.widget_status, "setMaxLines", 1)
                if (active) {
                    view.setViewVisibility(R.id.widget_detail, View.GONE)
                    view.setViewVisibility(R.id.widget_timer, View.VISIBLE)
                    view.setChronometerCountDown(R.id.widget_timer, true)
                    view.setChronometer(R.id.widget_timer, android.os.SystemClock.elapsedRealtime() + data.focusRemainingMs, "%s", true)
                }
                view.setTextViewText(R.id.widget_button, if (data.focusPaused) "Resume" else if (active) "Pause" else "Start 30 min")
                view.setOnClickPendingIntent(R.id.widget_button, if (active || data.focusPaused)
                    WidgetCommandReceiver.pending(context, id, kind, if (data.focusPaused) "ResumeFocus" else "PauseFocus", data.focusStartedAt.toString())
                    else WidgetActions.focusStart(context, id))
            }
            WidgetKind.Contacts -> {
                val people = WidgetContacts.read(context, id)
                text("", if (people.all { it == null }) "Add your people" else "")
                view.setTextViewText(R.id.widget_action, "+")
                view.setContentDescription(R.id.widget_action, "Edit widget contacts")
                view.setOnClickPendingIntent(R.id.widget_root, WidgetActions.contacts(context, id))
                view.setOnClickPendingIntent(R.id.widget_action, WidgetActions.contacts(context, id))
                view.setViewVisibility(R.id.widget_photos, View.VISIBLE)
                view.setViewVisibility(R.id.widget_photo_names, View.VISIBLE)
                val images = listOf(R.id.widget_photo1, R.id.widget_photo2, R.id.widget_photo3, R.id.widget_photo4)
                val labels = listOf(R.id.widget_person1, R.id.widget_person2, R.id.widget_person3, R.id.widget_person4)
                people.forEachIndexed { index, person ->
                    view.setViewVisibility(images[index], View.VISIBLE)
                    view.setImageViewBitmap(images[index], contactAvatar(thumbnail(context, person?.photo), person?.name ?: "+"))
                    view.setTextViewText(labels[index], person?.name?.substringBefore(' ') ?: "Add")
                    val action = if (person == null) WidgetActions.contacts(context, id, index)
                        else WidgetActions.dial(context, id, index, person.number)
                    view.setOnClickPendingIntent(images[index], action)
                    view.setOnClickPendingIntent(labels[index], action)
                    view.setContentDescription(images[index], person?.let { "Call ${it.name}" } ?: "Add contact ${index + 1}")
                }
                if (android.os.Build.VERSION.SDK_INT >= 31) {
                    val avatarHeight = (height - 100).coerceIn(40, 100).toFloat()
                    view.setViewLayoutHeight(R.id.widget_photos, avatarHeight + 4, android.util.TypedValue.COMPLEX_UNIT_DIP)
                    images.forEach { view.setViewLayoutHeight(it, avatarHeight, android.util.TypedValue.COMPLEX_UNIT_DIP) }
                }
            }
            WidgetKind.Hush -> {
                text(data.notifications?.let { if (it == 0) "All quiet" else "$it waiting" } ?: "A quieter overview", if (data.notifications == null) "Tap to allow notification access" else if (data.notifications == 0) "Nothing needs your attention" else "Tap an app to review")
                if (data.notificationPackages.isNotEmpty()) {
                    view.setViewVisibility(R.id.widget_photos, View.VISIBLE)
                    val images = listOf(R.id.widget_photo1, R.id.widget_photo2, R.id.widget_photo3)
                    data.notificationPackages.forEachIndexed { index, pkg ->
                        runCatching { context.packageManager.getApplicationIcon(pkg).toBitmap(64, 64) }.getOrNull()?.let { bitmap ->
                            view.setViewVisibility(images[index], View.VISIBLE)
                            view.setImageViewBitmap(images[index], bitmap)
                            view.setContentDescription(images[index], "Review notifications")
                            view.setOnClickPendingIntent(images[index], WidgetActions.pending(context, id, "Hush", pkg))
                        }
                    }
                }
            }
            WidgetKind.TravelDocs -> {
                text("${data.stored.travelAttachments.size} documents", data.stored.travelAttachments.firstOrNull()?.title ?: "Everything for the journey")
                view.setViewVisibility(R.id.widget_button, View.VISIBLE)
                view.setTextViewText(R.id.widget_button, "Open documents")
                view.setOnClickPendingIntent(R.id.widget_button, WidgetActions.pending(context, id, "TravelDocs"))
            }
            WidgetKind.Battery -> {
                text(data.battery?.let { "$it%" } ?: "Battery unavailable", if (data.charging) "Charging \u00b7 Phone" else "Phone battery")
                view.setTextColor(R.id.widget_detail, 0xFF71D7B0.toInt())
                view.setTextViewTextSize(R.id.widget_detail, android.util.TypedValue.COMPLEX_UNIT_SP, 28f)
                data.battery?.let { level ->
                    view.setViewVisibility(R.id.widget_meter, View.VISIBLE)
                    view.setProgressBar(R.id.widget_meter, 100, level, false)
                }
            }
        }
        if (kind == WidgetKind.Quote) {
            view.setInt(R.id.widget_detail, "setMaxLines", 3)
            view.setTextViewTextSize(R.id.widget_detail, android.util.TypedValue.COMPLEX_UNIT_SP, 14f)
        }
        return view
    }

    private fun contentViews(context: Context, id: Int, kind: WidgetKind, data: WidgetSnapshot): RemoteViews {
        val view = RemoteViews(context.packageName, R.layout.lumen_widget_content)
        view.setInt(R.id.widget_root, "setBackgroundResource", background(data))
        val source = if (data.locked) null else when (kind) {
            WidgetKind.Quote -> data.quoteCreation
            WidgetKind.Photos -> data.photos.firstOrNull()
            else -> data.scribble
        }
        val bitmap = thumbnail(context, source, 768)
        val prefs = context.getSharedPreferences("lumen_widget_quotes", Context.MODE_PRIVATE)
        val quote = WidgetQuotes.at(if (id == 0) prefs.getInt("last_quote", 0) else prefs.getInt("quote_$id", prefs.getInt("last_quote", 0)))
        val emptyText = when {
            data.locked && kind != WidgetKind.Quote -> "Unlock to see your creation"
            kind == WidgetKind.Quote -> quote
            kind == WidgetKind.Photos -> "Choose your first photo"
            else -> "Draw your first scribble"
        }
        view.setViewVisibility(R.id.widget_canvas, if (bitmap != null) View.VISIBLE else View.GONE)
        view.setViewVisibility(R.id.widget_content, if (bitmap == null) View.VISIBLE else View.GONE)
        if (bitmap != null) view.setImageViewBitmap(R.id.widget_canvas, bitmap)
        view.setTextViewText(R.id.widget_content, emptyText)
        view.setContentDescription(R.id.widget_root, if (bitmap != null) "Your latest ${kind.title.lowercase()}" else emptyText)
        val rootAction = if (bitmap != null) WidgetActions.pending(context, id, "Photo", source.orEmpty())
            else if (kind == WidgetKind.Quote) LumenWidgetProvider.quoteRefresh(context, id)
            else WidgetActions.pending(context, id, kind.name)
        view.setOnClickPendingIntent(R.id.widget_root, rootAction)
        if (kind != WidgetKind.Quote && !data.locked) {
            view.setViewVisibility(R.id.widget_content_action, View.VISIBLE)
            view.setOnClickPendingIntent(R.id.widget_content_action, WidgetActions.pending(context, id, kind.name))
            view.setContentDescription(R.id.widget_content_action, if (kind == WidgetKind.Photos) "Choose photo" else "New scribble")
        }
        return view
    }

    private fun usageBars(values: List<Long>): Bitmap {
        val bitmap = Bitmap.createBitmap(220, 44, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val max = values.maxOrNull()?.coerceAtLeast(1) ?: 1
        values.forEachIndexed { index, value ->
            val x = index * 34f + 8f
            paint.color = if (index % 2 == 0) 0xFFBBA4F4.toInt() else 0xFF8F9DDD.toInt()
            canvas.drawRoundRect(RectF(x, 42 - (value * 38f / max).coerceAtLeast(3f), x + 18, 42f), 5f, 5f, paint)
        }
        return bitmap
    }

    /** Decode at thumbnail size, so multiple widgets stay well below RemoteViews' bitmap limit. */
    private fun thumbnail(context: Context, source: String?, maxSize: Int = 256): Bitmap? {
        if (source.isNullOrBlank()) return null
        return runCatching {
            val uri = if (source.startsWith('/')) Uri.fromFile(java.io.File(source)) else Uri.parse(source)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSize) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        }.getOrNull()
    }

    private fun roundedThumbnail(source: Bitmap, aspect: Float): Bitmap {
        val width = 160
        val height = (width / aspect).toInt()
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val shader = android.graphics.BitmapShader(source, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP)
        val scale = maxOf(width.toFloat() / source.width, height.toFloat() / source.height)
        shader.setLocalMatrix(android.graphics.Matrix().apply {
            setScale(scale, scale); postTranslate((width - source.width * scale) / 2, (height - source.height * scale) / 2)
        })
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader }
        val radius = minOf(width, height) * .12f
        canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), radius, radius, paint)
        return output
    }

    private fun contactAvatar(source: Bitmap?, name: String): Bitmap {
        val result = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        if (source != null) {
            val shader = android.graphics.BitmapShader(source, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP)
            val scale = 96f / minOf(source.width, source.height)
            shader.setLocalMatrix(android.graphics.Matrix().apply {
                setScale(scale, scale); postTranslate((96 - source.width * scale) / 2, (96 - source.height * scale) / 2)
            })
            paint.shader = shader
        } else paint.color = 0xFF36535D.toInt()
        canvas.drawCircle(48f, 48f, 42f, paint)
        if (source == null) {
            paint.apply { color = 0xFFE4CB91.toInt(); textSize = 32f; textAlign = Paint.Align.CENTER }
            canvas.drawText(WidgetContentRules.initials(name), 48f, 48f - (paint.ascent() + paint.descent()) / 2, paint)
        }
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f
        paint.color = 0xFFE4CB91.toInt()
        canvas.drawCircle(48f, 48f, 46f, paint)
        return result
    }
}
