package com.lumen.launcher.social

import android.app.Application
import android.graphics.Bitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.lumen.launcher.social.stickers.AddStickerResult
import com.lumen.launcher.social.stickers.LumenStickerPack
import com.lumen.launcher.social.stickers.StickerLibrary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
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

    /** Lumen's sticker packs (also what WhatsApp reads via LumenStickerProvider). */
    val stickerPacks: StateFlow<List<LumenStickerPack>> = StickerLibrary.packs

    init {
        // Loads the library and renders the 3 starter stickers on first run.
        scope.launch(Dispatchers.IO) { runCatching { StickerLibrary.ensureReady(application) } }
    }

    /**
     * Adds a sticker (built from a transparent-background bitmap) to the Lumen sticker pack.
     * If that pack is already in WhatsApp, the version bump tells WhatsApp to refresh it.
     */
    fun addToStickerPack(
        kind: CreationKind,
        transparentBitmap: Bitmap,
        accessibilityText: String,
        onDone: (Boolean) -> Unit = {}
    ) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                StickerLibrary.add(
                    context = application,
                    transparentBitmap = transparentBitmap,
                    emoji = when (kind) {
                        CreationKind.Scribble -> "✨"
                        CreationKind.Quote -> "💬"
                        CreationKind.Photo -> "📸"
                    },
                    accessibilityText = accessibilityText,
                    source = kind.name
                )
            }
            when (result) {
                is AddStickerResult.Added -> {
                    val p = result.pack
                    toast("Added to ${p.name} (${p.stickers.size}/${StickerLibrary.MAX_STICKERS})")
                    onDone(true)
                }
                is AddStickerResult.Failed -> {
                    toast("Couldn't add sticker: ${result.reason}")
                    onDone(false)
                }
            }
        }
    }

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

    /** Saves the die-cut sticker (transparent PNG, 512x512) to Pictures/Lumen. */
    fun saveStickerToGallery(kind: CreationKind, transparentBitmap: Bitmap) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                val sticker = StickerMaker.make(transparentBitmap)
                val png = ByteArrayOutputStream().use { s ->
                    sticker.compress(Bitmap.CompressFormat.PNG, 100, s)
                    s.toByteArray()
                }
                share.saveToGallery(png, "lumen_sticker_${System.currentTimeMillis()}.png", "image/png")
            }
            saveBitmap(kind, transparentBitmap)
            toast(if (ok) "Sticker saved to Pictures/Lumen" else "Couldn't save — needs Android 10 or newer")
        }
    }

    /** Saves the full image (with its chosen background) to Pictures/Lumen. */
    fun saveImageToGallery(kind: CreationKind, bitmap: Bitmap) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                val png = ByteArrayOutputStream().use { s ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, s)
                    s.toByteArray()
                }
                share.saveToGallery(png, "lumen_${kind.name.lowercase()}_${System.currentTimeMillis()}.png", "image/png")
            }
            saveBitmap(kind, bitmap)
            toast(if (ok) "Saved to Pictures/Lumen" else "Couldn't save — needs Android 10 or newer")
        }
    }

    private fun toast(message: String) {
        android.widget.Toast.makeText(application, message, android.widget.Toast.LENGTH_SHORT).show()
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
