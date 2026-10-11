package com.lumen.launcher.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context

object HomeUsage {
    fun allowed(context: Context): Boolean = (context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager)
        .checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED

    fun rank(context: Context, apps: List<AppInfo>): List<AppInfo> {
        if (!allowed(context)) return emptyList()
        val now = System.currentTimeMillis()
        val events = (context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager)
            .queryEvents(now - 7 * 86_400_000L, now)
        val counts = mutableMapOf<String, Int>()
        val latest = mutableMapOf<String, Long>()
        val event = UsageEvents.Event()
        var previous: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                val pkg = event.packageName
                if (pkg != previous) counts[pkg] = (counts[pkg] ?: 0) + 1
                latest[pkg] = event.timeStamp
                previous = pkg
            }
        }
        return apps.filter { (counts[it.packageName] ?: 0) > 0 }.sortedWith(
            compareByDescending<AppInfo> { counts[it.packageName] ?: 0 }
                .thenByDescending { latest[it.packageName] ?: 0L }.thenBy { it.key })
    }
}
