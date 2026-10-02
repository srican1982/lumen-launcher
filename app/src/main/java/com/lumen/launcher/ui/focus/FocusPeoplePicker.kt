package com.lumen.launcher.ui.focus

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lumen.launcher.data.ContactLookup
import com.lumen.launcher.focus.FocusPerson
import com.lumen.launcher.focus.FocusReach
import com.lumen.launcher.ui.theme.Outfit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

private val FocusGreen = Color(0xFF34D399)
private val colors = listOf(
    0xFF34D399, 0xFF60A5FA, 0xFFA78BFA, 0xFFF472B6, 0xFFFBBF24, 0xFFFB7185
)

@Composable
fun FocusPeoplePicker(
    selected: List<FocusPerson>,
    onDismiss: () -> Unit,
    onSave: (List<FocusPerson>) -> Unit
) {
    val context = LocalContext.current
    val lookup = remember { ContactLookup(context) }
    var query by remember { mutableStateOf("") }
    var names by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var draft by remember { mutableStateOf(selected.associateBy { it.id }.toMutableMap()) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            if (!lookup.hasAccess()) {
                names = emptyList()
                return@withContext
            }
            val found = lookup.names(80).map { name ->
                val match = lookup.matches(name, 1).firstOrNull()
                name to (match?.phone.orEmpty())
            }
            names = found
        }
    }

    val filtered = remember(query, names) {
        val q = query.trim().lowercase()
        if (q.isBlank()) names else names.filter { it.first.lowercase().contains(q) }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xF21A1F2A))
                .border(1.dp, Color.White.copy(0.12f), RoundedCornerShape(28.dp))
                .padding(18.dp)
        ) {
            Text(
                "Who can reach you",
                color = Color.White,
                fontFamily = Outfit,
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp
            )
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(0.08f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Search, null, tint = Color.White.copy(0.55f), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontFamily = Outfit, fontSize = 14.sp),
                    cursorBrush = SolidColor(FocusGreen),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        if (query.isBlank()) {
                            Text("Search contacts", color = Color.White.copy(0.4f), fontFamily = Outfit, fontSize = 14.sp)
                        }
                        inner()
                    }
                )
            }
            Spacer(Modifier.height(12.dp))
            if (!lookup.hasAccess()) {
                Text(
                    "Allow Contacts access to pick people.",
                    color = Color.White.copy(0.6f),
                    fontFamily = Outfit,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 20.dp)
                )
            } else {
                LazyColumn(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filtered, key = { it.first + it.second }) { (name, phone) ->
                        val existing = draft.values.find {
                            it.name.equals(name, true) ||
                                (phone.isNotBlank() && it.phone.filter(Char::isDigit) == phone.filter(Char::isDigit))
                        }
                        val checked = existing != null
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    if (checked && existing != null) {
                                        draft = draft.toMutableMap().also { it.remove(existing.id) }
                                    } else {
                                        val id = existing?.id ?: UUID.randomUUID().toString()
                                        val color = colors[kotlin.math.abs(name.hashCode()) % colors.size]
                                        draft = draft.toMutableMap().also {
                                            it[id] = FocusPerson(
                                                id = id,
                                                name = name,
                                                phone = phone,
                                                reach = FocusReach.CallsAndMessages,
                                                avatarColor = color
                                            )
                                        }
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PersonAvatar(name, existing?.avatarColor ?: colors[kotlin.math.abs(name.hashCode()) % colors.size])
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(name, color = Color.White, fontFamily = Outfit, fontSize = 15.sp)
                                if (phone.isNotBlank()) {
                                    Text(phone, color = Color.White.copy(0.45f), fontFamily = Outfit, fontSize = 11.sp)
                                }
                            }
                            Box(
                                Modifier
                                    .size(22.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (checked) FocusGreen else Color.White.copy(0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (checked) Icon(Icons.Outlined.Check, null, tint = Color.Black, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = Color.White.copy(0.7f), fontFamily = Outfit)
                }
                TextButton(onClick = { onSave(draft.values.toList()) }) {
                    Text("Done", color = FocusGreen, fontFamily = Outfit, fontWeight = FontWeight.SemiBold)
                }
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
