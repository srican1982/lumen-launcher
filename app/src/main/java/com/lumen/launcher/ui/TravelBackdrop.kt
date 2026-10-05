package com.lumen.launcher.ui
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.lumen.launcher.R
@Composable
internal fun TravelBackdrop() {
    Box(Modifier.fillMaxSize()) {
        Image(painterResource(R.drawable.travel_evening_valley), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x333A132A), Color(0x220C1424), Color(0x5507101D)))))
    }
}
