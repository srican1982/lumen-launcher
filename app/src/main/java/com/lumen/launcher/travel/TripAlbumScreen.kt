package com.lumen.launcher.travel

import android.app.Activity
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FolderDelete
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.lumen.launcher.travel.model.Trip
import com.lumen.launcher.travel.model.TripPhoto
import com.lumen.launcher.ui.theme.Lumen
import com.lumen.launcher.ui.theme.Outfit

private val TripPurple = Color(0xFFB794F6)
private val TripPurpleDeep = Color(0xFF9B6DEF)
private val SheetBg = Color(0xFF1C1A24)

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
    val context = LocalContext.current

    val filtered = remember(photos, filter) {
        when (filter) {
            "All" -> photos
            "Other" -> photos.filter { it.city.isNullOrBlank() }
            else -> photos.filter { it.city.equals(filter, ignoreCase = true) }
        }
    }
    val selecting = selected.isNotEmpty()
    val selectedPhotos = remember(filtered, selected) { filtered.filter { it.id in selected } }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            tripVm.removeFromTrip(selected.toList())
            selected = emptySet()
        }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFF14121A)) {
            Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
                ) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = Lumen.Text)
                    }
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                trip.displayTitle,
                                color = Lumen.Text,
                                fontFamily = Outfit,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 20.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(trip.flagEmoji, fontSize = 18.sp)
                        }
                        Text(
                            formatTripRange(trip.startTime, trip.endTime) + " · " +
                                if (photos.size == 1) "1 photo" else "${photos.size} photos",
                            color = Lumen.Muted,
                            fontFamily = Outfit,
                            fontSize = 12.sp
                        )
                    }
                }

                val place = trip.primaryCity ?: trip.citiesLabel?.substringBefore(" · ")
                if (!place.isNullOrBlank() || photos.isNotEmpty()) {
                    LocationSummaryCard(
                        city = place ?: "Trip photos",
                        count = if (filter == "All") photos.size else filtered.size
                    )
                    Spacer(Modifier.height(12.dp))
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    item {
                        FilterPill(
                            label = "All (${photos.size})",
                            selected = filter == "All",
                            onClick = { filter = "All" }
                        )
                    }
                    items(cities, key = { it.city }) { bucket ->
                        FilterPill(
                            label = "${bucket.city} (${bucket.count})",
                            selected = filter == bucket.city,
                            onClick = { filter = bucket.city }
                        )
                    }
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(bottom = if (selecting) 140.dp else 16.dp)
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
                                model = ImageRequest.Builder(context)
                                    .data(Uri.parse(photo.contentUri))
                                    .crossfade(false)
                                    .build(),
                                contentDescription = photo.displayName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            SelectionMark(
                                selected = isSelected,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(6.dp)
                            )
                        }
                    }
                }
            }

            if (selecting) {
                SelectionSheet(
                    photos = selectedPhotos,
                    onClear = { selected = emptySet() },
                    onShare = {
                        tripVm.share(selectedPhotos.map { Uri.parse(it.contentUri) })
                    },
                    onRemove = {
                        tripVm.removeFromTrip(selected.toList())
                        selected = emptySet()
                    },
                    onDelete = {
                        val uris = selectedPhotos.map { Uri.parse(it.contentUri) }
                        if (Build.VERSION.SDK_INT >= 30 && uris.isNotEmpty()) {
                            val request = MediaStore.createDeleteRequest(context.contentResolver, uris)
                            deleteLauncher.launch(IntentSenderRequest.Builder(request).build())
                        } else {
                            tripVm.removeFromTrip(selected.toList())
                            selected = emptySet()
                        }
                    },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
            }
        }
    }
}

@Composable
private fun LocationSummaryCard(city: String, count: Int) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White.copy(alpha = 0.07f))
            .border(0.8.dp, Color.White.copy(alpha = 0.12f), shape)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(TripPurple.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Place, null, tint = TripPurple, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(city, color = Lumen.Text, fontFamily = Outfit, fontWeight = FontWeight.Medium, fontSize = 15.sp)
            Text(
                if (count == 1) "1 photo" else "$count photos",
                color = Lumen.Muted,
                fontFamily = Outfit,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Text(
        label,
        color = if (selected) Color.White else Lumen.Text.copy(alpha = 0.85f),
        fontFamily = Outfit,
        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        fontSize = 13.sp,
        modifier = Modifier
            .clip(shape)
            .background(if (selected) TripPurpleDeep else Color.White.copy(alpha = 0.08f))
            .border(
                0.8.dp,
                if (selected) Color.Transparent else Color.White.copy(alpha = 0.12f),
                shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

@Composable
private fun SelectionMark(selected: Boolean, modifier: Modifier = Modifier) {
    if (selected) {
        Icon(
            Icons.Outlined.CheckCircle,
            null,
            tint = TripPurple,
            modifier = modifier.size(22.dp)
        )
    } else {
        Box(
            modifier
                .size(20.dp)
                .border(1.5.dp, Color.White.copy(alpha = 0.7f), CircleShape)
        )
    }
}

@Composable
private fun SelectionSheet(
    photos: List<TripPhoto>,
    onClear: () -> Unit,
    onShare: () -> Unit,
    onRemove: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(SheetBg)
            .border(0.8.dp, Color.White.copy(0.1f), shape)
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${photos.size} photos selected",
                color = Lumen.Text,
                fontFamily = Outfit,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onClear, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Outlined.Close, "Clear", tint = Lumen.Muted)
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(photos, key = { it.id }) { photo ->
                AsyncImage(
                    model = Uri.parse(photo.contentUri),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionTile(
                label = "Share",
                icon = { Icon(Icons.Outlined.Share, null, tint = Color.White) },
                background = TripPurpleDeep,
                onClick = onShare,
                modifier = Modifier.weight(1f)
            )
            ActionTile(
                label = "Remove",
                icon = { Icon(Icons.Outlined.FolderDelete, null, tint = Lumen.Text) },
                background = Color.White.copy(alpha = 0.08f),
                onClick = onRemove,
                modifier = Modifier.weight(1f)
            )
            ActionTile(
                label = "Delete",
                icon = { Icon(Icons.Outlined.Delete, null, tint = Color(0xFFFF8A8A)) },
                background = Color(0x33FF6B6B),
                onClick = onDelete,
                modifier = Modifier.weight(1f),
                labelColor = Color(0xFFFF8A8A)
            )
        }
    }
}

@Composable
private fun ActionTile(
    label: String,
    icon: @Composable () -> Unit,
    background: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    labelColor: Color = Lumen.Text
) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        icon()
        Text(label, color = labelColor, fontFamily = Outfit, fontSize = 12.sp)
    }
}
