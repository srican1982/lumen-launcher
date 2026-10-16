package com.lumen.launcher.widgets

import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Pick up to four world cities for the World Time widget. */
class WidgetWorldCitiesActivity : ComponentActivity() {
    private var widgetId = -1
    private var slot = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
        slot = savedInstanceState?.getInt("slot") ?: intent.getIntExtra("slot", -1)
        val owned = AppWidgetManager.getInstance(this)
            .getAppWidgetIds(LumenWidgetProvider.component(this, WidgetKind.WorldClock))
        if (widgetId !in owned) { finish(); return }
        if (savedInstanceState == null && slot in 0..3) choose(slot) else showSlots()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("slot", slot)
        super.onSaveInstanceState(outState)
    }

    private fun showSlots() {
        if (isFinishing) return
        val cities = WidgetWorldCities.read(this, widgetId)
        AlertDialog.Builder(this)
            .setTitle("World Time cities")
            .setItems(cities.mapIndexed { i, c -> "${i + 1}. ${c.name}" }.toTypedArray()) { _, index ->
                choose(index)
            }
            .setPositiveButton("Done") { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun choose(index: Int) {
        slot = index.coerceIn(0, 3)
        val selected = WidgetWorldCities.read(this, widgetId).map { it.id }.toSet()
        val options = WidgetWorldCities.catalog
        AlertDialog.Builder(this)
            .setTitle("City ${slot + 1}")
            .setItems(options.map { city ->
                val mark = if (city.id in selected) "✓ " else ""
                "$mark${city.name}"
            }.toTypedArray()) { _, picked ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        WidgetWorldCities.set(this@WidgetWorldCitiesActivity, widgetId, slot, options[picked])
                    }
                    LumenWidgetProvider.refreshAll(this@WidgetWorldCitiesActivity)
                    Toast.makeText(this@WidgetWorldCitiesActivity, options[picked].name, Toast.LENGTH_SHORT).show()
                    showSlots()
                }
            }
            .setNegativeButton("Back") { _, _ -> showSlots() }
            .setOnCancelListener { showSlots() }
            .show()
    }
}
