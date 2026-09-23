package com.lumen.launcher.ui.social

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.foundation.gestures.detectTapGestures
import com.lumen.launcher.social.photo.BlurLevels
import com.lumen.launcher.social.photo.BlurBox
import androidx.compose.foundation.layout.fillMaxHeight
import kotlin.math.roundToInt
import com.lumen.launcher.social.quote.QuoteTextTransform
import kotlinx.coroutines.launch
import com.lumen.launcher.social.quote.QuoteShapes
import com.lumen.launcher.social.quote.QuoteShape
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Redo
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.lumen.launcher.social.CreationKind
import com.lumen.launcher.social.SocialCreateCoordinator
import com.lumen.launcher.social.SocialCreateTool
import com.lumen.launcher.social.quote.QuoteAspect
import com.lumen.launcher.social.quote.QuoteBackgroundKind
import com.lumen.launcher.social.quote.QuoteStyle
import com.lumen.launcher.social.quote.QuoteStyleRenderer
import com.lumen.launcher.social.scribble.InkStroke
import com.lumen.launcher.social.scribble.ScribbleBrushStyle
import com.lumen.launcher.social.scribble.ScribbleExport
import com.lumen.launcher.social.scribble.ScribbleExportBackground
import com.lumen.launcher.social.scribble.ScribbleRenderer
import com.lumen.launcher.social.scribble.StrokeSmoothing
import com.lumen.launcher.ui.theme.Outfit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun SocialToolOverlay(
    tool: SocialCreateTool,
    coordinator: SocialCreateCoordinator,
    onClose: () -> Unit
) {
    Box(
        Modifier
            .fillMaxSize()
            .zIndex(200f)
            .drawBehind { createScreenBackground() }
            // Swallow touches so nothing behind the tool screen reacts.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        when (tool) {
            SocialCreateTool.Scribble -> ScribbleScreen(coordinator, onClose)
            SocialCreateTool.Quote -> QuoteScreen(coordinator, onClose)
            SocialCreateTool.Photo -> PhotoMarkupScreen(coordinator, onClose)
        }
    }
}

// ───────────────────────────── Scribble ─────────────────────────────

@Composable
private fun ScribbleScreen(coordinator: SocialCreateCoordinator, onClose: () -> Unit) {
    val strokes = remember { mutableStateListOf<InkStroke>() }
    val undo = remember { mutableStateListOf<InkStroke>() }
    val livePoints = remember { mutableStateListOf<Offset>() }
    var penColor by remember { mutableStateOf(Color.White) }
    var erasing by remember { mutableStateOf(false) }
    var brushStyle by remember { mutableStateOf(ScribbleBrushStyle.Sketch) }
    var showExportSheet by remember { mutableStateOf(false) }
    val penColors = listOf(Color.White, Color.Black, Color(0xFFB794F4), Color(0xFFFFD56A))

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            CreateToolHeader("Scribble", onClose, onShare = { if (strokes.isNotEmpty()) showExportSheet = true })

            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ScribbleBrushStyle.entries.forEach { s ->
                    CreateChip(s.name, brushStyle == s, leading = { BrushStyleIcon(s, brushStyle == s) }) { brushStyle = s }
                }
            }

            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CreateChip("Pen", !erasing, leading = {
                    Icon(Icons.Outlined.Edit, null, tint = chipIconTint(!erasing), modifier = Modifier.size(20.dp))
                }) { erasing = false }
                CreateChip("Eraser", erasing, leading = {
                    Icon(CreateIcons.Eraser, null, tint = chipIconTint(erasing), modifier = Modifier.size(22.dp))
                }) { erasing = true }
                CreateChip("Undo", false, leading = {
                    Icon(Icons.AutoMirrored.Outlined.Undo, null, tint = chipIconTint(false), modifier = Modifier.size(20.dp))
                }) { if (strokes.isNotEmpty()) undo.add(strokes.removeAt(strokes.lastIndex)) }
                CreateChip("Redo", false, leading = {
                    Icon(Icons.Outlined.Redo, null, tint = chipIconTint(false), modifier = Modifier.size(20.dp))
                }) { if (undo.isNotEmpty()) strokes.add(undo.removeAt(undo.lastIndex)) }
                CreateChip("Clear", false, leading = {
                    Icon(Icons.Outlined.Delete, null, tint = chipIconTint(false), modifier = Modifier.size(20.dp))
                }) {
                    strokes.clear()
                    undo.clear()
                }
            }

            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                penColors.forEach { c ->
                    ColorSwatch(c, selected = !erasing && penColor == c) {
                        penColor = c
                        erasing = false
                    }
                }
            }

            ScribbleInkPad(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                strokes = strokes,
                livePoints = livePoints,
                penColor = penColor,
                erasing = erasing,
                brushStyle = brushStyle,
                onStrokeCommitted = { undo.clear() }
            )

            CreateActionBar(
                onSave = {
                    if (strokes.isNotEmpty()) {
                        coordinator.saveImageToGallery(
                            CreationKind.Scribble,
                            ScribbleExport.render(strokes, brushStyle, ScribbleExportBackground.Dark)
                        )
                    }
                },
                onShare = { if (strokes.isNotEmpty()) showExportSheet = true },
                onSticker = {
                    if (strokes.isNotEmpty()) {
                        coordinator.addToStickerPack(
                            CreationKind.Scribble,
                            ScribbleExport.render(strokes, brushStyle, ScribbleExportBackground.Transparent, sticker = true),
                            "A hand-drawn scribble"
                        )
                    }
                }
            )
        }

        if (showExportSheet) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
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
                    onSaveSticker = { bmp -> coordinator.saveStickerToGallery(CreationKind.Scribble, bmp) },
                    onAddToPack = { bmp ->
                        coordinator.addToStickerPack(CreationKind.Scribble, bmp, "A hand-drawn scribble")
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
    livePoints: SnapshotStateList<Offset>,
    penColor: Color,
    erasing: Boolean,
    brushStyle: ScribbleBrushStyle,
    onStrokeCommitted: () -> Unit
) {
    val shape = RoundedCornerShape(30.dp)
    val strokeWidth = ScribbleRenderer.widthFor(brushStyle, erasing)
    val eraseMode = erasing

    Box(
        modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF211338), Color(0xFF160C27))))
            .border(1.dp, Color(0x669D63EE), shape)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
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
                            strokes.add(InkStroke(livePoints.toList(), penColor, strokeWidth, eraseMode))
                            onStrokeCommitted()
                        }
                        livePoints.clear()
                    },
                    onDragCancel = { livePoints.clear() }
                )
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            strokes.forEach { ScribbleRenderer.drawStroke(this, it, brushStyle) }
            if (livePoints.size >= 2) {
                ScribbleRenderer.drawStroke(
                    this,
                    InkStroke(livePoints.toList(), penColor, strokeWidth, eraseMode),
                    brushStyle,
                    live = true
                )
            }
        }
        if (strokes.isEmpty() && livePoints.isEmpty()) {
            Text(
                "Draw here",
                color = Color.White.copy(alpha = 0.25f),
                fontFamily = Outfit,
                fontSize = 16.sp,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

// ───────────────────────────── Quote ─────────────────────────────

private enum class QuoteStep { Write, Design }

@Composable
private fun QuoteScreen(coordinator: SocialCreateCoordinator, onClose: () -> Unit) {
    var step by remember { mutableStateOf(QuoteStep.Write) }
    var text by remember { mutableStateOf("") }
    var style by remember { mutableStateOf(QuoteStyle.Bubble) }
    var aspect by remember { mutableStateOf(QuoteAspect.Square) }
    var background by remember { mutableStateOf(QuoteBackgroundKind.SocialBlue) }
    var shape by remember { mutableStateOf(QuoteShape.None) }
    var shapePhoto by remember { mutableStateOf<Bitmap?>(null) }
    var transform by remember { mutableStateOf(QuoteTextTransform()) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isSticker = background == QuoteBackgroundKind.Transparent
    val shown = text.ifBlank { "Hello" }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { picked ->
        if (picked != null) {
            scope.launch {
                shapePhoto = withContext(Dispatchers.IO) { decodeForMarkup(context, picked, maxSide = 1600) }
                if (shape == QuoteShape.None) shape = QuoteShape.Circle
            }
        }
    }

    fun render(forSticker: Boolean): Bitmap = QuoteStyleRenderer.render(
        context = context,
        text = shown,
        style = style,
        aspect = if (forSticker) QuoteAspect.Square else aspect,
        background = if (forSticker) QuoteBackgroundKind.Transparent else background,
        shape = shape,
        shapePhoto = shapePhoto,
        transform = transform
    )

    // "Clear" means a see-through sticker; every other background is a normal image.
    fun share() {
        if (isSticker) {
            coordinator.shareAsSticker(CreationKind.Quote, render(forSticker = true)) { onClose() }
        } else {
            coordinator.saveBitmap(CreationKind.Quote, render(forSticker = false)) { item ->
                coordinator.share(coordinator.repository.fileFor(item))
                onClose()
            }
        }
    }

    fun save() {
        if (isSticker) coordinator.saveStickerToGallery(CreationKind.Quote, render(forSticker = true))
        else coordinator.saveImageToGallery(CreationKind.Quote, render(forSticker = false))
    }

    fun addSticker() {
        coordinator.addToStickerPack(
            CreationKind.Quote,
            render(forSticker = true),
            "The words ${shown.take(90)} in sticker lettering"
        )
    }

    // Live preview, rendered off the main thread and debounced while typing.
    // Half resolution keeps it smooth while dragging the position / size / rotate bars.
    val preview by produceState<ImageBitmap?>(null, text, style, aspect, background, shape, shapePhoto, transform) {
        delay(30)
        value = withContext(Dispatchers.Default) {
            QuoteStyleRenderer.renderSized(
                context = context,
                text = shown,
                style = style,
                w = aspect.width / 2,
                h = aspect.height / 2,
                background = background,
                shape = shape,
                shapePhoto = shapePhoto,
                watermark = true,
                transform = transform
            ).asImageBitmap()
        }
    }

    when (step) {
        QuoteStep.Write -> QuoteWriteStep(
            text = text,
            onText = { text = it },
            preview = preview,
            aspect = aspect,
            isSticker = isSticker,
            onClose = onClose,
            onShare = ::share,
            onNext = { step = QuoteStep.Design }
        )
        QuoteStep.Design -> QuoteDesignStep(
            text = shown,
            preview = preview,
            style = style,
            onStyle = { style = it },
            shape = shape,
            onShape = { shape = it },
            hasPhoto = shapePhoto != null,
            onPickPhoto = { photoPicker.launch("image/*") },
            onClearPhoto = { shapePhoto = null },
            shapePhoto = shapePhoto,
            background = background,
            onBackground = { background = it },
            aspect = aspect,
            onAspect = { aspect = it },
            transform = transform,
            onTransform = { transform = it },
            isSticker = isSticker,
            onEditText = { step = QuoteStep.Write },
            onClose = onClose,
            onSave = ::save,
            onSticker = ::addSticker,
            onShare = ::share
        )
    }
}

/** Step 1: type. The preview stays visible above the keyboard. */
@Composable
private fun QuoteWriteStep(
    text: String,
    onText: (String) -> Unit,
    preview: ImageBitmap?,
    aspect: QuoteAspect,
    isSticker: Boolean,
    onClose: () -> Unit,
    onShare: () -> Unit,
    onNext: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }
    Column(Modifier.fillMaxSize()) {
        CreateToolHeader("Quote", onClose, onShare, subtitle = "Step 1 of 2 · Write")
        QuotePreviewCard(
            preview = preview,
            aspect = aspect,
            isSticker = isSticker,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
        val fieldShape = RoundedCornerShape(30.dp)
        BasicTextField(
            value = text,
            onValueChange = onText,
            textStyle = TextStyle(color = Color.White, fontFamily = Outfit, fontSize = 19.sp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = {
                keyboard?.hide()
                onNext()
            }),
            cursorBrush = SolidColor(Color.White),
            maxLines = 3,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .clip(fieldShape)
                .background(Color(0x1FFFFFFF))
                .border(1.dp, Color(0x40B794F4), fieldShape)
                .padding(horizontal = 22.dp, vertical = 18.dp),
            decorationBox = { inner ->
                if (text.isBlank()) {
                    Text("Write something short…", color = Color.White.copy(alpha = 0.5f), fontFamily = Outfit, fontSize = 19.sp)
                }
                inner()
            }
        )
        val btnShape = RoundedCornerShape(28.dp)
        Row(
            Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .height(54.dp)
                .shadow(14.dp, btnShape, spotColor = CreatePalette.Accent)
                .clip(btnShape)
                .background(CreatePalette.ShareFill)
                .clickable {
                    keyboard?.hide()
                    onNext()
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text("Next: choose a design", color = CreatePalette.Ink, fontFamily = Outfit, fontWeight = FontWeight.Medium, fontSize = 16.sp)
            Spacer(Modifier.width(6.dp))
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = CreatePalette.Ink, modifier = Modifier.size(22.dp))
        }
    }
}

/** Step 2: design (preview pinned at the top, choices scroll below). */
@Composable
private fun QuoteDesignStep(
    text: String,
    preview: ImageBitmap?,
    style: QuoteStyle,
    onStyle: (QuoteStyle) -> Unit,
    shape: QuoteShape,
    onShape: (QuoteShape) -> Unit,
    hasPhoto: Boolean,
    onPickPhoto: () -> Unit,
    onClearPhoto: () -> Unit,
    shapePhoto: Bitmap?,
    background: QuoteBackgroundKind,
    onBackground: (QuoteBackgroundKind) -> Unit,
    aspect: QuoteAspect,
    onAspect: (QuoteAspect) -> Unit,
    transform: QuoteTextTransform,
    onTransform: (QuoteTextTransform) -> Unit,
    isSticker: Boolean,
    onEditText: () -> Unit,
    onClose: () -> Unit,
    onSave: () -> Unit,
    onSticker: () -> Unit,
    onShare: () -> Unit
) {
    val context = LocalContext.current
    // One small thumbnail per design, drawn with the user's own text.
    val thumbs by produceState(emptyMap<QuoteStyle, ImageBitmap>(), text, background, shape, shapePhoto) {
        delay(120)
        value = withContext(Dispatchers.Default) {
            QuoteStyle.entries.associateWith { s ->
                QuoteStyleRenderer.renderThumb(context, text, s, background, shape, shapePhoto, size = 220).asImageBitmap()
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        CreateToolHeader("Quote", onClose, onShare, subtitle = "Step 2 of 2 · Design")
        // Preview with a vertical bar (move up/down) on the right…
        Row(
            Modifier
                .fillMaxWidth()
                .height(250.dp)
                .padding(start = 16.dp, end = 6.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            QuotePreviewCard(
                preview = preview,
                aspect = aspect,
                isSticker = isSticker,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
            DragBar(
                value = transform.offsetY / 0.8f + 0.5f,
                onValue = { onTransform(transform.copy(offsetY = (it - 0.5f) * 0.8f)) },
                vertical = true,
                modifier = Modifier
                    .width(40.dp)
                    .fillMaxHeight()
            )
        }
        // …and a horizontal bar (move left/right) underneath.
        DragBar(
            value = transform.offsetX / 0.8f + 0.5f,
            onValue = { onTransform(transform.copy(offsetX = (it - 0.5f) * 0.8f)) },
            vertical = false,
            modifier = Modifier
                .padding(start = 16.dp, end = 46.dp)
                .fillMaxWidth()
                .height(40.dp)
        )
        Row(
            Modifier
                .align(Alignment.CenterHorizontally)
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onEditText)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Edit, null, tint = CreatePalette.Accent, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Edit text", color = CreatePalette.Accent, fontFamily = Outfit, fontSize = 14.sp)
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CreateSectionLabel("Adjust text", Modifier.weight(1f))
                if (!transform.isDefault) {
                    Text(
                        "Reset",
                        color = CreatePalette.Accent,
                        fontFamily = Outfit,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onTransform(QuoteTextTransform()) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
            LabeledDragBar(
                label = "Size",
                valueText = "${(transform.scale * 100).roundToInt()}%",
                value = scaleToBar(transform.scale),
                onValue = { onTransform(transform.copy(scale = barToScale(it))) }
            )
            LabeledDragBar(
                label = "Rotate",
                valueText = "${transform.rotation.roundToInt()}°",
                value = transform.rotation / 180f + 0.5f,
                onValue = { onTransform(transform.copy(rotation = (it - 0.5f) * 180f)) }
            )

            CreateSectionLabel("Design")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuoteStyle.entries.forEach { s ->
                    DesignThumb(label = s.label, image = thumbs[s], selected = style == s) { onStyle(s) }
                }
            }

            CreateSectionLabel("Shape")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuoteShape.entries.forEach { sh ->
                    val sel = shape == sh
                    CreateChip(sh.label, sel, leading = { ShapeGlyph(sh, chipIconTint(sel)) }) { onShape(sh) }
                }
            }
            Row(
                Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CreateChip(
                    if (hasPhoto) "Change photo" else "Photo in shape",
                    hasPhoto,
                    leading = { Icon(Icons.Outlined.Image, null, tint = chipIconTint(hasPhoto), modifier = Modifier.size(20.dp)) },
                    onClick = onPickPhoto
                )
                if (hasPhoto) {
                    CreateChip("Remove photo", false, onClick = onClearPhoto)
                }
            }

            CreateSectionLabel("Background")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(
                    QuoteBackgroundKind.SocialBlue to "Sky",
                    QuoteBackgroundKind.PurpleGradient to "Purple",
                    QuoteBackgroundKind.LumenDark to "Dark",
                    QuoteBackgroundKind.Light to "Light",
                    QuoteBackgroundKind.Transparent to "Clear"
                ).forEach { (kind, label) ->
                    val sel = background == kind
                    CreateChip(label, sel, leading = {
                        when (kind) {
                            QuoteBackgroundKind.SocialBlue -> SkySwatch()
                            QuoteBackgroundKind.PurpleGradient -> DotSwatch(Color(0xFF9333EA))
                            QuoteBackgroundKind.LumenDark -> Icon(Icons.Outlined.DarkMode, null, tint = Color(0xFFF7B267), modifier = Modifier.size(20.dp))
                            QuoteBackgroundKind.Light -> Icon(Icons.Outlined.WbSunny, null, tint = chipIconTint(sel), modifier = Modifier.size(20.dp))
                            QuoteBackgroundKind.Transparent -> CheckerSwatch()
                        }
                    }) { onBackground(kind) }
                }
            }

            CreateSectionLabel("Size")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(
                    Triple(QuoteAspect.Square, "1:1", 1f to 1f),
                    Triple(QuoteAspect.Story, "9:16", 9f to 16f),
                    Triple(QuoteAspect.Landscape, "16:9", 16f to 9f)
                ).forEach { (a, label, ratio) ->
                    val sel = aspect == a
                    CreateChip(label, sel, leading = { AspectGlyph(ratio.first, ratio.second, chipIconTint(sel)) }) { onAspect(a) }
                }
            }
            Text(
                if (isSticker) "Clear = see-through sticker with a cut-out border."
                else "Sticker always uses a see-through background.",
                color = CreatePalette.Label,
                fontFamily = Outfit,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 14.dp, bottom = 8.dp)
            )
        }
        CreateActionBar(onSave = onSave, onShare = onShare, onSticker = onSticker)
    }
}

/** Size bar: left half = 50%–100%, right half = 100%–180%, middle = 100%. */
private fun barToScale(v: Float): Float = if (v < 0.5f) 0.5f + v else 1f + (v - 0.5f) * 1.6f
private fun scaleToBar(s: Float): Float = if (s < 1f) s - 0.5f else 0.5f + (s - 1f) / 1.6f

/** Preview card that keeps the chosen size's shape and shows a checkerboard for "Clear". */
@Composable
private fun QuotePreviewCard(
    preview: ImageBitmap?,
    aspect: QuoteAspect,
    isSticker: Boolean,
    modifier: Modifier
) {
    val ratio = aspect.width.toFloat() / aspect.height
    Box(modifier, contentAlignment = Alignment.Center) {
        val cardShape = RoundedCornerShape(26.dp)
        Box(
            Modifier
                .aspectRatio(ratio, matchHeightConstraintsFirst = true)
                .shadow(18.dp, cardShape, spotColor = Color(0xFF6D28D9))
                .clip(cardShape)
                .then(if (isSticker) Modifier.drawBehind { checkerboard() } else Modifier.background(Color(0xFF1B1030)))
                .border(1.dp, Color(0x40B794F4), cardShape)
        ) {
            preview?.let {
                Image(it, "Quote preview", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

/** A design option shown as a real mini render of the user's text. */
@Composable
private fun DesignThumb(label: String, image: ImageBitmap?, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier
            .width(92.dp)
            .clip(shape)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(88.dp)
                .clip(shape)
                .background(Color(0xFF1B1030))
                .border(if (selected) 2.5.dp else 1.dp, if (selected) CreatePalette.Accent else Color(0x2EC7A6F5), shape)
        ) {
            image?.let {
                Image(it, label, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(2.dp))
            }
        }
        Text(
            label,
            color = if (selected) Color.White else Color.White.copy(alpha = 0.7f),
            fontFamily = Outfit,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

/** Tiny outline of a quote shape for its chip. */
@Composable
private fun ShapeGlyph(shape: QuoteShape, color: Color) {
    Canvas(Modifier.size(20.dp)) {
        if (shape == QuoteShape.None) {
            drawLine(color, Offset(size.width * 0.2f, size.height * 0.8f), Offset(size.width * 0.8f, size.height * 0.2f), strokeWidth = 2.dp.toPx())
            drawCircle(color, radius = size.minDimension * 0.4f, style = Stroke(1.6.dp.toPx()))
        } else {
            val path = QuoteShapes.path(shape, android.graphics.RectF(1f, 1f, size.width - 1f, size.height - 1f))
            drawIntoCanvas { c ->
                c.nativeCanvas.drawPath(path, android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = color.toArgb()
                    style = android.graphics.Paint.Style.STROKE
                    strokeWidth = 1.8.dp.toPx()
                    strokeJoin = android.graphics.Paint.Join.ROUND
                })
            }
        }
    }
}

// ───────────────────────────── Photo ─────────────────────────────

/** A mark on the photo, stored in image-relative units (0..1) so it maps exactly on export. */
private data class PhotoMark(val points: List<Offset>, val color: Color, val widthFraction: Float)

private val PhotoColors = listOf(
    Color(0xFFFFD56A), Color(0xFF9B5CF6), Color(0xFF3B82F6), Color(0xFFF472B6), Color.White
)
private val ExtraPhotoColors = listOf(
    Color(0xFFEF4444), Color(0xFFF97316), Color(0xFF22C55E), Color(0xFF14B8A6), Color.Black, Color(0xFF8B5E3C)
)
/** Brush sizes in dp, as drawn on screen. */
private val PhotoBrushSizes = listOf(3f, 6f, 10f, 16f)

@Composable
private fun PhotoMarkupScreen(coordinator: SocialCreateCoordinator, onClose: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var uri by remember { mutableStateOf<Uri?>(null) }
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var loading by remember { mutableStateOf(false) }
    val marks = remember { mutableStateListOf<PhotoMark>() }
    val live = remember { mutableStateListOf<Offset>() }
    var penColor by remember { mutableStateOf(PhotoColors.first()) }
    var sizeIdx by remember { mutableIntStateOf(2) }
    var showMoreColors by remember { mutableStateOf(false) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    // Blur boxes (e.g. to hide faces); strength 1.0 = unrecognizable.
    var blurMode by remember { mutableStateOf(false) }
    val blurBoxes = remember { mutableStateListOf<BlurBox>() }
    var selectedBlur by remember { mutableStateOf<Long?>(null) }
    var blurLevels by remember { mutableStateOf<BlurLevels?>(null) }
    var levelImages by remember { mutableStateOf<List<ImageBitmap>>(emptyList()) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { picked ->
        if (picked != null) uri = picked
    }
    LaunchedEffect(Unit) { picker.launch("image/*") }
    LaunchedEffect(uri) {
        val u = uri ?: return@LaunchedEffect
        loading = true
        marks.clear()
        blurBoxes.clear()
        selectedBlur = null
        blurMode = false
        blurLevels = null
        levelImages = emptyList()
        photo = withContext(Dispatchers.IO) { decodeForMarkup(context, u) }
        loading = false
        photo?.let { p ->
            val levels = withContext(Dispatchers.Default) { BlurLevels.build(p) }
            blurLevels = levels
            levelImages = levels.levels.map { it.asImageBitmap() }
        }
    }

    fun screenRectOf(b: BlurBox, rect: Rect) = Rect(
        rect.left + b.left * rect.width,
        rect.top + b.top * rect.height,
        rect.left + b.right * rect.width,
        rect.top + b.bottom * rect.height
    )

    /** Adds a blur box in the middle of the photo (square on screen) and selects it. */
    fun addBlurBox() {
        val bmp = photo ?: return
        val w = 0.3f
        val h = (w * bmp.width / bmp.height).coerceIn(0.1f, 0.6f)
        val shift = (blurBoxes.size % 4) * 0.05f
        val l = (0.5f - w / 2f + shift).coerceIn(0f, 1f - w)
        val t = (0.5f - h / 2f + shift).coerceIn(0f, 1f - h)
        val box = BlurBox(System.nanoTime(), l, t, l + w, t + h)
        blurBoxes.add(box)
        selectedBlur = box.id
        blurMode = true
    }

    // Where the photo actually sits inside the drawing box (ContentScale.Fit).
    val imageRect: Rect? = photo?.let { bmp ->
        if (boxSize.width == 0 || boxSize.height == 0) null
        else {
            val s = minOf(boxSize.width / bmp.width.toFloat(), boxSize.height / bmp.height.toFloat())
            val w = bmp.width * s
            val h = bmp.height * s
            val left = (boxSize.width - w) / 2f
            val top = (boxSize.height - h) / 2f
            Rect(left, top, left + w, top + h)
        }
    }

    fun rendered(): Bitmap? {
        val base = photo ?: return null
        val out = base.copy(Bitmap.Config.ARGB_8888, true)
        blurLevels?.applyTo(out, blurBoxes.toList())
        val canvas = android.graphics.Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        marks.forEach { m ->
            paint.color = m.color.toArgb()
            paint.strokeWidth = m.widthFraction * out.width
            val pts = m.points.map { Offset(it.x * out.width, it.y * out.height) }
            canvas.drawPath(StrokeSmoothing.toPath(pts).asAndroidPath(), paint)
        }
        return out
    }

    fun share() {
        val out = rendered() ?: return
        coordinator.saveBitmap(CreationKind.Photo, out) { item ->
            coordinator.share(coordinator.repository.fileFor(item))
            onClose()
        }
    }

    fun save() {
        val out = rendered() ?: return
        coordinator.saveImageToGallery(CreationKind.Photo, out)
    }

    Column(Modifier.fillMaxSize()) {
        CreateToolHeader("Photo", onClose, ::share)

        if (photo == null) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.Image, null, tint = CreatePalette.Accent, modifier = Modifier.size(44.dp))
                    Text(
                        if (loading) "Opening photo…" else "Pick a photo or screenshot",
                        color = Color.White,
                        fontFamily = Outfit,
                        fontSize = 18.sp,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                    Text(
                        "Draw on it, then share. Your original stays unchanged.",
                        color = CreatePalette.Label,
                        fontFamily = Outfit,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 6.dp, bottom = 20.dp)
                    )
                    if (!loading) {
                        val shape = RoundedCornerShape(26.dp)
                        Text(
                            "Choose image",
                            color = CreatePalette.Ink,
                            fontFamily = Outfit,
                            fontWeight = FontWeight.Medium,
                            fontSize = 16.sp,
                            modifier = Modifier
                                .shadow(14.dp, shape, spotColor = CreatePalette.Accent)
                                .clip(shape)
                                .background(CreatePalette.ShareFill)
                                .clickable { picker.launch("image/*") }
                                .padding(horizontal = 28.dp, vertical = 14.dp)
                        )
                    }
                }
            }
        } else {

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(if (blurMode) "Blur areas" else "Draw on photo", color = CreatePalette.Label, fontFamily = Outfit, fontSize = 15.sp, modifier = Modifier.weight(1f))
            if (marks.isNotEmpty()) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { marks.removeAt(marks.lastIndex) }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.AutoMirrored.Outlined.Undo, "Undo", tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Undo", color = Color.White.copy(alpha = 0.8f), fontFamily = Outfit, fontSize = 15.sp)
                }
                Spacer(Modifier.width(10.dp))
            }
            Text(
                "Change",
                color = CreatePalette.Accent,
                fontFamily = Outfit,
                fontSize = 15.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { picker.launch("image/*") }
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            )
        }

        // Photo card
        val cardShape = RoundedCornerShape(30.dp)
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .clip(cardShape)
                .background(Color(0x14FFFFFF))
                .border(1.dp, Color(0x559D63EE), cardShape)
                .padding(14.dp)
        ) {
            val bmp = photo!!
            val image = remember(bmp) { bmp.asImageBitmap() }
            Box(
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { boxSize = it }
                    .clipToBounds()
                    .pointerInput(blurMode, imageRect) {
                        // Blur mode: tap a box to select it (tap elsewhere to deselect).
                        val rect = imageRect ?: return@pointerInput
                        if (!blurMode) return@pointerInput
                        detectTapGestures { o ->
                            selectedBlur = blurBoxes.lastOrNull { screenRectOf(it, rect).contains(o) }?.id
                        }
                    }
                    .pointerInput(bmp, penColor, sizeIdx, imageRect, blurMode) {
                        val rect = imageRect ?: return@pointerInput
                        if (blurMode) {
                            // Drag a corner handle to resize, drag inside a box to move it.
                            val handle = 28.dp.toPx()
                            var activeId: Long? = null
                            var corner = -1
                            detectDragGestures(
                                onDragStart = { o ->
                                    activeId = null
                                    corner = -1
                                    blurBoxes.firstOrNull { it.id == selectedBlur }?.let { sel ->
                                        val r = screenRectOf(sel, rect)
                                        val hit = listOf(r.topLeft, r.topRight, r.bottomRight, r.bottomLeft)
                                            .indexOfFirst { (it - o).getDistance() <= handle }
                                        if (hit >= 0) {
                                            activeId = sel.id
                                            corner = hit
                                        }
                                    }
                                    if (activeId == null) {
                                        blurBoxes.lastOrNull { screenRectOf(it, rect).contains(o) }?.let {
                                            activeId = it.id
                                            selectedBlur = it.id
                                        }
                                    }
                                },
                                onDrag = { c, d ->
                                    val id = activeId
                                    val i = blurBoxes.indexOfFirst { it.id == id }
                                    if (id != null && i >= 0) {
                                        c.consume()
                                        val dx = d.x / rect.width
                                        val dy = d.y / rect.height
                                        blurBoxes[i] = if (corner >= 0) blurBoxes[i].resized(corner, dx, dy) else blurBoxes[i].moved(dx, dy)
                                    }
                                },
                                onDragEnd = { activeId = null },
                                onDragCancel = { activeId = null }
                            )
                            return@pointerInput
                        }
                        fun norm(p: Offset) = Offset((p.x - rect.left) / rect.width, (p.y - rect.top) / rect.height)
                        detectDragGestures(
                            onDragStart = { o ->
                                live.clear()
                                live.add(norm(o))
                            },
                            onDrag = { c, _ ->
                                c.consume()
                                live.add(norm(c.position))
                            },
                            onDragEnd = {
                                if (live.size >= 2) {
                                    val px = with(density) { PhotoBrushSizes[sizeIdx].dp.toPx() }
                                    marks.add(PhotoMark(live.toList(), penColor, px / rect.width))
                                }
                                live.clear()
                            },
                            onDragCancel = { live.clear() }
                        )
                    }
            ) {
                Image(
                    image,
                    contentDescription = "Photo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp))
                )
                Canvas(Modifier.fillMaxSize()) {
                    val rect = imageRect ?: return@Canvas
                    fun toScreen(p: Offset) = Offset(rect.left + p.x * rect.width, rect.top + p.y * rect.height)
                    clipRect(rect.left, rect.top, rect.right, rect.bottom) {
                        val levels = blurLevels
                        if (levels != null && levelImages.size == levels.levels.size) {
                            val dstOffset = IntOffset(rect.left.roundToInt(), rect.top.roundToInt())
                            val dstSize = IntSize(rect.width.roundToInt(), rect.height.roundToInt())
                            blurBoxes.forEach { b ->
                                val r = screenRectOf(b, rect)
                                val m = levels.mix(b.strength)
                                clipRect(r.left, r.top, r.right, r.bottom) {
                                    if (m.lower > 0) {
                                        drawImage(levelImages[m.lower - 1], dstOffset = dstOffset, dstSize = dstSize, filterQuality = FilterQuality.High)
                                    }
                                    if (m.upper > m.lower && m.fraction > 0f) {
                                        drawImage(levelImages[m.upper - 1], dstOffset = dstOffset, dstSize = dstSize, alpha = m.fraction, filterQuality = FilterQuality.High)
                                    }
                                }
                            }
                        }
                        marks.forEach { m ->
                            drawPath(
                                StrokeSmoothing.toPath(m.points.map(::toScreen)),
                                m.color,
                                style = Stroke(m.widthFraction * rect.width, cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )
                        }
                        if (live.size >= 2) {
                            drawPath(
                                StrokeSmoothing.toPath(live.map(::toScreen)),
                                penColor,
                                style = Stroke(PhotoBrushSizes[sizeIdx].dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )
                        }
                    }
                    if (blurMode) {
                        blurBoxes.forEach { b ->
                            val r = screenRectOf(b, rect)
                            val sel = b.id == selectedBlur
                            drawRect(
                                Color.White.copy(alpha = if (sel) 1f else 0.6f),
                                topLeft = r.topLeft,
                                size = r.size,
                                style = Stroke(
                                    width = (if (sel) 2f else 1.5f).dp.toPx(),
                                    pathEffect = if (sel) null else PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
                                )
                            )
                            if (sel) {
                                listOf(r.topLeft, r.topRight, r.bottomRight, r.bottomLeft).forEach { c ->
                                    drawCircle(Color.White, radius = 9.dp.toPx(), center = c)
                                    drawCircle(CreatePalette.Accent, radius = 9.dp.toPx(), center = c, style = Stroke(2.dp.toPx()))
                                }
                            }
                        }
                    }
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CreateChip("Draw", !blurMode, leading = {
                Icon(Icons.Outlined.Edit, null, tint = chipIconTint(!blurMode), modifier = Modifier.size(20.dp))
            }) {
                blurMode = false
                selectedBlur = null
            }
            CreateChip("Blur", blurMode, leading = {
                Icon(Icons.Outlined.BlurOn, null, tint = chipIconTint(blurMode), modifier = Modifier.size(20.dp))
            }) {
                if (blurBoxes.isEmpty()) addBlurBox() else blurMode = true
            }
        }
        if (blurMode) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CreateChip("Add box", false, leading = {
                        Icon(Icons.Outlined.Add, null, tint = chipIconTint(false), modifier = Modifier.size(20.dp))
                    }) { addBlurBox() }
                    if (selectedBlur != null) {
                        CreateChip("Remove", false, leading = {
                            Icon(Icons.Outlined.Delete, null, tint = chipIconTint(false), modifier = Modifier.size(20.dp))
                        }) {
                            blurBoxes.removeAll { it.id == selectedBlur }
                            selectedBlur = null
                        }
                    }
                }
                val selIndex = blurBoxes.indexOfFirst { it.id == selectedBlur }
                if (selIndex >= 0) {
                    val sel = blurBoxes[selIndex]
                    LabeledDragBar(
                        label = "Blur",
                        valueText = "${(sel.strength * 100).roundToInt()}",
                        value = sel.strength,
                        snapTo = null,
                        onValue = { v ->
                            val i = blurBoxes.indexOfFirst { it.id == selectedBlur }
                            if (i >= 0) blurBoxes[i] = blurBoxes[i].copy(strength = v)
                        },
                        modifier = Modifier.padding(top = 6.dp)
                    )
                } else {
                    Text(
                        "Tap a box to select it, or add one. Drag to move, pull a corner to resize.",
                        color = CreatePalette.Label,
                        fontFamily = Outfit,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 10.dp, bottom = 6.dp)
                    )
                }
            }
        } else {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToolPill(Modifier.weight(1f)) {
                PhotoColors.forEach { c ->
                    ColorSwatch(c, selected = penColor == c, size = 26.dp) { penColor = c }
                }
                // Rainbow: opens more colors
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .then(
                            if (penColor in ExtraPhotoColors) Modifier.border(2.dp, CreatePalette.Accent, CircleShape)
                            else Modifier
                        )
                        .clickable { showMoreColors = !showMoreColors },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.sweepGradient(
                                    listOf(
                                        Color(0xFFEF4444), Color(0xFFF59E0B), Color(0xFF22C55E),
                                        Color(0xFF3B82F6), Color(0xFFA855F7), Color(0xFFEF4444)
                                    )
                                )
                            )
                    )
                }
            }
            Box(
                Modifier
                    .padding(horizontal = 8.dp)
                    .width(1.dp)
                    .height(30.dp)
                    .background(Color.White.copy(alpha = 0.15f))
            )
            ToolPill {
                PhotoBrushSizes.forEachIndexed { i, dpSize ->
                    val sel = sizeIdx == i
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .then(
                                if (sel) Modifier
                                    .background(Color(0x339D63EE))
                                    .border(2.dp, CreatePalette.Accent, CircleShape)
                                else Modifier
                            )
                            .clickable { sizeIdx = i },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            Modifier
                                .size((5f + dpSize * 0.8f).dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = if (sel) 1f else 0.8f))
                        )
                    }
                }
            }
        }
        if (showMoreColors) {
            Row(
                Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ExtraPhotoColors.forEach { c ->
                    ColorSwatch(c, selected = penColor == c, size = 26.dp) {
                        penColor = c
                        showMoreColors = false
                    }
                }
            }
        }
        }

        CreateActionBar(
            onSave = ::save,
            onShare = ::share,
            onSticker = {
                rendered()?.let { coordinator.addToStickerPack(CreationKind.Photo, it, "A photo with drawn marks") }
            }
        )
        }
    }
}

@Composable
private fun ToolPill(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(32.dp)
    Row(
        modifier
            .clip(shape)
            .background(Color(0x14FFFFFF))
            .border(1.dp, Color(0x2EC7A6F5), shape)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) { content() }
}

/**
 * Decodes the picked image with its EXIF rotation applied (API 28+) and capped to 2560px,
 * so what you draw on screen lines up exactly with the exported image.
 */
private fun decodeForMarkup(context: Context, uri: Uri, maxSide: Int = 2560): Bitmap? = runCatching {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val w = info.size.width
            val h = info.size.height
            val scale = minOf(1f, maxSide.toFloat() / maxOf(w, h))
            if (scale < 1f) {
                decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
            }
        }
    } else {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }
}.getOrNull()
