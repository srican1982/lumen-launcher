package com.lumen.launcher.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Geocoder
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Museum
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Hotel
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.lumen.launcher.data.TripStop
import com.lumen.launcher.data.TripStopCategory
import com.lumen.launcher.data.TripStopNavigator
import com.lumen.launcher.ui.theme.Lumen
import com.lumen.launcher.ui.theme.Outfit
import com.lumen.launcher.vm.LauncherViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val TripPurple = Color(0xFFB794F6)
private val TripPurpleDeep = Color(0xFF9B6DEF)

@Composable
internal fun TravelNavigationBlock(
    stops: List<TripStop>,
    lastSelectedId: String?,
    vm: LauncherViewModel
) {
    var addStop by remember { mutableStateOf(false) }
    var addCategory by remember { mutableStateOf(TripStopCategory.Other) }
    var showRoute by remember { mutableStateOf(false) }
    var askWhere by remember { mutableStateOf(false) }
    val now = System.currentTimeMillis()
    val next = remember(stops, lastSelectedId, now) {
        TripStopNavigator.nextStop(now, stops, lastSelectedId)
    }
    val today = remember(stops, now) { TripStopNavigator.todaysRoute(now, stops) }
    val shape = RoundedCornerShape(16.dp)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (next != null) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(Color.White.copy(alpha = 0.07f))
                    .border(0.8.dp, Color.White.copy(alpha = 0.12f), shape)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Next",
                    color = TripPurple,
                    fontFamily = Outfit,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.8.sp
                )
                Text(
                    next.place,
                    color = Lumen.Text,
                    fontFamily = Outfit,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    stopDetailLine(next),
                    color = Lumen.Muted,
                    fontFamily = Outfit,
                    fontSize = 12.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { vm.navigateToStop(next) },
                        colors = ButtonDefaults.buttonColors(containerColor = TripPurpleDeep),
                        modifier = Modifier.weight(1f)
                    ) { Text("Navigate") }
                    OutlinedButton(
                        onClick = { showRoute = true },
                        modifier = Modifier.weight(1f)
                    ) { Text("Today's route") }
                }
            }
        } else {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(Color.White.copy(alpha = 0.07f))
                    .border(0.8.dp, Color.White.copy(alpha = 0.12f), shape)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "No next stop yet",
                    color = Lumen.Text,
                    fontFamily = Outfit,
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp
                )
                Text(
                    "Add a hotel, airport or place — Navigate will take you there.",
                    color = Lumen.Muted,
                    fontFamily = Outfit,
                    fontSize = 12.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { askWhere = true },
                        colors = ButtonDefaults.buttonColors(containerColor = TripPurpleDeep),
                        modifier = Modifier.weight(1f)
                    ) { Text("Navigate") }
                    OutlinedButton(
                        onClick = {
                            addCategory = TripStopCategory.Other
                            addStop = true
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Add stop") }
                }
            }
        }

        if (today.isNotEmpty() && next != null) {
            Text(
                "Today's route",
                color = Lumen.Muted,
                fontFamily = Outfit,
                fontSize = 11.sp
            )
            Text(
                today.joinToString(" → ") { shortLabel(it) },
                color = Lumen.Text.copy(alpha = 0.9f),
                fontFamily = Outfit,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable { showRoute = true }
            )
        }

        WorkspaceActions {
            OutlinedButton(onClick = {
                addCategory = TripStopCategory.Other
                addStop = true
            }) {
                Icon(Icons.Outlined.Add, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add stop")
            }
            if (today.isNotEmpty()) {
                OutlinedButton(onClick = { showRoute = true }) { Text("Today's route") }
            }
        }
    }

    if (addStop) {
        AddTripStopDialog(
            initialCategory = addCategory,
            onDismiss = { addStop = false },
            onSave = { stop ->
                vm.addTripStop(stop)
                addStop = false
            }
        )
    }
    if (showRoute) {
        TodaysRouteDialog(
            stops = today.ifEmpty { stops },
            onDismiss = { showRoute = false },
            onNavigate = { vm.navigateToStop(it); showRoute = false },
            onDelete = { vm.removeTripStop(it) },
            onMarkNext = { vm.markTripStopNext(it) }
        )
    }
    if (askWhere) {
        NavigateChooserSheet(
            onDismiss = { askWhere = false },
            onCategory = { cat ->
                askWhere = false
                addCategory = cat
                addStop = true
            },
            onSearch = {
                askWhere = false
                vm.openSearch()
            }
        )
    }
}

@Composable
private fun NavigateChooserSheet(
    onDismiss: () -> Unit,
    onCategory: (TripStopCategory) -> Unit,
    onSearch: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Where to?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(
                    TripStopCategory.Hotel to Icons.Outlined.Hotel,
                    TripStopCategory.Airport to Icons.Outlined.Flight,
                    TripStopCategory.Restaurant to Icons.Outlined.Restaurant,
                    TripStopCategory.Attraction to Icons.Outlined.Museum,
                    TripStopCategory.Saved to Icons.Outlined.Place
                ).forEach { (cat, icon) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onCategory(cat) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(icon, null, tint = TripPurple, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(cat.title, color = Lumen.Text, fontFamily = Outfit, fontSize = 15.sp)
                    }
                }
                TextButton(onClick = onSearch) { Text("Search in Maps…") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun TodaysRouteDialog(
    stops: List<TripStop>,
    onDismiss: () -> Unit,
    onNavigate: (TripStop) -> Unit,
    onDelete: (TripStop) -> Unit,
    onMarkNext: (TripStop) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Today's route") },
        text = {
            if (stops.isEmpty()) {
                Text("No stops yet. Add a place to build your route.")
            } else {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(stops, key = { it.id }) { stop ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onNavigate(stop) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                categoryIcon(stop.category),
                                null,
                                tint = TripPurple,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(stop.place, color = Lumen.Text, fontFamily = Outfit, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(stopDetailLine(stop), color = Lumen.Muted, fontSize = 11.sp)
                            }
                            IconButton(onClick = { onMarkNext(stop) }) {
                                Icon(Icons.Outlined.Map, "Mark next", tint = if (stop.markedNext) TripPurple else Lumen.Muted, modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { onDelete(stop) }) {
                                Icon(Icons.Outlined.Delete, "Remove", tint = Lumen.Muted, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

@Composable
internal fun AddTripStopDialog(
    onDismiss: () -> Unit,
    onSave: (TripStop) -> Unit,
    initialCategory: TripStopCategory = TripStopCategory.Other
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var place by remember { mutableStateOf("") }
    var category by remember(initialCategory) { mutableStateOf(initialCategory) }
    var hour by remember { mutableStateOf(14) }
    var minute by remember { mutableStateOf(0) }
    var useTime by remember { mutableStateOf(false) }
    var dayOffset by remember { mutableStateOf(0) } // 0 today, 1 tomorrow
    var lat by remember { mutableStateOf<Double?>(null) }
    var lng by remember { mutableStateOf<Double?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val ok = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (ok) {
            scope.launch {
                busy = true
                runCatching { fillFromCurrentLocation(context) }
                    .onSuccess { filled ->
                        place = filled.place
                        lat = filled.lat
                        lng = filled.lng
                        if (category == TripStopCategory.Other) category = TripStopCategory.Saved
                    }
                    .onFailure { error = "Could not read current location." }
                busy = false
            }
        } else {
            error = "Location permission is needed for current place."
        }
    }

    fun save() {
        val name = place.trim()
        if (name.isBlank()) {
            error = "Enter a place or address."
            return
        }
        val at = if (useTime) {
            Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, dayOffset)
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        } else if (dayOffset != 0) {
            Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, dayOffset)
                set(Calendar.HOUR_OF_DAY, 12)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        } else null
        onSave(
            TripStop(
                place = name,
                query = name,
                category = category,
                at = at,
                latitude = lat,
                longitude = lng
            )
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add stop") },
        text = {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = place,
                    onValueChange = { place = it },
                    label = { Text("Place / address") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = {
                        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                            PackageManager.PERMISSION_GRANTED ||
                            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                            PackageManager.PERMISSION_GRANTED
                        if (granted) {
                            scope.launch {
                                busy = true
                                runCatching { fillFromCurrentLocation(context) }
                                    .onSuccess { filled ->
                                        place = filled.place
                                        lat = filled.lat
                                        lng = filled.lng
                                        if (category == TripStopCategory.Other) category = TripStopCategory.Saved
                                    }
                                    .onFailure { error = "Could not read current location." }
                                busy = false
                            }
                        } else {
                            locationPermission.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    },
                    enabled = !busy
                ) {
                    Icon(Icons.Outlined.MyLocation, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (busy) "Locating…" else "Use current location")
                }
                Text("Category", color = Lumen.Muted, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TripStopCategory.entries.filter { it != TripStopCategory.Other }.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat.title, fontSize = 11.sp) }
                        )
                    }
                }
                Text("When", color = Lumen.Muted, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = dayOffset == 0, onClick = { dayOffset = 0 }, label = { Text("Today") })
                    FilterChip(selected = dayOffset == 1, onClick = { dayOffset = 1 }, label = { Text("Tomorrow") })
                    FilterChip(selected = useTime, onClick = { useTime = !useTime }, label = { Text(if (useTime) "Time on" else "No time") })
                }
                if (useTime) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = hour.toString().padStart(2, '0'),
                            onValueChange = { v -> hour = v.filter { it.isDigit() }.take(2).toIntOrNull()?.coerceIn(0, 23) ?: hour },
                            label = { Text("Hour") },
                            modifier = Modifier.width(88.dp),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = minute.toString().padStart(2, '0'),
                            onValueChange = { v -> minute = v.filter { it.isDigit() }.take(2).toIntOrNull()?.coerceIn(0, 59) ?: minute },
                            label = { Text("Min") },
                            modifier = Modifier.width(88.dp),
                            singleLine = true
                        )
                    }
                }
                error?.let { Text(it, color = Color(0xFFFF8A8A), fontSize = 12.sp) }
            }
        },
        confirmButton = { TextButton(onClick = ::save) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private data class FilledPlace(val place: String, val lat: Double, val lng: Double)

@SuppressLint("MissingPermission")
private suspend fun fillFromCurrentLocation(context: android.content.Context): FilledPlace {
    val client = LocationServices.getFusedLocationProviderClient(context)
    val token = CancellationTokenSource()
    val loc = try {
        client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, token.token).await()
            ?: client.lastLocation.await()
    } finally {
        token.cancel()
    } ?: error("no location")
    val label = withContext(Dispatchers.IO) {
        runCatching {
            if (!Geocoder.isPresent()) return@runCatching null
            @Suppress("DEPRECATION")
            Geocoder(context, Locale.getDefault())
                .getFromLocation(loc.latitude, loc.longitude, 1)
                ?.firstOrNull()
                ?.let { a ->
                    a.featureName?.takeIf { it.isNotBlank() && it != a.thoroughfare }
                        ?: listOfNotNull(a.thoroughfare, a.subLocality, a.locality)
                            .firstOrNull { it.isNotBlank() }
                        ?: a.getAddressLine(0)
                }
        }.getOrNull()
    } ?: "Current location"
    return FilledPlace(label, loc.latitude, loc.longitude)
}

private fun categoryIcon(category: TripStopCategory): ImageVector = when (category) {
    TripStopCategory.Airport -> Icons.Outlined.Flight
    TripStopCategory.Hotel -> Icons.Outlined.Hotel
    TripStopCategory.Restaurant -> Icons.Outlined.Restaurant
    TripStopCategory.Attraction -> Icons.Outlined.Museum
    TripStopCategory.Saved, TripStopCategory.Other -> Icons.Outlined.Place
}

private fun shortLabel(stop: TripStop): String =
    stop.place.substringBefore(",").trim().ifBlank { stop.place }

private fun stopDetailLine(stop: TripStop): String {
    val bits = mutableListOf<String>()
    bits += stop.category.title
    stop.at?.let {
        bits += DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it))
        if (!TripStopNavigator.isSameDay(it, System.currentTimeMillis())) {
            bits += DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it))
        }
    }
    return bits.joinToString(" · ")
}
