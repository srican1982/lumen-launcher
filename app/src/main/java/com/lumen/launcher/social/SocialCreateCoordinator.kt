package com.lumen.launcher.social

import android.app.Application
import android.graphics.Bitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File

class SocialCreateCoordinator(
    private val application: Application,
    private val scope: CoroutineScope
) {
    val repository = CreationRepository(application)
    val share = ShareContentManager(application)

    val creations: Flow<List<CreationItem>> = repository.items

    fun saveBitmap(kind: CreationKind, bitmap: Bitmap, onSaved: (CreationItem) -> Unit = {}) {
        scope.launch {
            val bytes = ByteArrayOutputStream().use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                stream.toByteArray()
            }
            val item = repository.savePng(kind, bytes)
            onSaved(item)
        }
    }

    /**
     * Saves the creation to history (PNG) and shares a die-cut WebP sticker built from it.
     * [transparentBitmap] must be rendered with no background.
     */
    fun shareAsSticker(kind: CreationKind, transparentBitmap: Bitmap, onDone: () -> Unit = {}) {
        scope.launch {
            val file = withContext(Dispatchers.Default) {
                val sticker = StickerMaker.make(transparentBitmap)
                val bytes = StickerMaker.encodeWebp(sticker)
                val dir = File(application.cacheDir, "stickers").apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() }
                File(dir, "lumen_sticker_${System.currentTimeMillis()}.webp").apply { writeBytes(bytes) }
            }
            saveBitmap(kind, transparentBitmap)
            share.shareSticker(file)
            onDone()
        }
    }

    fun share(file: File) {
        share.shareImage(file)
    }

    fun copy(file: File) {
        share.copyImageUri(file)
    }

    fun delete(id: String) {
        scope.launch { repository.delete(id) }
    }
}
