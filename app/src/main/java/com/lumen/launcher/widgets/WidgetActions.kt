package com.lumen.launcher.widgets

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.lumen.launcher.LauncherActivity
import java.util.UUID

/** Only our immutable, explicit widget PendingIntents can invoke a widget action. */
object WidgetActions {
    private const val ACTION = "com.lumen.launcher.WIDGET_ACTION"
    fun isWidgetIntent(intent: Intent?) = intent?.action == ACTION

    fun contacts(context: Context, id: Int, slot: Int = -1): PendingIntent = PendingIntent.getActivity(context, id,
        Intent(context, WidgetContactsActivity::class.java)
            .setData(Uri.parse("lumen-widget://contacts/$id/$slot"))
            .putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, id).putExtra("slot", slot),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun worldCities(context: Context, id: Int, slot: Int = -1): PendingIntent {
        val requestCode = 31 * id + ("WorldCities/$slot").hashCode()
        return PendingIntent.getActivity(context, requestCode,
            Intent(context, WidgetWorldCitiesActivity::class.java)
                .setData(Uri.parse("lumen-widget://world-cities/$id/$slot"))
                .putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, id).putExtra("slot", slot),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun launchPackage(context: Context, widgetId: Int, packageName: String): PendingIntent {
        val launch = context.packageManager.getLaunchIntentForPackage(packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?: Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val requestCode = 31 * widgetId + ("Launch/$packageName").hashCode()
        return PendingIntent.getActivity(
            context, requestCode, launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun dial(context: Context, id: Int, slot: Int, number: String): PendingIntent = PendingIntent.getActivity(context, id,
        Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun message(context: Context, id: Int, slot: Int, numbers: List<String>): PendingIntent = PendingIntent.getActivity(context, id,
        Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", numbers.distinct().joinToString(";"), null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun contactEmail(context: Context, id: Int, address: String): PendingIntent = PendingIntent.getActivity(context, id,
        Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", address, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun contactVideo(context: Context, id: Int, number: String): PendingIntent = PendingIntent.getActivity(context, id,
        Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null))
            .putExtra(android.telecom.TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE, android.telecom.VideoProfile.STATE_BIDIRECTIONAL)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun tripToggle(context: Context, id: Int, tripId: Long?, kind: WidgetKind = WidgetKind.Trip): PendingIntent {
        val repo = com.lumen.launcher.travel.data.TripRepository.get(context)
        val ready = repo.hasLocationPermission() && repo.isLocationServicesEnabled() &&
            com.lumen.launcher.travel.TripViewModel.hasMediaPermission(context)
        return if (tripId != null || ready) WidgetCommandReceiver.pending(context, id, kind,
            if (tripId == null) "StartTrip" else "EndTrip", tripId?.toString().orEmpty())
        else pending(context, id, "TripToggle")
    }

    fun focusStart(context: Context, id: Int): PendingIntent {
        val ready = com.lumen.launcher.focus.FocusCallAccess.ready(context) &&
            com.lumen.launcher.alarm.AlarmScheduler.canExact(context) &&
            com.lumen.launcher.focus.FocusSessionManager.get(context).hasPolicyAccess() &&
            (android.os.Build.VERSION.SDK_INT < 33 || androidx.core.content.ContextCompat.checkSelfPermission(context,
                android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED)
        return if (ready) WidgetCommandReceiver.pending(context, id, WidgetKind.Focus, "StartFocus")
        else pending(context, id, "FocusSetup")
    }
    internal fun token(context: Context): String {
        val prefs = context.getSharedPreferences("widget_actions", Context.MODE_PRIVATE)
        synchronized(this) {
            return prefs.getString("token", null) ?: UUID.randomUUID().toString().also {
                prefs.edit().putString("token", it).commit()
            }
        }
    }
    fun pending(context: Context, widgetId: Int, destination: String, item: String = ""): PendingIntent {
        val intent = Intent(context, LauncherActivity::class.java).setAction(ACTION)
            .setData(Uri.Builder().scheme("lumen-widget").authority("open").appendPath(widgetId.toString())
                .appendPath(destination).appendPath(item).build())
            .putExtra("widget_token", token(context)).putExtra("widget_destination", destination).putExtra("widget_item", item)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        // Unique request codes so each button keeps its own destination.
        val requestCode = 31 * widgetId + (destination + '/' + item).hashCode()
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
    fun read(context: Context, intent: Intent?): Pair<String, String>? {
        if (intent?.action != ACTION || intent.getStringExtra("widget_token") != token(context)) return null
        val destination = intent.getStringExtra("widget_destination") ?: return null
        if (WidgetKind.parse(destination) == null && destination !in setOf("AddTask", "CompleteTask", "StartFocus", "Person", "Photo", "TripToggle", "TripAlbums", "FocusSetup", "UsageAccess", "TripPlan", "TripCamera", "TripNote", "TripShare", "TaskList", "Calendar", "WorldCities", "OpenMedia", "MediaStore")) return null
        val item = intent.getStringExtra("widget_item").orEmpty()
        intent.removeExtra("widget_token") // do not replay on resume / configuration change
        return destination to item
    }
}
