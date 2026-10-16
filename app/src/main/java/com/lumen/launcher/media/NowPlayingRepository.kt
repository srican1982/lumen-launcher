package com.lumen.launcher.media

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.AudioManager
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import com.lumen.launcher.inbox.LumenNotificationListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlin.math.max

/** What is currently playing on the phone (Spotify, YouTube Music, podcasts…). */
data class NowPlaying(
    val packageName: String,
    val title: String,
    val artist: String,
    val art: Bitmap?,
    /** Some apps only give a link to the artwork instead of the picture. */
    val artUri: String? = null,
    val playing: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    /** SystemClock.elapsedRealtime() when [positionMs] was measured. */
    val positionUpdatedAt: Long,
    val speed: Float,
    val canSeek: Boolean = false
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
    /** Players found through their media notifications (backup when the session list is unavailable). */
    private var observed: List<MediaController> = emptyList()
    private var fromNotifications: List<MediaController> = emptyList()

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
        choose(merge(list.orEmpty(), fromNotifications))
    }

    /**
     * Called by the notification listener with the media-session tokens found in current
     * notifications. Every music app posts one, so this works even where the phone refuses
     * the session list (seen on some Samsung builds).
     */
    fun updateFromNotifications(context: Context, tokens: List<MediaSession.Token>) {
        val app = context.applicationContext
        main.post {
            val known = fromNotifications.associateBy { it.sessionToken }
            fromNotifications = tokens.distinct().map { t -> known[t] ?: MediaController(app, t) }
            refreshFromManager()
        }
    }

    /** Safe to call repeatedly; silently does nothing until notification access is granted. */
    fun start(context: Context) {
        val app = context.applicationContext
        if (Looper.myLooper() == Looper.getMainLooper()) attach(app)
        else main.post { attach(app) }
    }

    /**
     * Attach on the main thread and return the current session after it has been chosen.
     * Widgets used to call [start] from a background thread then read [state] immediately —
     * the attach was still queued, so YouTube looked like "nothing playing".
     */
    suspend fun current(context: Context): NowPlaying? = withContext(Dispatchers.Main.immediate) {
        attach(context.applicationContext)
        state.value
    }

    private fun attach(app: Context) {
        if (manager != null) {
            refreshFromManager()
            return
        }
        val m = app.getSystemService(MediaSessionManager::class.java) ?: return
        val comp = ComponentName(app, LumenNotificationListener::class.java)
        try {
            m.addOnActiveSessionsChangedListener(sessionsListener, comp, main)
            manager = m
            component = comp
            choose(merge(m.getActiveSessions(comp), fromNotifications))
        } catch (_: SecurityException) {
            // Session list not allowed here; notification tokens still work.
            manager = m
            component = comp
            refreshFromManager()
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

    fun playPause(context: Context) {
        val c = controller ?: return sendMediaKey(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        if (isPlaying(c.playbackState?.state)) c.transportControls.pause()
        else c.transportControls.play()
    }

    fun next(context: Context) {
        val c = controller ?: return sendMediaKey(context, KeyEvent.KEYCODE_MEDIA_NEXT)
        c.transportControls.skipToNext()
    }

    fun previous(context: Context) {
        val c = controller ?: return sendMediaKey(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        c.transportControls.skipToPrevious()
    }

    /** Seek only when the active session advertises support and a known duration. */
    fun seekTo(fraction: Float) {
        val c = controller ?: return
        val duration = c.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: return
        val actions = c.playbackState?.actions ?: 0L
        if (!fraction.isFinite() || duration <= 0L || actions and PlaybackState.ACTION_SEEK_TO == 0L) return
        c.transportControls.seekTo((duration * fraction.coerceIn(0f, 1f)).toLong())
    }

    /** Same as a headphone button: resumes / skips in whatever app played last. */
    private fun sendMediaKey(context: Context, code: Int) {
        val audio = context.getSystemService(AudioManager::class.java) ?: return
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        // The player usually appears a moment later; look again.
        main.postDelayed({ refreshFromManager() }, 600)
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
        val m = manager
        val c = component
        val fromManager = if (m != null && c != null) {
            runCatching { m.getActiveSessions(c) }.getOrNull().orEmpty()
        } else {
            emptyList()
        }
        choose(merge(fromManager, fromNotifications))
    }

    /** Session list + notification players, without duplicates. */
    private fun merge(a: List<MediaController>, b: List<MediaController>): List<MediaController> {
        val seen = HashSet<MediaSession.Token>()
        return (a + b).filter { seen.add(it.sessionToken) }
    }

    private fun isPlaying(state: Int?): Boolean = state in setOf(PlaybackState.STATE_PLAYING, PlaybackState.STATE_BUFFERING, PlaybackState.STATE_CONNECTING)

    private fun choose(list: List<MediaController>) {
        observed.filter { old -> list.none { it.sessionToken == old.sessionToken } }.forEach { runCatching { it.unregisterCallback(callback) } }
        list.filter { new -> observed.none { it.sessionToken == new.sessionToken } }.forEach { it.registerCallback(callback, main) }
        observed = list

        val playing = list.firstOrNull { isPlaying(it.playbackState?.state) }
        val current = list.firstOrNull { it.sessionToken == controller?.sessionToken }
        val pick = playing ?: current ?: list.firstOrNull()
        if (pick?.sessionToken != controller?.sessionToken) {
            controller = pick
        }
        publish()
    }

    private fun detach() {
        observed.forEach { runCatching { it.unregisterCallback(callback) } }
        observed = emptyList()
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
            ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_DESCRIPTION)
            ?: ""
        val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
            ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM)
            ?: ""
        val art = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        val state = ps?.state
        val playing = isPlaying(state)
        // Keep a visible row even when YouTube/Music briefly omit metadata.
        val shownTitle = title.ifBlank {
            when {
                playing -> "Playing now"
                else -> ""
            }
        }
        _state.value = NowPlaying(
            packageName = c.packageName,
            title = shownTitle,
            artist = artist,
            art = art?.let(::thumbnail),
            artUri = md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
                ?: md?.getString(MediaMetadata.METADATA_KEY_ART_URI)
                ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI),
            playing = playing,
            positionMs = ps?.position ?: 0L,
            durationMs = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L,
            positionUpdatedAt = ps?.lastPositionUpdateTime?.takeIf { it > 0 } ?: SystemClock.elapsedRealtime(),
            speed = ps?.playbackSpeed?.takeIf { it > 0f } ?: 1f,
            canSeek = ((ps?.actions ?: 0L) and PlaybackState.ACTION_SEEK_TO) != 0L
        )
    }

    private fun thumbnail(b: Bitmap): Bitmap {
        val maxSide = 192
        if (max(b.width, b.height) <= maxSide) return b
        val scale = maxSide.toFloat() / max(b.width, b.height)
        return Bitmap.createScaledBitmap(b, (b.width * scale).toInt().coerceAtLeast(1), (b.height * scale).toInt().coerceAtLeast(1), true)
    }
}
