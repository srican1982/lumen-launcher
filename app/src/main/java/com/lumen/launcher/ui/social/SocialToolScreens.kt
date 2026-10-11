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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Tune
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
    val stickerSaved = rememberStickerSavedPrompt()
    val strokes = remember { mutableStateListOf<InkStroke>() }
    val undo = remember { mutableStateListOf<InkStroke>() }
    val stickerStrokes = strokes.toList()
    // The producer assigns value below; workaround for the bundled Compose lint detector.
    @Suppress("ProduceStateDoesNotAssignValue")
    val miniSticker by produceState<ImageBitmap?>(null,stickerStrokes) {
        value = if(stickerStrokes.isEmpty()) null else withContext(Dispatchers.Default) {
            com.lumen.launcher.social.StickerMaker.make(
                ScribbleExport.render(stickerStrokes,ScribbleBrushStyle.Sketch,ScribbleExportBackground.Transparent,sticker=true)
            ).asImageBitmap()
        }
    }

    val livePoints = remember { mutableStateListOf<Offset>() }
    var penColor by remember { mutableStateOf(Color.White) }
    var erasing by remember { mutableStateOf(false) }
    var brushStyle by remember { mutableStateOf(ScribbleBrushStyle.Sketch) }
    var showExportSheet by remember { mutableStateOf(false) }
    var brushSize by remember { mutableStateOf(12f) }
    var brushOpen by remember { mutableStateOf(false) }
    var customColor by remember { mutableStateOf(false) }
    var clearConfirm by remember { mutableStateOf(false) }
    var eraserSize by remember { mutableStateOf(28f) }
    var opacity by remember { mutableStateOf(1f) }
    val penColors = listOf(Color.White, Color.Black, Color(0xFF9460FF), Color(0xFF80BBFF), Color(0xFFFF88C4), Color(0xFFFFD56A), Color(0xFF51DDC1))
    val visibleColors = remember { mutableStateListOf(Color.White, Color.Black, Color(0xFF9460FF), Color(0xFF80BBFF)) }
    fun selectColor(color: Color) {
        if (color != Color.White) {
            val next = (listOf(Color.White, color) + visibleColors.filter { it != Color.White && it != color }).take(4)
            visibleColors.clear()
            visibleColors.addAll(next)
        }
        penColor = color
        erasing = false
    }


    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal=16.dp,vertical=6.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                ScribbleBoardButton("Close",Icons.Outlined.Close,onClick=onClose)
                listOf(ScribbleBrushStyle.Sketch,ScribbleBrushStyle.Pen,ScribbleBrushStyle.Marker).forEach { style ->
                    ScribbleStyleChip(style,!erasing && brushStyle==style) { brushStyle=style;erasing=false }
                }
            }
            ScribbleInkPad(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                strokes = strokes,
                livePoints = livePoints,
                penColor = penColor.copy(alpha=opacity),
                customWidth = if(erasing) eraserSize else brushSize,
                erasing = erasing,
                brushStyle = brushStyle,
                onStrokeCommitted = { undo.clear() },
                onBegin = { brushOpen=false },
                brushPanel = {
                    if(brushOpen) Column(Modifier.fillMaxWidth().padding(10.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xF2221D3B)).border(1.dp,Color(0x66F5CB7E),RoundedCornerShape(20.dp)).padding(12.dp)) {
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Text(if(erasing) "Eraser size" else "Size",color=Color(0xFFF3D7A0),fontSize=12.sp,modifier=Modifier.width(66.dp))
                            RoundBrushSlider(if(erasing) eraserSize else brushSize,if(erasing) 8f..64f else 2f..28f,Modifier.weight(1f)) { if(erasing) eraserSize=it else brushSize=it }
                            Box(Modifier.size(34.dp),contentAlignment=Alignment.Center) { Box(Modifier.size((if(erasing) eraserSize else brushSize).coerceAtMost(30f).dp).clip(CircleShape).background(penColor.copy(alpha=opacity))) }
                        }
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Text("Opacity",color=Color(0xFFF3D7A0),fontSize=12.sp,modifier=Modifier.width(66.dp))
                            RoundBrushSlider(opacity,.1f..1f,Modifier.weight(1f)) { opacity=it }
                            Text("${(opacity*100).toInt()}%",color=Color.White,fontSize=11.sp,modifier=Modifier.width(34.dp))
                        }
                    }
                },
                controls = {
                    Row(Modifier.fillMaxWidth().padding(10.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        ScribbleBoardButton("Undo",Icons.AutoMirrored.Outlined.Undo) { if(strokes.isNotEmpty()) undo.add(strokes.removeAt(strokes.lastIndex)) }
                        Spacer(Modifier.width(8.dp))
                        ScribbleBoardButton("Redo",Icons.Outlined.Redo) { if(undo.isNotEmpty()) strokes.add(undo.removeAt(undo.lastIndex)) }
                        Spacer(Modifier.weight(1f))
                        ScribbleBoardButton("Clear",Icons.Outlined.Delete) { clearConfirm=true }
                    }
                }
            )

            Row(
                Modifier.padding(horizontal=16.dp,vertical=4.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp)).background(Color.White.copy(alpha=.06f))
                    .border(1.dp,Color(0x55F5CB7E),RoundedCornerShape(24.dp)).padding(horizontal=4.dp,vertical=4.dp),
                verticalAlignment=Alignment.CenterVertically
            ) {
                visibleColors.toList().forEach { color ->
                    Box(Modifier.weight(1f),contentAlignment=Alignment.Center) {
                        ScribbleToolbarControl(selected=!erasing && penColor==color,onClick={selectColor(color)}) {
                            Box(Modifier.fillMaxSize().clip(CircleShape).background(color).border(1.dp,Color.White.copy(alpha=.35f),CircleShape))
                        }
                    }
                }
                Box(Modifier.weight(1f),contentAlignment=Alignment.Center) {
                    ScribbleToolbarControl(selected=customColor,onClick={customColor=true}) {
                        Box(Modifier.fillMaxSize().clip(CircleShape).background(Brush.sweepGradient(listOf(Color.Red,Color.Yellow,Color.Green,Color.Cyan,Color.Blue,Color.Magenta,Color.Red))))
                    }
                }
                Box(Modifier.weight(1f),contentAlignment=Alignment.Center) {
                    ScribbleToolbarControl(selected=erasing,onClick={erasing=true;brushOpen=true}) {
                        Icon(CreateIcons.Eraser,"Eraser",tint=if(erasing) Color(0xFF211338) else Color(0xFFF3D7A0),modifier=Modifier.size(19.dp))
                    }
                }
                Box(Modifier.weight(1f),contentAlignment=Alignment.Center) {
                    ScribbleToolbarControl(selected=brushOpen,onClick={brushOpen=!brushOpen}) {
                        Icon(Icons.Outlined.Tune,"Brush settings",tint=if(brushOpen) Color(0xFF211338) else Color(0xFFF3D7A0),modifier=Modifier.size(19.dp))
                    }
                }
            }
            CreateActionBar(
                small = true,
                goldAccent = true,
                stickerTipKey = "scribble",
                stickerPreview = miniSticker,
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
                            "A hand-drawn scribble",
                            onPackSaved = stickerSaved
                        )
                    }
                }
            )
        }

        if(clearConfirm) androidx.compose.material3.AlertDialog(onDismissRequest={clearConfirm=false},title={Text("Clear drawing?")},text={Text("This removes all strokes from the board.")},confirmButton={androidx.compose.material3.TextButton(onClick={strokes.clear();undo.clear();livePoints.clear();clearConfirm=false}) {Text("Clear")}},dismissButton={androidx.compose.material3.TextButton(onClick={clearConfirm=false}) {Text("Cancel")}})
        if(customColor) ScribbleColourWheel(penColor,onDismiss={customColor=false}) { selectColor(it);customColor=false }
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
                        coordinator.addToStickerPack(CreationKind.Scribble, bmp, "A hand-drawn scribble", onPackSaved=stickerSaved)
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
    customWidth: Float,
    erasing: Boolean,
    brushStyle: ScribbleBrushStyle,
    onStrokeCommitted: () -> Unit,
    onBegin: () -> Unit,
    brushPanel: @Composable () -> Unit,
    controls: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(30.dp)
    val strokeWidth = customWidth
    val eraseMode = erasing

    Box(
        modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF211338), Color(0xFF160C27))))
            .border(1.dp, Color(0x66F5CB7E), shape)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .pointerInput(eraseMode, penColor, brushStyle, strokeWidth) {
                detectDragGestures(
                    onDragStart = { offset ->
                        onBegin()
                        livePoints.clear()
                        livePoints.add(offset)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        livePoints.add(change.position)
                    },
                    onDragEnd = {
                        if (livePoints.size >= 2) {
                            strokes.add(InkStroke(livePoints.toList(), penColor, strokeWidth, eraseMode, brushStyle))
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
                    InkStroke(livePoints.toList(), penColor, strokeWidth, eraseMode, brushStyle),
                    brushStyle,
                    live = true
                )
            }
        }
        if (strokes.isEmpty() && livePoints.isEmpty()) {
            Column(Modifier.align(Alignment.Center).padding(bottom=46.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("Start\nScribbling",color=Color(0xFFF5CB7E),fontFamily=androidx.compose.ui.text.font.FontFamily.Cursive,fontSize=36.sp,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                Text("Ideas start with a simple stroke.",color=Color(0xFFF3D7A0),fontFamily=Outfit,fontSize=12.sp)
            }
        }
        Box(Modifier.align(Alignment.TopCenter)) { controls() }
        Box(Modifier.align(Alignment.BottomCenter)) { brushPanel() }
    }
}

// ───────────────────────────── Quote ─────────────────────────────

private enum class QuoteStep { Write, Design }

@Composable
private fun QuoteScreen(coordinator: SocialCreateCoordinator, onClose: () -> Unit) {
    val stickerSaved = rememberStickerSavedPrompt()
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
            "The words ${shown.take(90)} in sticker lettering",
            onPackSaved = stickerSaved
        )
    }

    // Live preview, rendered off the main thread and debounced while typing.
    // Half resolution keeps it smooth while dragging the position / size / rotate bars.
    val preview by produceState<ImageBitmap?>(null, text, style, aspect, background, shape, shapePhoto, transform) {
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

    // The producer assigns value below; workaround for the bundled Compose lint detector.
    @Suppress("ProduceStateDoesNotAssignValue")
    val miniSticker by produceState<ImageBitmap?>(null,text,style,aspect,shape,shapePhoto,transform) {
        delay(100)
        value = withContext(Dispatchers.Default) {
            com.lumen.launcher.social.StickerMaker.make(render(forSticker=true)).asImageBitmap()
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
            stickerPreview = miniSticker,
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
    stickerPreview: ImageBitmap?,
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
    val designContentScroll = rememberScrollState()
    var adjustOpen by remember { mutableStateOf(false) }
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
        CreateToolHeader("Quote", onClose, onShare, subtitle = "Make your words shine", showShare = false)
        QuotePreviewCard(
            preview=preview, aspect=aspect, isSticker=isSticker,
            transform=transform, onTransform=onTransform,
            modifier=Modifier.fillMaxWidth().height(230.dp).padding(horizontal=16.dp,vertical=6.dp)
        )
        Row(Modifier.fillMaxWidth().padding(horizontal=20.dp),verticalAlignment=Alignment.CenterVertically) {
            Text("Edit text",color=Color(0xFFF5CB7E),fontSize=14.sp,modifier=Modifier.clickable(onClick=onEditText).padding(8.dp))
            Spacer(Modifier.weight(1f))
            Text("Drag \u00b7 Pinch \u00b7 Rotate",color=Color.White.copy(alpha=.65f),fontSize=12.sp)
            Spacer(Modifier.weight(1f))
            Text(if(adjustOpen) "Adjust -" else "Adjust +",color=Color(0xFFF5CB7E),fontSize=14.sp,modifier=Modifier.clickable {adjustOpen=!adjustOpen}.padding(8.dp))
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(designContentScroll)
                .padding(horizontal = 16.dp)
        ) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                CreateSectionLabel("Design",Modifier.weight(1f))
                Text("Swipe for more \u2192",color=Color(0xFFF3D7A0),fontSize=12.sp)
            }
            val designScroll = rememberScrollState()
            Row(Modifier.fillMaxWidth().drawWithContent {
                drawContent()
                if(designScroll.canScrollForward) drawRect(
                    Brush.horizontalGradient(listOf(Color.Transparent,Color(0xFF221D4A)),startX=size.width-28.dp.toPx(),endX=size.width)
                )
            }.horizontalScroll(designScroll), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuoteStyle.entries.forEach { s ->
                    DesignThumb(label = s.label, image = thumbs[s], selected = style == s) { onStyle(s) }
                }
            }

            if(adjustOpen) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CreateSectionLabel("Adjust text", Modifier.weight(1f))
                if (!transform.isDefault) {
                    Text(
                        "Reset",
                        color = Color(0xFFF5CB7E),
                        fontFamily = Outfit,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onTransform(QuoteTextTransform()) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
            QuoteAdjustBar(
                label = "Size",
                valueText = "${(transform.scale * 100).roundToInt()}%",
                value = scaleToBar(transform.scale),
                onValue = { onTransform(transform.copy(scale = barToScale(it))) }
            )
            QuoteAdjustBar(
                label = "Rotate",
                valueText = "${transform.rotation.roundToInt()}°",
                value = transform.rotation / 180f + 0.5f,
                onValue = { onTransform(transform.copy(rotation = (it - 0.5f) * 180f)) }
            )

                QuoteAdjustBar(label="Left / right",valueText="",value=transform.offsetX/.8f+.5f,onValue={onTransform(transform.copy(offsetX=(it-.5f)*.8f))})
                QuoteAdjustBar(label="Up / down",valueText="",value=transform.offsetY/.8f+.5f,onValue={onTransform(transform.copy(offsetY=(it-.5f)*.8f))})
            }
            CreateSectionLabel("Shape")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuoteShape.entries.forEach { sh ->
                    val sel = shape == sh
                    GoldQuoteChip(sh.label, sel, leading = { ShapeGlyph(sh, chipIconTint(sel)) }) { onShape(sh) }
                }
            }
            Row(
                Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GoldQuoteChip(
                    if (hasPhoto) "Change photo" else "Photo in shape",
                    hasPhoto,
                    leading = { Icon(Icons.Outlined.Image, null, tint = chipIconTint(hasPhoto), modifier = Modifier.size(20.dp)) },
                    onClick = onPickPhoto
                )
                if (hasPhoto) {
                    GoldQuoteChip("Remove photo", false, onClick = onClearPhoto)
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
                    GoldQuoteChip(label, sel, leading = {
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
                    GoldQuoteChip(label, sel, leading = { AspectGlyph(ratio.first, ratio.second, chipIconTint(sel)) }) { onAspect(a) }
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
        QuoteScrollHint(designContentScroll)
        CreateActionBar(onSave = onSave, onShare = onShare, onSticker = onSticker, small=true, goldAccent=true, stickerTipKey="quote", stickerPreview=stickerPreview)
    }
}

@Composable
private fun QuoteScrollHint(scroll: androidx.compose.foundation.ScrollState) {
    var dismissed by remember { mutableStateOf(false) }
    val visible = scroll.canScrollForward && !dismissed
    val movement = remember { androidx.compose.animation.core.Animatable(0f) }
    val opacity by androidx.compose.animation.core.animateFloatAsState(
        if(visible) .7f else 0f,
        animationSpec=androidx.compose.animation.core.tween(350),label="Quote scroll hint"
    )
    LaunchedEffect(scroll.value) {
        if(scroll.value > 4) dismissed = true
    }
    LaunchedEffect(visible) {
        if(visible) {
            delay(600)
            while(true) {
                movement.animateTo(2f,androidx.compose.animation.core.tween(450))
                movement.animateTo(0f,androidx.compose.animation.core.tween(450))
                delay(2100)
            }
        } else movement.snapTo(0f)
    }
    Box(Modifier.fillMaxWidth().height(16.dp),contentAlignment=Alignment.Center) {
        Canvas(Modifier.size(18.dp,14.dp).graphicsLayer {
            alpha=opacity
            translationY=movement.value.dp.toPx()
        }) {
            repeat(2) { index ->
                val top=index*5.dp.toPx()
                val path=androidx.compose.ui.graphics.Path().apply {
                    moveTo(size.width*.2f,top+2.dp.toPx())
                    lineTo(size.width*.5f,top+5.dp.toPx())
                    lineTo(size.width*.8f,top+2.dp.toPx())
                }
                drawPath(path,Color(0xFFF5CB7E).copy(alpha=if(index==0) .7f else 1f),
                    style=Stroke(1.5.dp.toPx(),cap=androidx.compose.ui.graphics.StrokeCap.Round,join=androidx.compose.ui.graphics.StrokeJoin.Round))
            }
        }
    }
}

@Composable
private fun QuoteAdjustBar(label: String, valueText: String, value: Float, onValue: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Text(label,color=Color(0xFFF3D7A0),fontSize=12.sp,modifier=Modifier.width(74.dp))
        RoundBrushSlider(value,0f..1f,Modifier.weight(1f),onValue)
        Text(valueText,color=Color.White,fontSize=12.sp,modifier=Modifier.width(44.dp))
    }
}

@Composable
private fun GoldQuoteChip(label: String, selected: Boolean, leading: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    CreateChip(label,selected,leading=leading,goldAccent=true,onClick=onClick)
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
    modifier: Modifier,
    transform: QuoteTextTransform = QuoteTextTransform(),
    onTransform: ((QuoteTextTransform) -> Unit)? = null
) {
    val currentTransform by rememberUpdatedState(transform)
    val updateTransform by rememberUpdatedState(onTransform)
    val ratio = aspect.width.toFloat() / aspect.height
    Box(modifier, contentAlignment = Alignment.Center) {
        val cardShape = RoundedCornerShape(26.dp)
        Box(
            Modifier
                .aspectRatio(ratio, matchHeightConstraintsFirst = true)
                .shadow(18.dp, cardShape, spotColor = Color(0xFFF5CB7E))
                .clip(cardShape)
                .then(if(onTransform != null) Modifier.pointerInput(aspect) {
                    detectTransformGestures { _, pan, zoom, rotation ->
                        val old = currentTransform
                        updateTransform?.invoke(old.copy(
                            offsetX=(old.offsetX+pan.x/size.width).coerceIn(-.4f,.4f),
                            offsetY=(old.offsetY+pan.y/size.height).coerceIn(-.4f,.4f),
                            scale=(old.scale*zoom).coerceIn(.5f,1.8f),
                            rotation=(old.rotation+rotation).coerceIn(-90f,90f)
                        ))
                    }
                } else Modifier)
                .then(if (isSticker) Modifier.drawBehind { checkerboard() } else Modifier.background(Color(0xFF1B1030)))
                .border(1.dp, Color(0x40F5CB7E), cardShape)
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
                .border(if (selected) 2.5.dp else 1.dp, if (selected) Color(0xFFF5CB7E) else Color(0x2EF5CB7E), shape)
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
private data class PhotoMark(val points: List<Offset>, val color: Color, val widthFraction: Float, val tool: PhotoShape = PhotoShape.Pen, val erasing: Boolean = false)

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
    var shapeMenu by remember { mutableStateOf(false) }
    var brushPanel by remember { mutableStateOf(false) }
    var erasing by remember { mutableStateOf(false) }
    var brushSize by remember { mutableStateOf(10f) }
    var eraserSize by remember { mutableStateOf(28f) }
    val tipPrefs = remember { context.getSharedPreferences("photo_tips",android.content.Context.MODE_PRIVATE) }
    var showHint by remember { mutableStateOf(!tipPrefs.getBoolean("draw_seen",false)) }
    fun dismissHint() { showHint=false; tipPrefs.edit().putBoolean("draw_seen",true).apply() }

    var uri by remember { mutableStateOf<Uri?>(null) }
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var cutoutSource by remember { mutableStateOf<Bitmap?>(null) }
    var cropSource by remember { mutableStateOf<Bitmap?>(null) }
    var beforeCrop by remember { mutableStateOf<Bitmap?>(null) }
    var loading by remember { mutableStateOf(false) }
    val marks = remember { mutableStateListOf<PhotoMark>() }
    var shapeTool by remember { mutableStateOf(PhotoShape.Pen) }
    var moveMode by remember { mutableStateOf(false) }
    var selectedMark by remember { mutableStateOf<Int?>(null) }
    val live = remember { mutableStateListOf<Offset>() }
    var penColor by remember { mutableStateOf(Color.White) }
    var sizeIdx by remember { mutableIntStateOf(2) }
    var showMoreColors by remember { mutableStateOf(false) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    // Blur boxes (e.g. to hide faces); strength 1.0 = unrecognizable.
    var blurMode by remember { mutableStateOf(false) }
    val blurBoxes = remember { mutableStateListOf<BlurBox>() }
    var selectedBlur by remember { mutableStateOf<Long?>(null) }
    var blurLevels by remember { mutableStateOf<BlurLevels?>(null) }
    var levelImages by remember { mutableStateOf<List<ImageBitmap>>(emptyList()) }
    val undoEdits = remember { mutableStateListOf<Pair<List<PhotoMark>,List<BlurBox>>>() }
    val redoEdits = remember { mutableStateListOf<Pair<List<PhotoMark>,List<BlurBox>>>() }
    fun checkpoint() {
        undoEdits.add(marks.toList() to blurBoxes.toList())
        if(undoEdits.size > 50) undoEdits.removeAt(0)
        redoEdits.clear()
    }
    fun restoreEdit(state: Pair<List<PhotoMark>,List<BlurBox>>) {
        marks.clear(); marks.addAll(state.first)
        blurBoxes.clear(); blurBoxes.addAll(state.second)
        selectedMark=null; selectedBlur=null; live.clear()
    }
    val palette = remember { mutableStateListOf(Color.White,Color(0xFFF5CB7E),Color(0xFF9460FF),Color(0xFFFF88C4)) }
    fun selectColor(color: Color) {
        if(color != Color.White) {
            val next=(listOf(Color.White,color)+palette.filter {it!=Color.White && it!=color}).take(4)
            palette.clear();palette.addAll(next)
        }
        penColor=color;erasing=false
    }


    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { picked ->
        if (picked != null) uri = picked
    }
    LaunchedEffect(Unit) { picker.launch("image/*") }
    LaunchedEffect(uri) {
        val u = uri ?: return@LaunchedEffect
        undoEdits.clear();redoEdits.clear()
        loading = true
        photo = null
        cropSource = null
        selectedMark = null
        live.clear()
        marks.clear()
        blurBoxes.clear()
        selectedBlur = null
        blurMode = false
        blurLevels = null
        levelImages = emptyList()
        photo = withContext(Dispatchers.IO) { decodeForMarkup(context, u) }
        beforeCrop = null
        loading = false
    }
    LaunchedEffect(photo) {
        blurLevels = null
        levelImages = emptyList()
        photo?.let { p ->
            val levels = withContext(Dispatchers.Default) { BlurLevels.build(p) }
            blurLevels = levels
            levelImages = levels.levels.map { it.asImageBitmap() }
        }
    }

    fun markBounds(index: Int, rect: Rect): Rect? {
        val mark = marks.getOrNull(index) ?: return null
        return photoShapePath(mark.points.map { Offset(rect.left + it.x * rect.width, rect.top + it.y * rect.height) }, mark.tool).getBounds()
    }
    fun hitMark(point: Offset, rect: Rect): Int? =
        marks.indices.reversed().firstOrNull { !marks[it].erasing && markBounds(it, rect)?.inflate(18f)?.contains(point) == true }

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
        checkpoint()
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
        if (loading || (blurBoxes.isNotEmpty() && blurLevels == null)) {
            android.widget.Toast.makeText(context, "Please wait for the photo edits to finish.", android.widget.Toast.LENGTH_SHORT).show()
            return null
        }
        val out = base.copy(Bitmap.Config.ARGB_8888, true)
        blurLevels?.applyTo(out, blurBoxes.toList())
        val canvas = android.graphics.Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val marksLayer = canvas.saveLayer(0f,0f,out.width.toFloat(),out.height.toFloat(),null)
        marks.forEach { m ->
            paint.xfermode = if(m.erasing) android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR) else null
            paint.color = m.color.toArgb()
            paint.strokeWidth = m.widthFraction * out.width
            val pts = m.points.map { Offset(it.x * out.width, it.y * out.height) }
            canvas.drawPath(photoShapePath(pts, m.tool).asAndroidPath(), paint)
        }
        canvas.restoreToCount(marksLayer)
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

    cropSource?.let { source ->
        PhotoCropDialog(source, onDismiss = { cropSource = null }, onApply = { cropped ->
            undoEdits.clear();redoEdits.clear()
            beforeCrop = source
            marks.clear()
            live.clear()
            blurBoxes.clear()
            selectedBlur = null
            selectedMark = null
            photo = cropped
            cropSource = null
        })
    }

    cutoutSource?.let { PhotoCutoutDialog(it, coordinator) { cutoutSource = null } }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically) {
            ScribbleBoardButton("Close",Icons.Outlined.Close,onClick=onClose)
            Text("Photo",color=Color.White,fontSize=22.sp,modifier=Modifier.weight(1f).padding(start=10.dp))
            androidx.compose.material3.TextButton(onClick={cropSource=rendered()},enabled=photo!=null) { Text("Crop",color=Color(0xFFF5CB7E)) }
            androidx.compose.material3.TextButton(onClick={picker.launch("image/*")}) { Text("Change",color=Color(0xFFF5CB7E)) }
        }

        if (photo == null) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.Image, null, tint = Color(0xFFF5CB7E), modifier = Modifier.size(44.dp))
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
                                .shadow(14.dp, shape, spotColor = Color(0xFFF5CB7E))
                                .clip(shape)
                                .background(Brush.horizontalGradient(listOf(Color(0xFFF3D7A0),Color(0xFFF5CB7E))))
                                .clickable { picker.launch("image/*") }
                                .padding(horizontal = 28.dp, vertical = 14.dp)
                        )
                    }
                }
            }
        } else {

        androidx.compose.material3.TextButton(onClick = { cutoutSource = rendered() }, enabled = !loading,
            modifier = Modifier.fillMaxWidth()) { Text("Remove background \u00B7 Make sticker", color = Color(0xFFF5CB7E), fontFamily = Outfit) }

        // Photo card
        val cardShape = RoundedCornerShape(30.dp)
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .clip(cardShape)
                .background(Color(0xFF160F26))
                .padding(4.dp)
        ) {
            val bmp = photo!!
            val image = remember(bmp) { bmp.asImageBitmap() }
            Box(
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { boxSize = it }
                    .clipToBounds()
                    .pointerInput(blurMode, imageRect, moveMode) {
                        // Blur mode: tap a box to select it (tap elsewhere to deselect).
                        val rect = imageRect ?: return@pointerInput
                        if (moveMode) {
                            detectTapGestures { o -> selectedMark = hitMark(o, rect) }
                            return@pointerInput
                        }
                        if (!blurMode) return@pointerInput
                        detectTapGestures { o ->
                            selectedBlur = blurBoxes.lastOrNull { screenRectOf(it, rect).contains(o) }?.id
                        }
                    }
                    .pointerInput(bmp, penColor, brushSize, eraserSize, erasing, imageRect, blurMode, shapeTool, moveMode) {
                        val rect = imageRect ?: return@pointerInput
                        if (moveMode) {
                            var moving: Int? = null
                            detectDragGestures(
                                onDragStart = { o -> checkpoint(); brushPanel=false; dismissHint(); moving = hitMark(o, rect); selectedMark = moving },
                                onDrag = { change, delta ->
                                    moving?.let { index ->
                                        marks.getOrNull(index)?.let { mark ->
                                            change.consume()
                                            val minX = mark.points.minOf { it.x }
                                            val maxX = mark.points.maxOf { it.x }
                                            val minY = mark.points.minOf { it.y }
                                            val maxY = mark.points.maxOf { it.y }
                                            val dx = (delta.x / rect.width).coerceIn(-minX, (1f - maxX).coerceAtLeast(-minX))
                                            val dy = (delta.y / rect.height).coerceIn(-minY, (1f - maxY).coerceAtLeast(-minY))
                                            marks[index] = mark.copy(points = mark.points.map { it + Offset(dx, dy) })
                                        }
                                    }
                                }, onDragEnd = { moving = null }, onDragCancel = { moving = null }
                            )
                            return@pointerInput
                        }
                        if (blurMode) {
                            // Drag a corner handle to resize, drag inside a box to move it.
                            val handle = 28.dp.toPx()
                            var activeId: Long? = null
                            var corner = -1
                            detectDragGestures(
                                onDragStart = { o ->
                                    checkpoint(); brushPanel=false; dismissHint()
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
                                checkpoint(); brushPanel=false; shapeMenu=false; dismissHint()
                                live.clear()
                                live.add(norm(o))
                            },
                            onDrag = { c, _ ->
                                c.consume()
                                live.add(norm(c.position))
                            },
                            onDragEnd = {
                                if (live.size >= 2) {
                                    val px = with(density) { (if(erasing) eraserSize else brushSize).dp.toPx() }
                                    marks.add(PhotoMark(live.toList(), penColor, px / rect.width, if(erasing) PhotoShape.Pen else shapeTool, erasing))
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
                        drawContext.canvas.saveLayer(Rect(0f,0f,size.width,size.height),androidx.compose.ui.graphics.Paint())
                        marks.forEach { m ->
                            drawPath(
                                photoShapePath(m.points.map(::toScreen), m.tool),
                                m.color,
                                blendMode=if(m.erasing) androidx.compose.ui.graphics.BlendMode.Clear else androidx.compose.ui.graphics.BlendMode.SrcOver,
                                style = Stroke(m.widthFraction * rect.width, cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )
                        }
                        if (live.size >= 2) {
                            drawPath(
                                photoShapePath(live.map(::toScreen), if(erasing) PhotoShape.Pen else shapeTool),
                                penColor,
                                blendMode=if(erasing) androidx.compose.ui.graphics.BlendMode.Clear else androidx.compose.ui.graphics.BlendMode.SrcOver,
                                style = Stroke((if(erasing) eraserSize else brushSize).dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )
                        }
                        drawContext.canvas.restore()
                    }
                    if (moveMode) selectedMark?.let { index ->
                        markBounds(index, rect)?.inflate(6.dp.toPx())?.let { bounds ->
                            drawRect(Color.White.copy(.8f), bounds.topLeft, bounds.size, style = Stroke(1.dp.toPx()))
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
                                    drawCircle(Color(0xFFF5CB7E), radius = 9.dp.toPx(), center = c, style = Stroke(2.dp.toPx()))
                                }
                            }
                        }
                    }
                }
            }
            Row(Modifier.align(Alignment.TopStart).padding(10.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                ScribbleBoardButton("Undo",Icons.AutoMirrored.Outlined.Undo,darkGlass=true) {
                    if(undoEdits.isNotEmpty()) {redoEdits.add(marks.toList() to blurBoxes.toList());restoreEdit(undoEdits.removeAt(undoEdits.lastIndex))}
                }
                ScribbleBoardButton("Redo",Icons.Outlined.Redo,darkGlass=true) {
                    if(redoEdits.isNotEmpty()) {undoEdits.add(marks.toList() to blurBoxes.toList());restoreEdit(redoEdits.removeAt(redoEdits.lastIndex))}
                }
            }
            if(beforeCrop!=null) Text("Undo crop",color=Color(0xFFF5CB7E),fontSize=12.sp,modifier=Modifier.align(Alignment.TopEnd).padding(12.dp).clickable {
                photo=beforeCrop;beforeCrop=null;marks.clear();blurBoxes.clear();live.clear();undoEdits.clear();redoEdits.clear();selectedMark=null;selectedBlur=null
            })
            if(brushPanel) Row(Modifier.align(Alignment.BottomCenter).padding(10.dp).fillMaxWidth().background(Color(0xF2221D3B),RoundedCornerShape(18.dp)).padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
                Text(if(erasing) "Eraser size" else "Brush size",color=Color(0xFFF3D7A0),fontSize=12.sp)
                RoundBrushSlider(if(erasing) eraserSize else brushSize,if(erasing) 8f..64f else 2f..28f,Modifier.weight(1f)) {if(erasing) eraserSize=it else brushSize=it}
                Box(Modifier.size(30.dp),contentAlignment=Alignment.Center) {Box(Modifier.size((if(erasing) eraserSize else brushSize).coerceAtMost(28f).dp).background(Color.White,CircleShape))}
            }
            if(showHint && !brushPanel) Text("Choose a tool, then drag on the photo.  Got it",color=Color(0xFFF3D7A0),fontSize=12.sp,
                modifier=Modifier.align(Alignment.BottomCenter).padding(8.dp).background(Color(0xDD191624),RoundedCornerShape(12.dp)).clickable {dismissHint()}.padding(10.dp))
        }

        Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=4.dp).horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Box {
                GoldQuoteChip("Draw",!blurMode && !moveMode && !erasing,leading={PhotoToolGlyph(shapeTool,if(!blurMode && !moveMode && !erasing) Color(0xFF211B13) else Color.White)}) {
                    if(!blurMode && !moveMode && !erasing) shapeMenu=!shapeMenu
                    blurMode=false;moveMode=false;erasing=false;selectedBlur=null
                }
                androidx.compose.material3.DropdownMenu(expanded=shapeMenu,onDismissRequest={shapeMenu=false}) {
                    PhotoShape.entries.forEach {tool ->
                        androidx.compose.material3.DropdownMenuItem(
                            text={Text(tool.name,color=if(tool==shapeTool) Color(0xFFF5CB7E) else Color.White)},
                            leadingIcon={PhotoToolGlyph(tool,Color(0xFFF5CB7E))},
                            onClick={shapeTool=tool;shapeMenu=false;erasing=false})
                    }
                }
            }
            GoldQuoteChip("Select",moveMode) {moveMode=true;blurMode=false;erasing=false;brushPanel=false;shapeMenu=false;live.clear()}
            GoldQuoteChip("Blur",blurMode) {moveMode=false;erasing=false;brushPanel=false;shapeMenu=false;if(blurBoxes.isEmpty()) addBlurBox() else blurMode=true}
        }
        Row(Modifier.padding(horizontal=16.dp,vertical=4.dp).fillMaxWidth()
            .background(Color.White.copy(alpha=.06f),RoundedCornerShape(24.dp))
            .border(1.dp,Color(0x55F5CB7E),RoundedCornerShape(24.dp)).padding(4.dp),
            verticalAlignment=Alignment.CenterVertically) {
            if(blurMode) {
                androidx.compose.material3.TextButton(onClick={addBlurBox()}) {Text("Add box",color=Color(0xFFF5CB7E),fontSize=12.sp)}
                val sel=blurBoxes.firstOrNull {it.id==selectedBlur}
                Text("Strength",color=Color(0xFFF3D7A0),fontSize=11.sp)
                RoundBrushSlider(sel?.strength ?: 1f,0f..1f,Modifier.weight(1f)) {v ->
                    val i=blurBoxes.indexOfFirst {it.id==selectedBlur}
                    if(i>=0) {checkpoint();blurBoxes[i]=blurBoxes[i].copy(strength=v)}
                }
                ScribbleBoardButton("Remove blur",Icons.Outlined.Delete) {
                    if(selectedBlur!=null) {checkpoint();blurBoxes.removeAll {it.id==selectedBlur};selectedBlur=null}
                }
            } else {
                palette.toList().forEach {color ->
                    Box(Modifier.weight(1f),contentAlignment=Alignment.Center) {
                        ScribbleToolbarControl(!erasing && penColor==color,{selectColor(color)}) {
                            Box(Modifier.fillMaxSize().background(color,CircleShape))
                        }
                    }
                }
                Box(Modifier.weight(1f),contentAlignment=Alignment.Center) {
                    ScribbleToolbarControl(showMoreColors,{showMoreColors=true}) {
                        Box(Modifier.fillMaxSize().clip(CircleShape).background(Brush.sweepGradient(listOf(Color.Red,Color.Yellow,Color.Green,Color.Cyan,Color.Blue,Color.Magenta,Color.Red))))
                    }
                }
                Box(Modifier.weight(1f),contentAlignment=Alignment.Center) {
                    ScribbleToolbarControl(erasing,{erasing=true;moveMode=false;brushPanel=true}) {
                        Icon(CreateIcons.Eraser,"Eraser",tint=if(erasing) Color(0xFF211B13) else Color(0xFFF3D7A0),modifier=Modifier.size(19.dp))
                    }
                }
                Box(Modifier.weight(1f),contentAlignment=Alignment.Center) {
                    ScribbleToolbarControl(brushPanel,{brushPanel=!brushPanel}) {
                        Icon(Icons.Outlined.Tune,"Brush size",tint=if(brushPanel) Color(0xFF211B13) else Color(0xFFF3D7A0),modifier=Modifier.size(19.dp))
                    }
                }
            }
        }
        if(showMoreColors) ScribbleColourWheel(penColor,onDismiss={showMoreColors=false}) {selectColor(it);showMoreColors=false}

        CreateActionBar(
            small=true,goldAccent=true,
            onSave = ::save,
            onShare = ::share
        )
        }
    }
}

@Composable
private fun PhotoToolGlyph(tool: PhotoShape, color: Color) {
    Canvas(Modifier.size(22.dp)) {
        val path = when(tool) {
            PhotoShape.Circle -> Path().apply {addOval(Rect(size.width*.16f,size.height*.16f,size.width*.84f,size.height*.84f))}
            PhotoShape.Square -> Path().apply {addRect(Rect(size.width*.16f,size.height*.16f,size.width*.84f,size.height*.84f))}
            PhotoShape.Rectangle -> Path().apply {addRect(Rect(size.width*.1f,size.height*.26f,size.width*.9f,size.height*.74f))}
            PhotoShape.Pen -> photoShapePath(listOf(Offset(size.width*.15f,size.height*.8f),Offset(size.width*.45f,size.height*.2f),Offset(size.width*.65f,size.height*.65f),Offset(size.width*.85f,size.height*.2f)),tool)
            else -> photoShapePath(listOf(Offset(size.width*.18f,size.height*.82f),Offset(size.width*.82f,size.height*.18f)),tool)
        }
        drawPath(path,color,style=Stroke(2.dp.toPx(),cap=StrokeCap.Round,join=StrokeJoin.Round))
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

@Composable
private fun ScribbleStyleChip(style: ScribbleBrushStyle, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(20.dp))
            .background(if(selected) Color(0xFFF5CB7E) else CreatePalette.ChipFill)
            .border(1.dp,if(selected) Color(0xFFF3D7A0) else Color(0x55F5CB7E),RoundedCornerShape(20.dp))
            .clickable(onClick=onClick).padding(horizontal=12.dp,vertical=7.dp),
        verticalAlignment=Alignment.CenterVertically
    ) {
        Box(Modifier.size(18.dp),contentAlignment=Alignment.Center) { BrushStyleIcon(style,selected) }
        Spacer(Modifier.width(6.dp))
        Text(style.name,color=if(selected) Color(0xFF211B13) else Color.White,fontSize=14.sp,fontWeight=FontWeight.Medium)
    }
}

@Composable
private fun ScribbleToolbarControl(selected: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).clickable(onClick=onClick)
            .then(if(selected) Modifier.border(2.dp,Color(0xFFF5CB7E),CircleShape) else Modifier)
            .padding(4.dp),
        contentAlignment=Alignment.Center
    ) {
        Box(
            Modifier.fillMaxSize().clip(CircleShape)
                .background(if(selected) Color(0xFFF5CB7E) else Color(0x441A1733))
                .border(1.dp,Color(0x55F5CB7E),CircleShape),
            contentAlignment=Alignment.Center
        ) { content() }
    }
}

@Composable
private fun ScribbleBoardButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean = false, darkGlass: Boolean = false, onClick: () -> Unit) {
    androidx.compose.material3.IconButton(onClick=onClick,modifier=Modifier.size(40.dp).clip(CircleShape)
        .background(if(selected) Color(0xFFF5CB7E) else if(darkGlass) Color(0xE6191C24) else Color(0x441A1733))
        .border(1.dp,Color(0x55F5CB7E),CircleShape)) {
        Icon(icon,label,tint=if(selected) Color(0xFF211338) else Color(0xFFF3D7A0),modifier=Modifier.size(19.dp))
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun RoundBrushSlider(value: Float, range: ClosedFloatingPointRange<Float>, modifier: Modifier, onChange: (Float) -> Unit) {
    androidx.compose.material3.Slider(value=value,onValueChange=onChange,valueRange=range,modifier=modifier,
        colors=androidx.compose.material3.SliderDefaults.colors(activeTrackColor=Color(0xFFF5CB7E),inactiveTrackColor=Color(0x44F5CB7E)),
        thumb={Box(Modifier.size(24.dp).clip(CircleShape).background(Color.White).border(3.dp,Color(0xFFF5CB7E),CircleShape))},
        track={ slider -> androidx.compose.material3.SliderDefaults.Track(sliderState=slider,drawStopIndicator=null,thumbTrackGapSize=0.dp) })
}
