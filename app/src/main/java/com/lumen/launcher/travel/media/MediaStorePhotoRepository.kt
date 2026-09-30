package com.lumen.launcher.travel.media

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class MediaStorePhoto(
    val mediaStoreId: Long,
    val contentUri: Uri,
    val displayName: String?,
    val dateTaken: Long,
    val relativePath: String?,
    val mimeType: String?
)

object MediaStorePhotoRepository {
    fun isLikelyCameraPhoto(relativePath: String?, displayName: String?): Boolean {
        val path = (relativePath ?: "").lowercase()
        val name = (displayName ?: "").lowercase()
        if (path.contains("screenshot") || name.contains("screenshot") || name.startsWith("img_screenshot")) return false
        if (path.contains("whatsapp") || path.contains("telegram") || path.contains("download")) return false
        if (path.contains("screenshots") || path.contains("screen recordings")) return false
        if (path.contains("dcim") || path.contains("camera") || path.contains("100andro") || path.contains("100media")) return true
        // Unknown path: accept stills that look like camera filenames.
        return name.startsWith("img_") || name.startsWith("dsc") || name.startsWith("pxl_") ||
            name.startsWith("photo") || name.contains("camera")
    }

    suspend fun queryPhotosSince(context: Context, sinceEpochMs: Long): List<MediaStorePhoto> =
        withContext(Dispatchers.IO) {
            val collection = if (Build.VERSION.SDK_INT >= 29) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.DATE_TAKEN,
                MediaStore.Images.Media.MIME_TYPE,
                MediaStore.Images.Media.RELATIVE_PATH
            )
            // DATE_ADDED is seconds; DATE_TAKEN is ms.
            val sinceSec = (sinceEpochMs / 1000L).coerceAtLeast(0L)
            val selection = "(${MediaStore.Images.Media.DATE_TAKEN} >= ? OR (${MediaStore.Images.Media.DATE_TAKEN} IS NULL OR ${MediaStore.Images.Media.DATE_TAKEN} = 0) AND ${MediaStore.Images.Media.DATE_ADDED} >= ?)"
            val args = arrayOf(sinceEpochMs.toString(), sinceSec.toString())
            val sort = "${MediaStore.Images.Media.DATE_ADDED} ASC"
            val out = mutableListOf<MediaStorePhoto>()
            context.contentResolver.query(collection, projection, selection, args, sort)?.use { c ->
                val idIdx = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameIdx = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val addedIdx = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val takenIdx = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
                val mimeIdx = c.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
                val pathIdx = c.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
                while (c.moveToNext()) {
                    val id = c.getLong(idIdx)
                    val name = c.getString(nameIdx)
                    val addedMs = c.getLong(addedIdx) * 1000L
                    val taken = c.getLong(takenIdx).takeIf { it > 0 } ?: addedMs
                    if (taken < sinceEpochMs && addedMs < sinceEpochMs) continue
                    val path = if (pathIdx >= 0) c.getString(pathIdx) else null
                    val mime = c.getString(mimeIdx)
                    if (!isLikelyCameraPhoto(path, name)) continue
                    out += MediaStorePhoto(
                        mediaStoreId = id,
                        contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id),
                        displayName = name,
                        dateTaken = taken,
                        relativePath = path,
                        mimeType = mime
                    )
                }
            }
            out
        }
}
