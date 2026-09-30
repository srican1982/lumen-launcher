package com.lumen.launcher.travel.media

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Watches MediaStore for new images while Trip Mode is active.
 * Debounces bursts and serializes sync so Coil / gallery reads do not cause UI flicker loops.
 */
class MediaStorePhotoObserver(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onMediaChanged: suspend () -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private var registered = false
    private var debounceJob: Job? = null
    private val syncMutex = Mutex()
    @Volatile private var lastFireAt = 0L

    private val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            onChange(selfChange, null)
        }

        override fun onChange(selfChange: Boolean, uri: Uri?) {
            // Ignore pure self-notifications; require a concrete images URI when provided.
            if (uri != null) {
                val s = uri.toString()
                if (!s.contains("images", ignoreCase = true) &&
                    !s.contains(MediaStore.Images.Media.EXTERNAL_CONTENT_URI.toString())
                ) return
            }
            val now = System.currentTimeMillis()
            // Hard rate limit — MediaStore can spam on some OEMs when thumbnails are read.
            if (now - lastFireAt < 400L) return
            debounceJob?.cancel()
            debounceJob = scope.launch {
                delay(1_200)
                lastFireAt = System.currentTimeMillis()
                syncMutex.withLock {
                    runCatching { onMediaChanged() }
                }
            }
        }
    }

    fun start() {
        if (registered) return
        context.contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            observer
        )
        registered = true
    }

    fun stop() {
        if (!registered) return
        runCatching { context.contentResolver.unregisterContentObserver(observer) }
        debounceJob?.cancel()
        debounceJob = null
        registered = false
    }
}
