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
    /**
     * Camera captures only (DCIM/Camera and OEM camera folders).
     * Screenshots, downloads, messaging apps, and gallery imports are excluded.
     */
    fun isCameraPhoto(relativePath: String?, displayName: String?, mimeType: String? = null): Boolean {
        val path = (relativePath ?: "").replace('\\', '/').lowercase().trim('/')
        val name = (displayName ?: "").lowercase()
        if (mimeType != null && !mimeType.startsWith("image/")) return false
        if (name.endsWith(".mp4") || name.endsWith(".mov") || name.endsWith(".webm")) return false
        if (path.contains("screenshot") || name.contains("screenshot") || name.startsWith("img_screenshot")) return false
        if (path.contains("screen recordings") || path.contains("screen_recording")) return false
        if (path.contains("whatsapp") || path.contains("telegram") || path.contains("download") ||
            path.contains("pictures/instagram") || path.contains("pictures/facebook") ||
            path.contains("pictures/messenger") || path.contains("pictures/snapchat") ||
            path.contains("pictures/twitter") || path.contains("pictures/chrome") ||
            path.contains("pictures/safari") || path.contains("pictures/wechat")
        ) return false
        if (path.contains("pictures/edited") || path.contains("pictures/raw") && !path.contains("dcim")) return false

        // Strict: only known camera output folders under DCIM.
        val cameraRoots = listOf(
            "dcim/camera",
            "dcim/100andro",
            "dcim/100media",
            "dcim/100apple",
            "dcim/opencamera",
            "dcim/camera/telephoto",
            "dcim/camera/wideangle",
            "dcim/gopro"
        )
        return cameraRoots.any { root ->
            path == root || path.startsWith("$root/") || path.startsWith(root)
        }
    }

    @Deprecated("Use isCameraPhoto", ReplaceWith("isCameraPhoto(relativePath, displayName, null)"))
    fun isLikelyCameraPhoto(relativePath: String?, displayName: String?): Boolean =
        isCameraPhoto(relativePath, displayName, null)

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
            val sinceSec = (sinceEpochMs / 1000L).coerceAtLeast(0L)
            val selection =
                "(${MediaStore.Images.Media.DATE_TAKEN} >= ? OR " +
                    "((${MediaStore.Images.Media.DATE_TAKEN} IS NULL OR ${MediaStore.Images.Media.DATE_TAKEN} = 0) " +
                    "AND ${MediaStore.Images.Media.DATE_ADDED} >= ?))"
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
                    if (!isCameraPhoto(path, name, mime)) continue
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
