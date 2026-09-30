package com.lumen.launcher.travel

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.lumen.launcher.travel.model.Trip
import com.lumen.launcher.ui.rememberNavBottomPadding
import com.lumen.launcher.ui.theme.Lumen
import com.lumen.launcher.ui.theme.Outfit

private val Gold = Color(0xFFE8C98A)
private val TripPurple = Color(0xFFB794F6)

@Composable
fun TripGalleryScreen(onClose: () -> Unit, tripVm: TripViewModel = viewModel()) {
    val state by tripVm.uiState.collectAsState()
    var selected by remember { mutableStateOf<Trip?>(null) }
    LaunchedEffect(Unit) { tripVm.onForeground() }
    val navBottom = rememberNavBottomPadding(32.dp)
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val trips = remember(state.activeTrip, state.pastTrips) {
        listOfNotNull(state.activeTrip) + state.pastTrips
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFF12101A)) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(top = statusTop)
                    .padding(horizontal = 18.dp)
                    .padding(bottom = navBottom)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Trip Albums",
                            color = Lumen.Text,
                            fontFamily = Outfit,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 26.sp
                        )
                        Text(
                            "Your journeys, kept as folders",
                            color = Lumen.Muted,
                            fontFamily = Outfit,
                            fontSize = 13.sp
                        )
                    }
                    IconButton(onClick = onClose) {
                        Icon(Icons.Outlined.Close, "Close", tint = Lumen.Muted)
                    }
                }

                if (trips.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            TripAlbumsIcon(size = 72.dp)
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "No trips yet",
                                color = Lumen.Text,
                                fontFamily = Outfit,
                                fontWeight = FontWeight.Medium,
                                fontSize = 17.sp
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Turn on Trip Mode in Travel to collect\nyour next journey’s camera photos.",
                                color = Lumen.Muted,
                                fontFamily = Outfit,
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize().padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(bottom = 20.dp)
                    ) {
                        items(trips, key = { it.id }) { trip ->
                            TripFolderCard(trip = trip, onOpen = { selected = trip })
                        }
                    }
                }
            }
        }
        selected?.let { trip ->
            TripAlbumScreen(trip = trip, onClose = { selected = null }, tripVm = tripVm)
        }
    }
}

@Composable
private fun TripFolderCard(trip: Trip, onOpen: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White.copy(alpha = 0.06f))
            .border(
                0.8.dp,
                Brush.verticalGradient(listOf(Color.White.copy(0.22f), Color.White.copy(0.06f))),
                shape
            )
            .clickable(onClick = onOpen)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.05f)
                .background(Color(0xFF1A1624))
        ) {
            if (trip.coverUri != null) {
                AsyncImage(
                    model = Uri.parse(trip.coverUri),
                    contentDescription = trip.displayTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.PhotoLibrary,
                        null,
                        tint = TripPurple.copy(0.7f),
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
            // Soft bottom fade for title legibility
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(72.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0xCC0E0C14))
                        )
                    )
            )
            if (trip.endTime == null) {
                Text(
                    "Live",
                    color = Color.White,
                    fontFamily = Outfit,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(TripPurple.copy(alpha = 0.85f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
            Row(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(trip.flagEmoji, fontSize = 16.sp)
                Spacer(Modifier.width(6.dp))
                Text(
                    trip.displayTitle,
                    color = Color.White,
                    fontFamily = Outfit,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                formatTripRange(trip.startTime, trip.endTime),
                color = Lumen.Muted,
                fontFamily = Outfit,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                buildString {
                    append(if (trip.photoCount == 1) "1 photo" else "${trip.photoCount} photos")
                    trip.citiesLabel?.takeIf { it.isNotBlank() }?.let {
                        append(" · ")
                        append(it)
                    }
                },
                color = Gold.copy(alpha = 0.9f),
                fontFamily = Outfit,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
