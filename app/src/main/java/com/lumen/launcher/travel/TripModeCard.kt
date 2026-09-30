package com.lumen.launcher.travel

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FlightTakeoff
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lumen.launcher.travel.model.Trip
import com.lumen.launcher.ui.theme.Lumen
import com.lumen.launcher.ui.theme.Outfit
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun TripModeCard(
    modifier: Modifier = Modifier,
    tripVm: TripViewModel = viewModel(),
    onViewTrip: (Trip) -> Unit = {}
) {
    val state by tripVm.uiState.collectAsState()
    var confirmEnd by remember { mutableStateOf(false) }
    val awaitingLocationSettings = remember { AtomicBoolean(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                tripVm.refreshPermissions()
                if (awaitingLocationSettings.compareAndSet(true, false)) {
                    tripVm.onReturnedFromLocationSettings()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        tripVm.onLocationPermissionResult(granted)
    }

    val mediaPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> tripVm.onMediaPermissionResult(granted) }

    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.07f))
                )
            )
            .border(
                0.8.dp,
                Brush.verticalGradient(listOf(Color.White.copy(0.35f), Color.White.copy(0.10f))),
                shape
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.FlightTakeoff, null, tint = Lumen.Text, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("Trip Mode", color = Lumen.Text, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text(
                    if (state.tripModeEnabled) "Organizing new photos by location on your device."
                    else "Turn on Trip Mode to organize photos from your next trip.",
                    color = Lumen.Muted,
                    fontFamily = Outfit,
                    fontSize = 12.sp
                )
            }
            if (state.isLoading) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Lumen.Text)
            } else {
                Switch(
                    checked = state.tripModeEnabled,
                    onCheckedChange = { tripVm.onTripModeToggled(it) }
                )
            }
        }

        val trip = state.activeTrip
        if (trip != null) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Current Trip", color = Lumen.Muted, fontFamily = Outfit, fontSize = 11.sp, letterSpacing = 0.6.sp)
                Text(trip.displayTitle, color = Lumen.Text, fontFamily = Outfit, fontWeight = FontWeight.Medium, fontSize = 18.sp)
                val place = listOfNotNull(trip.primaryCity, trip.countryName).distinct().joinToString(" · ")
                if (place.isNotBlank()) Text(place, color = Lumen.Text.copy(alpha = 0.85f), fontFamily = Outfit, fontSize = 13.sp)
                Text(
                    formatTripRange(trip.startTime, trip.endTime),
                    color = Lumen.Muted,
                    fontFamily = Outfit,
                    fontSize = 12.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.PhotoLibrary, null, tint = Lumen.Text.copy(0.8f), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (trip.photoCount == 1) "1 photo" else "${trip.photoCount} photos",
                        color = Lumen.Text,
                        fontFamily = Outfit,
                        fontSize = 13.sp
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onViewTrip(trip) }, modifier = Modifier.weight(1f)) {
                    Text("View Trip", fontSize = 13.sp)
                }
                OutlinedButton(onClick = { confirmEnd = true }, modifier = Modifier.weight(1f)) {
                    Text("End Trip", fontSize = 13.sp)
                }
            }
            Text("Trip organization happens on your device.", color = Lumen.Faint, fontFamily = Outfit, fontSize = 11.sp)
        }
    }

    if (state.needsLocationPermission) {
        AlertDialog(
            onDismissRequest = tripVm::dismissLocationPermissionPrompt,
            title = { Text("Allow location for Trip Mode?") },
            text = {
                Text(
                    "Allow Lumen to use your location while Trip Mode is active?\n\n" +
                        "Lumen uses your phone's location to organize photos into trip albums such as Japan, Tokyo, Kyoto.\n\n" +
                        "Camera Location Tags are not required. Your photos are not uploaded, and location is not used when Trip Mode is off."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    tripVm.dismissLocationPermissionPrompt()
                    locationPermission.launch(TripViewModel.locationPermissions)
                }) { Text("Continue") }
            },
            dismissButton = {
                TextButton(onClick = tripVm::dismissLocationPermissionPrompt) { Text("Not Now") }
            }
        )
    }

    if (state.needsLocationServices) {
        AlertDialog(
            onDismissRequest = tripVm::dismissLocationServicesPrompt,
            title = { Text("Location is turned off") },
            text = {
                Text("Trip Mode needs your phone's location to organize photos by country and city.")
            },
            confirmButton = {
                TextButton(onClick = {
                    awaitingLocationSettings.set(true)
                    tripVm.openLocationSettings()
                }) { Text("Open Location Settings") }
            },
            dismissButton = {
                TextButton(onClick = {
                    awaitingLocationSettings.set(false)
                    tripVm.dismissLocationServicesPrompt()
                }) { Text("Cancel") }
            }
        )
    }

    if (state.needsMediaPermission) {
        AlertDialog(
            onDismissRequest = tripVm::dismissMediaPermissionPrompt,
            title = { Text("Allow photo access?") },
            text = {
                Text("Lumen watches for new camera photos while Trip Mode is on, and links them into a virtual trip album. Originals stay in your gallery.")
            },
            confirmButton = {
                TextButton(onClick = {
                    tripVm.dismissMediaPermissionPrompt()
                    mediaPermission.launch(TripViewModel.mediaPermission())
                }) { Text("Continue") }
            },
            dismissButton = {
                TextButton(onClick = tripVm::dismissMediaPermissionPrompt) { Text("Not Now") }
            }
        )
    }

    if (confirmEnd && state.activeTrip != null) {
        val t = state.activeTrip!!
        AlertDialog(
            onDismissRequest = { confirmEnd = false },
            title = { Text("End ${t.displayTitle}?") },
            text = { Text("New photos will stop being added automatically.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmEnd = false
                    tripVm.endTripConfirmed()
                }) { Text("End Trip") }
            },
            dismissButton = { TextButton(onClick = { confirmEnd = false }) { Text("Cancel") } }
        )
    }

    state.completedTrip?.let { done ->
        AlertDialog(
            onDismissRequest = tripVm::dismissCompleted,
            title = { Text("${done.displayTitle} complete") },
            text = {
                Text(
                    "${done.photoCount} photos" +
                        (done.primaryCity?.let { " · $it" } ?: "") +
                        (done.countryName?.let { " · $it" } ?: "")
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    tripVm.dismissCompleted()
                    onViewTrip(done)
                }) { Text("View Trip") }
            },
            dismissButton = {
                TextButton(onClick = tripVm::dismissCompleted) { Text("Close") }
            }
        )
    }

    state.errorMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = tripVm::clearError,
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = tripVm::clearError) { Text("OK") } }
        )
    }
}

private fun formatTripRange(start: Long, end: Long?): String {
    val fmt = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault())
    val startLabel = fmt.format(Date(start))
    return if (end == null) "$startLabel – Present" else "$startLabel – ${fmt.format(Date(end))}"
}
