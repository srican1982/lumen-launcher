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
import androidx.compose.foundation.border
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
import kotlinx.coroutines.launch
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


@Composable
fun FocusPeoplePicker(
    selected: List<FocusPerson>,
    onDismiss: () -> Unit,
    onSave: (List<FocusPerson>) -> Unit,
    dismissSignal: Int = 0
) {
    val context = LocalContext.current
    val currentSelected by rememberUpdatedState(selected)
    val save by rememberUpdatedState(onSave)
    val dismiss by rememberUpdatedState(onDismiss)
    var error by remember { mutableStateOf("") }
    var launched by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode != android.app.Activity.RESULT_OK || uri == null) dismiss()
        else scope.launch {
            val picked = withContext(Dispatchers.IO) { runCatching {
                context.contentResolver.query(uri, arrayOf(
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
                    ContactsContract.CommonDataKinds.Phone.PHOTO_URI), null, null, null)?.use { c ->
                    if (c.moveToFirst()) FocusPerson(c.getString(0), c.getString(1).orEmpty(), c.getString(2).orEmpty(),
                        c.getString(3).orEmpty(), photoUri = c.getString(4).orEmpty()) else null
                }?.let { person ->
                    // Save a private thumbnail while the picker URI grant is still available.
                    val photo = runCatching {
                        val bytes = context.contentResolver.query(uri, arrayOf(ContactsContract.CommonDataKinds.Phone.PHOTO_ID), null, null, null)?.use { c ->
                            if (c.moveToFirst() && !c.isNull(0)) {
                                val photoId = c.getLong(0)
                                context.contentResolver.query(ContactsContract.Data.CONTENT_URI,
                                    arrayOf(ContactsContract.CommonDataKinds.Photo.PHOTO),
                                    "_id = ?", arrayOf(photoId.toString()), null)?.use { photoCursor ->
                                    if (photoCursor.moveToFirst()) photoCursor.getBlob(0) else null
                                }
                            } else null
                        } ?: person.photoUri.takeIf { it.isNotBlank() }?.let {
                            context.contentResolver.openInputStream(Uri.parse(it))?.use { stream -> stream.readBytes() }
                        }
                        bytes?.let {
                            val dir = java.io.File(context.filesDir, "focus_avatars").apply { mkdirs() }
                            val file = java.io.File(dir, "${person.id.filter(Char::isDigit)}.jpg")
                            file.writeBytes(it)
                            Uri.fromFile(file).toString()
                        }
                    }.getOrNull()
                    person.copy(photoUri = photo ?: person.photoUri)
                }
            }.getOrNull() }
            if (picked == null || picked.phone.isBlank()) error = "Couldn't read that contact. Please try again."
            else {
                val previous = currentSelected.find { it.id == picked.id }
                save(currentSelected.filterNot { it.id == picked.id } + picked.copy(reach = previous?.reach ?: FocusReach.CallsOnly))
            }
        }
    }
    fun openContacts() {
        runCatching { picker.launch(Intent(Intent.ACTION_PICK).setType(ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE)) }
            .onFailure { error = "No contact picker is available on this phone." }
    }
    LaunchedEffect(Unit) { if (!launched) { launched = true; openContacts() } }
    val dismissBaseline = remember { dismissSignal }
    LaunchedEffect(dismissSignal) { if (dismissSignal != dismissBaseline) dismiss() }
    if (error.isNotBlank()) AlertDialog(onDismissRequest = onDismiss,
        title = { Text("Contact unavailable") }, text = { Text(error) },
        confirmButton = { TextButton(onClick = { error = ""; openContacts() }) { Text("Try again") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })

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
