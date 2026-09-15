package com.lumen.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.launcher.alarm.AlarmTones
import com.lumen.launcher.data.AppInfo
import com.lumen.launcher.data.DockResolver
import com.lumen.launcher.data.GestureAction
import com.lumen.launcher.data.TouchpadHaptics
import com.lumen.launcher.ui.theme.Lumen
import com.lumen.launcher.ui.theme.Outfit
import com.lumen.launcher.util.AssistantRole
import com.lumen.launcher.vm.LauncherUiState
import com.lumen.launcher.vm.LauncherViewModel

@Composable
fun BottomMenu(
    title: String,
    subtitle: String? = null,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.42f))
                .clickable(onClick = onDismiss)
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(12.dp)
                .denseGlass(RoundedCornerShape(Lumen.SheetRadius))
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 14.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.28f))
            )
            Text(
                title,
                color = Lumen.Text,
                fontSize = 22.sp,
                fontFamily = Outfit,
                fontWeight = FontWeight.Light
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    color = Lumen.Faint,
                    fontSize = 13.sp,
                    fontFamily = Outfit,
                    fontWeight = FontWeight.Light,
                    modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                )
            } else {
                Spacer(Modifier.height(10.dp))
            }
            content()
        }
    }
}

@Composable
fun MenuSheet(viewModel: LauncherViewModel, onRequestDefaultHome: () -> Unit) {
    BottomMenu("Home", "Wallpaper, settings, default app", onDismiss = viewModel::closeSheet) {
        MenuRow("Search") { viewModel.openSearch() }
        MenuRow("Wallpaper") { viewModel.openWallpaperPicker() }
        MenuRow("Launcher settings") { viewModel.openSettings() }
        MenuRow("Set as default Home") { onRequestDefaultHome() }
    }
}

@Composable
fun AppActionsSheet(app: AppInfo, state: LauncherUiState, viewModel: LauncherViewModel) {
    val pinned = app.key in state.favorites
    val hidden = app.packageName in state.hidden
    val inDock = state.dock.any { it.key == app.key }
    val dockFull = !inDock && state.dock.size >= DockResolver.MAX
    val self = LocalContext.current.packageName
    BottomMenu(app.label, app.category.label, onDismiss = viewModel::dismissAppActions) {
        MenuRow(if (pinned) "Unpin from home" else "Pin to home") { viewModel.toggleFavorite(app) }
        MenuRow(
            when {
                inDock -> "Remove from dock"
                dockFull -> "Dock is full"
                else -> "Add to dock"
            }
        ) {
            if (!dockFull || inDock) viewModel.toggleDock(app)
        }
        MenuRow(
            if (app.key in state.privateApps) "Remove from Private Space" else "Move to Private Space"
        ) { viewModel.togglePrivate(app) }
        MenuRow(if (hidden) "Unhide app" else "Hide app") { viewModel.toggleHidden(app) }
        MenuRow("App info") { viewModel.openAppInfo(app) }
        if (app.packageName != self) {
            MenuRow("Uninstall") { viewModel.uninstallApp(app) }
        }
    }
}

@Composable
fun SettingsSheet(state: LauncherUiState, viewModel: LauncherViewModel) {
    BottomMenu("Lumen", "Fine-tune home, search, and the TouchPad", onDismiss = viewModel::closeSheet) {
        Column(modifier = Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState())) {
        Text(
            "Icon size",
            color = Lumen.Muted,
            fontSize = 12.sp,
            fontFamily = Outfit,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.4.sp
        )
        Slider(
            value = state.iconSizeDp,
            onValueChange = viewModel::setIconSize,
            valueRange = 44f..72f,
            colors = SliderDefaults.colors(
                thumbColor = Lumen.Accent,
                activeTrackColor = Lumen.AccentDeep,
                inactiveTrackColor = Color.White.copy(alpha = 0.12f)
            )
        )
        if (state.hidden.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Hidden apps",
                color = Lumen.Muted,
                fontSize = 12.sp,
                fontFamily = Outfit,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.4.sp
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.height(140.dp)) {
                items(state.apps.filter { it.packageName in state.hidden }, key = { it.key }) { app ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.07f))
                            .clickable { viewModel.unhide(app.packageName) }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            app.label,
                            color = Lumen.Text,
                            fontFamily = Outfit,
                            modifier = Modifier.weight(1f)
                        )
                        Text("Unhide", color = Lumen.Accent, fontSize = 13.sp, fontFamily = Outfit, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "LUMEN VOICE",
            color = Lumen.Muted,
            fontSize = 12.sp,
            fontFamily = Outfit,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.4.sp
        )
        MenuRow("Hey Lumen  ·  ${if (state.heyLumen) "On" else "Off"}") {
            viewModel.setHeyLumen(!state.heyLumen)
        }
        MenuRow("Digital assistant  ·  ${if (AssistantRole.isHeld(LocalContext.current)) "Lumen" else "Set Lumen"}") {
            viewModel.requestDigitalAssistant()
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "ALARMS",
            color = Lumen.Muted,
            fontSize = 12.sp,
            fontFamily = Outfit,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.4.sp
        )
        MenuRow("Alarm tone  ·  ${AlarmTones.label(state.alarmTone)}") {
            viewModel.cycleAlarmTone()
        }
        Text(
            "${AlarmTones.hint(state.alarmTone)}. Tap to hear Aura, Pulse, Dawn, or Bell.",
            color = Lumen.Faint,
            fontSize = 12.sp,
            fontFamily = Outfit,
            fontWeight = FontWeight.Light,
            lineHeight = 18.sp,
            modifier = Modifier.padding(top = 0.dp, bottom = 8.dp, start = 4.dp, end = 4.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "CALENDAR",
            color = Lumen.Muted,
            fontSize = 12.sp,
            fontFamily = Outfit,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.4.sp
        )
        MenuRow("Android calendar  ·  ${if (state.calendarAccess) "On" else "Allow"}") {
            viewModel.requestCalendarAccess()
        }
        Text(
            "Optional. Next only appears when this phone already has events. Flow works without it.",
            color = Lumen.Faint,
            fontSize = 12.sp,
            fontFamily = Outfit,
            fontWeight = FontWeight.Light,
            lineHeight = 18.sp,
            modifier = Modifier.padding(top = 0.dp, bottom = 8.dp, start = 4.dp, end = 4.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "MAIL",
            color = Lumen.Muted,
            fontSize = 12.sp,
            fontFamily = Outfit,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.4.sp
        )
        MenuRow("Outlook & Gmail  ·  ${if (state.inboxAccess) "On" else "Allow"}") {
            viewModel.requestInboxAccess()
        }
        Text(
            "Mail previews from Outlook and Gmail already on this phone. The same access shows WhatsApp missed calls.",
            color = Lumen.Faint,
            fontSize = 12.sp,
            fontFamily = Outfit,
            fontWeight = FontWeight.Light,
            lineHeight = 18.sp,
            modifier = Modifier.padding(top = 0.dp, bottom = 8.dp, start = 4.dp, end = 4.dp)
        )
        MenuRow("Missed calls  ·  ${if (state.callLogAccess) "On" else "Allow"}") {
            viewModel.requestCallLogAccess()
        }
        Text(
            "Phone and WhatsApp missed calls. They leave Flow after you call that number back, or after 3 days.",
            color = Lumen.Faint,
            fontSize = 12.sp,
            fontFamily = Outfit,
            fontWeight = FontWeight.Light,
            lineHeight = 18.sp,
            modifier = Modifier.padding(top = 0.dp, bottom = 8.dp, start = 4.dp, end = 4.dp)
        )
        Text(
            "Say “Hey Lumen” while Home is on screen.\nHi Bixby works with the phone asleep because Samsung built that into the hardware. Lumen can’t use that wake chip. Set Lumen as your digital assistant to open it from the side key, including the lock screen.",
            color = Lumen.Faint,
            fontSize = 12.sp,
            fontFamily = Outfit,
            fontWeight = FontWeight.Light,
            lineHeight = 18.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp, start = 4.dp, end = 4.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "LUMEN TOUCHPAD",
            color = Lumen.Muted,
            fontSize = 12.sp,
            fontFamily = Outfit,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.4.sp
        )
        MenuRow("Tap  ·  ${GestureAction.label(state.tapAction, state.apps)}") {
            viewModel.openGesturePicker("tap")
        }
        MenuRow("Double tap  ·  ${GestureAction.label(state.doubleAction, state.apps)}") {
            viewModel.openGesturePicker("double")
        }
        MenuRow("Triple tap  ·  ${GestureAction.label(state.tripleAction, state.apps)}") {
            viewModel.openGesturePicker("triple")
        }
        Text(
            "Long press  ·  Private Space  ·  fixed",
            color = Lumen.Muted,
            fontSize = 17.sp,
            fontFamily = Outfit,
            fontWeight = FontWeight.Light,
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 4.dp)
        )
        MenuRow("Swipe up  ·  ${GestureAction.label(state.swipeUpAction, state.apps)}") {
            viewModel.openGesturePicker("swipeUp")
        }
        MenuRow("Swipe down  ·  ${GestureAction.label(state.swipeDownAction, state.apps)}") {
            viewModel.openGesturePicker("swipeDown")
        }
        MenuRow("Swipe left  ·  ${GestureAction.label(state.swipeLeftAction, state.apps)}") {
            viewModel.openGesturePicker("swipeLeft")
        }
        MenuRow("Swipe right  ·  ${GestureAction.label(state.swipeRightAction, state.apps)}") {
            viewModel.openGesturePicker("swipeRight")
        }
        MenuRow("Haptics  ·  ${TouchpadHaptics.label(state.touchpadHaptics)}") {
            viewModel.cycleTouchpadHaptics()
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Tap an empty gesture on the TouchPad to assign it. Long-press is always Private Space.\nLight follows your finger; pull back before release to cancel a swipe.\nPinch with two fingers for launcher settings.\nSwipe right from Home for Flow.\nLong-press an app to pin, dock, or hide it.",
            color = Lumen.Faint,
            fontSize = 12.sp,
            fontFamily = Outfit,
            fontWeight = FontWeight.Light,
            lineHeight = 18.sp
        )
        }
    }
}

@Composable
fun AppPickerSheet(state: LauncherUiState, viewModel: LauncherViewModel) {
    val title = GestureAction.pickerTitle(state.pickerKind)
    BottomMenu(title, "Choose what the TouchPad does", onDismiss = viewModel::closeSheet) {
        MenuRow("Not set") { viewModel.setGestureAction(GestureAction.OFF) }
        MenuRow("Lumen Voice") { viewModel.setGestureAction(GestureAction.VOICE) }
        MenuRow("Lock screen") { viewModel.setGestureAction(GestureAction.LOCK) }
        MenuRow("App drawer") { viewModel.setGestureAction(GestureAction.DRAWER) }
        MenuRow("Search") { viewModel.setGestureAction(GestureAction.SEARCH) }
        MenuRow("Notifications") { viewModel.setGestureAction(GestureAction.NOTIFICATIONS) }
        MenuRow("Quick Settings") { viewModel.setGestureAction(GestureAction.QUICK_SETTINGS) }
        Spacer(Modifier.height(8.dp))
        Text(
            "APPS",
            color = Lumen.Muted,
            fontSize = 12.sp,
            fontFamily = Outfit,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.4.sp
        )
        Spacer(Modifier.height(6.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.height(220.dp)) {
            items(state.visibleApps, key = { it.key }) { app ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { viewModel.setGestureAction(GestureAction.app(app.key)) }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppIcon(app.packageName, app.activityName, 36.dp, viewModel.icons)
                    Text(
                        app.label,
                        color = Lumen.Text,
                        fontFamily = Outfit,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MenuRow(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        color = Lumen.Text,
        fontSize = 17.sp,
        fontFamily = Outfit,
        fontWeight = FontWeight.Light,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 4.dp)
    )
}
