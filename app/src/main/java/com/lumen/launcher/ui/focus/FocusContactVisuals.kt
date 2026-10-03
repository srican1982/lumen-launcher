package com.lumen.launcher.ui.focus

import android.content.ContentUris
import android.net.Uri
import android.provider.ContactsContract
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import com.lumen.launcher.focus.FocusReach
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
        value = if (person.photoUri.startsWith("file:")) person.photoUri else withContext(Dispatchers.IO) {
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
internal fun FocusContactIndicators(person: FocusPerson, onChange: ((FocusReach) -> Unit)? = null) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(Triple(Icons.Filled.Call, person.reach.allowsCalls, "Calls")).forEach { (icon, enabled, label) ->
            Box(Modifier.size(48.dp).then(if (onChange != null) Modifier.toggleable(value = enabled, role = Role.Switch, onValueChange = {
                onChange(if (label == "Calls") person.reach.toggleCalls() else person.reach.toggleMessages())
            }) else Modifier).clip(CircleShape).background(if (enabled) Color(0xFF222B45) else Color(0xFF172330)), contentAlignment = Alignment.Center) {
                Icon(icon, "$label ${if (enabled) "selected" else "not selected"}", tint = if (enabled) Color(0xFF9081FF) else Color(0xFF657185), modifier = Modifier.size(17.dp))
            }
        }
    }
}
