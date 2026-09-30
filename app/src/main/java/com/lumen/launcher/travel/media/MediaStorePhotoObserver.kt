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

/**
 * Watches MediaStore for new images while Trip Mode is active.
 * Debounces bursts of insert notifications, then asks the repository to sync.
 */
class MediaStorePhotoObserver(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onMediaChanged: suspend () -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private var registered = false
    private var debounceJob: Job? = null

    private val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            debounceJob?.cancel()
            debounceJob = scope.launch {
                delay(700)
                runCatching { onMediaChanged() }
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
