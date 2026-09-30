package com.lumen.launcher.travel.share

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

object TripShareManager {
    private const val INSTAGRAM = "com.instagram.android"

    fun isInstagramInstalled(context: Context): Boolean =
        runCatching {
            context.packageManager.getPackageInfo(INSTAGRAM, 0)
            true
        }.getOrDefault(false)

    fun shareImages(context: Context, uris: List<Uri>, targetPackage: String? = null) {
        if (uris.isEmpty()) return
        val intent = Intent().apply {
            action = if (uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE
            type = "image/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (uris.size == 1) putExtra(Intent.EXTRA_STREAM, uris.first())
            else putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            if (!targetPackage.isNullOrBlank()) setPackage(targetPackage)
        }
        val chooser = Intent.createChooser(intent, "Share photos")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    fun shareToInstagram(context: Context, uris: List<Uri>) {
        if (isInstagramInstalled(context)) shareImages(context, uris, INSTAGRAM)
        else shareImages(context, uris)
    }
}
