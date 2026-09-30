package com.lumen.launcher.travel

import android.net.Uri
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.lumen.launcher.travel.model.Trip
import com.lumen.launcher.ui.theme.Lumen
import com.lumen.launcher.ui.theme.Outfit
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TripAlbumScreen(
    trip: Trip,
    onClose: () -> Unit,
    tripVm: TripViewModel = viewModel()
) {
    val photos by tripVm.photos(trip.id).collectAsState()
    val cities by tripVm.cityBuckets(trip.id).collectAsState()
    var filter by remember { mutableStateOf("All") }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    val filtered = remember(photos, filter) {
        when (filter) {
            "All" -> photos
            "Other" -> photos.filter { it.city.isNullOrBlank() }
            else -> photos.filter { it.city.equals(filter, ignoreCase = true) }
        }
    }
    val selecting = selected.isNotEmpty()

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFF1A1922)) {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = Lumen.Text)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(trip.displayTitle, color = Lumen.Text, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
                        Text(
                            formatRange(trip.startTime, trip.endTime) + " · " +
                                if (photos.size == 1) "1 photo" else "${photos.size} photos",
                            color = Lumen.Muted,
                            fontFamily = Outfit,
                            fontSize = 12.sp
                        )
                    }
                    if (selecting) {
                        TextButton(onClick = { selected = emptySet() }) { Text("Clear") }
                    }
                }

                if (cities.isNotEmpty()) {
                    Text("Cities", color = Lumen.Muted, fontFamily = Outfit, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                        items(cities) { bucket ->
                            Text(
                                "${bucket.city} · ${bucket.count}",
                                color = Lumen.Text.copy(alpha = 0.85f),
                                fontFamily = Outfit,
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(1.dp, Color.White.copy(0.18f), RoundedCornerShape(12.dp))
                                    .clickable { filter = bucket.city }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                    val chips = listOf("All") + cities.map { it.city }.distinct()
                    items(chips) { chip ->
                        FilterChip(
                            selected = filter == chip,
                            onClick = { filter = chip },
                            label = { Text(chip) }
                        )
                    }
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(bottom = 12.dp)
                ) {
                    items(filtered, key = { it.id }) { photo ->
                        val isSelected = photo.id in selected
                        Box(
                            Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    selected = if (isSelected) selected - photo.id else selected + photo.id
                                }
                        ) {
                            AsyncImage(
                                model = Uri.parse(photo.contentUri),
                                contentDescription = photo.displayName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            if (isSelected) {
                                Icon(
                                    Icons.Outlined.CheckCircle,
                                    null,
                                    tint = Color(0xFFE8D5A3),
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(6.dp)
                                        .size(22.dp)
                                )
                            }
                        }
                    }
                }

                if (selecting) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val uris = filtered.filter { it.id in selected }.map { Uri.parse(it.contentUri) }
                                tripVm.share(uris)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Outlined.Share, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("Share (${selected.size})")
                        }
                        if (tripVm.instagramInstalled()) {
                            OutlinedButton(
                                onClick = {
                                    val uris = filtered.filter { it.id in selected }.map { Uri.parse(it.contentUri) }
                                    tripVm.share(uris, instagramOnly = true)
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("Instagram") }
                        }
                    }
                } else {
                    Text(
                        "Tap photos to select, then share.",
                        color = Lumen.Faint,
                        fontFamily = Outfit,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                }
            }
        }
    }
}

private fun formatRange(start: Long, end: Long?): String {
    val fmt = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault())
    val a = fmt.format(Date(start))
    return if (end == null) "$a – Present" else "$a – ${fmt.format(Date(end))}"
}
