package com.lumen.launcher.widgets

import android.Manifest
import android.app.AppOpsManager
import android.app.KeyguardManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.lumen.launcher.data.LauncherPreferences
import com.lumen.launcher.data.LauncherState
import com.lumen.launcher.data.TaskAgenda
import com.lumen.launcher.data.TodoItem
import com.lumen.launcher.data.UsageTimeline
import com.lumen.launcher.inbox.InboxHub
import com.lumen.launcher.social.CreationKind
import com.lumen.launcher.social.CreationRepository
import com.lumen.launcher.travel.data.TripRepository
import com.lumen.launcher.travel.model.Trip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

data class WidgetPerson(val id: Long, val name: String, val photo: String?)
data class WidgetSnapshot(
    val stored: LauncherState = LauncherState(),
    val tasks: List<TodoItem> = emptyList(),
    val trip: Trip? = null,
    val lastTrip: Trip? = null,
    val photos: List<String> = emptyList(),
    val scribble: String? = null,
    val quoteCreation: String? = null,
    val people: List<WidgetPerson> = emptyList(),
    val usage: Map<String, Long>? = null,
    val notifications: Int? = null,
    val notificationPackages: List<String> = emptyList(),
    val battery: Int? = null,
    val charging: Boolean = false,
    val focusPaused: Boolean = false,
    val focusStartedAt: Long = 0L,
    val focusRemainingMs: Long = 0L,
    val focusError: String = "",
    val locked: Boolean = false
)

/** Small, bounded snapshots: widgets never need to construct a launcher ViewModel. */
object WidgetRepository {
    suspend fun load(context: Context): WidgetSnapshot = withContext(Dispatchers.IO) {
        val stored = LauncherPreferences(context).state.first()
        val locked = context.getSystemService(KeyguardManager::class.java).isKeyguardLocked
        val battery = context.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percent = if (level >= 0 && scale > 0) (level * 100 / scale).coerceIn(0, 100) else null
        if (locked) return@withContext WidgetSnapshot(stored = stored, battery = percent, locked = true)
        val trip = TripRepository.get(context).getActiveTrip()
        val creations = CreationRepository(context).items.first()
        val photos = creations
            .filter { it.kind == CreationKind.Photo }.map { it.filePath }.take(3)
        val notifications = InboxHub.items.value.filter { !it.isDigest && !it.secret && it.packageName !in stored.hidden && stored.privateApps.none { key -> key.substringBefore('/') == it.packageName } }
        val focus = com.lumen.launcher.focus.FocusSessionManager.get(context).snapshotNow()
        WidgetSnapshot(
            stored = stored,
            tasks = TaskAgenda.today(stored.todos.filter { it.space != com.lumen.launcher.data.SpaceKind.Private }, System.currentTimeMillis()),
            trip = trip,
            lastTrip = TripRepository.get(context).getLatestEndedTrip(),
            photos = photos,
            scribble = creations.firstOrNull { it.kind == CreationKind.Scribble }?.filePath,
            quoteCreation = creations.firstOrNull { it.kind == CreationKind.Quote }?.filePath,
            usage = socialUsage(context, stored.hidden + stored.privateApps.map { it.substringBefore('/') }),
            notifications = if (InboxHub.hasAccess(context)) notifications.size else null,
            notificationPackages = if (InboxHub.hasAccess(context)) notifications.map { it.packageName }.distinct().take(3) else emptyList(),
            battery = percent,
            charging = (battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0,
            focusPaused = focus.paused,
            focusStartedAt = focus.startedAt,
            focusRemainingMs = focus.remainingMs(),
            focusError = focus.error
        )
    }

    private fun starred(context: Context): List<WidgetPerson> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return emptyList()
        return runCatching {
            val uri = ContactsContract.Contacts.CONTENT_URI
            val columns = arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY, ContactsContract.Contacts.PHOTO_THUMBNAIL_URI)
            context.contentResolver.query(uri, columns, "${ContactsContract.Contacts.STARRED}=1", null,
                "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} COLLATE LOCALIZED ASC")?.use { cursor ->
                buildList {
                    while (size < 4 && cursor.moveToNext()) add(WidgetPerson(cursor.getLong(0), cursor.getString(1).orEmpty(), cursor.getString(2)))
                }
            }.orEmpty()
        }.getOrDefault(emptyList())
    }

    private val socialPackages = setOf("com.whatsapp", "com.whatsapp.w4b", "com.instagram.android", "com.zhiliaoapp.musically", "org.telegram.messenger", "com.twitter.android", "com.snapchat.android", "com.facebook.orca", "com.facebook.katana")
    private fun socialUsage(context: Context, excluded: Set<String>): Map<String, Long>? {
        val ops = context.getSystemService(AppOpsManager::class.java)
        if (ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName) != AppOpsManager.MODE_ALLOWED) return null
        return runCatching {
            val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val now = System.currentTimeMillis()
            val events = context.getSystemService(UsageStatsManager::class.java).queryEvents(start - 86_400_000, now)
            val event = UsageEvents.Event()
            val samples = mutableListOf<UsageTimeline.Event>()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                when (event.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> samples += UsageTimeline.Event(event.timeStamp, event.packageName)
                    UsageEvents.Event.ACTIVITY_PAUSED -> samples += UsageTimeline.Event(event.timeStamp, event.packageName, false)
                    UsageEvents.Event.SCREEN_NON_INTERACTIVE, UsageEvents.Event.DEVICE_SHUTDOWN -> samples += UsageTimeline.Event(event.timeStamp, null, false)
                }
            }
            UsageTimeline.totals(samples, start, now, socialPackages - excluded)
        }.getOrNull()
    }
}
