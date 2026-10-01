package com.lumen.launcher.ui

import android.app.Activity
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lumen.launcher.vm.LauncherUiState
import com.lumen.launcher.vm.LauncherViewModel

private val WorkspaceAccent = Color(0xFFBF94EE)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspacePopup(state: LauncherUiState, vm: LauncherViewModel) {
    MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(primary = WorkspaceAccent, outline = WorkspaceAccent.copy(alpha = .4f))) {
        ModalBottomSheet(
            onDismissRequest = { vm.setWorkspaceOpen(false) },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Color.Transparent,
            scrimColor = Color(0x55090D16),
            dragHandle = null,
            contentWindowInsets = { WindowInsets.safeDrawing }
        ) {
            val frame = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
            Column(Modifier.fillMaxWidth()
                .background(Brush.linearGradient(listOf(Color(0xBDA5B7B0), Color(0xAD697A8F), Color(0xAD897B9F))), frame)
                .border(1.dp, Color.White.copy(alpha = .35f), frame)
                .padding(horizontal = 16.dp)
            ) {
                BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = .65f), modifier = Modifier.align(androidx.compose.ui.Alignment.CenterHorizontally))
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
                    BackHandler { vm.setWorkspaceOpen(false) }
                    ModeWorkspace(state, vm)
                }
            }
        }
    }
}

@Composable
fun WorkspacePill(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier.background(
        Brush.verticalGradient(listOf(Color.White.copy(alpha = .15f), Color.White.copy(alpha = .03f))), RoundedCornerShape(50)),
        border = BorderStroke(1.dp, WorkspaceAccent.copy(alpha = .48f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = WorkspaceAccent), content = content)
}

/** A small vector map keeps the illustration crisp at every icon size. */
@Composable
fun TravelMapEmblem(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val gold = Color(0xFFE9C96F)
        val w = size.width; val h = size.height
        val map = Path().apply {
            moveTo(w*.08f,h*.32f); lineTo(w*.34f,h*.20f); lineTo(w*.63f,h*.34f)
            lineTo(w*.92f,h*.23f); lineTo(w*.92f,h*.83f); lineTo(w*.64f,h*.95f)
            lineTo(w*.34f,h*.81f); lineTo(w*.08f,h*.94f); close()
        }
        drawPath(map,gold, style = Stroke(2.dp.toPx()))
        drawLine(gold,Offset(w*.34f,h*.22f),Offset(w*.34f,h*.8f),1.dp.toPx())
        drawLine(gold,Offset(w*.64f,h*.55f),Offset(w*.64f,h*.92f),1.dp.toPx())
        for(i in 0..4) drawCircle(gold,1.4.dp.toPx(),Offset(w*(.2f+i*.13f),h*(.58f+ .12f*kotlin.math.sin(i.toFloat()))))
        val pin = Path().apply {
            moveTo(w*.71f,h*.52f)
            cubicTo(w*.40f,h*.15f,w*.54f,0f,w*.71f,0f)
            cubicTo(w*.91f,0f,w*.99f,h*.17f,w*.71f,h*.52f)
            close()
        }
        drawPath(pin,gold)
        drawCircle(Color(0xFF666C50),w*.055f,Offset(w*.71f,h*.16f))
    }
}

/** Blur the system wallpaper behind our transparent launcher window without reading it. */
@Composable
fun WallpaperWindowBlur(enabled: Boolean, radius: Dp) {
    val context = LocalContext.current
    val window = (context as? Activity)?.window ?: return
    val pixels = with(LocalDensity.current) { radius.roundToPx() }
    DisposableEffect(window, enabled, pixels) {
        if (Build.VERSION.SDK_INT >= 31) {
            val old = window.attributes.blurBehindRadius
            val hadFlag = window.attributes.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND != 0
            if (enabled) {
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                window.attributes = window.attributes.apply { blurBehindRadius = pixels }
            }
            onDispose {
                if (enabled) {
                    window.attributes = window.attributes.apply { blurBehindRadius = old }
                    if (!hadFlag) window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                }
            }
        } else onDispose { }
    }
}
