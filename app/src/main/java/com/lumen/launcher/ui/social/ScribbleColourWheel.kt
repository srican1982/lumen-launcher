package com.lumen.launcher.ui.social

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlin.math.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScribbleColourWheel(initial: Color,onDismiss: () -> Unit,onSelect: (Color) -> Unit) {
    val prefs=LocalContext.current.getSharedPreferences("scribble_colours",0)
    val hsv=remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initial.toArgb(),it) } }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var saturation by remember { mutableFloatStateOf(hsv[1]) }
    var brightness by remember { mutableFloatStateOf(hsv[2]) }
    var history by remember { mutableStateOf(prefs.getString("recent","").orEmpty().split(",").mapNotNull { it.toIntOrNull() }) }
    val chosen=Color(android.graphics.Color.HSVToColor(floatArrayOf(hue,saturation,brightness)))
    AlertDialog(onDismissRequest=onDismiss,containerColor=Color(0xFF19182C),title={Text("Colours",color=Color.White)},text={
        Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Text("Choose your colour",color=Color.LightGray)
                Box(Modifier.size(30.dp).background(chosen,CircleShape))
            }
            Canvas(Modifier.fillMaxWidth().aspectRatio(1f)
                .pointerInput(Unit) { detectTapGestures { p ->
                    val dx=p.x-size.width/2f;val dy=p.y-size.height/2f
                    hue=((atan2(dy,dx)*180f/PI.toFloat())+360f)%360f
                    saturation=(sqrt(dx*dx+dy*dy)/(size.width/2f)).coerceIn(0f,1f)
                } }
                .pointerInput(Unit) { detectDragGestures { change,_ ->
                    change.consume()
                    val dx=change.position.x-size.width/2f;val dy=change.position.y-size.height/2f
                    hue=((atan2(dy,dx)*180f/PI.toFloat())+360f)%360f
                    saturation=(sqrt(dx*dx+dy*dy)/(size.width/2f)).coerceIn(0f,1f)
                } }) {
                val radius=size.minDimension/2
                drawCircle(Brush.sweepGradient(listOf(Color.Red,Color.Yellow,Color.Green,Color.Cyan,Color.Blue,Color.Magenta,Color.Red)))
                drawCircle(Brush.radialGradient(listOf(Color.White,Color.Transparent),radius=radius))
                drawCircle(Color.Black.copy(alpha=1-brightness))
                val angle=hue*PI.toFloat()/180f
                val marker=center+Offset(cos(angle),sin(angle))*(saturation*(radius-5.dp.toPx()))
                drawCircle(Color.Black,6.dp.toPx(),marker)
                drawCircle(Color.White,5.dp.toPx(),marker,style=Stroke(2.dp.toPx()))
            }
            Text("Brightness",color=Color.LightGray,modifier=Modifier.align(Alignment.Start))
            Slider(value=brightness,onValueChange={brightness=it},thumb={Box(Modifier.size(22.dp).background(Color.White,CircleShape))})
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                Text("Recent colours",color=Color.LightGray)
                TextButton(onClick={history=emptyList();prefs.edit().remove("recent").apply()}) {Text("Clear")}
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                history.take(7).forEach { argb -> Box(Modifier.size(26.dp).background(Color(argb),CircleShape).clickable {
                    val v=FloatArray(3);android.graphics.Color.colorToHSV(argb,v);hue=v[0];saturation=v[1];brightness=v[2]
                }) }
            }
        }
    },confirmButton={TextButton(onClick={prefs.edit().putString("recent",(listOf(chosen.toArgb())+history).distinct().take(7).joinToString(",")).apply();onSelect(chosen)}) {Text("Use colour")}},dismissButton={TextButton(onClick=onDismiss) {Text("Cancel")}})
}
