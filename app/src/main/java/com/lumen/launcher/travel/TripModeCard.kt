package com.lumen.launcher.travel

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.FlightTakeoff
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.lumen.launcher.travel.model.Trip
import com.lumen.launcher.ui.theme.Lumen
import com.lumen.launcher.ui.theme.Outfit
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

private val TripPurple = Color(0xFFB794F6)
private val TripPurpleDeep = Color(0xFF9B6DEF)
private val CardFill = Color.White.copy(alpha = 0.08f)
private val CardStroke = Color(0xFFBF94EE).copy(alpha = 0.22f)

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
                tripVm.onForeground()
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
            .background(CardFill)
            .border(0.8.dp, CardStroke, shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(TripPurple.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.FlightTakeoff, null, tint = TripPurple, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Trip Mode",
                    color = Lumen.Text,
                    fontFamily = Outfit,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
                Text(
                    if (state.tripModeEnabled) "Organizing new photos by location on your device."
                    else "Organize travel photos automatically by location.",
                    color = Lumen.Muted,
                    fontFamily = Outfit,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Switch(
                checked = state.tripModeEnabled,
                onCheckedChange = { enabled ->
                    if (!state.isLoading) {
                        if (enabled) tripVm.onTripModeToggled(true) else confirmEnd = true
                    }
                },
                enabled = !state.isLoading,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = TripPurple,
                    checkedBorderColor = Color.Transparent,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = Color.White.copy(alpha = 0.22f),
                    uncheckedBorderColor = Color.Transparent
                )
            )
        }

        val trip = state.activeTrip
        if (trip != null) {
            ActiveTripBlock(
                trip = trip,
                onViewTrip = { onViewTrip(trip) },
                onEndTrip = { confirmEnd = true },
                busy = state.isLoading
            )
        }

        if (state.pastTrips.isNotEmpty()) {
            PastTripsBlock(
                trips = state.pastTrips.take(3),
                onOpen = onViewTrip
            )
        }
    }

    if (state.needsLocationPermission) {
        AlertDialog(
            onDismissRequest = tripVm::dismissLocationPermissionPrompt,
            title = { Text("Allow location for Trip Mode?") },
            text = {
                Text(
                    "Lumen uses your phone's location while Trip Mode is on to organize camera photos by country and city.\n\n" +
                        "Camera Location Tags are not required. Photos stay on your device."
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
            text = { Text("Turn on Location so Trip Mode can organize camera photos by place.") },
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
                Text("Lumen only watches for new Camera photos while Trip Mode is on, and links them into a trip album. Originals stay in your gallery.")
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
            title = { Text("End Trip?") },
            text = {
                Text("End your ${t.displayTitle} trip? New photos will no longer be added to this trip.")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmEnd = false
                    tripVm.endTripConfirmed()
                }) { Text("End Trip", color = Color(0xFFFF6B6B)) }
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

@Composable
private fun ActiveTripBlock(
    trip: Trip,
    onViewTrip: () -> Unit,
    onEndTrip: () -> Unit,
    busy: Boolean
) {
    val inner = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(inner)
            .background(Color.White.copy(alpha = 0.06f))
            .border(0.8.dp, Color.White.copy(alpha = 0.10f), inner)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TripCover(uri = trip.coverUri, modifier = Modifier.size(64.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(trip.flagEmoji, fontSize = 16.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        trip.displayTitle,
                        color = Lumen.Text,
                        fontFamily = Outfit,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                trip.primaryCity?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = Lumen.Text.copy(0.9f), fontFamily = Outfit, fontSize = 13.sp)
                }
                Text(
                    formatTripRange(trip.startTime, trip.endTime),
                    color = Lumen.Muted,
                    fontFamily = Outfit,
                    fontSize = 12.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.PhotoLibrary, null, tint = Lumen.Muted, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (trip.photoCount == 1) "1 photo" else "${trip.photoCount} photos",
                        color = Lumen.Text,
                        fontFamily = Outfit,
                        fontSize = 12.sp
                    )
                }
            }
        }

        if (trip.previewUris.size > 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                trip.previewUris.take(4).forEach { uri ->
                    AsyncImage(
                        model = Uri.parse(uri),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onViewTrip,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TripPurpleDeep,
                    contentColor = Color.White
                )
            ) {
                Icon(Icons.Outlined.PhotoLibrary, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("View Trip", fontFamily = Outfit, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                Spacer(Modifier.width(2.dp))
                Icon(Icons.Outlined.ChevronRight, null, modifier = Modifier.size(18.dp))
            }
            OutlinedButton(
                onClick = onEndTrip,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, TripPurple.copy(0.55f))
            ) {
                Icon(Icons.Outlined.Stop, null, tint = TripPurple, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("End Trip", color = TripPurple, fontFamily = Outfit, fontWeight = FontWeight.Medium, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun PastTripsBlock(trips: List<Trip>, onOpen: (Trip) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Past Trips",
            color = Lumen.Muted,
            fontFamily = Outfit,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            letterSpacing = 0.4.sp
        )
        trips.forEach { trip ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .clickable { onOpen(trip) }
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TripCover(uri = trip.coverUri, modifier = Modifier.size(48.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(trip.flagEmoji, fontSize = 14.sp)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            trip.displayTitle,
                            color = Lumen.Text,
                            fontFamily = Outfit,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        formatTripRange(trip.startTime, trip.endTime),
                        color = Lumen.Muted,
                        fontFamily = Outfit,
                        fontSize = 11.sp
                    )
                    Text(
                        buildString {
                            append(if (trip.photoCount == 1) "1 photo" else "${trip.photoCount} photos")
                            trip.citiesLabel?.let { append(" · "); append(it) }
                        },
                        color = Lumen.Faint,
                        fontFamily = Outfit,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(Icons.Outlined.ChevronRight, null, tint = Lumen.Faint, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun TripCover(uri: String?, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.08f))
    ) {
        if (uri != null) {
            AsyncImage(
                model = Uri.parse(uri),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                Icons.Outlined.PhotoLibrary,
                null,
                tint = Lumen.Faint,
                modifier = Modifier.align(Alignment.Center).size(22.dp)
            )
        }
    }
}

internal fun formatTripRange(start: Long, end: Long?): String {
    val fmt = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault())
    val startLabel = fmt.format(Date(start))
    return if (end == null) "$startLabel – Present" else "$startLabel – ${fmt.format(Date(end))}"
}
