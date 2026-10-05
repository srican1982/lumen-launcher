package com.lumen.launcher.ui.focus

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.launcher.R
import com.lumen.launcher.focus.*

@Composable
internal fun FocusActiveSoundCard() {
    val context = LocalContext.current
    val player = remember { FocusSoundPlayer.get(context) }
    var changing by remember { mutableStateOf(false) }
    var sound by remember { mutableStateOf(FocusSoundPrefs.sound(context)) }
    var volume by remember { mutableFloatStateOf(FocusSoundPrefs.volume(context)) }
    var playing by remember { mutableStateOf(player.isPlaying()) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Focus sound: ${sound.title}", Modifier.weight(1f), color = Color.White, fontSize = 14.sp)
        TextButton(onClick = { changing = true }) { Text("Change sounds", color = FocusAccent) }
    }
    FocusGlass {
        Box(Modifier.fillMaxWidth().height(64.dp)) {
            val art = when(sound) {
                FocusSound.Forest -> R.drawable.focus_sound_forest
                FocusSound.Ocean -> R.drawable.focus_sound_ocean
                FocusSound.Flute -> R.drawable.focus_sound_flute
                else -> R.drawable.focus_rain_lake
            }
            Image(painterResource(art), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(FocusInk.copy(alpha = .9f), Color.Transparent))))
            Column(Modifier.align(Alignment.CenterStart).padding(8.dp)) {
                Text(sound.title, color = Color.White, fontSize = 16.sp)
                Text("A calmer mind. A brighter tomorrow.", color = FocusMuted, fontSize = 11.sp)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(enabled = sound != FocusSound.Off, onClick = {
                if (player.isPlaying()) player.pauseSession() else player.resumeSession()
                playing = player.isPlaying()
            }) { Text(if (playing) "Pause sound" else "Play sound", color = FocusAccent, fontSize = 11.sp) }
            Slider(volume, { volume = it; player.setVolume(it) }, Modifier.weight(1f), valueRange = .05f..1f,
                colors = SliderDefaults.colors(thumbColor = FocusAccent, activeTrackColor = FocusAccent))
            Text("${(volume * 100).toInt()}%", color = FocusMuted, fontSize = 11.sp)
        }
    }
    if (changing) FocusPopupSheet(onDismiss = {
        changing = false; sound = FocusSoundPrefs.sound(context); volume = player.volume(); playing = player.isPlaying()
    }, heightFraction = .6f) {
        FocusSoundsSection()
        FocusPrimaryAction("Done", action = { changing = false; sound = FocusSoundPrefs.sound(context); volume = player.volume(); playing = player.isPlaying() })
    }
}
