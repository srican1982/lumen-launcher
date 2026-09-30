package com.lumen.launcher.travel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.lumen.launcher.travel.model.Trip

@Composable
fun TripGalleryScreen(onClose: () -> Unit, tripVm: TripViewModel = viewModel()) {
    val state by tripVm.uiState.collectAsState()
    var selected by remember { mutableStateOf<Trip?>(null) }
    LaunchedEffect(Unit) { tripVm.onForeground() }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Trip Albums", style = MaterialTheme.typography.headlineSmall)
                    TextButton(onClick = onClose) { Text("Close") }
                }
                Text("Your journeys, in photos", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                val trips = listOfNotNull(state.activeTrip) + state.pastTrips
                if (trips.isEmpty()) Text("No trips yet. Turn on Trip Mode in Travel to collect your next journey.")
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(trips, key = { it.id }) { trip ->
                        Card(Modifier.fillMaxWidth().clickable { selected = trip }) {
                            Column(Modifier.padding(16.dp)) {
                                Text(trip.displayTitle, style = MaterialTheme.typography.titleLarge)
                                Text(formatTripRange(trip.startTime, trip.endTime))
                                Text("${trip.photoCount} ${if (trip.photoCount == 1) "photo" else "photos"}${if (trip.endTime == null) " · In progress" else ""}")
                                if (trip.previewUris.isNotEmpty()) {
                                    Spacer(Modifier.height(12.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        trip.previewUris.take(4).forEach { uri ->
                                            AsyncImage(uri, null, modifier = Modifier.size(64.dp), contentScale = ContentScale.Crop)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        selected?.let { TripAlbumScreen(it, onClose = { selected = null }, tripVm = tripVm) }
    }
}
