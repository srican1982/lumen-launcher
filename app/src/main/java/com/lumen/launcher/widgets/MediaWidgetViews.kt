package com.lumen.launcher.widgets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.view.View
import android.widget.RemoteViews
import androidx.core.graphics.drawable.toBitmap
import com.lumen.launcher.R
import com.lumen.launcher.media.NowPlaying
import kotlin.math.sin

/** Four Apple-style media widgets driven by the phone's active media session. */
object MediaWidgetViews {
    private val shortcutCandidates = listOf(
        MediaShortcut("YouTube", listOf("com.google.android.youtube"), "▶", 0xFFFF2D55.toInt()),
        MediaShortcut("Spotify", listOf("com.spotify.music", "com.spotify.lite"), "♫", 0xFF1DB954.toInt()),
        MediaShortcut(
            "Music",
            listOf(
                "com.google.android.apps.youtube.music",
                "com.apple.android.music",
                "com.samsung.android.app.music",
                "com.android.music"
            ),
            "♪",
            0xFFFC3C44.toInt()
        ),
        MediaShortcut(
            "Podcasts",
            listOf(
                "com.google.android.apps.podcasts",
                "com.apple.android.podcasts",
                "com.bambuna.podcastaddict"
            ),
            "◎",
            0xFF8B5CF6.toInt()
        )
    )

    fun views(context: Context, id: Int, kind: WidgetKind, data: WidgetSnapshot): RemoteViews {
        val layout = when (kind) {
            WidgetKind.MediaContinue -> R.layout.widget_media_continue
            WidgetKind.MediaPlayer -> R.layout.widget_media_player
            WidgetKind.MediaNowPlaying -> R.layout.widget_media_now
            WidgetKind.MediaShortcuts -> R.layout.widget_media_shortcuts
            else -> R.layout.widget_media_continue
        }
        val view = RemoteViews(context.packageName, layout)
        when (kind) {
            WidgetKind.MediaContinue -> bindContinue(context, id, kind, view, data)
            WidgetKind.MediaPlayer -> bindPlayer(context, id, kind, view, data)
            WidgetKind.MediaNowPlaying -> bindNow(context, id, kind, view, data)
            WidgetKind.MediaShortcuts -> bindShortcuts(context, id, view)
            else -> Unit
        }
        return view
    }

    private fun bindContinue(context: Context, id: Int, kind: WidgetKind, view: RemoteViews, data: WidgetSnapshot) {
        val np = data.nowPlaying
        bindHeader(context, view, np)
        bindArt(context, view, np, R.drawable.focus_evening_lake)
        view.setTextViewText(R.id.media_widget_title, np?.title?.takeIf { it.isNotBlank() } ?: "Continue watching")
        view.setTextViewText(
            R.id.media_widget_subtitle,
            np?.artist?.takeIf { it.isNotBlank() } ?: "Tap play when something is ready"
        )
        bindProgress(view, np)
        bindTransport(context, id, kind, view, np)
        val open = WidgetActions.pending(context, id, "OpenMedia")
        view.setOnClickPendingIntent(R.id.media_widget_root, open)
        view.setOnClickPendingIntent(R.id.media_widget_play_overlay, open)
        view.setViewVisibility(R.id.media_widget_play_overlay, if (np?.playing == true) View.GONE else View.VISIBLE)
    }

    private fun bindPlayer(context: Context, id: Int, kind: WidgetKind, view: RemoteViews, data: WidgetSnapshot) {
        val np = data.nowPlaying
        bindHeader(context, view, np)
        bindArt(context, view, np, R.drawable.focus_morning_lake)
        view.setTextViewText(R.id.media_widget_title, np?.title?.takeIf { it.isNotBlank() } ?: "Nothing playing")
        view.setTextViewText(
            R.id.media_widget_subtitle,
            np?.artist?.takeIf { it.isNotBlank() } ?: "Start media in YouTube, Spotify or Music"
        )
        bindProgress(view, np)
        val elapsed = np?.currentPosition() ?: 0L
        val duration = np?.durationMs ?: 0L
        view.setTextViewText(R.id.media_widget_elapsed, if (duration > 0) formatMs(elapsed) else "0:00")
        view.setTextViewText(
            R.id.media_widget_remaining,
            if (duration > 0) "-${formatMs((duration - elapsed).coerceAtLeast(0))}" else "--:--"
        )
        bindTransport(context, id, kind, view, np)
        val open = WidgetActions.pending(context, id, "OpenMedia")
        view.setTextViewText(
            R.id.media_widget_cta,
            if (np == null) "Open Media  ›" else if (isVideoApp(np.packageName)) "Continue Watching  ›" else "Open Player  ›"
        )
        view.setOnClickPendingIntent(R.id.media_widget_root, open)
        view.setOnClickPendingIntent(R.id.media_widget_cta, open)
        view.setOnClickPendingIntent(R.id.media_widget_open, open)
    }

    private fun bindNow(context: Context, id: Int, kind: WidgetKind, view: RemoteViews, data: WidgetSnapshot) {
        val np = data.nowPlaying
        bindArt(context, view, np, R.drawable.focus_sound_forest)
        view.setTextViewText(R.id.media_widget_title, np?.title?.takeIf { it.isNotBlank() } ?: "Not playing")
        view.setTextViewText(
            R.id.media_widget_subtitle,
            np?.artist?.takeIf { it.isNotBlank() } ?: appLabel(context, np?.packageName) ?: "Choose a song to begin"
        )
        view.setImageViewBitmap(R.id.media_widget_visualizer, visualizer(np))
        bindTransport(context, id, kind, view, np)
        val open = WidgetActions.pending(context, id, "OpenMedia")
        view.setOnClickPendingIntent(R.id.media_widget_root, open)
        view.setOnClickPendingIntent(R.id.media_widget_open, open)
        view.setOnClickPendingIntent(R.id.media_widget_heart, open)
    }

    private fun bindShortcuts(context: Context, id: Int, view: RemoteViews) {
        val slots = listOf(
            Triple(R.id.media_widget_slot1, R.id.media_widget_slot_icon1, R.id.media_widget_slot_label1),
            Triple(R.id.media_widget_slot2, R.id.media_widget_slot_icon2, R.id.media_widget_slot_label2),
            Triple(R.id.media_widget_slot3, R.id.media_widget_slot_icon3, R.id.media_widget_slot_label3),
            Triple(R.id.media_widget_slot4, R.id.media_widget_slot_icon4, R.id.media_widget_slot_label4)
        )
        shortcutCandidates.forEachIndexed { index, shortcut ->
            val (root, iconId, labelId) = slots[index]
            val pkg = shortcut.packages.firstOrNull { installed(context, it) }
            view.setTextViewText(labelId, shortcut.label)
            val icon = pkg?.let { packageIcon(context, it) }
            if (icon != null) view.setImageViewBitmap(iconId, icon)
            else {
                // Fallback glyph drawn as a simple colored tile via text isn't available on ImageView;
                // use a tiny generated bitmap with the brand color.
                view.setImageViewBitmap(iconId, glyphIcon(shortcut.glyph, shortcut.color))
            }
            view.setOnClickPendingIntent(
                root,
                if (pkg != null) WidgetActions.launchPackage(context, id, pkg)
                else WidgetActions.pending(context, id, "MediaStore", shortcut.label)
            )
        }
        view.setOnClickPendingIntent(R.id.media_widget_root, WidgetActions.pending(context, id, "MediaShortcuts"))
    }

    private fun bindHeader(context: Context, view: RemoteViews, np: NowPlaying?) {
        val label = appLabel(context, np?.packageName) ?: if (np == null) "Media" else "Now Playing"
        view.setTextViewText(R.id.media_widget_app, label)
        view.setTextViewText(R.id.media_widget_app_icon, if (isVideoApp(np?.packageName)) "▶" else "♪")
        view.setTextColor(
            R.id.media_widget_app_icon,
            when {
                isVideoApp(np?.packageName) -> 0xFFFF2D55.toInt()
                np?.packageName?.contains("spotify", true) == true -> 0xFF1DB954.toInt()
                else -> 0xFF8B5CF6.toInt()
            }
        )
    }

    private fun bindArt(context: Context, view: RemoteViews, np: NowPlaying?, fallback: Int) {
        val art = np?.art
            ?: np?.artUri?.let { uri ->
                runCatching {
                    context.contentResolver.openInputStream(android.net.Uri.parse(uri))?.use {
                        android.graphics.BitmapFactory.decodeStream(it)
                    }
                }.getOrNull()
            }
            ?: np?.packageName?.let { packageIcon(context, it, 256) }
        if (art != null) view.setImageViewBitmap(R.id.media_widget_art, art)
        else view.setImageViewResource(R.id.media_widget_art, fallback)
    }

    private fun bindProgress(view: RemoteViews, np: NowPlaying?) {
        val progress = ((np?.progress ?: 0f) * 1000).toInt().coerceIn(0, 1000)
        view.setProgressBar(R.id.media_widget_progress, 1000, progress, false)
    }

    private fun bindTransport(context: Context, id: Int, kind: WidgetKind, view: RemoteViews, np: NowPlaying?) {
        val playing = np?.playing == true
        view.setTextViewText(R.id.media_widget_play, if (playing) "❚❚" else "▶")
        view.setOnClickPendingIntent(
            R.id.media_widget_play,
            WidgetCommandReceiver.pending(context, id, kind, "PlayPause")
        )
        view.setOnClickPendingIntent(
            R.id.media_widget_prev,
            WidgetCommandReceiver.pending(context, id, kind, "MediaPrevious")
        )
        view.setOnClickPendingIntent(
            R.id.media_widget_next,
            WidgetCommandReceiver.pending(context, id, kind, "MediaNext")
        )
    }

    private fun visualizer(np: NowPlaying?): Bitmap {
        val width = 420
        val height = 84
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val bars = 28
        val gap = 4f
        val barWidth = (width - gap * (bars + 1)) / bars
        val seed = ((np?.title?.hashCode() ?: 0) xor (np?.currentPosition()?.div(250)?.toInt() ?: 0)).toLong()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, width.toFloat(), 0f,
                intArrayOf(0xFF8B5CF6.toInt(), 0xFFFF6B9D.toInt(), 0xFFFFFFFF.toInt()),
                null, Shader.TileMode.CLAMP
            )
        }
        val active = np?.playing == true
        for (i in 0 until bars) {
            val wave = (sin((i * 0.55) + seed * 0.08) + 1.0) / 2.0
            val pulse = if (active) 0.35 + wave * 0.65 else 0.18 + (i % 5) * 0.05
            val h = (height * pulse).toFloat().coerceIn(8f, height - 4f)
            val left = gap + i * (barWidth + gap)
            val top = (height - h) / 2f
            canvas.drawRoundRect(left, top, left + barWidth, top + h, 4f, 4f, paint)
        }
        return bitmap
    }

    private fun glyphIcon(glyph: String, color: Int): Bitmap {
        val size = 96
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        canvas.drawRoundRect(0f, 0f, size.toFloat(), size.toFloat(), 24f, 24f, bg)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = 0xFFFFFFFF.toInt()
            textAlign = Paint.Align.CENTER
            textSize = 42f
            isFakeBoldText = true
        }
        canvas.drawText(glyph, size / 2f, size / 2f + 14f, text)
        return bitmap
    }

    private fun formatMs(ms: Long): String {
        val total = (ms / 1000).toInt().coerceAtLeast(0)
        val m = total / 60
        val s = total % 60
        return "%d:%02d".format(m, s)
    }

    private fun isVideoApp(packageName: String?): Boolean {
        val p = packageName.orEmpty().lowercase()
        return (p.contains("youtube") && !p.contains("music")) ||
            p.contains("netflix") || p.contains("primevideo") || p.contains("disney")
    }

    private fun appLabel(context: Context, packageName: String?): String? {
        if (packageName.isNullOrBlank()) return null
        val p = packageName.lowercase()
        return when {
            p.contains("youtube") && p.contains("music") -> "YouTube Music"
            p.contains("youtube") -> "YouTube"
            p.contains("spotify") -> "Spotify"
            p.contains("podcast") -> "Podcasts"
            else -> runCatching {
                val pm = context.packageManager
                pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
            }.getOrNull()
        }
    }

    private fun installed(context: Context, packageName: String): Boolean =
        runCatching {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        }.getOrDefault(false)

    private fun packageIcon(context: Context, packageName: String, size: Int = 96): Bitmap? =
        runCatching {
            context.packageManager.getApplicationIcon(packageName).toBitmap(size, size)
        }.getOrNull()

    private data class MediaShortcut(val label: String, val packages: List<String>, val glyph: String, val color: Int)
}
