package com.lumen.launcher.social

import android.content.ClipData
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import androidx.core.content.FileProvider
import java.io.File

class ShareContentManager(private val context: Context) {

    fun uriFor(file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    fun shareImage(file: File, chooserTitle: String = "Share") {
        val uri = uriFor(file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /**
     * Saves an image into the phone's Gallery under Pictures/Lumen (visible in Gallery,
     * Google Photos and My Files). Android 10+ needs no storage permission for this.
     * Returns false if saving is not possible on this device.
     */
    fun saveToGallery(bytes: ByteArray, displayName: String, mimeType: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Lumen")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: return false
        return runCatching {
            resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("no stream")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        }.getOrElse {
            resolver.delete(uri, null, null)
            false
        }
    }

    /** Shares a WebP sticker; apps that support stickers keep the transparency. */
    fun shareSticker(file: File) {
        val uri = uriFor(file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/webp"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share sticker").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun copyImageUri(file: File): Boolean {
        val uri = uriFor(file)
        val clip = ClipData.newUri(context.contentResolver, "image", uri)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(clip)
        return clipboard != null
    }

    fun startDrag(view: View, file: File, label: String = "Lumen creation") {
        val uri = uriFor(file)
        val clip = ClipData.newUri(context.contentResolver, label, uri)
        val shadow = View.DragShadowBuilder(view)
        view.startDragAndDrop(clip, shadow, uri, View.DRAG_FLAG_GLOBAL)
    }
}
