package com.lumen.launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.lumen.launcher.ui.theme.Lumen
import com.lumen.launcher.ui.theme.Outfit
import com.lumen.launcher.vm.LauncherUiState
import com.lumen.launcher.vm.LauncherViewModel

@Composable
fun PrivatePage(
    state: LauncherUiState,
    viewModel: LauncherViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp)
    ) {
        Spacer(Modifier.height(18.dp))
        Text("LOCKED", color = Lumen.Faint, fontFamily = Outfit, fontWeight = FontWeight.Medium, letterSpacing = 2.sp, fontSize = 12.sp)
        Text("Space", color = Lumen.Text, fontFamily = Outfit, fontWeight = FontWeight.Light, fontSize = 40.sp)
        Text(
            if (state.privateUnlocked) "Unlocked. Close this screen to lock again."
            else "Hides and locks access through Lumen. Apps can still appear in Settings, Play Store, another launcher, and notifications.",
            color = Lumen.Faint,
            fontFamily = Outfit,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
        )
        if (!state.privateUnlocked) {
            PrivateLockCard(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                onUnlock = viewModel::unlockPrivate
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(state.gridColumns),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(top = 8.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.privateAppList, key = { it.key }) { app ->
                    IconSlot(
                        label = app.label,
                        packageName = app.packageName,
                        activityName = app.activityName,
                        iconSize = state.iconSizeDp.dp,
                        icons = viewModel.icons,
                        showLabel = state.showLabels,
                        onClick = { viewModel.launch(app) },
                        onLongClick = { viewModel.showAppActions(app) }
                    )
                }
                if (state.privateAppList.isEmpty()) {
                    item(key = "empty-private", span = { GridItemSpan(state.gridColumns) }) {
                        Text(
                            "Long-press any app on Home and choose Move to Locked Space.",
                            color = Lumen.Faint,
                            fontSize = 14.sp,
                            fontFamily = Outfit,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }
        Text(
            "Tap outside or press Back to lock",
            color = Lumen.Faint,
            fontFamily = Outfit,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 18.dp)
        )
    }
}
