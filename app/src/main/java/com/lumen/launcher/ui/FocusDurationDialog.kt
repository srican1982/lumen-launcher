package com.lumen.launcher.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun FocusDurationDialog(onDismiss: () -> Unit, onStart: (Int) -> Unit, initialMinutes: Int = 25, confirmLabel: String = "Start focus") {
    var hours by rememberSaveable { mutableStateOf((initialMinutes / 60).toString()) }
    var minutes by rememberSaveable { mutableStateOf((initialMinutes % 60).toString()) }
    val h = hours.toIntOrNull() ?: 0
    val m = minutes.toIntOrNull() ?: 0
    val total = h * 60 + m
    val valid = h in 0..24 && m in 0..59 && total in 1..1440
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = Color(0xFF292532),
        title = { Text("Time to focus", fontSize = 26.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Quiet notifications. One thing at a time.", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = hours, onValueChange = { input -> if (input.length <= 2 && input.all { it in '0'..'9' }) hours = input },
                        label = { Text("Hours") }, modifier = Modifier.weight(1f), singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        textStyle = TextStyle(fontSize = 32.sp, textAlign = TextAlign.Center),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = h > 24
                    )
                    OutlinedTextField(
                        value = minutes, onValueChange = { input -> if (input.length <= 2 && input.all { it in '0'..'9' }) minutes = input },
                        label = { Text("Minutes") }, modifier = Modifier.weight(1f), singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        textStyle = TextStyle(fontSize = 32.sp, textAlign = TextAlign.Center),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = m > 59
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(25, 50, 90).forEach { preset ->
                        FilterChip(selected = total == preset, onClick = {
                            hours = (preset / 60).toString(); minutes = (preset % 60).toString()
                        }, label = { Text(preset.toString() + "m") }, modifier = Modifier.weight(1f))
                    }
                }
                Text(if (valid) "${h}h ${m}m · You can end the session anytime." else "Choose 1 minute to 24 hours. Minutes must be 0–59.",
                    color = if (valid) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { Button(onClick = { if (valid) onStart(total) }, enabled = valid, modifier = Modifier.height(50.dp), shape = RoundedCornerShape(16.dp)) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
