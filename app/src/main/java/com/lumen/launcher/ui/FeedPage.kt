package com.lumen.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Biotech
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.SportsSoccer
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.lumen.launcher.data.CalendarRole
import com.lumen.launcher.data.DeviceCalendar
import com.lumen.launcher.data.PhoneAccount
import com.lumen.launcher.data.NewsItem
import com.lumen.launcher.data.NewsTopic
import com.lumen.launcher.flow.defaultFlowEnabled
import com.lumen.launcher.flow.defaultFlowNames
import com.lumen.launcher.flow.parseFlowOrder
import com.lumen.launcher.ui.theme.Lumen
import com.lumen.launcher.ui.theme.Outfit
import com.lumen.launcher.vm.LauncherUiState
import com.lumen.launcher.vm.LauncherViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedPage(
    state: LauncherUiState,
    viewModel: LauncherViewModel
) {
    var editingTopics by remember { mutableStateOf(false) }
    if (state.newsInterests.isEmpty() || editingTopics) {
        FlowInterestPicker(
            selected = state.newsInterests,
            calendars = state.deviceCalendars,
            accounts = state.phoneAccounts,
            workIds = state.workCalendars,
            personalIds = state.personalCalendars,
            hasCalendarPermission = false,
            onSave = { names, work, personal ->
                viewModel.setNewsInterests(names)
                viewModel.setCalendarRoles(work, personal)
                editingTopics = false
            },
            onAddGoogle = viewModel::addGoogleAccount,
            onAddWork = viewModel::addWorkAccount,
            onAllowCalendar = viewModel::requestCalendarAccess,
            inboxAccess = state.inboxAccess,
            onAllowInbox = viewModel::requestInboxAccess,
            hasCallLogPermission = state.callLogAccess,
            onAllowCallLog = viewModel::requestCallLogAccess
        )
        return
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp)
    ) {
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("FEED", color = Lumen.Faint, fontFamily = Outfit, fontWeight = FontWeight.Medium, letterSpacing = 2.sp, fontSize = 12.sp)
                Text("Your mix", color = Lumen.Text, fontFamily = Outfit, fontWeight = FontWeight.Light, fontSize = 34.sp)
            }
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.White.copy(0.1f))
                    .clickable { editingTopics = true }
                    .padding(10.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Tune, contentDescription = "Topics", tint = Lumen.Accent, modifier = Modifier.size(18.dp))
            }
        }
        Text(
            "Only stories from the topics you picked.",
            color = Lumen.Faint,
            fontFamily = Outfit,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            items(NewsTopic.entries, key = { it.name }) { topic ->
                val on = topic.name in state.newsInterests
                Text(
                    text = topic.title,
                    color = if (on) Lumen.OnAccent else Lumen.Muted,
                    fontSize = 13.sp,
                    fontFamily = Outfit,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .then(
                            if (on) Modifier.background(Lumen.AccentFill)
                            else Modifier.background(Color.White.copy(0.08f))
                        )
                        .clickable { viewModel.toggleNewsInterest(topic) }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                )
            }
        }
        PullToRefreshBox(
            isRefreshing = state.newsLoading,
            onRefresh = viewModel::loadNews,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            val featured = state.news.firstOrNull()
            val rest = state.news.drop(1)
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 28.dp)
            ) {
                if (!state.newsLoading && state.news.isEmpty()) {
                    item(key = "empty-feed", span = { GridItemSpan(2) }) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .glass(RoundedCornerShape(24.dp), LocalGlass.current)
                                .clickable(onClick = viewModel::loadNews)
                                .padding(18.dp)
                        ) {
                            Text("NO STORIES YET", color = Lumen.Accent, fontFamily = Outfit, fontSize = 11.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Medium)
                            Text(
                                "Pull down to load photos and headlines for your topics.",
                                color = Lumen.Muted,
                                fontFamily = Outfit,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
                if (featured != null) {
                    item(key = "hero-${featured.url}", span = { GridItemSpan(2) }) {
                        HeroStory(featured) { viewModel.openNews(featured) }
                    }
                }
                items(rest, key = { it.url }) { item ->
                    PhotoStory(item) { viewModel.openNews(item) }
                }
                item(key = "feed-hint", span = { GridItemSpan(2) }) {
                    Text(
                        "Swipe right for Home",
                        color = Lumen.Faint,
                        fontFamily = Outfit,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun FlowInterestPicker(
    selected: Set<String>,
    calendars: List<DeviceCalendar>,
    accounts: List<PhoneAccount> = emptyList(),
    workIds: Set<String>,
    personalIds: Set<String>,
    hasCalendarPermission: Boolean,
    onSave: (Set<String>, Set<String>, Set<String>) -> Unit,
    onAddGoogle: () -> Unit,
    onAddWork: () -> Unit,
    onAllowCalendar: () -> Unit,
    inboxAccess: Boolean = false,
    onAllowInbox: () -> Unit = {},
    hasCallLogPermission: Boolean = false,
    onAllowCallLog: () -> Unit = {},
    flowOrder: List<String> = defaultFlowNames(),
    flowEnabled: Set<String> = defaultFlowEnabled(),
    onToggleFlow: (String) -> Unit = {},
    onMoveFlow: (String, Int) -> Unit = { _, _ -> }
) {
    var tab by remember { mutableStateOf(0) }
    var draft by remember(selected) { mutableStateOf(selected) }
    var roles by remember(calendars, workIds, personalIds) {
        mutableStateOf(
            calendars.associate { cal ->
                val id = cal.id.toString()
                id to when {
                    workIds.isEmpty() && personalIds.isEmpty() -> cal.roleGuess
                    id in workIds -> CalendarRole.Work
                    id in personalIds -> CalendarRole.Personal
                    else -> CalendarRole.Off
                }
            }
        )
    }
    val work = roles.filterValues { it == CalendarRole.Work }.keys
    val personal = roles.filterValues { it == CalendarRole.Personal }.keys
    val ready = draft.isNotEmpty()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 22.dp)
    ) {
        Spacer(Modifier.height(18.dp))
        Text("FLOW", color = Lumen.Faint, fontFamily = Outfit, fontWeight = FontWeight.Medium, letterSpacing = 2.sp, fontSize = 12.sp)
        Text("Personalize", color = Lumen.Text, fontFamily = Outfit, fontWeight = FontWeight.Light, fontSize = 34.sp, lineHeight = 40.sp)
        Row(
            modifier = Modifier
                .padding(top = 16.dp, bottom = 14.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White.copy(0.10f))
                .padding(4.dp)
        ) {
            listOf("Cards", "Calendars", "News").forEachIndexed { index, label ->
                val on = tab == index
                Text(
                    label,
                    color = if (on) Lumen.OnAccent else Lumen.Text,
                    fontFamily = Outfit,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (on) Lumen.Accent else Color.Transparent)
                        .clickable { tab = index }
                        .padding(vertical = 10.dp)
                )
            }
        }
        if (tab == 0) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "Choose what Flow shows, and the order. Empty cards still hide themselves.",
                    color = Lumen.Faint,
                    fontFamily = Outfit,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 14.dp)
                )
                parseFlowOrder(flowOrder).forEach { module ->
                    val enabled = module.name in flowEnabled
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(0.08f))
                            .clickable { onToggleFlow(module.name) }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (enabled) "On" else "Off",
                            color = if (enabled) Lumen.Accent else Lumen.Faint,
                            fontFamily = Outfit,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                            modifier = Modifier.width(36.dp)
                        )
                        Text(
                            module.title,
                            color = Lumen.Text,
                            fontFamily = Outfit,
                            fontSize = 16.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "↑",
                            color = Lumen.Faint,
                            fontSize = 16.sp,
                            modifier = Modifier
                                .clickable { onMoveFlow(module.name, -1) }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                        Text(
                            "↓",
                            color = Lumen.Faint,
                            fontSize = 16.sp,
                            modifier = Modifier
                                .clickable { onMoveFlow(module.name, 1) }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        } else if (tab == 1) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "Next is optional. Allow Android calendar access to show events already on this phone. If nothing is there, Next stays hidden — Flow still works with weather, calls, apps, reminders, and news.",
                    color = Lumen.Faint,
                    fontFamily = Outfit,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 14.dp)
                )
                if (!hasCalendarPermission) {
                    Text(
                        "Allow calendar access",
                        color = Lumen.OnAccent,
                        fontFamily = Outfit,
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Lumen.Accent)
                            .clickable(onClick = onAllowCalendar)
                            .padding(vertical = 14.dp)
                    )
                    Text(
                        "Optional. Lumen only reads calendars Android already exposes. It does not sign into Google or Microsoft.",
                        color = Lumen.Faint,
                        fontFamily = Outfit,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 10.dp, bottom = 12.dp)
                    )
                } else if (calendars.isEmpty()) {
                    Text(
                        "No calendars on this phone yet, so Next stays hidden. Connect Google Calendar or Microsoft only if you want events here. Work Outlook and Teams often keep their calendars inside those apps, and Lumen will not try to bypass that.",
                        color = Lumen.Muted,
                        fontFamily = Outfit,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White.copy(0.08f))
                            .padding(16.dp)
                    )
                } else {
                    calendars.forEach { cal ->
                        val id = cal.id.toString()
                        CalendarRoleCard(
                            calendar = cal,
                            role = roles[id] ?: CalendarRole.Off,
                            onRole = { next -> roles = roles + (id to next) }
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                }
                Text(
                    "Deeper integration is optional.",
                    color = Lumen.Faint,
                    fontFamily = Outfit,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AccountChip("Connect Google Calendar", onAddGoogle)
                    AccountChip("Connect Microsoft", onAddWork)
                }
                Spacer(Modifier.height(8.dp))
                if (!inboxAccess) {
                    Text(
                        "Allow Outlook & Gmail",
                        color = Lumen.OnAccent,
                        fontFamily = Outfit,
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Lumen.Accent)
                            .clickable(onClick = onAllowInbox)
                            .padding(vertical = 14.dp)
                    )
                    Text(
                        "Uses Outlook and Gmail already signed in. The same access shows WhatsApp missed calls. Lumen does not read Teams chats.",
                        color = Lumen.Faint,
                        fontFamily = Outfit,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 10.dp, bottom = 8.dp)
                    )
                }
                if (!hasCallLogPermission) {
                    Text(
                        "Allow missed calls",
                        color = Lumen.OnAccent,
                        fontFamily = Outfit,
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Lumen.Accent)
                            .clickable(onClick = onAllowCallLog)
                            .padding(vertical = 14.dp)
                    )
                    Text(
                        "Phone call log is only requested if Lumen is the default digital assistant. WhatsApp missed calls still use notification access.",
                        color = Lumen.Faint,
                        fontFamily = Outfit,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 10.dp, bottom = 8.dp)
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(NewsTopic.entries, key = { it.name }) { topic ->
                    val on = topic.name in draft
                    TopicCard(
                        topic = topic,
                        selected = on,
                        onClick = {
                            draft = draft.toMutableSet().also { next ->
                                if (on) next.remove(topic.name) else next.add(topic.name)
                            }
                        }
                    )
                }
            }
        }
        Text(
            if (ready) "Save" else "Pick a news topic to save",
            color = if (ready) Lumen.OnAccent else Lumen.Faint,
            fontFamily = Outfit,
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp,
            modifier = Modifier
                .padding(top = 16.dp, bottom = 18.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(if (ready) Lumen.AccentFill else Brush.linearGradient(listOf(Color.White.copy(0.08f), Color.White.copy(0.08f))))
                .clickable(enabled = ready) { onSave(draft, work, personal) }
                .padding(vertical = 14.dp),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun AccountChip(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = Lumen.Text,
        fontFamily = Outfit,
        fontSize = 13.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

@Composable
private fun CalendarRoleCard(
    calendar: DeviceCalendar,
    role: CalendarRole,
    onRole: (CalendarRole) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(0.10f))
            .padding(12.dp)
    ) {
        Text(
            calendar.displayName,
            color = Lumen.Text,
            fontFamily = Outfit,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            calendar.account,
            color = Lumen.Faint,
            fontFamily = Outfit,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            RoleChip("Work", role == CalendarRole.Work) { onRole(if (role == CalendarRole.Work) CalendarRole.Off else CalendarRole.Work) }
            RoleChip("Personal", role == CalendarRole.Personal) { onRole(if (role == CalendarRole.Personal) CalendarRole.Off else CalendarRole.Personal) }
        }
    }
}

@Composable
private fun RoleChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) Lumen.OnAccent else Lumen.Faint,
        fontFamily = Outfit,
        fontSize = 11.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Lumen.Accent else Color.White.copy(0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp)
    )
}

@Composable
private fun TopicCard(topic: NewsTopic, selected: Boolean, onClick: () -> Unit) {
    val tint = Color(topic.tint)
    Box(
        modifier = Modifier
            .aspectRatio(1.05f)
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.linearGradient(
                    listOf(tint.copy(0.95f), tint.copy(0.45f), Lumen.Night.copy(0.4f))
                )
            )
            .then(
                if (selected) Modifier.border(2.dp, Lumen.Accent, RoundedCornerShape(26.dp))
                else Modifier.border(1.dp, Color.White.copy(0.12f), RoundedCornerShape(26.dp))
            )
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        if (selected) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Lumen.AccentFill),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Check, null, tint = Lumen.OnAccent, modifier = Modifier.size(14.dp))
            }
        }
        Column(Modifier.align(Alignment.BottomStart)) {
            Icon(topic.icon, null, tint = Color.White, modifier = Modifier.size(26.dp))
            Text(topic.title, color = Color.White, fontFamily = Outfit, fontWeight = FontWeight.Medium, fontSize = 18.sp, modifier = Modifier.padding(top = 8.dp))
            Text(topic.kicker, color = Color.White.copy(0.78f), fontFamily = Outfit, fontSize = 12.sp)
        }
    }
}

@Composable
private fun HeroStory(item: NewsItem, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .clip(RoundedCornerShape(28.dp))
            .clickable(onClick = onClick)
    ) {
        StoryImage(item.imageUrl, item.topic, Modifier.fillMaxSize())
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.35f to Color.Transparent,
                        1f to Color(0xE614061F)
                    )
                )
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            TopicPill(item)
            Text(
                item.title,
                color = Lumen.Text,
                fontFamily = Outfit,
                fontWeight = FontWeight.Medium,
                fontSize = 24.sp,
                lineHeight = 30.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                item.source,
                color = Lumen.Faint,
                fontFamily = Outfit,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

@Composable
private fun PhotoStory(item: NewsItem, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White.copy(0.06f))
            .clickable(onClick = onClick)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.05f)
                .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
        ) {
            StoryImage(item.imageUrl, item.topic, Modifier.fillMaxSize())
            TopicPill(item, modifier = Modifier.align(Alignment.TopStart).padding(8.dp))
        }
        Text(
            item.title,
            color = Lumen.Text,
            fontFamily = Outfit,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)
        )
    }
}

@Composable
internal fun StoryImage(url: String, topic: NewsTopic, modifier: Modifier) {
    val tint = Color(topic.tint)
    val context = LocalContext.current
    Box(
        modifier.background(
            Brush.linearGradient(listOf(tint.copy(0.7f), Lumen.Night))
        )
    ) {
        if (url.isNotBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(url)
                    .size(720)
                    .crossfade(false)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
internal fun TopicPill(item: NewsItem, modifier: Modifier = Modifier) {
    Text(
        item.topic.title.uppercase(),
        color = Color.White,
        fontFamily = Outfit,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        letterSpacing = 1.1.sp,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(item.topic.tint).copy(0.92f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

private val NewsTopic.icon: ImageVector
    get() = when (this) {
        NewsTopic.World -> Icons.Outlined.Public
        NewsTopic.Nation -> Icons.Outlined.Flag
        NewsTopic.Business -> Icons.Outlined.ShowChart
        NewsTopic.Technology -> Icons.Outlined.Devices
        NewsTopic.Entertainment -> Icons.Outlined.Movie
        NewsTopic.Sports -> Icons.Outlined.SportsSoccer
        NewsTopic.Science -> Icons.Outlined.Biotech
        NewsTopic.Health -> Icons.Outlined.FavoriteBorder
    }
