package com.lumen.launcher.ui.social

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Redo
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.lumen.launcher.social.CreationKind
import com.lumen.launcher.social.SocialCreateCoordinator
import com.lumen.launcher.social.SocialCreateTool
import com.lumen.launcher.social.scribble.InkStroke
import com.lumen.launcher.social.scribble.ScribbleBrushStyle
import com.lumen.launcher.social.scribble.ScribbleRenderer
import com.lumen.launcher.ui.theme.Lumen
import com.lumen.launcher.ui.theme.Outfit

private data class StrokeLine(val path: Path, val color: Color, val width: Float, val erase: Boolean)

@Composable
fun SocialToolOverlay(
    tool: SocialCreateTool,
    coordinator: SocialCreateCoordinator,
    onClose: () -> Unit
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF100818))
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .zIndex(200f)
    ) {
        when (tool) {
            SocialCreateTool.Scribble -> ScribbleScreen(coordinator, onClose)
            SocialCreateTool.Quote -> QuoteScreen(coordinator, onClose)
            SocialCreateTool.Photo -> PhotoMarkupScreen(coordinator, onClose)
        }
    }
}

@Composable
private fun ToolTopBar(title: String, onClose: () -> Unit, onShare: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.Close, "Close", tint = Lumen.Text, modifier = Modifier
            .size(28.dp)
            .clickable(onClick = onClose))
        Text(
            title,
            color = Lumen.Text,
            fontFamily = Outfit,
            fontSize = 18.sp,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        )
        Icon(Icons.Outlined.Share, "Share", tint = Lumen.Accent, modifier = Modifier
            .size(26.dp)
            .clickable(onClick = onShare))
    }
}

@Composable
private fun ScribbleScreen(coordinator: SocialCreateCoordinator, onClose: () -> Unit) {
    val strokes = remember { mutableStateListOf<InkStroke>() }
    val undo = remember { mutableStateListOf<InkStroke>() }
    val livePoints = remember { mutableStateListOf<Offset>() }
    var penColor by remember { mutableStateOf(Color.White) }
    var erasing by remember { mutableStateOf(false) }
    var brushStyle by remember { mutableStateOf(ScribbleBrushStyle.Sketch) }
    var canvasSize by remember { mutableStateOf(IntSize(1080, 1080)) }
    var showExportSheet by remember { mutableStateOf(false) }
    val penColors = listOf(Color.White, Color.Black, Color(0xFFB794F4), Color(0xFFFFD56A))

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ToolTopBar("Scribble", onClose, { showExportSheet = true })
            Row(
                Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ScribbleBrushStyle.entries.forEach { s ->
                    Text(
                        s.name,
                        color = if (brushStyle == s) Lumen.OnAccent else Lumen.Muted,
                        fontSize = 11.sp,
                        fontFamily = Outfit,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .then(
                                if (brushStyle == s) {
                                    Modifier.background(Lumen.AccentFill, RoundedCornerShape(10.dp))
                                } else {
                                    Modifier.background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                                }
                            )
                            .clickable { brushStyle = s }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
            Row(
                Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Pen", color = if (!erasing) Lumen.Text else Lumen.Faint, modifier = Modifier.clickable { erasing = false })
                Text("Eraser", color = if (erasing) Lumen.Text else Lumen.Faint, modifier = Modifier.clickable { erasing = true })
                Spacer(Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Outlined.Undo, null, tint = Lumen.Text, modifier = Modifier
                    .size(22.dp)
                    .clickable {
                        if (strokes.isNotEmpty()) {
                            undo.add(strokes.removeAt(strokes.lastIndex))
                        }
                    })
                Icon(Icons.Outlined.Redo, null, tint = Lumen.Text, modifier = Modifier
                    .size(22.dp)
                    .clickable {
                        if (undo.isNotEmpty()) strokes.add(undo.removeAt(undo.lastIndex))
                    })
                Text("Clear", color = Lumen.Faint, modifier = Modifier.clickable {
                    strokes.clear()
                    undo.clear()
                })
            }
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                penColors.forEach { c ->
                    Box(
                        Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(c)
                            .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                            .clickable { penColor = c; erasing = false }
                    )
                }
            }
            ScribbleInkPad(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp),
                strokes = strokes,
                undo = undo,
                livePoints = livePoints,
                penColor = penColor,
                erasing = erasing,
                brushStyle = brushStyle,
                onSize = { canvasSize = it },
                onStrokeCommitted = { undo.clear() }
            )
        }

        if (showExportSheet) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.42f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { showExportSheet = false },
                contentAlignment = Alignment.BottomCenter
            ) {
                ScribbleExportSheet(
                    strokes = strokes,
                    initialStyle = brushStyle,
                    onShare = { bmp ->
                        coordinator.saveBitmap(CreationKind.Scribble, bmp) { item ->
                            coordinator.share(coordinator.repository.fileFor(item))
                            showExportSheet = false
                            onClose()
                        }
                    },
                    onSticker = { bmp ->
                        coordinator.shareAsSticker(CreationKind.Scribble, bmp) {
                            showExportSheet = false
                            onClose()
                        }
                    },
                    onDismiss = { showExportSheet = false }
                )
            }
        }
    }
}

@Composable
private fun ScribbleInkPad(
    modifier: Modifier,
    strokes: SnapshotStateList<InkStroke>,
    undo: SnapshotStateList<InkStroke>,
    livePoints: SnapshotStateList<Offset>,
    penColor: Color,
    erasing: Boolean,
    brushStyle: ScribbleBrushStyle,
    onSize: (IntSize) -> Unit,
    onStrokeCommitted: () -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    val strokeWidth = ScribbleRenderer.widthFor(brushStyle, erasing)
    val eraseMode = erasing

    Box(
        modifier
            .clip(shape)
            .background(Color(0xFF1A1028))
            .border(0.6.dp, Color.White.copy(alpha = 0.15f), shape)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .onSizeChanged(onSize)
            .pointerInput(eraseMode, penColor, brushStyle) {
                detectDragGestures(
                    onDragStart = { offset ->
                        livePoints.clear()
                        livePoints.add(offset)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        livePoints.add(change.position)
                    },
                    onDragEnd = {
                        if (livePoints.size >= 2) {
                            strokes.add(
                                InkStroke(
                                    points = livePoints.toList(),
                                    color = penColor,
                                    width = strokeWidth,
                                    erase = eraseMode
                                )
                            )
                            onStrokeCommitted()
                        }
                        livePoints.clear()
                    },
                    onDragCancel = { livePoints.clear() }
                )
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            strokes.forEach { stroke ->
                ScribbleRenderer.drawStroke(this, stroke, brushStyle)
            }
            if (livePoints.size >= 2) {
                ScribbleRenderer.drawStroke(
                    this,
                    InkStroke(
                        points = livePoints.toList(),
                        color = penColor,
                        width = strokeWidth,
                        erase = eraseMode
                    ),
                    brushStyle,
                    live = true
                )
            }
        }
    }
}

private fun pathFromPoints(points: List<Offset>): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points.first().x, points.first().y)
    for (i in 1 until points.size) {
        path.lineTo(points[i].x, points[i].y)
    }
    return path
}

@Composable
private fun QuoteScreen(coordinator: SocialCreateCoordinator, onClose: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var style by remember { mutableStateOf(com.lumen.launcher.social.quote.QuoteStyle.Bubble) }
    var aspect by remember { mutableStateOf(com.lumen.launcher.social.quote.QuoteAspect.Square) }
    var background by remember { mutableStateOf(com.lumen.launcher.social.quote.QuoteBackgroundKind.SocialBlue) }

    val context = LocalContext.current

    fun exportAndShare() {
        val bmp = com.lumen.launcher.social.quote.QuoteStyleRenderer.render(
            context = context,
            text = text.ifBlank { "Hello" },
            style = style,
            aspect = aspect,
            background = background
        )
        coordinator.saveBitmap(CreationKind.Quote, bmp) { item ->
            coordinator.share(coordinator.repository.fileFor(item))
            onClose()
        }
    }

    fun exportSticker() {
        val bmp = com.lumen.launcher.social.quote.QuoteStyleRenderer.render(
            context = context,
            text = text.ifBlank { "Hello" },
            style = style,
            aspect = com.lumen.launcher.social.quote.QuoteAspect.Square,
            background = com.lumen.launcher.social.quote.QuoteBackgroundKind.Transparent
        )
        coordinator.shareAsSticker(CreationKind.Quote, bmp) { onClose() }
    }

    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        ToolTopBar("Quote", onClose, ::exportAndShare)
        Text(
            "Sticker-style text for shares — try Bubble on Sky.",
            color = Lumen.Faint,
            fontFamily = Outfit,
            fontSize = 13.sp,
            modifier = Modifier.padding(bottom = 10.dp)
        )
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            textStyle = TextStyle(color = Color.White, fontFamily = Outfit, fontSize = 18.sp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.14f))
                .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                .padding(14.dp),
            decorationBox = { inner ->
                if (text.isBlank()) {
                    Text("Write something short…", color = Lumen.Faint, fontFamily = Outfit, fontSize = 16.sp)
                }
                inner()
            }
        )
        Spacer(Modifier.height(12.dp))
        Text("Style", color = Lumen.Faint, fontFamily = Outfit, fontSize = 11.sp)
        QuoteStylePicker(selected = style, onSelect = { style = it })
        Spacer(Modifier.height(8.dp))
        Text("Background", color = Lumen.Faint, fontFamily = Outfit, fontSize = 11.sp)
        QuoteBackgroundPicker(selected = background, onSelect = { background = it })
        Spacer(Modifier.height(8.dp))
        Text("Size", color = Lumen.Faint, fontFamily = Outfit, fontSize = 11.sp)
        QuoteAspectPicker(selected = aspect, onSelect = { aspect = it })
        Spacer(Modifier.height(14.dp))
        QuoteLivePreview(text, style, background)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Share as sticker",
                color = Lumen.Text,
                fontFamily = Outfit,
                fontSize = 14.sp,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.12f))
                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                    .clickable { exportSticker() }
                    .padding(vertical = 12.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Text(
                "Share image",
                color = Lumen.OnAccent,
                fontFamily = Outfit,
                fontSize = 14.sp,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Lumen.Accent)
                    .clickable { exportAndShare() }
                    .padding(vertical = 12.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
        Text(
            "Sticker ignores the background choice and adds a cut-out border.",
            color = Lumen.Faint,
            fontFamily = Outfit,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun PhotoMarkupScreen(coordinator: SocialCreateCoordinator, onClose: () -> Unit) {
    val context = LocalContext.current
    var uri by remember { mutableStateOf<android.net.Uri?>(null) }
    val strokes = remember { mutableStateListOf<StrokeLine>() }
    var current by remember { mutableStateOf<Path?>(null) }
    var penColor by remember { mutableStateOf(Color(0xFFFFD56A)) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { picked ->
        uri = picked
    }
    LaunchedEffect(Unit) { picker.launch("image/*") }

    fun exportAndShare() {
        val picked = uri ?: return
        val base = context.contentResolver.openInputStream(picked)?.use {
            android.graphics.BitmapFactory.decodeStream(it)
        } ?: return
        val out = base.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = AndroidCanvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 8f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        strokes.forEach { line ->
            paint.color = if (line.erase) android.graphics.Color.TRANSPARENT else line.color.toArgb()
            canvas.drawPath(line.path.asAndroidPath(), paint)
        }
        coordinator.saveBitmap(CreationKind.Photo, out) { item ->
            coordinator.share(coordinator.repository.fileFor(item))
            onClose()
        }
    }

    Column(Modifier.fillMaxSize()) {
        ToolTopBar("Photo", onClose, ::exportAndShare)
        if (uri == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Pick a photo or screenshot",
                        color = Lumen.Text,
                        fontFamily = Outfit,
                        fontSize = 16.sp
                    )
                    Text(
                        "Draw on it, then Share — original stays unchanged",
                        color = Lumen.Faint,
                        fontFamily = Outfit,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
                    )
                    Text(
                        "Choose image",
                        color = Lumen.OnAccent,
                        fontFamily = Outfit,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Lumen.Accent)
                            .clickable { picker.launch("image/*") }
                            .padding(horizontal = 24.dp, vertical = 14.dp)
                    )
                }
            }
        } else {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Draw on photo",
                    color = Lumen.Faint,
                    fontFamily = Outfit,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "Change",
                    color = Lumen.Accent,
                    fontFamily = Outfit,
                    fontSize = 12.sp,
                    modifier = Modifier.clickable { picker.launch("image/*") }
                )
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { o -> current = Path().apply { moveTo(o.x, o.y) } },
                            onDrag = { c, _ -> current?.lineTo(c.position.x, c.position.y) },
                            onDragEnd = {
                                current?.let { strokes.add(StrokeLine(it, penColor, 6f, false)) }
                                current = null
                            },
                            onDragCancel = { current = null }
                        )
                    }
            ) {
                androidx.compose.foundation.Image(
                    painter = coil.compose.rememberAsyncImagePainter(uri),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
                Canvas(Modifier.fillMaxSize()) {
                    strokes.forEach { drawPath(it.path, it.color, style = Stroke(6f, cap = StrokeCap.Round)) }
                    current?.let { drawPath(it, penColor, style = Stroke(6f, cap = StrokeCap.Round)) }
                }
            }
        }
    }
}

private fun renderStrokes(w: Int, h: Int, bg: Color, strokes: List<StrokeLine>): Bitmap {
    val width = w.coerceAtLeast(512)
    val height = h.coerceAtLeast(512)
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bmp)
    if (bg.alpha > 0f) {
        canvas.drawColor(bg.toArgb())
    }
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    strokes.forEach { line ->
        paint.strokeWidth = line.width
        paint.color = if (line.erase) android.graphics.Color.TRANSPARENT else line.color.toArgb()
        canvas.drawPath(line.path.asAndroidPath(), paint)
    }
    return bmp
}

