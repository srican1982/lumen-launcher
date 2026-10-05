package com.lumen.launcher.ui.focus

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.lumen.launcher.focus.*
import com.lumen.launcher.ui.theme.Outfit
import com.lumen.launcher.ui.theme.LumenPalette
import kotlinx.coroutines.launch

/** Full-screen Focus dialogs must opt out of decor fitting so inset paddings apply. */
internal val FocusDialogProperties = DialogProperties(
    usePlatformDefaultWidth = false,
    decorFitsSystemWindows = false
)

/**
 * Travel-style Focus popup: frosted glass frame + dark inner card + drag handle.
 * Use for contacts, apps, custom time — not full-page screens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FocusPopupSheet(
    onDismiss: () -> Unit,
    /** Fraction of screen height for tall pickers (apps / contacts). */
    heightFraction: Float = 0.78f,
    lockDismiss: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val maxH = (LocalConfiguration.current.screenHeightDp * heightFraction).dp
    val sheetContent: @Composable () -> Unit = {
        val frame = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
        val card = RoundedCornerShape(24.dp)
        Column(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xBDA5B0D8), Color(0xAD4A5680), Color(0xAD6E7CFF))
                    ),
                    frame
                )
                .border(1.dp, Color.White.copy(alpha = 0.35f), frame)
                .padding(horizontal = 14.dp)
        ) {
            if (!lockDismiss) BottomSheetDefaults.DragHandle(
                color = Color.White.copy(alpha = 0.65f),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) else Spacer(Modifier.height(14.dp))
            BackHandler(onBack = onDismiss)
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = if (lockDismiss) maxH - 76.dp else maxH)
                    .then(if (lockDismiss) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                    .clip(card)
                    .background(Color(0xF212182A))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), card)
                    .padding(16.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content
            )
            Spacer(Modifier.height(10.dp))
        }
    }
    if (lockDismiss) {
        androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false,
                dismissOnBackPress = true, dismissOnClickOutside = false)) {
            Box(Modifier.fillMaxSize().background(Color(0x6605081D)).statusBarsPadding().padding(bottom = maxOf(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(), 64.dp) + 12.dp), contentAlignment = Alignment.BottomCenter) {
                sheetContent()
            }
        }
    } else {
        ModalBottomSheet(onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Color.Transparent, scrimColor = Color(0x6605081D), dragHandle = null,
            contentWindowInsets = { WindowInsets.safeDrawing }) { sheetContent() }
    }

}

/** Clears 3-button / gesture nav under Done / Save CTAs. */
@Composable
internal fun Modifier.focusSafeBottom(extra: Dp = 24.dp): Modifier {
    val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val gestures = WindowInsets.systemGestures.asPaddingValues().calculateBottomPadding()
    // 3-button nav bars are often ~48–56dp; keep extra clearance so CTAs never sit under them.
    val bottom = maxOf(nav, gestures, 72.dp) + extra
    return this.padding(bottom = bottom)
}

/** Deep Focus mock — navy ink + indigo/periwinkle glow. */
internal val FocusAccent get() = if (LumenPalette.whiteGlass) Color(0xFF8B80F8) else Color(0xFF6E7CFF)
internal val FocusAccentEnd get() = if (LumenPalette.whiteGlass) Color(0xFF4C73F8) else Color(0xFF4C73F8)
internal val FocusMuted get() = if (LumenPalette.whiteGlass) Color(0xFFA1A5B1) else Color(0xFF8E99B3)
internal val FocusInk get() = if (LumenPalette.whiteGlass) Color(0xFF0B0E14) else Color(0xFF05081D)
internal val FocusCard get() = if (LumenPalette.whiteGlass) Color(0xFF1A1F2A) else Color(0xFF12182A)
internal val FocusCardSelected get() = if (LumenPalette.whiteGlass) Color(0xFF252B4A) else Color(0xFF1A2248)
internal val FocusBorder get() = if (LumenPalette.whiteGlass) Color(0xFF2A3140) else Color(0x33FFFFFF)
internal val FocusBorderGlow get() = if (LumenPalette.whiteGlass) Color(0xFF6E78F0) else Color(0xFF6E7CFF)
internal val FocusIconTint get() = if (LumenPalette.whiteGlass) Color(0xFFACB4E0) else Color(0xFFACB4E0)
internal val FocusCtaText get() = Color(0xFF0B0E18)
internal val FocusSelectionGradient get() = Brush.verticalGradient(listOf(Color(0xFF1E2758), Color(0xFF151C3A)))
internal val FocusGradient get() = Brush.horizontalGradient(listOf(Color(0xFF7E8DFF), Color(0xFF5175FF)))
internal val FocusTitleGradient get() = Brush.horizontalGradient(listOf(Color(0xFFB8C2FF), Color(0xFFB49BFF)))
internal val FocusSurface get() = Brush.linearGradient(listOf(Color(0xE612182A), Color(0xE60B101C)))
internal val FocusDanger = Color(0xFFFF6B8A)
internal val FocusOk = Color(0xFF34D399)

@Composable
internal fun FocusGlass(modifier: Modifier = Modifier, contentPadding: Dp = 14.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(FocusSurface)
            .border(1.dp, FocusBorder, RoundedCornerShape(20.dp))
            .padding(contentPadding),
        content = content
    )
}

/**
 * Title row that flips into a search field in-place (same row — no extra vertical band).
 */
@Composable
internal fun FocusSheetSearchHeader(
    title: String,
    query: String,
    onQueryChange: (String) -> Unit,
    searching: Boolean,
    onSearchingChange: (Boolean) -> Unit,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (searching) {
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                cursorBrush = SolidColor(FocusAccent),
                textStyle = TextStyle(color = Color.White, fontFamily = Outfit, fontSize = 15.sp),
                modifier = Modifier
                    .weight(1f)
                    .clip(shape)
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(1.dp, FocusAccent.copy(alpha = 0.55f), shape)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                decorationBox = { inner ->
                    Box(Modifier.fillMaxWidth()) {
                        if (query.isEmpty()) {
                            Text("Search", color = FocusMuted, fontFamily = Outfit, fontSize = 15.sp)
                        }
                        inner()
                    }
                }
            )
            Icon(
                Icons.Outlined.Close,
                contentDescription = "Close search",
                tint = FocusMuted,
                modifier = Modifier
                    .size(22.dp)
                    .clickable {
                        onQueryChange("")
                        onSearchingChange(false)
                    }
            )
        } else {
            Text(
                title,
                color = Color.White,
                fontFamily = Outfit,
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
            Icon(
                Icons.Outlined.Search,
                contentDescription = "Search",
                tint = FocusMuted,
                modifier = Modifier
                    .size(22.dp)
                    .clickable { onSearchingChange(true) }
            )
            trailing()
        }
    }
}

/** Separate frosted tile for apps / groups / people in a horizontal scroll row. */
@Composable
internal fun FocusGlassTile(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    width: Dp = 76.dp,
    height: Dp = 96.dp,
    topTrailing: (@Composable BoxScope.() -> Unit)? = null,
    icon: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier
            .width(width)
            .height(height)
            .then(
                if (selected) Modifier.shadow(10.dp, shape, ambientColor = FocusAccent.copy(0.4f), spotColor = FocusAccent.copy(0.5f))
                else Modifier
            )
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = if (selected) 0.18f else 0.12f),
                        Color.White.copy(alpha = if (selected) 0.07f else 0.04f)
                    )
                )
            )
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                brush = if (selected) Brush.linearGradient(listOf(FocusBorderGlow, FocusAccentEnd))
                else Brush.verticalGradient(listOf(Color.White.copy(0.42f), Color.White.copy(0.12f))),
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = if (height <= 70.dp) 6.dp else 10.dp)
    ) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            icon()
            Spacer(Modifier.height(if (height <= 70.dp) 4.dp else 8.dp))
            Text(
                label,
                color = Color.White,
                fontFamily = Outfit,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
        if (topTrailing != null) {
            Box(Modifier.align(Alignment.TopEnd), content = topTrailing)
        }
    }
}

@Composable
fun FocusHeader(greeting: String, weatherContent: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(greeting, color = Color.White, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 28.sp)
            Text(
                java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMMM")),
                color = FocusMuted,
                fontFamily = Outfit,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 1.dp)
            )
        }
        weatherContent()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusSettingsButton(label: String? = null, dismissSignal: Int = 0) {
    val context = LocalContext.current
    val repo = remember { FocusAllowedPeopleRepository.get(context) }
    val settings by repo.settings.collectAsState()
    val people by repo.people.collectAsState()
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    LaunchedEffect(dismissSignal) { open = false }
    if (label == null) IconButton(onClick = { open = true }) {
        Icon(Icons.Outlined.Settings, "Focus settings", tint = FocusMuted, modifier = Modifier.size(22.dp))
    } else TextButton(onClick = { open = true }) { Text(label, color = FocusAccent, fontSize = 12.sp) }
    if (open) FocusPopupSheet(onDismiss = { open = false }, heightFraction = 0.82f) {
        Text("Focus settings", color = Color.White, fontFamily = Outfit, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        fun save(next: FocusPolicySettings) {
            repo.setSettings(next)
            scope.launch { FocusSessionManager.get(context).refreshPolicy() }
        }
        FocusToggle("Calls from selected people", settings.allowCallsFromSelected) { save(settings.copy(allowCallsFromSelected = it)) }
        FocusToggle("Messages from ALL Android favorites", settings.allowMessagesFromSelected) { save(settings.copy(allowMessagesFromSelected = it)) }
        FocusToggle("Repeated callers", settings.allowRepeatedCallers) { save(settings.copy(allowRepeatedCallers = it)) }
        FocusToggle("Alarms", settings.allowAlarms) { save(settings.copy(allowAlarms = it)) }
        FocusToggle("Calendar reminders", settings.allowCalendarReminders) { save(settings.copy(allowCalendarReminders = it)) }
        FocusToggle("Hide notification visuals", settings.silenceEveryoneElse) { save(settings.copy(silenceEveryoneElse = it)) }
        FocusRingCheck()
        Text(FocusPolicyController(context).capabilityNote(people, settings), color = FocusMuted, fontSize = 12.sp, lineHeight = 18.sp)
        FocusPrimaryAction(label = "Done", action = { open = false })
    }
}

@Composable
private fun FocusToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(FocusCard)
            .border(1.dp, FocusBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, Modifier.weight(1f), color = Color.White, fontFamily = Outfit, fontSize = 14.sp, lineHeight = 19.sp)
        Switch(
            checked,
            onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = FocusAccent,
                checkedThumbColor = Color.White,
                uncheckedTrackColor = FocusBorder,
                uncheckedThumbColor = FocusMuted
            )
        )
    }
}

@Composable
internal fun FocusPrimaryAction(label: String, action: () -> Unit, showCheck: Boolean = true) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(54.dp)
            .shadow(16.dp, RoundedCornerShape(28.dp), ambientColor = FocusAccent.copy(0.35f), spotColor = FocusAccent.copy(0.45f))
            .clip(RoundedCornerShape(28.dp))
            .background(FocusGradient)
            .clickable(onClick = action),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (showCheck) Icon(Icons.Filled.Check, null, tint = FocusCtaText, modifier = Modifier.size(20.dp))
            Text(label, color = FocusCtaText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, fontFamily = Outfit)
        }
    }
}

@Composable
internal fun FocusBackRow(onBack: () -> Unit) {
    IconButton(
        onClick = onBack,
        modifier = Modifier.size(36.dp).offset(x = (-6).dp)
    ) {
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(28.dp))
    }
}

/** Keeps Focus app/group horizontal rows from flipping the launcher pager page. */
@Composable
internal fun Modifier.focusContainHorizontalScroll(): Modifier {
    val connection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
                Offset(available.x, 0f)
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
                Velocity(available.x, 0f)
        }
    }
    return this.nestedScroll(connection)
}

@Composable
internal fun FocusPageBanner(title: String, subtitle: String, badge: String? = null) {
    Box(Modifier.fillMaxWidth().height(128.dp).clip(RoundedCornerShape(20.dp))) {
        androidx.compose.foundation.Image(
            androidx.compose.ui.res.painterResource(com.lumen.launcher.R.drawable.focus_moonlit_lake),
            null,
            modifier = Modifier.fillMaxSize(),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(FocusInk.copy(alpha = 0.15f), FocusInk.copy(alpha = 0.88f), FocusInk))
            )
        )
        Column(Modifier.align(Alignment.BottomStart).padding(bottom = 10.dp, end = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    title,
                    style = androidx.compose.ui.text.TextStyle(brush = FocusTitleGradient, fontWeight = FontWeight.Bold, fontSize = 34.sp),
                    fontFamily = Outfit
                )
                if (badge != null) {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(badge, color = FocusMuted, fontSize = 12.sp, fontFamily = Outfit)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = FocusMuted, fontSize = 13.sp, fontFamily = Outfit, lineHeight = 18.sp)
        }
    }
}
