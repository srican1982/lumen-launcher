package com.lumen.launcher.media

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.lumen.launcher.inbox.LumenNotificationListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.max

/** What is currently playing on the phone (Spotify, YouTube Music, podcasts…). */
data class NowPlaying(
    val packageName: String,
    val title: String,
    val artist: String,
    val art: Bitmap?,
    val playing: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    /** SystemClock.elapsedRealtime() when [positionMs] was measured. */
    val positionUpdatedAt: Long,
    val speed: Float
) {
    /** Live position, advanced by the time passed since the player last reported it. */
    fun currentPosition(now: Long = SystemClock.elapsedRealtime()): Long {
        if (!playing) return positionMs
        val p = positionMs + ((now - positionUpdatedAt) * speed).toLong()
        return if (durationMs > 0) p.coerceIn(0L, durationMs) else max(0L, p)
    }

    val progress: Float
        get() = if (durationMs > 0) (currentPosition().toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

/**
 * Follows the phone's active media session. Android only allows this for apps with
 * notification access — Lumen already has that for badges and Flow, so no new permission.
 */
object NowPlayingRepository {
    private val _state = MutableStateFlow<NowPlaying?>(null)
    val state: StateFlow<NowPlaying?> = _state

    private val main = Handler(Looper.getMainLooper())
    private var manager: MediaSessionManager? = null
    private var component: ComponentName? = null
    private var controller: MediaController? = null

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            // Another app may have started playing; prefer whichever is playing.
            refreshFromManager()
        }

        override fun onSessionDestroyed() {
            detach()
            refreshFromManager()
        }
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        choose(list.orEmpty())
    }

    /** Safe to call repeatedly; silently does nothing until notification access is granted. */
    fun start(context: Context) {
        main.post {
            if (manager != null) {
                refreshFromManager()
                return@post
            }
            val app = context.applicationContext
            val m = app.getSystemService(MediaSessionManager::class.java) ?: return@post
            val comp = ComponentName(app, LumenNotificationListener::class.java)
            try {
                m.addOnActiveSessionsChangedListener(sessionsListener, comp, main)
                manager = m
                component = comp
                choose(m.getActiveSessions(comp))
            } catch (_: SecurityException) {
                // Notification access not granted (yet).
            }
        }
    }

    fun stop() {
        main.post {
            runCatching { manager?.removeOnActiveSessionsChangedListener(sessionsListener) }
            manager = null
            component = null
            detach()
            _state.value = null
        }
    }

    fun playPause() {
        val c = controller ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause()
        else c.transportControls.play()
    }

    fun next() {
        controller?.transportControls?.skipToNext()
    }

    fun previous() {
        controller?.transportControls?.skipToPrevious()
    }

    /** Opens the app that is playing (its player screen if it provides one). */
    fun openPlayer(context: Context): Boolean {
        val c = controller ?: return false
        c.sessionActivity?.let { pi ->
            if (runCatching { pi.send() }.isSuccess) return true
        }
        val launch = context.packageManager.getLaunchIntentForPackage(c.packageName) ?: return false
        context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    private fun refreshFromManager() {
        val m = manager ?: return publish()
        val c = component ?: return publish()
        runCatching { choose(m.getActiveSessions(c)) }.onFailure { publish() }
    }

    private fun choose(list: List<MediaController>) {
        val playing = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
        val current = list.firstOrNull { it.sessionToken == controller?.sessionToken }
        val pick = playing ?: current ?: list.firstOrNull()
        if (pick?.sessionToken != controller?.sessionToken) {
            detach()
            controller = pick
            pick?.registerCallback(callback, main)
        }
        publish()
    }

    private fun detach() {
        runCatching { controller?.unregisterCallback(callback) }
        controller = null
    }

    private fun publish() {
        val c = controller
        if (c == null) {
            _state.value = null
            return
        }
        val md = c.metadata
        val ps = c.playbackState
        val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: ""
        val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
            ?: ""
        val art = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        val state = ps?.state
        _state.value = NowPlaying(
            packageName = c.packageName,
            title = title,
            artist = artist,
            art = art?.let(::thumbnail),
            playing = state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING,
            positionMs = ps?.position ?: 0L,
            durationMs = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L,
            positionUpdatedAt = ps?.lastPositionUpdateTime?.takeIf { it > 0 } ?: SystemClock.elapsedRealtime(),
            speed = ps?.playbackSpeed?.takeIf { it > 0f } ?: 1f
        )
    }

    private fun thumbnail(b: Bitmap): Bitmap {
        val maxSide = 192
        if (max(b.width, b.height) <= maxSide) return b
        val scale = maxSide.toFloat() / max(b.width, b.height)
        return Bitmap.createScaledBitmap(b, (b.width * scale).toInt().coerceAtLeast(1), (b.height * scale).toInt().coerceAtLeast(1), true)
    }
}
