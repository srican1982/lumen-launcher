package com.lumen.launcher.social

import android.content.ClipData
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
