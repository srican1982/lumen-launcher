package com.lumen.launcher.search

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.ContactsContract
import android.provider.MediaStore

/** Searches only device content the user has allowed Lumen to read. */
class DeviceContentSearch(private val context: Context) {
    fun results(query: String): List<SearchHit.Action> {
        val term = query.trim().take(64)
        if (term.length < 2) return emptyList()
        val found = mutableListOf<SearchHit.Action>()
        if (hasContactsAccess()) found += contacts(term)
        else found += SearchHit.Action("permission:contacts", "Search contacts", "Allow access to find people", term)
        if (hasPhotosAccess()) found += photos(term)
        else found += SearchHit.Action("permission:photos", "Search photos", "Allow access to find images by name or album", term)
        return found
    }

    private fun hasContactsAccess() = context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    private fun hasPhotosAccess(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED ||
            (Build.VERSION.SDK_INT >= 34 && context.checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED)
    }

    private fun contacts(term: String): List<SearchHit.Action> = runCatching {
        val out = mutableListOf<SearchHit.Action>()
        val column = ContactsContract.Contacts.DISPLAY_NAME_PRIMARY
        context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts._ID, column),
            "$column LIKE ?", arrayOf("%$term%"), "$column COLLATE NOCASE"
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(column)
            while (cursor.moveToNext() && out.size < 8) {
                val name = cursor.getString(nameIndex)?.trim().orEmpty()
                if (name.isNotEmpty()) out += SearchHit.Action("contact:${cursor.getLong(idIndex)}", name, "Contact", term)
            }
        }
        out
    }.getOrDefault(emptyList())

    private fun photos(term: String): List<SearchHit.Action> = runCatching {
        val out = mutableListOf<SearchHit.Action>()
        val name = MediaStore.Images.Media.DISPLAY_NAME
        val path = if (Build.VERSION.SDK_INT >= 29) MediaStore.Images.Media.RELATIVE_PATH else null
        val columns = if (path == null) arrayOf(MediaStore.Images.Media._ID, name) else arrayOf(MediaStore.Images.Media._ID, name, path)
        val where = if (path == null) "$name LIKE ?" else "($name LIKE ? OR $path LIKE ?)"
        val args = if (path == null) arrayOf("%$term%") else arrayOf("%$term%", "%$term%")
        context.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, columns, where, args, "${MediaStore.Images.Media.DATE_ADDED} DESC")?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(name)
            val pathIndex = path?.let(cursor::getColumnIndex) ?: -1
            while (cursor.moveToNext() && out.size < 8) {
                val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cursor.getLong(idIndex))
                val fileName = cursor.getString(nameIndex)?.trim().orEmpty()
                val album = if (pathIndex >= 0) cursor.getString(pathIndex)?.trim('/')?.substringAfterLast('/') else null
                out += SearchHit.Action("photo:$uri", fileName.ifBlank { "Photo" },
                    "Photo" + album?.takeIf { it.isNotBlank() }?.let { " \u00b7 $it" }.orEmpty(), term)
            }
        }
        out
    }.getOrDefault(emptyList())
}
