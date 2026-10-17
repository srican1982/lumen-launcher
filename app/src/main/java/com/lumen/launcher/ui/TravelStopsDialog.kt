package com.lumen.launcher.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lumen.launcher.data.TripStop
import com.lumen.launcher.vm.LauncherUiState
import com.lumen.launcher.vm.LauncherViewModel
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun TravelStopsDialog(state: LauncherUiState, vm: LauncherViewModel, onDismiss: () -> Unit) {
    var place by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var latitude by remember { mutableStateOf("") }
    var longitude by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Itinerary & places") },
        text = { Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.travelStops.forEach { stop ->
                Row(Modifier.fillMaxWidth()) {
                    Text(stop.place, modifier = Modifier.weight(1f))
                    TextButton(onClick = { vm.removeTravelStop(stop.id) }) { Text("Remove") }
                }
            }
            OutlinedTextField(place, { place = it }, label = { Text("Place name") }, singleLine = true)
            OutlinedTextField(address, { address = it }, label = { Text("Address for navigation") }, singleLine = true)
            OutlinedTextField(date, { date = it }, label = { Text("Date & time (optional)") }, placeholder = { Text("2026-10-27 10:00") }, singleLine = true)
            Text("Optional coordinates let Lumen draw your saved route without downloading a map.")
            OutlinedTextField(latitude, { latitude = it }, label = { Text("Latitude (optional)") }, singleLine = true)
            OutlinedTextField(longitude, { longitude = it }, label = { Text("Longitude (optional)") }, singleLine = true)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { TextButton(onClick = {
            val time = if (date.isBlank()) null else runCatching {
                LocalDateTime.parse(date.trim().replace(' ', 'T')).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }.getOrNull()
            val lat = latitude.toDoubleOrNull(); val lon = longitude.toDoubleOrNull()
            when {
                place.isBlank() -> error = "Enter a place name."
                date.isNotBlank() && time == null -> error = "Use YYYY-MM-DD HH:mm."
                (latitude.isNotBlank() || longitude.isNotBlank()) &&
                    (lat == null || lon == null || !lat.isFinite() || !lon.isFinite() || lat !in -90.0..90.0 || lon !in -180.0..180.0) -> error = "Enter a valid latitude and longitude together."
                else -> {
                    vm.saveTravelStop(TripStop(place = place.trim(), query = address.trim().ifBlank { place.trim() }, at = time, latitude = lat, longitude = lon))
                    place = ""; address = ""; date = ""; latitude = ""; longitude = ""; error = null
                }
            }
        }) { Text("Add stop") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Done") } })
}
