package com.lumen.launcher.ui.focus

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.launcher.R
import com.lumen.launcher.focus.FocusSound
import com.lumen.launcher.focus.FocusSoundPlayer
import com.lumen.launcher.focus.FocusSoundPrefs
import com.lumen.launcher.ui.theme.Outfit
import kotlin.math.sin
import kotlinx.coroutines.delay

@Composable
internal fun FocusSoundsSection(dismissSignal: Int = 0, heading: String = "4. Focus sound") {
    val context = LocalContext.current
    val player = remember { FocusSoundPlayer.get(context) }
    var sound by remember { mutableStateOf(FocusSoundPrefs.sound(context)) }
    var volume by remember { mutableFloatStateOf(FocusSoundPrefs.volume(context)) }
    var previewing by remember { mutableStateOf(false) }

    LaunchedEffect(dismissSignal) {
        if (previewing) {
            player.stopPreview()
            previewing = false
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            player.stopPreview()
        }
    }
    LaunchedEffect(previewing, sound) {
        if (!previewing) return@LaunchedEffect
        delay(8_000)
        player.stopPreview()
        previewing = false
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                heading,
                color = Color.White,
                fontSize = 15.sp,
                fontFamily = Outfit,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Text(
                "optional",
                color = FocusMuted,
                fontSize = 12.sp,
                fontFamily = Outfit
            )
        }
        LazyRow(
            Modifier.fillMaxWidth().focusContainHorizontalScroll(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "off") {
                SoundCard(
                    title = "Off",
                    selected = sound == FocusSound.Off,
                    art = { OffArt() },
                    onClick = {
                        sound = FocusSound.Off
                        previewing = false
                        player.select(FocusSound.Off)
                    }
                )
            }
            items(FocusSound.selectable, key = { it.id }) { item ->
                SoundCard(
                    title = when (item) {
                        FocusSound.Flute -> "Flute"
                        FocusSound.BrownNoise -> "Brown"
                        else -> item.title
                    },
                    selected = sound == item,
                    art = { SoundArt(item) },
                    onClick = {
                        sound = item
                        previewing = true
                        player.setVolume(volume)
                        player.select(item, preview = true)
                    }
                )
            }
        }

        if (sound != FocusSound.Off) {
            val stripShape = RoundedCornerShape(18.dp)
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(stripShape)
                    .background(Color.White.copy(alpha = 0.06f))
                    .border(1.dp, Color.White.copy(alpha = 0.14f), stripShape)
                    .padding(horizontal = 10.dp, vertical = 0.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.VolumeUp, null, tint = FocusMuted, modifier = Modifier.size(18.dp))
                    Slider(
                        value = volume,
                        onValueChange = {
                            volume = it
                            player.setVolume(it)
                        },
                        valueRange = 0.05f..1f,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp).scale(scaleX = 1f, scaleY = 0.65f),
                        colors = SliderDefaults.colors(
                            thumbColor = FocusAccent,
                            activeTrackColor = FocusAccent,
                            inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                        )
                    )
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(FocusAccent.copy(alpha = 0.18f))
                            .border(1.dp, FocusAccent.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                            .clickable {
                                previewing = true
                                player.setVolume(volume)
                                player.select(sound, preview = true)
                            }
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Filled.PlayArrow, null, tint = FocusAccent, modifier = Modifier.size(16.dp))
                        Text("Play preview", color = FocusAccent, fontFamily = Outfit, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SoundCard(
    title: String,
    selected: Boolean,
    art: @Composable () -> Unit,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .width(68.dp)
            .then(
                if (selected) Modifier.shadow(10.dp, shape, ambientColor = FocusAccent.copy(0.4f), spotColor = FocusAccent.copy(0.5f))
                else Modifier
            )
            .clip(shape)
            .border(
                width = if (selected) 2.dp else 1.dp,
                brush = if (selected) Brush.linearGradient(listOf(FocusBorderGlow, FocusAccentEnd))
                else Brush.linearGradient(listOf(FocusBorder, FocusBorder)),
                shape = shape
            )
            .clickable(onClick = onClick)
    ) {
        Box(Modifier.height(46.dp).fillMaxWidth()) {
            art()
            if (selected) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(FocusAccent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(12.dp))
                }
            }
        }
        Text(
            title,
            color = Color.White,
            fontFamily = Outfit,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .fillMaxWidth()
                .background(FocusCard)
                .padding(horizontal = 8.dp, vertical = 8.dp)
        )
    }
}

@Composable
internal fun SoundArt(sound: FocusSound) {
    when (sound) {
        FocusSound.Flute -> {
            Image(
                painter = painterResource(R.drawable.focus_sound_flute),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.18f)))
        }
        FocusSound.Rain -> Image(painterResource(R.drawable.focus_rain_lake), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        FocusSound.Forest -> Image(painterResource(R.drawable.focus_sound_forest), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        FocusSound.Ocean -> Image(painterResource(R.drawable.focus_sound_ocean), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        FocusSound.Fireplace -> GradientArt(listOf(Color(0xFF6B2E14), Color(0xFF2A1208), Color(0xFFA84A1C)))
        FocusSound.BrownNoise -> GradientArt(listOf(Color(0xFF3A2A4A), Color(0xFF1A1224), Color(0xFF6E4C8A)))
        FocusSound.Off -> OffArt()
    }
}

@Composable
private fun OffArt() {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF1A1F2E), Color(0xFF0E121C)))),
        contentAlignment = Alignment.Center
    ) {
        Text("Off", color = FocusMuted, fontFamily = Outfit, fontSize = 13.sp)
    }
}

@Composable
private fun GradientArt(colors: List<Color>) {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(colors)))
}

@Composable
private fun MiniWaveform(modifier: Modifier = Modifier, active: Boolean) {
    Canvas(modifier) {
        val bars = 28
        val gap = 3.dp.toPx()
        val barW = (size.width - gap * (bars - 1)) / bars
        val mid = size.height / 2f
        for (i in 0 until bars) {
            val wave = (sin(i * 0.65f) * 0.35f + 0.55f)
            val h = size.height * wave * if (active) 1f else 0.55f
            drawRoundRect(
                color = if (active) Color(0xFF7E8DFF) else Color.White.copy(alpha = 0.22f),
                topLeft = Offset(i * (barW + gap), mid - h / 2f),
                size = androidx.compose.ui.geometry.Size(barW, h)
            )
        }
    }
}
