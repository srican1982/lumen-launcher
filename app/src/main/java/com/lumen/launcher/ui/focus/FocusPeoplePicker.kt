package com.lumen.launcher.ui.focus

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.lumen.launcher.focus.FocusPerson
import com.lumen.launcher.focus.FocusReach
import com.lumen.launcher.ui.theme.Outfit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val FocusGreen = Color(0xFF83F5AC)

@Composable
fun FocusPeoplePicker(selected: List<FocusPerson>, onDismiss: () -> Unit, onSave: (List<FocusPerson>) -> Unit) {
    val context = LocalContext.current
    var access by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { access = it }
    var query by remember { mutableStateOf("") }
    var people by remember { mutableStateOf(emptyList<FocusPerson>()) }
    var draft by remember { mutableStateOf(selected) }
    var error by remember { mutableStateOf("") }
    LaunchedEffect(access) {
        if (access) {
            val result = withContext(Dispatchers.IO) { runCatching {
                context.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(ContactsContract.CommonDataKinds.Phone.CONTACT_ID, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY, ContactsContract.CommonDataKinds.Phone.PHOTO_URI),
                    null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC")?.use { c -> buildList {
                        while (c.moveToNext()) add(FocusPerson(id = c.getString(0), name = c.getString(1).orEmpty(),
                            phone = c.getString(2).orEmpty(), contactLookupKey = c.getString(3).orEmpty(), photoUri = c.getString(4).orEmpty()))
                    } }.orEmpty().distinctBy { it.id }
            } }
            people = result.getOrDefault(emptyList())
            error = if (result.isFailure) "Contacts could not be loaded. Check Contacts access." else ""
        }
    }
    val filtered = (draft + people).distinctBy { it.id }.filter { it.name.contains(query.trim(), true) || it.phone.contains(query.trim()) }
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().heightIn(max = 620.dp).clip(RoundedCornerShape(26.dp))
            .background(Color(0xFF17212B)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Important people", color = Color.White, fontSize = 22.sp)
            Text("Calls enabled here are allowed during Focus. Message choices are saved preferences only; Android message exceptions use all favorites.", color = Color.White.copy(.65f), fontSize = 12.sp)
            OutlinedTextField(query, { query = it }, placeholder = { Text("Search contacts") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (!access) TextButton(onClick = { permission.launch(Manifest.permission.READ_CONTACTS) }) { Text("Allow Contacts access", color = FocusGreen) }
            if (error.isNotBlank()) Text(error, color = Color(0xFFFDA4AF))
            LazyColumn(Modifier.weight(1f, fill = false).heightIn(max = 340.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                if (filtered.isEmpty()) item { Text("No matching contacts", color = Color.White.copy(.6f)) }
                items(filtered, key = { it.id }) { person ->
                    val saved = draft.find { it.id == person.id }
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(.06f)).padding(horizontal = 10.dp, vertical = 3.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (person.photoUri.isNotBlank()) coil.compose.AsyncImage(person.photoUri, person.name, modifier = Modifier.size(32.dp).clip(CircleShape))
                            else PersonAvatar(person.name, person.avatarColor, 32.dp)
                            Text(person.name, color = Color.White, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                            Checkbox(saved != null, onCheckedChange = { checked ->
                                draft = if (checked) (draft + person).take(24) else draft.filterNot { it.id == person.id }
                            })
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                        if (saved != null) {
                            TextButton(onClick = {
                                val next = FocusReach.entries[(saved.reach.ordinal + 1) % FocusReach.entries.size]
                                draft = draft.map { if (it.id == person.id) it.copy(reach = next) else it }
                            }) { Text(saved.reach.label() + "", color = FocusGreen) }
                        }
                        if (person.contactLookupKey.isNotBlank()) TextButton(onClick = {
                            val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, Uri.encode(person.contactLookupKey))
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }.onFailure { error = "Contacts app unavailable." }
                        }) { Text("Android favorite ↗", color = Color(0xFF93C5FD), fontSize = 10.sp) }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onDismiss) { Text("Cancel") }
                TextButton({ onSave(draft) }) { Text("Done", color = FocusGreen) }
            }
        }
    }
}

@Composable
internal fun PersonAvatar(name: String, colorLong: Long, size: androidx.compose.ui.unit.Dp = 40.dp) {
    val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(colorLong).copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        Text(initial, color = Color.White, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = (size.value * 0.38f).sp)
    }
}
