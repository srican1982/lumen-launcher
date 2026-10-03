package com.lumen.launcher.ui.focus

import android.content.ContentUris
import android.net.Uri
import android.provider.ContactsContract
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lumen.launcher.focus.FocusPerson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun FocusContactAvatar(person: FocusPerson, size: Dp = 34.dp) {
    val context = LocalContext.current
    val photo by produceState(person.photoUri, person.id, person.photoUri, person.contactLookupKey) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val uri = if (person.contactLookupKey.isNotBlank()) Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, Uri.encode(person.contactLookupKey))
                    else person.id.toLongOrNull()?.let { ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, it) }
                uri?.let { context.contentResolver.query(it, arrayOf(ContactsContract.Contacts.PHOTO_URI), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null } }
            }.getOrNull().orEmpty().ifBlank { person.photoUri }
        }
    }
    var failed by remember(photo) { mutableStateOf(false) }
    if (photo.isNotBlank() && !failed) coil.compose.AsyncImage(photo, person.name, contentScale = ContentScale.Crop,
        modifier = Modifier.size(size).clip(CircleShape), onError = { failed = true })
    else PersonAvatar(person.name, person.avatarColor, size)
}

@Composable
internal fun FocusContactIndicators(person: FocusPerson) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(Triple(Icons.Filled.Call, person.reach.allowsCalls, "Calls"), Triple(Icons.Filled.ChatBubble, person.reach.allowsMessages, "Messages")).forEach { (icon, enabled, label) ->
            Box(Modifier.size(30.dp).clip(CircleShape).background(if (enabled) Color(0xFF252F49) else Color(0xFF27313D)), contentAlignment = Alignment.Center) {
                Icon(icon, "$label ${if (enabled) "selected" else "not selected"}", tint = if (enabled) Color(0xFF9F90FF) else Color(0xFF657185), modifier = Modifier.size(17.dp))
            }
        }
    }
}
