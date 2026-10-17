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
        TravelDashboardContent(
            state = state, vm = vm, tripState = tripState, photos = photos,
            destination = destination, start = if (planned) start else null, end = if (planned) end else null,
            onToggle = { enabled ->
                if (!enabled) prefs.edit().putBoolean("attempted", true).apply()
                toggle(enabled)
            },
            onEdit = { edit = true },
            onOpenTrip = { if (active != null) openTrip(active) else edit = true }
        )

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
