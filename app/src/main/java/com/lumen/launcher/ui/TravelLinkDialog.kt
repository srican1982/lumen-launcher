package com.lumen.launcher.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.lumen.launcher.data.TravelCategory
import com.lumen.launcher.data.TravelAttachments
import com.lumen.launcher.ui.theme.Outfit

@Composable
internal fun TravelLinkDialog(category: TravelCategory, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var url by rememberSaveable { mutableStateOf("") }
    var title by rememberSaveable { mutableStateOf("") }
    val normalized = TravelAttachments.webLink(url)
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = Color(0xFF292532)) {
            Column(Modifier.background(Brush.linearGradient(listOf(Color(0xFF3C304B), Color(0xFF242D34))))
                .verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TravelBadge(category)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(category.title.uppercase(), color = category.tint, fontSize = 10.sp, letterSpacing = 1.sp)
                        Text("Save a link", fontFamily = Outfit, color = Color.White, fontSize = 24.sp)
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "Close", tint = Color.White) }
                }
                Text("Keep bookings and useful websites close to your trip.", color = Color(0xFFD3C7DE), fontSize = 13.sp)
                OutlinedTextField(url, { url = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Website address") }, placeholder = { Text("example.com/booking") },
                    leadingIcon = { Icon(Icons.Outlined.Link, null) }, singleLine = true,
                    shape = RoundedCornerShape(16.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    isError = url.isNotBlank() && normalized == null,
                    supportingText = { if (url.isNotBlank() && normalized == null) Text("Enter a valid website address.") })
                OutlinedTextField(title, { title = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Name (optional)") }, placeholder = { Text("My booking") },
                    singleLine = true, shape = RoundedCornerShape(16.dp))
                normalized?.let { address ->
                    Surface(shape = RoundedCornerShape(14.dp), color = Color.White.copy(.06f)) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Link, null, tint = category.tint)
                            Column(Modifier.padding(start = 10.dp)) {
                                Text(title.ifBlank { "Saved website" }, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(Uri.parse(address).host.orEmpty(), color = category.tint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                Button(onClick = { normalized?.let { onSave(it, title.trim().ifBlank { Uri.parse(it).host ?: it }) } },
                    enabled = normalized != null, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(16.dp)) {
                    Text("Save link")
                }
            }
        }
    }
}
