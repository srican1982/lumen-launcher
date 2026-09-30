package com.lumen.launcher.ui.social

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lumen.launcher.ui.rememberNavBottomPadding
import kotlin.math.roundToInt

/** Crops a rendered copy so existing drawing and blur edits remain aligned. */
@Composable
internal fun PhotoCropDialog(source: Bitmap, onDismiss: () -> Unit, onApply: (Bitmap) -> Unit) {
    var crop by remember(source) { mutableStateOf(Rect(0f, 0f, 1f, 1f)) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val image = remember(source) { source.asImageBitmap() }
    val scale = minOf(size.width.toFloat() / source.width, size.height.toFloat() / source.height)
    val imageSize = Size(source.width * scale, source.height * scale)
    val origin = Offset((size.width - imageSize.width) / 2, (size.height - imageSize.height) / 2)
    val navBottom = rememberNavBottomPadding(56.dp)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFF171426)) {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp)
                    .padding(top = 16.dp, bottom = navBottom + 36.dp)
            ) {
                Text("Crop photo", style = MaterialTheme.typography.headlineSmall, color = Color.White)
                Text("Drag a corner to resize, or drag inside to move. Your original stays unchanged.", color = Color.LightGray)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Reset" to 0f, "Square" to 1f, "4:3" to 4f/3, "16:9" to 16f/9).forEach { (label, ratio) ->
                        TextButton(onClick = {
                            val aspect = source.width.toFloat() / source.height
                            val w = if (ratio == 0f) 1f else minOf(1f, ratio / aspect)
                            val h = if (ratio == 0f) 1f else minOf(1f, aspect / ratio)
                            crop = Rect((1-w)/2, (1-h)/2, (1+w)/2, (1+h)/2)
                        }) { Text(label) }
                    }
                }
                Canvas(Modifier.weight(1f).fillMaxWidth().onSizeChanged { size = it }
                    .pointerInput(size, source) {
                        var corner = -1
                        var before = crop
                        var start = Offset.Zero
                        detectDragGestures(onDragStart = { p ->
                            if (imageSize.width <= 0 || imageSize.height <= 0) return@detectDragGestures
                            before = crop
                            start = p
                            val corners = listOf(crop.topLeft, crop.topRight, crop.bottomRight, crop.bottomLeft)
                            corner = corners.indices.minByOrNull { i ->
                                (origin + Offset(corners[i].x * imageSize.width, corners[i].y * imageSize.height) - p).getDistance()
                            } ?: -1
                            val nearest = corners[corner]
                            if ((origin + Offset(nearest.x * imageSize.width, nearest.y * imageSize.height) - p).getDistance() > 48.dp.toPx()) corner = -1
                        }, onDrag = { change, _ ->
                            change.consume()
                            if (imageSize.width > 0 && imageSize.height > 0) {
                                val d = change.position - start
                                val dx = d.x / imageSize.width
                                val dy = d.y / imageSize.height
                                val minWidth = minOf(.05f, before.width)
                                val minHeight = minOf(.05f, before.height)
                                crop = when (corner) {
                                    0 -> Rect((before.left+dx).coerceIn(0f,before.right-minWidth), (before.top+dy).coerceIn(0f,before.bottom-minHeight), before.right,before.bottom)
                                    1 -> Rect(before.left,(before.top+dy).coerceIn(0f,before.bottom-minHeight),(before.right+dx).coerceIn(before.left+minWidth,1f),before.bottom)
                                    2 -> Rect(before.left,before.top,(before.right+dx).coerceIn(before.left+minWidth,1f),(before.bottom+dy).coerceIn(before.top+minHeight,1f))
                                    3 -> Rect((before.left+dx).coerceIn(0f,before.right-minWidth),before.top,before.right,(before.bottom+dy).coerceIn(before.top+minHeight,1f))
                                    else -> before.translate(Offset(dx.coerceIn(-before.left,1-before.right),dy.coerceIn(-before.top,1-before.bottom)))
                                }
                            }
                        })
                    }) {
                    if (imageSize.width <= 0 || imageSize.height <= 0) return@Canvas
                    drawImage(image, dstOffset = IntOffset(origin.x.roundToInt(),origin.y.roundToInt()), dstSize = IntSize(imageSize.width.roundToInt(),imageSize.height.roundToInt()))
                    val r = Rect(origin.x+crop.left*imageSize.width,origin.y+crop.top*imageSize.height,origin.x+crop.right*imageSize.width,origin.y+crop.bottom*imageSize.height)
                    val shade = Color.Black.copy(alpha=.6f)
                    drawRect(shade, origin, Size(imageSize.width,r.top-origin.y))
                    drawRect(shade, Offset(origin.x,r.bottom), Size(imageSize.width,origin.y+imageSize.height-r.bottom))
                    drawRect(shade, Offset(origin.x,r.top), Size(r.left-origin.x,r.height))
                    drawRect(shade, Offset(r.right,r.top), Size(origin.x+imageSize.width-r.right,r.height))
                    drawRect(Color.White,r.topLeft,r.size,style=Stroke(2.dp.toPx()))
                    for (i in 1..2) {
                        drawLine(Color.White.copy(alpha=.5f),Offset(r.left+r.width*i/3,r.top),Offset(r.left+r.width*i/3,r.bottom))
                        drawLine(Color.White.copy(alpha=.5f),Offset(r.left,r.top+r.height*i/3),Offset(r.right,r.top+r.height*i/3))
                    }
                    listOf(r.topLeft,r.topRight,r.bottomLeft,r.bottomRight).forEach { drawCircle(Color.White,6.dp.toPx(),it) }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(onClick = {
                        val left = (crop.left*source.width).roundToInt().coerceIn(0,source.width-1)
                        val top = (crop.top*source.height).roundToInt().coerceIn(0,source.height-1)
                        val right = (crop.right*source.width).roundToInt().coerceIn(left+1,source.width)
                        val bottom = (crop.bottom*source.height).roundToInt().coerceIn(top+1,source.height)
                        onApply(Bitmap.createBitmap(source,left,top,right-left,bottom-top))
                    }) { Text("Apply crop") }
                }
            }
        }
    }
}
