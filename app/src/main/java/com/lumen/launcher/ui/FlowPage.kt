package com.lumen.launcher.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Grain
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Thunderstorm
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WbCloudy
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.lumen.launcher.alarm.LumenAlarm
import com.lumen.launcher.data.AppInfo
import com.lumen.launcher.data.CaptureKind
import com.lumen.launcher.data.NeedNowHint
import com.lumen.launcher.data.CalendarEvent
import com.lumen.launcher.data.IconCache
import com.lumen.launcher.data.MissedCall
import com.lumen.launcher.data.NewsItem
import com.lumen.launcher.data.SpaceCopy
import com.lumen.launcher.data.UpNext
import com.lumen.launcher.data.WeatherCodes
import com.lumen.launcher.data.WeatherHour
import com.lumen.launcher.data.WeatherSnapshot
import com.lumen.launcher.flow.FlowModule
import com.lumen.launcher.inbox.InboxItem
import com.lumen.launcher.ui.theme.Lumen
import com.lumen.launcher.ui.theme.Outfit
import com.lumen.launcher.vm.LauncherUiState
import com.lumen.launcher.vm.LauncherViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun FlowPage(
    state: LauncherUiState,
    viewModel: LauncherViewModel,
    isActive: Boolean = false
) {
    var personalize by remember { mutableStateOf(false) }
    val showPersonalize = personalize || state.personalizeFlow
    LaunchedEffect(isActive) {
        if (isActive) viewModel.refreshFlow()
    }
    if (showPersonalize) {
        LaunchedEffect(Unit) { viewModel.refreshFlow() }
        FlowInterestPicker(
            selected = state.newsInterests,
            calendars = state.deviceCalendars,
            accounts = state.phoneAccounts,
            workIds = state.workCalendars,
            personalIds = state.personalCalendars,
            hasCalendarPermission = ContextCompat.checkSelfPermission(
                LocalContext.current,
                Manifest.permission.READ_CALENDAR
            ) == PackageManager.PERMISSION_GRANTED,
            onSave = { names, work, personal ->
                viewModel.setNewsInterests(names)
                viewModel.setCalendarRoles(work, personal)
                viewModel.closePersonalize()
                personalize = false
            },
            onAddGoogle = viewModel::addGoogleAccount,
            onAddWork = viewModel::addWorkAccount,
            onAllowCalendar = viewModel::requestCalendarAccess,
            inboxAccess = state.inboxAccess,
            onAllowInbox = viewModel::requestInboxAccess,
            hasCallLogPermission = state.callLogAccess,
            onAllowCallLog = viewModel::requestCallLogAccess,
            flowOrder = state.flowOrder,
            flowEnabled = state.flowEnabled,
            onToggleFlow = viewModel::toggleFlowModule,
            onMoveFlow = viewModel::moveFlowModule
        )
        return
    }
    val hour = LocalDateTime.now().hour
    val greeting = SpaceCopy.greetingFor(hour, state.firstName)
    val continueApp = state.recentApps.firstOrNull()
    val news = state.news.take(2)
    val modules = state.flowModules.filter { module ->
        when (module) {
            FlowModule.Alarms -> state.upcomingAlarms.isNotEmpty()
            FlowModule.Next -> state.upcomingEvents.isNotEmpty() || state.nextEvent != null
            FlowModule.Missed -> state.missedCalls.isNotEmpty()
            FlowModule.Inbox -> state.inbox.any { !it.isDigest }
            FlowModule.Weather -> state.weather != null
            FlowModule.NeedNow -> state.needNowHints.isNotEmpty()
            FlowModule.Continue -> continueApp != null
            FlowModule.Reminders -> state.openTasks.isNotEmpty()
            FlowModule.News -> news.isNotEmpty()
            FlowModule.Review -> state.dailyReview.visible
            FlowModule.Later -> state.later.isNotEmpty()
            FlowModule.Notes -> true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text("Flow", color = Lumen.Accent, fontFamily = Outfit, fontWeight = FontWeight.Light, fontSize = 42.sp)
                Text(greeting, color = Lumen.Text, fontFamily = Outfit, fontWeight = FontWeight.Light, fontSize = 22.sp, modifier = Modifier.padding(top = 2.dp))
                Text("Here's what matters right now.", color = Lumen.Faint, fontFamily = Outfit, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
            }
            Column(horizontalAlignment = Alignment.End) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FlowRoundIcon(Icons.Outlined.AutoAwesome) { viewModel.openCapture() }
                    FlowRoundIcon(Icons.Outlined.PersonOutline) { viewModel.openSettings() }
                    FlowRoundIcon(Icons.Outlined.Settings) { viewModel.openSettings() }
                }
                state.weather?.let { weather ->
                    Text(
                        DateTimeFormatter.ofPattern("EEE, MMM d").format(LocalDate.now()),
                        color = Lumen.Faint,
                        fontFamily = Outfit,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                        Icon(weatherGlyph(weather.summary), null, tint = Color(0xFFFFD56A), modifier = Modifier.size(14.dp))
                        Text(
                            "${weather.temperatureLabel()} ${weather.summary}",
                            color = Lumen.Text,
                            fontFamily = Outfit,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                    Text(weather.place, color = Lumen.Faint, fontFamily = Outfit, fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        if (state.focusing) {
            FocusFlowCard(state, viewModel)
        }
        state.upNext?.let { item ->
            Spacer(Modifier.height(8.dp))
            UpNextCard(item) { viewModel.openUpNext(item) }
        }
        modules.forEach { module ->
            when (module) {
                FlowModule.Alarms -> {
                    if (state.upcomingAlarms.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        AlarmsCard(state.upcomingAlarms.take(3), viewModel)
                    }
                }
                FlowModule.Next -> {
                    val event = state.upcomingEvents.firstOrNull() ?: state.nextEvent
                    val same = event != null && state.upNext?.event?.id == event.id && event.id != 0L
                    if (event != null && !same) {
                        Spacer(Modifier.height(8.dp))
                        NextCard(event) { viewModel.openCalendarEvent(event) }
                    }
                }
                FlowModule.Missed -> {
                    if (state.missedCalls.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        MissedCallsCard(state.missedCalls.take(3), viewModel::callBack, viewModel::openCallLog)
                    }
                }
                FlowModule.Inbox -> {
                    val mail = state.inbox.filterNot { it.isDigest }
                    if (mail.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        InboxCard(mail.take(2), mail.size, state.inboxDigest, viewModel, state.apps)
                    }
                }
                FlowModule.Weather -> {
                    state.weather?.let {
                        Spacer(Modifier.height(8.dp))
                        WeatherCard(it)
                    }
                }
                FlowModule.Review -> {
                    if (state.dailyReview.visible) {
                        Spacer(Modifier.height(8.dp))
                        ReviewCard(state, viewModel)
                    }
                }
                FlowModule.Later -> {
                    if (state.later.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        LaterCard(state, viewModel)
                    }
                }
                FlowModule.Notes -> {
                    Spacer(Modifier.height(8.dp))
                    NotesCard(state, viewModel)
                }
                FlowModule.NeedNow -> {
                    if (state.needNowHints.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        NeedNowCard(state.needNowHints, viewModel)
                    }
                }
                FlowModule.Continue -> {
                    continueApp?.let {
                        Spacer(Modifier.height(8.dp))
                        ContinueCard(
                            app = it,
                            lastUsedAt = state.launchTimes[it.key],
                            icons = viewModel.icons,
                            onOpen = { viewModel.launch(it) }
                        )
                    }
                }
                FlowModule.Reminders -> {
                    if (state.openTasks.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        RemindersCard(state.openTasks.take(3), state.openTasks.size, viewModel::completeTask, viewModel::openTodoList)
                    }
                }
                FlowModule.News -> {
                    if (news.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        NewsCard(
                            items = news,
                            topics = state.selectedNewsTopics.joinToString(" · ") { it.title },
                            onOpen = viewModel::openNews,
                            onSeeAll = { personalize = true }
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .glassPill(RoundedCornerShape(24.dp), LocalGlass.current)
                .clickable { personalize = true }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Tune, null, tint = Lumen.Accent, modifier = Modifier.size(15.dp))
            Text("Personalize Flow", color = Lumen.Text, fontFamily = Outfit, fontWeight = FontWeight.Medium, fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp))
            Icon(Icons.Outlined.ChevronRight, null, tint = Lumen.Faint, modifier = Modifier.size(15.dp))
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun FlowRoundIcon(icon: ImageVector, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .glassPill(CircleShape, LocalGlass.current)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun FlowCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(24.dp), LocalGlass.current)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) { content() }
}

@Composable
private fun LeadIcon(
    icon: ImageVector,
    tint: Color = Lumen.Accent,
    background: Color = LocalGlass.current.pill,
    leading: @Composable (() -> Unit)? = null
) {
    if (leading != null) {
        leading()
    } else {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(background),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(17.dp))
        }
    }
}

@Composable
private fun Kicker(text: String) {
    Text(
        text,
        color = Lumen.Faint,
        fontFamily = Outfit,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.2.sp,
        fontSize = 10.sp
    )
}

@Composable
private fun SeeAll(onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(onClick = onClick)) {
        Text("See all", color = Lumen.Faint, fontFamily = Outfit, fontSize = 12.sp)
        Icon(Icons.Outlined.ChevronRight, null, tint = Lumen.Faint, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun ActionPill(onClick: () -> Unit, content: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .glassPill(RoundedCornerShape(16.dp), LocalGlass.current)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) { content() }
}

@Composable
private fun UpNextCard(item: UpNext, onClick: () -> Unit) {
    val icon = when (item.kind) {
        UpNext.Kind.Flight -> Icons.Outlined.Place
        UpNext.Kind.Meeting -> Icons.Outlined.CalendarMonth
        UpNext.Kind.Delivery, UpNext.Kind.Message -> Icons.Outlined.MailOutline
    }
    FlowCard {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LeadIcon(icon)
            Column(Modifier.weight(1f).padding(start = 10.dp, end = 8.dp)) {
                Kicker("UP NEXT")
                Text(
                    item.title,
                    color = Lumen.Text,
                    fontFamily = Outfit,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.detail.isNotBlank()) {
                    Text(
                        item.detail,
                        color = Lumen.Muted,
                        fontFamily = Outfit,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Lumen.Accent.copy(alpha = 0.22f))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    item.actionLabel,
                    color = Lumen.Accent,
                    fontFamily = Outfit,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun NextCard(
    event: CalendarEvent,
    onClick: () -> Unit
) {
    val away = minutesAway(event.begin)
    FlowCard {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LeadIcon(Icons.Outlined.CalendarMonth)
            Column(Modifier.weight(1f).padding(start = 10.dp, end = 8.dp)) {
                Kicker("NEXT")
                Text(
                    event.title,
                    color = Lumen.Text,
                    fontFamily = Outfit,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    formatEventSpan(event.begin, event.end),
                    color = Lumen.Muted,
                    fontFamily = Outfit,
                    fontSize = 12.sp
                )
                if (event.location.isNotBlank() || event.teams) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Icon(Icons.Outlined.Place, null, tint = Lumen.Faint, modifier = Modifier.size(12.dp))
                        Text(
                            when {
                                event.teams && (event.location.isBlank() || event.location.contains("http", true)) ->
                                    "Microsoft Teams"
                                event.location.isNotBlank() -> event.location
                                else -> "Microsoft Teams"
                            },
                            color = Lumen.Faint,
                            fontFamily = Outfit,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Lumen.Accent.copy(alpha = 0.22f))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                if (away.first != null) {
                    Text(away.first!!, color = Lumen.Accent, fontFamily = Outfit, fontSize = 10.sp)
                }
                Text(
                    away.second,
                    color = Lumen.Accent,
                    fontFamily = Outfit,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun MissedCallsCard(
    calls: List<MissedCall>,
    onCall: (MissedCall) -> Unit,
    onSeeAll: () -> Unit
) {
    val total = calls.sumOf { it.count }.coerceAtLeast(calls.size)
    FlowCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            LeadIcon(
                icon = Icons.Outlined.Call,
                tint = Color.White,
                background = Color(0xCCE25C7A)
            )
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Kicker("MISSED CALLS")
                Text(
                    if (total == 1) "1 missed call" else "$total missed calls",
                    color = Lumen.Faint,
                    fontFamily = Outfit,
                    fontSize = 12.sp
                )
            }
            SeeAll(onSeeAll)
        }
        calls.forEach { call ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(LocalGlass.current.well)
                    .clickable { onCall(call) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        missedCallTitle(call),
                        color = Lumen.Text,
                        fontFamily = Outfit,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        missedCallDetail(call),
                        color = Lumen.Faint,
                        fontFamily = Outfit,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                ActionPill(onClick = { onCall(call) }) {
                    Icon(Icons.Outlined.Call, null, tint = Lumen.Text, modifier = Modifier.size(13.dp))
                    Text(
                        "Call",
                        color = Lumen.Text,
                        fontFamily = Outfit,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 5.dp)
                    )
                }
            }
        }
    }
}

private fun missedCallTitle(call: MissedCall): String {
    val who = call.display
    val extra = if (call.count > 1) " · ${call.count}x" else ""
    return who + extra
}

private fun missedCallDetail(call: MissedCall): String {
    val number = call.number.trim()
    val bits = mutableListOf<String>()
    if (number.isNotBlank() && !number.equals(call.display, ignoreCase = true)) bits += number
    if (call.whatsapp) bits += "WhatsApp"
    bits += relativeMissed(call.at)
    return bits.joinToString("  ·  ")
}

@Composable
private fun AlarmsCard(alarms: List<LumenAlarm>, viewModel: LauncherViewModel) {
    FlowCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LeadIcon(Icons.Outlined.Alarm, leading = { LumenMark(size = 32.dp, glow = true) })
            Column(Modifier.padding(start = 10.dp)) {
                Kicker("ALARMS")
                Text(
                    if (alarms.size == 1) "1 upcoming" else "${alarms.size} upcoming",
                    color = Lumen.Faint,
                    fontFamily = Outfit,
                    fontSize = 12.sp
                )
            }
        }
        alarms.forEach { alarm ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { viewModel.toggleAlarm(alarm.id) }
                    .padding(top = 8.dp, start = 42.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        alarm.displayTime(),
                        color = Lumen.Text,
                        fontFamily = Outfit,
                        fontWeight = FontWeight.Medium,
                        fontSize = 20.sp
                    )
                    Text(
                        listOfNotNull(alarm.whenLabel(), alarm.label.takeIf { it.isNotBlank() }).joinToString(" · "),
                        color = Lumen.Faint,
                        fontFamily = Outfit,
                        fontSize = 12.sp
                    )
                }
                Text(
                    "Remove",
                    color = Lumen.Accent,
                    fontFamily = Outfit,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { viewModel.deleteAlarm(alarm.id) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun InboxCard(
    items: List<InboxItem>,
    total: Int,
    digest: String,
    viewModel: LauncherViewModel,
    apps: List<AppInfo>
) {
    FlowCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            LeadIcon(Icons.Outlined.MailOutline)
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Kicker("INBOX")
                Text(
                    digest.ifBlank { if (total == 1) "1 new" else "$total new" },
                    color = Lumen.Faint,
                    fontFamily = Outfit,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            SeeAll(viewModel::openMailApp)
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items.forEach { item ->
                val mailApp = BrandApps.mail(apps, item.packageName)
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(LocalGlass.current.well)
                        .clickable { viewModel.openInboxItem(item) }
                        .padding(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    FlowBrandIcon(
                        packageName = mailApp.packageName.ifBlank { item.packageName },
                        activityName = mailApp.activityName,
                        icons = viewModel.icons,
                        size = 22.dp,
                        fallback = Icons.Outlined.MailOutline
                    )
                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                item.title,
                                color = Lumen.Text,
                                fontFamily = Outfit,
                                fontWeight = FontWeight.Medium,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                inboxTime(item.postedAt),
                                color = Lumen.Faint,
                                fontFamily = Outfit,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                        if (item.preview.isNotBlank()) {
                            Text(
                                item.preview,
                                color = Lumen.Faint,
                                fontFamily = Outfit,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
            if (items.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun WeatherCard(weather: WeatherSnapshot) {
    FlowCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            LeadIcon(weatherGlyph(weather.summary), tint = Color(0xFFFFD56A))
            Column(Modifier.padding(start = 10.dp, end = 8.dp)) {
                Kicker("WEATHER")
                Text(
                    weather.temperatureLabel(),
                    color = Lumen.Text,
                    fontFamily = Outfit,
                    fontWeight = FontWeight.Light,
                    fontSize = 26.sp,
                    lineHeight = 28.sp
                )
                Text(weather.summary, color = Lumen.Muted, fontFamily = Outfit, fontSize = 12.sp)
                Text(weather.place, color = Lumen.Faint, fontFamily = Outfit, fontSize = 11.sp)
            }
            Spacer(Modifier.weight(1f))
            if (weather.hours.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    weather.hours.take(3).forEach { HourChip(it) }
                }
            }
            Icon(
                Icons.Outlined.ChevronRight,
                null,
                tint = Lumen.Faint,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(16.dp)
            )
        }
    }
}

@Composable
private fun HourChip(hour: WeatherHour) {
    val label = DateTimeFormatter.ofPattern("h a", Locale.getDefault())
        .format(Instant.ofEpochMilli(hour.epochMs).atZone(ZoneId.systemDefault()))
        .uppercase(Locale.getDefault())
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Lumen.Faint, fontFamily = Outfit, fontSize = 9.sp)
        Icon(
            weatherGlyph(WeatherCodes.summary(hour.code), hour.epochMs),
            null,
            tint = Color(0xFFFFD56A),
            modifier = Modifier
                .padding(vertical = 3.dp)
                .size(14.dp)
        )
        Text(hour.temperatureLabel(), color = Lumen.Text, fontFamily = Outfit, fontSize = 12.sp)
    }
}

@Composable
private fun NeedNowCard(hints: List<NeedNowHint>, viewModel: LauncherViewModel) {
    val task = hints.firstOrNull()?.detail
    FlowCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            LeadIcon(Icons.Outlined.Widgets)
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Kicker("NEED NOW")
                Text(
                    task?.takeIf { it.isNotBlank() } ?: "Based on your task and routine",
                    color = Lumen.Faint,
                    fontFamily = Outfit,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            ActionPill(onClick = viewModel::openDrawer) {
                Text("See all", color = Lumen.Text, fontFamily = Outfit, fontSize = 12.sp)
                Icon(Icons.Outlined.ChevronRight, null, tint = Lumen.Faint, modifier = Modifier.size(14.dp))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            hints.forEach { hint ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                    val app = hint.app
                    if (app != null) {
                        IconSlot(
                            label = hint.title,
                            packageName = app.packageName,
                            activityName = app.activityName,
                            iconSize = 40.dp,
                            icons = viewModel.icons,
                            labelSize = 10.sp,
                            onClick = { viewModel.openNeedNow(hint) },
                            onLongClick = { viewModel.showAppActions(app) }
                        )
                    } else {
                        Text(
                            hint.title,
                            color = Lumen.Text,
                            fontFamily = Outfit,
                            fontSize = 12.sp,
                            modifier = Modifier.clickable { viewModel.openNeedNow(hint) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewCard(state: LauncherUiState, viewModel: LauncherViewModel) {
    val review = state.dailyReview
    FlowCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            LeadIcon(Icons.Outlined.CalendarMonth)
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Kicker("TODAY")
                Text(
                    if (review.tasksLeft == 1) "1 task left" else "${review.tasksLeft} tasks left",
                    color = Lumen.Text,
                    fontFamily = Outfit,
                    fontSize = 16.sp
                )
                if (review.overdue > 0) {
                    Text(
                        if (review.overdue == 1) "1 overdue" else "${review.overdue} overdue",
                        color = Lumen.Faint,
                        fontFamily = Outfit,
                        fontSize = 12.sp
                    )
                }
                if (review.nextLine.isNotBlank()) {
                    Text(review.nextLine, color = Lumen.Faint, fontFamily = Outfit, fontSize = 12.sp)
                }
            }
            ActionPill(onClick = viewModel::openTodoList) {
                Text("Tasks", color = Lumen.Text, fontFamily = Outfit, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun LaterCard(state: LauncherUiState, viewModel: LauncherViewModel) {
    FlowCard {
        Kicker("LATER")
        state.later.take(3).forEach { item ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clickable { viewModel.openLater(item) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(item.text, color = Lumen.Text, fontFamily = Outfit, fontSize = 15.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Open", color = Lumen.Faint, fontFamily = Outfit, fontSize = 12.sp, modifier = Modifier.clickable { viewModel.openLater(item) }.padding(end = 8.dp))
                Text("Done", color = Lumen.Faint, fontFamily = Outfit, fontSize = 12.sp, modifier = Modifier.clickable { viewModel.dismissLater(item.id) })
            }
        }
    }
}

@Composable
private fun NotesCard(state: LauncherUiState, viewModel: LauncherViewModel) {
    FlowCard {
        Kicker("NOTES")
        state.spaceNotes.take(4).forEach { note ->
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    note.text,
                    color = Lumen.Text,
                    fontFamily = Outfit,
                    fontSize = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "×",
                    color = Lumen.Faint,
                    fontFamily = Outfit,
                    fontSize = 16.sp,
                    modifier = Modifier.clickable { viewModel.deleteNote(note.id) }.padding(start = 8.dp)
                )
            }
        }
        Text(
            "+ Add note",
            color = Lumen.Faint,
            fontFamily = Outfit,
            fontSize = 14.sp,
            modifier = Modifier
                .padding(top = 10.dp)
                .clickable { viewModel.openCapture(CaptureKind.Note) }
        )
    }
}

@Composable
private fun FocusFlowCard(state: LauncherUiState, viewModel: LauncherViewModel) {
    val left = ((state.focusUntil - System.currentTimeMillis()).coerceAtLeast(0L) / 60000L).toInt()
    FlowCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            LeadIcon(Icons.Outlined.PlayArrow)
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Kicker("FOCUS")
                Text(state.focusTask?.text ?: "This space", color = Lumen.Text, fontFamily = Outfit, fontSize = 16.sp, maxLines = 1)
                Text("$left min left", color = Lumen.Faint, fontFamily = Outfit, fontSize = 12.sp)
            }
            ActionPill(onClick = viewModel::endFocus) {
                Text("End", color = Lumen.Text, fontFamily = Outfit, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ContinueCard(
    app: AppInfo,
    lastUsedAt: Long?,
    icons: IconCache,
    onOpen: () -> Unit
) {
    FlowCard {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LeadIcon(Icons.Outlined.History, leading = { AppIcon(app.packageName, app.activityName, 32.dp, icons) })
            Column(Modifier.weight(1f).padding(start = 10.dp, end = 8.dp)) {
                Kicker("CONTINUE")
                Text(
                    app.label,
                    color = Lumen.Text,
                    fontFamily = Outfit,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    relativeUsed(lastUsedAt),
                    color = Lumen.Faint,
                    fontFamily = Outfit,
                    fontSize = 12.sp
                )
            }
            ActionPill(onClick = onOpen) {
                Icon(Icons.Outlined.PlayArrow, null, tint = Lumen.Text, modifier = Modifier.size(14.dp))
                Text(
                    "Resume",
                    color = Lumen.Text,
                    fontFamily = Outfit,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 4.dp)
                )
                Icon(Icons.Outlined.ChevronRight, null, tint = Lumen.Faint, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun RemindersCard(
    tasks: List<String>,
    total: Int,
    onComplete: (String) -> Unit,
    onSeeAll: () -> Unit
) {
    FlowCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            LeadIcon(Icons.Outlined.CheckBoxOutlineBlank)
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Kicker("REMINDERS")
                Text(
                    if (total == 1) "1 thing today" else "$total things today",
                    color = Lumen.Faint,
                    fontFamily = Outfit,
                    fontSize = 12.sp
                )
            }
            SeeAll(onSeeAll)
        }
        tasks.forEach { item ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onComplete(item) }
                    .padding(top = 8.dp, start = 42.dp)
            ) {
                Icon(Icons.Outlined.CheckBoxOutlineBlank, null, tint = Lumen.Accent, modifier = Modifier.size(18.dp))
                Text(
                    item,
                    color = Lumen.Text,
                    fontFamily = Outfit,
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun NewsCard(
    items: List<NewsItem>,
    topics: String,
    onOpen: (NewsItem) -> Unit,
    onSeeAll: () -> Unit
) {
    FlowCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            LeadIcon(Icons.Outlined.Newspaper)
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Kicker("YOUR NEWS")
                Text(
                    if (topics.isBlank()) "Top stories" else "Top stories from $topics",
                    color = Lumen.Faint,
                    fontFamily = Outfit,
                    fontSize = 12.sp
                )
            }
            SeeAll(onSeeAll)
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items.forEach { item ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(LocalGlass.current.well)
                        .clickable { onOpen(item) }
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.55f)
                            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    ) {
                        StoryImage(item.imageUrl, item.topic, Modifier.fillMaxSize())
                        TopicPill(item, Modifier.align(Alignment.TopStart).padding(7.dp))
                    }
                    Text(
                        item.title,
                        color = Lumen.Text,
                        fontFamily = Outfit,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp)
                    )
                    Text(
                        item.source,
                        color = Lumen.Faint,
                        fontFamily = Outfit,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 3.dp, bottom = 8.dp)
                    )
                }
            }
        }
    }
}

private fun weatherGlyph(summary: String, epochMs: Long? = null): ImageVector {
    val hour = epochMs?.let {
        Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).hour
    }
    val night = hour != null && (hour < 6 || hour >= 19)
    return when {
        summary.contains("Storm", true) -> Icons.Outlined.Thunderstorm
        summary.contains("Snow", true) -> Icons.Outlined.AcUnit
        summary.contains("Rain", true) || summary.contains("Drizzle", true) -> Icons.Outlined.Grain
        summary.contains("Overcast", true) || summary.contains("Fog", true) -> Icons.Outlined.Cloud
        night -> Icons.Outlined.DarkMode
        summary.contains("Mostly", true) -> Icons.Outlined.WbCloudy
        else -> Icons.Outlined.WbSunny
    }
}

private fun formatEventSpan(begin: Long, end: Long): String {
    val zone = ZoneId.systemDefault()
    val fmt = DateTimeFormatter.ofPattern("h:mm a")
    val start = Instant.ofEpochMilli(begin).atZone(zone)
    val stop = Instant.ofEpochMilli(end).atZone(zone)
    return "${fmt.format(start)} – ${fmt.format(stop)}"
}

private fun minutesAway(begin: Long): Pair<String?, String> {
    val mins = ((begin - System.currentTimeMillis()) / 60000L).toInt()
    return when {
        mins <= 0 -> null to "Now"
        mins < 60 -> "in" to "$mins min"
        mins < 120 -> "in" to "1 hr"
        mins < 24 * 60 -> "in" to "${mins / 60} hr"
        mins < 48 * 60 -> null to "Tomorrow"
        else -> null to DateTimeFormatter.ofPattern("EEE").format(
            Instant.ofEpochMilli(begin).atZone(ZoneId.systemDefault())
        )
    }
}

private fun relativeMissed(at: Long): String {
    val mins = ((System.currentTimeMillis() - at) / 60000L).toInt().coerceAtLeast(0)
    return when {
        mins < 1 -> "just now"
        mins == 1 -> "1 min ago"
        mins < 60 -> "$mins min ago"
        mins < 120 -> "1 hr ago"
        mins < 24 * 60 -> "${mins / 60} hr ago"
        else -> "${mins / 1440}d ago"
    }
}

private fun relativeUsed(at: Long?): String {
    if (at == null) return "Opened recently"
    val mins = ((System.currentTimeMillis() - at) / 60000L).toInt().coerceAtLeast(0)
    return when {
        mins < 1 -> "Opened just now"
        mins == 1 -> "Opened 1 min ago"
        mins < 60 -> "Opened $mins min ago"
        mins < 120 -> "Opened 1 hr ago"
        mins < 24 * 60 -> "Opened ${mins / 60} hr ago"
        else -> "Opened ${mins / 1440}d ago"
    }
}

private fun inboxTime(at: Long): String =
    DateTimeFormatter.ofPattern("h:mm a").format(Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()))

private object BrandApps {
    private val outlookPkgs = listOf("com.microsoft.office.outlook")
    private val gmailPkgs = listOf("com.google.android.gm", "com.google.android.gm.lite")

    fun mail(apps: List<AppInfo>, packageName: String?): BrandApp {
        val preferred = packageName?.takeIf { it.isNotBlank() }
        return pick(apps, listOfNotNull(preferred) + outlookPkgs + gmailPkgs)
    }

    private fun pick(apps: List<AppInfo>, packages: List<String>): BrandApp {
        for (pkg in packages) {
            apps.find { it.packageName.equals(pkg, true) }?.let {
                return BrandApp(it.packageName, it.activityName)
            }
        }
        return BrandApp(packages.firstOrNull().orEmpty(), "")
    }
}

private data class BrandApp(val packageName: String, val activityName: String)

@Composable
private fun FlowBrandIcon(
    packageName: String,
    activityName: String,
    icons: IconCache,
    size: androidx.compose.ui.unit.Dp,
    fallback: ImageVector
) {
    var bitmap by remember(packageName, activityName) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(packageName, activityName) {
        bitmap = if (packageName.isBlank()) null else icons.get(packageName, activityName)
    }
    if (bitmap != null && packageName.isNotBlank()) {
        AppIcon(packageName, activityName, size, icons)
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(8.dp))
                .background(LocalGlass.current.well),
            contentAlignment = Alignment.Center
        ) {
            Icon(fallback, null, tint = Lumen.Accent, modifier = Modifier.size(size * 0.52f))
        }
    }
}
