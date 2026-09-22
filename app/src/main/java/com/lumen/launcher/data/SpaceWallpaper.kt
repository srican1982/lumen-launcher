package com.lumen.launcher.data

import android.content.Context
import android.net.Uri
import java.io.File

object SpaceWallpaper {
    fun file(context: Context, space: SpaceKind): File {
        val dir = File(context.filesDir, "wallpapers").apply { mkdirs() }
        return File(dir, "${space.name}.jpg")
    }

    fun copy(context: Context, uri: Uri, space: SpaceKind): String? {
        val dest = file(context, space)
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        dest.setLastModified(System.currentTimeMillis())
        return dest.absolutePath
    }

    fun delete(context: Context, space: SpaceKind) {
        file(context, space).delete()
    }
}
