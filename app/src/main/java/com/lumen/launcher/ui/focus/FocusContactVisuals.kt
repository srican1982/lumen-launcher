package com.lumen.launcher.ui.focus

import android.content.ContentUris
import android.net.Uri
import android.provider.ContactsContract
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.BusinessCenter
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.lumen.launcher.R
import com.lumen.launcher.focus.FocusPeopleGroup
import com.lumen.launcher.focus.FocusPerson
import com.lumen.launcher.focus.FocusReach
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun FocusContactAvatar(person: FocusPerson, size: Dp = 34.dp) {
    val context = LocalContext.current
    val photo by produceState(person.photoUri, person.id, person.photoUri, person.contactLookupKey) {
        value = if (person.photoUri.startsWith("file:")) person.photoUri else withContext(Dispatchers.IO) {
            runCatching {
                val uri = if (person.contactLookupKey.isNotBlank()) {
                    Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, Uri.encode(person.contactLookupKey))
                } else {
                    person.id.toLongOrNull()?.let { ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, it) }
                }
                uri?.let {
                    context.contentResolver.query(it, arrayOf(ContactsContract.Contacts.PHOTO_URI), null, null, null)
                        ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                }
            }.getOrNull().orEmpty().ifBlank { person.photoUri }
        }
    }
    var failed by remember(photo) { mutableStateOf(false) }
    if (photo.isNotBlank() && !failed) {
        coil.compose.AsyncImage(
            photo,
            person.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape),
            onError = { failed = true }
        )
    } else {
        FocusAtlasAvatar(seed = person.id + person.name, size = size)
    }
}

/** Round photo-style avatar from the shared social atlas (real faces, not letter circles). */
@Composable
internal fun FocusAtlasAvatar(seed: String, size: Dp, indexOverride: Int? = null) {
    val atlas = ImageBitmap.imageResource(R.drawable.social_avatar_atlas)
    val index = indexOverride ?: Math.floorMod(seed.hashCode(), 6)
    val cellWidth = atlas.width / 3
    val cellHeight = atlas.height / 2
    val painter = remember(atlas, index) {
        BitmapPainter(
            atlas,
            IntOffset((index % 3) * cellWidth, (index / 3) * cellHeight + (cellHeight - cellWidth) / 3),
            IntSize(cellWidth, cellWidth)
        )
    }
    Image(
        painter = painter,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape)
    )
}

/** Member photo when present; otherwise a real-looking atlas face (fixed per preset). */
@Composable
internal fun FocusGroupAvatar(group: FocusPeopleGroup, members: List<FocusPerson>, size: Dp = 40.dp) {
    val lead = members.firstOrNull()
    val presetIndex = when (group.id) {
        "family" -> 0
        "work_vips" -> 1
        "emergency" -> 2
        "vip" -> 3
        else -> null
    }
    if (lead != null) FocusContactAvatar(lead, size)
    else FocusAtlasAvatar(seed = group.id + group.title, size = size, indexOverride = presetIndex)
}

internal fun focusGroupIcon(group: FocusPeopleGroup): ImageVector = when (group.id) {
    "family" -> Icons.Filled.Home
    "work_vips" -> Icons.Outlined.BusinessCenter
    "emergency" -> Icons.Outlined.Shield
    "vip" -> Icons.Outlined.Star
    else -> Icons.Outlined.People
}

@Composable
internal fun FocusContactIndicators(person: FocusPerson, onChange: ((FocusReach) -> Unit)? = null) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(Triple(Icons.Filled.Call, person.reach.allowsCalls, "Calls")).forEach { (icon, enabled, label) ->
            Box(
                Modifier
                    .size(48.dp)
                    .then(
                        if (onChange != null) Modifier.toggleable(
                            value = enabled,
                            role = Role.Switch,
                            onValueChange = {
                                onChange(
                                    if (label == "Calls") person.reach.toggleCalls()
                                    else person.reach.toggleMessages()
                                )
                            }
                        ) else Modifier
                    )
                    .clip(CircleShape)
                    .background(if (enabled) Color(0xFF222B45) else Color(0xFF172330)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    "$label ${if (enabled) "selected" else "not selected"}",
                    tint = if (enabled) Color(0xFF9081FF) else Color(0xFF657185),
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}
