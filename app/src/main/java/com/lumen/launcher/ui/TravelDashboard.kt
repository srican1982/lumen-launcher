package com.lumen.launcher.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lumen.launcher.R
import com.lumen.launcher.data.TravelCategory
import com.lumen.launcher.data.TripStopNavigator
import com.lumen.launcher.travel.TripGalleryScreen
import com.lumen.launcher.travel.model.TripPhoto
import com.lumen.launcher.travel.model.TripUiState
import com.lumen.launcher.ui.theme.Lumen
import com.lumen.launcher.ui.theme.Outfit
import com.lumen.launcher.vm.LauncherUiState
import com.lumen.launcher.vm.LauncherViewModel
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TravelBlue = Color(0xFF1687FF)

@Composable
internal fun TravelDashboardContent(
    state: LauncherUiState, vm: LauncherViewModel, tripState: TripUiState, photos: List<TripPhoto>,
    destination: String, start: LocalDate?, end: LocalDate?, onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit, onOpenTrip: () -> Unit
) {
    val context = LocalContext.current
    val active = tripState.activeTrip
    var albums by remember { mutableStateOf(false) }
    var stopsOpen by remember { mutableStateOf(false) }
    var documents by remember { mutableStateOf(false) }
    var collection by remember { mutableStateOf<TravelCategory?>(null) }
    val next = TripStopNavigator.nextStop(stops = state.travelStops)
    val dateFormat = remember { DateTimeFormatter.ofPattern("MMM d, yyyy") }
    fun navigate() {
        val query = next?.query ?: destination.takeIf { it.isNotBlank() } ?: return
        val nav = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=${Uri.encode(query)}"))
        runCatching { context.startActivity(nav) }.onFailure {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(query)}"))) }
        }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Travel Mode", color = Color.White, fontFamily = Outfit, fontSize = 28.sp, fontWeight = FontWeight.Medium)
                Text("Organize. Navigate. Relive.", color = Color.White.copy(.85f), fontFamily = Outfit, fontSize = 13.sp)
            }
            Switch(tripState.tripModeEnabled, onToggle, enabled = !tripState.isLoading,
                colors = SwitchDefaults.colors(checkedTrackColor = Lumen.BrandGold,
                    checkedThumbColor = Lumen.OnBrandGold, checkedBorderColor = Color.Transparent,
                    uncheckedTrackColor = Color.White.copy(.2f)))
        }
        // Reveal the existing scenic wallpaper instead of covering the header with another card.
        Spacer(Modifier.height(56.dp))
        DashboardGlass {
            Row(Modifier.fillMaxWidth().clickable(onClick = onEdit), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(active?.coverUri ?: photos.firstOrNull()?.contentUri ?: R.drawable.travel_evening_valley,
                    "Trip cover", contentScale = ContentScale.Crop,
                    modifier = Modifier.size(76.dp).clip(RoundedCornerShape(16.dp)))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(active?.displayTitle ?: destination.ifBlank { "Your next adventure" }, color = Color.White,
                        fontFamily = Outfit, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val dates = when {
                        start != null && end != null -> "${start.format(dateFormat)} - ${end.format(dateFormat)}"
                        active != null -> "Since ${Instant.ofEpochMilli(active.startTime).atZone(ZoneId.systemDefault()).format(dateFormat)}"
                        else -> "Add a destination and trip dates"
                    }
                    Text(dates, color = Lumen.Muted, fontFamily = Outfit, fontSize = 11.sp)
                    Text(if (active != null) "Active Trip" else if (start != null) "Upcoming Trip" else "Add trip",
                        color = if (active != null) Color(0xFF55DEA0) else Lumen.BrandGold, fontFamily = Outfit, fontSize = 12.sp)
                }
                Icon(Icons.Outlined.ChevronRight, "Edit trip", tint = Color.White, modifier = Modifier.size(24.dp))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            val shortcuts = listOf(
                Triple("Itinerary", Icons.Outlined.Place, TravelBlue),
                Triple("Hotels", Icons.Outlined.Apartment, Color(0xFF8A59E8)),
                Triple("Places", Icons.Outlined.Restaurant, Color(0xFFEF7942)),
                Triple("Photos", Icons.Outlined.PhotoCamera, Color(0xFF16A36E)),
                Triple("Documents", Icons.Outlined.Description, Color(0xFFD65C60)),
                Triple("More", Icons.Outlined.MoreHoriz, Color(0xFF786B73)))
            shortcuts.forEachIndexed { index, (label, icon, color) ->
                Column(Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).clickable {
                    when (index) {
                        0, 2 -> stopsOpen = true
                        1 -> collection = TravelCategory.Hotels
                        3 -> albums = true
                        4 -> documents = true
                        else -> collection = TravelCategory.Apps
                    }
                }.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(42.dp).clip(CircleShape)
                        .background(Brush.verticalGradient(listOf(color.copy(.95f), color.copy(.65f))))
                        .border(.8.dp, Color.White.copy(.5f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.height(5.dp))
                    Text(label, color = Color.White, fontFamily = Outfit, fontSize = 9.sp, maxLines = 1)
                }
            }
        }
        Row(Modifier.fillMaxWidth().height(188.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DashboardGlass(Modifier.weight(1.15f).fillMaxHeight()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Next Destination", color = Color.White, fontFamily = Outfit, fontSize = 13.sp, lineHeight = 16.sp)
                    Text(next?.place ?: "Where to next?", color = Color.White, fontFamily = Outfit, fontSize = 16.sp,
                        fontWeight = FontWeight.Medium, lineHeight = 19.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(next?.at?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("EEE, MMM d - h:mm a")) } ?: "Add a stop to your itinerary",
                        color = Lumen.Muted, fontFamily = Outfit, fontSize = 10.sp, lineHeight = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(onClick = { navigate() }, enabled = next != null, colors = ButtonDefaults.buttonColors(containerColor = TravelBlue),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp), modifier = Modifier.weight(1f)) {
                            Text("Navigate", fontSize = 10.sp, maxLines = 1)
                        }
                        OutlinedButton(onClick = { stopsOpen = true }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            modifier = Modifier.weight(1f)) { Text("Details", color = Color.White, fontSize = 10.sp) }
                    }
                }
            }
            TravelDestinationMap(
                query = next?.query ?: destination,
                latitude = next?.latitude,
                longitude = next?.longitude,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                onOpenMaps = { navigate() },
                onAddPlace = { stopsOpen = true }
            )
        }
        val goldShape = RoundedCornerShape(22.dp)
        Column(Modifier.fillMaxWidth().shadow(8.dp, goldShape, spotColor = Lumen.BrandGold.copy(.5f))
            .clip(goldShape).background(Color(0x66231E2C)).border(1.5.dp, Lumen.BrandGold, goldShape).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth().clickable { if (active != null) onOpenTrip() else albums = true }, verticalAlignment = Alignment.CenterVertically) {
                Text(if (active != null) "Trip Album (Active)" else "Trip Albums", color = Color.White,
                    fontFamily = Outfit, fontSize = 18.sp, modifier = Modifier.weight(1f))
                Text("${active?.photoCount ?: tripState.pastTrips.sumOf { it.photoCount }} Photos", color = Lumen.Muted, fontFamily = Outfit, fontSize = 11.sp)
                Icon(Icons.Outlined.ChevronRight, "Open trip album", tint = Color.White)
            }
            val previews = photos.take(4).map { it.contentUri }.ifEmpty { tripState.pastTrips.mapNotNull { it.coverUri }.take(4) }
            if (previews.isEmpty()) {
                Text("Your trip memories will appear here. Turn on Travel mode to collect new camera photos.",
                    color = Lumen.Muted, fontFamily = Outfit, fontSize = 12.sp, modifier = Modifier.padding(vertical = 12.dp))
            } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                previews.forEach { uri -> AsyncImage(uri, "Trip photo", contentScale = ContentScale.Crop,
                    modifier = Modifier.weight(1f).height(64.dp).clip(RoundedCornerShape(10.dp))
                        .clickable { if (active != null) onOpenTrip() else albums = true }) }
                val remaining = (active?.photoCount ?: 0) - previews.size
                if (remaining > 0) Box(Modifier.weight(1f).height(64.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(.12f))
                    .clickable(onClick = onOpenTrip), contentAlignment = Alignment.Center) {
                    Text("+$remaining", color = Color.White, fontFamily = Outfit, fontSize = 16.sp)
                }
            }
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White.copy(.1f)).padding(9.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Info, null, tint = Lumen.Muted, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(7.dp))
                Text("One album during your trip. After it ends, Lumen organizes photos by city where location is available.",
                    color = Lumen.Muted, fontFamily = Outfit, fontSize = 10.sp, lineHeight = 13.sp)
            }
        }
    }
    if (stopsOpen) TravelStopsDialog(state, vm) { stopsOpen = false }
    if (albums) TripGalleryScreen(onClose = { albums = false })
    if (documents) TravelCollection(state, vm, null) { documents = false }
    collection?.let { TravelCollection(state, vm, it) { collection = null } }
}

@Composable
private fun DashboardGlass(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(modifier.fillMaxWidth().clip(shape)
        .background(Brush.verticalGradient(listOf(Color.White.copy(.2f), Color(0x66211E2A))))
        .border(.8.dp, Color.White.copy(.4f), shape).padding(12.dp), content = content)
}
