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
    var dismissedInitial by remember { mutableStateOf(false) }
    if ((state.newsInterests.isEmpty() && !dismissedInitial) || editingTopics) {
        FlowInterestPicker(
            onClose={editingTopics=false;dismissedInitial=true},
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
            onConnectionsChanged = viewModel::setCalendarRoles,
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
                Icon(Icons.Outlined.Tune, contentDescription = "Topics", tint = Color(0xFFF5CB7E), modifier = Modifier.size(18.dp))
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
                    color = if (on) Color(0xFF241B10) else Lumen.Muted,
                    fontSize = 13.sp,
                    fontFamily = Outfit,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .then(
                            if (on) Modifier.background(Brush.horizontalGradient(listOf(Color(0xFFF3D7A0), Color(0xFFF5CB7E))))
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
                                .glass(RoundedCornerShape(24.dp))
                                .clickable(onClick = viewModel::loadNews)
                                .padding(18.dp)
                        ) {
                            Text("NO STORIES YET", color = Color(0xFFF5CB7E), fontFamily = Outfit, fontSize = 11.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Medium)
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

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
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
    onMoveFlow: (String, Int) -> Unit = { _, _ -> },
    onConnectionsChanged: (Set<String>, Set<String>) -> Unit = { _, _ -> },
    onClose: () -> Unit = {}
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
    val ready = tab != 2 || draft.isNotEmpty()
    androidx.activity.compose.BackHandler(onBack=onClose)
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.foundation.LocalOverscrollConfiguration provides null) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 22.dp, end = 22.dp, bottom = 40.dp)
    ) {
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            androidx.compose.material3.TextButton(onClick=onClose) {Text("\u2715",color=Lumen.Text,fontSize=24.sp)}
            Text("Personalize",color=Lumen.Text,fontFamily=Outfit,fontSize=30.sp)
        }
        Row(
            modifier = Modifier
                .padding(top = 16.dp, bottom = 14.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White.copy(0.10f))
                .padding(4.dp)
        ) {
            listOf("Cards", "Connections", "News").forEachIndexed { index, label ->
                val on = tab == index
                Text(
                    label,
                    color = if (on) Color(0xFF241B10) else Lumen.Text,
                    fontFamily = Outfit,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (on) Color(0xFFF5CB7E) else Color.Transparent)
                        .clickable { tab = index }
                        .padding(vertical = 10.dp)
                )
            }
        }
        if (tab == 0) {
            FlowCardOrderEditor(flowOrder,flowEnabled,onToggleFlow,onMoveFlow,Modifier.weight(1f))
        } else if (tab == 1) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("Choose what Flow can access.",color=Lumen.Faint,fontSize=14.sp,modifier=Modifier.padding(bottom=12.dp))
                FlowConnectionRow("Calendars","Events on this phone",hasCalendarPermission,onAllowCalendar)
                if(hasCalendarPermission) {
                    if(calendars.isEmpty()) Text("No calendars found on this phone.",color=Lumen.Faint,fontSize=12.sp,modifier=Modifier.padding(8.dp))
                    calendars.forEach {cal ->
                        val id=cal.id.toString()
                        CalendarRoleCard(cal,roles[id] ?: CalendarRole.Off) {next -> roles=roles+(id to next); onConnectionsChanged(roles.filterValues { it == CalendarRole.Work }.keys, roles.filterValues { it == CalendarRole.Personal }.keys)}
                        Spacer(Modifier.height(8.dp))
                    }
                }
                FlowConnectionRow("Outlook & Gmail","Email notifications",inboxAccess,onAllowInbox)
                FlowConnectionRow("WhatsApp missed calls","Call notifications",inboxAccess,onAllowInbox)
                FlowConnectionRow("Phone missed calls","Phone call history",hasCallLogPermission,onAllowCallLog)
                Text("Optional accounts",color=Lumen.Faint,fontSize=13.sp,modifier=Modifier.padding(top=16.dp,bottom=8.dp))
                AccountChip("Connect Google Calendar",onAddGoogle)
                Spacer(Modifier.height(8.dp))
                AccountChip("Connect Microsoft",onAddWork)
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
        if(tab == 2) Text(
            if(tab == 1) "Save connections" else if(ready) "Save ${draft.size} ${if(draft.size == 1) "topic" else "topics"}" else "Pick a news topic to save",
            color = if (ready) Color(0xFF241B10) else Lumen.Faint,
            fontFamily = Outfit,
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp,
            modifier = Modifier
                .padding(top = 16.dp, bottom = 18.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(if (ready) Brush.horizontalGradient(listOf(Color(0xFFF3D7A0), Color(0xFFF5CB7E))) else Brush.linearGradient(listOf(Color.White.copy(0.08f), Color.White.copy(0.08f))))
                .clickable(enabled = ready) { onSave(draft, work, personal) }
                .padding(vertical = 14.dp),
            textAlign = TextAlign.Center
        )
    }
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
        color = if (selected) Color(0xFF241B10) else Lumen.Faint,
        fontFamily = Outfit,
        fontSize = 11.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Color(0xFFF5CB7E) else Color.White.copy(0.08f))
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
                if (selected) Modifier.border(3.dp, Color.White, RoundedCornerShape(26.dp))
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
                    .background(Brush.horizontalGradient(listOf(Color(0xFFF3D7A0), Color(0xFFF5CB7E)))),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Check, null, tint = Color(0xFF241B10), modifier = Modifier.size(14.dp))
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
