package com.lumen.launcher.widgets

import android.appwidget.AppWidgetHost
import android.content.Context

/** One host connection shared by the mode canvas and the arrangement page. */
object WidgetHostSession {
    private var shared: AppWidgetHost? = null
    private var listeners = 0
    @Synchronized fun host(context: Context): AppWidgetHost = shared ?: AppWidgetHost(
        context.applicationContext, WidgetHostStore.HOST_ID).also { shared = it }
    @Synchronized fun start() {
        if (listeners++ == 0) shared?.startListening()
    }
    @Synchronized fun stop() {
        if (listeners > 0 && --listeners == 0) shared?.stopListening()
    }
}
