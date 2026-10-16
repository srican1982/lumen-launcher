package com.lumen.launcher.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FlightTakeoff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.lumen.launcher.data.TravelCategory
import com.lumen.launcher.travel.TripModeCard
import com.lumen.launcher.travel.TripViewModel
import com.lumen.launcher.ui.theme.Lumen
import com.lumen.launcher.ui.theme.Outfit
import com.lumen.launcher.vm.LauncherUiState
import com.lumen.launcher.vm.LauncherViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf

/** Trip planning is explicitly entered locally; attached files are never treated as parsed flight data. */
@Composable
internal fun TravelHeroPanel(state: LauncherUiState, vm: LauncherViewModel, tripVm: TripViewModel = viewModel()) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("lumen_planned_trip", Context.MODE_PRIVATE) }
    var revision by remember { mutableIntStateOf(0) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> revision++ }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    var edit by remember { mutableStateOf(false) }
    var collection by remember { mutableStateOf<TravelCategory?>(null) }
    var today by remember { mutableStateOf(LocalDate.now()) }
    LaunchedEffect(Unit) { while (true) { today = LocalDate.now(); delay(60_000) } }
    val start = remember(revision) { runCatching { LocalDate.parse(prefs.getString("start", "")) }.getOrNull() }
    val end = remember(revision) { runCatching { LocalDate.parse(prefs.getString("end", "")) }.getOrNull() }
    val planned = start != null && end != null && !today.isAfter(end)
    val destination = remember(revision) { prefs.getString("destination", "").orEmpty() }
    TripModeCard(tripVm = tripVm, heroContent = { tripState, toggle, openTrip ->
        val active = tripState.activeTrip
        val photos by remember(active?.id) {
            active?.let { tripVm.photos(it.id) } ?: flowOf(emptyList())
        }.collectAsStateWithLifecycle(initialValue = emptyList())
        val todaysPhotos = remember(photos, today) { photos.filter {
            Instant.ofEpochMilli(it.dateTaken).atZone(ZoneId.systemDefault()).toLocalDate() == today
        } }
        LaunchedEffect(today, start, end, revision, tripState.isLoading, active?.id) {
            // Foreground activation respects the existing permission flow and can be overridden.
            if (planned && !today.isBefore(start) && active == null && !tripState.isLoading &&
                prefs.getBoolean("automatic", false) && !prefs.getBoolean("attempted", false)) {
                prefs.edit().putBoolean("attempted", true).apply()
                toggle(true)
            }
        }
        val shape = RoundedCornerShape(26.dp)
        Column(Modifier.fillMaxWidth().clip(shape).background(Color.White.copy(.09f))
            .border(.8.dp, Color.White.copy(.23f), shape).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    val days = start?.let { ChronoUnit.DAYS.between(today, it) }
                    Text(when {
                        active != null -> "ON TRIP"
                        planned && days != null && days > 0 -> "NEXT TRIP - IN $days DAYS"
                        planned -> "YOUR TRIP IS READY"
                        else -> "YOUR NEXT CHAPTER"
                    }, color = Lumen.Muted, fontFamily = Outfit, fontSize = 10.sp, letterSpacing = 1.4.sp)
                    Text(when {
                        active != null -> {
                            val day = ChronoUnit.DAYS.between(Instant.ofEpochMilli(active.startTime)
                                .atZone(ZoneId.systemDefault()).toLocalDate(), today).coerceAtLeast(0) + 1
                            "Day $day - ${active.primaryCity ?: destination.ifBlank { active.displayTitle }}"
                        }
                        planned -> destination
                        else -> "Where to next?"
                    }, color = Lumen.Text, fontFamily = Outfit, fontSize = 23.sp,
                        fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { if (active != null) openTrip(active) else edit = true })
                    if (planned) Text(
                        "${start.format(DateTimeFormatter.ofPattern("MMM d"))} - ${end.format(DateTimeFormatter.ofPattern("MMM d"))}",
                        color = Lumen.Muted, fontFamily = Outfit, fontSize = 12.sp)
                }
                Switch(checked = tripState.tripModeEnabled, enabled = !tripState.isLoading,
                    onCheckedChange = { enabled ->
                        if (!enabled) prefs.edit().putBoolean("attempted", true).apply()
                        toggle(enabled)
                    }, colors = SwitchDefaults.colors(checkedTrackColor = Lumen.BrandGold,
                        checkedThumbColor = Lumen.OnBrandGold, checkedBorderColor = Color.Transparent,
                        uncheckedTrackColor = Color.White.copy(.15f)))
            }
            when {
                active != null -> {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        todaysPhotos.take(3).forEach { photo ->
                            AsyncImage(photo.contentUri, "Photo from today", contentScale = ContentScale.Crop,
                                modifier = Modifier.weight(1f).height(68.dp).clip(RoundedCornerShape(12.dp))
                                    .clickable { openTrip(active) })
                        }
                    }
                    Text(if (todaysPhotos.isEmpty()) "Today's memories will appear here." else
                        "${todaysPhotos.size} photos today" + todaysPhotos.mapNotNull { it.city }.distinct()
                            .takeIf { it.isNotEmpty() }?.joinToString(prefix = " - ").orEmpty(),
                        color = Lumen.Muted, fontFamily = Outfit, fontSize = 12.sp)
                    val next = state.travelStops.filter { it.at == null || it.at >= System.currentTimeMillis() }
                        .sortedWith(compareByDescending<com.lumen.launcher.data.TripStop> { it.markedNext }.thenBy { it.at ?: Long.MAX_VALUE })
                        .firstOrNull()
                    Text(next?.let { "Next: ${it.place}" + (it.at?.let { at -> " - " + Instant.ofEpochMilli(at)
                        .atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("h:mm a")) } ?: "") }
                        ?: "Add your next plan to Itinerary", color = Lumen.Text, fontFamily = Outfit, fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth().clickable { collection = TravelCategory.Itinerary }.padding(vertical = 6.dp))
                }
                planned -> {
                    BoardingStub(prefs, onClick = { edit = true })
                    Text(if (prefs.getBoolean("automatic", false))
                        "Trip mode starts when you open Travel on or after ${start!!.format(DateTimeFormatter.ofPattern("MMM d"))}. The switch is your override."
                        else "Turn on Trip mode when you're ready.", color = Lumen.Muted, fontFamily = Outfit, fontSize = 11.sp)
                }
                else -> {
                    Text("Keep tickets, plans and trip albums together. Photos stay on your device; location is used only while Trip mode is on.",
                        color = Lumen.Muted, fontFamily = Outfit, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { edit = true }, modifier = Modifier.weight(1f)) { Text("Add trip", color = Lumen.BrandGold) }
                        OutlinedButton(onClick = { collection = TravelCategory.Flights }, modifier = Modifier.weight(1f)) { Text("Boarding pass", color = Lumen.Text) }
                    }
                }
            }
            if (planned || active != null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Edit trip", color = Lumen.BrandGold, fontFamily = Outfit, fontSize = 11.sp,
                    modifier = Modifier.clickable { edit = true }.padding(vertical = 6.dp))
                Text("Boarding pass", color = Lumen.BrandGold, fontFamily = Outfit, fontSize = 11.sp,
                    modifier = Modifier.clickable { collection = TravelCategory.Flights }.padding(vertical = 6.dp))
            }
        }
    })
    collection?.let { TravelCollection(state, vm, it) { collection = null } }
    if (edit) TripPlanDialog(prefs, onDismiss = { edit = false })
}

@Composable
private fun BoardingStub(prefs: SharedPreferences, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(Modifier.fillMaxWidth().clip(shape).background(Lumen.BrandGold.copy(.06f))
        .border(1.dp, Lumen.BrandGold.copy(.6f), shape).clickable(onClick = onClick)
        .height(IntrinsicSize.Min).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(prefs.getString("from", "").orEmpty().ifBlank { "From" }, color = Lumen.Text, fontFamily = Outfit, fontSize = 22.sp)
                Icon(Icons.Outlined.FlightTakeoff, null, tint = Lumen.BrandGold, modifier = Modifier.padding(horizontal = 8.dp).size(18.dp))
                Text(prefs.getString("to", "").orEmpty().ifBlank { "To" }, color = Lumen.Text, fontFamily = Outfit, fontSize = 22.sp)
            }
            Text(listOf(prefs.getString("flight", "").orEmpty(), prefs.getString("time", "").orEmpty())
                .filter { it.isNotBlank() }.joinToString(" - ").ifBlank { "Add flight details from your pass" },
                color = Lumen.Muted, fontFamily = Outfit, fontSize = 11.sp)
        }
        Canvas(Modifier.padding(horizontal = 10.dp).width(1.dp).fillMaxHeight()) {
            drawLine(Lumen.BrandGold.copy(.7f), Offset(size.width / 2, 0f), Offset(size.width / 2, size.height),
                strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("SEAT", color = Lumen.Muted, fontFamily = Outfit, fontSize = 9.sp)
            Text(prefs.getString("seat", "").orEmpty().ifBlank { "--" }, color = Lumen.BrandGold,
                fontFamily = Outfit, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun TripPlanDialog(prefs: SharedPreferences, onDismiss: () -> Unit) {
    val fields = remember { mutableStateMapOf<String, String>().apply {
        listOf("destination", "start", "end", "from", "to", "flight", "time", "seat").forEach {
            put(it, prefs.getString(it, "").orEmpty())
        }
    } }
    var automatic by remember { mutableStateOf(prefs.getBoolean("automatic", false)) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Your trip") },
        text = { Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("destination" to "Destination", "start" to "Start date (YYYY-MM-DD)", "end" to "End date (YYYY-MM-DD)",
                "from" to "From airport (optional)", "to" to "To airport (optional)", "flight" to "Flight (optional)",
                "time" to "Local departure time (HH:mm, optional)", "seat" to "Seat (optional)").forEach { (key, label) ->
                OutlinedTextField(fields.getValue(key), { fields[key] = it }, label = { Text(label) }, singleLine = true)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(automatic, { automatic = it })
                Text("Start on the trip date when Travel is open")
            }
            Text("Enter the details printed on your pass. File import does not extract flight details yet.")
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { TextButton(onClick = {
            val start = runCatching { LocalDate.parse(fields.getValue("start")) }.getOrNull()
            val end = runCatching { LocalDate.parse(fields.getValue("end")) }.getOrNull()
            when {
                fields.getValue("destination").isBlank() -> error = "Enter a destination."
                start == null || end == null || end < start -> error = "Enter valid dates, with the end on or after the start."
                fields.getValue("time").isNotBlank() && runCatching { LocalTime.parse(fields.getValue("time")) }.isFailure -> error = "Use HH:mm for the local departure time."
                listOf("from", "to").any { fields.getValue(it).isNotBlank() && !fields.getValue(it).trim().matches(Regex("[A-Za-z]{3}")) } -> error = "Airport codes must have three letters."
                else -> {
                    val edit = prefs.edit()
                    fields.forEach { (key, value) -> edit.putString(key, if (key in listOf("from", "to", "flight", "seat")) value.trim().uppercase(java.util.Locale.ROOT) else value.trim()) }
                    edit.putBoolean("automatic", automatic).putBoolean("attempted", false).apply()
                    onDismiss()
                }
            }
        }) { Text("Save") } },
        dismissButton = { Row {
            if (prefs.contains("start")) TextButton(onClick = { prefs.edit().clear().apply(); onDismiss() }) { Text("Remove plan") }
            TextButton(onClick = onDismiss) { Text("Cancel") }
        } })
}
