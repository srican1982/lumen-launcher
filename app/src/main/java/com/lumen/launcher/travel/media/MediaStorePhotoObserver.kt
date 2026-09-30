package com.lumen.launcher.travel.media

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    private var worker: Job? = null
    private val changes = Channel<Unit>(Channel.CONFLATED)

    private val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            onChange(selfChange, null)
        }

        override fun onChange(selfChange: Boolean, uri: Uri?) {
            // OEM cameras also notify external_primary/file rather than external/images.
            // The repository query filters images and deduplicates existing captures.
            if (registered) changes.trySend(Unit)
        }
    }

    fun start() {
        if (registered) return
        context.contentResolver.registerContentObserver(
            Uri.parse("content://media"),
            true,
            observer
        )
        registered = true
        worker = scope.launch {
            for (change in changes) {
                delay(1_200)
                // Coalesce bursts without cancelling an import already in progress.
                changes.tryReceive()
                try {
                    onMediaChanged()
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Retry on the next notification or foreground refresh.
                }
            }
        }
    }

    fun stop() {
        if (!registered) return
        runCatching { context.contentResolver.unregisterContentObserver(observer) }
        worker?.cancel()
        worker = null
        registered = false
    }
}
