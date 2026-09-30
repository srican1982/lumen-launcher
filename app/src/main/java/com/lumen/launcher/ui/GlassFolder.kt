package com.lumen.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lumen.launcher.data.AppInfo
import com.lumen.launcher.data.IconCache
import com.lumen.launcher.ui.theme.Outfit
import com.lumen.launcher.vm.LauncherUiState
import com.lumen.launcher.vm.LauncherViewModel

@Composable
private fun folderBrush() = Brush.linearGradient(listOf(LocalGlass.current.airyTop, LocalGlass.current.cardBottom))

@Composable
internal fun FolderPreview(apps: List<AppInfo>, size: Dp, icons: IconCache) {
    val folderFill = folderBrush()
    val stroke = LocalGlass.current.strokeTop
    Box(Modifier.size(size).clip(RoundedCornerShape(size * .30f)).background(folderFill)
        .border(1.dp, stroke, RoundedCornerShape(size * .30f))) {
        apps.take(9).forEachIndexed { index, app ->
            val column = index % 3
            val row = index / 3
            val y = .16f + row * .25f + if (column == 1) -.07f else .03f
            Box(Modifier.offset(x = size * (.08f + column * .29f), y = size * y).size(size * .24f)) {
                AppIcon(app.packageName, app.activityName, size * .24f, icons)
            }
        }
    }
}

@Composable
internal fun GlassFolderPanel(state: LauncherUiState, viewModel: LauncherViewModel) {
    val folderFill = folderBrush()
    val stroke = LocalGlass.current.strokeTop
    val folder = state.activeFolder ?: return
    val apps = folder.appKeys.mapNotNull { key -> state.visibleApps.find { it.key == key } }
    val pages = apps.chunked(9).ifEmpty { listOf(emptyList()) }
    val pager = rememberPagerState(pageCount = { pages.size })
    Dialog(onDismissRequest = viewModel::closeSheet, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.padding(24.dp).widthIn(max = 420.dp).fillMaxWidth()
            .clip(RoundedCornerShape(64.dp)).background(folderFill)
            .border(1.dp, stroke, RoundedCornerShape(64.dp))
            .verticalScroll(rememberScrollState()).padding(24.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(folder.name, color = Color.White, fontFamily = Outfit, fontWeight = FontWeight.SemiBold,
                    fontSize = 26.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                TextButton(onClick = { viewModel.editFolder(folder) }, modifier = Modifier.size(48.dp)
                    .background(Color.White.copy(.16f), CircleShape)) {
                    Text("+", color = Color.White, fontSize = 30.sp)
                }
            }
            Spacer(Modifier.height(20.dp))
            if (apps.isEmpty()) Text("Tap + to add apps to this folder.", color = Color.White.copy(.8f),
                fontFamily = Outfit, modifier = Modifier.padding(vertical = 32.dp))
            else HorizontalPager(state = pager, verticalAlignment = Alignment.Top) { page ->
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val cell = maxWidth / 3
                    val icon = minOf(72.dp, cell - 10.dp)
                    val step = icon + 22.dp
                    Box(Modifier.fillMaxWidth().height(step * 3 + 24.dp)) {
                        pages[page].forEachIndexed { index, app ->
                            val column = index % 3
                            val row = index / 3
                            val shift = if (column == 1) 0.dp else 24.dp
                            Box(Modifier.offset(x = cell * column + (cell - icon) / 2, y = step * row + shift)
                                .size(icon)
                                .semantics { contentDescription = app.label }
                                .clickable { viewModel.launch(app) }) {
                                AppIcon(app.packageName, app.activityName, icon, viewModel.icons)
                            }
                        }
                    }
                }
            }

            if (pages.size > 1) Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.Center) {
                repeat(pages.size) { page ->
                    Box(Modifier.padding(horizontal = 4.dp).size(7.dp)
                        .background(Color.White.copy(if (page == pager.currentPage) 1f else .3f), CircleShape))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { viewModel.editFolder(folder) }) { Text("Edit", color = Color.White.copy(.8f)) }
                TextButton(onClick = viewModel::deleteActiveFolder) { Text("Delete folder", color = Color.White.copy(.65f)) }
                TextButton(onClick = viewModel::closeSheet) { Text("Done", color = Color.White) }
            }
        }
    }
}
