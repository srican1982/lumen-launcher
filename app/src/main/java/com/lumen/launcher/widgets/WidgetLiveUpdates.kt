package com.lumen.launcher.widgets

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.lumen.launcher.data.LauncherPreferences
import com.lumen.launcher.inbox.InboxHub
import com.lumen.launcher.media.NowPlayingRepository
import com.lumen.launcher.social.CreationRepository
import com.lumen.launcher.travel.data.TripRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

object WidgetLiveUpdates {
    private val pulse = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    @OptIn(FlowPreview::class)
    fun start(context: Context) {
        val app = context.applicationContext
        // A widget can start this process without opening any launcher page.
        // Resume photo observation for an already-active trip in that case too.
        LumenWidgetProvider.scope.launch { runCatching { TripRepository.get(app).restoreIfNeeded() } }
        NowPlayingRepository.start(app)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) { pulse.tryEmit(Unit) }
        }
        ContextCompat.registerReceiver(app, receiver, IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIME_TICK)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        LumenWidgetProvider.scope.launch {
            combine(LauncherPreferences(app).state, CreationRepository(app).items,
                TripRepository.get(app).activeTripFlow, InboxHub.items,
                com.lumen.launcher.focus.FocusSessionManager.get(app).snapshot) { _, _, _, _, _ -> Unit }
                .debounce(700).collect { refresh(app) }
        }
        LumenWidgetProvider.scope.launch {
            NowPlayingRepository.state.debounce(350).collect { refresh(app) }
        }
        LumenWidgetProvider.scope.launch { pulse.debounce(400).collect { refresh(app) } }
    }
    private suspend fun refresh(context: Context) {
        runCatching { withTimeout(8_000) { LumenWidgetProvider.refreshAll(context) } }
    }
}
