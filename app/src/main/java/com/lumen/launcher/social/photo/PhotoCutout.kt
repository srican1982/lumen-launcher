package com.lumen.launcher.social.photo

import android.content.Context
import android.graphics.Bitmap
import com.google.android.gms.common.moduleinstall.InstallStatusListener
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.android.gms.common.moduleinstall.ModuleInstallStatusUpdate.InstallState
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

object PhotoCutout {
    suspend fun remove(context: Context, original: Bitmap, onStatus: (String) -> Unit): Bitmap {
        val segmenter = SubjectSegmentation.getClient(SubjectSegmenterOptions.Builder().enableForegroundBitmap().build())
        val installer = ModuleInstall.getClient(context)
        var listener: InstallStatusListener? = null
        try {
            if (!installer.areModulesAvailable(segmenter).await().areModulesAvailable()) {
                onStatus("Downloading background removal. First use needs internet.")
                val ready = CompletableDeferred<Unit>()
                listener = InstallStatusListener { update ->
                    when (update.installState) {
                        InstallState.STATE_COMPLETED -> ready.complete(Unit)
                        InstallState.STATE_FAILED, InstallState.STATE_CANCELED -> ready.completeExceptionally(
                            IllegalStateException("Couldn't download background removal. Check internet and Google Play services, then retry."))
                    }
                }
                withTimeout(120_000) {
                    val response = installer.installModules(ModuleInstallRequest.newBuilder().addApi(segmenter).setListener(listener!!).build()).await()
                    if (!response.areModulesAlreadyInstalled()) ready.await()
                }
            }
            onStatus("Removing background on your phone...")
            val source = withContext(Dispatchers.Default) {
                val max = maxOf(original.width, original.height)
                if (max > 1600) {
                    val scale = 1600f / max
                    Bitmap.createScaledBitmap(original, (original.width * scale).toInt().coerceAtLeast(1), (original.height * scale).toInt().coerceAtLeast(1), true)
                } else original
            }
            val output = segmenter.process(InputImage.fromBitmap(source, 0)).await().foregroundBitmap
                    ?: error("No subject found. Try a photo with a clear person, pet or object.")
                val visible = withContext(Dispatchers.Default) {
                    val pixels = IntArray(output.width * output.height)
                    output.getPixels(pixels, 0, output.width, 0, 0, output.width, output.height)
                    pixels.any { (it ushr 24) > 16 }
                }
                check(visible) { "No subject found. Try another photo." }
            // Don't recycle source: cancellation can precede completion of the ML task.
            return output
        } finally {
            listener?.let { installer.unregisterListener(it) }
            segmenter.close()
        }
    }
}
