package com.lumen.launcher.widgets

import android.app.PendingIntent
import android.app.KeyguardManager
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.lumen.launcher.data.*
import com.lumen.launcher.focus.FocusSessionManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** In-place controls never launch the launcher or change the visible Space. */
class WidgetCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION || intent.getStringExtra("token") != WidgetActions.token(context)) return
        val id = intent.getIntExtra("id", -1)
        val kind = WidgetKind.parse(intent.getStringExtra("kind")) ?: return
        if (id !in AppWidgetManager.getInstance(context).getAppWidgetIds(LumenWidgetProvider.component(context, kind))) return
        if (context.getSystemService(KeyguardManager::class.java).isKeyguardLocked) return
        val command = intent.getStringExtra("command") ?: return
        val item = intent.getStringExtra("item").orEmpty()
        if (!allowed(kind, command)) return
        val result = goAsync()
        LumenWidgetProvider.scope.launch {
            try {
                withTimeout(8_000) {
                    commands.withLock {
                    when (command) {
                        "CompleteTask" -> LauncherPreferences(context).updateTodos { tasks ->
                            val now = System.currentTimeMillis()
                            WidgetCommandRules.completeTask(tasks, item, now)
                        }
                        "PauseFocus", "ResumeFocus" -> {
                            val manager = FocusSessionManager.get(context)
                            val snap = manager.snapshotNow()
                            // A delayed control from an old session must not affect a new one.
                            if (WidgetCommandRules.canControlFocus(snap, item, command, System.currentTimeMillis())) {
                                if (command == "PauseFocus") manager.pause()
                                else manager.resume()
                            }
                        }
                        "StartFocus" -> {
                            val manager = FocusSessionManager.get(context)
                            if (!manager.snapshotNow().focusing) manager.start(30)
                        }
                        "StartTrip", "EndTrip" -> {
                            val repo = com.lumen.launcher.travel.data.TripRepository.get(context)
                            val active = repo.getActiveTrip()
                            if (command == "StartTrip" && active == null && repo.hasLocationPermission() &&
                                repo.isLocationServicesEnabled() && com.lumen.launcher.travel.TripViewModel.hasMediaPermission(context)) repo.startTrip()
                            if (command == "EndTrip" && active != null && active.id.toString() == item) repo.endTrip()
                        }
                    }
                    }
                    LumenWidgetProvider.refreshAll(context)
                }
            } catch (_: Exception) {
                // Keep the last valid render; the next live update refreshes it.
            } finally { result.finish() }
        }
    }

    companion object {
        private const val ACTION = "com.lumen.launcher.widgets.COMMAND"
        private val commands = Mutex()
        internal fun allowed(kind: WidgetKind, command: String): Boolean = when (kind) {
            WidgetKind.Tasks -> command == "CompleteTask"
            WidgetKind.Focus -> command in setOf("StartFocus", "PauseFocus", "ResumeFocus")
            WidgetKind.Trip -> command in setOf("StartTrip", "EndTrip")
            else -> false
        }
        fun pending(context: Context, id: Int, kind: WidgetKind, command: String, item: String = ""): PendingIntent =
            PendingIntent.getBroadcast(context, id, Intent(context, WidgetCommandReceiver::class.java)
                .setAction(ACTION).setData(Uri.Builder().scheme("lumen-widget").authority("command")
                    .appendPath(id.toString()).appendPath(command).appendPath(item).build())
                .putExtra("id", id).putExtra("kind", kind.name).putExtra("command", command)
                .putExtra("item", item).putExtra("token", WidgetActions.token(context)),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
