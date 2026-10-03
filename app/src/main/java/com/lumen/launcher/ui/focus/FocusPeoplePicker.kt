package com.lumen.launcher.ui.focus

import android.Manifest
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.People
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.lumen.launcher.focus.FocusPerson
import com.lumen.launcher.focus.FocusReach
import com.lumen.launcher.ui.theme.Outfit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class DeviceContact(
    val id: String,
    val name: String,
    val phone: String,
    val lookupKey: String,
    val photoUri: String
)

@Composable
fun FocusPeoplePicker(
    selected: List<FocusPerson>,
    onDismiss: () -> Unit,
    onSave: (List<FocusPerson>) -> Unit,
    dismissSignal: Int = 0
) {
    val context = LocalContext.current
    val dismiss by rememberUpdatedState(onDismiss)
    val dismissBaseline = remember { dismissSignal }
    LaunchedEffect(dismissSignal) {
        if (dismissSignal != dismissBaseline) dismiss()
    }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
    }

    val contacts by produceState(emptyList<DeviceContact>(), hasPermission) {
        value = if (!hasPermission) emptyList() else withContext(Dispatchers.IO) {
            loadDeviceContacts(context)
        }
    }

    var draft by remember(selected) {
        mutableStateOf(selected.associateBy { it.id }.toMutableMap().let { LinkedHashMap(it) })
    }
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }

    val filtered = remember(contacts, query) {
        val q = query.trim()
        if (q.isEmpty()) contacts
        else contacts.filter {
            it.name.contains(q, ignoreCase = true) || it.phone.contains(q)
        }
    }

    FocusPopupSheet(onDismiss = onDismiss, heightFraction = 0.88f) {
        FocusSheetSearchHeader(
            title = "Add contacts",
            query = query,
            onQueryChange = { query = it },
            searching = searching,
            onSearchingChange = { searching = it }
        ) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(Icons.Outlined.People, null, tint = FocusMuted, modifier = Modifier.size(14.dp))
                Text("${draft.size} selected", color = FocusMuted, fontSize = 12.sp, fontFamily = Outfit)
            }
        }

        if (!searching) {
            Text(
                "Select everyone who can still call during Focus.",
                color = FocusMuted,
                fontFamily = Outfit,
                fontSize = 13.sp
            )
        }

        when {
            !hasPermission -> {
                Text(
                    "Contacts permission is needed to pick people.",
                    color = FocusMuted,
                    fontFamily = Outfit,
                    fontSize = 13.sp
                )
                TextButton(onClick = { permissionLauncher.launch(Manifest.permission.READ_CONTACTS) }) {
                    Text("Allow contacts", color = FocusAccent)
                }
            }
            filtered.isEmpty() -> {
                Text(
                    if (query.isNotBlank()) "No contacts match “$query”." else "No contacts with phone numbers found.",
                    color = FocusMuted,
                    fontFamily = Outfit,
                    fontSize = 13.sp
                )
            }
            else -> {
                LazyColumn(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered, key = { it.id }) { contact ->
                        val checked = contact.id in draft
                        val shape = RoundedCornerShape(16.dp)
                        val person = FocusPerson(
                            id = contact.id,
                            name = contact.name,
                            phone = contact.phone,
                            contactLookupKey = contact.lookupKey,
                            reach = draft[contact.id]?.reach ?: FocusReach.CallsOnly,
                            photoUri = contact.photoUri
                        )
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(shape)
                                .background(FocusCard.copy(alpha = 0.9f))
                                .border(1.dp, FocusBorder, shape)
                                .clickable {
                                    draft = LinkedHashMap(draft).also { map ->
                                        if (checked) map.remove(contact.id) else map[contact.id] = person
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FocusContactAvatar(person, 40.dp)
                            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                                Text(
                                    contact.name,
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontFamily = Outfit,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    contact.phone,
                                    color = FocusMuted,
                                    fontSize = 12.sp,
                                    fontFamily = Outfit,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Switch(
                                checked = checked,
                                onCheckedChange = { on ->
                                    draft = LinkedHashMap(draft).also { map ->
                                        if (on) map[contact.id] = person else map.remove(contact.id)
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedTrackColor = FocusAccent,
                                    checkedThumbColor = Color.White,
                                    uncheckedTrackColor = Color(0xFF1A1F35),
                                    uncheckedThumbColor = FocusMuted
                                )
                            )
                        }
                    }
                }
            }
        }

        FocusPrimaryAction(
            label = "Save contacts",
            action = {
                onSave(draft.values.toList())
            }
        )
    }
}

@Composable
internal fun PersonAvatar(name: String, colorLong: Long, size: androidx.compose.ui.unit.Dp = 40.dp) {
    FocusAtlasAvatar(seed = name + colorLong.toString(), size = size)
}

private fun loadDeviceContacts(context: android.content.Context): List<DeviceContact> {
    val found = LinkedHashMap<String, DeviceContact>()
    runCatching {
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
                ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
                ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY
            ),
            null,
            null,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} COLLATE NOCASE ASC"
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val phoneIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val lookupIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY)
            val photoIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)
            val primaryIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY)
            while (cursor.moveToNext()) {
                val id = cursor.getString(idIdx)?.trim().orEmpty()
                val name = cursor.getString(nameIdx)?.trim().orEmpty()
                val phone = cursor.getString(phoneIdx)?.trim().orEmpty()
                if (id.isBlank() || name.isBlank() || phone.isBlank()) continue
                val primary = primaryIdx >= 0 && cursor.getInt(primaryIdx) == 1
                val next = DeviceContact(
                    id = id,
                    name = name,
                    phone = phone,
                    lookupKey = if (lookupIdx >= 0) cursor.getString(lookupIdx).orEmpty() else "",
                    photoUri = if (photoIdx >= 0) cursor.getString(photoIdx).orEmpty() else ""
                )
                val existing = found[id]
                if (existing == null || primary) found[id] = next
            }
        }
    }
    return found.values.toList()
}
