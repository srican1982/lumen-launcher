package com.lumen.launcher.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

object DeviceFirstName {
    fun read(context: Context): String? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) !=
            PackageManager.PERMISSION_GRANTED
        ) return null
        val raw = runCatching {
            context.contentResolver.query(
                ContactsContract.Profile.CONTENT_URI,
                arrayOf(ContactsContract.Profile.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()?.trim().orEmpty()
        val first = raw.substringBefore(' ').trim()
        val blocked = setOf("owner", "user", "phone", "samsung", "galaxy", "pixel", "android")
        if (first.length !in 2..18) return null
        if (first.lowercase() in blocked) return null
        if (!first.all { it.isLetter() || it == '-' || it == '\'' }) return null
        return first.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
    }
}
