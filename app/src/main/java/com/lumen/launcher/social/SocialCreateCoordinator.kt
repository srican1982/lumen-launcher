package com.lumen.launcher.social

import android.app.Application
import android.graphics.Bitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File

class SocialCreateCoordinator(
    application: Application,
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
