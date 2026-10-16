package com.lumen.launcher.widgets

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

/** Uses the host API so provider configuration also works for protected activities. */
class WidgetConfigureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
        val pending = getSharedPreferences("widget_binding", MODE_PRIVATE)
        if (id == -1 || pending.getInt("id", -1) != id || AppWidgetManager.getInstance(this).getAppWidgetInfo(id) == null) {
            setResult(RESULT_CANCELED); finish(); return
        }
        if (savedInstanceState == null) runCatching {
            WidgetHostSession.host(this).startAppWidgetConfigureActivityForResult(this, id, 0, 92, null)
        }.onFailure { setResult(RESULT_CANCELED); finish() }
    }
    @Deprecated("Legacy callback required by AppWidgetHost configuration API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 92) { setResult(resultCode); finish() }
    }
}
