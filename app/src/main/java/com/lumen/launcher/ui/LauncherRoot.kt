package com.lumen.launcher.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.lumen.launcher.data.IconSkin
import com.lumen.launcher.ui.theme.Lumen
import com.lumen.launcher.ui.theme.LumenTheme
import com.lumen.launcher.vm.LauncherUiState
import com.lumen.launcher.vm.LauncherViewModel
import com.lumen.launcher.ui.social.SocialCreatePanel
import com.lumen.launcher.ui.social.SocialToolOverlay
import com.lumen.launcher.vm.Sheet
import androidx.compose.runtime.CompositionLocalProvider
import kotlinx.coroutines.launch

@Composable
fun LauncherRoot(
    viewModel: LauncherViewModel,
    state: LauncherUiState,
    onRequestDefaultHome: () -> Unit
) {
    val density = LocalDensity.current
    val layoutDir = LocalLayoutDirection.current
    val screen = LocalView.current.resources.displayMetrics
    val statusTop = WindowInsets.statusBars.getTop(density).toFloat()
    val topZonePx = maxOf(statusTop, with(density) { 24.dp.toPx() }) + with(density) { 20.dp.toPx() }
    val shadeStripPx = maxOf(statusTop, with(density) { 28.dp.toPx() })
    val navBottomPx = WindowInsets.navigationBars.getBottom(density).toFloat()
    val gestureBottomPx = WindowInsets.systemGestures.getBottom(density).toFloat()
    val gestureLeftPx = WindowInsets.systemGestures.getLeft(density, layoutDir).toFloat()
    val gestureRightPx = WindowInsets.systemGestures.getRight(density, layoutDir).toFloat()
    val bottomDeadPx = maxOf(navBottomPx, gestureBottomPx, with(density) { 28.dp.toPx() })
    val edgeDeadPx = maxOf(gestureLeftPx, gestureRightPx, with(density) { 18.dp.toPx() })
    val drawerZonePx = bottomDeadPx + with(density) { 108.dp.toPx() }
    val homePage = 1
    val pagerState = rememberPagerState(initialPage = homePage, pageCount = { 3 })
    val pagerScope = rememberCoroutineScope()
    var listTyping by remember { mutableStateOf(false) }
    val wallpaperPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(viewModel::setSpaceWallpaper)
    }
    LaunchedEffect(state.wallpaperPickPulse) {
        if (state.wallpaperPickPulse == 0) return@LaunchedEffect
        wallpaperPicker.launch("image/*")
    }
    LaunchedEffect(state.homePulse) {
        if (state.homePulse == 0) return@LaunchedEffect
        pagerState.scrollToPage(homePage)
    }
    LaunchedEffect(state.pagerPulse) {
        if (state.pagerPulse == 0) return@LaunchedEffect
        pagerState.animateScrollToPage(state.pagerPage)
    }
    LaunchedEffect(pagerState.settledPage) {
        if (pagerState.settledPage != 2) listTyping = false
    }
    val createUiOpen = state.recentsOpen || state.socialCreateTool != null
    val homeIdle = state.sheet == Sheet.None && !state.privatePageActive && !createUiOpen
    val privateExpand = remember { Animatable(0f) }
    LaunchedEffect(state.privatePageActive) {
        if (state.privatePageActive) {
            privateExpand.snapTo(0f)
            privateExpand.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
        } else {
            privateExpand.animateTo(0f, tween(200))
        }
    }
    LumenTheme {
        CompositionLocalProvider(
            LocalIconTreatment provides IconSkin.treatment(state.iconSkin, state.activeSpace, state.focusing),
            LocalGlass provides rememberGlassColors(state.spaceWallpaper, state.glassDepth)
        ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .homeGestures(
                    enabled = homeIdle,
                    topZonePx = topZonePx,
                    bottomDeadPx = bottomDeadPx,
                    edgeDeadPx = edgeDeadPx,
                    drawerZonePx = drawerZonePx,
                    onSwipeUp = viewModel::openDrawer,
                    onNotifications = viewModel::expandNotifications,
                    onQuickSettings = viewModel::expandQuickSettings,
                    onPinch = viewModel::openSettings
                )
        ) {
            SpaceBackdrop(state.spaceWallpaper)
            AmbientBackdrop()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        when (state.sheet) {
                            Sheet.Voice -> Modifier.blur(22.dp)
                            Sheet.Drawer -> Modifier.blur(28.dp)
                            else -> Modifier
                        }
                    )
            ) {
                HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                userScrollEnabled = homeIdle && !listTyping,
                flingBehavior = PagerDefaults.flingBehavior(
                    state = pagerState,
                    snapAnimationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
                ),
                modifier = Modifier.fillMaxSize()
            ) { page ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { clip = true }
                ) {
                    when (page) {
                        0 -> FlowPage(
                            state = state,
                            viewModel = viewModel,
                            isActive = pagerState.settledPage == 0
                        )
                        2 -> TodoPage(
                            state = state,
                            viewModel = viewModel,
                            onComposerFocus = { listTyping = it }
                        )
                        else -> HomeScreen(
                            state = state,
                            viewModel = viewModel,
                            onRequestDefaultHome = onRequestDefaultHome,
                            isActive = pagerState.settledPage == homePage
                        )
                    }
                }
            }
            if (homeIdle) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(with(density) { shadeStripPx.toDp() })
                        .align(Alignment.TopCenter)
                        .topShadeGestures(
                            onNotifications = viewModel::expandNotifications,
                            onQuickSettings = viewModel::expandQuickSettings
                        )
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = if (pagerState.currentPage == homePage) (state.iconSizeDp + 28).dp else 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(3) { index ->
                        val active = pagerState.currentPage == index
                        Box(
                            Modifier
                                .width(if (active) 18.dp else 14.dp)
                                .height(18.dp)
                                .clip(CircleShape)
                                .clickable { pagerScope.launch { pagerState.animateScrollToPage(index) } },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                Modifier
                                    .width(if (active) 8.dp else 7.dp)
                                    .height(7.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = if (active) 0.95f else 0.32f))
                            )
                        }
                    }
                }
            }
            }
            if (privateExpand.value > 0.01f) {
                val p = privateExpand.value
                val originX = if (state.touchpadRight > state.touchpadLeft) {
                    ((state.touchpadLeft + state.touchpadRight) / 2f) / screen.widthPixels.toFloat()
                } else 0.76f
                val originY = if (state.touchpadBottom > state.touchpadTop) {
                    ((state.touchpadTop + state.touchpadBottom) / 2f) / screen.heightPixels.toFloat()
                } else 0.34f
                Box(Modifier.fillMaxSize()) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.52f * p))
                            .clickable(onClick = viewModel::closePrivatePage)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                transformOrigin = TransformOrigin(
                                    originX.coerceIn(0.05f, 0.95f),
                                    originY.coerceIn(0.05f, 0.95f)
                                )
                                scaleX = 0.22f + 0.78f * p
                                scaleY = 0.20f + 0.80f * p
                            }
                            .statusBarsPadding()
                            .navigationBarsPadding()
                            .padding(lerp(18f, 0f, p).dp)
                            .clip(RoundedCornerShape(lerp(36f, 0f, p).dp))
                            .denseGlass(RoundedCornerShape(lerp(36f, 0f, p).dp))
                            .clickable(enabled = false) {}
                    ) {
                        Box(Modifier.graphicsLayer { alpha = ((p - 0.32f) / 0.68f).coerceIn(0f, 1f) }) {
                            PrivatePage(state = state, viewModel = viewModel)
                        }
                    }
                }
            }
            AnimatedVisibility(
                visible = state.sheet == Sheet.Drawer,
                enter = fadeIn(tween(180)) + slideInVertically(spring(dampingRatio = 0.86f, stiffness = 380f)) { it / 2 },
                exit = fadeOut(tween(140)) + slideOutVertically(tween(180)) { it / 3 }
            ) {
                DrawerSheet(state = state, viewModel = viewModel, onDismiss = viewModel::closeSheet)
            }
            AnimatedVisibility(
                visible = state.sheet == Sheet.Capture,
                enter = fadeIn(tween(160)),
                exit = fadeOut(tween(120))
            ) {
                CaptureSheet(state, viewModel)
            }
            AnimatedVisibility(
                visible = state.sheet == Sheet.Search,
                enter = fadeIn(tween(90)),
                exit = fadeOut(tween(70))
            ) {
                SearchSheet(state = state, viewModel = viewModel, onDismiss = viewModel::closeSheet)
            }
            AnimatedVisibility(
                visible = state.sheet == Sheet.Voice,
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(140))
            ) {
                VoiceSheet(
                    state = state,
                    onDismiss = viewModel::closeSheet,
                    onSubmit = viewModel::submitVoiceText,
                    onRetry = viewModel::retryVoice
                )
            }
            AnimatedVisibility(
                visible = state.sheet == Sheet.Menu,
                enter = fadeIn(tween(160)),
                exit = fadeOut(tween(120))
            ) {
                MenuSheet(state, viewModel, onRequestDefaultHome)
            }
            AnimatedVisibility(
                visible = state.sheet == Sheet.Settings,
                enter = fadeIn(tween(160)),
                exit = fadeOut(tween(120))
            ) {
                SettingsSheet(state, viewModel)
            }
            val active = state.activeApp
            AnimatedVisibility(
                visible = active != null && state.sheet != Sheet.Drawer,
                enter = fadeIn(tween(160)),
                exit = fadeOut(tween(120))
            ) {
                if (active != null) {
                    AppActionsSheet(active, state, viewModel)
                }
            }
            AnimatedVisibility(
                visible = state.sheet == Sheet.AppPicker,
                enter = fadeIn(tween(160)),
                exit = fadeOut(tween(120))
            ) {
                AppPickerSheet(state, viewModel)
            }
            AnimatedVisibility(
                visible = state.sheet == Sheet.Folder,
                enter = fadeIn(tween(160)),
                exit = fadeOut(tween(120))
            ) {
                FolderSheet(state, viewModel)
            }
            AnimatedVisibility(
                visible = state.sheet == Sheet.FolderEditor,
                enter = fadeIn(tween(160)),
                exit = fadeOut(tween(120))
            ) {
                FolderEditorSheet(state, viewModel)
            }
            if (state.recentsOpen) {
                SocialCreatePanel(
                    open = true,
                    creations = state.socialCreations,
                    activeSpace = state.activeSpace,
                    shareManager = viewModel.socialCreate.share,
                    onDismiss = { viewModel.setRecentsOpen(false) },
                    onOpenTool = viewModel::openSocialTool
                )
            }
            state.socialCreateTool?.let { tool ->
                SocialToolOverlay(
                    tool = tool,
                    coordinator = viewModel.socialCreate,
                    onClose = viewModel::closeSocialTool
                )
            }
        }
        }
    }
}
