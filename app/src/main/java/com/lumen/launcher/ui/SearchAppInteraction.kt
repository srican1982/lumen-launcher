package com.lumen.launcher.ui

import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import com.lumen.launcher.ui.theme.Outfit
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.lumen.launcher.data.AppInfo
import com.lumen.launcher.ui.theme.Lumen
import com.lumen.launcher.vm.LauncherUiState
import com.lumen.launcher.vm.LauncherViewModel
import kotlin.math.roundToInt

class SearchAppInteraction {
    var lifted by mutableStateOf<AppInfo?>(null)
    var menu by mutableStateOf<AppInfo?>(null)
    var position by mutableStateOf(Offset.Zero)
    var hideBounds by mutableStateOf(Rect.Zero)
    var editing by mutableStateOf(false)
    val overHide get() = hideBounds.contains(position)
    fun clear() { lifted = null; menu = null }
}
val LocalSearchAppInteraction = staticCompositionLocalOf<SearchAppInteraction?> { null }

@Composable
fun Modifier.searchAppContact(app: AppInfo, vm: LauncherViewModel): Modifier {
    val interaction = LocalSearchAppInteraction.current ?: return this.clickable { vm.launch(app) }
    var bounds by remember { mutableStateOf(Rect.Zero) }
    var pickup by remember { mutableStateOf(Offset.Zero) }
    val haptics = LocalHapticFeedback.current
    return onGloballyPositioned { bounds = it.boundsInRoot() }.iconContact(
        key = app.key, allowDrag = true, onPhase = {}, onLaunch = { vm.launch(app) },
        onLift = {
            pickup = bounds.center
            interaction.position = pickup
            interaction.menu = null
            interaction.lifted = app
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        },
        onLongPress = { interaction.lifted = null; interaction.menu = app },
        onDrag = { interaction.position = pickup + it },
        onDragEnd = {
            if (interaction.overHide) vm.hideAppFromSearch(app)
            interaction.clear()
        },
        onDragCancel = { interaction.clear() }
    )
}

@Composable
fun SearchAppOverlay(interaction: SearchAppInteraction, origin: Offset, state: LauncherUiState, vm: LauncherViewModel) {
    val context = LocalContext.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val app = interaction.lifted ?: interaction.menu
    if (app != null) {
        if (interaction.menu != null) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .18f)).clickable { interaction.clear() })
        if (interaction.lifted != null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val scale by animateFloatAsState(if (interaction.overHide) 1.06f else 1f,
                    animationSpec = spring(stiffness = 450f), label = "Lumen pad hover")
                val glass = LocalGlass.current
                Row(Modifier.graphicsLayer { scaleX = scale; scaleY = scale }
                    .clip(RoundedCornerShape(30.dp))
                    .background(Brush.verticalGradient(listOf(glass.airyTop, glass.airyBottom)))
                    .background(if (interaction.overHide) Lumen.Accent.copy(alpha = .18f) else Color.Transparent)
                    .onGloballyPositioned { interaction.hideBounds = it.boundsInRoot() }
                    .padding(horizontal = 22.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LumenMark(size = 44.dp, glow = true)
                    Column {
                        Text("Lumen", color = Lumen.Text, fontFamily = Outfit, fontSize = 19.sp)
                        Text("Hide app", color = Lumen.Muted, fontFamily = Outfit, fontSize = 13.sp)
                    }
                }
            }
            val pad = with(density) { 36.dp.toPx() }
            Box(Modifier.offset { IntOffset((interaction.position.x - origin.x - pad).roundToInt(), (interaction.position.y - origin.y - pad).roundToInt()) }
                .size(72.dp).glass(RoundedCornerShape(24.dp), LocalGlass.current).padding(10.dp), contentAlignment = Alignment.Center) {
                AppIcon(app.packageName, app.activityName, 52.dp, vm.icons)
            }
        } else {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 160.dp).denseGlass(RoundedCornerShape(28.dp)).padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(app.packageName, app.activityName, 48.dp, vm.icons)
                    Text(app.label, color = Lumen.Text, fontSize = 20.sp, modifier = Modifier.padding(12.dp))
                }
                fun close() { interaction.clear() }
                Text("Edit ${state.activeSpace.title} apps", color = Lumen.Text, modifier = Modifier.fillMaxWidth().clickable { close(); interaction.editing = true }.padding(14.dp))
                Text("Share app", color = Lumen.Text, modifier = Modifier.fillMaxWidth().clickable {
                    close()
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "https://play.google.com/store/apps/details?id=${app.packageName}")
                    }, "Share ${app.label}"))
                }.padding(14.dp))
                Text("App info", color = Lumen.Text, modifier = Modifier.fillMaxWidth().clickable { close(); vm.openAppInfo(app) }.padding(14.dp))
                Text("Hide app", color = Lumen.Text, modifier = Modifier.fillMaxWidth().clickable { close(); vm.hideAppFromSearch(app) }.padding(14.dp))
                if (app.packageName != context.packageName) Text("Delete app", color = Color(0xFFFF8A8A), modifier = Modifier.fillMaxWidth().clickable { close(); vm.uninstallApp(app) }.padding(14.dp))
            }
        }
    }
    if (interaction.editing) AppEditPage(state, vm, onDismiss = { interaction.editing = false })
}

